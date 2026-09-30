package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import io.github.devers2.s2util.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2util.support.S2PdfUtil.PdfSource;

/**
 * Merge quality: Korean text, image formats and limits, error context, bookmarks, page numbers and metadata.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 병합 품질을 확인합니다: 한글, 이미지 형식과 한도, 오류 위치, 책갈피, 쪽 번호, 문서 정보.
 */
class S2PdfMergeTest {

    @TempDir
    Path dir;

    private static PDDocument load(InputStream merged) throws IOException {
        try (merged) {
            return Loader.loadPDF(merged.readAllBytes());
        }
    }

    private static String text(PDDocument doc) throws IOException {
        return new PDFTextStripper().getText(doc);
    }

    private static boolean koreanFontInstalled() {
        return S2PdfUtil.SYSTEM_FONT_CANDIDATES.stream().anyMatch(p -> Files.isReadable(Path.of(p)));
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        var out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out), format);
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ fonts

    @Test
    void koreanTextIsRenderedWithAnInstalledFont() throws IOException {
        Assumptions.assumeTrue(koreanFontInstalled(), "no Korean font installed");
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofText("Hello 한글 텍스트"),
                PdfSource.ofHtml("<p>HTML 한글</p><pre>코드 속 한글</pre>")))) {
            var text = text(doc);
            assertTrue(text.contains("한글 텍스트") && text.contains("HTML 한글") && text.contains("코드 속 한글"), text);
            assertFalse(text.contains("##"), text);
        }
    }

    @Test
    void koreanWithoutAFontFailsInsteadOfPrintingHashes() throws IOException {
        var e = assertThrows(IOException.class, () -> S2PdfUtil.requireFontFor("<p>한글</p>", false));
        assertTrue(e.getMessage().contains("setDefaultFont"), e.getMessage());
        S2PdfUtil.requireFontFor("<p>English only</p>", false);
        S2PdfUtil.requireFontFor("<p>한글</p>", true);
    }

    @Test
    void aMissingFontFileIsReported() {
        var e = assertThrows(IOException.class,
                () -> S2PdfUtil.merge(PdfSource.ofHtml("<p>x</p>", null, null, "/fonts/missing.ttf", S2PdfUtil.class)));
        assertTrue(e.getMessage().contains("#1 (HTML)") && e.getMessage().contains("/fonts/missing.ttf"), e.getMessage());
        assertThrows(IOException.class, () -> S2PdfUtil.setDefaultFont(dir.resolve("missing.ttf")));
    }

    @Test
    void defaultFontCanBeSetAndReset() throws IOException {
        var candidate = S2PdfUtil.SYSTEM_FONT_CANDIDATES.stream().map(Path::of).filter(Files::isReadable).findFirst();
        Assumptions.assumeTrue(candidate.isPresent(), "no Korean font installed");
        try {
            S2PdfUtil.setDefaultFont(candidate.get());
            try (var doc = load(S2PdfUtil.merge(PdfSource.ofText("기본 폰트")))) {
                assertTrue(text(doc).contains("기본 폰트"));
            }
        } finally {
            S2PdfUtil.resetDefaultFont();
        }
    }

    // ----------------------------------------------------------------- images

    @Test
    void jpegIsEmbeddedWithoutReencoding() throws IOException {
        var jpeg = image("jpg", 300, 200);
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofImage(jpeg)))) {
            var resources = doc.getPage(0).getResources();
            var name = resources.getXObjectNames().iterator().next();
            var xobject = (PDImageXObject) resources.getXObject(name);
            assertEquals(COSName.DCT_DECODE, xobject.getCOSObject().getCOSName(COSName.FILTER));
            assertEquals(300, xobject.getWidth());
            // Wider than tall: landscape A4 | 가로가 길면 가로 A4
            assertTrue(doc.getPage(0).getMediaBox().getWidth() > doc.getPage(0).getMediaBox().getHeight());
        }
    }

    @Test
    void pngAndWebpBecomeLosslessImages() throws IOException {
        var webp = java.util.Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofImage(image("png", 20, 40)), PdfSource.ofImage(webp)))) {
            assertEquals(2, doc.getNumberOfPages());
        }
    }

    @Test
    void hugeImagesAreRefusedBeforeDecoding() {
        var ihdr = ByteBuffer.allocate(17).put("IHDR".getBytes()).putInt(50_000).putInt(50_000)
                .put(new byte[] { 8, 2, 0, 0, 0 }).array();
        var crc = new CRC32();
        crc.update(ihdr);
        var png = ByteBuffer.allocate(8 + 4 + 17 + 4)
                .put(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a }).putInt(13).put(ihdr)
                .putInt((int) crc.getValue()).array();
        var e = assertThrows(IOException.class, () -> S2PdfUtil.merge(PdfSource.ofImage(png)));
        assertTrue(e.getMessage().contains("50000x50000"), e.getMessage());
    }

    // ------------------------------------------------------ limits and errors

    @Test
    void failuresNameTheSource() throws IOException {
        var e = assertThrows(IOException.class, () -> S2PdfUtil.merge(PdfSource.ofText("ok"),
                PdfSource.ofImage("not an image".getBytes()), PdfSource.ofText("never reached")));
        assertTrue(e.getMessage().startsWith("병합 소스 #2 (IMAGE)"), e.getMessage());
        assertNotNull(e.getCause());
    }

    @Test
    void downloadsAreLimited() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/big.txt", exchange -> {
            var body = "x".repeat(5000).getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/big.txt";
            var e = assertThrows(IOException.class, () -> S2PdfUtil.merge(PdfSource.ofUrl(url).maxBytes(1000)));
            assertTrue(e.getMessage().startsWith("병합 소스 #1 (URL " + url + ")"), e.getMessage());
            assertTrue(e.getMessage().contains("1000"), e.getMessage());
            try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(url).maxBytes(10_000)))) {
                assertTrue(doc.getNumberOfPages() >= 1);
            }
        } finally {
            server.stop(0);
        }
    }

    // ------------------------------------------- bookmarks, numbers, metadata

    /** A two-page PDF with its own bookmark | 자체 책갈피가 있는 2쪽 PDF */
    private Path chapterPdf() throws IOException {
        var path = dir.resolve("chapters.pdf");
        try (var doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            var outline = new PDDocumentOutline();
            var chapter = new PDOutlineItem();
            chapter.setTitle("Chapter 2");
            chapter.setDestination(doc.getPage(1));
            outline.addLast(chapter);
            doc.getDocumentCatalog().setDocumentOutline(outline);
            doc.save(path.toFile());
        }
        return path;
    }

    private static List<String> titles(Iterable<PDOutlineItem> items) {
        var titles = new ArrayList<String>();
        items.forEach(item -> titles.add(item.getTitle()));
        return titles;
    }

    @Test
    void bookmarksPointAtEachSourceAndKeepExistingOnes() throws IOException {
        var options = MergeOptions.create().bookmarks(true);
        var sources = List.of(PdfSource.ofPdf(chapterPdf()), PdfSource.ofText("appendix").title("Appendix"),
                PdfSource.ofText("third"));
        try (var doc = load(S2PdfUtil.merge(sources, options))) {
            var outline = doc.getDocumentCatalog().getDocumentOutline();
            assertEquals(List.of("chapters.pdf", "Appendix", "문서 3"), titles(outline.children()));

            var first = outline.getFirstChild();
            assertEquals(List.of("Chapter 2"), titles(first.children()), "existing bookmark kept under its source");
            var appendix = first.getNextSibling();
            var target = ((PDPageDestination) appendix.getDestination()).getPage();
            assertEquals(2, doc.getPages().indexOf(target), "second source starts after the two chapter pages");
        }
        assertTrue(Files.exists(dir.resolve("chapters.pdf")));
        try (var original = Loader.loadPDF(dir.resolve("chapters.pdf").toFile())) {
            assertEquals(List.of("Chapter 2"), titles(original.getDocumentCatalog().getDocumentOutline().children()),
                    "the source file is not modified");
        }
    }

    @Test
    void pageNumbersAndMetadata() throws IOException {
        var options = MergeOptions.create().pageNumbers(true).title("보고서").author("s2");
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofPdf(chapterPdf()), PdfSource.ofText("last")), options))) {
            assertEquals(3, doc.getNumberOfPages());
            var text = text(doc);
            assertTrue(text.contains("1 / 3") && text.contains("2 / 3") && text.contains("3 / 3"), text);
            assertEquals("보고서", doc.getDocumentInformation().getTitle());
            assertEquals("s2", doc.getDocumentInformation().getAuthor());
        }
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofText("a")),
                MergeOptions.create().pageNumberStyle("- %d -", 12)))) {
            assertTrue(text(doc).contains("- 1 -"));
        }
        assertThrows(java.util.IllegalFormatException.class, () -> MergeOptions.create().pageNumberStyle("%d %s %q", 10));
    }

    private static String pageText(PDDocument doc, int page) throws IOException {
        var stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(doc);
    }

    @Test
    void pageNumbersCanSkipFirstAndLastPages() throws IOException {
        // cover (1) + chapters (2) + body (1) + back cover (1) = 5 pages | 표지 1 + 본문 2 + 본문 1 + 뒤표지 1 = 5쪽
        var sources = List.of(PdfSource.ofText("cover"), PdfSource.ofPdf(chapterPdf()), PdfSource.ofText("body"),
                PdfSource.ofText("back"));
        try (var doc = load(S2PdfUtil.merge(sources, MergeOptions.create().pageNumbers(1, 1)))) {
            assertEquals(5, doc.getNumberOfPages());
            assertFalse(pageText(doc, 1).contains(" / "), "cover has no number");
            assertTrue(pageText(doc, 2).contains("1 / 3"), pageText(doc, 2));
            assertTrue(pageText(doc, 3).contains("2 / 3"));
            assertTrue(pageText(doc, 4).contains("3 / 3"));
            assertFalse(pageText(doc, 5).contains(" / "), "back cover has no number");
        }
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofText("a"), PdfSource.ofText("b")),
                MergeOptions.create().pageNumbers(1, 0).pageNumberStyle("- %d -", 10)))) {
            assertTrue(pageText(doc, 2).contains("- 1 -"), "style keeps the skipped pages");
        }
        assertThrows(IllegalArgumentException.class, () -> MergeOptions.create().pageNumbers(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.merge(
                List.of(PdfSource.ofText("only")), MergeOptions.create().pageNumbers(1, 0)));
    }

    @Test
    void addPageNumbersFailsLoudly() throws IOException {
        var pdf = chapterPdf();
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.addPageNumbers(pdf, 5, 10, null));
        try (var doc = load(S2PdfUtil.addPageNumbers(pdf, 2, 10, null))) {
            assertTrue(text(doc).contains("2 / 2"));
        }
        assertThrows(IOException.class, () -> S2PdfUtil.addPageNumbers(dir.resolve("missing.pdf"), 1, 10, null));
    }
}
