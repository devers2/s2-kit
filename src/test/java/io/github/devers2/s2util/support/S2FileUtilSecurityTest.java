package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2util.exception.S2RuntimeException;

/**
 * Archive extraction, path containment and Content-Disposition parsing.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 압축 해제, 경로 이탈 방지, Content-Disposition 파일명 해석을 확인합니다.
 */
class S2FileUtilSecurityTest {

    @TempDir
    Path root;

    private static byte[] zip(String... namesAndContents) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zos.putNextEntry(new ZipEntry(namesAndContents[i]));
                zos.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void unzipRejectsEntriesOutsideTheTarget() throws IOException {
        var dest = root.resolve("dest");
        for (var evil : new String[] { "../escaped.txt", "a/../../escaped.txt", "..\\escaped.txt", "/tmp/abs.txt" }) {
            var data = zip(evil, "x");
            assertThrows(S2RuntimeException.class, () -> S2FileUtil.unzipFiles(new ByteArrayInputStream(data), dest),
                    evil);
        }
        assertFalse(Files.exists(root.resolve("escaped.txt")));
    }

    @Test
    void unzipCreatesParentDirectoriesAndEnforcesLimits() throws IOException {
        var dest = root.resolve("dest");
        S2FileUtil.unzipFiles(new ByteArrayInputStream(zip("a/b/c.txt", "hello")), dest);
        assertEquals("hello", Files.readString(dest.resolve("a/b/c.txt")));

        var many = zip("1.txt", "a", "2.txt", "b", "3.txt", "c");
        var tooMany = assertThrows(IOException.class,
                () -> S2FileUtil.unzipFiles(new ByteArrayInputStream(many), root.resolve("n"), 2, 1_000));
        assertTrue(tooMany.getMessage().contains("항목 수"));

        var big = zip("big.txt", "x".repeat(100));
        var tooBig = assertThrows(IOException.class,
                () -> S2FileUtil.unzipFiles(new ByteArrayInputStream(big), root.resolve("b"), 10, 50));
        assertTrue(tooBig.getMessage().contains("크기"));
    }

    @Test
    void zipDirectoryRoundTripsWithSlashSeparators() throws IOException {
        var source = Files.createDirectories(root.resolve("src/sub"));
        Files.writeString(source.resolve("f.txt"), "nested");
        var bytes = new ByteArrayOutputStream();
        S2FileUtil.zipDirectory(root.resolve("src"), bytes);

        var names = new java.util.ArrayList<String>();
        try (var zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            for (ZipEntry e; (e = zis.getNextEntry()) != null;) {
                names.add(e.getName());
            }
        }
        assertEquals(java.util.List.of("sub/", "sub/f.txt"), names);

        S2FileUtil.unzipFiles(new ByteArrayInputStream(bytes.toByteArray()), root.resolve("out"));
        assertEquals("nested", Files.readString(root.resolve("out/sub/f.txt")));
    }

    @Test
    void resolveWithinKeepsPathsInsideTheBase() {
        var base = root.resolve("base");
        assertEquals(base.resolve("a/b.txt").toAbsolutePath().normalize(), S2FileUtil.resolveWithin(base, "a/b.txt"));
        assertEquals(base.resolve("b.txt").toAbsolutePath().normalize(), S2FileUtil.resolveWithin(base, "a/../b.txt"));
        for (var bad : new String[] { "../x", "a/../../x", "..\\x", "/etc/passwd", "", ".", " " }) {
            assertThrows(S2RuntimeException.class, () -> S2FileUtil.resolveWithin(base, bad), bad);
        }
    }

    @Test
    void resolveRemoteWithinKeepsRemotePathsInsideTheBase() {
        assertEquals("/upload/a.txt", S2FileUtil.resolveRemoteWithin("/upload", "a.txt"));
        assertEquals("/upload/2026/a.txt", S2FileUtil.resolveRemoteWithin("/upload/", "2026//./a.txt"));
        assertEquals("/upload/b.txt", S2FileUtil.resolveRemoteWithin("/upload", "a/../b.txt"));
        assertEquals("data/a.txt", S2FileUtil.resolveRemoteWithin("data", "a.txt"));
        assertEquals("a.txt", S2FileUtil.resolveRemoteWithin("", "a.txt"));
        assertEquals("/a.txt", S2FileUtil.resolveRemoteWithin("/", "a.txt"));
        for (var bad : new String[] { "../etc/passwd", "a/../../x", "..\\x", "/etc/passwd", "", ".", "a/.." }) {
            assertThrows(S2RuntimeException.class, () -> S2FileUtil.resolveRemoteWithin("/upload", bad), bad);
        }
        assertThrows(S2RuntimeException.class, () -> S2FileUtil.resolveRemoteWithin("", "../x"));
        assertThrows(S2RuntimeException.class, () -> S2FileUtil.resolveRemoteWithin("/upload", "../upload2/x"));
    }

    @Test
    void contentDispositionFilename() {
        // filename* wins over filename regardless of order (RFC 6266) | 순서와 무관하게 filename* 우선
        assertEquals("한글.txt", S2FileUtil.parseContentDispositionFilename(
                "attachment; filename=\"fallback.txt\"; filename*=UTF-8''%ED%95%9C%EA%B8%80.txt"));
        assertEquals("a+b.txt", S2FileUtil.parseContentDispositionFilename("attachment; filename*=UTF-8''a+b.txt"));
        assertEquals("a b.txt", S2FileUtil.parseContentDispositionFilename("attachment; filename=\"a b.txt\""));
        assertEquals("a\"b.txt", S2FileUtil.parseContentDispositionFilename("attachment; filename=\"a\\\"b.txt\""));
        assertEquals("plain.txt", S2FileUtil.parseContentDispositionFilename("attachment; filename=plain.txt; size=3"));
        assertEquals("passwd", S2FileUtil.parseContentDispositionFilename("attachment; filename=\"../../etc/passwd\""));
        assertEquals("fallback.txt", S2FileUtil.parseContentDispositionFilename(
                "attachment; filename=fallback.txt; filename*=UTF-8''%E"));
        assertEquals("", S2FileUtil.parseContentDispositionFilename("inline"));
        assertEquals("", S2FileUtil.parseContentDispositionFilename(null));
    }
}
