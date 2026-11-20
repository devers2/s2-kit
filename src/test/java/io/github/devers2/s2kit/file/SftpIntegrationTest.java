package io.github.devers2.s2kit.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2kit.file.impl.S2SftpFileManagerImpl;
import io.github.devers2.s2kit.file.impl.spring.SpringSftpConfig;
import io.github.devers2.s2kit.file.impl.spring.SpringSftpFileManagerImpl;

/**
 * Both SFTP file managers against a real SSH server (Apache MINA SSHD, embedded): host key verification, transfers and
 * path containment.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 두 SFTP 파일 관리자를 실제 SSH 서버(내장 Apache MINA SSHD)에 붙여 호스트 키 검증, 전송, 경로 이탈 차단을 확인합니다.
 */
class SftpIntegrationTest {

    private static final String USER = "app";
    private static final String PASSWORD = "secret";

    @TempDir
    static Path temp;

    private static SshServer server;
    private static Path root;
    private static Path knownHosts;
    private static Path emptyKnownHosts;
    private static Path wrongKnownHosts;

    /** SSH connections open on the server now, and the most seen since the last reset | 서버에 열린 SSH 연결 수, 마지막 초기화 이후 최댓값 */
    private static final AtomicInteger openConnections = new AtomicInteger();
    private static final AtomicInteger peakConnections = new AtomicInteger();
    private static final AtomicInteger authentications = new AtomicInteger();
    private static final AtomicInteger peakAuthentications = new AtomicInteger();
    private static volatile long authDelayMillis = 0;

