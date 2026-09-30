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
package io.github.devers2.s2util.file.impl.spring;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.integration.sftp.session.SftpSession;
import org.springframework.stereotype.Component;

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.file.FileManager;
import io.github.devers2.s2util.file.S2ResourceInputStream;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;
import io.github.devers2.s2util.support.S2FileUtil;

/**
 * SFTP 파일 전송 서비스 파일 업로드, 다운로드, 삭제 및 디렉토리 생성 기능을 제공합니다.
 */
@Component
public class SpringSftpFileManagerImpl implements FileManager {

    private static final S2Logger logger = S2LogManager.getLogger(SpringSftpFileManagerImpl.class);

    private final GenericObjectPool<SftpSession> sessionPool;

    public SpringSftpFileManagerImpl(GenericObjectPool<SftpSession> sessionPool) {
        this.sessionPool = sessionPool;
    }

    /**
     * 원격 여부
     *
     * @return 원격 여부
     */
    @Override
    public boolean isRemote() {
        return true;
    }

    // 에빅터와 validate가 설정되어 있어 별도 강제 리셋은 하지 않음

    /**
     * 세션 풀에서 세션을 안전하게 가져옵니다. 풀이 가득 찼을 경우 재시도합니다.
     */
    private SftpSession borrowSessionSafely() throws InterruptedException {
        int attempts = 0;
        Exception lastException = null;

        // 최대 재시도 횟수
        int maxRetries = 3;
        while (attempts < maxRetries) {
            try {
                if (sessionPool.getNumActive() >= sessionPool.getMaxTotal()) {
                    logger.warn("Session pool is full. Triggering eviction and retry...");
                    try {
                        sessionPool.evict();
                    } catch (Exception ignore) {
                        // evict() 메서드는 Exception만을 처리 함
                        logger.debug("Ignored exception during pool eviction", ignore);
                    }
                    Thread.sleep(500);
                }

                // 풀에서만 대여 (직접 생성 금지)
                SftpSession session = sessionPool.borrowObject();
                if (session != null && session.isOpen()) {
                    return session;
                }
                if (session != null) {
                    try {
                        session.close();
                    } catch (RuntimeException ignore) {
                        logger.debug("Ignored runtime exception while closing SFTP session", ignore);
                    }
                }
            } catch (InterruptedException e) {
                // 인터럽트는 재시도 대상이 아니다. 상태를 복구하고 즉시 전파해서 호출자(예: 종료 중인
                // ExecutorService)가 인터럽트를 인지할 수 있게 한다 - 삼켜서 재시도하면 안 된다.
                Thread.currentThread().interrupt();
                throw e;
            } catch (java.util.NoSuchElementException | IllegalStateException e) {
                lastException = e;
                attempts++;
                logger.warn("Failed to get session, attempt {}/{}: {}", attempts, maxRetries, e.getMessage());

                if (attempts < maxRetries) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                lastException = e;
                attempts++;
                logger.warn("Failed to get session, attempt {}/{}: {}", attempts, maxRetries, e.getMessage());
                if (attempts < maxRetries) {
                    Thread.sleep(1000);
                }
            }
        }

        throw new S2RuntimeException(
                "SFTP 세션 획득 실패: " + (lastException != null ? lastException.getMessage() : "최대 재시도 횟수 초과"),
                lastException);
    }

