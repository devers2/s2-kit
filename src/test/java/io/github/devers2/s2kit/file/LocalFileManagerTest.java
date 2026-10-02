package io.github.devers2.s2kit.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2kit.file.impl.S2FileManagerImpl;

/**
 * The local file manager follows the same overwrite rule as the SFTP managers, atomically.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 로컬 파일 관리자가 SFTP 관리자와 같은 덮어쓰기 규칙을 원자적으로 따르는지 확인합니다.
 */
class LocalFileManagerTest {

    @TempDir
    Path dir;

    @Test
    void overwritesOnlyWhenAsked() throws IOException {
        var files = new S2FileManagerImpl();
        SftpIntegrationTest.assertOverwriteContract(files, dir.toString(), () -> {
            try {
                return Files.readString(dir.resolve("same.txt"));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    @Test
    void concurrentWritesOfTheSameNameLeaveOneWinner() throws Exception {
        var files = new S2FileManagerImpl();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                var body = "writer-" + i;
                Callable<Boolean> task = () -> {
                    start.await();
                    try {
                        files.writeFile(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), dir.toString(),
                                "race.txt");
                        return true;
                    } catch (S2RuntimeException e) {
                        return false;
                    }
                };
                tasks.add(pool.submit(task));
            }
            start.countDown();
            int winners = 0;
            for (var task : tasks) {
                winners += task.get() ? 1 : 0;
            }
            assertEquals(1, winners);
            assertTrue(Files.readString(dir.resolve("race.txt")).startsWith("writer-"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedWriteLeavesNoPartialFile() {
        var files = new S2FileManagerImpl();
        InputStream broken = new InputStream() {
            private int left = 10;

            @Override
            public int read() throws IOException {
                if (left-- > 0) {
                    return 'x';
                }
                throw new IOException("connection lost");
            }
        };
        var e = assertThrows(S2RuntimeException.class, () -> files.writeFile(broken, dir.toString(), "partial.txt"));
        assertEquals("connection lost", e.getCause().getMessage());
        assertFalse(Files.exists(dir.resolve("partial.txt")));
    }
}
