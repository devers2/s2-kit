package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for defects found in the full code review of the support package.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * support 패키지 전체 정독에서 찾은 결함의 회귀 시험입니다.
 */
class S2SupportFixesTest {

    @TempDir
    Path dir;

    // 대상이 없을 때 Files.isSameFile 이 NoSuchFileException 을 던져 새 경로로 복사·이동이 실패했다.
    @Test
    void copyAndMoveToNewPath() throws IOException {
        var source = Files.writeString(dir.resolve("a.txt"), "x");
        var copied = dir.resolve("sub/b.txt");
        S2FileUtil.copy(source, copied, false);
        assertEquals("x", Files.readString(copied));

        var moved = dir.resolve("c.txt");
        S2FileUtil.move(source, moved, false);
        assertTrue(Files.notExists(source));
        assertEquals("x", Files.readString(moved));

        var tree = Files.createDirectories(dir.resolve("tree/inner"));
        Files.writeString(tree.resolve("f.txt"), "y");
        S2FileUtil.copy(dir.resolve("tree"), dir.resolve("tree2"), false);
        assertEquals("y", Files.readString(dir.resolve("tree2/inner/f.txt")));
    }

    enum Grade {
        LOW, HIGH
    }

    // enum·Timestamp 는 Comparable 을 상위 클래스(Enum·Date)에서 물려받아 직접 구현 인터페이스가 없다.
    @Test
    void compareUsesInheritedComparable() {
        assertTrue(S2TypeUtil.compare(Grade.LOW, Grade.HIGH) < 0);
        assertTrue(S2TypeUtil.compare(new Timestamp(2000), new Timestamp(1000)) > 0);
    }

    static class Plain {
    }

    // 컨텍스트 클래스 로더에서 보이지 않는 클래스도 넘겨받은 클래스로 만든다.
    @Test
    void createInstanceFallsBackWhenContextLoaderCannotSeeClass() throws IOException {
        var thread = Thread.currentThread();
        var previous = thread.getContextClassLoader();
        try (var isolated = new URLClassLoader(new URL[0], null)) {
            thread.setContextClassLoader(isolated);
            assertInstanceOf(Plain.class, S2TypeUtil.createInstance(Plain.class));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    // 터키어 로케일에서 "I".toLowerCase() 는 점 없는 'ı' 가 된다.
    @Test
    void lowerCaseIgnoresDefaultLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("gif", S2FileUtil.getExtension("IMAGE.GIF", true));
            assertTrue(S2ServletUtil.isMobile("Mozilla/5.0 (IPHONE; CPU OS 17_0)"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    // 순환이 아닌 반복(같은 객체가 두 번)은 두 번 모두 모은다.
    @Test
    void getValueAllCollectsRepeatedObjects() {
        Map<String, Object> item = new HashMap<>();
        item.put("id", "a");
        var ids = S2ServletUtil.getValueAll(List.of(item, item), null, "id");
        assertEquals(List.of("a", "a"), ids);
    }

    // 경로 중간의 "word/"·"version.xml" 은 docx·hwpx 표시가 아니다.
    @Test
    void zipTypeLooksAtRootEntriesOnly() throws IOException {
        assertEquals("zip", S2FileUtil.detectExtension(zip("keyword/a.txt", "app/version.xml")));
        assertEquals("docx", S2FileUtil.detectExtension(zip("word/document.xml")));
        assertEquals("hwpx", S2FileUtil.detectExtension(zip("version.xml", "Contents/header.xml")));
    }

    private static byte[] zip(String... names) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (var name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("x".getBytes(StandardCharsets.US_ASCII));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
