package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2util.exception.S2RuntimeException;

/**
 * File helpers report failures with their cause instead of returning -1, null or false, and leave no partial files.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 파일 헬퍼가 실패를 -1, null, false 대신 원인을 담은 예외로 알리고, 쓰다 만 파일을 남기지 않는지 확인합니다.
 */
class S2FileUtilFailureTest {

    @TempDir
    Path dir;

    /** Returns some bytes, then fails like a dropped connection | 몇 바이트 뒤 연결이 끊긴 것처럼 실패 */
    private static InputStream brokenStream() {
        return new InputStream() {
            private int left = 100;

            @Override
            public int read() throws IOException {
                if (left-- > 0) {
                    return 'x';
                }
                throw new IOException("connection lost");
            }
        };
    }

    @Test
    void streamToFileThrowsAndRemovesThePartialFile() {
        var target = dir.resolve("sub/out.bin");
        var e = assertThrows(S2RuntimeException.class, () -> S2FileUtil.streamToFile(brokenStream(), target));
        assertEquals("connection lost", e.getCause().getMessage());
        assertFalse(Files.exists(target));

        var byName = assertThrows(S2RuntimeException.class,
                () -> S2FileUtil.streamToFile(brokenStream(), dir.resolve("b.bin").toString()));
        assertInstanceOf(IOException.class, byName.getCause());
        assertThrows(IllegalArgumentException.class, () -> S2FileUtil.streamToFile(brokenStream(), " "));
    }

    @Test
    void streamToFileWritesAndCreatesParents() throws IOException {
        var target = dir.resolve("a/b/c.txt");
        assertEquals(7, S2FileUtil.streamToFile(new ByteArrayInputStream("content".getBytes()), target));
        var text = "한글 😀";
        assertEquals(dir.resolve("r.txt"), S2FileUtil.streamToFile(new StringReader(text), dir.resolve("r.txt").toString()));
        assertEquals(text, Files.readString(dir.resolve("r.txt")));
    }

    @Test
    void tempFilesFailLoudlyAndAreCleanedUp() throws IOException {
        assertThrows(S2RuntimeException.class, () -> S2FileUtil.streamToTempFile(brokenStream()));

        var seen = new AtomicReference<Path>();
        var size = S2FileUtil.processStreamWithTempFile(new ByteArrayInputStream("abc".getBytes()), "txt", path -> {
            seen.set(path);
            return S2FileUtil.getSize(path);
        });
        assertEquals(3, size);
        assertTrue(seen.get().toString().endsWith(".txt"));
        assertFalse(Files.exists(seen.get()), "temp file removed after processing");

        // The processor's own exception propagates and the temp file is still removed | 처리 중 예외도 전파되고 임시 파일은 삭제됨
        var failing = new AtomicReference<Path>();
        assertThrows(IllegalStateException.class,
                () -> S2FileUtil.processStreamWithTempFile(new ByteArrayInputStream("abc".getBytes()), null, path -> {
                    failing.set(path);
                    throw new IllegalStateException("processing failed");
                }));
        assertFalse(Files.exists(failing.get()));

        // A failed copy never reaches the processor with null | 복사 실패 시 null 로 처리기를 부르지 않음
        assertThrows(S2RuntimeException.class,
                () -> S2FileUtil.processStreamWithTempFile(brokenStream(), null, path -> fail("processor called")));
    }

    @Test
    void makeDirectoryReportsCreatedExistingAndFailure() throws IOException {
        var created = dir.resolve("x/y");
        assertTrue(S2FileUtil.makeDirectory(created));
        assertFalse(S2FileUtil.makeDirectory(created));
        var file = Files.writeString(dir.resolve("file"), "f");
        assertThrows(S2RuntimeException.class, () -> S2FileUtil.makeDirectory(file.resolve("child")));
        assertThrows(IllegalArgumentException.class, () -> S2FileUtil.makeDirectory(" "));
    }

    @Test
    void deleteDistinguishesAbsentDeletedAndFailed() throws IOException {
        assertFalse(S2FileUtil.delete(dir.resolve("absent")));
        assertFalse(S2FileUtil.delete((Path) null));
        var tree = Files.createDirectories(dir.resolve("tree/a/b"));
        Files.writeString(tree.resolve("f.txt"), "x");
        assertTrue(S2FileUtil.delete(dir.resolve("tree")));
        assertFalse(Files.exists(dir.resolve("tree")));

        // A read-only directory cannot lose its children (POSIX, not root) | 읽기 전용 디렉토리의 하위 항목은 지울 수 없음
        Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        var locked = Files.createDirectories(dir.resolve("locked"));
        var child = Files.writeString(locked.resolve("child.txt"), "x");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            Assumptions.assumeFalse(Files.isWritable(locked), "running as root");
            var e = assertThrows(S2RuntimeException.class, () -> S2FileUtil.delete(locked));
            assertInstanceOf(IOException.class, e.getCause());
            assertTrue(Files.exists(child));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void openingAndSizingMissingFilesThrow() {
        var missing = dir.resolve("missing.txt");
        assertThrows(S2RuntimeException.class, () -> S2FileUtil.getSize(missing));
        assertThrows(S2RuntimeException.class, () -> S2FileUtil.fileToInputStream(missing));
        var e = assertThrows(S2RuntimeException.class, () -> S2FileUtil.fileToReader(missing));
        assertNotNull(e.getCause());
        assertThrows(IllegalArgumentException.class, () -> S2FileUtil.fileToReader(""));
        assertThrows(IllegalArgumentException.class, () -> S2FileUtil.fileToInputStream((String) null));
    }

    @Test
    void readsThroughTheHelpers() throws IOException {
        var file = Files.writeString(dir.resolve("in.txt"), "hello", StandardCharsets.UTF_8);
        try (var in = S2FileUtil.fileToInputStream(file.toString()); var reader = S2FileUtil.fileToReader(file)) {
            assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals('h', reader.read());
        }
    }
}
