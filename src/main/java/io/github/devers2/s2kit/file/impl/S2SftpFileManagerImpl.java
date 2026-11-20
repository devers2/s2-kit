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
package io.github.devers2.s2kit.file.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

import io.github.devers2.s2kit.S2KitMessages;
import io.github.devers2.s2util.core.S2Util;
import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2kit.file.FileManager;
import io.github.devers2.s2kit.file.S2FileExistsException;
import io.github.devers2.s2kit.file.JschSessionFactory;
import io.github.devers2.s2kit.file.S2ResourceInputStream;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;
import io.github.devers2.s2kit.support.S2FileUtil;
import io.github.devers2.s2kit.support.S2StreamUtil;

/**
 * s2's utilities
 * SFTP 파일 전송 서비스 파일 업로드, 다운로드, 삭제 및 디렉토리 생성 기능을 제공
 * (단, 엔터프라이즈 환경에서 안정성, 모니터링, 대용량 처리가 중요하다면 spring-integration-sftp 사용을 고려할 필요가 있음)
 *
 * @author devers2
 * @version 1.0
 * @since 2025. 02. 01.
 */
public class S2SftpFileManagerImpl implements FileManager, AutoCloseable {

    private static final S2Logger logger = S2LogManager.getLogger(S2SftpFileManagerImpl.class);

    // 디렉토리 생성 캐시: 키 → 마지막 기록 시각(ms)
    private final ConcurrentMap<String, Long> createdDirs = new ConcurrentHashMap<>();
    // TTL 기본값 (5분). 시스템 프로퍼티로 오버라이드 가능: -Ds2.sftp.dirCacheTtlMs=60000
    private static final long DEFAULT_DIR_CACHE_TTL_MS = 5 * 60_000L;
    private final long dirCacheTtlMillis;
    // 주기적 정리 트리거 (정비 주기마다 캐시 스윕)
    private final AtomicInteger cacheUpdateCounter = new AtomicInteger();

    private static final int CHANNEL_CONNECT_TIMEOUT = 30_000; // 30초
    /*
     * 업/다운로드 idle(마지막 활동 이후 미사용) 자동 정리 시간(밀리초)
     * - 다운로드: 호출 측이 반환받은 InputStream 을 close() 하지 않아도, 마지막 read() 이후 이
     *   시간 동안 추가 읽기가 없으면 자동으로 스트림/채널/세션을 정리합니다.
     * - 업로드: JSch 진행률 콜백(count())이 마지막으로 호출된 이후 이 시간 동안 추가 진행이
     *   없으면(=완전히 멈춘 전송) 채널을 강제로 끊어 블로킹 중인 put() 호출을 깨웁니다.
     * - 둘 다 "생성/시작 후 경과 시간"이 아닌 "마지막 활동 이후 경과 시간" 기준이므로, 대용량
     *   파일을 느린 회선으로 계속 주고받는 정상적인 전송은 끊기지 않습니다.
     * - 장시간 유출(호출 측 close() 누락)이나 완전히 멎어버린 전송으로 인한 세션 풀 고갈을
     *   방지하기 위함입니다.
     */
    private static final long TRANSFER_IDLE_MILLIS = 60_000L; // 1분
    private static final long TRANSFER_IDLE_CHECK_INTERVAL_MILLIS = 30_000L; // 30초마다 idle 여부 점검

    private final JschSessionFactory jschSessionFactory;

    /** 정리 작업을 공용 실행기에서 돌린다 ({@link S2TransferWatchdog#runCleanup}). */
    private static void runCleanup(Runnable cleanup) {
        S2TransferWatchdog.runCleanup(cleanup);
    }

    /** 주기 점검을 등록한다 ({@link S2TransferWatchdog#scheduleCheck}). */
    private static ScheduledFuture<?> scheduleCheck(Runnable check, long intervalMillis) {
        return S2TransferWatchdog.scheduleCheck(check, intervalMillis);
    }

    /**
     * @param host           sftp host
     * @param port           sftp port
     * @param username       sftp username
     * @param privateKeyPath sftp private key path
     * @param passphrase     sftp private key passphrase
     * @param password       sftp password
     */
    public S2SftpFileManagerImpl(String host, int port, String username, String privateKeyPath, String passphrase,
            String password) {
        this(host, port, username, privateKeyPath, passphrase, password, null, null, null);
    }

