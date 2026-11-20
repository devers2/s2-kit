package io.github.devers2.s2kit.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2kit.file.impl.S2FileManagerImpl;
import io.github.devers2.s2util.exception.S2RuntimeException;

class S2UploadQueueTest {

    @TempDir
    Path dir;

    private static InputStream data(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /** A local file manager that counts parallel writes and can be held or made to fail | 동시 쓰기 수를 세고, 멈추거나 실패시킬 수 있는 로컬 관리자 */
    static class TestManager implements FileManager {
        final FileManager local = new S2FileManagerImpl();
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        volatile CountDownLatch hold = new CountDownLatch(0);
        volatile int failFirst = 0;
        volatile RuntimeException failure = new S2RuntimeException("connection reset");
        final java.util.List<String> resumeKeys = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicInteger discarded = new AtomicInteger();

        @Override
        public long writeFile(Path source, String savePath, String saveName, boolean overwrite, String resumeKey) {
            resumeKeys.add(resumeKey);
            return FileManager.super.writeFile(source, savePath, saveName, overwrite, resumeKey);
        }

        @Override
        public void discardPartial(String savePath, String saveName, String resumeKey) {
            discarded.incrementAndGet();
        }

        @Override
        public boolean isRemote() {
            return false;
        }

        @Override
        public long writeFile(InputStream fileData, String savePath, String saveName, boolean overwrite) {
            var call = calls.incrementAndGet();
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                hold.await(30, TimeUnit.SECONDS);
                if (call <= failFirst) {
                    throw failure;
                }
                return local.writeFile(fileData, savePath, saveName, overwrite);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new S2RuntimeException("interrupted", e);
            } finally {
                running.decrementAndGet();
            }
        }

        @Override
        public InputStream readFile(String savePath, String saveName) {
            return local.readFile(savePath, saveName);
        }

        @Override
        public void deleteFile(String savePath, String saveName) {
            local.deleteFile(savePath, saveName);
        }
    }

    private S2UploadQueue.Builder queue(FileManager manager) {
        return S2UploadQueue.builder(manager).tempDir(dir.resolve("tmp")).retryDelay(Duration.ofMillis(10))
                .minFreeDiskBytes(0);
    }

    private long tempFiles() throws IOException {
        try (var files = Files.list(dir.resolve("tmp"))) {
            return files.count();
        }
    }

    @Test
    void manyUploadsRunWithTheGivenParallelism() throws Exception {
        var manager = new TestManager();
        var out = dir.resolve("out");
        try (var queue = queue(manager).concurrency(4).build()) {
            var futures = new ArrayList<CompletableFuture<Long>>();
            for (var i = 0; i < 200; i++) {
                futures.add(queue.submit(data("file-" + i), out.toString(), "f" + i + ".txt"));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
            assertEquals(0, queue.getQueuedCount());
        }
        for (var i = 0; i < 200; i++) {
            assertEquals("file-" + i, Files.readString(out.resolve("f" + i + ".txt")));
        }
        assertTrue(manager.peak.get() <= 4, "parallel transfers: " + manager.peak.get());
        assertEquals(0, tempFiles(), "temp files are deleted");
    }

    @Test
    void aFullQueueRejectsAtOnce() throws Exception {
        var manager = new TestManager();
        manager.hold = new CountDownLatch(1);
        try (var queue = queue(manager).concurrency(1).maxQueued(2).build()) {
            var first = queue.submit(data("a"), dir.toString(), "a.txt");
            var second = queue.submit(data("b"), dir.toString(), "b.txt");
            var started = System.nanoTime();
            assertThrows(RejectedExecutionException.class, () -> queue.submit(data("c"), dir.toString(), "c.txt"));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000, "rejected without waiting");
            assertEquals(2, tempFiles(), "the rejected upload left no temp file");

            manager.hold.countDown();
            assertEquals(1L, first.get(10, TimeUnit.SECONDS));
            assertEquals(1L, second.get(10, TimeUnit.SECONDS));
            assertEquals(1L, queue.submit(data("c"), dir.toString(), "c.txt").get(10, TimeUnit.SECONDS), "room again");
        }
    }

    @Test
    void temporaryFailuresAreRetried() throws Exception {
        var manager = new TestManager();
        manager.failFirst = 2;
        try (var queue = queue(manager).maxAttempts(3).build()) {
            assertEquals(2L, queue.submit(data("ok"), dir.toString(), "r.txt").get(10, TimeUnit.SECONDS));
            assertEquals(3, manager.calls.get());
            // Every attempt carries the same key, so SFTP continues where the last one stopped | 모든 시도가 같은 키라 이어 씀
            assertEquals(3, manager.resumeKeys.size());
            assertEquals(1, new java.util.HashSet<>(manager.resumeKeys).size(), manager.resumeKeys.toString());
            assertNotNull(manager.resumeKeys.get(0));
            assertEquals(0, manager.discarded.get(), "nothing to discard after success");
        }
    }

