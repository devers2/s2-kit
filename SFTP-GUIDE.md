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
| Parallel new connections | constructor `maxConcurrentConnects` or JVM option `-Ds2.sftp.maxConcurrentConnects` | `sftp.pool.max-concurrent-connects` | 4 | 2 app servers × 4 = 8 < first `MaxStartups` value 10, so the server does not drop connections at random |
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
  it and retry temporary failures from where they stopped (section 8.4).
- **Keep-alive:** checked every 60s; three missed replies close the connection. This also keeps firewalls and NAT from
  dropping idle connections.

## 3. Tuning by symptom

| Symptom (log or exception) | Cause | Adjust |
|---|---|---|
| `Retries exhausted` (JSch) or `Cannot get an SFTP session` (Spring), only in short bursts | All connections briefly busy | Wait a little longer for a connection |
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
        128, 16, 30_000, "/etc/ssh/known_hosts_sftp", false, 8);   // last: parallel new connections (null: 4)
// For every JSch manager in the JVM at once: -Ds2.sftp.maxConcurrentConnects=8 (the constructor argument wins)
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

- JSch warning `Cannot get a session (attempt n/3) ... [Active: a, Idle: i, Max: m]` (`세션 획득 실패 (시도 n/3)` on a
  Korean-locale JVM): if `Active` often reaches `Max`, follow
  the table in section 3.
- `S2UploadQueue.getQueuedCount()`: if it only grows, transfers cannot keep up.
- SFTP server: number of `sshd` processes, memory, upload disk usage, and connections dropped by `MaxStartups`
  (`drop connection` in `/var/log/auth.log`).

## 8. Large uploads (hundreds of MB)

The SFTP side handles 200–300MB files. Bandwidth is the limit (250MB takes about 2.3s on 1Gbps, about 26 per minute at
full use), and **size and time limits in front of the application, and disk space,** stop uploads first.

### 8.1 Front settings (otherwise uploads are rejected before reaching SFTP)

**Spring Boot (`application.yml`)**
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 300MB        # default 1MB
      max-request-size: 310MB     # default 10MB
      file-size-threshold: 0      # write to disk at once (not memory)
      location: /data/upload-tmp  # same disk as the queue temp folder (transferTo moves instead of copying)
server:
  tomcat:
    max-swallow-size: -1          # read and drop the body of a rejected upload instead of resetting the connection
```

**nginx**
```nginx
client_max_body_size   300m;   # default 1m
client_body_timeout    300s;   # longest gap between body reads
proxy_request_buffering off;   # pass the body on instead of saving it to disk again
proxy_send_timeout     300s;
proxy_read_timeout     300s;
```
Also raise the load balancer idle timeout (AWS ALB: 60s by default) above the upload time. At 20Mbps a user needs about
two minutes just to send 300MB.

### 8.2 Controller: move the received file into the queue

Passing a stream creates two copies (the multipart temp file and the queue temp file). Handing over the file received
with `transferTo` needs one (a move, not a copy, on the same disk).

```java
@PostMapping("/files")
public ResponseEntity<Void> upload(@RequestParam MultipartFile file) throws IOException {
    Path received = Files.createTempFile(Path.of("/data/upload-tmp"), "in-", ".part");
    file.transferTo(received.toFile());   // File overload: moves on the same disk (the Path overload copies)
    try {
        uploadQueue.submit(received, "/upload/2026", storedName, false, true)   // true: delete received when done
                .whenComplete((size, error) -> fileService.markUploaded(id, error == null));
    } catch (RejectedExecutionException e) {
        Files.deleteIfExists(received);
        return ResponseEntity.status(503).header("Retry-After", "60").build();      // queue is full
    }
    return ResponseEntity.accepted().build();                                       // accepted; sent in the background
}
```

### 8.3 Sizing the queue

| Setting | How to choose | Example (100GB temp disk, 1Gbps, files up to 300MB) |
|---|---|---|
| `concurrency` | bandwidth ÷ speed wanted per transfer; more does not raise total throughput | 4 (about 27MB/s each) |
| `maxQueued` | (temp disk − free margin) ÷ largest file | (100GB − 10GB) ÷ 300MB ≈ 300 |
| `minFreeDiskBytes` | about largest file × uploads accepted at once | 10GB |
| `maxFileBytes` | service limit (applies when a stream is submitted) | 300MB |

```java
S2UploadQueue uploadQueue = S2UploadQueue.builder(sftp)
        .concurrency(4)
        .maxQueued(300)
        .minFreeDiskBytes(10L << 30)
        .maxFileBytes(300L << 20)
        .tempDir(Path.of("/data/upload-tmp"))
        .build();
```

### 8.4 Resuming

When the queue retries, the SFTP managers **continue where the transfer stopped**: if 300MB broke at 250MB, only the
last 50MB is sent.

- The file being sent is written to a hidden `.<name>.<key>.s2part` next to the target and renamed when complete, so
  nobody reads a half-written file.
- A partial file larger than the source (other content) is written again from the start; if the size differs from the
  source after sending, it is deleted and the attempt fails.
- The queue deletes the partial file when it gives an upload up. One can remain after a forced shutdown, so delete old
  ones on the SFTP server:
  ```bash
  find /upload -name '.*.s2part' -mmin +1440 -delete   # cron, daily
  ```
- Without the queue: `fileManager.writeFile(path, savePath, saveName, overwrite, resumeKey)`; call again with the same
  `resumeKey` after a failure, and `discardPartial(savePath, saveName, resumeKey)` when giving up.
- Without `overwrite`, the target is checked just before the rename. SFTP servers usually rename over existing files,
  so a file created under the same name in that instant can be replaced (unique stored names, such as UUIDs, avoid it).

### 8.5 SFTP server disk

Hundreds of 300MB files a day add tens to hundreds of GB a day. Keep the upload folder on its own partition or quota
with a usage alert (for example 80%). A full disk stops not only uploads but every service using that disk.

## 9. Operations checklist

What people have to do in the production environment, since the code cannot. Check it at the first deployment and
whenever the size changes.

- [ ] **Server settings** (section 4): `MaxStartups` for the size, `Subsystem sftp internal-sftp`. Check that the
  account's `ulimit -u`/`-n` covers (app servers × max connections)
- [ ] **Upload disk** (section 8.5): its own partition or quota, with a usage alert (for example 80%)
- [ ] **Partial files** (section 8.4): a cron job on the SFTP server that deletes `.s2part` files older than a day
  ```bash
  0 4 * * * find /upload -name '.*.s2part' -mmin +1440 -delete
  ```
- [ ] **App server temp folder**: put the `S2UploadQueue` `tempDir` and the multipart `location` on the same large
  disk (section 8.2)
- [ ] **Load test on staging**: push uploads and downloads with real file sizes and user counts, and check that
  - the SFTP server's `sshd` processes stay within (app servers × max connections)
  - `/var/log/auth.log` (or `secure`) has no `drop connection ... past MaxStartups`; if it does, raise `MaxStartups`
    or lower the parallel new connections (section 3)
  - the app log has no burst of session failures (`Cannot get a session` / `세션 획득 실패`) and
    `S2UploadQueue.getQueuedCount()` goes down
  - throughput close to the estimates in section 5; if far below, suspect bandwidth and disk first
- [ ] **Right after a deploy**: the log shows `Opened SFTP sessions in advance` (`SFTP 세션을 미리 열었습니다`), so
  sessions were opened in advance
