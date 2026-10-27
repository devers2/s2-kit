package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2kit.support.S2PdfUtil.ImageStamp;
import io.github.devers2.s2kit.support.S2PdfUtil.PageNumber;
import io.github.devers2.s2kit.support.S2PdfUtil.PageSelector;
import io.github.devers2.s2kit.support.S2PdfUtil.PdfSource;
import io.github.devers2.s2kit.support.S2PdfUtil.Position;
import io.github.devers2.s2kit.support.S2PdfUtil.QrStamp;
import io.github.devers2.s2kit.support.S2PdfUtil.StampOptions;
import io.github.devers2.s2kit.support.S2PdfUtil.Text;
import io.github.devers2.s2kit.support.S2PdfUtil.Watermark;

class S2PdfStampTest {

    @TempDir
    Path dir;

    private Path multiPagePdf(int pages) throws IOException {
        var sources = new java.util.ArrayList<PdfSource>();
        for (int i = 1; i <= pages; i++) {
            sources.add(PdfSource.ofText("Page content " + i));
        }
        var pdfPath = dir.resolve("multi_" + pages + ".pdf");
        try (var is = S2PdfUtil.merge(sources)) {
            Files.copy(is, pdfPath);
        }
        return pdfPath;
    }

    private static String text(PDDocument doc) throws IOException {
        return new PDFTextStripper().getText(doc);
    }

    private static String pageText(PDDocument doc, int pageNumber) throws IOException {
        var stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(doc);
    }

