package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2util.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2util.support.S2PdfUtil.PdfSource;

/**
 * Office and Hangul documents in merges: conversion through a soffice-compatible command, clear failures when it is
 * missing or fails, and a real conversion when a converter is installed.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 병합의 오피스·한글 문서를 확인합니다: soffice 호환 명령으로 변환, 명령이 없거나 실패할 때의 명확한 오류, 변환기가 설치된 환경의 실제 변환.
 */
class S2PdfOfficeTest {

    @TempDir
    Path dir;

    private Path samplePdf;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "shell scripts");
        samplePdf = dir.resolve("converted.pdf");
        try (var doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(samplePdf.toFile());
        }
    }

    @AfterEach
    void tearDown() {
        S2PdfUtil.resetOfficeCommand();
        S2PdfUtil.setOfficeTimeout(Duration.ofMinutes(3));
    }

    /** A soffice stand-in: {@code behavior} is "ok", "fail" or "hang" | soffice 대역 */
    private Path fakeSoffice(String behavior) throws IOException {
        var script = dir.resolve("fake-soffice-" + behavior);
        Files.writeString(script, """
                #!/usr/bin/env bash
                echo "$@" >> "%s/calls.log"
                out=""; input=""
                while [ $# -gt 0 ]; do
                  case "$1" in
                    --outdir) out="$2"; shift 2 ;;
                    --convert-to) shift 2 ;;
                    -*) shift ;;
                    *) input="$1"; shift ;;
                  esac
                done
                case "%s" in
                  ok) base="$(basename "$input")"; cp "%s" "$out/${base%%.*}.pdf" ;;
                  fail) echo "Error: source file could not be loaded" ; exit 1 ;;
                  hang) sleep 30 ;;
                esac
                """.formatted(dir, behavior, samplePdf));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private static PDDocument load(InputStream merged) throws IOException {
        try (merged) {
            return Loader.loadPDF(merged.readAllBytes());
        }
    }

    @Test
    void documentsAreConvertedAndMergedInOrder() throws IOException {
        S2PdfUtil.setOfficeCommand(fakeSoffice("ok").toString());
        var docx = Files.writeString(dir.resolve("계획서 초안.docx"), "docx bytes");
        var options = MergeOptions.create().bookmarks(true);
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofText("cover"), PdfSource.ofDocument(docx),
                PdfSource.ofDocument(new ByteArrayInputStream("hwp".getBytes()), "보고서.hwp")), options))) {
            assertEquals(1 + 2 + 2, doc.getNumberOfPages());
            var titles = new java.util.ArrayList<String>();
            doc.getDocumentCatalog().getDocumentOutline().children().forEach(item -> titles.add(item.getTitle()));
            assertEquals(List.of("문서 1", "계획서 초안.docx", "보고서.hwp"), titles);
        }
        var calls = Files.readAllLines(dir.resolve("calls.log"));
        assertEquals(2, calls.size());
        assertTrue(calls.get(0).contains("--convert-to pdf") && calls.get(0).contains("document.docx"), calls.get(0));
        assertTrue(calls.get(0).contains("-env:UserInstallation="), "a private profile per conversion");
        assertTrue(Files.exists(docx), "the source file is left alone");
    }

    @Test
    void withoutLibreOfficeOnlyDocumentSourcesFail() throws IOException {
        S2PdfUtil.setOfficeCommand(dir.resolve("missing-soffice").toString());
        var e = assertThrows(IOException.class,
                () -> S2PdfUtil.merge(PdfSource.ofText("ok"), PdfSource.ofDocument(new byte[] { 1 }, "보고서.docx")));
        assertEquals("병합 소스 #2 (DOCUMENT 보고서.docx) 처리 실패: 오피스·한글 문서를 변환하려면 s2-office-converter 설치가 필요합니다.",
                e.getMessage());

        assertFalse(S2PdfUtil.isOfficeConversionAvailable(), "a configured command that does not exist");

        // Other sources still merge | 다른 소스는 그대로 병합
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofText("still works")))) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    void failedConversionsExplainWhy() throws IOException {
        S2PdfUtil.setOfficeCommand(fakeSoffice("fail").toString());
        var e = assertThrows(IOException.class,
                () -> S2PdfUtil.merge(PdfSource.ofDocument(new byte[] { 1 }, "보고서.hwp")));
        // Plain LibreOffice failing on a Hangul file: the converter (with H2Orestart) is what is missing
        // | 일반 LibreOffice 가 한글 파일에서 실패: 빠진 것은 변환기(H2Orestart 포함)
        assertTrue(e.getMessage().endsWith("s2-office-converter 설치가 필요합니다."), e.getMessage());
        // Details stay in the cause, not in the message | 상세는 메시지가 아닌 원인(cause)에
        var detail = e.getCause().getCause();
        assertTrue(detail.getMessage().contains("source file could not be loaded"), detail.getMessage());
        assertFalse(e.getMessage().contains("source file could not be loaded"));

        var docx = assertThrows(IOException.class,
                () -> S2PdfUtil.merge(PdfSource.ofDocument(new byte[] { 1 }, "a.docx")));
        assertTrue(docx.getMessage().endsWith("문서를 PDF로 변환하지 못했습니다: a.docx"), docx.getMessage());
    }

    @Test
    void hungConversionsAreStopped() throws IOException {
        S2PdfUtil.setOfficeCommand(fakeSoffice("hang").toString());
        S2PdfUtil.setOfficeTimeout(Duration.ofMillis(500));
        var start = System.nanoTime();
        var e = assertThrows(IOException.class, () -> S2PdfUtil.merge(PdfSource.ofDocument(new byte[] { 1 }, "a.xlsx")));
        assertTrue(e.getMessage().contains("제한 시간"), e.getMessage());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 10, "does not wait for the hung process");
    }

    @Test
    void unsupportedFormatsAreRejectedUpFront() {
        assertThrows(IllegalArgumentException.class, () -> PdfSource.ofDocument(new byte[] { 1 }, "a.exe"));
        assertThrows(IllegalArgumentException.class, () -> PdfSource.ofDocument(new byte[] { 1 }, "noextension"));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.setOfficeTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.setOfficeCommand());
    }

    @Test
    void realConversionWhenAConverterIsInstalled() throws IOException {
        S2PdfUtil.resetOfficeCommand();
        Assumptions.assumeTrue(S2PdfUtil.isOfficeConversionAvailable(), "no LibreOffice / s2-soffice installed");
        // RTF is plain text, so the test needs no binary sample | RTF 는 텍스트라 이진 표본이 필요 없음
        var rtf = "{\\rtf1\\ansi\\uc0 Hello S2 \\u54620\\u44544}";
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofDocument(rtf.getBytes(), "sample.rtf")))) {
            var text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Hello S2") && text.contains("한글"), text);
        }
    }
}
