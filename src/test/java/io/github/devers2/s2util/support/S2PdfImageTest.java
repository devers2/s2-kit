package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2util.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2util.support.S2PdfUtil.PdfSource;

/**
 * Photos come out upright (EXIF orientation), and with {@code imageDpi} oversized images, also inside PDF sources,
 * are shrunk and cached.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 사진이 EXIF 방향대로 바로 나오고, {@code imageDpi} 를 주면 PDF 소스 안을 포함해 너무 큰 이미지를 줄이고 캐시하는지 확인합니다.
 */
class S2PdfImageTest {

    @TempDir
    Path dir;

    @AfterEach
    void tearDown() {
        S2PdfUtil.resetConversionCache();
    }

    /** Left half red, right half blue | 왼쪽 빨강, 오른쪽 파랑 */
    private static BufferedImage halves(int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    /** Inserts an EXIF APP1 segment with the orientation tag right after SOI | SOI 바로 뒤에 EXIF 방향 태그를 넣음 */
    private static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] app1 = {
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22, // marker, length 34 | 마커, 길이
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // big-endian TIFF, IFD at 8 | TIFF 헤더
                0x00, 0x01, // one entry | 항목 1개
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00 };
        var out = new byte[jpeg.length + app1.length];
        System.arraycopy(jpeg, 0, out, 0, 2);
        System.arraycopy(app1, 0, out, 2, app1.length);
        System.arraycopy(jpeg, 2, out, 2 + app1.length, jpeg.length - 2);
        return out;
    }

    private static PDDocument load(InputStream merged) throws IOException {
        try (merged) {
            return Loader.loadPDF(merged.readAllBytes());
        }
    }

    private static int[] bounds(PDDocument doc, Color color) throws IOException {
        var image = new PDFRenderer(doc).renderImageWithDPI(0, 36);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                var c = new Color(image.getRGB(x, y));
                if (Math.abs(c.getRed() - color.getRed()) < 60 && Math.abs(c.getGreen() - color.getGreen()) < 60
                        && Math.abs(c.getBlue() - color.getBlue()) < 60) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(maxX >= 0, "color drawn");
        return new int[] { minX, minY, maxX, maxY };
    }

    private static List<PDImageXObject> images(PDDocument doc) throws IOException {
        var images = new ArrayList<PDImageXObject>();
        for (var page : doc.getPages()) {
            for (var name : page.getResources().getXObjectNames()) {
                if (page.getResources().getXObject(name) instanceof PDImageXObject image) {
                    images.add(image);
                }
            }
        }
        return images;
    }

    // ------------------------------------------------------------ S2ImageUtil

    @Test
    void exifOrientationIsRead() throws IOException {
        var plain = jpeg(halves(40, 20));
        assertEquals(1, S2ImageUtil.exifOrientation(plain));
        for (int o = 1; o <= 8; o++) {
            assertEquals(o, S2ImageUtil.exifOrientation(withOrientation(plain, o)));
        }
        assertEquals(1, S2ImageUtil.exifOrientation(new byte[] { 1, 2, 3 }));
    }

    @Test
    void resizeToFitShrinksReadsSubsampledAndTurnsUpright() throws IOException {
        var big = jpeg(halves(4000, 3000));
        var fitted = ImageIO.read(new ByteArrayInputStream(S2ImageUtil.resizeToFit(big, 1000, 1000)));
        assertEquals(1000, fitted.getWidth());
        assertEquals(750, fitted.getHeight());

        var small = jpeg(halves(400, 200));
        assertSame(small, S2ImageUtil.resizeToFit(small, 1000, 1000), "nothing to change");

        // Stored sideways (EXIF 6): upright even when no shrinking is needed | 옆으로 저장(EXIF 6): 줄이지 않아도 바로 세움
        var upright = ImageIO.read(new ByteArrayInputStream(S2ImageUtil.resizeToFit(withOrientation(small, 6), 1000, 1000)));
        assertEquals(200, upright.getWidth());
        assertEquals(400, upright.getHeight());
        // Turned clockwise: the stored left (red) is now on top | 시계 방향: 저장된 왼쪽(빨강)이 위로
        assertEquals(Color.RED.getRGB() & 0xF0F0F0, upright.getRGB(100, 50) & 0xF0F0F0);
        assertThrows(IllegalArgumentException.class, () -> S2ImageUtil.resizeToFit(small, 0, 10));
    }

    @Test
    void existingResizeKeepsPortraitPhotosUpright() throws IOException {
        var file = dir.resolve("portrait.jpg");
        Files.write(file, withOrientation(jpeg(halves(400, 200)), 6));
        var result = S2ImageUtil.imageResize(file, 100, 100, true, dir.resolve("out").toString(), null, false);
        var image = ImageIO.read(result.getFile().toFile());
        assertEquals(50, image.getWidth());
        assertEquals(100, image.getHeight());
    }

    // --------------------------------------------------------------- merging

    @Test
    void photosAreUprightInThePdf() throws IOException {
        var stored = jpeg(halves(400, 200));
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofImage(stored)))) {
            var box = doc.getPage(0).getMediaBox();
            assertTrue(box.getWidth() > box.getHeight(), "landscape as stored");
            assertTrue(bounds(doc, Color.RED)[2] < bounds(doc, Color.BLUE)[0], "red left of blue");
        }
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofImage(withOrientation(stored, 6))))) {
            var box = doc.getPage(0).getMediaBox();
            assertTrue(box.getHeight() > box.getWidth(), "portrait page for a portrait photo");
            assertTrue(bounds(doc, Color.RED)[3] < bounds(doc, Color.BLUE)[1], "red above blue (turned clockwise)");
            assertEquals(400, images(doc).get(0).getWidth(), "the JPEG itself is not re-encoded");
        }
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofImage(withOrientation(stored, 8))))) {
            assertTrue(bounds(doc, Color.BLUE)[3] < bounds(doc, Color.RED)[1], "turned counter-clockwise: blue above red");
        }
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofImage(withOrientation(stored, 3))))) {
            assertTrue(bounds(doc, Color.BLUE)[2] < bounds(doc, Color.RED)[0], "upside down: blue left of red");
        }
    }

    @Test
    void imageDpiShrinksOversizedImagesOnly() throws IOException {
        var big = jpeg(halves(6000, 4000));
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofImage(big)), MergeOptions.create().imageDpi(150)))) {
            var image = images(doc).get(0);
            // A4 landscape content box 802pt at 150 dpi = 1671px | A4 가로 본문 802pt, 150dpi
            assertEquals(1671, image.getWidth(), 2);
        }
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofImage(big)))) {
            assertEquals(6000, images(doc).get(0).getWidth(), "kept without imageDpi");
        }
        var nearlyRight = jpeg(halves(1800, 1200));
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofImage(nearlyRight)), MergeOptions.create().imageDpi(150)))) {
            assertEquals(1800, images(doc).get(0).getWidth(), "within 1.2 times the target: left alone");
        }
        assertThrows(IllegalArgumentException.class, () -> MergeOptions.create().imageDpi(50));
    }

    /** A PDF with a large photo, a small one and a 1-bit image on one page | 큰 사진, 작은 사진, 1비트 이미지가 있는 PDF */
    private Path pdfWithImages() throws IOException {
        var path = dir.resolve("scan.pdf");
        try (var doc = new PDDocument()) {
            var page = new PDPage();
            doc.addPage(page);
            var large = JPEGFactory.createFromByteArray(doc, jpeg(halves(5000, 6000)));
            var small = JPEGFactory.createFromByteArray(doc, jpeg(halves(300, 200)));
            var oneBit = new BufferedImage(4000, 4000, BufferedImage.TYPE_BYTE_BINARY);
            var bits = LosslessFactory.createFromImage(doc, oneBit);
            try (var cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(large, 0, 200, 400, 480);
                cs.drawImage(small, 420, 600, 150, 100);
                cs.drawImage(bits, 420, 400, 100, 100);
            }
            doc.save(path.toFile());
        }
        return path;
    }

    @Test
    void imagesInsidePdfSourcesAreShrunk() throws IOException {
        var pdf = pdfWithImages();
        var before = Files.size(pdf);
        var out = dir.resolve("out.pdf");
        try (var merged = S2PdfUtil.merge(List.of(PdfSource.ofPdf(pdf)), MergeOptions.create().imageDpi(150))) {
            Files.copy(merged, out);
        }
        try (var doc = Loader.loadPDF(out.toFile())) {
            var widths = images(doc).stream().map(PDImageXObject::getWidth).sorted().toList();
            // Letter 612 x 792pt at 150 dpi = 1275 x 1650px; 5000 x 6000 fits as 1275 x 1530 | Letter 150dpi 한도 안에 맞춤
            assertEquals(3, widths.size());
            assertTrue(widths.contains(300), "small one untouched: " + widths);
            assertTrue(widths.contains(4000), "1-bit untouched: " + widths);
            assertTrue(widths.stream().anyMatch(w -> Math.abs(w - 1275) <= 2), "large one shrunk to 1275 x 1530: " + widths);
        }
        assertTrue(Files.size(out) < before / 2, "smaller file: " + before + " -> " + Files.size(out));
        assertTrue(Files.exists(pdf), "the source is not modified");
        try (var original = Loader.loadPDF(pdf.toFile())) {
            assertTrue(images(original).stream().anyMatch(i -> i.getWidth() == 5000));
        }
    }

    @Test
    void shrunkResultsAreCached() throws IOException {
        var cache = dir.resolve("cache");
        S2PdfUtil.setConversionCache(cache, 1L << 30, Duration.ofDays(1), 1);
        var options = MergeOptions.create().imageDpi(150).cache(true);
        var pdf = pdfWithImages();
        var photo = jpeg(halves(6000, 4000));
        for (int i = 0; i < 2; i++) {
            try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofPdf(pdf), PdfSource.ofImage(photo)), options))) {
                assertEquals(2, doc.getNumberOfPages());
            }
        }
        try (Stream<Path> walk = Files.walk(cache)) {
            assertEquals(2, walk.filter(p -> p.toString().endsWith(".pdf")).count(), "the shrunk PDF and the shrunk photo");
        }
        // Another dpi is another result | 다른 dpi 는 다른 결과
        S2PdfUtil.merge(List.of(PdfSource.ofImage(photo)), MergeOptions.create().imageDpi(200).cache(true)).close();
        try (Stream<Path> walk = Files.walk(cache)) {
            assertEquals(3, walk.filter(p -> p.toString().endsWith(".pdf")).count());
        }
    }
}
