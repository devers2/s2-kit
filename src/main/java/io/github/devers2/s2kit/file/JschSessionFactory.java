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
package io.github.devers2.s2kit.file;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import io.github.devers2.s2util.core.S2ThreadUtil;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2020. 07. 08.
 */
public class JschSessionFactory {

    private static final S2Logger logger = S2LogManager.getLogger(JschSessionFactory.class);

    /*
     * 세션 풀 유지/정비 관련 상수
     * - EVICTION_RUN_INTERVAL_MILLIS: 유휴 객체 정비 주기 (밀리초)
     * - IDLE_EVICT_MILLIS: 사용하지 않은 유휴 세션의 최대 보관 시간 (밀리초)
     * - ABANDONED_TIMEOUT_SECONDS: 반납되지 않은(대여 상태로 방치된) 세션 강제 회수 시간 (초)
     *
     * ABANDONED_TIMEOUT_SECONDS 는 "빌린 시점 이후 경과 시간" 기준이라(commons-pool2 가
     * TrackedUse 를 구현하지 않는 Session 객체의 실제 활동 여부는 알 수 없음), 정상적으로 오래
     * 걸리는 작업까지 방치로 오판할 수 있다. 그래서 S2SftpFileManagerImpl 쪽에 "마지막 활동
     * (read()/count() 콜백) 이후 idle 시간" 기준의 정밀한 워치독을 별도로 두어 업/다운로드
     * 전송 구간을 우선 감시하고, 이 값은 그 워치독이 커버하지 못하는 범위(전송 전후의 다른
     * 블로킹 SFTP 호출이 멈추는 등, 극히 드문 경우)를 위한 최후의 안전망으로만 아주 길게 잡는다.
     */
    private static final long EVICTION_RUN_INTERVAL_MILLIS = 60_000L; // 1분
    private static final long IDLE_EVICT_MILLIS = 30L * 60_000L; // 30분
    private static final int ABANDONED_TIMEOUT_SECONDS = 2 * 60 * 60; // 2시간 (최후의 안전망)

    /*
     * ── 기본값 기준: 일반 운영 서버 ──
     *   SFTP 서버 4 vCPU / 8GB RAM / 1Gbps, 앱 서버 2대, OpenSSH 기본 설정(MaxStartups 10:30:100) 그대로.
     *   이 기준에서 서버 설정을 바꾸지 않아도 연결이 끊기거나 서버가 무리하지 않도록 잡았다.
     *   - 최대 세션 64: 상한일 뿐이고 연결은 필요할 때만 열며 30분 쓰지 않으면 닫는다. 작은 파일이면 초당 200건 안팎을 처리한다.
     *     앱 2대 × 64 = 연결 128개 — sshd 메모리보다 디스크·대역폭과 계정 프로세스 한도(ulimit)가 먼저 한계가 된다.
     *   - 최소 유지 8: 배포 직후 첫 요청이 연결 맺기를 기다리지 않을 만큼만 미리 연다.
     *   - 동시 신규 연결 4: 앱 2대 × 4 = 8 < MaxStartups 첫 값 10 → 서버가 인증 전 연결을 무작위로 끊지 않는다.
     *
     * ── 상황별 조절 (자세한 것은 SFTP-GUIDE.ko.md) ──
     *   - 대기 실패("최대 재시도 횟수 초과")가 잦고 서버 CPU·디스크·대역폭에 여유가 있다 → sessionMaxTotal 을 올린다 (64 → 128).
     *   - 큰 파일·일괄 업로드로 대기 실패가 난다 → 풀을 키우지 말고 S2UploadQueue 로 동시 전송 수를 묶는다 (대역폭이 한계라 풀을
     *     키워도 한 건씩 더 느려질 뿐이다).
     *   - 배포 직후 첫 요청이 느리다 → sessionMinIdle 을 올린다 (평소 동시 사용량 정도).
     *   - 앱 서버가 3대 이상이다 → 서버 MaxStartups 첫 값 > (앱 서버 수 × 동시 신규 연결)로 올리거나 -Ds2.sftp.maxConcurrentConnects 를 낮춘다.
     *   - SFTP 서버가 작거나(2 vCPU / 4GB) 다른 서비스와 함께 쓴다 → sessionMaxTotal 32, sessionMinIdle 4 로 낮춘다.
     *   - 서버가 멀거나 느려 연결이 자주 시간 초과된다 → sessionMaxWaitMillis 를 늘린다 (연결 제한 시간도 이 값을 쓴다).
     */
    /* 최대 세션 수 기본값 */
    private final int DEFAULT_SESSION_MAX_TOTAL = 64;
    /* 최소 유지 세션 수 기본값 */
    private final int DEFAULT_SESSION_MIN_IDLE = 8;
    /* 세션 획득 대기 시간 기본값 (30초) */
    private final int DEFAULT_SESSION_MAX_WAIT_MILLIS = 30_000;

