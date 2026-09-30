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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2025. 03. 07.
 */
public class S2StreamUtil {

    private static final S2Logger logger = S2LogManager.getLogger(S2StreamUtil.class);

    /**
     * 기본 버퍼 크기를 반환한다.
     *
     * @return 버퍼 크기(기본값 32KB)
     */
    public static int getBufferSize() {
        return getBufferSize(null);
    }

    /**
     * 파일 또는 데이터의 예상 크기에 따라 적절한 버퍼 크기를 반환한다.
     *
     * @param targetSize 파일 또는 데이터 사이즈
     * @return 파일 또는 데이터 사이즈별 버퍼 크기
     */
    public static int getBufferSize(Long targetSize) {
        if (targetSize == null) {
            // 기본 버퍼 크기는 32KB 또는 64KB
            return 32 * 1024;
        } else if (targetSize <= 100 * 1024) { // 100KB 이하
            return 8 * 1024;
        } else if (targetSize <= 1024 * 1024) { // 1MB 이하
            return 16 * 1024;
        } else if (targetSize <= 100 * 1024 * 1024) { // 100MB 이하
            return 64 * 1024;
        } else if (targetSize <= 500 * 1024 * 1024) { // 500MB 이하
            return 128 * 1024;
        } else if (targetSize <= 1024 * 1024 * 1024) { // 1GB 이하
            return 256 * 1024;
        } else { // 1GB 초과
            return 512 * 1024; // 또는 1MB 등으로 조정 가능
        }
    }