    /**
     * 지정된 경로에 파일을 업로드
     *
     * @param fileData 업로드할 파일 데이터 (InputStream)
     * @param savePath 원격 서버의 대상 파일 저장 경로
     * @param saveName 원격 서버의 대상 파일 저장 명
     * @return 저장된 파일의 크기(바이트 단위), 실패 시 -1
     */
    @Override
    public long writeFile(InputStream fileData, String savePath, String saveName) {
        long fileSize = -1;
        String remoteFileFullPath = S2FileUtil.resolveRemoteWithin(savePath, saveName);
        SftpSession session = null;

        try {
            session = borrowSessionSafely();
            try (InputStream bufferedInput = new BufferedInputStream(fileData);
                    CountingInputStream countingInput = new CountingInputStream(bufferedInput)) {
                // 디렉토리가 없으면 생성
                createDirectoryIfNotExists(session, savePath);

                // 파일 업로드
                session.write(countingInput, remoteFileFullPath);
                // 실제 전송한 바이트 수 반환
                fileSize = countingInput.getByteCount();
                logger.debug("File uploaded successfully: {}", remoteFileFullPath);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to upload file: {}", remoteFileFullPath, e);
            throw new S2RuntimeException("원격 파일 업로드 실패: " + saveName + " (" + e.getMessage() + ")", e);
        } finally {
            if (session != null) {
                try {
                    sessionPool.returnObject(session);
                } catch (IllegalStateException | IllegalArgumentException e) {
                    logger.warn("Failed to return session to pool", e);
                }
            }
        }
        return fileSize;
    }

    /**
     * 지정된 경로의 파일을 다운로드
     *
     * @param savePath 원격 서버의 대상 파일 저장 경로
     * @param saveName 원격 서버의 대상 파일 저장 명
     * @return 파일 내용을 담은 InputStream
     */
    @Override
    public InputStream readFile(String savePath, String saveName) {
        String remoteFileFullPath = S2FileUtil.resolveRemoteWithin(savePath, saveName);
        SftpSession session = null;
        boolean success = false;
        try {
            session = borrowSessionSafely(); // 수정된 메서드 사용
            if (!session.exists(remoteFileFullPath)) {
                throw new IOException("File not found: " + remoteFileFullPath);
            }

            InputStream rawInputStream = session.readRaw(remoteFileFullPath);
            logger.debug("File download stream opened successfully: {}", remoteFileFullPath);

            final SftpSession finalSession = session;
            InputStream resultStream = new S2ResourceInputStream(rawInputStream) {
                private volatile boolean closed = false;

                @Override
                public synchronized void close() {
                    if (!closed) {
                        try {
                            super.close();
                        } finally {
                            try {
                                sessionPool.returnObject(finalSession);
                            } catch (IllegalStateException | IllegalArgumentException e) {
                                logger.warn("Failed to return SFTP session to pool", e);
                            }
                            closed = true;
                        }
                    }
                }
            };
            success = true; // Mark as success only before returning the stream
            return resultStream;
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to open download stream for file: {}", remoteFileFullPath, e);
            throw new S2RuntimeException("원격 파일 다운로드 실패: " + saveName + " (" + e.getMessage() + ")", e);
        } finally {
            if (!success && session != null) {
                try {
                    sessionPool.returnObject(session);
                } catch (IllegalStateException | IllegalArgumentException e) {
                    logger.warn("Failed to return session to pool on error path", e);
                }
            }
        }
    }

    /**
     * 지정된 경로의 파일을 삭제합니다.
     *
     * @param savePath 원격 서버의 대상 파일 저장 경로
     * @param saveName 원격 서버의 대상 파일 저장 명
     */
    public void deleteFile(String savePath, String saveName) {
        String remoteFileFullPath = S2FileUtil.resolveRemoteWithin(savePath, saveName);
        SftpSession session = null;
        try {
            // writeFile/readFile 과 동일하게 재시도/eviction 이 포함된 안전한 대여를 사용한다
            // (직접 sessionPool.borrowObject() 를 쓰면 풀 고갈 시 재시도 없이 바로 실패한다).
            session = borrowSessionSafely();
            if (session.exists(remoteFileFullPath)) {
                session.remove(remoteFileFullPath);
                logger.debug("File deleted successfully: {}", remoteFileFullPath);
            } else {
                logger.warn("File not found for deletion: {}", remoteFileFullPath);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Failed to delete file (interrupted): {}", remoteFileFullPath, e);
            throw new S2RuntimeException("Failed to delete file: " + e);
        } catch (Exception e) {
            logger.error("Failed to delete file: {}", remoteFileFullPath, e);
            throw new S2RuntimeException("Failed to delete file: " + e);
        } finally {
            if (session != null) {
                try {
                    sessionPool.returnObject(session);
                } catch (IllegalStateException | IllegalArgumentException e) {
                    logger.warn("Failed to return session to pool", e);
                }
            }
        }
    }

    /**
     * 지정된 경로의 디렉토리가 존재하지 않을 경우 생성합니다. 중간 경로의 디렉토리들도 필요한 경우 함께 생성됩니다.
     *
     * @param session sftp 세션
     * @param path    생성할 디렉토리 경로
     */
    private void createDirectoryIfNotExists(SftpSession session, String path) {
        try {
            String[] folders = path.split("/");
            StringBuilder fullPath = new StringBuilder();

            for (String folder : folders) {
                if (folder.isBlank())
                    continue;

                fullPath.append("/").append(folder);
                if (!session.exists(fullPath.toString())) {
                    session.mkdir(fullPath.toString());
                    logger.debug("Created directory: {}", fullPath);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to create directory: {}", path, e);
            throw new S2RuntimeException("Failed to create directory: " + e);
        }
    }

    private static class CountingInputStream extends FilterInputStream {
        private long byteCount = 0L;

        protected CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                byteCount++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                byteCount += n;
            }
            return n;
        }

        public long getByteCount() {
            return byteCount;
        }
    }

}
