package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import io.github.devers2.s2util.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2util.support.S2PdfUtil.PdfSource;

/**
 * Conversion result cache: a converted source is reused only for the same content and settings, fallbacks are not
 * kept, and cleanup runs at most once a day or when over the size limit.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 변환 결과 캐시: 같은 내용·설정일 때만 재사용하고, 대체 결과는 저장하지 않으며, 정리는 하루 한 번 또는 크기 상한을 넘을 때만 하는지 확인합니다.
 */
class S2PdfCacheTest {

    @TempDir
    Path dir;

    private Path cache;
    private Path samplePdf;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "shell scripts");
        cache = dir.resolve("cache");
        S2PdfUtil.setConversionCache(cache, 1024L * 1024 * 1024, Duration.ofDays(1), 1);
        S2PdfUtil.setBrowserRenderingEnabled(false);
        samplePdf = dir.resolve("converted.pdf");
        try (var doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(samplePdf.toFile());
        }
    }

    @AfterEach
    void tearDown() {
        S2PdfUtil.resetConversionCache();
        S2PdfUtil.resetOfficeCommand();
        S2PdfUtil.resetBrowserCommand();
        S2PdfUtil.setBrowserRenderingEnabled(true);
    }

    /** A soffice stand-in that logs each call | 호출을 기록하는 soffice 대역 */
    private Path fakeSoffice(String name) throws IOException {
        var script = dir.resolve(name);
        Files.writeString(script, """
                #!/usr/bin/env bash
                echo "$@" >> "%s/soffice-calls.log"
                out=""; input=""
                while [ $# -gt 0 ]; do
                  case "$1" in
                    --outdir) out="$2"; shift 2 ;;
                    --convert-to) shift 2 ;;
                    -*) shift ;;
                    *) input="$1"; shift ;;
                  esac
                done
                base="$(basename "$input")"; cp "%s" "$out/${base%%.*}.pdf"
                """.formatted(dir, samplePdf));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private long calls() throws IOException {
        var log = dir.resolve("soffice-calls.log");
        return Files.exists(log) ? Files.readAllLines(log).size() : 0;
    }

    private static PDDocument load(InputStream merged) throws IOException {
        try (merged) {
            return Loader.loadPDF(merged.readAllBytes());
        }
    }

    private List<Path> entries() throws IOException {
        if (!Files.isDirectory(cache)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(cache)) {
            return walk.filter(p -> p.toString().endsWith(".pdf")).toList();
        }
    }

    private static final MergeOptions CACHED = MergeOptions.create().cache(true);

    @Test
    void theSameDocumentIsConvertedOnce() throws IOException {
        S2PdfUtil.setOfficeCommand(fakeSoffice("soffice").toString());
        var docx = Files.writeString(dir.resolve("plan.docx"), "docx bytes");
        for (int i = 0; i < 3; i++) {
            try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofText("cover"), PdfSource.ofDocument(docx)), CACHED))) {
                assertEquals(3, doc.getNumberOfPages());
            }
        }
        assertEquals(1, calls(), "converted once, then reused");
        // A stream with the same content hits the same entry | 같은 내용의 스트림도 같은 항목
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofDocument(
                new ByteArrayInputStream("docx bytes".getBytes()), "다른 이름.docx")), CACHED))) {
            assertEquals(2, doc.getNumberOfPages());
        }
        assertEquals(1, calls());
        assertTrue(Files.exists(docx), "the source is left alone");
    }

    @Test
    void theCacheIsUsedOnlyWhenAsked() throws IOException {
        S2PdfUtil.setOfficeCommand(fakeSoffice("soffice").toString());
        var docx = Files.writeString(dir.resolve("plan.docx"), "docx bytes");
        for (int i = 0; i < 2; i++) {
            S2PdfUtil.merge(List.of(PdfSource.ofDocument(docx))).close();
        }
        assertEquals(2, calls());
        assertEquals(List.of(), entries(), "nothing stored without cache(true)");
    }

    @Test
    void changedContentOrConverterIsConvertedAgain() throws IOException {
        S2PdfUtil.setOfficeCommand(fakeSoffice("soffice").toString());
        var docx = Files.writeString(dir.resolve("plan.docx"), "version 1");
        S2PdfUtil.merge(List.of(PdfSource.ofDocument(docx)), CACHED).close();
        Files.writeString(docx, "version 2");
        S2PdfUtil.merge(List.of(PdfSource.ofDocument(docx)), CACHED).close();
        assertEquals(2, calls(), "new content");

        // Upgrading the converter (a different wrapper) | 변환기 업그레이드 (다른 래퍼)
        var upgraded = fakeSoffice("soffice-v2");
        Files.writeString(upgraded, Files.readString(upgraded) + "# v2\n");
        S2PdfUtil.setOfficeCommand(upgraded.toString());
        S2PdfUtil.merge(List.of(PdfSource.ofDocument(docx)), CACHED).close();
        assertEquals(3, calls(), "new converter");
        assertEquals(3, entries().size());
    }

    @Test
    void htmlAndTextResultsAreReused() throws IOException {
        var sources = List.of(PdfSource.ofHtml("<h1>Cached report</h1>"), PdfSource.ofText("plain"));
        try (var doc = load(S2PdfUtil.merge(sources, CACHED))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("Cached report"));
        }
        var stored = entries();
        assertEquals(2, stored.size());
        var times = new ArrayList<FileTime>();
        for (var entry : stored) {
            Files.setLastModifiedTime(entry, FileTime.from(Instant.now().minusSeconds(3600)));
            times.add(Files.getLastModifiedTime(entry));
        }
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofHtml("<h1>Cached report</h1>"), PdfSource.ofText("plain")),
                CACHED))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("Cached report"));
        }
        assertEquals(2, entries().size());
        for (int i = 0; i < stored.size(); i++) {
            assertTrue(Files.getLastModifiedTime(stored.get(i)).compareTo(times.get(i)) > 0, "a hit marks the entry as used");
        }
    }

    @Test
    void pdfAndJpegSourcesTakeNoCacheSpace() throws IOException {
        var pdf = dir.resolve("scan.pdf");
        Files.copy(samplePdf, pdf);
        var jpeg = new java.io.ByteArrayOutputStream();
        var png = new java.io.ByteArrayOutputStream();
        var image = new java.awt.image.BufferedImage(50, 50, java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(image, "jpg", jpeg);
        javax.imageio.ImageIO.write(image, "png", png);
        S2PdfUtil.merge(List.of(PdfSource.ofPdf(pdf), PdfSource.ofImage(jpeg.toByteArray())), CACHED).close();
        assertEquals(List.of(), entries(), "nothing to convert, nothing stored");
        S2PdfUtil.merge(List.of(PdfSource.ofImage(png.toByteArray())), CACHED).close();
        assertEquals(1, entries().size(), "PNG is decoded, so it is cached");
    }

    @Test
    void aBrowserFallbackIsNotCached() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = "<html><body><p>WEB PAGE</p></body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/page.html";
            var failing = dir.resolve("chrome-fail");
            Files.writeString(failing, "#!/usr/bin/env bash\nexit 1\n");
            Files.setPosixFilePermissions(failing, PosixFilePermissions.fromString("rwx------"));
            S2PdfUtil.setBrowserRenderingEnabled(true);
            S2PdfUtil.setBrowserCommand(failing.toString());
            try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofUrl(url)), CACHED))) {
                assertTrue(new PDFTextStripper().getText(doc).contains("WEB PAGE"), "fallback rendering");
            }
            assertEquals(List.of(), entries(), "a fallback is not kept");

            var working = dir.resolve("chrome-ok");
            Files.writeString(working, "#!/usr/bin/env bash\ncp \"" + samplePdf + "\" \"$2\"\n");
            Files.setPosixFilePermissions(working, PosixFilePermissions.fromString("rwx------"));
            S2PdfUtil.setBrowserCommand(working.toString());
            try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofUrl(url)), CACHED))) {
                assertEquals(2, doc.getNumberOfPages(), "the browser's result");
            }
            assertEquals(1, entries().size());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cleanupRemovesExpiredThenLeastRecentlyUsed() throws IOException {
        S2PdfUtil.merge(List.of(PdfSource.ofText("a"), PdfSource.ofText("b"), PdfSource.ofText("c")), CACHED).close();
        var stored = entries();
        assertEquals(3, stored.size());
        var size = Files.size(stored.get(0));
        // One expired, two kept but over a limit that fits one | 하나는 기간 만료, 둘은 한 개만 들어가는 상한 초과
        Files.setLastModifiedTime(stored.get(0), FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        Files.setLastModifiedTime(stored.get(1), FileTime.from(Instant.now().minusSeconds(600)));
        Files.setLastModifiedTime(stored.get(2), FileTime.from(Instant.now()));
        S2PdfUtil.setConversionCache(cache, size + size / 2, Duration.ofDays(1), 1);
        S2PdfCache.cleanup();
        assertEquals(List.of(stored.get(2)), entries(), "expired one and least recently used one removed");
        assertTrue(Files.exists(cache.resolve(".last-cleanup")));
    }

    @Test
    void cleanupStartsOnARequestWhenTheLastOneWasBeforeToday() throws Exception {
        S2PdfUtil.merge(List.of(PdfSource.ofText("old")), CACHED).close();
        var old = entries().get(0);
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(3))));
        var marker = cache.resolve(".last-cleanup");
        Files.write(marker, new byte[0]);
        Files.setLastModifiedTime(marker, FileTime.from(Instant.now().minus(Duration.ofDays(1))));

        S2PdfUtil.merge(List.of(PdfSource.ofText("new")), CACHED).close();
        for (int i = 0; i < 100 && Files.exists(old); i++) {
            Thread.sleep(50);
        }
        assertFalse(Files.exists(old), "cleaned in the background on a request");
        assertTrue(Files.getLastModifiedTime(marker).toInstant().isAfter(Instant.now().minusSeconds(60)));
    }

    @Test
    void lowDiskSpaceSkipsStoringButStillConverts() throws IOException {
        S2PdfUtil.setConversionCache(cache, 1024L * 1024 * 1024, Duration.ofDays(1), Long.MAX_VALUE / 2);
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofText("no room")), CACHED))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("no room"));
        }
        assertEquals(List.of(), entries());
    }

    @Test
    void theFolderIsPrivateAndConcurrentRequestsAgree() throws Exception {
        S2PdfUtil.setOfficeCommand(fakeSoffice("soffice").toString());
        var docx = Files.writeString(dir.resolve("same.docx"), "same content");
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<Integer>>();
            for (int i = 0; i < 16; i++) {
                tasks.add(() -> {
                    try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofDocument(docx)), CACHED))) {
                        return doc.getNumberOfPages();
                    }
                });
            }
            for (var result : pool.invokeAll(tasks)) {
                assertEquals(2, result.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(1, entries().size());
        assertTrue(calls() < 16, "later requests reuse the result: " + calls());
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(cache)));

        S2PdfUtil.clearConversionCache();
        assertEquals(List.of(), entries());
    }

    @Test
    void preparedSourcesMakeTheFirstMergeFast() throws Exception {
        S2PdfUtil.setOfficeCommand(fakeSoffice("soffice").toString());
        var docx = Files.writeString(dir.resolve("uploaded.docx"), "uploaded");
        var html = "<h1>note</h1>";
        // Right after an upload | 업로드 직후
        S2PdfUtil.prepare(List.of(PdfSource.ofDocument(docx), PdfSource.ofHtml(html)), null).get(30, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1, calls());
        assertEquals(2, entries().size());
        // The first view uses the prepared results | 처음 볼 때 미리 변환한 결과를 씀
        try (var doc = load(S2PdfUtil.merge(List.of(PdfSource.ofHtml(html), PdfSource.ofDocument(docx)),
                MergeOptions.create().cache(true).pageNumbers(true)))) {
            assertEquals(3, doc.getNumberOfPages());
        }
        assertEquals(1, calls(), "not converted again");
    }

    @Test
    void preparingUsesTheImageSettingsOfTheLaterMerge() throws Exception {
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3000, 2000, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "png", png);
        var image = png.toByteArray();
        S2PdfUtil.prepare(List.of(PdfSource.ofImage(image)), MergeOptions.create().imageDpi(150)).get(30,
                java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1, entries().size());
        S2PdfUtil.merge(List.of(PdfSource.ofImage(image)), MergeOptions.create().imageDpi(150).cache(true)).close();
        assertEquals(1, entries().size(), "same settings: reused");
        S2PdfUtil.merge(List.of(PdfSource.ofImage(image)), CACHED).close();
        assertEquals(2, entries().size(), "other settings: another result");
    }

    @Test
    void aFailedPreparationDoesNotThrow() throws IOException {
        S2PdfUtil.setOfficeCommand(dir.resolve("missing-soffice").toString());
        var future = S2PdfUtil.prepare(PdfSource.ofDocument(Files.writeString(dir.resolve("a.docx"), "x")),
                PdfSource.ofText("still prepared"));
        var e = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> future.get(30, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(e.getCause().getMessage().contains("s2-office-converter"), e.getCause().getMessage());
        assertEquals(1, entries().size(), "the other source was still prepared");
    }

    @Test
    void settingsAreChecked() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.setConversionCache(cache, 1, Duration.ZERO, 0));
        // 0 = defaults: reserve min(10% of disk, 20GB), size limit half of it | 0 = 기본값
        S2PdfUtil.setConversionCache(cache, 0, Duration.ofDays(1), 0);
        Files.createDirectories(cache);
        var total = Files.getFileStore(cache).getTotalSpace();
        assertEquals(Math.min(total / 10, 20L << 30) / 2, S2PdfCache.maxBytes());
        S2PdfUtil.setConversionCache(cache, 0, Duration.ofDays(1), 4L << 30);
        assertEquals(2L << 30, S2PdfCache.maxBytes(), "half of a configured reserve");
        S2PdfUtil.setConversionCache(cache, 123, Duration.ofDays(1), 0);
        assertEquals(123, S2PdfCache.maxBytes());

        // One setting at a time; the others stay | 하나씩 바꾸고 나머지는 유지
        S2PdfUtil.setConversionCacheMaxBytes(0);
        S2PdfUtil.setConversionCacheMinFreeBytes(6L << 30);
        assertEquals(3L << 30, S2PdfCache.maxBytes(), "half of the new reserve");
        S2PdfUtil.setConversionCacheMaxBytes(456);
        assertEquals(456, S2PdfCache.maxBytes());
        S2PdfUtil.setConversionCacheMaxAge(Duration.ofHours(6));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.setConversionCacheMaxAge(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.setConversionCacheMaxAge(null));
        S2PdfUtil.setConversionCacheDirectory(dir.resolve("other"));
        assertEquals(dir.resolve("other"), S2PdfCache.directory());
        assertEquals(456, S2PdfCache.maxBytes(), "unchanged by the folder change");
    }

    @Test
    void anExpiredEntryIsNotServedBeforeCleanup() throws IOException {
        S2PdfUtil.setConversionCacheMaxAge(Duration.ofHours(24));
        S2PdfUtil.setOfficeCommand(fakeSoffice("soffice").toString());
        var docx = Files.writeString(dir.resolve("old.docx"), "old");
        S2PdfUtil.merge(List.of(PdfSource.ofDocument(docx)), CACHED).close();
        Files.setLastModifiedTime(entries().get(0), FileTime.from(Instant.now().minus(Duration.ofHours(25))));
        Files.write(cache.resolve(".last-cleanup"), new byte[0]); // cleaned today: no cleanup runs | 오늘 정리함
        S2PdfUtil.merge(List.of(PdfSource.ofDocument(docx)), CACHED).close();
        assertEquals(2, calls(), "past 24 hours it is converted again");
    }
}
