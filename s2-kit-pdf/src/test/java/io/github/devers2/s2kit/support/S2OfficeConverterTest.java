package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2kit.support.S2OfficeConverter.HtmlOptions;

/**
 * Office and Hangul documents to PDF, to HTML for web editors and to other formats.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 오피스·한글 문서를 PDF, 웹에디터용 HTML, 다른 형식으로 바꾸는지 확인합니다.
 */
class S2OfficeConverterTest {

    @TempDir
    Path dir;

    private Path fixtures;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "shell script");
        fixtures = Files.createDirectories(dir.resolve("fixtures"));
        // What LibreOffice leaves next to an HTML export | LibreOffice 가 HTML 과 함께 남기는 것
        ImageIO.write(new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB), "bmp", fixtures.resolve("document_html_a.bmp").toFile());
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", fixtures.resolve("document_html_b.png").toFile());
        Files.write(fixtures.resolve("document_html_c.wmf"), new byte[] { (byte) 0xD7, (byte) 0xCD, (byte) 0xC6, (byte) 0x9A, 0, 0 });
        Files.writeString(fixtures.resolve("document.html"), """
                <!DOCTYPE html><html><head><meta name="generator" content="LibreOffice"/>
                <style>p { margin: 0 }</style><script>steal()</script></head>
                <body lang="ko-KR"><h1>보고서</h1>
                <center><table style="page-break-before: always; background: #fff"><tr><td>c</td></tr></table></center>
                <p onclick="steal()" style="text-align: center; page-break-after: auto; widows: 2"><font color="#ff0000" face="맑은 고딕" size="4">빨간 글자</font></p>
                <table border="1"><tr><td><b>항목</b></td><td>값</td></tr></table>
                <p><img src="document_html_a.bmp" name="Image1" width="600"/> <img src="document_html_b.png"/>
                <img src="document_html_a.bmp"/> <img src="document_html_c.wmf"/> <img src="../../etc/passwd"/></p>
                <p><a href="javascript:steal()">bad</a> <a href="https://ok.example">ok</a></p>
                <iframe src="https://evil.example"></iframe></body></html>
                """);
        try (var doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(fixtures.resolve("document.pdf").toFile());
        }
        Files.writeString(fixtures.resolve("document.docx"), "docx");
        var script = dir.resolve("soffice");
        Files.writeString(script, """
                #!/usr/bin/env bash
                out=""; format=""
                while [ $# -gt 0 ]; do case "$1" in --outdir) out="$2"; shift 2 ;; --convert-to) format="$2"; shift 2 ;; *) shift ;; esac; done
                echo "$format" >> "%1$s/formats.log"
                case "${format%%%%:*}" in
                  html) cp "%2$s"/document.html "%2$s"/document_html_* "$out/" ;;
                  pdf) cp "%2$s/document.pdf" "$out/" ;;
                  docx) cp "%2$s/document.docx" "$out/" ;;
                  *) exit 1 ;;
                esac
                """.formatted(dir, fixtures));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        S2OfficeConverter.setCommand(script.toString());
    }

    @AfterEach
    void tearDown() {
        S2OfficeConverter.resetCommand();
    }

    @Test
    void htmlIsCleanedForEditors() throws IOException {
        var doc = S2OfficeConverter.toHtml(Files.writeString(dir.resolve("보고서.hwp"), "hwp"));
        var html = doc.html();
        assertTrue(html.contains("<h1>보고서</h1>") && html.contains("<table") && html.contains("<b>항목</b>"), html);
        assertTrue(html.contains("<p style=\"text-align: center;\">"), "formatting kept, print-only rules gone: " + html);
        assertFalse(html.contains("page-break") || html.contains("widows") || html.contains("<center"), html);
        assertTrue(html.contains("<div style=\"text-align: center\">") && html.contains("<table style=\"background: #fff\">"), html);
        assertFalse(html.contains("script") || html.contains("onclick") || html.contains("iframe") || html.contains("<style"), html);
        assertFalse(html.contains("javascript:"), html);
        assertTrue(html.contains("href=\"https://ok.example\""), html);
        assertFalse(html.contains("<font"), html);
        assertTrue(html.contains("color: #ff0000") && html.contains("font-family: 맑은 고딕") && html.contains("font-size: large"), html);
        assertTrue(Files.readAllLines(dir.resolve("formats.log")).contains("html"), "the plain HTML filter (XHTML crashes on hwp)");
    }

    @Test
    void imagesAreCollectedNamedAndMadeWebReady() throws IOException {
        var doc = S2OfficeConverter.toHtml(Files.writeString(dir.resolve("a.docx"), "x"), HtmlOptions.create().maxImageWidth(800));
        var images = doc.images();
        assertEquals(List.of("image-1.png", "image-2.png"), images.stream().map(S2OfficeConverter.OfficeImage::name).toList(),
                "BMP becomes PNG; the same file once; WMF and paths outside the folder dropped");
        assertEquals("image/png", images.get(0).mimeType());
        assertEquals(800, ImageIO.read(new ByteArrayInputStream(images.get(0).bytes())).getWidth(), "shrunk to maxImageWidth");
        assertEquals(40, ImageIO.read(new ByteArrayInputStream(images.get(1).bytes())).getWidth(), "small ones kept");

        var html = doc.html();
        assertEquals(3, html.split("<img", -1).length - 1, "the two images (one used twice), nothing else: " + html);
        assertFalse(html.contains("passwd") || html.contains(".wmf") || html.contains("name=\"Image1\""), html);
        assertTrue(html.contains("width=\"600\""), "size attributes kept");

        var uploaded = doc.html(name -> "/files/editor/" + name);
        assertTrue(uploaded.contains("src=\"/files/editor/image-1.png\"") && uploaded.contains("src=\"/files/editor/image-2.png\""), uploaded);
        var embedded = doc.htmlWithEmbeddedImages();
        assertEquals(3, embedded.split("src=\"data:image/png;base64,", -1).length - 1, embedded.length() + "");
    }

    @Test
    void pdfAndOtherFormats() throws IOException {
        var hwp = Files.writeString(dir.resolve("계획.hwp"), "hwp");
        try (var pdf = S2OfficeConverter.toPdf(hwp); var doc = Loader.loadPDF(pdf.readAllBytes())) {
            assertEquals(1, doc.getNumberOfPages());
        }
        try (var pdf = S2OfficeConverter.toPdf("bytes".getBytes(StandardCharsets.UTF_8), "a.xlsx")) {
            assertTrue(pdf.readAllBytes().length > 0);
        }
        var docx = S2OfficeConverter.convert(hwp, "docx", dir.resolve("out"));
        assertEquals(dir.resolve("out/계획.docx"), docx);
        assertTrue(Files.isRegularFile(docx));
        var word = Files.writeString(dir.resolve("본문.docx"), "docx");
        var e = assertThrows(IOException.class, () -> S2OfficeConverter.convert(word, "odt", dir.resolve("out")));
        assertTrue(e.getMessage().contains("문서를 ODT로 변환하지 못했습니다: 본문.docx"), e.getMessage());
        // A plain LibreOffice failing on Hangul means the H2Orestart extension is missing | 일반 LibreOffice 의 한글 실패는 확장이 없다는 뜻
        var hangul = assertThrows(IOException.class, () -> S2OfficeConverter.convert(hwp, "odt", dir.resolve("out")));
        assertTrue(hangul.getMessage().contains("s2-office-converter 설치가 필요합니다"), hangul.getMessage());
        assertThrows(IllegalArgumentException.class, () -> S2OfficeConverter.toPdf(dir.resolve("a.zip")));
    }

    @Test
    void withoutAConverterTheMessageSaysWhatToInstall() throws IOException {
        S2OfficeConverter.setCommand(dir.resolve("missing").toString());
        assertFalse(S2OfficeConverter.isAvailable());
        var e = assertThrows(IOException.class, () -> S2OfficeConverter.toHtml(Files.writeString(dir.resolve("a.docx"), "x")));
        assertEquals("오피스·한글 문서를 변환하려면 s2-office-converter 설치가 필요합니다.", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> S2OfficeConverter.setParallelism(0));
        assertThrows(IllegalArgumentException.class, () -> HtmlOptions.create().maxImageWidth(-1));
    }

    @Test
    void realHtmlWhenAConverterIsInstalled() throws IOException {
        S2OfficeConverter.resetCommand();
        Assumptions.assumeTrue(S2OfficeConverter.isAvailable(), "no LibreOffice / s2-soffice installed");
        var rtf = Files.writeString(dir.resolve("note.rtf"), "{\\rtf1\\ansi\\uc0 {\\b Hello S2} \\u54620\\u44544\\par}");
        var doc = S2OfficeConverter.toHtml(rtf);
        assertTrue(doc.html().contains("Hello S2") && doc.html().contains("한글"), doc.html());
        assertTrue(doc.html().contains("<b>") || doc.html().contains("font-weight"), doc.html());
    }
}
