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

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.sshd.core.CoreModuleProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpSession;

import io.github.devers2.s2util.core.S2ThreadUtil;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

@Configuration
public class SpringSftpConfig {

    private static final S2Logger logger = S2LogManager.getLogger(SpringSftpConfig.class);

    /* 유휴 객체 정비 주기 (초) */
    private static final int EVICTION_RUN_INTERVAL_SECONDS = 60;
    /* 사용하지 않은 유휴 세션의 최대 보관 시간 (분) */
    private static final int MIN_EVICTABLE_IDLE_MINUTES = 30;
    /* 연결 유지 확인 간격 (초): 방화벽·NAT 가 유휴 연결을 끊지 않게 하고, 죽은 연결을 찾는다 */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 60;
    /* 연결 유지 확인 응답 대기 시간 (초) */
    private static final int HEARTBEAT_REPLY_WAIT_SECONDS = 30;
    /* 연결 유지 확인에 연달아 응답이 없으면 연결을 끊는 횟수 */
    private static final int HEARTBEAT_NO_REPLY_MAX = 3;
    /* 반납되지 않은(대여 상태로 방치된) 세션 강제 회수 시간 (분) */
    private static final int ABANDONED_TIMEOUT_MINUTES = 35;

    @Value("${sftp.host}")
    private String sftpHost;

    @Value("${sftp.port}")
    private int sftpPort;

    @Value("${sftp.user}")
    private String sftpUser;

    @Value("${sftp.password:#{null}}")
    private String sftpPassword;

    /* 개인키 경로 (선택). 없으면 sftp.password 로 인증한다. */
    @Value("${sftp.private-key-path:#{null}}")
    private String privateKeyPath;

    // 패스프레이즈 없는 개인키가 흔하므로 sftp.password 와 마찬가지로 선택 값으로 처리한다.
    @Value("${sftp.private-key-passphrase:#{null}}")
    private String privateKeyPassphrase;

    /*
     * SFTP 서버의 호스트 키(서버가 제시하는 SSH 공개키)를 검증할지 여부.
     * DefaultSftpSessionFactory 자체 기본값(false, 안전)을 그대로 따른다.
     *
     * - false(기본값): 호스트 키를 반드시 검증해야 함 -> 아래 knownHostsPath 로 known_hosts 파일을
     *   지정해야 접속이 가능하다. knownHostsPath 도 없으면 검증할 대상이 없어 모든 연결이 거부된다
     *   (RejectAllServerKeyVerifier) - 즉 "설정을 깜빡해서 통과되는" 게 아니라 "설정을 안 하면 연결이
     *   막히는" fail-safe 방향이다.
     * - true: 호스트 키를 검증하지 않고 서버가 무엇을 제시하든 그대로 신뢰한다. 중간자 공격(MITM)에
     *   노출될 수 있으므로, 신뢰할 수 있는 사설망 안에서만 sftp.allow-unknown-hosts=true 로 명시적으로
     *   완화할 것을 권장한다.
     *
     * 설정 예 (application.yml):
     *   sftp:
     *     known-hosts-path: /etc/ssh/known_hosts_sftp   # 정상적인 검증 (권장)
     *     # 또는, 신뢰된 내부망이라 검증이 불필요하다면:
     *     allow-unknown-hosts: true
     */
    @Value("${sftp.allow-unknown-hosts:false}")
    private boolean allowUnknownHosts;

    /*
     * known_hosts 파일 경로 (선택). 설정하면 allowUnknownHosts 값과 무관하게 이 파일 기준으로
     * 호스트 키를 검증한다(known_hosts 가 우선 적용됨). ssh-keyscan 등으로 대상 SFTP 서버의 공개
     * 키를 미리 등록해두는 방식으로, allow-unknown-hosts=true 보다 안전한 정석적인 방법이다.
     * 예: ssh-keyscan -p ${sftp.port} ${sftp.host} >> /etc/ssh/known_hosts_sftp
     */
    @Value("${sftp.known-hosts-path:#{null}}")
    private String knownHostsPath;

    @Value("${sftp.pool.max-total:128}")
    private int maxTotal;

    @Value("${sftp.pool.max-idle:32}")
    private int maxIdle;