    @Test
    void testBasicStampTextAndPageNumber() throws IOException {
        var pdf = multiPagePdf(3);

        var options = StampOptions.builder()
                .addText(Text.of("Header Title")
                        .position(Position.TOP_CENTER)
                        .offset(0, 20)
                        .fontSize(12))
                .pageNumber(PageNumber.of("%d / %d")
                        .position(Position.BOTTOM_CENTER)
                        .offset(0, 20)
                        .fontSize(10))
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(3, doc.getNumberOfPages());
            for (int i = 1; i <= 3; i++) {
                var content = pageText(doc, i);
                assertTrue(content.contains("Header Title"), "Page " + i + " should contain Header Title");
                assertTrue(content.contains(i + " / 3"), "Page " + i + " should contain page number " + i + " / 3");
            }
        }
    }

    @Test
    void testDynamicTitleProvider() throws IOException {
        var pdf = multiPagePdf(3);

        var options = StampOptions.builder()
                .addText(Text.of(pageNo -> "Dynamic Note " + pageNo)
                        .position(Position.TOP_CENTER)
                        .offset(0, 15)
                        .fontSize(11))
                .pageNumber(PageNumber.of())
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(3, doc.getNumberOfPages());
            assertTrue(pageText(doc, 1).contains("Dynamic Note 1"));
            assertTrue(pageText(doc, 2).contains("Dynamic Note 2"));
            assertTrue(pageText(doc, 3).contains("Dynamic Note 3"));
        }
    }

    @Test
    void testNumberOfPagesToApplySkipsCover() throws IOException {
        var pdf = multiPagePdf(3);

        // Apply only to last 2 pages (skips page 1)
        var options = StampOptions.builder()
                .numberOfPagesToApply(2)
                .addText(Text.of("Body Note Title")
                        .position(Position.TOP_CENTER)
                        .offset(0, 20)
                        .fontSize(11))
                .pageNumber(PageNumber.of("%d / %d")
                        .position(Position.BOTTOM_CENTER)
                        .offset(0, 20)
                        .fontSize(10))
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(3, doc.getNumberOfPages());

            // Page 1 is skipped
            var p1 = pageText(doc, 1);
            assertFalse(p1.contains("Body Note Title"));
            assertFalse(p1.contains("1 / 2"));

            // Page 2 is 1st stamped page
            var p2 = pageText(doc, 2);
            assertTrue(p2.contains("Body Note Title"));
            assertTrue(p2.contains("1 / 2"));

            // Page 3 is 2nd stamped page
            var p3 = pageText(doc, 3);
            assertTrue(p3.contains("Body Note Title"));
            assertTrue(p3.contains("2 / 2"));
        }
    }

    @Test
    void testInvalidNumberOfPagesToApplyThrows() throws IOException {
        var pdf = multiPagePdf(2);

        var options = StampOptions.builder()
                .numberOfPagesToApply(5)
                .pageNumber(PageNumber.of())
                .build();

        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.stamp(pdf, options));
    }

    @Test
    void testTextOptionsConfiguration() {
        var text = Text.of("Confidential")
                .position(Position.CENTER)
                .offset(10, 20)
                .fontSize(24)
                .color(Color.RED)
                .color(255, 0, 0)
                .opacity(0.5f)
                .rotation(45f)
                .repeat(true, 120f, 120f);

        assertEquals(Position.CENTER, text.getPosition());
        assertEquals(10f, text.getOffsetX());
        assertEquals(20f, text.getOffsetY());
        assertEquals(24f, text.getFontSize());
        assertEquals(Color.RED, text.getColor());
        assertEquals(0.5f, text.getOpacity());
        assertEquals(45f, text.getRotation());
        assertTrue(text.isRepeat());
        assertEquals(120f, text.getRepeatGapX());
        assertEquals(120f, text.getRepeatGapY());
        assertEquals("Confidential", text.resolveText(1));
    }

    @Test
    void testTextAndPageNumberThicknessAndBold() {
        var t1 = Text.of("Bold Title").bold();
        assertTrue(t1.isBold());
        assertEquals(0f, t1.getThickness());

        var t2 = Text.of("Thick Title").thickness(0.75f);
        assertTrue(t2.isBold());
        assertEquals(0.75f, t2.getThickness());
        assertEquals(0.75f, t2.getStrokeWidth());

        var p = PageNumber.of().thickness(0.4f);
        assertTrue(p.isBold());
        assertEquals(0.4f, p.getThickness());
    }

    @Test
    void testStampWithTextThicknessAndBold() throws IOException {
        var pdf = multiPagePdf(2);

        var options = StampOptions.builder()
                .addText(Text.of("Bold Header")
                        .position(Position.TOP_CENTER)
                        .fontSize(12)
                        .bold())
                .addText(Text.of("Thick Watermark")
                        .position(Position.CENTER)
                        .fontSize(20)
                        .thickness(0.5f)
                        .color(Color.BLUE)
                        .rotation(30f))
                .pageNumber(PageNumber.of("%d / %d")
                        .bold())
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(2, doc.getNumberOfPages());
            for (int i = 1; i <= 2; i++) {
                var content = pageText(doc, i);
                var noSpaces = content.replaceAll("\\s+", "");
                assertTrue(noSpaces.contains("BoldHeader"));
                assertTrue(noSpaces.contains("ThickWatermark"));
                assertTrue(noSpaces.contains(i + "/2"));
            }
        }
    }

    @Test
    void testWatermarkInStampOptions() throws IOException {
        var pdf = multiPagePdf(1);

        var img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        var baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        var mark = Watermark.of(baos.toByteArray())
                .size(60, 60)
                .position(Position.BOTTOM_RIGHT)
                .offset(10, 10)
                .opacity(0.8f);

        var options = StampOptions.builder()
                .watermark(mark)
                .pageNumber(PageNumber.of())
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(1, doc.getNumberOfPages());
            assertTrue(text(doc).contains("1 / 1"));
        }
    }

    @Test
    void testAddPageNumbersCompatibility() throws IOException {
        var pdf = multiPagePdf(2);

        try (var is = S2PdfUtil.addPageNumbers(pdf, 2, 10, (java.io.File) null);
                var doc = Loader.loadPDF(is.readAllBytes())) {
            assertEquals(2, doc.getNumberOfPages());
            assertTrue(pageText(doc, 1).contains("1 / 2"));
            assertTrue(pageText(doc, 2).contains("2 / 2"));
        }
    }

    @Test
    void testPageNumberBottomDirectionsAndDistinctStyles() throws IOException {
        var pdf = multiPagePdf(3);

        var pn = PageNumber.of()
                .bottomRight(25, 25)
                .currentPageFontSize(12)
                .currentPageBold()
                .currentPageColor(Color.RED)
                .separator(" / ")
                .separatorColor(Color.GRAY)
                .totalPageFontSize(10)
                .totalPageColor(Color.DARK_GRAY);

        assertTrue(pn.hasDistinctStyles());
        assertEquals(Position.BOTTOM_RIGHT, pn.getPosition());
        assertEquals(25f, pn.getOffsetX());
        assertEquals(25f, pn.getOffsetY());
        assertEquals(12f, pn.getCurrentPageFontSize());
        assertTrue(pn.isCurrentPageBold());
        assertEquals(Color.RED, pn.getCurrentPageColor());
        assertEquals(10f, pn.getTotalPageFontSize());
        assertEquals(Color.DARK_GRAY, pn.getTotalPageColor());

        var options = StampOptions.builder()
                .pageNumber(pn)
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(3, doc.getNumberOfPages());
            for (int i = 1; i <= 3; i++) {
                var content = pageText(doc, i);
                var noSpaces = content.replaceAll("\\s+", "");
                assertTrue(noSpaces.contains(i + "/3"), "Page " + i + " should contain " + i + "/3");
            }
        }
    }

    @Test
    void testTextWithPageTextProviderAndBottomDirections() throws IOException {
        var pdf = multiPagePdf(2);

        var textStamp = Text.of((n, m) -> "Page " + n + " of " + m)
                .bottomCenter(0, 30)
                .bold()
                .fontSize(11)
                .color(Color.BLUE);

        assertEquals(Position.BOTTOM_CENTER, textStamp.getPosition());
        assertEquals(0f, textStamp.getOffsetX());
        assertEquals(30f, textStamp.getOffsetY());
        assertTrue(textStamp.isBold());
        assertEquals("Page 1 of 5", textStamp.resolveText(1, 5));

        var leftHeader = Text.of("Goono ELN")
                .bottomLeft(20, 30)
                .fontSize(10);
        assertEquals(Position.BOTTOM_LEFT, leftHeader.getPosition());

        var rightFooter = Text.of("CONFIDENTIAL")
                .bottomRight(20, 30)
                .fontSize(10);
        assertEquals(Position.BOTTOM_RIGHT, rightFooter.getPosition());

        var options = StampOptions.builder()
                .addText(textStamp)
                .addText(leftHeader)
                .addText(rightFooter)
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(2, doc.getNumberOfPages());
            for (int i = 1; i <= 2; i++) {
                var content = pageText(doc, i);
                assertTrue(content.contains("Page " + i + " of 2"));
                assertTrue(content.contains("Goono ELN"));
                assertTrue(content.contains("CONFIDENTIAL"));
            }
        }
    }

    @Test
    void testPageNumberPrefixSuffixAndSinglePageMode() throws IOException {
        var pdf = multiPagePdf(2);

        var pn = PageNumber.of()
                .bottomCenter()
                .prefix("[ ")
                .separator(" / ")
                .suffix(" ]")
                .currentPageBold()
                .currentPageFontSize(11)
                .totalPageFontSize(10);

        var options = StampOptions.builder()
                .pageNumber(pn)
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(2, doc.getNumberOfPages());
            var p1 = pageText(doc, 1).replaceAll("\\s+", "");
            assertTrue(p1.contains("[1/2]"), "Should contain [1/2], got: " + p1);
            var p2 = pageText(doc, 2).replaceAll("\\s+", "");
            assertTrue(p2.contains("[2/2]"), "Should contain [2/2], got: " + p2);
        }

        // Test single page mode (showTotalPage = false)
        var pnSingle = PageNumber.of()
                .bottomCenter()
                .prefix("- ")
                .suffix(" -")
                .showTotalPage(false);

        var singleOptions = StampOptions.builder()
                .pageNumber(pnSingle)
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, singleOptions);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(2, doc.getNumberOfPages());
            var p1 = pageText(doc, 1).replaceAll("\\s+", "");
            assertTrue(p1.contains("-1-"), "Should contain -1-, got: " + p1);
            assertFalse(p1.contains("/"), "Should not contain separator");
        }
    }

    @Test
    void testEncryptedPdfProtection() throws IOException {
        var encryptedPdf = dir.resolve("restricted.pdf");
        try (var doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            var ap = new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            ap.setCanExtractContent(false);
            var spp = new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("ownerPass", "", ap);
            doc.protect(spp);
            doc.save(encryptedPdf.toFile());
        }

        var options = StampOptions.builder().pageNumber(PageNumber.of()).build();
        var ex = assertThrows(IOException.class, () -> S2PdfUtil.stamp(encryptedPdf, options));
        assertTrue(ex.getMessage().contains("암호화 또는 권한 제한"), ex.getMessage());

        var mergeEx = assertThrows(IOException.class, () -> S2PdfUtil.merge(PdfSource.ofPdf(encryptedPdf)));
        assertTrue(mergeEx.getMessage().contains("암호화 또는 권한 제한"), mergeEx.getMessage());
    }

    @Test
    void testPageSelectorLogic() {
        // All
        var all = PageSelector.all();
        assertTrue(all.includes(0, 5));
        assertTrue(all.includes(4, 5));

        // First
        var first = PageSelector.first();
        assertTrue(first.includes(0, 5));
        assertFalse(first.includes(1, 5));

        // Last
        var last = PageSelector.last();
        assertFalse(last.includes(0, 5));
        assertTrue(last.includes(4, 5));

        // Range
        var range = PageSelector.range(2, 4);
        assertFalse(range.includes(0, 5)); // Page 1
        assertTrue(range.includes(1, 5));  // Page 2
        assertTrue(range.includes(2, 5));  // Page 3
        assertTrue(range.includes(3, 5));  // Page 4
        assertFalse(range.includes(4, 5)); // Page 5

        // Only
        var only = PageSelector.only(1, 3, 5);
        assertTrue(only.includes(0, 5));  // Page 1
        assertFalse(only.includes(1, 5)); // Page 2
        assertTrue(only.includes(2, 5));  // Page 3
        assertFalse(only.includes(3, 5)); // Page 4
        assertTrue(only.includes(4, 5));  // Page 5

        // ExceptFirst
        var exceptFirst = PageSelector.exceptFirst();
        assertFalse(exceptFirst.includes(0, 5));
        assertTrue(exceptFirst.includes(1, 5));
        assertTrue(exceptFirst.includes(4, 5));

        var exceptFirst2 = PageSelector.exceptFirst(2);
        assertFalse(exceptFirst2.includes(0, 5));
        assertFalse(exceptFirst2.includes(1, 5));
        assertTrue(exceptFirst2.includes(2, 5));

        // ExceptLast
        var exceptLast = PageSelector.exceptLast();
        assertTrue(exceptLast.includes(0, 5));
        assertTrue(exceptLast.includes(3, 5));
        assertFalse(exceptLast.includes(4, 5));

        var exceptLast2 = PageSelector.exceptLast(2);
        assertTrue(exceptLast2.includes(2, 5));
        assertFalse(exceptLast2.includes(3, 5));
        assertFalse(exceptLast2.includes(4, 5));

        // Invalid args
        assertThrows(IllegalArgumentException.class, () -> PageSelector.range(0, 3));
        assertThrows(IllegalArgumentException.class, () -> PageSelector.range(4, 2));
        assertThrows(IllegalArgumentException.class, () -> PageSelector.only());
        assertThrows(IllegalArgumentException.class, () -> PageSelector.only(0));
        assertThrows(IllegalArgumentException.class, () -> PageSelector.exceptFirst(0));
        assertThrows(IllegalArgumentException.class, () -> PageSelector.exceptLast(0));
    }

    @Test
    void testImageStampConfigurationAndRendering() throws IOException {
        var pdf = multiPagePdf(3);

        var img = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        var baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        var imgBytes = baos.toByteArray();

        var stamp1 = ImageStamp.of(imgBytes)
                .topRight(20, 20)
                .size(60, 30)
                .opacity(0.8f)
                .rotation(15f);

        assertEquals(Position.TOP_RIGHT, stamp1.getPosition());
        assertEquals(20f, stamp1.getOffsetX());
        assertEquals(20f, stamp1.getOffsetY());
        assertEquals(0.8f, stamp1.getOpacity());
        assertEquals(15f, stamp1.getRotation());
        assertEquals(60f, stamp1.getWidth());
        assertEquals(30f, stamp1.getHeight());

        // Aspect ratio drawSize
        var sizeBoth = stamp1.drawSize(100, 50);
        assertEquals(60f, sizeBoth[0]);
        assertEquals(30f, sizeBoth[1]);

        var stampWidthOnly = ImageStamp.of(imgBytes).width(50f);
        var sizeW = stampWidthOnly.drawSize(100, 50);
        assertEquals(50f, sizeW[0]);
        assertEquals(25f, sizeW[1]);

        var stampHeightOnly = ImageStamp.of(imgBytes).height(50f);
        var sizeH = stampHeightOnly.drawSize(100, 50);
        assertEquals(100f, sizeH[0]);
        assertEquals(50f, sizeH[1]);

        // Stamp with PageSelector (only on page 2)
        var options = StampOptions.builder()
                .addImage(stamp1.pages(PageSelector.range(2, 2)))
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(3, doc.getNumberOfPages());
        }
    }

    @Test
    void testQrStampGenerationAndPlacement() throws IOException {
        var pdf = multiPagePdf(2);

        var qr = QrStamp.ofUrl("https://example.com/verify/DOC-12345")
                .bottomRight(15, 15)
                .size(80f)
                .opacity(0.9f)
                .resolution(200)
                .colors(Color.BLACK, Color.WHITE);

        assertEquals("https://example.com/verify/DOC-12345", qr.getContent());
        assertEquals(Position.BOTTOM_RIGHT, qr.getPosition());
        assertEquals(15f, qr.getOffsetX());
        assertEquals(15f, qr.getOffsetY());
        assertEquals(80f, qr.getDisplayWidth());
        assertEquals(80f, qr.getDisplayHeight());
        assertEquals(0.9f, qr.getOpacity());
        assertEquals(200, qr.getResolution());

        // Verify generated QR bytes is a valid image
        byte[] qrBytes = qr.generateImageBytes();
        assertTrue(qrBytes.length > 0);
        var readImage = ImageIO.read(new java.io.ByteArrayInputStream(qrBytes));
        assertTrue(readImage.getWidth() > 0);
        assertTrue(readImage.getHeight() > 0);

        // Apply QR to only the last page
        var options = StampOptions.builder()
                .addQr(qr.pages(PageSelector.last()))
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(2, doc.getNumberOfPages());
        }
    }

    @Test
    void testCompositeStampWithTextAndImageAndQr() throws IOException {
        var pdf = multiPagePdf(3);

        var img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        var baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);

        var options = StampOptions.builder()
                // Header text on pages 2~3
                .addText(Text.of("CONFIDENTIAL")
                        .topCenter(0, 15)
                        .fontSize(10)
                        .pages(PageSelector.exceptFirst()))
                // Certification seal on first page only
                .addImage(ImageStamp.of(baos.toByteArray())
                        .topLeft(20, 20)
                        .size(40, 40)
                        .pages(PageSelector.first()))
                // QR verification on last page only
                .addQr(QrStamp.ofUrl("https://cert.goono.so/verify?id=TEST")
                        .bottomRight(20, 20)
                        .size(60)
                        .pages(PageSelector.last()))
                // Page numbers on all pages
                .pageNumber(PageNumber.of("%d / %d")
                        .bottomCenter(0, 15)
                        .fontSize(9))
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(3, doc.getNumberOfPages());

            // Page 1 should NOT have "CONFIDENTIAL" header
            var p1 = pageText(doc, 1);
            assertFalse(p1.contains("CONFIDENTIAL"));
            assertTrue(p1.contains("1 / 3"));

            // Page 2 should have "CONFIDENTIAL" header
            var p2 = pageText(doc, 2);
            assertTrue(p2.contains("CONFIDENTIAL"));
            assertTrue(p2.contains("2 / 3"));

            // Page 3 should have "CONFIDENTIAL" header
            var p3 = pageText(doc, 3);
            assertTrue(p3.contains("CONFIDENTIAL"));
            assertTrue(p3.contains("3 / 3"));
        }
    }

    @Test
    void testWatermarkWithPageSelector() throws IOException {
        var pdf = multiPagePdf(3);

        var img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        var baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);

        var watermark = Watermark.of(baos.toByteArray())
                .size(50, 50)
                .pages(PageSelector.only(1, 3));

        assertTrue(watermark.appliesToPage(0, 3)); // Page 1
        assertFalse(watermark.appliesToPage(1, 3)); // Page 2
        assertTrue(watermark.appliesToPage(2, 3)); // Page 3

        var options = StampOptions.builder()
                .watermark(watermark)
                .build();

        try (var stampedStream = S2PdfUtil.stamp(pdf, options);
                var doc = Loader.loadPDF(stampedStream.readAllBytes())) {
            assertEquals(3, doc.getNumberOfPages());
        }
    }

    @Test
    void getPageCount_allVariants() throws IOException {
        var pdfPath = multiPagePdf(4);
        var pdfBytes = Files.readAllBytes(pdfPath);

        // Path
        assertEquals(4, S2PdfUtil.getPageCount(pdfPath));
        // File
        assertEquals(4, S2PdfUtil.getPageCount(pdfPath.toFile()));
        // InputStream (auto close)
        assertEquals(4, S2PdfUtil.getPageCount(Files.newInputStream(pdfPath)));
        // InputStream (shouldCloseStream = false)
        try (var in = Files.newInputStream(pdfPath)) {
            assertEquals(4, S2PdfUtil.getPageCount(in, false));
        }
        // byte[]
        assertEquals(4, S2PdfUtil.getPageCount(pdfBytes));

        // Edge cases
        assertEquals(0, S2PdfUtil.getPageCount((Path) null));
        assertEquals(0, S2PdfUtil.getPageCount((java.io.File) null));
        assertEquals(0, S2PdfUtil.getPageCount((java.io.InputStream) null));
        assertEquals(0, S2PdfUtil.getPageCount((byte[]) null));
        assertEquals(0, S2PdfUtil.getPageCount(new byte[0]));
        assertEquals(0, S2PdfUtil.getPageCount(dir.resolve("non_existent.pdf")));
    }
}
