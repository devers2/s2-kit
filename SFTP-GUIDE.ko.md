# SFTP 운영 가이드

[English](./SFTP-GUIDE.md) | **한국어**

`S2SftpFileManagerImpl`(JSch), `SpringSftpConfig`(Spring Integration), `S2UploadQueue`의 기본값과 조절 방법을 정리합니다.

## 1. 기본값의 기준: 일반 운영 서버

| 항목 | 기준 |
|---|---|
| SFTP 서버 | 4 vCPU / 8GB RAM / 1Gbps |
| 앱 서버 | 2대 |
| sshd_config | OpenSSH 기본값 그대로 (`MaxStartups 10:30:100`) |

이 환경에서 **서버 설정을 바꾸지 않아도** 연결이 끊기거나 서버가 무리하지 않도록 기본값을 잡았습니다. 기본값일 뿐이며 모두
아래 이름으로 바꿀 수 있습니다(6절 예시).

| 설정 | JSch (`S2SftpFileManagerImpl`) | Spring (`application.yml`) | 기본값 | 이유 |
|---|---|---|---|---|
| 최대 연결(세션) 수 | 생성자 `sessionMaxTotal` | `sftp.pool.max-total` | 64 | 상한일 뿐 연결은 필요할 때만 열고 30분 안 쓰면 닫음. 작은 파일 초당 약 200건 |
| 미리 열어 둘 연결 수 | 생성자 `sessionMinIdle` | `sftp.pool.min-idle` | 8 | 기동 직후 첫 요청이 연결 맺기를 기다리지 않음 |
| 한가할 때 남길 연결 수 | (= 최대) | `sftp.pool.max-idle` | 32 | |
| 동시 신규 연결 | 생성자 `maxConcurrentConnects` 또는 JVM 옵션 `-Ds2.sftp.maxConcurrentConnects` | `sftp.pool.max-concurrent-connects` | 4 | 앱 2대 × 4 = 8 < `MaxStartups` 첫 값 10 → 서버가 연결을 무작위로 끊지 않음 |
| 연결을 기다리는 시간 | 생성자 `sessionMaxWaitMillis` | `sftp.pool.max-wait-millis` | 30초 / 10초 | 넘으면 재시도(최대 3번) 후 실패 |
| 연결·인증 제한 시간 | (`sessionMaxWaitMillis` 와 같음) | `sftp.timeout-millis` | 30초 | |
| 큐 동시 전송 수 | `S2UploadQueue.Builder.concurrency` | | 8 | 큐가 풀의 일부만 써서 화면 요청도 연결을 얻음 |
| 큐 대기 상한 | `maxQueued` | | 1000 | 넘으면 바로 거절 (`RejectedExecutionException`) |
| 임시 디스크 여유 | `minFreeDiskBytes` | | 1GB | 임시 파일이 앱 서버 디스크를 채우지 않음 |

연결 수의 실제 한계는 메모리보다 **디스크 I/O·대역폭**(동시 전송을 늘려도 한 건씩 느려짐)과 **계정 프로세스·파일 수 한도
(`ulimit -u`/`-n`)**입니다. sshd 는 연결마다 프로세스를 띄우므로, 앱 2대 × 64 = 연결 128개가 그 한도 안에 드는지 확인합니다.

## 2. 어떻게 동작하나

- **풀:** 연결(SSH 세션)마다 SFTP 채널을 한 번에 하나만 엽니다. 그래서 서버의 `MaxSessions`는 늘릴 필요가 없습니다. 연결은 최대치까지만 열고, 모두 사용 중이면 호출 측이 기다립니다.
- **동시 신규 연결 제한:** 요청이 몰리거나 배포 직후여도 새 연결은 정해진 수씩 차례로 맺습니다.
- **업로드 큐:** 요청 스레드는 임시 파일에 받기만 하고 바로 돌아옵니다. 실제 전송은 정해진 수만큼만 백그라운드에서 하고, 일시적 실패는 다시 시도합니다.
- **연결 유지 확인:** 60초마다 확인하고 3번 연속 응답이 없으면 끊습니다. 방화벽·NAT 가 유휴 연결을 끊는 것도 막습니다.

## 3. 증상별 조절

| 증상 (로그·예외) | 원인 | 조절 |
|---|---|---|
| `최대 재시도 횟수 초과`(JSch), `SFTP 세션 획득 실패`(Spring)가 가끔, 짧게 몰릴 때만 | 순간적으로 연결이 모두 사용 중 | 연결을 기다리는 시간을 조금 늘림 |
| 위 실패가 자주 나고, 서버 CPU·메모리·대역폭에 여유가 있음 | 동시 사용량이 최대 연결 수보다 많음 | 최대 연결 수를 64 → 128 로 올림 (서버 한도 확인, 4절) |
| 큰 파일·일괄 업로드 때만 위 실패가 남 | 대역폭이 한계라 전송이 오래 연결을 붙잡음 | 풀은 그대로 두고 `S2UploadQueue`로 보냄. 큐 동시 수는 오히려 낮춤 |
| `Upload queue is full` | 큐에 쌓인 업로드가 상한을 넘음 | 사용자에게 다시 시도 안내. 계속되면 서버에 여유가 있을 때만 큐 동시 수를 올리고, `maxQueued`를 올림 |
| `Not enough disk space for upload temp files` | 앱 서버 임시 디스크 부족 | 임시 폴더(`tempDir`)를 넉넉한 디스크로 옮기거나 `maxQueued`를 낮춤 |
| 배포 직후 첫 요청들만 느림 | 연결을 맺는 중 | 미리 열어 둘 연결 수를 평소 동시 사용량만큼 올림 |
| `Too many SFTP connections are opening at once` | 새 연결을 기다리는 요청이 많음 (콜드스타트·연결이 자주 끊김) | 미리 열어 둘 연결 수를 올림. 서버 `MaxStartups`에 여유가 있으면 동시 신규 연결을 올림 |
| 연결 중 `Connection closed`, `connection is closed by foreign host`가 몰릴 때 남 | 서버 `MaxStartups`가 인증 전 연결을 끊음 | 동시 신규 연결을 낮추거나, 서버 `MaxStartups` 첫 값을 (앱 서버 수 × 동시 신규 연결)보다 크게 |
| 연결 시간 초과가 잦음 (서버가 멀거나 느림) | 핸드셰이크가 제한 시간 안에 안 끝남 | 연결·인증 제한 시간과 연결을 기다리는 시간을 늘림 |
| SFTP 서버 메모리·프로세스 부족 | (앱 서버 수 × 최대 연결 수)가 서버 한도를 넘음 | 앱 서버당 최대 연결 수를 낮추고 대량 작업은 큐로 |

