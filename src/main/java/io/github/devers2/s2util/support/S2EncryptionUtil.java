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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * s2's Encryption utilities
 * <p>
 * 비밀번호 기반 AES-256 암호화. 형식은 {@code s2v2:} + Base64(반복 횟수 4바이트 + Salt 16바이트 + IV 12바이트 + 암호문·인증 태그)이며,
 * AES-GCM 이므로 비밀번호가 틀리거나 데이터가 바뀌면 복호화가 반드시 실패한다.
 * </p>
 * <p>
 * 1.x 형식(접두사 없는 Base64, AES-CBC)도 계속 복호화한다. 이 형식은 무결성 검증이 없어 드물게 틀린 비밀번호로도 깨진 문자열이 나올 수 있으므로,
 * 읽은 뒤 {@link #encrypt(String, String)}로 다시 저장하는 것을 권장한다.
 * </p>
 *
 * @author devers2
 * @version 2.0
 * @since 2025. 04. 09.
 */
public class S2EncryptionUtil {

    /** Prefix of the current format | 현재 형식의 접두사 */
    public static final String FORMAT_PREFIX = "s2v2:";

    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    /** Upper bound for the iteration count read from a ciphertext (limits CPU spent on forged input) | 암호문에서 읽는 반복 횟수 상한 */
    private static final int MAX_ITERATION_COUNT = 10_000_000;
    private static final int SALT_LENGTH = 16;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 1.x format: Base64(salt 16 + IV 16 + AES-CBC ciphertext) | 1.x 형식 */
    private static final int LEGACY_IV_LENGTH = 16;
    private static final String LEGACY_ALGORITHM = "AES/CBC/PKCS5Padding";

    private S2EncryptionUtil() {
    }

    /**
     * 주어진 평문을 비밀번호를 사용하여 AES-256-GCM 으로 암호화 한다.
     *
     * @param plainText 암호화할 원본 텍스트
     * @param password  암호화에 사용할 비밀번호
     * @return {@code s2v2:}로 시작하는 암호문
     * @throws GeneralSecurityException 암호화 알고리즘을 사용할 수 없을 때
     * @throws NullPointerException     plainText 또는 password 가 null 일 때
     */
    public static String encrypt(String plainText, String password) throws GeneralSecurityException {
        var plain = plainText.getBytes(StandardCharsets.UTF_8);
        var salt = randomBytes(SALT_LENGTH);
        var iv = randomBytes(GCM_IV_LENGTH);

        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, generateKey(password, salt, ITERATION_COUNT), new GCMParameterSpec(GCM_TAG_BITS, iv));
        var encrypted = cipher.doFinal(plain);

        var combined = ByteBuffer.allocate(4 + salt.length + iv.length + encrypted.length)
                .putInt(ITERATION_COUNT).put(salt).put(iv).put(encrypted).array();
        return FORMAT_PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 암호문을 비밀번호를 사용하여 복호화 한다. {@code s2v2:} 형식과 1.x 형식을 모두 읽는다.
     *
     * @param encryptedText 복호화할 암호문
     * @param password      복호화에 사용할 비밀번호
     * @return 복호화된 원본 텍스트
     * @throws GeneralSecurityException 비밀번호가 틀렸거나 데이터가 손상·변조되었을 때 ({@code javax.crypto.AEADBadTagException} 등), 형식이 잘못되었을 때
     * @throws NullPointerException     encryptedText 또는 password 가 null 일 때
     */
    public static String decrypt(String encryptedText, String password) throws GeneralSecurityException {
        byte[] combined;
        var current = encryptedText.startsWith(FORMAT_PREFIX);
        try {
            combined = Base64.getDecoder().decode(current ? encryptedText.substring(FORMAT_PREFIX.length()) : encryptedText);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("암호문이 Base64 형식이 아닙니다.", e);
        }
        return current ? decryptCurrent(combined, password) : decryptLegacy(combined, password);
    }

    /**
     * 1.x 형식인지 확인한다. 참이면 복호화한 뒤 {@link #encrypt(String, String)}로 다시 저장하는 것을 권장한다.
     *
     * @param encryptedText 암호문
     * @return 1.x 형식 여부
     */
    public static boolean isLegacyFormat(String encryptedText) {
        return encryptedText != null && !encryptedText.startsWith(FORMAT_PREFIX);
    }

    private static String decryptCurrent(byte[] combined, String password) throws GeneralSecurityException {
        if (combined.length < 4 + SALT_LENGTH + GCM_IV_LENGTH + GCM_TAG_BITS / 8) {
            throw new GeneralSecurityException("암호문이 너무 짧습니다.");
        }
        var buffer = ByteBuffer.wrap(combined);
        var iterations = buffer.getInt();
        if (iterations < 1 || iterations > MAX_ITERATION_COUNT) {
            throw new GeneralSecurityException("암호문의 반복 횟수가 올바르지 않습니다: " + iterations);
        }
        var salt = new byte[SALT_LENGTH];
        var iv = new byte[GCM_IV_LENGTH];
        buffer.get(salt).get(iv);
        var encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, generateKey(password, salt, iterations), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static String decryptLegacy(byte[] combined, String password) throws GeneralSecurityException {
        if (combined.length <= SALT_LENGTH + LEGACY_IV_LENGTH) {
            throw new GeneralSecurityException("암호문이 너무 짧습니다.");
        }
        var salt = new byte[SALT_LENGTH];
        var iv = new byte[LEGACY_IV_LENGTH];
        var encrypted = new byte[combined.length - SALT_LENGTH - LEGACY_IV_LENGTH];
        System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
        System.arraycopy(combined, SALT_LENGTH, iv, 0, LEGACY_IV_LENGTH);
        System.arraycopy(combined, SALT_LENGTH + LEGACY_IV_LENGTH, encrypted, 0, encrypted.length);

        var cipher = Cipher.getInstance(LEGACY_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, generateKey(password, salt, ITERATION_COUNT), new IvParameterSpec(iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    /**
     * PBKDF2 알고리즘을 사용하여 비밀번호와 Salt 로부터 AES 암호화 키를 생성합니다.
     */
    private static SecretKey generateKey(String password, byte[] salt, int iterations) throws GeneralSecurityException {
        var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
        try {
            var key = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM).generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] randomBytes(int length) {
        var bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

}
