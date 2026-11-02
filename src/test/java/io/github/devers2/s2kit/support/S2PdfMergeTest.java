package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PDF and image merging with PDFBox alone (no external converters): order, skipped entries, stream closing,
 * protected documents, bookmarks and page count.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * PDFBox 만으로 하는 PDF·이미지 병합(외부 변환기 없음): 순서, 건너뛰는 항목, 스트림 닫기, 보호된 문서, 책갈피, 쪽 수.
 */
class S2PdfMergeTest {

    @TempDir
    Path dir;

    /** A PDF whose pages each carry one line of text | 쪽마다 글 한 줄이 있는 PDF */
    private Path pdf(String name, String... pageTexts) throws IOException {
        var path = dir.resolve(name);
        try (var doc = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (var text : pageTexts) {
                var page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (var content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(font, 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            doc.save(path.toFile());
        }
        return path;
    }

    private static byte[] png(int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String text(InputStream merged) throws IOException {
        try (merged; var doc = Loader.loadPDF(merged.readAllBytes())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void pdfsAreMergedInTheGivenOrder() throws IOException {
        var first = pdf("a.pdf", "Alpha 1", "Alpha 2");
        var second = pdf("b.pdf", "Bravo 1");
        var text = text(S2PdfUtil.mergePdfPaths(first, second));
        assertTrue(text.indexOf("Alpha 1") < text.indexOf("Alpha 2") && text.indexOf("Alpha 2") < text.indexOf("Bravo 1"), text);
        try (var merged = S2PdfUtil.mergePdfFiles(first.toFile(), second.toFile())) {
            assertEquals(3, S2PdfUtil.getPageCount(merged));
        }
    }

    @Test
    void missingFilesAreSkippedButAllMissingIsAnError() throws IOException {
        var only = pdf("only.pdf", "Only");
        try (var merged = S2PdfUtil.mergePdfPaths(List.of(dir.resolve("missing.pdf"), only))) {
            assertEquals(1, S2PdfUtil.getPageCount(merged));
        }
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.mergePdfPaths(dir.resolve("missing.pdf")));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.mergePdfPaths(List.of()));
    }

    @Test
    void streamsAreClosedOnlyWhenAsked() throws IOException {
        var bytes = Files.readAllBytes(pdf("s.pdf", "Stream"));
        var kept = new TrackingStream(bytes);
        try (var merged = S2PdfUtil.mergePdfs(List.of(kept), false)) {
            assertEquals(1, S2PdfUtil.getPageCount(merged));
        }
        assertFalse(kept.closed);
        var closed = new TrackingStream(bytes);
        S2PdfUtil.mergePdfs(closed).close();
        assertTrue(closed.closed);
    }

    @Test
    void protectedPdfsAreRefused() throws IOException {
        var restricted = dir.resolve("restricted.pdf");
        try (var doc = new PDDocument()) {
            doc.addPage(new PDPage());
            var permission = new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            permission.setCanExtractContent(false);
            doc.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner", "", permission));
            doc.save(restricted.toFile());
        }
        var e = assertThrows(IOException.class, () -> S2PdfUtil.mergePdfPaths(restricted, pdf("ok.pdf", "OK")));
        assertTrue(e.getMessage().contains("암호화 또는 권한 제한"), e.getMessage());
    }

    @Test
    void imagesBecomeOneA4PageEach() throws IOException {
        try (var merged = S2PdfUtil.mergeImagesToPdf(new ByteArrayInputStream(png(400, 300)), new ByteArrayInputStream(png(300, 400)));
                var doc = Loader.loadPDF(merged.readAllBytes())) {
            assertEquals(2, doc.getNumberOfPages());
            var landscape = doc.getPage(0).getMediaBox();
            var portrait = doc.getPage(1).getMediaBox();
            assertTrue(landscape.getWidth() > landscape.getHeight(), "a wide image gets a landscape page");
            assertTrue(portrait.getHeight() > portrait.getWidth(), "a tall image gets a portrait page");
        }
        var file = dir.resolve("i.png");
        Files.write(file, png(50, 50));
        try (var merged = S2PdfUtil.mergeImagePaths(file, dir.resolve("missing.png"))) {
            assertEquals(1, S2PdfUtil.getPageCount(merged));
        }
        try (var single = S2PdfUtil.convertImageToPdf(file)) {
            assertEquals(1, S2PdfUtil.getPageCount(single));
        }
    }

    @Test
    void bookmarkPointsAtTheFirstPageAndKeepsTheOriginal() throws IOException {
        var source = pdf("doc.pdf", "One", "Two");
        var copy = dir.resolve("bookmarked.pdf");
        S2PdfUtil.addBookmark(source.toFile(), "보고서", copy);
        try (var doc = Loader.loadPDF(copy.toFile())) {
            var outline = doc.getDocumentCatalog().getDocumentOutline();
            assertEquals("보고서", outline.getFirstChild().getTitle());
            assertEquals(2, doc.getNumberOfPages());
        }
        try (var original = Loader.loadPDF(source.toFile())) {
            assertEquals(null, original.getDocumentCatalog().getDocumentOutline(), "the source file is not changed");
        }
    }

    @Test
    void controlCharacterCleanupLeavesNonFontsAlone() {
        var notAFont = "not a font".getBytes();
        assertSame(notAFont, S2FontCmap.withoutControlCharacters(notAFont));
    }

    /** Remembers whether it was closed | 닫혔는지 기억하는 스트림 */
    private static final class TrackingStream extends ByteArrayInputStream {
        boolean closed;

        TrackingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