    private final String host;
    private final int port;
    private final String username;
    /* privateKey 파일 경로 */
    private final String privateKeyPath;
    /* privateKey 비밀번호 (있는 경우) */
    private final String passphrase;
    /* 비밀번호 (privateKeyPath 가 없는 경우 사용) */
    private final String password;
    /* known_hosts 파일 경로 (null: ~/.ssh/known_hosts) */
    private final String knownHostsPath;
    /* 호스트 키 검증 생략 여부 (true: 중간자 공격에 노출됨) */
    private final boolean allowUnknownHosts;

    // getSession()/returnSession() 등 동기화 없이 읽는 스레드에 forceResetPool()의 재할당이
    // 즉시 보이도록 volatile 로 선언한다 (JMM 가시성 보장 없이는 스레드가 이미 close() 된
    // 이전 풀을 임의의 시간 동안 계속 바라볼 수 있음).
    private volatile GenericObjectPool<Session> sessionPool;

    // forceResetPool() 에서 재사용하기 위해 실제 적용된 풀 크기를 보관한다.
    // (하드코딩된 DEFAULT_* 를 다시 쓰면 사용자가 지정한 커스텀 크기가 재초기화 때마다 유실된다)
    private final int sessionMaxTotal;
    private final int sessionMinIdle;

    // 짧은 시간 내 중복 강제 초기화(thundering herd) 방지: 여러 스레드가 동시에 풀 고갈을
    // 감지해 forceResetPool()을 호출해도, synchronized 로 직렬화된 후속 호출은 직전 호출이
    // 방금 새로 만든 풀을 불필요하게 다시 파괴하지 않도록 최소 간격 내에서는 건너뛴다.
    private static final long MIN_RESET_INTERVAL_MILLIS = 5_000L;
    private volatile long lastPoolResetAtMillis = 0L;

    /* 세션 풀에서 사용 가능한 세션을 기다리는 최대 시간 */
    private final Integer sessionMaxWaitMillis;

    private static final int MAX_RETRY_COUNT = 3;

    /* 서버 연결 유지 확인 간격 (밀리초) */
    private static final int SERVER_ALIVE_INTERVAL_MILLIS = 60_000;
    /* 연결 유지 확인 실패 허용 횟수 */
    private static final int SERVER_ALIVE_COUNT_MAX = 3;

    /*
     * 동시에 진행하는 신규 SSH 연결(핸드셰이크·인증) 수 상한 기본값. 시스템 프로퍼티로 바꿀 수 있다: -Ds2.sftp.maxConcurrentConnects=4
     * - 대량 요청이 몰리거나 배포 직후 풀이 비어 있으면 수십 개의 연결을 한꺼번에 열게 되는데, OpenSSH 는 인증 전 연결이
     *   MaxStartups(기본 10:30:100)의 첫 값(10)을 넘으면 무작위로 끊는다. 앱 한 대가 그 값보다 적게 열도록 줄 세운다.
     * - 같은 SFTP 서버를 여러 앱 서버가 함께 쓰면 (앱 서버 수 × 이 값)이 MaxStartups 첫 값보다 작도록 낮추거나 서버 값을 올린다.
     */
    private static final int DEFAULT_MAX_CONCURRENT_CONNECTS = 4;
    private final Semaphore connectPermits;
    private final int maxConcurrentConnects;

