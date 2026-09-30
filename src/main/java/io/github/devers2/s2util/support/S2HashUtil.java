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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import io.github.devers2.s2util.core.S2Util;
import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;
import net.jpountz.xxhash.XXHashFactory;

/**
 * s2's Encryption utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2025. 04. 09.
 */
public class S2HashUtil {

    private static final S2Logger logger = S2LogManager.getLogger(S2HashUtil.class);

    /** Prefix of the current password hash format | 현재 비밀번호 해시 형식의 접두사 */
    public static final String FORMAT_PREFIX = "s2v2:";
    /** PBKDF2 iterations for new hashes (OWASP: PBKDF2-HMAC-SHA256 ≥ 310,000) | 새 해시의 반복 횟수 */
    private static final int ITERATION_COUNT = 310_000;
    /** Iterations of the 1.x format | 1.x 형식의 반복 횟수 */
    private static final int LEGACY_ITERATION_COUNT = 65536;
    /** Upper bound for the iteration count read from a stored hash | 저장된 해시에서 읽는 반복 횟수 상한 */
    private static final int MAX_ITERATION_COUNT = 10_000_000;
    private static final int KEY_LENGTH = 256; // 출력 길이 (비트)
    private static final int SALT_LENGTH = 16; // 솔트 길이 (바이트)
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String HASH_ALGORITHM_SHA256 = "SHA-256";
    private static final String HASH_ALGORITHM_SHA512_256 = "SHA-512/256";
    private static final String HASH_ALGORITHM_SHA512 = "SHA-512";

    /** Initialized once on first use without locking every call | 매 호출 잠금 없이 최초 사용 시 한 번 초기화 */
    private static final class XXHashHolder {
        static final XXHashFactory FACTORY = create();

