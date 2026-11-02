package io.github.devers2.s2kit.spring;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.sun.net.httpserver.HttpServer;

/**
 * Raw response bodies, form vs multipart POST, and custom request headers.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 응답 본문을 가공하지 않는지, POST 가 폼/멀티파트를 고르는지, 요청 헤더가 전달되는지 확인합니다.
 */
class S2RestApiUtilTest {

    private HttpServer server;
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            // A JSON string containing an escaped quote | 이스케이프된 따옴표가 든 JSON 문자열
            var response = "{\"msg\":\"say \\u0022hi\\u0022\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @Test
    void responseBodyIsReturnedAsIs() {
        var result = S2RestApiUtil.callApi(url(), HttpMethod.GET, Map.entry("q", "1"));
        assertEquals("{\"msg\":\"say \\u0022hi\\u0022\"}", result, "unicode escapes must not be decoded");
    }

    @Test
    void postIsAFormWithoutFilesAndMultipartWithFiles() {
        S2RestApiUtil.callApi(url(), HttpMethod.POST, Map.entry("a", "1"), Map.entry("b", 2));
        assertTrue(contentType.get().startsWith("application/x-www-form-urlencoded"), contentType.get());
        assertEquals("a=1&b=2", body.get());

        var file = S2RestApiUtil.createInputStreamResource(new ByteArrayInputStream(new byte[] { 1, 2 }), "f.bin", 2);
        S2RestApiUtil.callApi(url(), HttpMethod.POST, Map.entry("a", "1"), Map.entry("file", file));
        assertTrue(contentType.get().startsWith("multipart/form-data"), contentType.get());
    }

    @Test
    void requestHeadersAreSent() {
        var headers = new HttpHeaders();
        headers.setBearerAuth("token");
        S2RestApiUtil.callApi(url(), HttpMethod.GET, null, headers);
        assertEquals("Bearer token", authorization.get());
    }
}