    /**
     * @param host                 sftp host
     * @param port                 sftp port
     * @param username             sftp username
     * @param privateKeyPath       sftp private key path
     * @param passphrase           sftp private key passphrase
     * @param password             sftp password
     * @param sessionMaxTotal      세션 풀의 최대 세션 수 (기본값 64)
     * @param sessionMinIdle       세션 풀에 유휴 상태로 유지할 최소 세션 수 (기본값 8)
     * @param sessionMaxWaitMillis 세션 풀에서 사용 가능한 세션을 기다리는 최대 시간
     * @apiNote 세션 풀의 각 세션은 SFTP 서버와의 독립적인 SSH 연결이고, 한 연결에서 SFTP 채널은 한 번에 하나만 연다.
     *          그래서 서버의 {@code MaxSessions}(연결당 채널 수, 기본 10)는 늘릴 필요가 없다.
     *          신규 연결은 동시에 최대 4개만 진행하도록 줄 세우므로(-Ds2.sftp.maxConcurrentConnects 로 변경), 앱 서버가 두 대까지는
     *          OpenSSH 기본값({@code MaxStartups 10:30:100}, 인증 전 연결이 10개를 넘으면 무작위로 끊음)으로도 콜드스타트나
     *          대량 요청 때 연결이 끊기지 않는다. 운영 환경에서는 아래를 권장한다.
     *
     *          <pre>{@code
     * 서버 sshd_config:
     *   MaxStartups 30:30:200          # 앱 서버가 3대 이상이면 첫 값 > 앱 서버 수 × 4
     *   Subsystem sftp internal-sftp   # 연결마다 sftp-server 프로세스를 따로 띄우지 않음
     *   # 계정 프로세스·파일 수 한도(ulimit -u / -n)가 (앱 서버 수 × sessionMaxTotal)을 감당하는지 확인
     *
     * 클라이언트 (기본값 사용):
     *   new S2SftpFileManagerImpl(host, port, username, privateKeyPath, passphrase, password);
     * }</pre>
     */
    public S2SftpFileManagerImpl(String host, int port, String username, String privateKeyPath, String passphrase,
            String password, Integer sessionMaxTotal, Integer sessionMinIdle, Integer sessionMaxWaitMillis) {
        this(host, port, username, privateKeyPath, passphrase, password, sessionMaxTotal, sessionMinIdle,
                sessionMaxWaitMillis, null, false);
    }

    /**
     * 호스트 키 검증 방식을 지정해 생성한다. 다른 생성자는 {@code ~/.ssh/known_hosts}로 검증한다.
     *
     * @param host                 sftp host
     * @param port                 sftp port
     * @param username             sftp username
     * @param privateKeyPath       sftp private key path
     * @param passphrase           sftp private key passphrase
     * @param password             sftp password
     * @param sessionMaxTotal      세션 풀의 최대 세션 수 (null: 64)
     * @param sessionMinIdle       세션 풀에 유휴 상태로 유지할 최소 세션 수 (null: 8)
     * @param sessionMaxWaitMillis 세션 풀에서 사용 가능한 세션을 기다리는 최대 시간 (null: 30초)
     * @param knownHostsPath       호스트 키를 검증할 known_hosts 파일 경로 (null: {@code ~/.ssh/known_hosts})
     * @param allowUnknownHosts    true 이면 {@code knownHostsPath}가 없을 때 호스트 키를 검증하지 않는다 (중간자 공격에 노출됨)
     * @see JschSessionFactory
     */
    public S2SftpFileManagerImpl(String host, int port, String username, String privateKeyPath, String passphrase,
            String password, Integer sessionMaxTotal, Integer sessionMinIdle, Integer sessionMaxWaitMillis,
            String knownHostsPath, boolean allowUnknownHosts) {
        this(host, port, username, privateKeyPath, passphrase, password, sessionMaxTotal, sessionMinIdle,
                sessionMaxWaitMillis, knownHostsPath, allowUnknownHosts, null);
    }

