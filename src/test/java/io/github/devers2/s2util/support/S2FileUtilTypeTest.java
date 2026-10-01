package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2util.exception.S2RuntimeException;

/**
 * File types from MIME types, names, hints and content.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * MIME 타입, 파일명, 힌트, 내용으로 파일 형식(확장자)을 판별하는지 확인합니다.
 */
class S2FileUtilTypeTest {

    @TempDir
    Path dir;

    @Test
    void mimeTypesAndExtensions() {
        assertEquals("pdf", S2FileUtil.getExtensionByMimeType("Application/PDF; charset=binary"));
        assertEquals("png", S2FileUtil.getExtensionByMimeType("image/png"));
        assertEquals("docx", S2FileUtil.getExtensionByMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        for (var hwp : List.of("application/x-hwp", "application/haansofthwp", "application/vnd.hancom.hwp", "application/hwp")) {
            assertEquals("hwp", S2FileUtil.getExtensionByMimeType(hwp), hwp);
        }
        assertEquals("hwpx", S2FileUtil.getExtensionByMimeType("application/hwp+zip"));
        assertEquals("", S2FileUtil.getExtensionByMimeType("image/heic"));
        assertEquals("", S2FileUtil.getExtensionByMimeType((String) null));

        assertEquals("application/x-hwp", S2FileUtil.getMimeTypeByExtension("hwp"));
        assertEquals("image/jpeg", S2FileUtil.getMimeTypeByExtension("사진.JPG"));
        assertEquals("text/markdown", S2FileUtil.getMimeTypeByExtension("md"));
        assertEquals("", S2FileUtil.getMimeTypeByExtension("unknownext"));
    }

    @Test
    void hintsAreFileNamesExtensionsOrMimeTypes() {
        assertEquals("pdf", S2FileUtil.getExtensionByHint("보고서.PDF"));
        assertEquals("pdf", S2FileUtil.getExtensionByHint("/data/files/report.pdf"));
        assertEquals("pdf", S2FileUtil.getExtensionByHint("docs/report.pdf"));
        assertEquals("hwp", S2FileUtil.getExtensionByHint("HWP"));
        assertEquals("pdf", S2FileUtil.getExtensionByHint("application/pdf"));
        // Subtypes ending like a file extension stay MIME types | 확장자처럼 끝나는 하위 유형도 MIME 타입
        assertEquals("odt", S2FileUtil.getExtensionByHint("application/vnd.oasis.opendocument.text"));
        assertEquals("docx", S2FileUtil.getExtensionByHint("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals("", S2FileUtil.getExtensionByHint("image/heic"));
        assertEquals("bin", S2FileUtil.getExtensionByHint("application/octet-stream"));
        assertEquals("", S2FileUtil.getExtensionByHint("a1b2c3d4-e5f6-7788"));
        assertEquals("", S2FileUtil.getExtensionByHint(" "));
    }

    private static byte[] zip(String... entries) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (int i = 0; i < entries.length; i += 2) {
                zip.putNextEntry(new ZipEntry(entries[i]));
                zip.write(entries[i + 1].getBytes(StandardCharsets.US_ASCII));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** An OLE header and a stream name in UTF-16 across the 64KB scan boundary | OLE 머리말 + 64KB 경계에 걸친 UTF-16 스트림 이름 */
    private static byte[] ole(String streamName) {
        var head = new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };
        var name = streamName.getBytes(StandardCharsets.UTF_16LE);
        var out = new byte[70_000 + name.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(name, 0, out, 65_530, name.length);
        return out;
    }

    private static byte[] pdf() throws IOException {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] png() throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void contentTellsTheTypeWhenThereIsNoName() throws IOException {
        record Case(byte[] content, String extension) {
        }
        var cases = List.of(
                new Case(pdf(), "pdf"),
                new Case(png(), "png"),
                new Case("﻿  <?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8), "svg"),
                new Case("<!DOCTYPE html><html><body>x</body></html>".getBytes(StandardCharsets.UTF_8), "html"),
                new Case("{\\rtf1\\ansi hello}".getBytes(StandardCharsets.US_ASCII), "rtf"),
                new Case(zip("[Content_Types].xml", "x", "word/document.xml", "x"), "docx"),
                new Case(zip("[Content_Types].xml", "x", "xl/workbook.xml", "x"), "xlsx"),
                new Case(zip("[Content_Types].xml", "x", "ppt/presentation.xml", "x"), "pptx"),
                new Case(zip("mimetype", "application/hwp+zip", "Contents/section0.xml", "x"), "hwpx"),
                new Case(zip("mimetype", "application/vnd.oasis.opendocument.text", "content.xml", "x"), "odt"),
                new Case(zip("readme.txt", "x"), "zip"),
                new Case(ole("HwpSummaryInformation"), "hwp"),
                new Case(ole("WordDocument"), "doc"),
                new Case(ole("Workbook"), "xls"),
                new Case(ole("PowerPoint Document"), "ppt"),
                new Case("just some words".getBytes(StandardCharsets.UTF_8), ""));
        for (var c : cases) {
            assertEquals(c.extension(), S2FileUtil.detectExtension(c.content()), "bytes: " + c.extension());
            var file = dir.resolve(UUID.randomUUID().toString()); // stored without an extension | 확장자 없이 저장
            Files.write(file, c.content());
            assertEquals(c.extension(), S2FileUtil.detectExtension(file), "file: " + c.extension());
        }
    }

    @Test
    void existingMimeLookupsFallBackToTheContent() throws IOException {
        var stored = dir.resolve(UUID.randomUUID().toString());
        Files.write(stored, pdf());
        assertEquals("pdf", S2FileUtil.getExtensionByMimeType(stored), "no extension: told by the content");
        assertEquals("application/pdf", S2FileUtil.getContentType(stored));
        assertThrows(S2RuntimeException.class, () -> S2FileUtil.detectExtension(dir.resolve("missing")));
    }
}
