# SFTP Operations Guide

**English** | [한국어](./SFTP-GUIDE.ko.md)

Defaults and tuning for `S2SftpFileManagerImpl` (JSch), `SpringSftpConfig` (Spring Integration) and `S2UploadQueue`.

## 1. What the defaults assume: a typical production server

| Item | Assumption |
|---|---|
| SFTP server | 4 vCPU / 8GB RAM / 1Gbps |
| App servers | 2 |
| sshd_config | OpenSSH defaults (`MaxStartups 10:30:100`) |

With these, **no server setting has to change**: connections are not dropped and the server is not overloaded. They are
only defaults; every one can be set under the names below (examples in section 6).

| Setting | JSch (`S2SftpFileManagerImpl`) | Spring (`application.yml`) | Default | Why |
|---|---|---|---|---|
| Max connections (sessions) | constructor `sessionMaxTotal` | `sftp.pool.max-total` | 64 | Only a cap: connections open when needed and close after 30 idle minutes. About 200 small files per second |
| Connections opened in advance | constructor `sessionMinIdle` | `sftp.pool.min-idle` | 8 | The first requests after startup do not wait for handshakes |
| Idle connections kept | (= max) | `sftp.pool.max-idle` | 32 | |
| Parallel new connections | JVM option `-Ds2.sftp.maxConcurrentConnects` | `sftp.pool.max-concurrent-connects` | 4 | 2 app servers × 4 = 8 < first `MaxStartups` value 10, so the server does not drop connections at random |
| Wait for a connection | constructor `sessionMaxWaitMillis` | `sftp.pool.max-wait-millis` | 30s / 10s | Then retried (up to 3 times) and failed |
| Connect and auth timeout | (same as `sessionMaxWaitMillis`) | `sftp.timeout-millis` | 30s | |
| Queue parallel transfers | `S2UploadQueue.Builder.concurrency` | | 8 | The queue uses part of the pool, so page requests still get connections |
| Queue limit | `maxQueued` | | 1000 | More are rejected at once (`RejectedExecutionException`) |
| Free temp disk | `minFreeDiskBytes` | | 1GB | Temp files cannot fill the app server disk |

What really limits connections is **disk I/O and bandwidth** (more parallel transfers make each one slower) and the
**account's process and file limits (`ulimit -u`/`-n`)** rather than memory. sshd runs processes per connection, so
check that 2 app servers × 64 = 128 connections fit those limits.

## 2. How it works

- **Pool:** each connection (SSH session) opens one SFTP channel at a time, so the server's `MaxSessions` does not need
  raising. Connections are opened up to the maximum; when all are busy, callers wait.
- **Parallel new connections:** bursts and cold starts open new connections a few at a time.
- **Upload queue:** the request thread only saves a temp file and returns; a fixed number of background transfers send
  it and retry temporary failures.
- **Keep-alive:** checked every 60s; three missed replies close the connection. This also keeps firewalls and NAT from
  dropping idle connections.

## 3. Tuning by symptom

| Symptom (log or exception) | Cause | Adjust |
|---|---|---|
| `최대 재시도 횟수 초과` (JSch) or `SFTP 세션 획득 실패` (Spring), only in short bursts | All connections briefly busy | Wait a little longer for a connection |
| The same failures often, with spare server CPU, memory and bandwidth | More concurrent use than connections | Raise max connections 64 → 128 (check server limits, section 4) |
| The same failures only with large files or bulk uploads | Bandwidth is the limit, so transfers hold connections long | Keep the pool; send through `S2UploadQueue`, and lower its parallelism |
| `Upload queue is full` | More queued uploads than the limit | Tell users to retry; if it persists, raise queue parallelism only with server headroom, and raise `maxQueued` |
| `Not enough disk space for upload temp files` | App server temp disk is short | Move `tempDir` to a larger disk or lower `maxQueued` |
| Only the first requests after a deploy are slow | Connections are being opened | Raise connections opened in advance to the usual concurrent use |
| `Too many SFTP connections are opening at once` | Many requests wait to open connections (cold start, frequent drops) | Raise connections opened in advance; raise parallel new connections if the server's `MaxStartups` allows |
| `Connection closed` or `connection is closed by foreign host` during bursts | The server's `MaxStartups` drops unauthenticated connections | Lower parallel new connections, or set the first `MaxStartups` value above (app servers × parallel new connections) |
| Frequent connect timeouts (distant or slow server) | Handshakes do not finish in time | Raise the connect/auth timeout and the wait for a connection |
| SFTP server short of memory or processes | (app servers × max connections) exceeds what it can hold | Lower max connections per app server and queue bulk work |