    /**
     * 호스트 키 검증 방식과 동시 신규 연결 수를 지정해 생성한다.
     *
     * @param host                  sftp host
     * @param port                  sftp port
     * @param username              sftp username
     * @param privateKeyPath        sftp private key path
     * @param passphrase            sftp private key passphrase
     * @param password              sftp password
     * @param sessionMaxTotal       세션 풀의 최대 세션 수 (null: 64)
     * @param sessionMinIdle        세션 풀에 유휴 상태로 유지할 최소 세션 수 (null: 8)
     * @param sessionMaxWaitMillis  세션 풀에서 사용 가능한 세션을 기다리는 최대 시간 (null: 30초)
     * @param knownHostsPath        호스트 키를 검증할 known_hosts 파일 경로 (null: {@code ~/.ssh/known_hosts})
     * @param allowUnknownHosts     true 이면 {@code knownHostsPath}가 없을 때 호스트 키를 검증하지 않는다 (중간자 공격에 노출됨)
     * @param maxConcurrentConnects 동시에 진행할 신규 연결 수 (null: 시스템 프로퍼티 {@code s2.sftp.maxConcurrentConnects},
     *                              없으면 4). 그 SFTP 서버의 {@code MaxStartups} 첫 값 &gt; 앱 서버 수 × 이 값이 되게 둔다
     * @see JschSessionFactory
     */
    public S2SftpFileManagerImpl(String host, int port, String username, String privateKeyPath, String passphrase,
            String password, Integer sessionMaxTotal, Integer sessionMinIdle, Integer sessionMaxWaitMillis,
            String knownHostsPath, boolean allowUnknownHosts, Integer maxConcurrentConnects) {
        // 디렉토리 캐시 TTL 우선순위: 시스템 프로퍼티 > 기본값
        long ttl = DEFAULT_DIR_CACHE_TTL_MS;
        try {
            var prop = System.getProperty("s2.sftp.dirCacheTtlMs");
            if (prop != null) {
                ttl = Long.parseLong(prop);
            }
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("s2.sftp.dirCacheTtlMs system property parse failed, using default {}",
                        DEFAULT_DIR_CACHE_TTL_MS);
            }
        }
        this.dirCacheTtlMillis = Math.max(0L, ttl);

        // 파라미터 유효성 검사 및 경고
        if (sessionMaxTotal != null && sessionMaxTotal <= 0) {
            if (logger.isWarnEnabled()) {
                logger.warn("sessionMaxTotal is <= 0, consider using a positive value. Provided: {}", sessionMaxTotal);
            }
        }
        if (sessionMinIdle != null && sessionMinIdle < 0) {
            if (logger.isWarnEnabled()) {
                logger.warn("sessionMinIdle is negative. Provided: {}", sessionMinIdle);
            }
        }
        if (sessionMaxWaitMillis != null && sessionMaxWaitMillis < 0) {
            if (logger.isWarnEnabled()) {
                logger.warn("sessionMaxWaitMillis is negative. Provided: {}", sessionMaxWaitMillis);
            }
        }