    @Test
    void retriesStopAtMaxAttempts() throws Exception {
        var manager = new TestManager();
        manager.failFirst = 10;
        try (var queue = queue(manager).maxAttempts(3).build()) {
            var e = assertThrows(ExecutionException.class,
                    () -> queue.submit(data("x"), dir.toString(), "m.txt").get(10, TimeUnit.SECONDS));
            assertEquals("connection reset", e.getCause().getMessage());
            assertEquals(3, manager.calls.get());
            assertEquals(1, manager.discarded.get(), "the partial file is discarded when the upload is given up");
            assertEquals(0, queue.getQueuedCount());
        }
    }

    @Test
    void anExistingFileIsNotRetried() throws Exception {
        var manager = new TestManager();
        Files.writeString(dir.resolve("same.txt"), "old");
        try (var queue = queue(manager).maxAttempts(3).build()) {
            var e = assertThrows(ExecutionException.class,
                    () -> queue.submit(data("new"), dir.toString(), "same.txt").get(10, TimeUnit.SECONDS));
            assertInstanceOf(S2FileExistsException.class, e.getCause());
            assertEquals(1, manager.calls.get());
            assertEquals("old", Files.readString(dir.resolve("same.txt")));
        }
    }

    @Test
    void badInputIsRejectedBeforeQueueing() throws Exception {
        var manager = new TestManager();
        try (var queue = queue(manager).maxFileBytes(3).build()) {
            assertThrows(S2RuntimeException.class, () -> queue.submit(data("x"), dir.toString(), "../escape.txt"));
            assertThrows(S2RuntimeException.class, () -> queue.submit(data("toolong"), dir.toString(), "big.txt"));
            assertEquals(0, queue.getQueuedCount());
            assertEquals(0, tempFiles());
            assertEquals(0, manager.calls.get());
        }
    }

    @Test
    void closeCancelsWhatIsLeftAndCleansUp() throws Exception {
        var manager = new TestManager();
        manager.hold = new CountDownLatch(1);
        var queue = queue(manager).concurrency(1).build();
        var futures = new ArrayList<CompletableFuture<Long>>();
        for (var i = 0; i < 5; i++) {
            futures.add(queue.submit(data("x"), dir.toString(), "c" + i + ".txt"));
        }
        queue.close(Duration.ofMillis(200));
        for (var future : futures) {
            // Cancelled ones throw CancellationException; the running one fails when interrupted | 취소된 것은
            // CancellationException, 실행 중이던 것은 인터럽트로 실패
            var e = assertThrows(Exception.class, () -> future.get(10, TimeUnit.SECONDS));
            assertTrue(e instanceof CancellationException
                    || (e instanceof ExecutionException && e.getCause() instanceof S2RuntimeException), String.valueOf(e));
        }
        assertThrows(RejectedExecutionException.class, () -> queue.submit(data("x"), dir.toString(), "late.txt"));
        assertEquals(0, queue.getQueuedCount());
        assertEquals(0, tempFiles());
        manager.hold.countDown();
    }

    @Test
    void aCancelledUploadIsSkippedAndFreesItsPlace() throws Exception {
        var manager = new TestManager();
        manager.hold = new CountDownLatch(1);
        try (var queue = queue(manager).concurrency(1).build()) {
            var running = queue.submit(data("a"), dir.toString(), "k1.txt");
            var waiting = queue.submit(data("b"), dir.toString(), "k2.txt");
            waiting.cancel(false);
            manager.hold.countDown();
            running.get(10, TimeUnit.SECONDS);
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (queue.getQueuedCount() > 0 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(0, queue.getQueuedCount());
            assertEquals(1, manager.calls.get(), "the cancelled upload was not sent");
            assertFalse(Files.exists(dir.resolve("k2.txt")));
            assertEquals(0, tempFiles());
        }
    }

    @Test
    void anExistingLocalFileIsUploadedWithoutCopying() throws Exception {
        var manager = new TestManager();
        var source = Files.writeString(dir.resolve("source.bin"), "abc");
        try (var queue = queue(manager).build()) {
            assertEquals(3L, queue.submit(source, dir.resolve("o").toString(), "s.bin", false, true).get(10,
                    TimeUnit.SECONDS));
        }
        assertEquals("abc", Files.readString(dir.resolve("o/s.bin")));
        assertFalse(Files.exists(source), "deleted when finished");
    }
}
