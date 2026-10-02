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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * S2EncryptionUtil 단위 테스트
 */
class S2EncryptionUtilTest {

    private static final String PASSWORD = "testPassword!@#123";

    // =========================================================================
    // encrypt / decrypt 왕복 테스트
    // =========================================================================

    @Nested
    @DisplayName("encrypt/decrypt 왕복")
    class RoundTrip {

        @Test
        @DisplayName("기본 텍스트를 암호화 후 복호화하면 원본과 일치한다")
        void basicRoundTrip() throws Exception {
            String plainText = "Hello, World!";
            String encrypted = S2EncryptionUtil.encrypt(plainText, PASSWORD);

            assertNotNull(encrypted);
            assertNotEquals(plainText, encrypted, "암호문은 평문과 달라야 한다");

            String decrypted = S2EncryptionUtil.decrypt(encrypted, PASSWORD);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("한글 텍스트를 암호화 후 복호화하면 원본과 일치한다")
        void koreanText() throws Exception {
            String plainText = "안녕하세요, 세계!";
            String encrypted = S2EncryptionUtil.encrypt(plainText, PASSWORD);
            String decrypted = S2EncryptionUtil.decrypt(encrypted, PASSWORD);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("빈 문자열을 암호화 후 복호화하면 빈 문자열이 반환된다")
        void emptyText() throws Exception {
            String plainText = "";
            String encrypted = S2EncryptionUtil.encrypt(plainText, PASSWORD);
            String decrypted = S2EncryptionUtil.decrypt(encrypted, PASSWORD);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("긴 텍스트를 암호화 후 복호화하면 원본과 일치한다")
        void longText() throws Exception {
            String plainText = "A".repeat(10000);
            String encrypted = S2EncryptionUtil.encrypt(plainText, PASSWORD);
            String decrypted = S2EncryptionUtil.decrypt(encrypted, PASSWORD);
            assertEquals(plainText, decrypted);
        }

        @Test
        @DisplayName("같은 평문도 매번 다른 암호문을 생성한다 (랜덤 Salt/IV)")
        void differentCiphertexts() throws Exception {
            String plainText = "same input";
            String enc1 = S2EncryptionUtil.encrypt(plainText, PASSWORD);
            String enc2 = S2EncryptionUtil.encrypt(plainText, PASSWORD);
            assertNotEquals(enc1, enc2, "동일 평문이라도 Salt/IV가 달라 암호문이 달라야 한다");
        }
    }

    // =========================================================================
    // decrypt 실패 케이스
    // =========================================================================

    @Nested
    @DisplayName("decrypt 실패 처리")
    class DecryptFailure {

        @Test
        @DisplayName("잘못된 비밀번호로 복호화하면 예외가 발생한다 (암호문을 돌려주지 않음)")
        void wrongPassword() throws Exception {
            String encrypted = S2EncryptionUtil.encrypt("secret data", PASSWORD);
            assertThrows(AEADBadTagException.class, () -> S2EncryptionUtil.decrypt(encrypted, "wrongPassword"));
        }

        @Test
        @DisplayName("잘못된 Base64 문자열로 복호화하면 예외가 발생한다")
        void invalidBase64() {
            assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt("not-valid-base64!@#$", PASSWORD));
            assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt("s2v2:%%%", PASSWORD));
            assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt("s2v2:AAAA", PASSWORD));
        }

        @Test
        @DisplayName("한 바이트라도 변조된 암호문은 복호화되지 않는다 (무결성 검증)")
        void tamperedCiphertext() throws Exception {
            String encrypted = S2EncryptionUtil.encrypt("test", PASSWORD);
            byte[] raw = Base64.getDecoder().decode(encrypted.substring(S2EncryptionUtil.FORMAT_PREFIX.length()));
            raw[raw.length - 1] ^= 1;
            String tampered = S2EncryptionUtil.FORMAT_PREFIX + Base64.getEncoder().encodeToString(raw);
            assertThrows(AEADBadTagException.class, () -> S2EncryptionUtil.decrypt(tampered, PASSWORD));
        }