    /**
     * 데이터 스트림을 닫는다.
     *
     * @param stream 스트림 객체
     */
    public static void closeStream(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                logger.error("Failed to close stream", e);
                stream = null;
            }
        }
    }

    /**
     * InputStream 을 BufferedInputStream 으로 변환한다.
     *
     * @param inputStream 변환할 InputStream
     * @return BufferedInputStream 또는 null (inputStream 이 null 인 경우)
     * @details
     *          <dl>
     *          <dd>이미 BufferedInputStream 인 경우 그대로 반환</dd>
     *          </dl>
     */
    public static BufferedInputStream getBufferedInputStream(InputStream inputStream) {
        return getBufferedInputStream(inputStream, null);
    }

    /**
     * InputStream 을 BufferedInputStream 으로 변환한다.
     *
     * @param inputStream 변환할 InputStream
     * @param bufferSize  버퍼 크기
     * @return BufferedInputStream 또는 null (inputStream 이 null 인 경우)
     * @details
     *          <dl>
     *          <dd>이미 BufferedInputStream 인 경우 그대로 반환</dd>
     *          </dl>
     */
    public static BufferedInputStream getBufferedInputStream(InputStream inputStream, Integer bufferSize) {
        if (inputStream == null) {
            logger.debug("InputStream is null.");
            return null;
        }
        return inputStream instanceof BufferedInputStream
                ? (BufferedInputStream) inputStream
                : bufferSize != null && bufferSize > getBufferSize()
                        ? new BufferedInputStream(inputStream, bufferSize)
                        : new BufferedInputStream(inputStream);
    }

    /**
     * OutputStream 을 BufferedOutputStream 으로 변환한다.
     *
     * @param outputStream 변환할 OutputStream
     * @return BufferedOutputStream 또는 null (outputStream 이 null 인 경우)
     * @details
     *          <dl>
     *          <dd>이미 BufferedOutputStream 인 경우 그대로 반환</dd>
     *          </dl>
     */
    public static BufferedOutputStream getBufferedOutputStream(OutputStream outputStream) {
        return getBufferedOutputStream(outputStream, null);
    }

    /**
     * OutputStream 을 BufferedOutputStream 으로 변환한다.
     *
     * @param outputStream 변환할 OutputStream
     * @param bufferSize   버퍼 크기
     * @return BufferedOutputStream 또는 null (outputStream 이 null 인 경우)
     * @details
     *          <dl>
     *          <dd>이미 BufferedOutputStream 인 경우 그대로 반환</dd>
     *          </dl>
     */
    public static BufferedOutputStream getBufferedOutputStream(OutputStream outputStream, Integer bufferSize) {
        if (outputStream == null) {
            logger.debug("OutputStream is null.");
            return null;
        }
        return outputStream instanceof BufferedOutputStream
                ? (BufferedOutputStream) outputStream
                : bufferSize != null && bufferSize > getBufferSize()
                        ? new BufferedOutputStream(outputStream, bufferSize)
                        : new BufferedOutputStream(outputStream);
    }

    /** Default limit of {@code streamToByteArray} (50MB) | streamToByteArray 기본 한도 (50MB) */
    public static final long DEFAULT_MAX_BYTES = 50L * 1024 * 1024;

    /**
     * InputStream 을 바이트 배열로 변환한다. 최대 {@link #DEFAULT_MAX_BYTES}(50MB)까지 읽는다.
     *
     * @param sourceStream      처리할 InputStream
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 바이트 배열
     * @throws NullPointerException sourceStream 이 null 일 때
     * @throws S2RuntimeException   읽기 실패 또는 한도 초과 시 (원인 예외 포함)
     */
    public static byte[] streamToByteArray(InputStream sourceStream, boolean shouldCloseStream) {
        return streamToByteArray(sourceStream, shouldCloseStream, DEFAULT_MAX_BYTES);
    }

    /**
     * InputStream 을 바이트 배열로 변환한다.
     *
     * @param sourceStream      처리할 InputStream
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @param maxBytes          최대 바이트 수 (넘으면 예외)
     * @return 바이트 배열
     * @throws NullPointerException sourceStream 이 null 일 때
     * @throws S2RuntimeException   읽기 실패 또는 한도 초과 시 (원인 예외 포함)
     */
    public static byte[] streamToByteArray(InputStream sourceStream, boolean shouldCloseStream, long maxBytes) {
        Objects.requireNonNull(sourceStream, "sourceStream");
        try (var outputStream = new LimitedByteArrayOutputStream(maxBytes)) {
            sourceStream.transferTo(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new S2RuntimeException("바이트 배열 변환 실패: " + e.getMessage(), e);
        } finally {
            if (shouldCloseStream) {
                closeStream(sourceStream);
            }
        }
    }

    /**
     * Reader 를 UTF-8 바이트 배열로 변환한다. 최대 {@link #DEFAULT_MAX_BYTES}(50MB)까지 읽는다.
     *
     * @param sourceReader      처리할 Reader
     * @param shouldCloseStream sourceReader 를 닫을지 여부
     * @return UTF-8 바이트 배열
     * @throws NullPointerException sourceReader 가 null 일 때
     * @throws S2RuntimeException   읽기 실패 또는 한도 초과 시 (원인 예외 포함)
     */
    public static byte[] streamToByteArray(Reader sourceReader, boolean shouldCloseStream) {
        return streamToByteArray(sourceReader, shouldCloseStream, DEFAULT_MAX_BYTES);
    }

    /**
     * Reader 를 UTF-8 바이트 배열로 변환한다. 버퍼 경계에 걸친 서로게이트 쌍(이모지 등)도 올바르게 인코딩한다.
     *
     * @param sourceReader      처리할 Reader
     * @param shouldCloseStream sourceReader 를 닫을지 여부
     * @param maxBytes          최대 바이트 수 (넘으면 예외)
     * @return UTF-8 바이트 배열
     * @throws NullPointerException sourceReader 가 null 일 때
     * @throws S2RuntimeException   읽기 실패 또는 한도 초과 시 (원인 예외 포함)
     */
    public static byte[] streamToByteArray(Reader sourceReader, boolean shouldCloseStream, long maxBytes) {
        Objects.requireNonNull(sourceReader, "sourceReader");
        var outputStream = new LimitedByteArrayOutputStream(maxBytes);
        // OutputStreamWriter keeps a pending high surrogate until its pair arrives | 짝이 올 때까지 상위 서로게이트를 보관
        try (var writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            sourceReader.transferTo(writer);
        } catch (IOException e) {
            throw new S2RuntimeException("바이트 배열 변환 실패: " + e.getMessage(), e);
        } finally {
            if (shouldCloseStream) {
                closeStream(sourceReader);
            }
        }
        return outputStream.toByteArray();
    }

    /** Fails as soon as more than {@code maxBytes} are written | 한도를 넘는 순간 실패 */
    private static final class LimitedByteArrayOutputStream extends ByteArrayOutputStream {
        private final long maxBytes;

        LimitedByteArrayOutputStream(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void write(int b) {
            ensure(1);
            super.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            ensure(len);
            super.write(b, off, len);
        }

        private void ensure(int len) {
            if ((long) count + len > maxBytes) {
                throw new S2RuntimeException("데이터 크기가 최대 허용 크기(" + maxBytes + " bytes)를 초과했습니다.");
            }
        }
    }

    /**
     * 입력 스트림에서 출력 스트림으로 데이터를 청크 단위로 복사하며, 전송된 총 바이트 수를 반환
     *
     * @param inputStream  입력 스트림
     * @param outputStream 출력 스트림
     * @return 전송된 총 바이트 수
     * @throws IOException 입출력 중 오류가 발생한 경우. 예: 스트림이 닫혔거나 네트워크 연결이 끊긴 경우.
     */
    public static long copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        if (inputStream == null || outputStream == null) {
            throw new NullPointerException("inputStream 과 outputStream 은 null 일 수 없습니다.");
        }

        // Java 9+ transferTo 사용 (네이티브 최적화 및 코드 단순화)
        return inputStream.transferTo(outputStream);
    }

    /**
     * InputStream 을 UTF-8 문자열로 변환한다. 스트림은 닫지 않는다.
     *
     * @param inputStream InputStream
     * @return 문자열
     * @throws NullPointerException inputStream 이 null 일 때
     * @throws S2RuntimeException   읽기 실패 시 (원인 예외 포함)
     */
    public static String convertStreamToString(InputStream inputStream) {
        return new String(streamToByteArray(inputStream, false, Long.MAX_VALUE), StandardCharsets.UTF_8);
    }

}