        jschSessionFactory = new JschSessionFactory(host, port, username, privateKeyPath, passphrase, password,
                sessionMaxTotal, sessionMinIdle, sessionMaxWaitMillis, knownHostsPath, allowUnknownHosts,
                maxConcurrentConnects);
    }

    /**
     * 원격 여부
     *
     * @return 원격 여부
     */
    public boolean isRemote() {
        return true;
    }

    /**
     * 지정된 경로에 파일을 업로드
     *
     * @param fileData 업로드할 파일 데이터 (InputStream)
     * @param savePath 원격 서버의 대상 파일 저장 경로
     * @param saveName 원격 서버의 대상 파일 저장 명
     * @param overwrite true 면 같은 이름의 원격 파일을 덮어씀 (false 면 예외)
     * @return 저장된 파일의 크기(바이트 단위)
     * @throws S2RuntimeException 같은 이름의 파일이 있거나(덮어쓰지 않을 때), 경로가 벗어나거나, 업로드에 실패했을 때
     */
    @Override
    public long writeFile(InputStream fileData, String savePath, String saveName, boolean overwrite) {
        var fileSize = -1L;
        var remoteFileFullPath = S2FileUtil.resolveRemoteWithin(savePath, saveName);
        // saveName 에 하위 폴더가 있을 수 있으므로 savePath 가 아니라 대상 파일의 상위 폴더를 만든다
        var remoteDir = parentDirOf(remoteFileFullPath);
        Session session = null;
        ChannelSftp channel = null;
        // 이 호출이 새로 만든(덮어쓰지 않는) 파일에 쓰기 시작했는지. 실패하면 불완전한 파일을 지운다
        var createdNewFile = false;

        // 커스텀 진행률 모니터 인스턴스 생성 (클래스는 static nested로 변경하여 클래스 로딩 비용 절감)
        var monitor = new SizeTrackingMonitor();

        try (InputStream bufferedInput = S2StreamUtil.getBufferedInputStream(fileData)) {
            // 세션/채널은 실제 전송 시간 동안에만 열고, 전송 완료 즉시 finally 블록에서 반환/종료한다.
            // 따라서 일반적인 업로드 흐름에서는 세션이 장시간 홀드되지 않는다.
            session = jschSessionFactory.getSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CHANNEL_CONNECT_TIMEOUT);

            // 디렉토리 존재 여부 확인 및 생성 (TTL 캐시로 네트워크 호출 최소화)
            ensureRemoteDir(channel, remoteDir);

            // 기존 파일 존재 여부 확인
            if (!overwrite && exists(channel, remoteFileFullPath)) {
                throw new S2FileExistsException("Remote file already exists: " + remoteFileFullPath, null,
                        S2KitMessages.key("s2kit.file.exists", saveName));
            }

            // 스트림 직접 전송 및 크기 추적. 마지막 진행(count()) 이후 TRANSFER_IDLE_MILLIS 동안
            // 진행이 없으면(정말로 멈춘 전송) 감시 타이머가 채널을 강제로 끊는다. 느리더라도 계속
            // 진행 중인 정상 업로드는 count()가 계속 호출되어 끊기지 않는다.
            var watchdog = startTransferIdleWatchdog(channel, monitor, TRANSFER_IDLE_MILLIS,
                    TRANSFER_IDLE_CHECK_INTERVAL_MILLIS);
            try {
                createdNewFile = !overwrite;
                channel.put(bufferedInput, remoteFileFullPath, monitor);
                createdNewFile = false; // 전송 완료: 이후 실패해도 파일은 온전하다
            } finally {
                watchdog.cancel(false);
            }
            var transferSize = monitor.getTotalBytesTransferred();

            var attrs = channel.stat(remoteFileFullPath);
            fileSize = attrs.getSize(); // 전송 완료된 실제 파일의 크기

            if (transferSize != fileSize) {
                if (logger.isWarnEnabled()) {
                    if (S2Util.isKorean()) {
                        logger.warn("업로드 전송량과 원격 파일 크기가 다릅니다. 전송: {}, 실제: {}", transferSize, fileSize);
                    } else {
                        logger.warn("Uploaded bytes differ from the remote file size. Sent: {}, actual: {}", transferSize, fileSize);
                    }
                }
            }
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error("Failed to upload file: {}", saveName, e);
            } else {
                logger.error("Failed to upload file: {}", saveName);
            }
            throw new S2RuntimeException("Cannot upload the remote file: " + saveName + " (" + e.getMessage() + ")", e,
                    S2KitMessages.key("s2kit.file.uploadFailed", saveName));
        } finally {
            if (createdNewFile && session != null) {
                // 로컬 구현처럼 불완전한 파일을 남기지 않는다 (남기면 다시 올릴 때 "이미 존재" 로 거부됨)
                removeQuietly(session, channel, remoteFileFullPath);
            }
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null) {
                jschSessionFactory.returnSession(session);
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
     * @apiNote 반환된 InputStream 은 SFTP 세션 풀 리소스를 물고 있으므로 사용 후 반드시 close() 해야 한다
     *          (try-with-resources 권장). 호출 측이 닫지 않더라도 마지막 read() 이후
     *          {@code TRANSFER_IDLE_MILLIS} 동안 추가 읽기가 없으면 자동으로 정리된다.
     */
    public InputStream readFile(String savePath, String saveName) {
        var remoteFileFullPath = S2FileUtil.resolveRemoteWithin(savePath, saveName);
        Session session = null;
        ChannelSftp channel = null;

        try {
            // 세션/채널은 스트림을 반환하기 직전까지 열리며, 호출 측이 스트림을 닫을 때 함께 정리된다.
            // 호출 측이 스트림을 닫지 않는 누수 케이스를 대비해 AutoClosingResourceInputStream이
            // 마지막 read() 이후 TRANSFER_IDLE_MILLIS 동안 idle 상태면 자동으로 정리한다.
            session = jschSessionFactory.getSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CHANNEL_CONNECT_TIMEOUT);

            if (!exists(channel, remoteFileFullPath)) {
                throw new S2RuntimeException("Remote file not found: " + remoteFileFullPath,
                        S2KitMessages.key("s2kit.file.notFound", saveName));
            }

            var rawInputStream = channel.get(remoteFileFullPath);
            var bufferedInputStream = S2StreamUtil.getBufferedInputStream(rawInputStream);

            ChannelSftp finalChannel = channel;
            Session finalSession = session;

            return new AutoClosingResourceInputStream(bufferedInputStream, rawInputStream, finalChannel, finalSession,
                    TRANSFER_IDLE_MILLIS, TRANSFER_IDLE_CHECK_INTERVAL_MILLIS, jschSessionFactory);
        } catch (Exception e) {
            // 예외 발생 시 리소스 정리
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null) {
                jschSessionFactory.returnSession(session);
            }
            if (S2Util.isKorean()) {
                logger.error("원격 파일 다운로드 스트림 열기 실패: {}", saveName, e);
            } else {
                logger.error("Cannot open the remote file download stream: {}", saveName, e);
            }
            throw new S2RuntimeException("Cannot download the remote file: " + saveName + " (" + e.getMessage() + ")", e,
                    S2KitMessages.key("s2kit.file.downloadFailed", saveName));
        }
    }

    /**
     * 지정된 경로의 파일을 삭제합니다.
     *
     * @param savePath 원격 서버의 대상 파일 저장 경로
     * @param saveName 원격 서버의 대상 파일 저장 명
     */
    public void deleteFile(String savePath, String saveName) {
        var remoteFileFullPath = S2FileUtil.resolveRemoteWithin(savePath, saveName);
        Session session = null;
        ChannelSftp channel = null;

        try {
            session = jschSessionFactory.getSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CHANNEL_CONNECT_TIMEOUT);

            if (exists(channel, remoteFileFullPath)) {
                channel.rm(remoteFileFullPath);
            }
        } catch (Exception e) {
            if (S2Util.isKorean()) {
                logger.error("원격 파일 삭제 실패: {}", saveName, e);
            } else {
                logger.error("Cannot delete the remote file: {}", saveName, e);
            }
            throw new S2RuntimeException("Cannot delete the remote file: " + saveName + " (" + e.getMessage() + ")", e,
                    S2KitMessages.key("s2kit.file.deleteFailed", saveName));
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect(); // 채널 종료
            }
            jschSessionFactory.returnSession(session);
        }
    }

    /**
     * 세션 풀을 닫아 유휴 SSH 연결과 풀 정비 스레드를 정리한다. 애플리케이션 종료 시 호출한다(Spring 빈이면 종료 시 자동 호출).
     * 닫은 뒤에는 이 객체를 사용할 수 없다.
     */
    @Override
    public void close() {
        jschSessionFactory.close();
    }

    private boolean exists(ChannelSftp channel, String path) throws Exception {
        try {
            channel.lstat(path);
            return true;
        } catch (SftpException e) {
            // 파일/디렉토리가 실제로 없는 경우에만 false. 그 외(네트워크/권한 등) 오류는 그대로 전파한다.
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw e;
        }
    }

    /**
     * 지정된 경로의 디렉토리가 존재하지 않을 경우 생성합니다. 중간 경로의 디렉토리들도 필요한 경우 함께 생성됩니다.
     *
     * @param channel SFTP 채널 객체
     * @param path    생성할 디렉토리 경로
     */
    private void createDirectoryIfNotExists(ChannelSftp channel, String path) throws Exception {
        var folders = path.split("/");
        var fullPath = new StringBuilder();
        // 상대 경로는 접속 계정의 홈 기준이므로 앞에 "/" 를 붙이지 않는다 (붙이면 파일과 다른 곳에 폴더가 생김)
        var absolute = path.startsWith("/");

        for (var folder : folders) {
            if (folder.isBlank())
                continue;

            if (absolute || fullPath.length() > 0) {
                fullPath.append("/");
            }
            fullPath.append(folder);
            // lstat 으로 존재 여부만 확인한다. cd()는 채널의 현재 작업 디렉토리를 바꾸는 부작용이 있어
            // 이후 상대경로 기반의 exists()/put() 호출이 엉뚱한 위치를 가리키게 될 수 있으므로 사용하지 않는다.
            if (!exists(channel, fullPath.toString())) {
                try {
                    channel.mkdir(fullPath.toString());
                } catch (SftpException e) {
                    // 동시에 같은 폴더로 올리는 다른 요청이 먼저 만들었을 수 있다 (확인과 생성 사이의 경쟁). 생겼으면 성공으로 본다
                    if (!exists(channel, fullPath.toString())) {
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * 대상 파일의 폴더가 없으면 만든다 (TTL 캐시로 네트워크 호출 최소화).
     */
    private void ensureRemoteDir(ChannelSftp channel, String remoteDir) {
        if (remoteDir == null || isDirCached(remoteDir)) {
            return;
        }
        try {
            createDirectoryIfNotExists(channel, remoteDir);
            putDirCache(remoteDir);
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                if (S2Util.isKorean()) {
                    logger.error("원격 디렉토리 생성 실패: {}", remoteDir, e);
                } else {
                    logger.error("Cannot create the remote directory: {}", remoteDir, e);
                }
            } else {
                if (S2Util.isKorean()) {
                    logger.error("원격 디렉토리 생성 실패: {}", remoteDir);
                } else {
                    logger.error("Cannot create the remote directory: {}", remoteDir);
                }
            }
            throw new S2RuntimeException("Cannot create the remote directory: " + remoteDir);
        }
    }

    /**
     * 이어 쓰기: {@code .<이름>.<resumeKey>.s2part}에 쓰고(남아 있으면 그 크기만큼 건너뛰고 이어 씀), 다 받으면 대상 이름으로 바꾼다.
     * 전송이 실패하면 부분 파일을 남겨 다음 시도가 이어 쓰게 한다.
     */
    @Override
    public long writeFile(java.nio.file.Path source, String savePath, String saveName, boolean overwrite,
            String resumeKey) {
        if (resumeKey == null) {
            return FileManager.super.writeFile(source, savePath, saveName, overwrite, null);
        }
        var target = S2FileUtil.resolveRemoteWithin(savePath, saveName);
        var part = partPathOf(target, resumeKey);
        Session session = null;
        ChannelSftp channel = null;
        var monitor = new SizeTrackingMonitor();
        try {
            var sourceSize = java.nio.file.Files.size(source);
            session = jschSessionFactory.getSession();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CHANNEL_CONNECT_TIMEOUT);
            ensureRemoteDir(channel, parentDirOf(target));

            if (!overwrite && exists(channel, target)) {
                throw new S2FileExistsException("Remote file already exists: " + target, null,
                        S2KitMessages.key("s2kit.file.exists", saveName));
            }
            var resume = exists(channel, part);
            if (resume && channel.lstat(part).getSize() > sourceSize) {
                // 다른 내용의 부분 파일: 처음부터 다시 쓴다
                channel.rm(part);
                resume = false;
            }
            if (resume) {
                if (S2Util.isKorean()) {
                    logger.info("업로드를 이어서 보냅니다: {} ({} / {} bytes)", target, channel.lstat(part).getSize(), sourceSize);
                } else {
                    logger.info("Resuming upload: {} ({} / {} bytes)", target, channel.lstat(part).getSize(), sourceSize);
                }
            }

            var watchdog = startTransferIdleWatchdog(channel, monitor, TRANSFER_IDLE_MILLIS,
                    TRANSFER_IDLE_CHECK_INTERVAL_MILLIS);
            // RESUME: 서버의 부분 파일 크기만큼 원본을 건너뛰고 그 뒤부터 덧붙인다
            try (var in = java.nio.file.Files.newInputStream(source)) {
                channel.put(in, part, monitor, resume ? ChannelSftp.RESUME : ChannelSftp.OVERWRITE);
            } finally {
                watchdog.cancel(false);
            }

            var size = channel.lstat(part).getSize();
            if (size != sourceSize) {
                channel.rm(part); // 어긋난 부분 파일은 이어 쓰면 안 되므로 지운다
                throw new S2RuntimeException("Uploaded size " + size + " does not match the file size " + sourceSize);
            }
            if (!overwrite && exists(channel, target)) {
                channel.rm(part);
                throw new S2FileExistsException("Remote file already exists: " + target, null,
                        S2KitMessages.key("s2kit.file.exists", saveName));
            }
            try {
                channel.rename(part, target); // posix-rename 을 지원하면 대상을 원자적으로 바꾼다
            } catch (SftpException e) {
                if (!overwrite || !exists(channel, target)) {
                    throw e;
                }
                // posix-rename 이 없는 서버는 대상이 있으면 이름을 바꾸지 못한다: 지우고 다시 바꾼다
                channel.rm(target);
                channel.rename(part, target);
            }
            return size;
        } catch (S2FileExistsException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to upload file (resumable): {}", saveName, e);
            throw new S2RuntimeException("Cannot upload the remote file: " + saveName + " (" + e.getMessage() + ")", e,
                    S2KitMessages.key("s2kit.file.uploadFailed", saveName));
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null) {
                jschSessionFactory.returnSession(session);
            }
        }
    }

    @Override
    public void discardPartial(String savePath, String saveName, String resumeKey) {
        if (resumeKey == null) {
            return;
        }
        var part = partPathOf(S2FileUtil.resolveRemoteWithin(savePath, saveName), resumeKey);
        Session session = null;
        try {
            session = jschSessionFactory.getSession();
            removeQuietly(session, null, part);
        } catch (Exception e) {
            if (S2Util.isKorean()) {
                logger.warn("부분 파일을 지우지 못했습니다: {} ({})", part, e.toString());
            } else {
                logger.warn("Cannot delete the partial file: {} ({})", part, e.toString());
            }
        } finally {
            jschSessionFactory.returnSession(session);
        }
    }

    /** 대상 옆의 숨김 부분 파일 경로: {@code <폴더>/.<이름>.<resumeKey>.s2part} */
    private static String partPathOf(String target, String resumeKey) {
        if (!resumeKey.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("resumeKey must be 1-64 letters, digits, '-' or '_': " + resumeKey);
        }
        var index = target.lastIndexOf('/');
        return target.substring(0, index + 1) + "." + target.substring(index + 1) + "." + resumeKey + ".s2part";
    }

    /**
     * 정규화된 원격 파일 경로의 상위 폴더를 반환한다 (상위 폴더가 홈이면 null).
     * @param remoteFilePath {@link S2FileUtil#resolveRemoteWithin}이 돌려준 경로
     * @return 상위 폴더 경로, 없으면 null
     */
    private static String parentDirOf(String remoteFilePath) {
        var index = remoteFilePath.lastIndexOf('/');
        if (index < 0) {
            return null;
        }
        return index == 0 ? "/" : remoteFilePath.substring(0, index);
    }

    /**
     * 실패한 업로드가 남긴 파일을 지운다. 감시 타이머가 채널을 끊었을 수 있으므로 그때는 새 채널을 연다. 실패는 기록만 한다.
     */
    private static void removeQuietly(Session session, ChannelSftp channel, String path) {
        ChannelSftp cleanup = null;
        try {
            if (channel != null && channel.isConnected()) {
                channel.rm(path);
            } else if (session.isConnected()) {
                cleanup = (ChannelSftp) session.openChannel("sftp");
                cleanup.connect(CHANNEL_CONNECT_TIMEOUT);
                cleanup.rm(path);
            }
        } catch (Exception e) {
            if (!(e instanceof SftpException se && se.id == ChannelSftp.SSH_FX_NO_SUCH_FILE)) {
                if (S2Util.isKorean()) {
                    logger.warn("실패한 업로드의 원격 파일을 지우지 못했습니다: {} ({})", path, e.getMessage());
                } else {
                    logger.warn("Cannot delete the remote file of a failed upload: {} ({})", path, e.getMessage());
                }
            }
        } finally {
            if (cleanup != null && cleanup.isConnected()) {
                cleanup.disconnect();
            }
        }
    }

    // SizeTrackingMonitor를 static nested 클래스로 이동(로딩 비용 절감)
    private static class SizeTrackingMonitor implements SftpProgressMonitor {
        private long totalBytesTransferred = 0L;
        // count()가 실제로 바이트를 전송할 때마다 갱신된다 (전송 idle 감시용 - 아래 startTransferIdleWatchdog 참고)
        private volatile long lastProgressAtMillis = System.currentTimeMillis();

        @Override
        public void init(int op, String src, String dest, long max) {
            lastProgressAtMillis = System.currentTimeMillis();
        }

        @Override
        public boolean count(long bytesTransferred) {
            totalBytesTransferred += bytesTransferred;
            lastProgressAtMillis = System.currentTimeMillis();
            return true;
        }

        @Override
        public void end() {
        }

        long getTotalBytesTransferred() {
            return totalBytesTransferred;
        }

        long getLastProgressAtMillis() {
            return lastProgressAtMillis;
        }
    }

    /**
     * 전송(put/get) 중 일정 시간 이상 실제 진행(count() 호출)이 없으면 채널을 강제로 끊어
     * 블로킹 중인 전송 호출을 깨우는 감시 타이머를 시작한다.
     * (count()는 바이트가 실제로 오갈 때만 호출되므로, 완전히 멈춘 전송은 count() 내부에서는
     * 감지할 수 없어 별도 스레드로 감시해야 한다.)
     *
     * @param channel             감시 대상 채널
     * @param monitor             진행 시각을 추적하는 모니터
     * @param idleTimeoutMillis   마지막 진행 이후 이 시간 동안 진행이 없으면 강제 종료
     * @param checkIntervalMillis idle 여부 점검 주기
     * @return 전송 완료 후 반드시 cancel() 해야 하는 점검 작업
     */
    private static ScheduledFuture<?> startTransferIdleWatchdog(ChannelSftp channel, SizeTrackingMonitor monitor,
            long idleTimeoutMillis, long checkIntervalMillis) {
        return scheduleCheck(() -> {
            if (System.currentTimeMillis() - monitor.getLastProgressAtMillis() >= idleTimeoutMillis
                    && channel.isConnected()) {
                runCleanup(() -> {
                    try {
                        channel.disconnect(); // 블로킹 중인 put()/get() 을 예외로 깨운다
                    } catch (Exception ignored) {
                    }
                });
            }
        }, checkIntervalMillis);
    }

    /*
     * 다운로드 스트림이 호출 측에서 닫히지 않아도 일정 시간 idle 상태가 지속되면 자동으로 닫히도록 하는 래퍼
     * - 마지막 read() 이후 경과 시간(idle time) 기준이므로, 계속 읽고 있는 정상적인 대용량/저속
     *   다운로드는 끊기지 않고 진짜로 방치된 스트림만 정리 대상이 된다.
     * - 세션/채널 누수 방지 목적
     * - 점검은 공용 스케줄러(S2TransferWatchdog)가 한다
     */
    private static class AutoClosingResourceInputStream extends S2ResourceInputStream {
        private final ChannelSftp channel;
        private final Session session;
        private final JschSessionFactory sessionFactory;
        private final ScheduledFuture<?> task;
        @SuppressWarnings("unused")
        private final long idleTimeoutMillis;
        private volatile long lastActivityAtMillis;
        private final AtomicBoolean closing = new AtomicBoolean();

        AutoClosingResourceInputStream(InputStream buffered, InputStream raw, ChannelSftp channel, Session session,
                long idleTimeoutMillis, long idleCheckIntervalMillis, JschSessionFactory sessionFactory) {
            super(buffered, raw);
            this.channel = channel;
            this.session = session;
            this.sessionFactory = sessionFactory;
            this.idleTimeoutMillis = idleTimeoutMillis;
            this.lastActivityAtMillis = System.currentTimeMillis();
            this.task = scheduleCheck(() -> {
                if (!closing.get() && System.currentTimeMillis() - lastActivityAtMillis >= idleTimeoutMillis) {
                    runCleanup(this::close);
                }
            }, idleCheckIntervalMillis);
        }

        private void markActivity() {
            this.lastActivityAtMillis = System.currentTimeMillis();
        }

        @Override
        public int read() throws IOException {
            var result = super.read();
            markActivity();
            return result;
        }

        @Override
        public int read(byte[] b) throws IOException {
            var result = super.read(b);
            markActivity();
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            var result = super.read(b, off, len);
            markActivity();
            return result;
        }

        @Override
        public long skip(long n) throws IOException {
            var result = super.skip(n);
            markActivity();
            return result;
        }

        @Override
        public void close() {
            // 감시 타이머와 호출 측이 동시에 닫을 수 있다. isClosed() 확인만으로는 둘 다 통과해 세션을 두 번 반납할 수 있고,
            // 그사이 다른 스레드가 빌린 세션이 풀에 다시 들어가므로 한 번만 정리되도록 원자적으로 막는다.
            if (closing.compareAndSet(false, true)) {
                try {
                    super.close();
                } finally {
                    try {
                        if (task != null)
                            task.cancel(false);
                    } catch (Exception ignored) {
                    }
                    try {
                        if (channel != null && channel.isConnected()) {
                            channel.disconnect();
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        if (sessionFactory != null && session != null) {
                            sessionFactory.returnSession(session);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    // dir cache 유틸리티
    private boolean isDirCached(String path) {
        if (path == null || path.isBlank())
            return false;
        var ts = createdDirs.get(path);
        if (ts == null)
            return false;
        if (dirCacheTtlMillis == 0L)
            return true; // 무제한
        if (System.currentTimeMillis() - ts > dirCacheTtlMillis) {
            createdDirs.remove(path, ts);
            return false;
        }
        return true;
    }

    private void putDirCache(String path) {
        if (path == null || path.isBlank())
            return;
        createdDirs.put(path, System.currentTimeMillis());
        // 주기적 정리: 256번째 업데이트마다 만료 항목을 스윕
        var count = cacheUpdateCounter.incrementAndGet();
        if ((count & 0xFF) == 0) {
            var now = System.currentTimeMillis();
            var ttl = dirCacheTtlMillis;
            if (ttl > 0) {
                createdDirs.entrySet().removeIf(e -> now - e.getValue() > ttl);
            }
        }
    }

}