## 4. 규모별 출발점

수치는 부하 시험으로 확인해야 할 **출발점**입니다.

| 규모 | SFTP 서버 | 앱 서버 | 최대 연결 | 미리 열기 | 동시 신규 연결 | 큐 동시 수 | sshd_config |
|---|---|---|---|---|---|---|---|
| 작은 서버 | 2C / 4GB, 다른 서비스와 공유 | 1~2대 | 32 | 4 | 4 | 4 | 바꾸지 않음 |
| 일반 (기본값) | 4C / 8GB | 2대 | 64 | 8 | 4 | 8 | 바꾸지 않음 (`internal-sftp` 권장) |
| 중간 | 4~8C / 8~16GB | 3~4대 | 64 | 8 | 4 | 8 | `MaxStartups 30:30:200`, `Subsystem sftp internal-sftp` |
| 큰 규모 | 8C / 16GB 이상 | 4대 이하 | 128 | 16 | 8 | 16 | `MaxStartups 60:30:400`, `internal-sftp`, 계정 `ulimit -u`/`-n` 확인 |

- `MaxStartups` 첫 값은 항상 **(앱 서버 수 × 동시 신규 연결)보다 크게** 둡니다.
- `Subsystem sftp internal-sftp`로 두면 연결마다 `sftp-server` 프로세스를 하나 더 띄우지 않습니다.
- SFTP 서버의 업로드 폴더는 별도 파티션이나 쿼터를 두고 사용량 경보를 다는 것을 권합니다. 디스크가 가득 차는 것이 서버를 멈추게 하는 가장 흔한 원인입니다.

## 5. 처리량 어림잡기

**동시 전송 수 ≈ 초당 전송 건수 × 전송 1건에 걸리는 시간(초)**

| 파일 | 1건 시간 | 연결 64개로 | 한계 |
|---|---|---|---|
| 1MB 이하 (문서·이미지) | 0.1~0.3초 | 초당 약 200~600건 | 연결 수 |
| 10MB | 대역폭에 따라 | 100Mbps 에서 초당 약 1건, 1Gbps 에서 약 11건 | 대역폭 |
| 100MB | 대역폭에 따라 | 1Gbps 에서 초당 약 1건 | 대역폭 |

큰 파일은 연결 수가 아니라 대역폭이 한계라서, 최대 연결 수를 올려도 처리량은 늘지 않고 한 건씩 느려집니다. 이때는 큐 동시 수를 대역폭에 맞춰 둡니다(100Mbps 면 2~4).

## 6. 설정 예

**JSch**
```java
// 최대 연결 128, 미리 열기 16, 연결 대기 30초 (큰 규모). null 이면 기본값(64, 8, 30초)
FileManager sftp = new S2SftpFileManagerImpl(host, 22, "app", "/keys/id_ed25519", null, null,
        128, 16, 30_000, "/etc/ssh/known_hosts_sftp", false, 8);   // 마지막: 동시 신규 연결 (null: 4)
// 같은 JVM 의 모든 JSch 관리자에 한 번에 주려면 JVM 옵션 -Ds2.sftp.maxConcurrentConnects=8 (생성자 인자가 우선)
```

**Spring (`application.yml`)**
```yaml
sftp:
  host: sftp.example.com
  port: 22
  user: app
  private-key-path: /keys/id_ed25519
  known-hosts-path: /etc/ssh/known_hosts_sftp
  timeout-millis: 30000          # 연결·인증 제한 시간
  pool:
    max-total: 64                # 최대 연결 수 (기본값 64, 생략 가능)
    max-idle: 32
    min-idle: 8                  # 기동 직후 미리 열 연결 수
    max-wait-millis: 10000       # 연결을 기다리는 시간
    max-concurrent-connects: 4   # 동시 신규 연결
```

**업로드 큐**
```java
S2UploadQueue queue = S2UploadQueue.builder(sftp)
        .concurrency(8)              // 큐 동시 전송 수 (최대 연결 수보다 충분히 작게)
        .maxQueued(1000)
        .maxFileBytes(500L << 20)
        .tempDir(Path.of("/data/upload-tmp"))
        .build();
```

## 7. 지켜볼 것

- JSch 경고 로그 `세션 획득 실패 (시도 n/3) ... [Active: a, Idle: i, Max: m]`: `Active`가 `Max`에 자주 닿으면 3절 표를 따릅니다.
- `S2UploadQueue.getQueuedCount()`: 늘기만 하면 전송이 처리량을 못 따라가는 것입니다.
- SFTP 서버: `sshd` 프로세스 수, 메모리, 업로드 디스크 사용량, `MaxStartups`로 끊긴 연결(`/var/log/auth.log`의 `drop connection`).
