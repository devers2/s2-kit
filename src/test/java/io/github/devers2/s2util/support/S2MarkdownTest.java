package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import io.github.devers2.s2util.support.S2PdfUtil.PdfSource;

/**
 * Markdown to HTML (safe by default) and Markdown sources in merges.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 마크다운 → HTML 변환(기본값으로 안전)과 병합의 마크다운 소스를 확인합니다.
 */
class S2MarkdownTest {

    @TempDir
    Path dir;

    private static final String SAMPLE = """
            # 실험 결과

            ## 요약
            **굵게**, *기울임*, ~~취소~~, `코드`, https://example.com

            | 항목 | 값 |
            |---|---:|
            | A | 1 |

            - [x] 완료한 일
            - [ ] 남은 일

            > 인용

            ```java
            var a = 1;
            ```
            """;

    @Test
    void markdownBecomesHtml() {
        assertTrue(S2MarkdownUtil.isAvailable());
        var html = S2MarkdownUtil.toHtml(SAMPLE);
        assertTrue(html.contains("<h1 id=\"실험-결과\">실험 결과</h1>"), html);
        assertTrue(html.contains("<table>") && html.contains("<td align=\"right\">1</td>"), html);
        assertTrue(html.contains("<del>취소</del>"), html);
        assertTrue(html.contains("href=\"https://example.com\">https://example.com</a>"), html);
        assertTrue(html.contains("rel=\"nofollow\""), "links in user content are not followed by crawlers");
        assertTrue(html.contains("type=\"checkbox\"") && html.contains("checked"), html);
        assertTrue(html.contains("<blockquote>") && html.contains("<code class=\"language-java\">"), html);
    }

    @Test
    void htmlAndDangerousLinksAreNeutralized() {
        var html = S2MarkdownUtil.toHtml("<script>alert(1)</script>\n\n[click](javascript:alert(1)) [data](data:text/html,x) "
                + "[ok](https://ok.example) [rel](docs/a.md) [top](#top) ![img](javascript:x)");
        assertFalse(html.contains("<script>"), html);
        assertTrue(html.contains("&lt;script&gt;"), html);
        assertFalse(html.contains("javascript:"), html);
        assertFalse(html.contains("data:text/html"), html);
        assertTrue(html.contains("href=\"https://ok.example\"") && html.contains("href=\"docs/a.md\"")
                && html.contains("href=\"#top\""), html);
        // Trusted documents may keep their HTML | 믿을 수 있는 문서는 HTML 유지 가능
        assertTrue(S2MarkdownUtil.toHtml("<b>bold</b>", true).contains("<b>bold</b>"));

        assertTrue(S2MarkdownUtil.isSafeLink("a/b:c"));
        assertTrue(S2MarkdownUtil.isSafeLink("MAILTO:x@y"));
        assertFalse(S2MarkdownUtil.isSafeLink(" JavaScript:alert(1)"));
        assertFalse(S2MarkdownUtil.isSafeLink("vbscript:x"));
    }

    private static PDDocument load(InputStream merged) throws IOException {
        try (merged) {
            return Loader.loadPDF(merged.readAllBytes());
        }
    }

    private static List<PDImageXObject> images(PDDocument doc) throws IOException {
        var images = new java.util.ArrayList<PDImageXObject>();
        for (var page : doc.getPages()) {
            for (var name : page.getResources().getXObjectNames()) {
                if (page.getResources().getXObject(name) instanceof PDImageXObject image) {
                    images.add(image);
                }
            }
        }
        return images;
    }

    private static byte[] png(int size) throws IOException {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, size, size);
        g.dispose();
        var out = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void markdownSourcesAreMerged() throws IOException {
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofText("cover"), PdfSource.ofMarkdown(SAMPLE))))) {
            assertEquals(2, doc.getNumberOfPages());
            var stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            var text = stripper.getText(doc);
            for (var expected : List.of("실험 결과", "요약", "항목", "완료한 일", "인용", "var a = 1;")) {
                assertTrue(text.contains(expected), expected + " in " + text);
            }
            assertTrue(text.contains("■") && text.contains("□"), "checkboxes as characters: " + text);
            assertFalse(text.contains("**"), "rendered, not printed as source");
        }
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofMarkdown(new ByteArrayInputStream("# 스트림".getBytes(StandardCharsets.UTF_8)))))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("스트림"));
        }
    }

    @Test
    void imagesComeOnlyFromTheMarkdownFolder() throws IOException {
        var notes = Files.createDirectories(dir.resolve("notes"));
        Files.createDirectories(notes.resolve("img"));
        Files.write(notes.resolve("img/chart.png"), png(30));
        Files.write(dir.resolve("secret.png"), png(40)); // outside the folder | 폴더 밖
        var linkOut = notes.resolve("img/link.png");
        try {
            Files.createSymbolicLink(linkOut, dir.resolve("secret.png"));
        } catch (IOException | UnsupportedOperationException e) {
            linkOut = null;
        }
        var hits = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            var md = notes.resolve("note.md");
            Files.writeString(md, "# 노트\n\n![chart](img/chart.png)\n\n![escape](../secret.png)\n\n![link](img/link.png)\n\n"
                    + "![remote](http://127.0.0.1:" + server.getAddress().getPort() + "/x.png)\n");
            try (var doc = load(S2PdfUtil.merge(PdfSource.ofMarkdown(md)))) {
                var widths = images(doc).stream().map(PDImageXObject::getWidth).toList();
                assertEquals(List.of(30), widths, "only the image inside the folder: " + widths);
            }
            assertEquals(0, hits.get(), "remote images are not fetched");
            // A string without a folder embeds no relative images | 폴더 없는 문자열은 상대 경로 이미지를 넣지 않음
            try (var doc = load(S2PdfUtil.merge(PdfSource.ofMarkdown("![chart](img/chart.png)")))) {
                assertEquals(List.of(), images(doc));
            }
            try (var doc = load(S2PdfUtil.merge(PdfSource.ofMarkdown("![chart](img/chart.png)", notes)))) {
                assertEquals(1, images(doc).size());
            }
        } finally {
            server.stop(0);
        }
    }
}
