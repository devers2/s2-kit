package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;

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

import io.github.devers2.s2kit.support.S2PdfUtil.PdfSource;

/**
 * Web pages are printed by the browser (s2-chrome) when it is installed, and fall back to the built-in renderer when
 * it is missing, fails or hangs.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 웹 페이지는 브라우저(s2-chrome)가 있으면 브라우저로 인쇄하고, 없거나 실패하거나 멈추면 내장 렌더러로 변환하는지 확인합니다.
 */
class S2PdfBrowserTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private String base;
    private Path browserPdf;

    @BeforeEach
    void setUp() throws IOException {
        Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "shell scripts");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            byte[] body;
            String type;
            if (path.equals("/logo.png")) {
                body = java.util.Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC");
                type = "image/png";
            } else {
                body = "<html><head><style>.row{display:flex}</style></head><body><h1>PAGE TEXT</h1><img src=\"logo.png\"></body></html>"
                        .getBytes(StandardCharsets.UTF_8);
                type = "text/html; charset=UTF-8";
            }
            exchange.getResponseHeaders().add("Content-Type", type);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        // A three-page PDF stands for the browser's output | 브라우저 결과 대역 (3쪽)
        browserPdf = dir.resolve("browser.pdf");
        try (var doc = new PDDocument()) {
            for (int i = 0; i < 3; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(browserPdf.toFile());
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        S2PdfUtil.resetBrowserCommand();
        S2PdfUtil.setBrowserRenderingEnabled(true);
        S2PdfUtil.setBrowserTimeout(Duration.ofSeconds(60));
    }

    /** An s2-chrome stand-in: "ok", "fail", "hang" or "garbage" | s2-chrome 대역 */
    private Path fakeBrowser(String behavior) throws IOException {
        var script = dir.resolve("fake-chrome-" + behavior);
        Files.writeString(script, """
                #!/usr/bin/env bash
                echo "$@" >> "%1$s/calls.log"
                [ "$1" = "--print-to-pdf" ] || exit 2
                cp "$3" "%1$s/received.html"
                case "%2$s" in
                  ok) cp "%3$s" "$2" ;;
                  fail) echo "crashed" ; exit 1 ;;
                  hang) sleep 30 ;;
                  garbage) echo "not a pdf" > "$2" ;;
                esac
                """.formatted(dir, behavior, browserPdf));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private static PDDocument load(InputStream merged) throws IOException {
        try (merged) {
            return Loader.loadPDF(merged.readAllBytes());
        }
    }

    @Test
    void webPagesArePrintedByTheBrowser() throws IOException {
        S2PdfUtil.setBrowserCommand(fakeBrowser("ok").toString());
        assertTrue(S2PdfUtil.isBrowserRenderingAvailable());
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            assertEquals(3, doc.getNumberOfPages(), "the browser's PDF is used");
        }
        var received = Files.readString(dir.resolve("received.html"));
        assertTrue(received.contains("data:image/png;base64,"), "the browser gets a self-contained file");
        assertTrue(received.contains("size: A4"), received);
        assertFalse(received.contains(base), "no address left for the browser to fetch");
    }

    @Test
    void failuresFallBackToTheBuiltInRenderer() throws IOException {
        for (var behavior : new String[] { "fail", "garbage" }) {
            S2PdfUtil.setBrowserCommand(fakeBrowser(behavior).toString());
            try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
                assertTrue(new PDFTextStripper().getText(doc).contains("PAGE TEXT"), behavior);
            }
        }
        S2PdfUtil.setBrowserCommand(dir.resolve("missing-chrome").toString());
        assertFalse(S2PdfUtil.isBrowserRenderingAvailable());
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("PAGE TEXT"));
        }
    }

    @Test
    void aHungBrowserIsStoppedAndFallsBack() throws IOException {
        S2PdfUtil.setBrowserCommand(fakeBrowser("hang").toString());
        S2PdfUtil.setBrowserTimeout(Duration.ofMillis(500));
        var start = System.nanoTime();
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("PAGE TEXT"));
        }
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 10);
    }

    @Test
    void theBrowserIsUsedOnlyForWebPagesAndCanBeTurnedOff() throws IOException {
        S2PdfUtil.setBrowserCommand(fakeBrowser("ok").toString());
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofHtml("<p>string html</p>")))) {
            assertEquals(1, doc.getNumberOfPages());
        }
        S2PdfUtil.setBrowserRenderingEnabled(false);
        assertFalse(S2PdfUtil.isBrowserRenderingAvailable());
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("PAGE TEXT"));
        }
        assertFalse(Files.exists(dir.resolve("calls.log")), "never called");
    }

    @Test
    void realBrowserWhenInstalled() throws IOException {
        S2PdfUtil.resetBrowserCommand();
        Assumptions.assumeTrue(S2PdfUtil.isBrowserRenderingAvailable(), "no s2-chrome installed");
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("PAGE TEXT"));
            var box = doc.getPage(0).getMediaBox();
            assertEquals(595, box.getWidth(), 2, "A4");
        }
    }
}
