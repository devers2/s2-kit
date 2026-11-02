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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

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
 * AES-256-GCM 암호화를 두 가지 방식으로 제공한다. 두 방식의 암호문은 접두사로 구분되며 서로 섞어 쓸 수 없다.
 * </p>
 * <ul>
 * <li><b>키 방식</b> ({@link #encrypt(String, SecretKey)}, 접두사 {@code s2k1:<키 이름>:}): {@link #generateKey()}로 만든 무작위 키를
 * 설정·비밀 저장소에 보관하고 계속 사용한다. 호출마다 AES 만 수행하므로 빠르다(1건 1ms 미만). DB 컬럼 암호화, 목록 화면 복호화처럼 여러 건을 처리할 때
 * 쓴다. 암호문에 키 이름이 기록되므로 {@link KeyRing}으로 서비스를 멈추지 않고 키를 교체할 수 있다.</li>
 * <li><b>비밀번호 방식</b> ({@link #encrypt(String, String)}, 접두사 {@code s2v2:}): 사람이 입력한 비밀번호에서 매번 PBKDF2(65,536회)로 키를
 * 만든다. 추측 공격을 늦추려고 일부러 느리게(1건 약 70ms) 만든 것이므로 가끔 한 건씩 쓸 때 사용한다.</li>
 * </ul>
 * <p>
 * 로그인 비밀번호처럼 원문을 다시 꺼낼 필요가 없는 값은 암호화가 아니라 {@link S2HashUtil#hash(String)}로 저장한다.
 * </p>
 * <p>
 * 비밀번호 방식 AES-256 암호화. 형식은 {@code s2v2:} + Base64(반복 횟수 4바이트 + Salt 16바이트 + IV 12바이트 + 암호문·인증 태그)이며,
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

    /** Prefix of the password-based format | 비밀번호 방식 형식의 접두사 */
    public static final String FORMAT_PREFIX = "s2v2:";

    /** Prefix of the key-based format: s2k1:&lt;key name&gt;:Base64(IV 12 bytes + ciphertext and tag) | 키 방식 형식의 접두사 */
    public static final String KEY_FORMAT_PREFIX = "s2k1:";

    private static final int KEY_BYTES = 32;

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
        if (encryptedText.startsWith(KEY_FORMAT_PREFIX)) {
            throw new GeneralSecurityException("키 방식(s2k1:) 암호문입니다. decrypt(String, SecretKey)로 복호화하십시오.");
        }
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
        return encryptedText != null && !encryptedText.startsWith(FORMAT_PREFIX)
                && !encryptedText.startsWith(KEY_FORMAT_PREFIX);
    }

    // ------------------------------------------------------------------------
    // Key-based API | 키 방식

    /**
     * 키 방식에 쓸 무작위 AES-256 키를 만든다. 한 번 만들어 {@link #keyToBase64(SecretKey)}로 환경 변수나 비밀 저장소에 보관하고 계속 사용한다.
     * 키를 잃으면 암호문을 복호화할 수 없고, 키가 유출되면 모든 암호문이 노출된다.
     *
     * @return 256비트 AES 키
     * @apiNote
     *
     *          <pre>{@code
     * // 한 번 실행해 출력값을 비밀 저장소에 보관 (예: 환경 변수 APP_ENCRYPTION_KEY)
     * System.out.println(S2EncryptionUtil.keyToBase64(S2EncryptionUtil.generateKey()));
     *
     * // 애플리케이션 시작 시 한 번 읽어 재사용
     * SecretKey key = S2EncryptionUtil.keyFromBase64(System.getenv("APP_ENCRYPTION_KEY"));
     * String enc = S2EncryptionUtil.encrypt("010-1234-5678", key);
     * String dec = S2EncryptionUtil.decrypt(enc, key);
     * }</pre>
     */
    public static SecretKey generateKey() {
        return new SecretKeySpec(randomBytes(KEY_BYTES), "AES");
    }

    /**
     * 키를 저장용 Base64 문자열로 바꾼다.
     *
     * @param key AES-256 키
     * @return Base64 문자열 (44자)
     * @throws IllegalArgumentException AES-256 키가 아닐 때
     */
    public static String keyToBase64(SecretKey key) {
        return Base64.getEncoder().encodeToString(checkKey(key).getEncoded());
    }

    /**
     * {@link #keyToBase64(SecretKey)}로 저장한 문자열에서 키를 읽는다.
     *
     * @param base64 Base64 문자열 (앞뒤 공백 허용)
     * @return AES-256 키
     * @throws IllegalArgumentException 비었거나 Base64 가 아니거나 256비트 키가 아닐 때
     */
    public static SecretKey keyFromBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("키가 비었습니다.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("키가 Base64 형식이 아닙니다.", e);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 키는 32바이트여야 합니다: " + bytes.length + "바이트");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    /** Key name recorded by the single-key API | 단일 키 API 가 기록하는 키 이름 */
    public static final String DEFAULT_KEY_ID = "default";

    /**
     * 키로 평문을 AES-256-GCM 암호화한다. 호출마다 새 IV 를 쓰므로 같은 평문도 매번 다른 암호문이 나온다.
     * <p>
     * 암호문에는 키 이름 {@value #DEFAULT_KEY_ID}가 기록된다. 나중에 키를 교체할 때는 지금 키를 {@value #DEFAULT_KEY_ID}로 {@link KeyRing}에
     * 등록하면 기존 암호문을 그대로 읽을 수 있다.
     * </p>
     *
     * @param plainText 암호화할 원본 텍스트
     * @param key       {@link #generateKey()} 또는 {@link #keyFromBase64(String)}로 얻은 키
     * @return {@code s2k1:default:}로 시작하는 암호문
     * @throws GeneralSecurityException 암호화 알고리즘을 사용할 수 없을 때
     * @throws IllegalArgumentException AES-256 키가 아닐 때
     * @see KeyRing
     */
    public static String encrypt(String plainText, SecretKey key) throws GeneralSecurityException {
        return encryptWithKey(plainText, DEFAULT_KEY_ID, key);
    }

    /**
     * 키로 암호문을 복호화한다. 암호문에 기록된 키 이름과 관계없이 주어진 키로 복호화를 시도한다(키 이름은 무결성 검증에 포함되므로 바뀐 암호문은
     * 실패한다). 여러 키를 이름으로 고르려면 {@link KeyRing#decrypt(String)}을 쓴다.
     *
     * @param encryptedText {@link #encrypt(String, SecretKey)}로 만든 암호문
     * @param key           암호화에 쓴 키
     * @return 복호화된 원본 텍스트
     * @throws GeneralSecurityException 키가 틀렸거나 데이터가 손상·변조되었을 때({@code javax.crypto.AEADBadTagException}), 비밀번호 방식 암호문이거나 형식이
     *                                  잘못되었을 때
     * @throws IllegalArgumentException AES-256 키가 아닐 때
     */
    public static String decrypt(String encryptedText, SecretKey key) throws GeneralSecurityException {
        var parsed = parseKeyCiphertext(encryptedText);
        return decryptWithKey(parsed, key);
    }

    /**
     * 키 방식 암호문에 기록된 키 이름을 돌려준다. 어떤 키로 암호화했는지 확인하거나, 교체할 대상을 고를 때 쓴다.
     *
     * @param encryptedText 키 방식 암호문
     * @return 키 이름
     * @throws GeneralSecurityException 키 방식 암호문이 아닐 때
     */
    public static String keyIdOf(String encryptedText) throws GeneralSecurityException {
        return parseKeyCiphertext(encryptedText).keyId();
    }

    private record KeyCiphertext(String keyId, byte[] data) {
    }

    /** A key name: letters, digits, '.', '_' and '-' (it goes into the ciphertext as is) | 키 이름: 영문·숫자·'.'·'_'·'-' */
    private static final java.util.regex.Pattern KEY_ID = java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static String checkKeyId(String keyId) {
        if (keyId == null || !KEY_ID.matcher(keyId).matches()) {
            throw new IllegalArgumentException("키 이름은 영문·숫자·'.'·'_'·'-' 1~64자여야 합니다: " + keyId);
        }
        return keyId;
    }

    /** The key name is authenticated data: changing it in the ciphertext makes decryption fail | 키 이름은 인증 데이터라 바꾸면 복호화 실패 */
    private static byte[] associatedData(String keyId) {
        return (KEY_FORMAT_PREFIX + keyId).getBytes(StandardCharsets.UTF_8);
    }

    private static String encryptWithKey(String plainText, String keyId, SecretKey key) throws GeneralSecurityException {
        var iv = randomBytes(GCM_IV_LENGTH);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, checkKey(key), new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(associatedData(keyId));
        var encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        var combined = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        return KEY_FORMAT_PREFIX + keyId + ":" + Base64.getEncoder().encodeToString(combined);
    }

    private static KeyCiphertext parseKeyCiphertext(String encryptedText) throws GeneralSecurityException {
        if (!encryptedText.startsWith(KEY_FORMAT_PREFIX)) {
            throw new GeneralSecurityException(encryptedText.startsWith(FORMAT_PREFIX) || isLegacyFormat(encryptedText)
                    ? "비밀번호 방식 암호문입니다. decrypt(String, String)으로 복호화하십시오."
                    : "키 방식 암호문이 아닙니다.");
        }
        var rest = encryptedText.substring(KEY_FORMAT_PREFIX.length());
        var colon = rest.indexOf(':');
        if (colon < 1 || !KEY_ID.matcher(rest.substring(0, colon)).matches()) {
            throw new GeneralSecurityException("키 방식 암호문의 키 이름이 올바르지 않습니다.");
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(rest.substring(colon + 1));
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("암호문이 Base64 형식이 아닙니다.", e);
        }
        if (data.length < GCM_IV_LENGTH + GCM_TAG_BITS / 8) {
            throw new GeneralSecurityException("암호문이 너무 짧습니다.");
        }
        return new KeyCiphertext(rest.substring(0, colon), data);
    }

    private static String decryptWithKey(KeyCiphertext parsed, SecretKey key) throws GeneralSecurityException {
        var data = parsed.data();
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, checkKey(key), new GCMParameterSpec(GCM_TAG_BITS, data, 0, GCM_IV_LENGTH));
        cipher.updateAAD(associatedData(parsed.keyId()));
        return new String(cipher.doFinal(data, GCM_IV_LENGTH, data.length - GCM_IV_LENGTH), StandardCharsets.UTF_8);
    }

    /**
     * 이름을 붙인 여러 키 묶음. 키를 교체(로테이션)할 때 쓴다. 불변이며 여러 스레드에서 함께 써도 된다.
     * <ul>
     * <li>암호화는 주 키(primary)로 하고, 암호문에 그 키의 이름을 기록한다.</li>
     * <li>복호화는 암호문에 기록된 이름의 키를 골라 쓰므로, 옛 키로 만든 암호문도 계속 읽힌다.</li>
     * <li>{@link #needsReencrypt(String)}와 {@link #reencrypt(String)}로 옛 암호문을 주 키로 천천히 옮긴다. 모두 옮긴 뒤 옛 키를 뺀다.</li>
     * </ul>
     *
     * <pre>{@code
     * // 교체 전: 단일 키 API (키 이름 "default")
     * String enc = S2EncryptionUtil.encrypt(phone, oldKey);
     *
     * // 교체 후: 옛 키는 "default" 그대로, 새 키를 주 키로
     * var keys = S2EncryptionUtil.KeyRing.builder()
     *         .add("default", oldKey)
     *         .add("2027", newKey)
     *         .primary("2027")
     *         .build();
     * String phone = keys.decrypt(row.getPhone());            // 옛 암호문도 읽힘
     * if (keys.needsReencrypt(row.getPhone())) {
     *     row.setPhone(keys.reencrypt(row.getPhone()));        // 읽을 때 또는 야간 작업으로 옮김
     * }
     * }</pre>
     */
    public static final class KeyRing {

        private final Map<String, SecretKey> keys;
        private final String primaryId;

        private KeyRing(Map<String, SecretKey> keys, String primaryId) {
            this.keys = Map.copyOf(keys);
            this.primaryId = primaryId;
        }

        /**
         * 키 하나로 된 묶음을 만든다.
         *
         * @param keyId 키 이름 (영문·숫자·'.'·'_'·'-' 1~64자)
         * @param key   AES-256 키
         * @return 주 키가 그 키인 묶음
         */
        public static KeyRing of(String keyId, SecretKey key) {
            return builder().add(keyId, key).primary(keyId).build();
        }

        /**
         * @return 묶음 빌더
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * @return 암호화에 쓰는 주 키의 이름
         */
        public String primaryId() {
            return primaryId;
        }

        /**
         * @return 등록된 키 이름들
         */
        public java.util.Set<String> keyIds() {
            return keys.keySet();
        }

        /**
         * 주 키로 암호화한다.
         *
         * @param plainText 암호화할 원본 텍스트
         * @return {@code s2k1:<주 키 이름>:}으로 시작하는 암호문
         * @throws GeneralSecurityException 암호화 알고리즘을 사용할 수 없을 때
         */
        public String encrypt(String plainText) throws GeneralSecurityException {
            return encryptWithKey(plainText, primaryId, keys.get(primaryId));
        }

        /**
         * 암호문에 기록된 이름의 키로 복호화한다.
         *
         * @param encryptedText 키 방식 암호문
         * @return 복호화된 원본 텍스트
         * @throws GeneralSecurityException 기록된 키 이름이 묶음에 없거나, 키가 틀렸거나, 데이터가 손상·변조되었을 때
         */
        public String decrypt(String encryptedText) throws GeneralSecurityException {
            var parsed = parseKeyCiphertext(encryptedText);
            var key = keys.get(parsed.keyId());
            if (key == null) {
                throw new GeneralSecurityException(
                        "등록되지 않은 키 이름입니다: " + parsed.keyId() + " (등록된 키: " + new java.util.TreeSet<>(keys.keySet()) + ")");
            }
            return decryptWithKey(parsed, key);
        }

        /**
         * 주 키가 아닌 키로 만든 암호문인지 확인한다.
         *
         * @param encryptedText 키 방식 암호문
         * @return 주 키로 다시 암호화해야 하면 true
         * @throws GeneralSecurityException 키 방식 암호문이 아닐 때
         */
        public boolean needsReencrypt(String encryptedText) throws GeneralSecurityException {
            return !primaryId.equals(parseKeyCiphertext(encryptedText).keyId());
        }

        /**
         * 암호문을 주 키로 다시 암호화한다. 이미 주 키로 만든 암호문은 그대로 돌려준다.
         *
         * @param encryptedText 키 방식 암호문
         * @return 주 키로 만든 암호문
         * @throws GeneralSecurityException 복호화할 수 없을 때 ({@link #decrypt(String)} 참고)
         */
        public String reencrypt(String encryptedText) throws GeneralSecurityException {
            return needsReencrypt(encryptedText) ? encrypt(decrypt(encryptedText)) : encryptedText;
        }

        /**
         * {@link KeyRing} 빌더.
         */
        public static final class Builder {
            private final Map<String, SecretKey> keys = new java.util.LinkedHashMap<>();
            private String primaryId;

            private Builder() {
            }

            /**
             * 키를 등록한다.
             *
             * @param keyId 키 이름 (영문·숫자·'.'·'_'·'-' 1~64자, 암호문에 그대로 기록됨)
             * @param key   AES-256 키
             * @return 이 빌더
             * @throws IllegalArgumentException 이름이 올바르지 않거나 이미 등록되었거나, AES-256 키가 아닐 때
             */
            public Builder add(String keyId, SecretKey key) {
                checkKeyId(keyId);
                checkKey(key);
                if (keys.putIfAbsent(keyId, key) != null) {
                    throw new IllegalArgumentException("이미 등록된 키 이름입니다: " + keyId);
                }
                return this;
            }

            /**
             * {@link S2EncryptionUtil#keyToBase64(SecretKey)}로 저장한 키를 등록한다.
             *
             * @param keyId  키 이름
             * @param base64 Base64 키 문자열
             * @return 이 빌더
             */
            public Builder add(String keyId, String base64) {
                return add(keyId, keyFromBase64(base64));
            }

            /**
             * 암호화에 쓸 주 키를 정한다. 키가 하나뿐이면 생략할 수 있다.
             *
             * @param keyId 등록한 키 이름
             * @return 이 빌더
             */
            public Builder primary(String keyId) {
                this.primaryId = keyId;
                return this;
            }

            /**
             * @return 키 묶음
             * @throws IllegalStateException 키가 없거나, 주 키를 정하지 않았거나(키가 둘 이상일 때), 주 키가 등록되지 않았을 때
             */
            public KeyRing build() {
                if (keys.isEmpty()) {
                    throw new IllegalStateException("등록된 키가 없습니다.");
                }
                var primary = primaryId != null ? primaryId : keys.size() == 1 ? keys.keySet().iterator().next() : null;
                if (primary == null) {
                    throw new IllegalStateException("키가 둘 이상이면 primary(키 이름)로 주 키를 정해야 합니다.");
                }
                if (!keys.containsKey(primary)) {
                    throw new IllegalStateException("주 키가 등록되지 않았습니다: " + primary);
                }
                return new KeyRing(keys, primary);
            }
        }
    }

    private static SecretKey checkKey(SecretKey key) {
        java.util.Objects.requireNonNull(key, "key");
        var encoded = key.getEncoded();
        if (!"AES".equalsIgnoreCase(key.getAlgorithm()) || encoded == null || encoded.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 키가 필요합니다 (generateKey() 또는 keyFromBase64()).");
        }
        return key;
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