    /* close() 이후에는 풀을 다시 만들지 않고 즉시 실패한다 */
    private volatile boolean closed = false;

    /**
     * 기본 풀 크기로 생성한다. 호스트 키는 {@code ~/.ssh/known_hosts}로 검증한다.
     *
     * @param host           sftp host
     * @param port           sftp port
     * @param username       sftp username
     * @param privateKeyPath sftp private key path
     * @param passphrase     sftp private key passphrase
     * @param password       sftp password
     */
    public JschSessionFactory(String host, int port, String username, String privateKeyPath, String passphrase,
            String password) {
        this(host, port, username, privateKeyPath, passphrase, password, null, null, null, null, false);
    }

    /**
     * 호스트 키는 {@code ~/.ssh/known_hosts}로 검증한다.
     *
     * @param host                 sftp host
     * @param port                 sftp port
     * @param username             sftp username
     * @param privateKeyPath       sftp private key path
     * @param passphrase           sftp private key passphrase
     * @param password             sftp password
     * @param sessionMaxTotal      세션 풀의 최대 세션 수 (기본값 64)
     * @param sessionMinIdle       세션 풀에 유휴 상태로 유지할 최소 세션 수 (기본값 8)
     * @param sessionMaxWaitMillis 세션 풀에서 사용 가능한 세션을 기다리는 최대 시간
     */
    public JschSessionFactory(String host, int port, String username, String privateKeyPath,
            String passphrase, String password, Integer sessionMaxTotal, Integer sessionMinIdle,
            Integer sessionMaxWaitMillis) {
        this(host, port, username, privateKeyPath, passphrase, password, sessionMaxTotal, sessionMinIdle,
                sessionMaxWaitMillis, null, false);
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
     * @param knownHostsPath       호스트 키를 검증할 known_hosts 파일 경로 (null: {@code ~/.ssh/known_hosts}).
     *                             예: {@code ssh-keyscan -p 22 sftp.example.com >> /etc/ssh/known_hosts_sftp}
     * @param allowUnknownHosts    true 이면 {@code knownHostsPath}가 없을 때 호스트 키를 검증하지 않는다. 중간자 공격(MITM)에
     *                             노출되므로 신뢰할 수 있는 사설망에서만 명시적으로 켠다. {@code knownHostsPath}가 있으면 무시된다.
     * @apiNote 호스트 키 검증은 기본적으로 켜져 있다(fail-closed). known_hosts 에 서버 키가 없으면 연결이 거부된다.
     *          <p>
     *          신규 연결은 동시에 최대 4개만 진행한다(-Ds2.sftp.maxConcurrentConnects). 상황별 조절은 SFTP-GUIDE.ko.md, 서버 sshd_config 권장값은
     *          {@link io.github.devers2.s2kit.file.impl.S2SftpFileManagerImpl#S2SftpFileManagerImpl(String, int, String, String, String, String, Integer, Integer, Integer)}
     *          참고.
     */
    public JschSessionFactory(String host, int port, String username, String privateKeyPath,
            String passphrase, String password, Integer sessionMaxTotal, Integer sessionMinIdle,
            Integer sessionMaxWaitMillis, String knownHostsPath, boolean allowUnknownHosts) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.privateKeyPath = privateKeyPath;
        this.passphrase = passphrase;
        this.password = password;
        this.knownHostsPath = knownHostsPath;
        this.allowUnknownHosts = allowUnknownHosts;
        if (allowUnknownHosts && (knownHostsPath == null || knownHostsPath.isBlank())) {
            logger.warn("SFTP 호스트 키 검증이 꺼져 있습니다(allowUnknownHosts=true). 중간자 공격에 노출될 수 있습니다: {}:{}", host, port);
        }

        this.sessionMaxTotal = sessionMaxTotal != null ? sessionMaxTotal : DEFAULT_SESSION_MAX_TOTAL;
        this.sessionMinIdle = sessionMinIdle != null ? sessionMinIdle : DEFAULT_SESSION_MIN_IDLE;
        sessionPool = createSessionPool(this.sessionMaxTotal, this.sessionMinIdle);

        this.sessionMaxWaitMillis = sessionMaxWaitMillis != null ? sessionMaxWaitMillis
                : DEFAULT_SESSION_MAX_WAIT_MILLIS;

        var connects = DEFAULT_MAX_CONCURRENT_CONNECTS;
        var prop = System.getProperty("s2.sftp.maxConcurrentConnects");
        if (prop != null) {
            try {
                connects = Math.max(1, Integer.parseInt(prop.trim()));
            } catch (NumberFormatException e) {
                logger.warn("s2.sftp.maxConcurrentConnects 값을 읽지 못해 기본값 {} 을 씁니다: {}", DEFAULT_MAX_CONCURRENT_CONNECTS, prop);
            }
        }
        this.maxConcurrentConnects = connects;
        this.connectPermits = new Semaphore(connects, true);

        prewarm(sessionPool);
    }

    /**
     * 최소 유휴 세션(sessionMinIdle)만큼 미리 연결해 둔다. 그대로 두면 풀 정비가 처음 돌 때(약 1분 뒤)에야 채워져, 배포 직후 몰린
     * 요청이 연결 맺기를 기다린다. 백그라운드에서 하므로 생성자는 기다리지 않고, SFTP 서버가 꺼져 있어도 앱 기동이 실패하지 않는다
     * (실패는 기록만 하고, 이후 요청 때 다시 연결한다).
     */
    private void prewarm(GenericObjectPool<Session> pool) {
        if (sessionMinIdle <= 0) {
            return;
        }
        Runnable task = () -> {
            try {
                pool.preparePool();
                logger.info("SFTP 세션을 미리 열었습니다: {}:{} [{}]", host, port, getPoolStatus());
            } catch (Exception e) {
                if (!closed) {
                    logger.warn("SFTP 세션을 미리 열지 못했습니다 (요청 때 다시 연결함): {}:{} - {}", host, port, e.toString());
                }
            }
        };
        try {
            S2ThreadUtil.getCommonExecutor().execute(task);
        } catch (RejectedExecutionException e) {
            logger.debug("미리 열기를 건너뜁니다 (실행기가 받지 않음): {}", e.getMessage());
        }
    }

    /**
     * 동시 신규 연결 수 상한 안에서 연결한다. 차례를 sessionMaxWaitMillis 안에 얻지 못하면 실패한다.
     */
    private void connectLimited(Session session) throws Exception {
        if (!connectPermits.tryAcquire(sessionMaxWaitMillis, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Too many SFTP connections are opening at once (limit " + maxConcurrentConnects
                    + "); no turn within " + sessionMaxWaitMillis + "ms");
        }
        try {
            session.connect(sessionMaxWaitMillis); // SFTP 연결 타임아웃
        } finally {
            connectPermits.release();
        }
    }

    /**
     * 세션 풀을 생성하고 공통 설정(검증/정비/방치 세션 회수 정책)을 적용한다.
     * 두 생성자와 forceResetPool() 이 동일한 설정을 공유한다.
     */
    private GenericObjectPool<Session> createSessionPool(int maxTotal, int minIdle) {
        var pool = new GenericObjectPool<>(new SessionFactory());
        pool.setMaxTotal(maxTotal);
        pool.setMinIdle(minIdle);
        pool.setMaxIdle(maxTotal);

        // 세션 풀 검증/정비 설정
        pool.setTestOnBorrow(true); // 대여 시 검증
        pool.setTestOnReturn(true); // 반환 시 검증
        pool.setTestWhileIdle(true); // 유휴 상태일 때 검증
        pool.setDurationBetweenEvictionRuns(Duration.ofMillis(EVICTION_RUN_INTERVAL_MILLIS)); // 주기적 유휴 검사
        pool.setMinEvictableIdleDuration(Duration.ofMillis(IDLE_EVICT_MILLIS)); // 오래된 유휴 세션 제거
        pool.setBlockWhenExhausted(true); // 풀이 가득 찼을 때 블록킹
        pool.setTestOnCreate(true); // 객체 생성 시 검증

        // 반납되지 않은(대여 중 방치) 세션 회수 설정
        var abandonedConfig = new AbandonedConfig();
        abandonedConfig.setRemoveAbandonedOnMaintenance(true);
        abandonedConfig.setRemoveAbandonedTimeout(Duration.ofSeconds(ABANDONED_TIMEOUT_SECONDS));
        // 빌리는 시점에도 방치(대여 상태) 세션 회수 활성화
        // - ABANDONED_TIMEOUT_SECONDS 초과한 세션만 회수 대상
        // - 장시간 홀드 중인 작업은 강제 종료되어 I/O 실패가 발생할 수 있으므로 운영 모니터링 권장
        abandonedConfig.setRemoveAbandonedOnBorrow(true);
        pool.setAbandonedConfig(abandonedConfig);

        return pool;
    }

    private class SessionFactory implements PooledObjectFactory<Session> {
        @Override
        public boolean validateObject(PooledObject<Session> p) {
            var session = p.getObject();
            if (!session.isConnected()) {
                return false;
            }
            try {
                // 실제 연결 상태 확인을 위한 더 강력한 검증
                session.sendKeepAliveMsg();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void activateObject(PooledObject<Session> p) throws Exception {
            var session = p.getObject();
            if (!session.isConnected()) {
                connectLimited(session);
            }
        }

        @Override
        public void destroyObject(PooledObject<Session> p) throws Exception {
            var session = p.getObject();
            if (session.isConnected()) {
                session.disconnect();
            }
        }

        @Override
        public PooledObject<Session> makeObject() throws Exception {
            var jsch = new JSch();
            var session = jsch.getSession(username, host, port);
            // ConnectTimeout·ServerAlive* 는 setConfig 로 넘기면 무시된다(JSch 는 OpenSSH 설정 파일에서만 읽음).
            // 연결 제한 시간은 connect(timeout) 인자로, 연결 유지 확인은 전용 setter 로 지정한다.
            session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MILLIS); // 서버 연결 유지 확인 간격 (60초)
            session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX); // 연결 유지 실패 허용 횟수

            // Secrets go to JSch as bytes (the String overloads are deprecated) and are wiped after use, as JSch
            // itself did | 비밀값은 바이트로 넘기고(String 오버로드는 deprecated) 쓴 뒤 지운다 (JSch 가 내부에서 하던 방식과 동일)
            if (privateKeyPath != null && !privateKeyPath.isBlank()) {
                // 개인키 설정
                var secret = passphrase != null ? passphrase.getBytes(StandardCharsets.UTF_8) : null;
                try {
                    jsch.addIdentity(privateKeyPath, secret);
                } finally {
                    if (secret != null) {
                        Arrays.fill(secret, (byte) 0);
                    }
                }
            } else if (password != null) {
                // privateKeyPath 가 없는 경우 password 사용 (Session 이 사본을 보관함)
                var secret = password.getBytes(StandardCharsets.UTF_8);
                session.setPassword(secret);
                Arrays.fill(secret, (byte) 0);
            }

            configureHostKeyChecking(jsch, session);
            connectLimited(session);
            return new DefaultPooledObject<>(session);
        }

        /**
         * Verifies the server's host key against known_hosts unless explicitly disabled (fail-closed) | 명시적으로 끄지 않는 한 known_hosts 로 서버 호스트 키 검증
         */
        private void configureHostKeyChecking(JSch jsch, Session session) throws Exception {
            if (knownHostsPath != null && !knownHostsPath.isBlank()) {
                jsch.setKnownHosts(knownHostsPath);
                session.setConfig("StrictHostKeyChecking", "yes");
            } else if (allowUnknownHosts) {
                session.setConfig("StrictHostKeyChecking", "no");
            } else {
                var defaultKnownHosts = Paths.get(System.getProperty("user.home"), ".ssh", "known_hosts");
                if (Files.isReadable(defaultKnownHosts)) {
                    jsch.setKnownHosts(defaultKnownHosts.toString());
                } else {
                    logger.warn("known_hosts 파일이 없어 SFTP 호스트 키를 검증할 수 없으므로 연결이 거부됩니다: {}. "
                            + "knownHostsPath 를 지정하거나, 신뢰할 수 있는 내부망이라면 allowUnknownHosts=true 로 설정하세요.",
                            defaultKnownHosts);
                }
                session.setConfig("StrictHostKeyChecking", "yes");
            }
        }

        @Override
        public void passivateObject(PooledObject<Session> p) throws Exception {
            // 필요한 경우 세션 비활성화 로직 추가
        }
    }

    /**
     * 풀에서 세션을 빌린다. 실패하면 잠시 기다렸다가 최대 {@code MAX_RETRY_COUNT}번 시도한다.
     * @return 연결된 세션 (사용 후 {@link #returnSession(Session)}으로 반납)
     * @throws IllegalStateException {@link #close()} 이후에 호출했을 때
     * @throws InterruptedException  기다리는 중 인터럽트되었을 때 (인터럽트 상태는 유지됨)
     * @throws Exception             재시도해도 세션을 얻지 못했을 때
     */
    public Session getSession() throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRY_COUNT) {
            if (closed) {
                throw new IllegalStateException("SFTP session factory is closed");
            }
            var pool = sessionPool;
            try {
                // 풀이 가득 차면 blockWhenExhausted + sessionMaxWaitMillis 대기가 정상적인 backpressure 역할을 한다.
                var session = pool.borrowObject(Duration.ofMillis(sessionMaxWaitMillis));
                if (session != null) {
                    if (!session.isConnected()) {
                        try {
                            connectLimited(session);
                        } catch (Exception e) {
                            invalidateSession(session);
                            throw e;
                        }
                    }
                    return session;
                }
            } catch (InterruptedException e) {
                // 인터럽트는 재시도하지 않는다 (종료 중인 실행기 등이 알 수 있도록 상태를 되살려 전파)
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                lastException = e;
                if (closed) {
                    throw new IllegalStateException("SFTP session factory is closed", e);
                }
                if (retryCount + 1 < MAX_RETRY_COUNT) {
                    logger.warn("세션 획득 실패 (시도 {}/{}) - {} [{}]", retryCount + 1, MAX_RETRY_COUNT, e.toString(),
                            getPoolStatus());
                } else {
                    logger.error("세션 획득 실패 (시도 {}/{}) [{}]", retryCount + 1, MAX_RETRY_COUNT, getPoolStatus(), e);
                }

                // 모든 세션이 사용 중이라 기다리다 시간이 다 된 것은 정상적인 과부하이므로 풀을 다시 만들지 않는다. 다시 만들면
                // 옛 풀의 세션이 살아 있는 채로 새 풀이 또 maxTotal 개까지 열어, 서버를 더 압박하고 연결 수가 상한을 넘는다.
                // 사용 중인 세션이 상한보다 적은데도 빌리지 못했을 때만 풀 고장으로 보고 재초기화한다 (디바운스 적용).
                if (e instanceof NoSuchElementException && pool == sessionPool
                        && pool.getNumActive() < pool.getMaxTotal()) {
                    forceResetPool();
                }
            }
            retryCount++;

            if (retryCount < MAX_RETRY_COUNT) {
                // 여러 스레드가 같은 순간에 다시 몰리지 않도록 대기 시간을 흩뜨린다 (0.5~1.5초)
                Thread.sleep(500L + ThreadLocalRandom.current().nextLong(1000L));
            }
        }

        throw new Exception(
                "최대 재시도 횟수 초과 - 마지막 오류: " + (lastException != null ? lastException.getMessage() : "알 수 없는 오류"),
                lastException);
    }

