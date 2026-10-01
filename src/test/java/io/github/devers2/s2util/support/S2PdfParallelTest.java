package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2util.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2util.support.S2PdfUtil.PdfSource;

/**
 * Sources of a merge are converted concurrently and still merged in their order; a failure names the first failing
 * source in that order.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 병합 소스를 동시에 변환하면서도 원래 순서대로 붙이고, 실패하면 순서상 첫 실패 소스를 알려 주는지 확인합니다.
 */
class S2PdfParallelTest {

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "shell scripts");
    }

    @AfterEach
    void tearDown() {
        S2PdfUtil.resetOfficeCommand();
        S2PdfUtil.setConversionParallelism(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
    }

    /**
     * A soffice stand-in taking a second; the page count is the digit in the file content, failing on "fail" | 1초 걸리는
     * soffice 대역. 쪽 수는 파일 내용의 숫자, "fail" 이면 실패
     */
    private Path slowSoffice() throws IOException {
        for (int pages = 1; pages <= 4; pages++) {
            try (var doc = new PDDocument()) {
                for (int i = 0; i < pages; i++) {
                    doc.addPage(new PDPage());
                }
                doc.save(dir.resolve(pages + ".pdf").toFile());
            }
        }
        var script = dir.resolve("slow-soffice");
        Files.writeString(script, """
                #!/usr/bin/env bash
                out=""; input=""
                while [ $# -gt 0 ]; do
                  case "$1" in
                    --outdir) out="$2"; shift 2 ;;
                    --convert-to) shift 2 ;;
                    -*) shift ;;
                    *) input="$1"; shift ;;
                  esac
                done
                sleep 1
                content="$(cat "$input")"
                [ "$content" = "fail" ] && { echo broken; exit 1; }
                base="$(basename "$input")"; cp "%s/$content.pdf" "$out/${base%%.*}.pdf"
                """.formatted(dir));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private List<PdfSource> documents(String... contents) throws IOException {
        return prefixedDocuments("", contents);
    }

    private List<PdfSource> prefixedDocuments(String prefix, String... contents) throws IOException {
        var sources = new ArrayList<PdfSource>();
        for (int i = 0; i < contents.length; i++) {
            sources.add(PdfSource.ofDocument(Files.writeString(dir.resolve(prefix + "doc" + i + ".docx"), contents[i])));
        }
        return sources;
    }

    private static PDDocument load(InputStream merged) throws IOException {
        try (merged) {
            return Loader.loadPDF(merged.readAllBytes());
        }
    }

    private static List<Integer> outlinePages(PDDocument doc) throws IOException {
        var pages = new ArrayList<Integer>();
        for (var item : doc.getDocumentCatalog().getDocumentOutline().children()) {
            var destination = (org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination) item
                    .getDestination();
            pages.add(doc.getPages().indexOf(destination.getPage()));
        }
        return pages;
    }

    @Test
    void documentsAreConvertedConcurrentlyInOrder() throws IOException {
        S2PdfUtil.setOfficeCommand(slowSoffice().toString());
        S2PdfUtil.setConversionParallelism(4);
        var start = System.nanoTime();
        try (var doc = load(S2PdfUtil.merge(documents("1", "2", "3", "4"), MergeOptions.create().bookmarks(true)))) {
            var elapsed = Duration.ofNanos(System.nanoTime() - start);
            assertTrue(elapsed.toMillis() < 3000, "four 1-second conversions at once: " + elapsed.toMillis() + "ms");
            assertEquals(1 + 2 + 3 + 4, doc.getNumberOfPages());
            assertEquals(List.of(0, 1, 3, 6), outlinePages(doc), "merged in the given order");
        }
    }

    @Test
    void parallelismOneConvertsOneAtATime() throws IOException {
        S2PdfUtil.setOfficeCommand(slowSoffice().toString());
        S2PdfUtil.setConversionParallelism(1);
        var start = System.nanoTime();
        S2PdfUtil.merge(documents("1", "1", "1")).close();
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() >= 3000);
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.setConversionParallelism(0));
    }

    @Test
    void theLimitHoldsAcrossConcurrentMerges() throws Exception {
        S2PdfUtil.setOfficeCommand(slowSoffice().toString());
        S2PdfUtil.setConversionParallelism(2);
        // Two merges of two documents each, at once: 4 conversions, 2 at a time | 문서 2건짜리 병합 2개 동시: 변환 4건을 2건씩
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = System.nanoTime();
            var first = prefixedDocuments("a", "1", "1");
            var second = prefixedDocuments("b", "2", "2");
            var a = pool.submit(() -> {
                S2PdfUtil.merge(first).close();
                return null;
            });
            var b = pool.submit(() -> {
                S2PdfUtil.merge(second).close();
                return null;
            });
            a.get();
            b.get();
            var elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertTrue(elapsed >= 2000, "no more than 2 conversions at once on the server: " + elapsed + "ms");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void theFirstFailureInOrderIsReported() throws IOException {
        S2PdfUtil.setOfficeCommand(slowSoffice().toString());
        S2PdfUtil.setConversionParallelism(4);
        var sources = new ArrayList<PdfSource>(List.of(PdfSource.ofText("ok")));
        sources.addAll(documents("1", "fail", "fail"));
        var e = assertThrows(IOException.class, () -> S2PdfUtil.merge(sources));
        assertTrue(e.getMessage().startsWith("병합 소스 #3 (DOCUMENT doc1.docx) 처리 실패:"), e.getMessage());
        assertNotNull(e.getCause(), "the conversion error is kept as the cause");
    }
}