        @Test
        @DisplayName("암호문의 반복 횟수가 비정상이면 키 생성 전에 거부한다")
        void forgedIterationCount() throws Exception {
            String encrypted = S2EncryptionUtil.encrypt("test", PASSWORD);
            byte[] raw = Base64.getDecoder().decode(encrypted.substring(S2EncryptionUtil.FORMAT_PREFIX.length()));
            raw[0] = 0x7f; // ~2 billion iterations
            String forged = S2EncryptionUtil.FORMAT_PREFIX + Base64.getEncoder().encodeToString(raw);
            var e = assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt(forged, PASSWORD));
            assertTrue(e.getMessage().contains("반복 횟수"));
        }
    }

    @Nested
    @DisplayName("1.x 형식 호환")
    class LegacyFormat {

        /** Builds a ciphertext exactly as 1.x did (salt 16 + IV 16 + AES-CBC) | 1.x 와 같은 방식으로 암호문 생성 */
        private String legacyEncrypt(String plain, String password) throws Exception {
            var random = new SecureRandom();
            var salt = new byte[16];
            var iv = new byte[16];
            random.nextBytes(salt);
            random.nextBytes(iv);
            var key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 65536, 256)).getEncoded();
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            var encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            var combined = new byte[32 + encrypted.length];
            System.arraycopy(salt, 0, combined, 0, 16);
            System.arraycopy(iv, 0, combined, 16, 16);
            System.arraycopy(encrypted, 0, combined, 32, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        }

        @Test
        @DisplayName("1.x 로 암호화한 값을 복호화할 수 있다")
        void decryptsLegacy() throws Exception {
            String legacy = legacyEncrypt("기존 데이터", PASSWORD);
            assertTrue(S2EncryptionUtil.isLegacyFormat(legacy));
            assertEquals("기존 데이터", S2EncryptionUtil.decrypt(legacy, PASSWORD));
            assertFalse(S2EncryptionUtil.isLegacyFormat(S2EncryptionUtil.encrypt("x", PASSWORD)));
        }

        @Test
        @DisplayName("잘린 1.x 암호문은 예외가 발생한다")
        void legacyTruncated() throws Exception {
            String legacy = legacyEncrypt("기존 데이터", PASSWORD);
            assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt(legacy.substring(0, 20), PASSWORD));
        }
    }

    @Nested
    @DisplayName("키 방식")
    class KeyBased {

        private final javax.crypto.SecretKey key = S2EncryptionUtil.generateKey();

        @Test
        @DisplayName("키로 암호화·복호화하며 같은 평문도 매번 다른 암호문이 나온다")
        void roundTrip() throws Exception {
            var enc1 = S2EncryptionUtil.encrypt("010-1234-5678", key);
            var enc2 = S2EncryptionUtil.encrypt("010-1234-5678", key);
            assertTrue(enc1.startsWith("s2k1:default:"), enc1);
            assertEquals(S2EncryptionUtil.DEFAULT_KEY_ID, S2EncryptionUtil.keyIdOf(enc1));
            assertNotEquals(enc1, enc2);
            assertEquals("010-1234-5678", S2EncryptionUtil.decrypt(enc1, key));
            assertEquals("", S2EncryptionUtil.decrypt(S2EncryptionUtil.encrypt("", key), key));
            assertEquals("한글 😀", S2EncryptionUtil.decrypt(S2EncryptionUtil.encrypt("한글 😀", key), key));
        }

        @Test
        @DisplayName("키를 Base64 로 저장했다가 읽어 같은 키로 쓴다")
        void keyRoundTrip() throws Exception {
            var stored = S2EncryptionUtil.keyToBase64(key);
            assertEquals(44, stored.length());
            var restored = S2EncryptionUtil.keyFromBase64(" " + stored + "\n");
            assertEquals("x", S2EncryptionUtil.decrypt(S2EncryptionUtil.encrypt("x", key), restored));

            assertThrows(IllegalArgumentException.class, () -> S2EncryptionUtil.keyFromBase64(""));
            assertThrows(IllegalArgumentException.class, () -> S2EncryptionUtil.keyFromBase64("not base64!"));
            assertThrows(IllegalArgumentException.class,
                    () -> S2EncryptionUtil.keyFromBase64(Base64.getEncoder().encodeToString(new byte[16])));
            assertThrows(IllegalArgumentException.class,
                    () -> S2EncryptionUtil.encrypt("x", new SecretKeySpec(new byte[16], "AES")));
        }

        @Test
        @DisplayName("틀린 키와 변조된 암호문은 복호화되지 않는다")
        void wrongKeyAndTampering() throws Exception {
            var enc = S2EncryptionUtil.encrypt("secret", key);
            assertThrows(AEADBadTagException.class, () -> S2EncryptionUtil.decrypt(enc, S2EncryptionUtil.generateKey()));

            var prefix = "s2k1:default:";
            byte[] raw = Base64.getDecoder().decode(enc.substring(prefix.length()));
            raw[raw.length - 1] ^= 1;
            var tampered = prefix + Base64.getEncoder().encodeToString(raw);
            assertThrows(AEADBadTagException.class, () -> S2EncryptionUtil.decrypt(tampered, key));
            assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt("s2k1:default:AAAA", key));
            assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt("s2k1:AAAA", key));
            assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt("s2k1:bad name:AAAA", key));
        }

        @Test
        @DisplayName("키 방식과 비밀번호 방식 암호문은 섞어 쓸 수 없으며 원인을 알려 준다")
        void formatsDoNotMix() throws Exception {
            var byPassword = S2EncryptionUtil.encrypt("x", PASSWORD);
            var e1 = assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt(byPassword, key));
            assertTrue(e1.getMessage().contains("비밀번호 방식"), e1.getMessage());

            var byKey = S2EncryptionUtil.encrypt("x", key);
            var e2 = assertThrows(GeneralSecurityException.class, () -> S2EncryptionUtil.decrypt(byKey, PASSWORD));
            assertTrue(e2.getMessage().contains("키 방식"), e2.getMessage());
            assertFalse(S2EncryptionUtil.isLegacyFormat(byKey));
        }

        @Test
        @DisplayName("1,000건도 빠르게 처리한다 (비밀번호 방식은 1건 수십 ms)")
        void fastForManyValues() throws Exception {
            var start = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                var value = "010-0000-" + i;
                assertEquals(value, S2EncryptionUtil.decrypt(S2EncryptionUtil.encrypt(value, key), key));
            }
            var millis = (System.nanoTime() - start) / 1_000_000;
            assertTrue(millis < 5_000, "1,000 round trips took " + millis + "ms");
        }
    }

    @Nested
    @DisplayName("키 교체 (KeyRing)")
    class Rotation {

        private final javax.crypto.SecretKey oldKey = S2EncryptionUtil.generateKey();
        private final javax.crypto.SecretKey newKey = S2EncryptionUtil.generateKey();

        private S2EncryptionUtil.KeyRing rotated() {
            return S2EncryptionUtil.KeyRing.builder().add("default", oldKey).add("2027", newKey).primary("2027").build();
        }

        @Test
        @DisplayName("단일 키로 만든 암호문을 교체 후에도 읽고, 새 암호문은 주 키로 만든다")
        void rotatesFromTheSingleKeyApi() throws Exception {
            var old = S2EncryptionUtil.encrypt("010-1111-2222", oldKey);
            var keys = rotated();

            assertEquals("010-1111-2222", keys.decrypt(old));
            var fresh = keys.encrypt("010-3333-4444");
            assertEquals("2027", S2EncryptionUtil.keyIdOf(fresh));
            assertEquals("010-3333-4444", keys.decrypt(fresh));
        }

        @Test
        @DisplayName("옛 암호문만 다시 암호화 대상이며, 옮긴 뒤에는 옛 키 없이 읽힌다")
        void reencryptsOldCiphertexts() throws Exception {
            var keys = rotated();
            var old = S2EncryptionUtil.encrypt("value", oldKey);
            assertTrue(keys.needsReencrypt(old));

            var moved = keys.reencrypt(old);
            assertFalse(keys.needsReencrypt(moved));
            assertSame(moved, keys.reencrypt(moved), "already on the primary key: unchanged");

            var withoutOldKey = S2EncryptionUtil.KeyRing.of("2027", newKey);
            assertEquals("value", withoutOldKey.decrypt(moved));
            var e = assertThrows(GeneralSecurityException.class, () -> withoutOldKey.decrypt(old));
            assertTrue(e.getMessage().contains("등록되지 않은 키 이름입니다: default"), e.getMessage());
        }

        @Test
        @DisplayName("암호문의 키 이름을 바꿔치기하면 두 키가 모두 있어도 복호화되지 않는다")
        void keyNameIsAuthenticated() throws Exception {
            // Same key under two names: only the name differs, and it is part of the authenticated data | 같은 키를 두 이름으로 등록해 이름만 다르게
            var keys = S2EncryptionUtil.KeyRing.builder().add("a", newKey).add("b", newKey).primary("a").build();
            var enc = keys.encrypt("x");
            var renamed = enc.replaceFirst("^s2k1:a:", "s2k1:b:");
            assertThrows(AEADBadTagException.class, () -> keys.decrypt(renamed));
        }

        @Test
        @DisplayName("키 묶음 설정 오류는 만들 때 알려 준다")
        void validatesTheRing() {
            assertThrows(IllegalStateException.class, () -> S2EncryptionUtil.KeyRing.builder().build());
            assertThrows(IllegalStateException.class,
                    () -> S2EncryptionUtil.KeyRing.builder().add("a", oldKey).add("b", newKey).build());
            assertThrows(IllegalStateException.class,
                    () -> S2EncryptionUtil.KeyRing.builder().add("a", oldKey).primary("z").build());
            assertThrows(IllegalArgumentException.class,
                    () -> S2EncryptionUtil.KeyRing.builder().add("a", oldKey).add("a", newKey));
            assertThrows(IllegalArgumentException.class, () -> S2EncryptionUtil.KeyRing.of("bad name", oldKey));
            assertThrows(IllegalArgumentException.class, () -> S2EncryptionUtil.KeyRing.of("a:b", oldKey));

            var single = S2EncryptionUtil.KeyRing.builder().add("only", S2EncryptionUtil.keyToBase64(oldKey)).build();
            assertEquals("only", single.primaryId());
            assertEquals(java.util.Set.of("default", "2027"), rotated().keyIds());
        }
    }
}
