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
package io.github.devers2.s2util.file;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.NoSuchElementException;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

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

    /* 최대 세션 수 기본값 (64/128/256) */
    private final int DEFAULT_SESSION_MAX_TOTAL = 128;
    /* 최소 유지 세션 수 기본값 (8/16/32) */
    private final int DEFAULT_SESSION_MIN_IDLE = 16;
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
     * @param sessionMaxTotal      세션 풀의 최대 세션 수 (기본값 128)
     * @param sessionMinIdle       세션 풀에 유휴 상태로 유지할 최소 세션 수 (기본값 16)
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
     * @param sessionMaxTotal      세션 풀의 최대 세션 수 (기본값 128)
     * @param sessionMinIdle       세션 풀에 유휴 상태로 유지할 최소 세션 수 (기본값 16)
     * @param sessionMaxWaitMillis 세션 풀에서 사용 가능한 세션을 기다리는 최대 시간
     * @param knownHostsPath       호스트 키를 검증할 known_hosts 파일 경로 (null: {@code ~/.ssh/known_hosts}).
     *                             예: {@code ssh-keyscan -p 22 sftp.example.com >> /etc/ssh/known_hosts_sftp}
     * @param allowUnknownHosts    true 이면 {@code knownHostsPath}가 없을 때 호스트 키를 검증하지 않는다. 중간자 공격(MITM)에
     *                             노출되므로 신뢰할 수 있는 사설망에서만 명시적으로 켠다. {@code knownHostsPath}가 있으면 무시된다.
     * @apiNote 호스트 키 검증은 기본적으로 켜져 있다(fail-closed). known_hosts 에 서버 키가 없으면 연결이 거부된다.
     *          <p>
     *          기본값(128/16)을 그대로 쓰려면 대상 SFTP 서버(단일 서버 기준)의 sshd_config 에
     *          {@code MaxStartups 20:30:128} 설정을 권장한다. 자세한 근거는
     *          {@link io.github.devers2.s2util.file.impl.S2SftpFileManagerImpl#S2SftpFileManagerImpl(String, int, String, String, String, String, Integer, Integer, Integer)}
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
                session.connect();
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
            session.setConfig("ConnectTimeout", String.valueOf(sessionMaxWaitMillis)); // SFTP 연결
            // 타임아웃
            session.setConfig("ServerAliveInterval", "60000"); // 서버 연결 유지 확인 간격 (60초)
            session.setConfig("ServerAliveCountMax", "3"); // 연결 유지 실패 허용 횟수

            if (privateKeyPath != null && !privateKeyPath.isBlank()) {
                // 개인키 설정
                jsch.addIdentity(privateKeyPath, passphrase);
            } else {
                // privateKeyPath 가 없는 경우 password 사용
                session.setPassword(password);
            }

            configureHostKeyChecking(jsch, session);
            session.connect();
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

    public Session getSession() throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                // 풀 사용률이 높은 것("바쁨")만으로는 초기화하지 않는다. blockWhenExhausted +
                // sessionMaxWaitMillis 대기가 정상적인 backpressure 역할을 하므로, 여기서
                // 대여를 그대로 시도하고 실제로 실패(타임아웃/풀 close 등, 즉 "고장")했을 때만
                // 아래 catch 에서 forceResetPool() 로 복구한다.
                var session = sessionPool.borrowObject(Duration.ofMillis(sessionMaxWaitMillis));
                if (session != null) {
                    if (!session.isConnected()) {
                        try {
                            session.connect();
                        } catch (Exception e) {
                            invalidateSession(session);
                            throw e;
                        }
                    }
                    return session;
                }
            } catch (Exception e) {
                lastException = e;
                logger.error("세션 획득 실패 (시도 {}) - ", (retryCount + 1) + "/" + MAX_RETRY_COUNT, e);

                // borrowObject() 타임아웃(NoSuchElementException) 또는 풀이 닫혀버린 경우처럼
                // 풀이 실제로 "고장"난 신호일 때만 강제 초기화한다. forceResetPool() 내부의
                // 디바운스(MIN_RESET_INTERVAL_MILLIS)가 짧은 시간 내 반복 초기화를 막아준다.
                if (e instanceof NoSuchElementException
                        || (e.getMessage() != null && e.getMessage().contains("Pool not open"))) {
                    forceResetPool();
                }
            }
            retryCount++;

            if (retryCount < MAX_RETRY_COUNT) {
                Thread.sleep(1000);
            }
        }

        throw new Exception(
                "최대 재시도 횟수 초과 - 마지막 오류: " + (lastException != null ? lastException.getMessage() : "알 수 없는 오류"));
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
            // 초기 min-idle 세션을 채우는 아래 5번 단계가 무한 대기하지 않도록 이 풀 인스턴스에만 별도로 제한을 둔다.
            newPool.setMaxWait(Duration.ofSeconds(30));

            // 4. 기존 풀 참조 교체
            this.sessionPool = newPool;
            this.lastPoolResetAtMillis = System.currentTimeMillis();

            // 5. 최소 세션 생성
            try {
                for (var i = 0; i < sessionMinIdle; i++) {
                    var session = this.sessionPool.borrowObject();
                    if (session != null) {
                        this.sessionPool.returnObject(session);
                    }
                }
            } catch (Exception e) {
                logger.error("초기 세션 생성 실패: {}", e.getMessage(), e);
            }

            logger.info("세션 풀 강제 초기화 완료 - 현재 상태: {}", getPoolStatus());
        } catch (Exception e) {
            logger.error("세션 풀 강제 초기화 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    public void returnSession(Session session) {
        if (session != null) {
            try {
                if (!session.isConnected()) {
                    invalidateSession(session);
                    return;
                }

                try {
                    session.sendKeepAliveMsg();
                    sessionPool.returnObject(session);
                } catch (Exception e) {
                    logger.warn("세션 반환 실패 - 세션을 무효화합니다: {}", e.getMessage());
                    invalidateSession(session);
                }
            } catch (Exception e) {
                logger.error("세션 반환/무효화 중 오류 발생: {}", e.getMessage(), e);
                try {
                    session.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void close() {
        sessionPool.close(); // 세션 풀만 종료
    }

    public String getPoolStatus() {
        return String.format("Active: %d, Idle: %d, Max: %d", sessionPool.getNumActive(), sessionPool.getNumIdle(),
                sessionPool.getMaxTotal());
    }

}
