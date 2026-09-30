package io.github.devers2.s2util.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import io.github.devers2.s2util.exception.S2RuntimeException;

/**
 * {@link FileManager#downloadRemoteFile}: one request, http(s) only, no escape from the save path.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 원격 파일 다운로드가 요청을 한 번만 보내고, http(s)만 받으며, 저장 경로를 벗어나지 않는지 확인합니다.
 */
class FileManagerDownloadTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            requests.incrementAndGet();
            var body = "content".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.getResponseHeaders().add("Content-Disposition",
                    "attachment; filename=\"fallback.txt\"; filename*=UTF-8''%ED%95%9C.txt");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void downloadsWithASingleRequest() throws IOException {
        var remote = FileManager.downloadRemoteFile(url("/file"), dir.toString(), "saved.txt");
        assertEquals(1, requests.get());
        assertEquals("content", Files.readString(dir.resolve("saved.txt")));
        assertEquals("한.txt", remote.getName());
        assertEquals(7, remote.getSize());
        assertTrue(remote.getContentType().startsWith("text/plain"));
    }

    @Test
    void rejectsNonHttpSchemesAndFailedResponses() throws IOException {
        var secret = Files.writeString(dir.resolve("secret.txt"), "secret");
        assertThrows(IllegalArgumentException.class,
                () -> FileManager.downloadRemoteFile(secret.toUri().toString(), dir.toString(), "copy.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> FileManager.downloadRemoteFile("jar:file:/x.jar!/a", dir.toString(), "copy.txt"));
        assertFalse(Files.exists(dir.resolve("copy.txt")));

        assertThrows(S2RuntimeException.class, () -> FileManager.downloadRemoteFile(url("/missing"), dir.toString(), "m.txt"));
        assertFalse(Files.exists(dir.resolve("m.txt")));
    }

    @Test
    void saveNameCannotLeaveTheSavePath() {
        var save = dir.resolve("save");
        assertThrows(S2RuntimeException.class,
                () -> FileManager.downloadRemoteFile(url("/file"), save.toString(), "../escaped.txt"));
        assertFalse(Files.exists(dir.resolve("escaped.txt")));
    }
}
