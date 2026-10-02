/**
 * S2 Kit Library
 *
 * Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)
 * Contact: eseungsu.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For more information, please see the LICENSE file in the root directory.
 */
package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * S2HashUtil 단위 테스트
 */
class S2HashUtilTest {

    // =========================================================================
    // PBKDF2 hash / verify
    // =========================================================================

    @Nested
    @DisplayName("PBKDF2 hash/verify")
    class Pbkdf2 {

        @Test
        @DisplayName("hash 후 verify가 true를 반환한다")
        void hashAndVerify() throws Exception {
            String text = "myPassword123!";
            String hashed = S2HashUtil.hash(text);

            assertNotNull(hashed);
            assertNotEquals(text, hashed);
            assertTrue(S2HashUtil.verify(text, hashed));
        }

        @Test
        @DisplayName("다른 텍스트로 verify하면 false를 반환한다")
        void verifyWrongText() throws Exception {
            String hashed = S2HashUtil.hash("correct");
            assertFalse(S2HashUtil.verify("wrong", hashed));
        }

        @Test
        @DisplayName("같은 텍스트도 매번 다른 해시를 생성한다 (랜덤 Salt)")
        void differentSalts() throws Exception {
            String text = "sameText";
            String hash1 = S2HashUtil.hash(text);
            String hash2 = S2HashUtil.hash(text);
            assertNotEquals(hash1, hash2, "Salt가 달라 해시값도 달라야 한다");
            assertTrue(S2HashUtil.verify(text, hash1));
            assertTrue(S2HashUtil.verify(text, hash2));
        }

        @Test
        @DisplayName("null storedHash로 verify하면 false를 반환한다")
        void verifyNullHash() throws Exception {
            assertFalse(S2HashUtil.verify("text", null));
        }

        @Test
        @DisplayName("빈 storedHash로 verify하면 false를 반환한다")
        void verifyEmptyHash() throws Exception {
            assertFalse(S2HashUtil.verify("text", ""));
        }

        @Test
        @DisplayName("잘못된 Base64 storedHash로 verify하면 false를 반환한다")
        void verifyInvalidBase64() throws Exception {
            assertFalse(S2HashUtil.verify("text", "not-valid-base64!@#"));
        }

