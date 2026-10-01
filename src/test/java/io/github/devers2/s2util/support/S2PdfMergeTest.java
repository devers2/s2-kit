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
import java.util.Map;
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

    // ------------------------------------------------------------- watermark

    private static byte[] colored(String format, int width, int height, java.awt.Color color) throws IOException {
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** Bounds {x, y, width, height} of reddish pixels at 72 dpi (1pt = 1px, y from the top) | 붉은 픽셀 영역 */
    private static int[] redBounds(PDDocument doc, int pageIndex) throws IOException {
        var image = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(pageIndex, 72);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                var c = new java.awt.Color(image.getRGB(x, y));
                if (c.getRed() > 200 && c.getGreen() < 180 && c.getBlue() < 180 && c.getRed() - c.getGreen() > 60) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new int[] { minX, minY, maxX - minX + 1, maxY - minY + 1 };
    }

    private static void assertBounds(int[] expected, int[] actual) {
        assertNotNull(actual, "watermark drawn");
        for (int i = 0; i < 4; i++) {
            assertEquals(expected[i], actual[i], 2, "x, y, width, height = " + java.util.Arrays.toString(actual));
        }
    }

    private PDDocument watermarked(S2PdfUtil.Watermark watermark) throws IOException {
        return load(S2PdfUtil.merge(List.of(PdfSource.ofText(" ")), MergeOptions.create().watermark(watermark)));
    }

    @Test
    void watermarkSizeKeepsTheRatioWhenOneSideIsGiven() throws IOException {
        var red = colored("png", 100, 50, java.awt.Color.RED);
        var position = S2PdfUtil.Watermark.Position.TOP_LEFT;
        try (var doc = watermarked(S2PdfUtil.Watermark.of(red).width(200).position(position).offset(20, 30))) {
            assertBounds(new int[] { 20, 30, 200, 100 }, redBounds(doc, 0));
        }
        try (var doc = watermarked(S2PdfUtil.Watermark.of(red).height(40).position(position).offset(20, 30))) {
            assertBounds(new int[] { 20, 30, 80, 40 }, redBounds(doc, 0));
        }
        try (var doc = watermarked(S2PdfUtil.Watermark.of(red).size(60, 90).position(position).offset(20, 30))) {
            assertBounds(new int[] { 20, 30, 60, 90 }, redBounds(doc, 0));
        }
        try (var doc = watermarked(S2PdfUtil.Watermark.of(red).position(position))) {
            assertBounds(new int[] { 0, 0, 100, 50 }, redBounds(doc, 0));
        }
    }

    @Test
    void watermarkPositionsAndOffsets() throws IOException {
        var red = colored("jpg", 60, 60, java.awt.Color.RED);
        // A4 595 x 842 | A4 595 x 842
        var cases = Map.of(
                S2PdfUtil.Watermark.Position.CENTER, new int[] { (595 - 60) / 2 + 10, (842 - 60) / 2 + 20 },
                S2PdfUtil.Watermark.Position.TOP, new int[] { (595 - 60) / 2 + 10, 20 },
                S2PdfUtil.Watermark.Position.TOP_RIGHT, new int[] { 595 - 60 - 10, 20 },
                S2PdfUtil.Watermark.Position.RIGHT, new int[] { 595 - 60 - 10, (842 - 60) / 2 + 20 },
                S2PdfUtil.Watermark.Position.BOTTOM_RIGHT, new int[] { 595 - 60 - 10, 842 - 60 - 20 },
                S2PdfUtil.Watermark.Position.BOTTOM, new int[] { (595 - 60) / 2 + 10, 842 - 60 - 20 },
                S2PdfUtil.Watermark.Position.BOTTOM_LEFT, new int[] { 10, 842 - 60 - 20 },
                S2PdfUtil.Watermark.Position.LEFT, new int[] { 10, (842 - 60) / 2 + 20 });
        for (var entry : cases.entrySet()) {
            try (var doc = watermarked(S2PdfUtil.Watermark.of(red).size(60, 60).position(entry.getKey()).offset(10, 20))) {
                var xy = entry.getValue();
                assertBounds(new int[] { xy[0], xy[1], 60, 60 }, redBounds(doc, 0));
            }
        }
    }

    @Test
    void watermarkIsOnEveryPageUnderThePageNumbersAndCanBeTranslucent() throws IOException {
        var red = colored("png", 40, 40, java.awt.Color.RED);
        var options = MergeOptions.create().pageNumbers(true)
                .watermark(S2PdfUtil.Watermark.of(red).size(40, 40).opacity(0.5f));
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofText("a"), PdfSource.ofPdf(chapterPdf())), options))) {
            assertEquals(3, doc.getNumberOfPages());
            for (int i = 0; i < 3; i++) {
                assertNotNull(redBounds(doc, i), "page " + (i + 1));
            }
            var image = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0, 72);
            var middle = new java.awt.Color(image.getRGB(595 / 2, 842 / 2));
            assertEquals(255, middle.getRed(), 3);
            assertEquals(128, middle.getGreen(), 10, "half transparent red on white");
            assertTrue(new PDFTextStripper().getText(doc).contains("3 / 3"));
        }
    }

    /** Bounds of pixels close to a color at 72 dpi | 한 색에 가까운 픽셀 영역 */
    private static int[] colorBounds(PDDocument doc, int pageIndex, java.awt.Color color) throws IOException {
        var image = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(pageIndex, 72);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                var c = new java.awt.Color(image.getRGB(x, y));
                if (Math.abs(c.getRed() - color.getRed()) < 40 && Math.abs(c.getGreen() - color.getGreen()) < 40
                        && Math.abs(c.getBlue() - color.getBlue()) < 40) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new int[] { minX, minY, maxX - minX + 1, maxY - minY + 1 };
    }

    @Test
    void watermarkFollowsRotatedPages() throws IOException {
        // Left half red, right half blue: shows the watermark is upright | 왼쪽 빨강, 오른쪽 파랑: 바로 섰는지 확인
        var img = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, 30, 40);
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(30, 0, 30, 40);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);

        var pdf = dir.resolve("rotated.pdf");
        try (var doc = new PDDocument()) {
            for (var rotation : new int[] { 0, 90, 180, 270 }) {
                var page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(50, 20, 595, 842));
                page.setRotation(rotation);
                doc.addPage(page);
            }
            doc.save(pdf.toFile());
        }
        var watermark = S2PdfUtil.Watermark.of(out.toByteArray()).size(60, 40)
                .position(S2PdfUtil.Watermark.Position.TOP_LEFT).offset(20, 30);
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofPdf(pdf)), MergeOptions.create().watermark(watermark)))) {
            for (int i = 0; i < 4; i++) {
                var red = colorBounds(doc, i, java.awt.Color.RED);
                var blue = colorBounds(doc, i, java.awt.Color.BLUE);
                var label = "rotation " + (i * 90);
                assertNotNull(red, label);
                assertNotNull(blue, label);
                assertEquals(20, red[0], 2, label + " x");
                assertEquals(30, red[1], 2, label + " y");
                assertEquals(30, red[2], 2, label + " width");
                assertEquals(40, red[3], 2, label + " height");
                assertEquals(50, blue[0], 2, label + " blue on the right");
                assertEquals(30, blue[1], 2, label + " blue y");
            }
        }
    }

    @Test
    void pageNumbersFollowRotatedPages() throws IOException {
        var pdf = dir.resolve("rotated-numbers.pdf");
        try (var doc = new PDDocument()) {
            for (var rotation : new int[] { 0, 90, 180, 270 }) {
                var page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(50, 20, 595, 842));
                page.setRotation(rotation);
                doc.addPage(page);
            }
            doc.save(pdf.toFile());
        }
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofPdf(pdf)),
                MergeOptions.create().pageNumberStyle("%d / %d", 20)))) {
            for (int i = 0; i < 4; i++) {
                var label = "rotation " + (i * 90);
                var shownWidth = i % 2 == 0 ? 595 : 842;
                var shownHeight = i % 2 == 0 ? 842 : 595;
                var text = colorBounds(doc, i, java.awt.Color.BLACK);
                assertNotNull(text, label);
                assertEquals(shownWidth / 2.0, text[0] + text[2] / 2.0, 3, label + " centered");
                assertEquals(shownHeight - 20, text[1] + text[3], 4, label + " on the shown bottom");
                assertTrue(text[2] > text[3], label + " upright (wider than tall): " + java.util.Arrays.toString(text));
            }
        }
    }

    @Test
    void watermarkInputIsChecked() throws IOException {
        var red = colored("png", 10, 10, java.awt.Color.RED);
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.Watermark.of(red).width(0));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.Watermark.of(red).size(10, -1));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.Watermark.of(red).opacity(1.5f));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.Watermark.of(new byte[0]));
        var e = assertThrows(IOException.class, () -> watermarked(S2PdfUtil.Watermark.of("not an image".getBytes())));
        assertTrue(e.getMessage().contains("워터마크"), e.getMessage());
        var file = dir.resolve("mark.png");
        Files.write(file, red);
        try (var doc = watermarked(S2PdfUtil.Watermark.of(file));
                var doc2 = watermarked(S2PdfUtil.Watermark.of(Files.newInputStream(file)))) {
            assertNotNull(redBounds(doc, 0));
            assertNotNull(redBounds(doc2, 0));
        }
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