    @BeforeAll
    static void startServer() throws IOException, java.security.GeneralSecurityException {
        root = Files.createDirectories(temp.resolve("sftp-root"));
        var hostKeys = new SimpleGeneratorHostKeyProvider(temp.resolve("hostkey.ser"));

        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(hostKeys);
        server.setPasswordAuthenticator((user, password, session) -> {
            // Counts handshakes in progress; authDelayMillis makes them overlap | 진행 중인 인증 수를 셈 (지연을 주면 겹침)
            peakAuthentications.accumulateAndGet(authentications.incrementAndGet(), Math::max);
            try {
                Thread.sleep(authDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                authentications.decrementAndGet();
            }
            return USER.equals(user) && PASSWORD.equals(password);
        });
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        server.setFileSystemFactory(new VirtualFileSystemFactory(root));
        server.addSessionListener(new SessionListener() {
            @Override
            public void sessionCreated(Session session) {
                peakConnections.accumulateAndGet(openConnections.incrementAndGet(), Math::max);
            }

            @Override
            public void sessionClosed(Session session) {
                openConnections.decrementAndGet();
            }
        });
        server.start();

        PublicKey hostKey = hostKeys.loadKeys(null).iterator().next().getPublic();
        knownHosts = Files.writeString(temp.resolve("known_hosts"), knownHostsLine(hostKey) + "\n");
        emptyKnownHosts = Files.writeString(temp.resolve("known_hosts_empty"), "");
        // Same host, different key: what a man-in-the-middle presents | 같은 호스트, 다른 키 (중간자가 제시하는 키)
        KeyPair other = KeyPairGenerator.getInstance(hostKey.getAlgorithm()).generateKeyPair();
        wrongKnownHosts = Files.writeString(temp.resolve("known_hosts_wrong"), knownHostsLine(other.getPublic()) + "\n");
    }

    @AfterAll
    static void stopServer() throws IOException {
        server.stop(true);
    }

    private static String knownHostsLine(PublicKey key) {
        return "[127.0.0.1]:" + server.getPort() + " " + PublicKeyEntry.toString(key);
    }

    private static ByteArrayInputStream data(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Walks the causes: the manager wraps the JSch/MINA error | 원인을 따라감 (관리자가 JSch/MINA 오류를 감쌈) */
    private static String causes(Throwable e) {
        var sb = new StringBuilder();
        for (var t = e; t != null; t = t.getCause()) {
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    /**
     * The overwrite rule every FileManager follows: refuse by default, replace only when asked | 모든 FileManager 규칙: 기본 거부, 요청 시에만 덮어씀
     */
    static void assertOverwriteContract(FileManager files, String savePath, java.util.function.Supplier<String> stored)
            throws IOException {
        assertEquals(3, files.writeFile(data("one"), savePath, "same.txt"));
        var e = assertThrows(S2RuntimeException.class, () -> files.writeFile(data("two"), savePath, "same.txt"));
        assertTrue(causes(e).contains("이미 존재"), causes(e));
        assertEquals("one", stored.get(), "a refused write must not change the file");

        assertEquals(5, files.writeFile(data("three"), savePath, "same.txt", true));
        assertEquals("three", stored.get());
        files.deleteFile(savePath, "same.txt");
    }

    /** Gives some bytes, then fails like a dropped upload | 일부를 준 뒤 끊긴 업로드처럼 실패 */
    private static java.io.InputStream failingAfter(String text) {
        return new java.io.SequenceInputStream(data(text), new java.io.InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("source broke");
            }
        });
    }

    /**
     * A name with sub folders creates them, and a failed new upload leaves no partial file behind | 하위 폴더가 있는 이름은 폴더를 만들고, 실패한 새 업로드는 파일을 남기지 않음
     */
    static void assertSubFoldersAndCleanup(FileManager files, String savePath, Path localDir) throws IOException {
        assertEquals(2, files.writeFile(data("hi"), savePath, "sub/deep/n.txt"));
        assertEquals("hi", Files.readString(localDir.resolve("sub/deep/n.txt")));
        files.deleteFile(savePath, "sub/deep/n.txt");

        assertThrows(S2RuntimeException.class, () -> files.writeFile(failingAfter("partial"), savePath, "broken.txt"));
        assertFalse(Files.exists(localDir.resolve("broken.txt")), "a failed new upload must not leave a partial file");
        assertEquals(2, files.writeFile(data("ok"), savePath, "broken.txt"), "the name must be free again");
        files.deleteFile(savePath, "broken.txt");
    }

    /** Starts counting the peak from the connections open now | 지금 열린 연결 수부터 최댓값을 다시 셈 */
    private static int resetPeak() {
        var now = openConnections.get();
        peakConnections.set(now);
        return now;
    }

    /** Waits until the server has at most the given number of connections | 서버 연결 수가 주어진 수 이하가 될 때까지 기다림 */
    private static void awaitConnectionsAtMost(int count) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (openConnections.get() > count && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(openConnections.get() <= count, "connections left open: " + openConnections.get());
    }

    /**
     * Resumable writes continue a partial file, start over when it cannot be continued, keep the overwrite rule, and
     * can be discarded | 이어 쓰기는 부분 파일 뒤에 이어 쓰고, 이어 쓸 수 없으면 처음부터 쓰며, 덮어쓰기 규칙을 지키고, 지울 수 있음
     */
    static void assertResume(FileManager files, String savePath, Path remoteDir) throws IOException {
        Files.createDirectories(remoteDir);
        var source = Files.writeString(temp.resolve("resume-source-" + savePath.substring(1)), "ABCDEFGHIJ");

        // A different prefix proves the first 5 bytes were not sent again | 앞부분을 다르게 두어 다시 보내지 않았음을 증명
        Files.writeString(remoteDir.resolve(".r.txt.key1.s2part"), "abcde");
        assertEquals(10, files.writeFile(source, savePath, "r.txt", false, "key1"));
        assertEquals("abcdeFGHIJ", read(remoteDir.resolve("r.txt")));
        assertFalse(Files.exists(remoteDir.resolve(".r.txt.key1.s2part")), "the partial file becomes the target");

        // A partial file larger than the source cannot be continued | 원본보다 큰 부분 파일은 이어 쓸 수 없음
        Files.writeString(remoteDir.resolve(".r2.txt.key2.s2part"), "0123456789012345");
        assertEquals(10, files.writeFile(source, savePath, "r2.txt", false, "key2"));
        assertEquals("ABCDEFGHIJ", read(remoteDir.resolve("r2.txt")));

        var e = assertThrows(S2RuntimeException.class, () -> files.writeFile(source, savePath, "r.txt", false, "key3"));
        assertTrue(causes(e).contains("이미 존재"), causes(e));
        assertEquals("abcdeFGHIJ", read(remoteDir.resolve("r.txt")), "not overwritten");
        assertEquals(10, files.writeFile(source, savePath, "r.txt", true, "key4"));
        assertEquals("ABCDEFGHIJ", read(remoteDir.resolve("r.txt")), "overwritten when asked");

        Files.writeString(remoteDir.resolve(".d.txt.key5.s2part"), "abc");
        files.discardPartial(savePath, "d.txt", "key5");
        assertFalse(Files.exists(remoteDir.resolve(".d.txt.key5.s2part")));

        assertThrows(IllegalArgumentException.class, () -> files.writeFile(source, savePath, "x.txt", false, "../bad"));
        assertEquals(10, files.writeFile(source, savePath, "plain.txt", false, null), "null key writes normally");
        try (var listing = Files.list(remoteDir)) {
            assertTrue(listing.noneMatch(f -> f.getFileName().toString().endsWith(".s2part")), "no partial files left");
        }
    }

    /** Waits until the server has at least the given number of connections | 서버 연결 수가 주어진 수 이상이 될 때까지 기다림 */
    private static void awaitConnectionsAtLeast(int count) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (openConnections.get() < count && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(openConnections.get() >= count, "connections open: " + openConnections.get() + ", expected " + count);
    }

    /**
     * Many parallel uploads through a small pool all succeed and never open more connections than the pool allows | 작은 풀로
     * 많은 업로드를 동시에 해도 모두 성공하고 풀 상한보다 많은 연결을 열지 않음
     */
    static void assertBulkUploads(FileManager files, String savePath, int maxConnections) throws Exception {
        var baseline = resetPeak();
        var executor = Executors.newFixedThreadPool(16);
        try {
            var futures = new java.util.ArrayList<Future<Long>>();
            for (var i = 0; i < 60; i++) {
                var n = i;
                Callable<Long> upload = () -> files.writeFile(data("file-" + n), savePath, "b" + n + ".txt");
                futures.add(executor.submit(upload));
            }
            for (var future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        for (var i = 0; i < 60; i++) {
            assertEquals("file-" + i, read(root.resolve(savePath.substring(1)).resolve("b" + i + ".txt")));
        }
        assertTrue(peakConnections.get() - baseline <= maxConnections,
                "opened " + (peakConnections.get() - baseline) + " connections for a pool of " + maxConnections);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Nested
    class Jsch {

        private S2SftpFileManagerImpl manager(String knownHostsPath, boolean allowUnknownHosts) {
            return new S2SftpFileManagerImpl("127.0.0.1", server.getPort(), USER, null, null, PASSWORD, 2, 0, 3000,
                    knownHostsPath, allowUnknownHosts);
        }

        @Test
        void transfersWhenTheHostKeyIsKnown() throws IOException {
            try (var sftp = manager(knownHosts.toString(), false)) {
                assertEquals(5, sftp.writeFile(data("hello"), "/upload/2026", "a.txt"));
                assertEquals("hello", Files.readString(root.resolve("upload/2026/a.txt")));

                try (var in = sftp.readFile("/upload/2026", "a.txt")) {
                    assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
                var duplicate = assertThrows(S2RuntimeException.class,
                        () -> sftp.writeFile(data("x"), "/upload/2026", "a.txt"));
                assertTrue(causes(duplicate).contains("이미 존재"), causes(duplicate));

                sftp.deleteFile("/upload/2026", "a.txt");
                assertFalse(Files.exists(root.resolve("upload/2026/a.txt")));
            }
        }

        @Test
        void overwritesOnlyWhenAsked() throws IOException {
            try (var sftp = manager(knownHosts.toString(), false)) {
                assertOverwriteContract(sftp, "/ow-jsch", () -> read(root.resolve("ow-jsch/same.txt")));
                assertSubFoldersAndCleanup(sftp, "/sub-jsch", root.resolve("sub-jsch"));
            }
        }

        @Test
        void bulkUploadsStayWithinThePool() throws Exception {
            try (var sftp = manager(knownHosts.toString(), false)) {
                assertBulkUploads(sftp, "/bulk-jsch", 2);
            }
        }

        @Test
        void anUploadQueueKeepsServerConnectionsAtItsParallelism() throws Exception {
            var baseline = resetPeak();
            // A large pool, but the queue sends only 3 at a time | 풀은 크지만 큐가 한 번에 3건만 보냄
            try (var sftp = new S2SftpFileManagerImpl("127.0.0.1", server.getPort(), USER, null, null, PASSWORD, 32, 0,
                    3000, knownHosts.toString(), false);
                    var queue = S2UploadQueue.builder(sftp).concurrency(3).tempDir(temp.resolve("queue-tmp"))
                            .minFreeDiskBytes(0).build()) {
                var futures = new java.util.ArrayList<java.util.concurrent.CompletableFuture<Long>>();
                for (var i = 0; i < 100; i++) {
                    futures.add(queue.submit(data("q-" + i), "/queued", "q" + i + ".txt"));
                }
                java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                        .get(120, TimeUnit.SECONDS);
            }
            for (var i = 0; i < 100; i++) {
                assertEquals("q-" + i, read(root.resolve("queued/q" + i + ".txt")));
            }
            assertTrue(peakConnections.get() - baseline <= 3, "connections: " + (peakConnections.get() - baseline));
        }

        @Test
        void aFullPoolMakesCallersWaitInsteadOfOpeningMore() throws Exception {
            var baseline = resetPeak();
            try (var sftp = new S2SftpFileManagerImpl("127.0.0.1", server.getPort(), USER, null, null, PASSWORD, 2, 0, 500,
                    knownHosts.toString(), false)) {
                sftp.writeFile(data("x"), "/full", "x.txt", true);
                // Two open downloads hold both sessions | 열린 다운로드 두 개가 세션 두 개를 모두 붙잡음
                try (var first = sftp.readFile("/full", "x.txt"); var second = sftp.readFile("/full", "x.txt")) {
                    var e = assertThrows(S2RuntimeException.class, () -> sftp.readFile("/full", "x.txt"));
                    assertTrue(causes(e).contains("최대 재시도"), causes(e));
                    assertTrue(peakConnections.get() - baseline <= 2,
                            "a busy pool must not be rebuilt with more connections: " + (peakConnections.get() - baseline));
                }
                // Sessions are free again once the streams are closed | 스트림을 닫으면 세션을 다시 쓸 수 있음
                try (var in = sftp.readFile("/full", "x.txt")) {
                    assertEquals("x", new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
                sftp.deleteFile("/full", "x.txt");
            }
        }

        @Test
        void minIdleSessionsAreOpenedBeforeTheFirstRequest() throws Exception {
            var baseline = openConnections.get();
            try (var sftp = new S2SftpFileManagerImpl("127.0.0.1", server.getPort(), USER, null, null, PASSWORD, 4, 3, 3000,
                    knownHosts.toString(), false)) {
                awaitConnectionsAtLeast(baseline + 3);
            }
            awaitConnectionsAtMost(baseline);
        }

        @Test
        void resumesAPartialUpload() throws IOException {
            try (var sftp = manager(knownHosts.toString(), false)) {
                assertResume(sftp, "/resume-jsch", root.resolve("resume-jsch"));
            }
        }

        @Test
        void theConstructorLimitsParallelNewConnections() throws Exception {
            authDelayMillis = 200;
            try {
                for (var limit : new int[] { 1, 3 }) {
                    peakAuthentications.set(0);
                    try (var sftp = new S2SftpFileManagerImpl("127.0.0.1", server.getPort(), USER, null, null, PASSWORD, 6,
                            0, 10_000, knownHosts.toString(), false, limit)) {
                        assertBulkUploads(sftp, "/connects-" + limit, 6);
                    }
                    assertTrue(peakAuthentications.get() <= limit,
                            "limit " + limit + " but " + peakAuthentications.get() + " handshakes overlapped");
                    if (limit > 1) {
                        assertTrue(peakAuthentications.get() > 1, "a limit above 1 lets handshakes overlap");
                    }
                }
            } finally {
                authDelayMillis = 0;
            }
            assertThrows(IllegalArgumentException.class, () -> new S2SftpFileManagerImpl("127.0.0.1", server.getPort(), USER,
                    null, null, PASSWORD, 2, 0, 1000, knownHosts.toString(), false, 0));
        }

        @Test
        void aClosedManagerFailsAtOnce() {
            var sftp = manager(knownHosts.toString(), false);
            sftp.close();
            var started = System.nanoTime();
            var e = assertThrows(S2RuntimeException.class, () -> sftp.writeFile(data("x"), "/closed", "x.txt"));
            assertTrue(causes(e).contains("closed"), causes(e));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000, "no retries after close");
        }

        @Test
        void refusesAServerMissingFromKnownHosts() {
            try (var sftp = manager(emptyKnownHosts.toString(), false)) {
                var e = assertThrows(S2RuntimeException.class, () -> sftp.writeFile(data("x"), "/upload", "b.txt"));
                assertTrue(causes(e).contains("reject HostKey"), causes(e));
            }
            assertFalse(Files.exists(root.resolve("upload/b.txt")));
        }

        @Test
        void refusesAChangedHostKey() {
            try (var sftp = manager(wrongKnownHosts.toString(), false)) {
                var e = assertThrows(S2RuntimeException.class, () -> sftp.writeFile(data("x"), "/upload", "c.txt"));
                assertTrue(causes(e).contains("reject HostKey"), causes(e));
            }
            assertFalse(Files.exists(root.resolve("upload/c.txt")));
        }

        @Test
        void defaultChecksTheUserKnownHostsFile() throws IOException {
            // An empty home without ~/.ssh/known_hosts: refused, never trusted by default | known_hosts 없는 홈: 기본은 거부
            var home = Files.createDirectories(temp.resolve("home"));
            var original = System.getProperty("user.home");
            System.setProperty("user.home", home.toString());
            try {
                try (var sftp = new S2SftpFileManagerImpl("127.0.0.1", server.getPort(), USER, null, null, PASSWORD, 2, 0,
                        3000)) {
                    assertThrows(S2RuntimeException.class, () -> sftp.writeFile(data("x"), "/upload", "d.txt"));
                }
                // ~/.ssh/known_hosts with the server key: accepted | 서버 키가 있으면 허용
                Files.createDirectories(home.resolve(".ssh"));
                Files.copy(knownHosts, home.resolve(".ssh/known_hosts"));
                try (var sftp = new S2SftpFileManagerImpl("127.0.0.1", server.getPort(), USER, null, null, PASSWORD, 2, 0,
                        3000)) {
                    assertEquals(1, sftp.writeFile(data("x"), "/upload", "d.txt"));
                    sftp.deleteFile("/upload", "d.txt");
                }
            } finally {
                System.setProperty("user.home", original);
            }
        }

        @Test
        void allowUnknownHostsSkipsVerificationOnlyWhenAsked() throws IOException {
            try (var sftp = manager(null, true)) {
                assertEquals(1, sftp.writeFile(data("y"), "/upload", "e.txt"));
                sftp.deleteFile("/upload", "e.txt");
            }
        }

        @Test
        void pathsCannotLeaveTheSavePath() {
            try (var sftp = manager(knownHosts.toString(), false)) {
                assertThrows(S2RuntimeException.class, () -> sftp.writeFile(data("x"), "/upload", "../escaped.txt"));
                assertThrows(S2RuntimeException.class, () -> sftp.readFile("/upload", "../../etc/passwd"));
                assertThrows(S2RuntimeException.class, () -> sftp.deleteFile("/upload", "/etc/passwd"));
            }
            assertFalse(Files.exists(root.resolve("escaped.txt")));
        }
    }

    @Nested
    class Spring {

        private AnnotationConfigApplicationContext context(Map<String, Object> extra) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("sftp.host", "127.0.0.1");
            properties.put("sftp.port", server.getPort());
            properties.put("sftp.user", USER);
            properties.put("sftp.password", PASSWORD);
            properties.put("sftp.pool.min-idle", 0);
            properties.put("sftp.pool.max-total", 2);
            properties.putAll(extra);
            var context = new AnnotationConfigApplicationContext();
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
            context.register(SpringSftpConfig.class, SpringSftpFileManagerImpl.class);
            context.refresh();
            return context;
        }

        @Test
        void transfersWithPasswordAndKnownHosts() throws IOException {
            try (var context = context(Map.of("sftp.known-hosts-path", knownHosts.toString()))) {
                var sftp = context.getBean(SpringSftpFileManagerImpl.class);
                assertEquals(3, sftp.writeFile(data("abc"), "/spring", "f.txt"));
                assertEquals("abc", Files.readString(root.resolve("spring/f.txt")));
                try (var in = sftp.readFile("/spring", "f.txt")) {
                    assertEquals("abc", new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
                sftp.deleteFile("/spring", "f.txt");
                assertFalse(Files.exists(root.resolve("spring/f.txt")));
            }
        }

        @Test
        void overwritesOnlyWhenAsked() throws IOException {
            try (var context = context(Map.of("sftp.known-hosts-path", knownHosts.toString()))) {
                assertOverwriteContract(context.getBean(SpringSftpFileManagerImpl.class), "/ow-spring",
                        () -> read(root.resolve("ow-spring/same.txt")));
                assertSubFoldersAndCleanup(context.getBean(SpringSftpFileManagerImpl.class), "/sub-spring",
                        root.resolve("sub-spring"));
            }
        }

        @Test
        void bulkUploadsStayWithinThePool() throws Exception {
            try (var context = context(Map.of("sftp.known-hosts-path", knownHosts.toString()))) {
                assertBulkUploads(context.getBean(SpringSftpFileManagerImpl.class), "/bulk-spring", 2);
            }
        }

        @Test
        void resumesAPartialUpload() throws IOException {
            try (var context = context(Map.of("sftp.known-hosts-path", knownHosts.toString()))) {
                assertResume(context.getBean(SpringSftpFileManagerImpl.class), "/resume-spring", root.resolve("resume-spring"));
            }
        }

        @Test
        void minIdleSessionsAreOpenedBeforeTheFirstRequest() throws Exception {
            var baseline = openConnections.get();
            try (var context = context(Map.of("sftp.known-hosts-path", knownHosts.toString(), "sftp.pool.min-idle", 3,
                    "sftp.pool.max-total", 4))) {
                awaitConnectionsAtLeast(baseline + 3);
            }
            awaitConnectionsAtMost(baseline);
        }

        @Test
        void eachPooledSessionHasItsOwnConnectionAndClosingReleasesThem() throws Exception {
            var baseline = resetPeak();
            try (var context = context(Map.of("sftp.known-hosts-path", knownHosts.toString()))) {
                var sftp = context.getBean(SpringSftpFileManagerImpl.class);
                sftp.writeFile(data("y"), "/own", "y.txt", true);
                try (var first = sftp.readFile("/own", "y.txt"); var second = sftp.readFile("/own", "y.txt")) {
                    // A shared session would put both on one connection | 공유 세션이면 둘이 연결 하나에 올라감
                    assertEquals(baseline + 2, openConnections.get());
                }
                sftp.deleteFile("/own", "y.txt");
            }
            awaitConnectionsAtMost(baseline);
        }

        @Test
        void refusesUnknownOrChangedHostKeys() {
            for (var file : List.of(emptyKnownHosts, wrongKnownHosts)) {
                try (var context = context(Map.of("sftp.known-hosts-path", file.toString()))) {
                    var sftp = context.getBean(SpringSftpFileManagerImpl.class);
                    assertThrows(RuntimeException.class, () -> sftp.writeFile(data("x"), "/spring", "g.txt"), file.toString());
                }
            }
            // No known_hosts and allow-unknown-hosts=false (the default): every connection is refused | 기본값은 모두 거부
            try (var context = context(Map.of())) {
                var sftp = context.getBean(SpringSftpFileManagerImpl.class);
                assertThrows(RuntimeException.class, () -> sftp.writeFile(data("x"), "/spring", "g.txt"));
            }
            assertFalse(Files.exists(root.resolve("spring/g.txt")));
        }

        @Test
        void needsAKeyOrAPassword() {
            var e = assertThrows(Exception.class,
                    () -> context(Map.of("sftp.password", "", "sftp.allow-unknown-hosts", true)).close());
            assertTrue(causes(e).contains("sftp.private-key-path 또는 sftp.password"), causes(e));
        }
    }
}
