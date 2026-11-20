package io.github.devers2.s2kit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2kit.file.FileManager;
import io.github.devers2.s2kit.file.S2FileExistsException;
import io.github.devers2.s2kit.file.S2UploadQueue;
import io.github.devers2.s2kit.file.S2UploadRejectedException;
import io.github.devers2.s2kit.file.impl.S2FileManagerImpl;
import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.message.S2Localizable;

/**
 * User-facing messages: both bundles have the same keys, every key the code uses exists, and failures render in the
 * request's language | 사용자용 문구: 두 번들의 키가 같고, 코드가 쓰는 키가 모두 있으며, 실패가 요청 언어로 보임
 */
class S2KitMessagesTest {

    @TempDir
    Path dir;

    private static Properties bundle(String name) throws IOException {
        var properties = new Properties();
        try (var in = S2KitMessagesTest.class.getResourceAsStream("/io/github/devers2/s2kit/" + name)) {
            assertNotNull(in, name);
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static String ko(Throwable e) {
        return S2Localizable.findLocalizedMessage(e, Locale.KOREAN).orElseThrow();
    }

    private static String en(Throwable e) {
        return S2Localizable.findLocalizedMessage(e, Locale.ENGLISH).orElseThrow();
    }

    @Test
    void bothBundlesHaveTheSameKeysAndEveryUsedKeyExists() throws IOException {
        var english = bundle("messages.properties");
        var korean = bundle("messages_ko.properties");
        assertEquals(new TreeSet<>(english.stringPropertyNames()), new TreeSet<>(korean.stringPropertyNames()));

        var used = Pattern.compile("S2KitMessages\\.key\\(\\s*\"([^\"]+)\"");
        var missing = new TreeSet<String>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                var matcher = used.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (!english.containsKey(matcher.group(1))) {
                        missing.add(matcher.group(1) + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "keys missing from messages.properties: " + missing);
    }

    @Test
    void anExistingFileIsReportedInTheUsersLanguage() {
        var files = new S2FileManagerImpl();
        files.writeFile(new ByteArrayInputStream(new byte[] { 1 }), dir.toString(), "보고서.txt");
        var e = assertThrows(S2FileExistsException.class,
                () -> files.writeFile(new ByteArrayInputStream(new byte[] { 2 }), dir.toString(), "보고서.txt"));
        assertTrue(e.getMessage().startsWith("File already exists: "), "the log message stays English");
        assertEquals("보고서.txt은(는) 이미 있는 파일입니다.", ko(e));
        assertEquals("보고서.txt already exists.", en(e));
        assertFalse(ko(e).contains(dir.toString()), "no server path in the user-facing text");

        var wrapped = new S2RuntimeException("Cannot upload the remote file: 사진", e,
                S2KitMessages.key("s2kit.file.uploadFailed", "사진"));
        assertEquals("보고서.txt은(는) 이미 있는 파일입니다.", ko(wrapped), "the specific cause wins over the wrapper");
    }

    @Test
    void uploadQueueRejectionsAreUserFacing() throws Exception {
        var hold = new CountDownLatch(1);
        FileManager slow = new S2FileManagerImpl() {
            @Override
            public long writeFile(java.io.InputStream data, String savePath, String saveName, boolean overwrite) {
                try {
                    hold.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.writeFile(data, savePath, saveName, overwrite);
            }
        };
        try (var queue = S2UploadQueue.builder(slow).concurrency(1).maxQueued(1).maxFileBytes(3L * 1024 * 1024)
                .tempDir(dir.resolve("tmp")).minFreeDiskBytes(0).build()) {
            queue.submit(new ByteArrayInputStream(new byte[] { 1 }), dir.toString(), "a.txt");
            var full = assertThrows(RejectedExecutionException.class,
                    () -> queue.submit(new ByteArrayInputStream(new byte[] { 1 }), dir.toString(), "b.txt"));
            assertInstanceOf(S2UploadRejectedException.class, full);
            assertEquals("업로드가 많이 몰려 있습니다. 잠시 후 다시 시도해 주세요.", ko(full));
            hold.countDown();
        }
        try (var queue = S2UploadQueue.builder(new S2FileManagerImpl()).maxFileBytes(3L * 1024 * 1024)
                .tempDir(dir.resolve("tmp")).minFreeDiskBytes(0).build()) {
            var tooLarge = assertThrows(S2RuntimeException.class,
                    () -> queue.submit(new ByteArrayInputStream(new byte[4 * 1024 * 1024]), dir.toString(), "big.bin"));
            assertEquals("파일이 너무 큽니다. 최대 3 MB까지 올릴 수 있습니다.", ko(tooLarge));
            assertEquals("The file is too large. The maximum is 3 MB.", en(tooLarge));
        }
    }
}