    @Value("${sftp.pool.min-idle:8}")
    private int minIdle;

    /* 세션 대여 대기 시간 (밀리초) */
    @Value("${sftp.pool.max-wait-millis:10000}")
    private long maxWaitMillis;

    /*
     * 동시에 진행하는 신규 SSH 연결(핸드셰이크·인증) 수 상한. OpenSSH 는 인증 전 연결이 MaxStartups 첫 값(기본 10)을 넘으면
     * 무작위로 끊으므로, 대량 요청이나 콜드스타트에 연결을 한꺼번에 열지 않도록 줄 세운다. 앱 서버가 여러 대면
     * (앱 서버 수 × 이 값)이 서버 MaxStartups 첫 값보다 작게 한다.
     */
    @Value("${sftp.pool.max-concurrent-connects:8}")
    private int maxConcurrentConnects;

    /* SSH 연결·인증 제한 시간 (밀리초) */
    @Value("${sftp.timeout-millis:30000}")
    private int timeoutMillis;

    @SuppressWarnings("null")
    @Bean
    public DefaultSftpSessionFactory sftpSessionFactory() throws IOException {
        // ssh 키 생성시에는 PEM 형식으로 할 것 !!!!!
        // ssh-keygen -p -m PEM -f /Users/eseun/.ssh/sftp_key
        // 공유하지 않는다(false): 공유(true)면 getSession() 이 모두 같은 SftpClient(SSH 연결·SFTP 채널 하나)를 돌려주므로 풀에
        // 몇 개가 있든 모든 전송이 채널 하나로 몰리고, 그 연결이 끊기면 진행 중인 전송이 한꺼번에 실패한다.
        // 공유하지 않으면 풀의 세션마다 자기 SSH 연결을 가진다 (연결당 채널 1개라 서버 MaxSessions 는 늘릴 필요 없음).
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(false);
        factory.setHost(sftpHost);
        factory.setPort(sftpPort);
        factory.setUser(sftpUser);
        factory.setTimeout(timeoutMillis); // 연결·인증이 응답 없는 서버에서 멈추지 않도록
        factory.setSshClientConfigurer(client -> {
            CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS));
            CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(client, Duration.ofSeconds(HEARTBEAT_REPLY_WAIT_SECONDS));
            CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, HEARTBEAT_NO_REPLY_MAX);
        });

        // 호스트 키 검증: known-hosts-path 가 설정되면 그 파일 기준으로 항상 검증한다(allowUnknownHosts와
        // 무관하게 우선 적용됨). known-hosts-path 가 없으면 allowUnknownHosts 값을 따르는데, 기본값(false)
        // 상태에서 known-hosts-path 도 없으면 모든 연결의 호스트 키 검증이 거부되어 연결에 실패한다 —
        // 신뢰할 수 있는 내부망이라 검증이 필요 없다면 sftp.allow-unknown-hosts=true 로 명시적으로 완화할 것.
        if (knownHostsPath != null && !knownHostsPath.isBlank()) {
            factory.setKnownHostsResource(new FileSystemResource(knownHostsPath));
        } else if (!allowUnknownHosts) {
            logger.warn(
                    "sftp.known-hosts-path 가 설정되지 않았고 sftp.allow-unknown-hosts 도 false 입니다. "
                            + "이 상태에서는 모든 SFTP 연결의 호스트 키 검증이 거부되어 연결이 실패합니다. "
                            + "known-hosts 파일 경로를 설정하거나, 신뢰할 수 있는 내부망이라면 "
                            + "sftp.allow-unknown-hosts=true 로 설정하세요.");
        }
        factory.setAllowUnknownKeys(allowUnknownHosts);

        // Private key when configured, otherwise password (same rule as JschSessionFactory) | 개인키가 있으면 개인키, 없으면 비밀번호 (JschSessionFactory 와 같은 규칙)
        if (privateKeyPath != null && !privateKeyPath.isBlank()) {
            factory.setPrivateKey(new FileSystemResource(privateKeyPath));
            factory.setPrivateKeyPassphrase(privateKeyPassphrase);
        } else if (sftpPassword != null && !sftpPassword.isEmpty()) {
            factory.setPassword(sftpPassword);
        } else {
            throw new IllegalStateException("sftp.private-key-path 또는 sftp.password 중 하나를 설정해야 합니다.");
        }
        return factory;
    }

    @Bean
    public GenericObjectPool<SftpSession> sftpSessionPool(DefaultSftpSessionFactory sessionFactory) {
        GenericObjectPoolConfig<SftpSession> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        // 검증 설정
        config.setTestOnBorrow(true);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(true);
        // 고갈 시 대기 설정
        config.setBlockWhenExhausted(true);
        config.setMaxWait(Duration.ofMillis(maxWaitMillis));
        // 에빅션 설정 (30분 이상 유휴 세션 제거, 60초 주기 검사)
        config.setTimeBetweenEvictionRuns(Duration.ofSeconds(EVICTION_RUN_INTERVAL_SECONDS));
        config.setMinEvictableIdleDuration(Duration.ofMinutes(MIN_EVICTABLE_IDLE_MINUTES));
        // JMX 비활성화
        config.setJmxEnabled(false);

        Semaphore connectPermits = new Semaphore(Math.max(1, maxConcurrentConnects), true);
        long connectWaitMillis = Math.max(0L, maxWaitMillis);
        GenericObjectPool<SftpSession> pool = new GenericObjectPool<>(new BasePooledObjectFactory<SftpSession>() {
            @Override
            public SftpSession create() throws InterruptedException {
                // 동시 신규 연결 수 상한 안에서 연결한다
                if (!connectPermits.tryAcquire(connectWaitMillis, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Too many SFTP connections are opening at once (limit "
                            + maxConcurrentConnects + "); no turn within " + connectWaitMillis + "ms");
                }
                try {
                    return sessionFactory.getSession();
                } finally {
                    connectPermits.release();
                }
            }

            @Override
            public PooledObject<SftpSession> wrap(SftpSession session) {
                return new DefaultPooledObject<>(session);
            }

            @Override
            public boolean validateObject(PooledObject<SftpSession> p) {
                try {
                    SftpSession s = p.getObject();
                    return s != null && s.isOpen();
                } catch (RuntimeException e) {
                    return false;
                }
            }

            @Override
            public void destroyObject(PooledObject<SftpSession> p) {
                try {
                    SftpSession s = p.getObject();
                    if (s != null) {
                        s.close();
                    }
                } catch (RuntimeException ignore) {
                    logger.debug("Ignored runtime exception while closing SFTP session", ignore);
                }
            }
        }, config);

        // Abandoned(반환 누락) 세션 자동 회수 설정
        AbandonedConfig abandoned = new AbandonedConfig();
        abandoned.setRemoveAbandonedOnBorrow(true);
        abandoned.setRemoveAbandonedOnMaintenance(true);
        abandoned.setRemoveAbandonedTimeout(Duration.ofMinutes(ABANDONED_TIMEOUT_MINUTES));
        abandoned.setLogAbandoned(true);
        pool.setAbandonedConfig(abandoned);

        // 최소 유휴 세션(min-idle)만큼 미리 연결해 둔다 (그대로 두면 첫 풀 정비 때(약 1분 뒤)에야 채워짐). 백그라운드에서 하므로
        // 기동을 기다리게 하지 않고, SFTP 서버가 꺼져 있어도 기동이 실패하지 않는다 (요청 때 다시 연결함).
        if (minIdle > 0) {
            Runnable prewarm = () -> {
                try {
                    pool.preparePool();
                    logger.info("SFTP 세션을 미리 열었습니다: {}:{} (idle {})", sftpHost, sftpPort, pool.getNumIdle());
                } catch (Exception e) {
                    if (!pool.isClosed()) {
                        logger.warn("SFTP 세션을 미리 열지 못했습니다 (요청 때 다시 연결함): {}:{} - {}", sftpHost, sftpPort,
                                e.toString());
                    }
                }
            };
            try {
                S2ThreadUtil.getCommonExecutor().execute(prewarm);
            } catch (RejectedExecutionException e) {
                logger.debug("미리 열기를 건너뜁니다 (실행기가 받지 않음): {}", e.getMessage());
            }
        }

        return pool;
    }
}