        private static XXHashFactory create() {
            try {
                return XXHashFactory.fastestInstance(); // JNI 모드 우선
            } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                logger.debug("XXHashFactory JNI mode failed, falling back to Java mode: {}", e.getMessage());
                return XXHashFactory.fastestJavaInstance();
            }
        }
    }

    private S2HashUtil() {
    }

    /**
     * 주어진 텍스트(비밀번호)를 PBKDF2-HMAC-SHA256 으로 단방향 해시한다.
     *
     * @param text 해시할 원본 텍스트 (예: 비밀번호)
     * @return {@code s2v2:} + Base64(반복 횟수 4바이트 + Salt 16바이트 + 해시 32바이트)
     * @throws NoSuchAlgorithmException 키 파생 알고리즘을 지원하지 않는 경우
     * @throws InvalidKeySpecException  키 스펙이 유효하지 않은 경우
     * @details
     *          <dl>
     *          <dd>반복 횟수를 해시에 저장하므로 이후 반복 횟수를 올려도 기존 해시를 검증할 수 있다. {@link #needsRehash(String)} 참고.</dd>
     *          <dd>높은 보안 요구사항이 있을때는 BCrypt 또는 Argon2 사용을 검토 해야함</dd>
     *          </dl>
     */
    public static String hash(String text) throws NoSuchAlgorithmException, InvalidKeySpecException {
        var salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        var hash = pbkdf2(text, salt, ITERATION_COUNT);
        var combined = ByteBuffer.allocate(4 + salt.length + hash.length).putInt(ITERATION_COUNT).put(salt).put(hash).array();
        return FORMAT_PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 저장된 해시값과 입력된 텍스트를 비교하여 일치 여부를 확인한다. {@code s2v2:} 형식과 1.x 형식(접두사 없는 Base64)을 모두 검증한다.
     *
     * @param text       확인할 원본 텍스트 (예: 입력된 비밀번호)
     * @param storedHash 저장된 해시값
     * @return 일치하면 true, 불일치 또는 형식이 잘못된 해시면 false
     * @throws NoSuchAlgorithmException 키 파생 알고리즘을 지원하지 않는 경우
     * @throws InvalidKeySpecException  키 스펙이 유효하지 않은 경우
     */
    public static boolean verify(String text, String storedHash)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        var parsed = parse(storedHash);
        if (parsed == null) {
            return false;
        }
        return MessageDigest.isEqual(parsed.hash, pbkdf2(text, parsed.salt, parsed.iterations));
    }

    /**
     * 저장된 해시를 현재 설정으로 다시 만들어야 하는지 확인한다. 1.x 형식이거나 반복 횟수가 현재보다 적으면 true 이다. 로그인 성공 후
     * {@link #hash(String)}로 다시 저장하는 데 쓴다.
     *
     * @param storedHash 저장된 해시값
     * @return 다시 해시해야 하면 true
     */
    public static boolean needsRehash(String storedHash) {
        var parsed = parse(storedHash);
        return parsed == null || parsed.iterations < ITERATION_COUNT;
    }

    private record ParsedHash(int iterations, byte[] salt, byte[] hash) {
    }

    /** Null for a malformed value | 형식이 잘못되면 null */
    private static ParsedHash parse(String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return null;
        }
        var current = storedHash.startsWith(FORMAT_PREFIX);
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(current ? storedHash.substring(FORMAT_PREFIX.length()) : storedHash);
        } catch (IllegalArgumentException e) {
            return null;
        }
        var buffer = ByteBuffer.wrap(combined);
        int iterations = LEGACY_ITERATION_COUNT;
        if (current) {
            if (combined.length <= 4 + SALT_LENGTH) {
                return null;
            }
            iterations = buffer.getInt();
            if (iterations < 1 || iterations > MAX_ITERATION_COUNT) {
                return null;
            }
        } else if (combined.length <= SALT_LENGTH) {
            return null;
        }
        var salt = new byte[SALT_LENGTH];
        buffer.get(salt);
        var hash = new byte[buffer.remaining()];
        buffer.get(hash);
        return new ParsedHash(iterations, salt, hash);
    }

    private static byte[] pbkdf2(String text, byte[] salt, int iterations)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        var spec = new PBEKeySpec(text.toCharArray(), salt, iterations, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * SHA-512 해시를 사용하여 파일을 512비트 해시로 변환한다.
     *
     * @param input Path
     * @return 128자리 고정 문자열(64바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 보안이 최우선인 경우 적합. 디지털 서명, 고도의 보안 환경에 사용</dd>
     *          </dl>
     */
    public static String generateSHA512(Path input) {
        return generateHash(HASH_ALGORITHM_SHA512, input);
    }

    /**
     * SHA-512 해시를 사용하여 InputStream 을 512비트 해시로 변환한다.
     *
     * @param input 입력 스트림
     * @return 128자리 고정 문자열(64바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 보안이 최우선인 경우 적합. 디지털 서명, 고도의 보안 환경에 사용</dd>
     *          <dd>주의!: 이 메서드는 InputStream 을 닫지 않으므로 호출자가 닫아야 함</dd>
     *          </dl>
     */
    public static String generateSHA512(InputStream input) {
        return generateHash(HASH_ALGORITHM_SHA512, input, false);
    }

    /**
     * SHA-512 해시를 사용하여 InputStream 을 512비트 해시로 변환한다.
     *
     * @param input             입력 스트림
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 128자리 고정 문자열(64바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 보안이 최우선인 경우 적합. 디지털 서명, 고도의 보안 환경에 사용</dd>
     *          </dl>
     */
    public static String generateSHA512(InputStream input, boolean shouldCloseStream) {
        return generateHash(HASH_ALGORITHM_SHA512, input, shouldCloseStream);
    }

    /**
     * SHA-512 해시를 사용하여 문자열을 512비트 해시로 변환한다.
     *
     * @param input 문자열
     * @return 128자리 고정 문자열(64바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 보안이 최우선인 경우 적합. 디지털 서명, 고도의 보안 환경에 사용</dd>
     *          </dl>
     */
    public static String generateSHA512(String input) {
        return generateHash(HASH_ALGORITHM_SHA512, input);
    }

    /**
     * SHA-512 해시를 사용하여 바이트 배열을 512비트 해시로 변환한다.
     *
     * @param input 바이트 배열
     * @return 128자리 고정 문자열(64바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 보안이 최우선인 경우 적합. 디지털 서명, 고도의 보안 환경에 사용</dd>
     *          </dl>
     */
    public static String generateSHA512(byte[] input) {
        return generateHash(HASH_ALGORITHM_SHA512, input);
    }

    /**
     * SHA-512/256 해시를 사용하여 파일을 256비트 해시로 변환한다.
     *
     * @param input Path
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 SHA-256과 동일한 출력 크기(256비트)를 제공하는 대체 알고리즘. 디지털 서명, 고도의 보안/성능이 중요한 환경에서 사용</dd>
     *          </dl>
     */
    public static String generateSHA512To256(Path input) {
        return generateHash(HASH_ALGORITHM_SHA512_256, input);
    }

    /**
     * SHA-512/256 해시를 사용하여 InputStream 을 256비트 해시로 변환한다.
     *
     * @param input 입력 스트림
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 SHA-256과 동일한 출력 크기(256비트)를 제공하는 대체 알고리즘. 디지털 서명, 고도의 보안/성능이 중요한 환경에서 사용</dd>
     *          <dd>주의!: 이 메서드는 InputStream 을 닫지 않으므로 호출자가 닫아야 함</dd>
     *          </dl>
     */
    public static String generateSHA512To256(InputStream input) {
        return generateHash(HASH_ALGORITHM_SHA512_256, input, false);
    }

    /**
     * SHA-512/256 해시를 사용하여 InputStream 을 256비트 해시로 변환한다.
     *
     * @param input             입력 스트림
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 SHA-256과 동일한 출력 크기(256비트)를 제공하는 대체 알고리즘. 디지털 서명, 고도의 보안/성능이 중요한 환경에서 사용</dd>
     *          </dl>
     */
    public static String generateSHA512To256(InputStream input, boolean shouldCloseStream) {
        return generateHash(HASH_ALGORITHM_SHA512_256, input, shouldCloseStream);
    }

    /**
     * SHA-512/256 해시를 사용하여 문자열을 256비트 해시로 변환한다.
     *
     * @param input 문자열
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 SHA-256과 동일한 출력 크기(256비트)를 제공하는 대체 알고리즘. 디지털 서명, 고도의 보안/성능이 중요한 환경에서 사용</dd>
     *          </dl>
     */
    public static String generateSHA512To256(String input) {
        return generateHash(HASH_ALGORITHM_SHA512_256, input);
    }

    /**
     * SHA-512/256 해시를 사용하여 바이트 배열을 256비트 해시로 변환한다.
     *
     * @param input 바이트 배열
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>64비트 시스템에서는 SHA-256 보다 효율적이며 SHA-256과 동일한 출력 크기(256비트)를 제공하는 대체 알고리즘. 디지털 서명, 고도의 보안/성능이 중요한 환경에서 사용</dd>
     *          </dl>
     */
    public static String generateSHA512To256(byte[] input) {
        return generateHash(HASH_ALGORITHM_SHA512_256, input);
    }

    /**
     * SHA-256 해시를 사용하여 파일을 256비트 해시로 변환한다.
     *
     * @param input Path
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>32비트 시스템으로 높은 호환성을 가지고 있음. XXHash64 에 비해 속도는 느리나 디지털 서명, 블록체인 등 보안성이 중요한 경우에 사용</dd>
     *          </dl>
     */
    public static String generateSHA256(Path input) {
        return generateHash(HASH_ALGORITHM_SHA256, input);
    }

    /**
     * SHA-256 해시를 사용하여 InputStream 을 256비트 해시로 변환한다.
     *
     * @param input 입력 스트림
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>32비트 시스템으로 높은 호환성을 가지고 있음. XXHash64 에 비해 속도는 느리나 디지털 서명, 블록체인 등 보안성이 중요한 경우에 사용</dd>
     *          <dd>주의!: 이 메서드는 InputStream 을 닫지 않으므로 호출자가 닫아야 함</dd>
     *          </dl>
     */
    public static String generateSHA256(InputStream input) {
        return generateHash(HASH_ALGORITHM_SHA256, input, false);
    }

    /**
     * SHA-256 해시를 사용하여 InputStream 을 256비트 해시로 변환한다.
     *
     * @param input             입력 스트림
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>32비트 시스템으로 높은 호환성을 가지고 있음. XXHash64 에 비해 속도는 느리나 디지털 서명, 블록체인 등 보안성이 중요한 경우에 사용</dd>
     *          </dl>
     */
    public static String generateSHA256(InputStream input, boolean shouldCloseStream) {
        return generateHash(HASH_ALGORITHM_SHA256, input, shouldCloseStream);
    }

    /**
     * SHA-256 해시를 사용하여 문자열을 256비트 해시로 변환한다.
     *
     * @param input 문자열
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>32비트 시스템으로 높은 호환성을 가지고 있음. XXHash64 에 비해 속도는 느리나 디지털 서명, 블록체인 등 보안성이 중요한 경우에 사용</dd>
     *          </dl>
     */
    public static String generateSHA256(String input) {
        return generateHash(HASH_ALGORITHM_SHA256, input);
    }

    /**
     * SHA-256 해시를 사용하여 바이트 배열을 256비트 해시로 변환한다.
     *
     * @param input 바이트 배열
     * @return 64자리 고정 문자열(32바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>32비트 시스템으로 높은 호환성을 가지고 있음. XXHash64 에 비해 속도는 느리나 디지털 서명, 블록체인 등 보안성이 중요한 경우에 사용</dd>
     *          </dl>
     */
    public static String generateSHA256(byte[] input) {
        return generateHash(HASH_ALGORITHM_SHA256, input);
    }

    /**
     * 지정된 알고리즘으로 파일을 해시로 변환하는 공통 구현.
     * generateSHA256/generateSHA512/generateSHA512To256(Path) 가 공유한다.
     *
     * @throws NullPointerException input 이 null 일 때
     * @throws S2RuntimeException   파일을 읽을 수 없을 때 (없는 파일, 디렉토리 등)
     */
    private static String generateHash(String algorithm, Path input) {
        Objects.requireNonNull(input, "input");
        try (InputStream inputStream = S2StreamUtil.getBufferedInputStream(Files.newInputStream(input))) {
            return generateHashFromStream(algorithm, inputStream, S2StreamUtil.getBufferSize(Files.size(input)));
        } catch (IOException e) {
            throw new S2RuntimeException(algorithm + " 해시 실패 [Path]: " + input, e);
        }
    }

    /**
     * 지정된 알고리즘으로 InputStream 을 해시로 변환하는 공통 구현.
     * generateSHA256/generateSHA512/generateSHA512To256(InputStream, boolean) 이 공유한다.
     */
    private static String generateHash(String algorithm, InputStream input, boolean shouldCloseStream) {
        Objects.requireNonNull(input, "input");
        try {
            return generateHashFromStream(algorithm, input, null);
        } catch (IOException e) {
            throw new S2RuntimeException(algorithm + " 해시 실패 [InputStream]", e);
        } finally {
            if (shouldCloseStream) {
                S2StreamUtil.closeStream(input);
            }
        }
    }

    /**
     * 지정된 알고리즘으로 문자열(UTF-8)을 해시로 변환하는 공통 구현. 빈 문자열도 해시한다.
     * generateSHA256/generateSHA512/generateSHA512To256(String) 이 공유한다.
     */
    private static String generateHash(String algorithm, String input) {
        return generateHash(algorithm, Objects.requireNonNull(input, "input").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 지정된 알고리즘으로 바이트 배열을 해시로 변환하는 공통 구현.
     * generateSHA256/generateSHA512/generateSHA512To256(byte[]) 이 공유한다.
     */
    private static String generateHash(String algorithm, byte[] input) {
        Objects.requireNonNull(input, "input");
        return bytesToHex(messageDigest(algorithm).digest(input));
    }

    private static MessageDigest messageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256/512 are mandatory in every JRE | 모든 JRE 필수 알고리즘
            throw new IllegalStateException(algorithm + " 을 지원하지 않는 JRE 입니다.", e);
        }
    }

    private static String generateHashFromStream(String algorithm, InputStream input, Integer bufferSize)
            throws IOException {
        var md = messageDigest(algorithm);
        var buffer = new byte[bufferSize != null && bufferSize > S2StreamUtil.getBufferSize() ? bufferSize
                : S2StreamUtil.getBufferSize()];
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            md.update(buffer, 0, bytesRead);
        }

        var hash = md.digest();
        return bytesToHex(hash);
    }

    private static String bytesToHex(byte[] hash) {
        return java.util.HexFormat.of().formatHex(hash);
    }

    /**
     * XXHash64 해시를 사용하여 파일을 64비트 해시로 변환한다.
     *
     * @param seed  해시 시드 값
     * @param input 해시할 파일
     * @return 16자리 고정 문자열(8바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>비암호화 해시 함수로 빠르고 효율적이며 보안성이 중요하지 않을때 사용</dd>
     *          </dl>
     */
    public static String generateXXHash64(long seed, Path input) {
        Objects.requireNonNull(input, "input");
        try (InputStream inputStream = S2StreamUtil.getBufferedInputStream(Files.newInputStream(input))) {
            return generateXXHash64FromStream(seed, S2StreamUtil.getBufferSize(Files.size(input)), false, inputStream);
        } catch (IOException e) {
            throw new S2RuntimeException("XXHash64 해시 실패 [Path]: " + input, e);
        }
    }

    /**
     * XXHash64 해시를 사용하여 InputStream 을 64비트 해시로 변환한다.
     *
     * @param seed   해시 시드 값
     * @param inputs 해시할 InputStream 배열 (가변 인자)
     * @return 16자리 고정 문자열(8바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>비암호화 해시 함수로 빠르고 효율적이며 보안성이 중요하지 않을때 사용</dd>
     *          <dd>주의!: 이 메서드는 InputStream 을 닫지 않으므로 호출자가 닫아야 함</dd>
     *          </dl>
     */
    public static String generateXXHash64(long seed, InputStream... inputs) {
        return generateXXHash64(seed, false, inputs);
    }

    /**
     * XXHash64 해시를 사용하여 InputStream 을 64비트 해시로 변환한다.
     *
     * @param seed              해시 시드 값
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @param inputs            해시할 InputStream 배열 (가변 인자)
     * @return 16자리 고정 문자열(8바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>비암호화 해시 함수로 빠르고 효율적이며 보안성이 중요하지 않을때 사용</dd>
     *          </dl>
     */
    public static String generateXXHash64(long seed, boolean shouldCloseStream, InputStream... inputs) {
        if (S2Util.isEmpty(inputs)) {
            throw new IllegalArgumentException("해시할 InputStream 이 없습니다.");
        }

        try {
            return generateXXHash64FromStream(seed, null, shouldCloseStream, inputs);
        } catch (IOException e) {
            throw new S2RuntimeException("XXHash64 해시 실패 [InputStream]", e);
        }
    }

    /**
     * XXHash64 해시를 사용하여 문자열을 64비트 해시로 변환한다.
     *
     * @param seed  해시 시드 값
     * @param input 해시할 문자열
     * @return 16자리 고정 문자열(8바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>비암호화 해시 함수로 빠르고 효율적이며 보안성이 중요하지 않을때 사용</dd>
     *          </dl>
     */
    public static String generateXXHash64(long seed, String input) {
        return generateXXHash64(seed, Objects.requireNonNull(input, "input").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * XXHash64 해시를 사용하여 바이트 배열을 64비트 해시로 변환한다.
     *
     * @param seed  해시 시드 값
     * @param input 해시할 바이트 배열
     * @return 16자리 고정 문자열(8바이트, 16진수)
     * @details
     *          <dl>
     *          <dd>비암호화 해시 함수로 빠르고 효율적이며 보안성이 중요하지 않을때 사용</dd>
     *          </dl>
     */
    public static String generateXXHash64(long seed, byte[] input) {
        Objects.requireNonNull(input, "input");
        return String.format("%016x", XXHashHolder.FACTORY.hash64().hash(input, 0, input.length, seed));
    }

    /**
     * XXHash64 해시를 사용하여 InputStream 을 64비트 해시로 변환하는 공통 메서드.
     *
     * @param seed              해시 시드 값
     * @param bufferSize        버퍼 크기
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @param inputs            InputStream 배열 (가변 인자)
     * @return 16자리 고정 문자열(8바이트, 16진수)
     * @throws IOException 스트림 읽기 오류 발생 시
     * @details
     *          <dl>
     *          <dd>비암호화 해시 함수로 빠르고 효율적이며 보안성이 중요하지 않을때 사용</dd>
     *          </dl>
     */
    private static String generateXXHash64FromStream(long seed, Integer bufferSize, boolean shouldCloseStream,
            InputStream... inputs) throws IOException {
        try (var hash64 = XXHashHolder.FACTORY.newStreamingHash64(seed)) {
            var buffer = new byte[bufferSize != null && bufferSize > S2StreamUtil.getBufferSize() ? bufferSize
                    : S2StreamUtil.getBufferSize()];

            for (var input : inputs) {
                if (input == null)
                    continue;

                try {
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        hash64.update(buffer, 0, bytesRead);
                    }
                } finally {
                    if (shouldCloseStream) {
                        S2StreamUtil.closeStream(input);
                    }
                }
            }

            return String.format("%016x", hash64.getValue());
        }

    }

}