        @Test
        @DisplayName("1.x 형식 해시도 검증하며 다시 해시가 필요하다고 알린다")
        void legacyFormat() throws Exception {
            var salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);
            var hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new javax.crypto.spec.PBEKeySpec("pw".toCharArray(), salt, 65536, 256)).getEncoded();
            var combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, 16);
            System.arraycopy(hash, 0, combined, 16, hash.length);
            var legacy = java.util.Base64.getEncoder().encodeToString(combined);

            assertTrue(S2HashUtil.verify("pw", legacy));
            assertFalse(S2HashUtil.verify("other", legacy));
            assertTrue(S2HashUtil.needsRehash(legacy));
            var current = S2HashUtil.hash("pw");
            assertTrue(current.startsWith(S2HashUtil.FORMAT_PREFIX));
            assertFalse(S2HashUtil.needsRehash(current));
        }

        @Test
        @DisplayName("너무 짧은 storedHash로 verify하면 false를 반환한다")
        void verifyTooShortHash() throws Exception {
            // Salt(16바이트) 이하 길이의 Base64
            String shortBase64 = java.util.Base64.getEncoder().encodeToString(new byte[10]);
            assertFalse(S2HashUtil.verify("text", shortBase64));
            assertFalse(S2HashUtil.verify("text", S2HashUtil.FORMAT_PREFIX + shortBase64));
            // A forged huge iteration count is rejected, not computed | 조작된 거대한 반복 횟수는 계산하지 않고 거부
            var forged = java.nio.ByteBuffer.allocate(4 + 16 + 32).putInt(Integer.MAX_VALUE).array();
            assertFalse(S2HashUtil.verify("text", S2HashUtil.FORMAT_PREFIX + java.util.Base64.getEncoder().encodeToString(forged)));
        }
    }

    // =========================================================================
    // SHA 해시 (문자열)
    // =========================================================================

    @Nested
    @DisplayName("SHA 해시 - 문자열")
    class ShaString {

        @Test
        @DisplayName("SHA-256 문자열 해시가 64자리 hex를 반환한다")
        void sha256String() {
            String hash = S2HashUtil.generateSHA256("hello");
            assertNotNull(hash);
            assertEquals(64, hash.length(), "SHA-256은 64자리 hex");
        }

        @Test
        @DisplayName("SHA-512 문자열 해시가 128자리 hex를 반환한다")
        void sha512String() {
            String hash = S2HashUtil.generateSHA512("hello");
            assertNotNull(hash);
            assertEquals(128, hash.length(), "SHA-512는 128자리 hex");
        }

        @Test
        @DisplayName("SHA-512/256 문자열 해시가 64자리 hex를 반환한다")
        void sha512To256String() {
            String hash = S2HashUtil.generateSHA512To256("hello");
            assertNotNull(hash);
            assertEquals(64, hash.length(), "SHA-512/256은 64자리 hex");
        }

        @Test
        @DisplayName("동일 입력은 동일 해시를 반환한다 (결정적)")
        void deterministic() {
            String h1 = S2HashUtil.generateSHA256("test");
            String h2 = S2HashUtil.generateSHA256("test");
            assertEquals(h1, h2);
        }

        @Test
        @DisplayName("다른 입력은 다른 해시를 반환한다")
        void differentInputs() {
            String h1 = S2HashUtil.generateSHA256("abc");
            String h2 = S2HashUtil.generateSHA256("xyz");
            assertNotEquals(h1, h2);
        }

        @Test
        @DisplayName("null 문자열은 예외가 발생한다")
        void nullString() {
            assertThrows(NullPointerException.class, () -> S2HashUtil.generateSHA256((String) null));
        }

        @Test
        @DisplayName("빈 문자열과 공백도 실제 해시를 반환한다")
        void emptyString() {
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", S2HashUtil.generateSHA256(""));
            assertNotEquals(S2HashUtil.generateSHA256(" "), S2HashUtil.generateSHA256("  "));
        }
    }

    // =========================================================================
    // SHA 해시 (바이트 배열)
    // =========================================================================

    @Nested
    @DisplayName("SHA 해시 - 바이트 배열")
    class ShaBytes {

        @Test
        @DisplayName("바이트 배열 SHA-256 해시가 올바르게 동작한다")
        void sha256Bytes() {
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            String hash = S2HashUtil.generateSHA256(data);
            assertEquals(64, hash.length());
            // 문자열 해시와 동일해야 한다
            assertEquals(S2HashUtil.generateSHA256("hello"), hash);
        }

        @Test
        @DisplayName("null 바이트 배열은 예외가 발생한다")
        void nullBytes() {
            assertThrows(NullPointerException.class, () -> S2HashUtil.generateSHA256((byte[]) null));
        }
    }

    // =========================================================================
    // SHA 해시 (InputStream)
    // =========================================================================

    @Nested
    @DisplayName("SHA 해시 - InputStream")
    class ShaInputStream {

        @Test
        @DisplayName("InputStream SHA-256 해시가 문자열 해시와 일치한다")
        void sha256Stream() {
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            try (var is = new ByteArrayInputStream(data)) {
                String hash = S2HashUtil.generateSHA256(is);
                assertEquals(S2HashUtil.generateSHA256("hello"), hash);
            } catch (Exception e) {
                fail("예외 발생: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("null InputStream은 예외가 발생한다")
        void nullInputStream() {
            assertThrows(NullPointerException.class, () -> S2HashUtil.generateSHA256((java.io.InputStream) null));
        }
    }

    // =========================================================================
    // SHA 해시 (Path)
    // =========================================================================

    @Nested
    @DisplayName("SHA 해시 - Path")
    class ShaPath {

        @Test
        @DisplayName("파일 SHA-256 해시가 동일 내용의 문자열 해시와 일치한다")
        void sha256Path(@TempDir Path tempDir) throws Exception {
            Path file = tempDir.resolve("test.txt");
            Files.writeString(file, "hello", StandardCharsets.UTF_8);

            String hash = S2HashUtil.generateSHA256(file);
            assertEquals(S2HashUtil.generateSHA256("hello"), hash);
        }

        @Test
        @DisplayName("null Path는 예외, 없는 파일은 원인을 담은 예외가 발생한다")
        void nullPath(@TempDir Path tempDir) {
            assertThrows(NullPointerException.class, () -> S2HashUtil.generateSHA256((Path) null));
            var e = assertThrows(io.github.devers2.s2util.exception.S2RuntimeException.class,
                    () -> S2HashUtil.generateSHA256(tempDir.resolve("missing.txt")));
            assertInstanceOf(java.nio.file.NoSuchFileException.class, e.getCause());
        }
    }

    // =========================================================================
    // XXHash64
    // =========================================================================

    @Nested
    @DisplayName("XXHash64")
    class XxHash64 {

        @Test
        @DisplayName("문자열 XXHash64 해시가 16자리 hex를 반환한다")
        void xxhash64String() {
            String hash = S2HashUtil.generateXXHash64(0L, "hello");
            assertNotNull(hash);
            assertEquals(16, hash.length(), "XXHash64는 16자리 hex");
        }

        @Test
        @DisplayName("동일 입력·동일 시드는 동일 해시를 반환한다")
        void deterministic() {
            String h1 = S2HashUtil.generateXXHash64(42L, "test");
            String h2 = S2HashUtil.generateXXHash64(42L, "test");
            assertEquals(h1, h2);
        }

        @Test
        @DisplayName("다른 시드는 다른 해시를 반환한다")
        void differentSeeds() {
            String h1 = S2HashUtil.generateXXHash64(0L, "test");
            String h2 = S2HashUtil.generateXXHash64(99L, "test");
            assertNotEquals(h1, h2);
        }

        @Test
        @DisplayName("null 문자열은 예외, 빈 문자열은 실제 해시를 반환한다")
        void nullString() {
            assertThrows(NullPointerException.class, () -> S2HashUtil.generateXXHash64(0L, (String) null));
            assertEquals("ef46db3751d8e999", S2HashUtil.generateXXHash64(0L, ""));
            assertEquals(S2HashUtil.generateXXHash64(7L, "abc"),
                    S2HashUtil.generateXXHash64(7L, new java.io.ByteArrayInputStream("abc".getBytes())));
        }

        @Test
        @DisplayName("바이트 배열 XXHash64가 문자열 XXHash64와 일치한다")
        void bytesMatchString() {
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            assertEquals(
                    S2HashUtil.generateXXHash64(0L, "hello"),
                    S2HashUtil.generateXXHash64(0L, data));
        }
    }
}
