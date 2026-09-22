/**
 * S2 Support Library
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
package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

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
        @DisplayName("잘못된 비밀번호로 복호화하면 원문(암호문)을 반환한다")
        void wrongPassword() throws Exception {
            String plainText = "secret data";
            String encrypted = S2EncryptionUtil.encrypt(plainText, PASSWORD);

            // 잘못된 비밀번호로 복호화 → 원문(암호문) 반환
            String result = S2EncryptionUtil.decrypt(encrypted, "wrongPassword");
            assertEquals(encrypted, result, "잘못된 비밀번호 시 원문(암호문)을 반환해야 한다");
        }

        @Test
        @DisplayName("잘못된 Base64 문자열로 복호화하면 원문을 반환한다")
        void invalidBase64() {
            String invalidData = "not-valid-base64!@#$";
            String result = S2EncryptionUtil.decrypt(invalidData, PASSWORD);
            assertEquals(invalidData, result);
        }

        @Test
        @DisplayName("손상된 암호문으로 복호화하면 원문을 반환한다")
        void corruptedCiphertext() throws Exception {
            String plainText = "test";
            String encrypted = S2EncryptionUtil.encrypt(plainText, PASSWORD);

            // 암호문 끝부분을 변조
            String corrupted = encrypted.substring(0, encrypted.length() - 4) + "XXXX";
            String result = S2EncryptionUtil.decrypt(corrupted, PASSWORD);
            assertEquals(corrupted, result);
        }
    }
}
