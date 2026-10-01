package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2util.support.S2FileKind.Kind;
import io.github.devers2.s2util.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2util.support.S2PdfUtil.PdfSource;

/**
 * Merge sources are told apart by a hint (file name, extension or MIME type), the file name, or the content, so a
 * mixed list from a database merges without branching.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 병합 소스의 종류를 힌트(파일명, 확장자, MIME 타입), 파일명, 내용으로 판별해 DB 의 섞인 목록을 분기 없이 병합하는지 확인합니다.
 */
class S2PdfSourceKindTest {

    @TempDir
    Path dir;

    @AfterEach
    void tearDown() {
        S2PdfUtil.resetOfficeCommand();
    }

    private static Kind kind(String hint) {
        var detected = S2FileKind.fromHint(hint);
        return detected == null ? null : detected.kind();
    }

    private static String extension(String hint) {
        return S2FileKind.fromHint(hint).extension();
    }

    @Test
    void hintsAreFileNamesExtensionsOrMimeTypes() {
        assertEquals(Kind.PDF, kind("보고서.PDF"));
        assertEquals(Kind.PDF, kind("pdf"));
        assertEquals(Kind.PDF, kind("application/pdf; charset=binary"));
        assertEquals(Kind.IMAGE, kind("사진.jpeg"));
        assertEquals(Kind.IMAGE, kind("image/heic"), "unknown image subtypes still count as images");
        assertEquals(Kind.SVG, kind("image/svg+xml"));
        assertEquals(Kind.MARKDOWN, kind("text/markdown"));
        assertEquals(Kind.TEXT, kind("/data/logs/app.log"));
        for (var hwp : List.of("application/x-hwp", "application/haansofthwp", "application/vnd.hancom.hwp", "a.hwp")) {
            assertEquals("hwp", extension(hwp), hwp);
        }
        assertEquals("hwpx", extension("application/vnd.hancom.hwpx"));
        assertEquals("xlsx", extension("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertNull(kind("application/octet-stream"), "generic types say nothing");
        assertNull(kind("archive.zip"));
        assertNull(kind("a1b2c3d4-e5f6"));
        assertNull(kind(" "));

        assertTrue(S2PdfUtil.isMergeable("보고서.hwpx"));
        assertTrue(S2PdfUtil.isMergeable("image/png"));
        assertFalse(S2PdfUtil.isMergeable("movie.mp4"));
        assertFalse(S2PdfUtil.isMergeable(null));
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

    /** An OLE header followed by a stream name in UTF-16, as in a directory sector | OLE 머리말 + UTF-16 스트림 이름 */
    private static byte[] ole(String streamName) {
        var head = new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };
        var name = streamName.getBytes(StandardCharsets.UTF_16LE);
        var out = new byte[70_000 + name.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(name, 0, out, 65_530, name.length); // across the 64KB scan boundary | 64KB 경계에 걸침
        return out;
    }

    private static byte[] pdf(int pages) throws IOException {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
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
    void contentTellsTheKindWhenThereIsNoName() throws IOException {
        record Case(byte[] content, String extension) {
        }
        var cases = List.of(
                new Case(pdf(1), "pdf"),
                new Case(png(), "png"),
                new Case("﻿  <?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8), "svg"),
                new Case("<!DOCTYPE html><html><body>x</body></html>".getBytes(StandardCharsets.UTF_8), "html"),
                new Case("{\\rtf1\\ansi hello}".getBytes(StandardCharsets.US_ASCII), "rtf"),
                new Case(zip("[Content_Types].xml", "x", "word/document.xml", "x"), "docx"),
                new Case(zip("[Content_Types].xml", "x", "xl/workbook.xml", "x"), "xlsx"),
                new Case(zip("[Content_Types].xml", "x", "ppt/presentation.xml", "x"), "pptx"),
                new Case(zip("mimetype", "application/hwp+zip", "Contents/section0.xml", "x"), "hwpx"),
                new Case(zip("mimetype", "application/vnd.oasis.opendocument.text", "content.xml", "x"), "odt"),
                new Case(ole("HwpSummaryInformation"), "hwp"),
                new Case(ole("WordDocument"), "doc"),
                new Case(ole("Workbook"), "xls"),
                new Case(ole("PowerPoint Document"), "ppt"));
        for (var c : cases) {
            assertEquals(c.extension(), S2FileKind.fromContent(c.content()).extension(), "bytes: " + c.extension());
            var file = dir.resolve(UUID.randomUUID().toString()); // stored without an extension | 확장자 없이 저장
            Files.write(file, c.content());
            assertEquals(c.extension(), S2FileKind.fromContent(file).extension(), "file: " + c.extension());
        }
        assertNull(S2FileKind.fromContent(zip("readme.txt", "x")), "a plain zip is not a document");
        assertNull(S2FileKind.fromContent("just some words".getBytes(StandardCharsets.UTF_8)));
    }

    private static PDDocument load(InputStream merged) throws IOException {
        try (merged) {
            return Loader.loadPDF(merged.readAllBytes());
        }
    }

    @Test
    void aMixedListMergesWithoutBranching() throws IOException {
        Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "shell script");
        // A soffice stand-in that records the input name and returns a 2-page PDF | 입력 이름을 기록하고 2쪽 PDF 를 돌려주는 대역
        var converted = dir.resolve("converted.pdf");
        Files.write(converted, pdf(2));
        var soffice = dir.resolve("soffice");
        Files.writeString(soffice, """
                #!/usr/bin/env bash
                out=""; input=""
                while [ $# -gt 0 ]; do case "$1" in --outdir) out="$2"; shift 2 ;; --convert-to) shift 2 ;; -*) shift ;; *) input="$1"; shift ;; esac; done
                echo "$input" >> "%s/inputs.log"; base="$(basename "$input")"; cp "%s" "$out/${base%%.*}.pdf"
                """.formatted(dir, converted));
        Files.setPosixFilePermissions(soffice, PosixFilePermissions.fromString("rwx------"));
        S2PdfUtil.setOfficeCommand(soffice.toString());

        // Like a table of attachments: stored path (no extension), original name or MIME type | 첨부 테이블처럼
        record Attachment(Path stored, String hint) {
        }
        var attachments = List.of(
                new Attachment(write(pdf(3)), "스캔본.pdf"),
                new Attachment(write("# 노트".getBytes(StandardCharsets.UTF_8)), "text/markdown"),
                new Attachment(write("hwp bytes".getBytes()), "실험계획.hwp"),
                new Attachment(write(png()), null), // nothing but the content | 내용만
                new Attachment(write("docx bytes".getBytes()), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        var sources = new java.util.ArrayList<PdfSource>();
        for (var a : attachments) {
            sources.add(PdfSource.of(a.stored(), a.hint()));
        }
        try (var doc = load(S2PdfUtil.merge(sources, MergeOptions.create().bookmarks(true)))) {
            assertEquals(3 + 1 + 2 + 1 + 2, doc.getNumberOfPages());
            var titles = new java.util.ArrayList<String>();
            for (PDOutlineItem item : doc.getDocumentCatalog().getDocumentOutline().children()) {
                titles.add(item.getTitle());
            }
            assertEquals("스캔본.pdf", titles.get(0), "the original name, not the stored UUID");
            assertEquals("실험계획.hwp", titles.get(2));
            assertTrue(new PDFTextStripper().getText(doc).contains("노트"));
        }
        var inputs = Files.readAllLines(dir.resolve("inputs.log"));
        assertTrue(inputs.stream().anyMatch(i -> i.endsWith("document.hwp")), "the converter gets the right extension: " + inputs);
        assertTrue(inputs.stream().anyMatch(i -> i.endsWith("document.docx")), inputs.toString());

        // From memory (a BLOB) and a stream | 메모리(BLOB)와 스트림
        try (var doc = load(S2PdfUtil.merge(PdfSource.of(pdf(2), null),
                PdfSource.of(new java.io.ByteArrayInputStream(png()), "image/png")))) {
            assertEquals(3, doc.getNumberOfPages());
        }
    }

    private Path write(byte[] content) throws IOException {
        var file = dir.resolve(UUID.randomUUID().toString());
        Files.write(file, content);
        return file;
    }

    @Test
    void unknownFilesAreRefusedWithAHint() throws IOException {
        var file = write("plain words".getBytes(StandardCharsets.UTF_8));
        var e = assertThrows(IllegalArgumentException.class, () -> PdfSource.of(file));
        assertTrue(e.getMessage().contains("알 수 없는 형식") && e.getMessage().contains("힌트"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PdfSource.of(file, "movie.mp4"));
        // A wrong generic MIME type falls back to the name and content | 일반 MIME 타입은 이름·내용으로 넘어감
        assertNotNull(PdfSource.of(write(pdf(1)), "application/octet-stream"));
        // A hint wins over the content | 힌트가 내용보다 우선
        assertNotNull(PdfSource.of(file, "notes.txt"));
    }
}