    private void invalidateSession(Session session) {
        if (session != null) {
            try {
                sessionPool.invalidateObject(session);
            } catch (Exception e) {
                logger.warn("세션 무효화 실패: {}", e.getMessage());
            } finally {
                try {
                    session.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private synchronized void forceResetPool() {
        if (closed) {
            return;
        }
        // synchronized 로 직렬화되어 들어온 뒤에도, 직전 호출이 이미 최근에 풀을 새로 만들었다면
        // (다른 스레드가 동시에 같은 고갈 상태를 감지해 대기했던 경우) 중복 초기화를 건너뛴다.
        if (System.currentTimeMillis() - lastPoolResetAtMillis < MIN_RESET_INTERVAL_MILLIS) {
            logger.info("세션 풀이 최근에 이미 초기화되어 중복 초기화를 건너뜁니다.");
            return;
        }

        try {
            logger.info("세션 풀 강제 초기화 시작 - 현재 상태: {}", getPoolStatus());

            // 1. 기존 풀 완전 종료
            try {
                sessionPool.close();
            } catch (Exception e) {
                logger.warn("기존 세션 풀 종료 중 오류: {}", e.getMessage());
            }

            // 2. 새로운 세션 풀 생성
            // 2~3. 새로운 세션 풀 생성 및 공통 설정 적용 (생성자에서 지정된 커스텀 크기를 그대로 유지한다)
            var newPool = createSessionPool(sessionMaxTotal, sessionMinIdle);

            // 4. 기존 풀 참조 교체
            this.sessionPool = newPool;
            this.lastPoolResetAtMillis = System.currentTimeMillis();

            // 5. 최소 세션 생성 (빌렸다 바로 반납하면 같은 세션만 되돌아와 1개만 생기므로 preparePool 로 minIdle 개를 채운다)
            try {
                newPool.preparePool();
            } catch (Exception e) {
                logger.error("초기 세션 생성 실패: {}", e.getMessage(), e);
            }

            logger.info("세션 풀 강제 초기화 완료 - 현재 상태: {}", getPoolStatus());
        } catch (Exception e) {
            logger.error("세션 풀 강제 초기화 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 세션을 풀에 반납한다. 연결이 끊긴 세션은 버린다 (반납 시 검증(testOnReturn)이 연결 유지 메시지로 한 번 더 확인함).
     * @param session 반납할 세션 (null 이면 무시)
     */
    public void returnSession(Session session) {
        if (session == null) {
            return;
        }
        if (!session.isConnected()) {
            invalidateSession(session);
            return;
        }
        try {
            sessionPool.returnObject(session);
        } catch (Exception e) {
            // 재초기화 전의 풀에서 빌린 세션 등: 이 풀 소속이 아니므로 연결만 끊는다
            logger.debug("세션을 풀에 반납하지 못해 연결을 끊습니다: {}", e.getMessage());
            try {
                session.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 세션 풀을 닫는다. 이후 {@link #getSession()}은 {@link IllegalStateException}을 던진다.
     */
    public void close() {
        closed = true;
        sessionPool.close(); // 세션 풀만 종료
    }

    public String getPoolStatus() {
        return String.format("Active: %d, Idle: %d, Max: %d", sessionPool.getNumActive(), sessionPool.getNumIdle(),
                sessionPool.getMaxTotal());
    }

}