## 4. Starting points by size

Starting points to confirm with a load test.

| Size | SFTP server | App servers | Max connections | Opened in advance | Parallel new connections | Queue parallelism | sshd_config |
|---|---|---|---|---|---|---|---|
| Small | 2C / 4GB, shared with other services | 1–2 | 32 | 4 | 4 | 4 | unchanged |
| Typical (defaults) | 4C / 8GB | 2 | 64 | 8 | 4 | 8 | unchanged (`internal-sftp` recommended) |
| Medium | 4–8C / 8–16GB | 3–4 | 64 | 8 | 4 | 8 | `MaxStartups 30:30:200`, `Subsystem sftp internal-sftp` |
| Large | 8C / 16GB+ | up to 4 | 128 | 16 | 8 | 16 | `MaxStartups 60:30:400`, `internal-sftp`, check the account's `ulimit -u`/`-n` |

- Keep the first `MaxStartups` value **above (app servers × parallel new connections)**.
- `Subsystem sftp internal-sftp` avoids one more `sftp-server` process per connection.
- Put the server's upload folder on its own partition or quota, with a usage alert. A full disk is the most common way
  a file server stops.

## 5. Estimating throughput

**Concurrent transfers ≈ transfers per second × seconds per transfer**

| File | Time per transfer | With 64 connections | Limit |
|---|---|---|---|
| 1MB or less (documents, images) | 0.1–0.3s | about 200–600 per second | connections |
| 10MB | depends on bandwidth | about 1/s at 100Mbps, 11/s at 1Gbps | bandwidth |
| 100MB | depends on bandwidth | about 1/s at 1Gbps | bandwidth |

Large files are limited by bandwidth, not connections: more connections make each transfer slower without raising
throughput. Match queue parallelism to the bandwidth instead (2–4 at 100Mbps).

## 6. Examples

**JSch**
```java
// 128 connections, 16 opened in advance, 30s wait (large). null means the default (64, 8, 30s)
FileManager sftp = new S2SftpFileManagerImpl(host, 22, "app", "/keys/id_ed25519", null, null,
        128, 16, 30_000, "/etc/ssh/known_hosts_sftp", false);
// Parallel new connections: JVM option -Ds2.sftp.maxConcurrentConnects=4
```

**Spring (`application.yml`)**
```yaml
sftp:
  host: sftp.example.com
  port: 22
  user: app
  private-key-path: /keys/id_ed25519
  known-hosts-path: /etc/ssh/known_hosts_sftp
  timeout-millis: 30000          # connect and auth timeout
  pool:
    max-total: 64                # max connections (default 64, may be omitted)
    max-idle: 32
    min-idle: 8                  # opened right after startup
    max-wait-millis: 10000       # wait for a connection
    max-concurrent-connects: 4   # parallel new connections
```

**Upload queue**
```java
S2UploadQueue queue = S2UploadQueue.builder(sftp)
        .concurrency(8)              // well below max connections
        .maxQueued(1000)
        .maxFileBytes(500L << 20)
        .tempDir(Path.of("/data/upload-tmp"))
        .build();
```

## 7. What to watch

- JSch warning `세션 획득 실패 (시도 n/3) ... [Active: a, Idle: i, Max: m]`: if `Active` often reaches `Max`, follow
  the table in section 3.
- `S2UploadQueue.getQueuedCount()`: if it only grows, transfers cannot keep up.
- SFTP server: number of `sshd` processes, memory, upload disk usage, and connections dropped by `MaxStartups`
  (`drop connection` in `/var/log/auth.log`).
