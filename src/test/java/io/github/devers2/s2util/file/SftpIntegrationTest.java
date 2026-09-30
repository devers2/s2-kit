package io.github.devers2.s2util.file;

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

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
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
import io.github.devers2.s2util.file.impl.S2SftpFileManagerImpl;
import io.github.devers2.s2util.file.impl.spring.SpringSftpConfig;
import io.github.devers2.s2util.file.impl.spring.SpringSftpFileManagerImpl;

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

    @BeforeAll
    static void startServer() throws IOException, java.security.GeneralSecurityException {
        root = Files.createDirectories(temp.resolve("sftp-root"));
        var hostKeys = new SimpleGeneratorHostKeyProvider(temp.resolve("hostkey.ser"));

        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(hostKeys);
        server.setPasswordAuthenticator((user, password, session) -> USER.equals(user) && PASSWORD.equals(password));
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        server.setFileSystemFactory(new VirtualFileSystemFactory(root));
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
            }
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
            }
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
