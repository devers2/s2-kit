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
package io.github.devers2.s2kit.file.impl.spring;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.integration.sftp.session.SftpSession;
import org.springframework.stereotype.Component;

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2kit.file.FileManager;
import io.github.devers2.s2kit.file.S2FileExistsException;
import io.github.devers2.s2kit.file.S2ResourceInputStream;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;
import io.github.devers2.s2kit.support.S2FileUtil;

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
                // 풀이 가득 차면 borrowObject 가 maxWait 동안 반납을 기다린다 (정상적인 backpressure). 예전처럼 evict() 를
                // 부르는 것은 유휴 세션만 정리하므로 도움이 안 되고, 고정 대기는 대량 요청에서 지연만 늘린다.
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
                    backoff();
                }
            } catch (Exception e) {
                lastException = e;
                attempts++;
                logger.warn("Failed to get session, attempt {}/{}: {}", attempts, maxRetries, e.getMessage());
                if (attempts < maxRetries) {
                    backoff();
                }
            }
        }

        throw new S2RuntimeException(
                "SFTP 세션 획득 실패: " + (lastException != null ? lastException.getMessage() : "최대 재시도 횟수 초과"),
                lastException);
    }

    /** 여러 스레드가 같은 순간에 다시 몰리지 않도록 흩뜨려 기다린다 (0.5~1.5초) */
    private static void backoff() throws InterruptedException {
        Thread.sleep(500L + java.util.concurrent.ThreadLocalRandom.current().nextLong(1000L));
    }

    /**
     * 지정된 경로에 파일을 업로드
     *
     * @param fileData 업로드할 파일 데이터 (InputStream)
     * @param savePath 원격 서버의 대상 파일 저장 경로
     * @param saveName  원격 서버의 대상 파일 저장 명
     * @param overwrite true 면 같은 이름의 원격 파일을 덮어씀 (false 면 예외)
     * @return 저장된 파일의 크기(바이트 단위)
     * @throws S2RuntimeException 같은 이름의 파일이 있거나(덮어쓰지 않을 때), 경로가 벗어나거나, 업로드에 실패했을 때
     */
    @Override
    public long writeFile(InputStream fileData, String savePath, String saveName, boolean overwrite) {
        long fileSize = -1;
        String remoteFileFullPath = S2FileUtil.resolveRemoteWithin(savePath, saveName);
        SftpSession session = null;
        // 이 호출이 새로 만든(덮어쓰지 않는) 파일에 쓰기 시작했는지. 실패하면 불완전한 파일을 지운다
        boolean createdNewFile = false;

        try {
            session = borrowSessionSafely();
            try (InputStream bufferedInput = new BufferedInputStream(fileData);
                    CountingInputStream countingInput = new CountingInputStream(bufferedInput)) {
                // 디렉토리가 없으면 생성 (saveName 에 하위 폴더가 있을 수 있으므로 대상 파일의 상위 폴더)
                String remoteDir = parentDirOf(remoteFileFullPath);
                if (remoteDir != null) {
                    createDirectoryIfNotExists(session, remoteDir);
                }

                // SftpSession.write replaces an existing file, so check first (same rule as the other managers) | write 는 기존 파일을 덮으므로 먼저 확인
                if (!overwrite && session.exists(remoteFileFullPath)) {
                    throw new S2FileExistsException("이미 존재하는 원격 파일입니다: " + remoteFileFullPath);
                }

                // 파일 업로드
                createdNewFile = !overwrite;
                session.write(countingInput, remoteFileFullPath);
                createdNewFile = false; // 전송 완료: 이후 실패해도 파일은 온전하다
                // 실제 전송한 바이트 수 반환
                fileSize = countingInput.getByteCount();
                logger.debug("File uploaded successfully: {}", remoteFileFullPath);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // 재시도 대기 중 인터럽트: 상태를 되살려 호출자가 알 수 있게 한다
            }
            logger.error("Failed to upload file: {}", remoteFileFullPath, e);
            throw new S2RuntimeException("원격 파일 업로드 실패: " + saveName + " (" + e.getMessage() + ")", e);
        } finally {
            if (createdNewFile && session != null) {
                // 로컬 구현처럼 불완전한 파일을 남기지 않는다 (남기면 다시 올릴 때 "이미 존재" 로 거부됨)
                try {
                    if (session.exists(remoteFileFullPath)) {
                        session.remove(remoteFileFullPath);
                    }
                } catch (IOException | RuntimeException e) {
                    logger.warn("Failed to remove the partial upload: {} ({})", remoteFileFullPath, e.getMessage());
                }
            }
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
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // 재시도 대기 중 인터럽트: 상태를 되살려 호출자가 알 수 있게 한다
            }
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
            throw new S2RuntimeException("Failed to delete file: " + e, e);
        } catch (Exception e) {
            logger.error("Failed to delete file: {}", remoteFileFullPath, e);
            throw new S2RuntimeException("Failed to delete file: " + e, e);
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
            // 상대 경로는 접속 계정의 홈 기준이므로 앞에 "/" 를 붙이지 않는다 (붙이면 파일과 다른 곳에 폴더가 생김)
            boolean absolute = path.startsWith("/");

            for (String folder : folders) {
                if (folder.isBlank())
                    continue;

                if (absolute || fullPath.length() > 0) {
                    fullPath.append("/");
                }
                fullPath.append(folder);
                if (!session.exists(fullPath.toString())) {
                    try {
                        session.mkdir(fullPath.toString());
                        logger.debug("Created directory: {}", fullPath);
                    } catch (IOException e) {
                        // 동시에 같은 폴더로 올리는 다른 요청이 먼저 만들었을 수 있다 (확인과 생성 사이의 경쟁). 생겼으면 성공으로 본다
                        if (!session.exists(fullPath.toString())) {
                            throw e;
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to create directory: {}", path, e);
            throw new S2RuntimeException("Failed to create directory: " + e, e);
        }
    }

    /**
     * 이어 쓰기: {@code .<이름>.<resumeKey>.s2part}에 쓰고(남아 있으면 그 크기만큼 건너뛰고 덧붙임), 다 받으면 대상 이름으로 바꾼다.
     * 전송이 실패하면 부분 파일을 남겨 다음 시도가 이어 쓰게 한다.
     */
    @Override
    public long writeFile(java.nio.file.Path source, String savePath, String saveName, boolean overwrite,
            String resumeKey) {
        if (resumeKey == null) {
            return FileManager.super.writeFile(source, savePath, saveName, overwrite, null);
        }
        String target = S2FileUtil.resolveRemoteWithin(savePath, saveName);
        String part = partPathOf(target, resumeKey);
        SftpSession session = null;
        try {
            long sourceSize = java.nio.file.Files.size(source);
            session = borrowSessionSafely();
            String remoteDir = parentDirOf(target);
            if (remoteDir != null) {
                createDirectoryIfNotExists(session, remoteDir);
            }
            if (!overwrite && session.exists(target)) {
                throw new S2FileExistsException("이미 존재하는 원격 파일입니다: " + target);
            }
            var client = session.getClientInstance();
            long offset = session.exists(part) ? client.stat(part).getSize() : 0L;
            if (offset > sourceSize) {
                // 다른 내용의 부분 파일: 처음부터 다시 쓴다
                session.remove(part);
                offset = 0L;
            }
            try (InputStream in = java.nio.file.Files.newInputStream(source)) {
                if (offset > 0) {
                    logger.info("Resuming upload: {} ({} / {} bytes)", target, offset, sourceSize);
                    in.skipNBytes(offset);
                    session.append(in, part);
                } else {
                    session.write(in, part);
                }
            }
            long size = client.stat(part).getSize();
            if (size != sourceSize) {
                session.remove(part); // 어긋난 부분 파일은 이어 쓰면 안 되므로 지운다
                throw new S2RuntimeException("Uploaded size " + size + " does not match the file size " + sourceSize);
            }
            if (!overwrite && session.exists(target)) {
                session.remove(part);
                throw new S2FileExistsException("이미 존재하는 원격 파일입니다: " + target);
            }
            session.rename(part, target); // 대상이 있으면 덮어씀 (overwrite 가 아니면 바로 위에서 확인함)
            return size;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Failed to upload file (resumable): {}", target, e);
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
    }

    @Override
    public void discardPartial(String savePath, String saveName, String resumeKey) {
        if (resumeKey == null) {
            return;
        }
        String part = partPathOf(S2FileUtil.resolveRemoteWithin(savePath, saveName), resumeKey);
        SftpSession session = null;
        try {
            session = borrowSessionSafely();
            if (session.exists(part)) {
                session.remove(part);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to remove the partial upload: {} ({})", part, e.toString());
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

    /** 대상 옆의 숨김 부분 파일 경로: {@code <폴더>/.<이름>.<resumeKey>.s2part} */
    private static String partPathOf(String target, String resumeKey) {
        if (!resumeKey.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("resumeKey must be 1-64 letters, digits, '-' or '_': " + resumeKey);
        }
        int index = target.lastIndexOf('/');
        return target.substring(0, index + 1) + "." + target.substring(index + 1) + "." + resumeKey + ".s2part";
    }

    /** 정규화된 원격 파일 경로의 상위 폴더 (상위 폴더가 홈이면 null) */
    private static String parentDirOf(String remoteFilePath) {
        int index = remoteFilePath.lastIndexOf('/');
        if (index < 0) {
            return null;
        }
        return index == 0 ? "/" : remoteFilePath.substring(0, index);
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
