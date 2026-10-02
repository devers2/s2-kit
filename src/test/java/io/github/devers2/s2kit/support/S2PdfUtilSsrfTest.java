package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * HTML rendering never fetches URLs written in the HTML (SSRF), and URL sources accept only http(s).
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * HTML 렌더링이 HTML 에 적힌 URL 을 가져오지 않는지(SSRF), URL 소스가 http(s)만 받는지 확인합니다.
 */
class S2PdfUtilSsrfTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void renderingDoesNotFetchUrlsInTheHtml() throws IOException {
        var base = "http://127.0.0.1:" + server.getAddress().getPort();
        var html = "<html><head><link rel=\"stylesheet\" href=\"" + base + "/style.css\"/>"
                + "<style>.a { background-image: url('" + base + "/bg.png'); }</style></head><body>"
                + "<img src=\"" + base + "/img.png\"/><img src=\"//127.0.0.1:" + server.getAddress().getPort()
                + "/proto.png\"/><img src=\"file:///etc/hostname\"/><div class=\"a\">x</div></body></html>";
        var pdf = S2PdfUtil.convertHtmlToPdf(html, null, null, null, null);
        assertFalse(pdf.isEmpty());
        assertEquals(0, hits.get(), "the renderer must not request URLs from the HTML");
    }

    @Test
    void urlSourcesAcceptOnlyHttp() {
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.PdfSource.ofUrl("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.PdfSource.ofUrl("jar:file:/a.jar!/x"));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.PdfSource.ofUrl("relative/path"));
        assertNotNull(S2PdfUtil.PdfSource.ofUrl("https://example.com/a.pdf"));
    }
}
