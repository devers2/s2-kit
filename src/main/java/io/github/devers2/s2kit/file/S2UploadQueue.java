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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.github.devers2.s2kit.support.S2FileUtil;
import io.github.devers2.s2kit.support.S2StreamUtil;
import io.github.devers2.s2util.core.S2ThreadUtil;
import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * Uploads files through a {@link FileManager} in the background with a fixed number of parallel transfers, so a burst of
 * uploads or a batch of large files neither holds request threads nor overloads the file server.
 * <ul>
 * <li>{@link #submit} first saves the data to a local temporary file, then returns at once with a
 * {@link CompletableFuture} of the stored size. The caller's thread does not wait for the transfer.</li>
 * <li>At most {@code concurrency} transfers run at once; the rest wait in the queue.</li>
 * <li>When {@code maxQueued} uploads are already accepted, {@code submit} fails at once with a
 * {@link RejectedExecutionException} instead of blocking (tell the user to try again later).</li>
 * <li>A failed transfer is retried with a growing, randomized delay. An existing file ({@link S2FileExistsException})
 * or an invalid path is not retried.</li>
 * <li>The queue lives in memory: uploads not finished when the application stops are lost (their temporary files
 * are deleted). Record the result of each future (for example a status column) if that matters.</li>
 * </ul>
 * <pre>{@code
 * S2UploadQueue queue = S2UploadQueue.builder(fileManager).concurrency(8).build();   // one per application
 * queue.submit(multipartFile.getInputStream(), "/upload/2026", storedName)
 *      .whenComplete((size, error) -> repository.markUploaded(id, error == null));
 * }</pre>
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * {@link FileManager}로 파일을 백그라운드에서 정해진 동시 전송 수만큼만 올립니다. 업로드가 몰리거나 큰 파일을 일괄로 올려도
 * 웹 요청 스레드를 붙잡지 않고 파일 서버에 부담을 주지 않습니다.
 * <ul>
 * <li>{@link #submit}은 데이터를 먼저 로컬 임시 파일에 저장하고, 저장된 크기를 담을 {@link CompletableFuture}를 바로
 * 돌려줍니다. 호출 스레드는 전송을 기다리지 않습니다.</li>
 * <li>동시에 최대 {@code concurrency}건만 전송하고 나머지는 큐에서 기다립니다.</li>
 * <li>이미 {@code maxQueued}건을 받아 두었으면 {@code submit}은 기다리지 않고 바로 {@link RejectedExecutionException}으로
 * 거절합니다 (사용자에게 잠시 후 다시 시도하라고 안내).</li>
 * <li>실패한 전송은 점점 늘어나는 무작위 간격으로 다시 시도합니다. 같은 이름의 파일({@link S2FileExistsException})이나
 * 잘못된 경로는 다시 시도하지 않습니다.</li>
 * <li>큐는 메모리에만 있습니다. 애플리케이션이 멈출 때 끝나지 않은 업로드는 사라지고 임시 파일도 지워집니다. 중요하면 각 future
 * 의 결과를 (상태 컬럼 등에) 기록하십시오.</li>
 * </ul>
 */
public final class S2UploadQueue implements AutoCloseable {

    private static final S2Logger logger = S2LogManager.getLogger(S2UploadQueue.class);

    /* 임시 파일 이름 (남은 파일 정리 때 이 형식만 지운다) */
    private static final String TEMP_PREFIX = "s2-upload-";
    private static final String TEMP_SUFFIX = ".part";
    /* 비정상 종료로 남은 임시 파일을 지우는 기준 (이보다 오래된 것만) */
    private static final Duration STALE_TEMP_AGE = Duration.ofDays(1);
    /* 재시도 간격 상한 */
    private static final long MAX_RETRY_DELAY_MILLIS = 60_000L;

    /* 재시도 시각만 맞추는 공용 데몬 스레드 1개 (실제 전송은 각 큐의 실행기에서) */
    private static final ScheduledThreadPoolExecutor RETRY_TIMER = createRetryTimer();

    private static ScheduledThreadPoolExecutor createRetryTimer() {
        var timer = new ScheduledThreadPoolExecutor(1, task -> {
            var thread = new Thread(task, "s2-upload-retry");
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    private final FileManager fileManager;
    private final int concurrency;
    private final int maxAttempts;
    private final long retryDelayMillis;
    private final Path tempDir;
    private final long maxFileBytes;
    private final long minFreeDiskBytes;

    private final ExecutorService executor;
    /* 받아 두고 아직 끝나지 않은 업로드 수 (실행 중 포함) */
    private final Semaphore slots;
    private final int maxQueued;
    private final Set<Job> jobs = ConcurrentHashMap.newKeySet();
    private volatile boolean closed = false;

    private S2UploadQueue(Builder builder) {
        this.fileManager = builder.fileManager;
        this.concurrency = builder.concurrency;
        this.maxAttempts = builder.maxAttempts;
        this.retryDelayMillis = builder.retryDelay.toMillis();
        this.maxFileBytes = builder.maxFileBytes;
        this.minFreeDiskBytes = builder.minFreeDiskBytes;
        this.maxQueued = builder.maxQueued;
        this.slots = new Semaphore(builder.maxQueued);
        try {
            this.tempDir = Files.createDirectories(builder.tempDir);
        } catch (IOException e) {
            throw new S2RuntimeException("Cannot create the upload temp directory: " + builder.tempDir, e);
        }
        deleteStaleTempFiles();
        // 실행 중인 전송 수만 제한한다. 대기 수는 slots 로 직접 세어, 가득 차면 막지 않고 바로 거절한다
        this.executor = S2ThreadUtil.newExecutor(concurrency);
    }

    /**
     * Starts building a queue for the given file manager.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 주어진 파일 관리자를 쓰는 큐를 만들기 시작합니다.
     * @param fileManager File manager that stores the files (local or SFTP) | 파일을 저장할 파일 관리자 (로컬 또는 SFTP)
     * @return Builder | 빌더
     */
    public static Builder builder(FileManager fileManager) {
        return new Builder(fileManager);
    }

    /**
     * Queues an upload that refuses to overwrite an existing file.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 같은 이름의 파일을 덮어쓰지 않는 업로드를 큐에 넣습니다.
     * @param data     Data to store; read to the end and closed by this method | 저장할 데이터 (이 메서드가 끝까지 읽고 닫음)
     * @param savePath Directory to store in | 저장 경로
     * @param saveName File name, which cannot leave {@code savePath} | 파일명 (저장 경로를 벗어날 수 없음)
     * @return Future completed with the stored size in bytes, or with the failure | 저장된 크기(바이트)나 실패로 끝나는 future
     * @throws RejectedExecutionException The queue is full or closed | 큐가 가득 찼거나 닫혔을 때
     * @throws S2RuntimeException         The path is invalid, the file is too large, the disk is nearly full, or the
     *                                    data cannot be read | 경로가 잘못됐거나, 파일이 너무 크거나, 디스크가 거의 찼거나,
     *                                    데이터를 읽지 못했을 때
     */
    public CompletableFuture<Long> submit(InputStream data, String savePath, String saveName) {
        return submit(data, savePath, saveName, false);
    }

    /**
     * Queues an upload.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 업로드를 큐에 넣습니다.
     * @param data      Data to store; read to the end and closed by this method | 저장할 데이터 (이 메서드가 끝까지 읽고 닫음)
     * @param savePath  Directory to store in | 저장 경로
     * @param saveName  File name, which cannot leave {@code savePath} | 파일명 (저장 경로를 벗어날 수 없음)
     * @param overwrite true to replace an existing file | true 면 같은 이름의 파일을 덮어씀
     * @return Future completed with the stored size in bytes, or with the failure | 저장된 크기(바이트)나 실패로 끝나는 future
     * @throws RejectedExecutionException The queue is full or closed | 큐가 가득 찼거나 닫혔을 때
     * @throws S2RuntimeException         The path is invalid, the file is too large, the disk is nearly full, or the
     *                                    data cannot be read | 경로가 잘못됐거나, 파일이 너무 크거나, 디스크가 거의 찼거나,
     *                                    데이터를 읽지 못했을 때
     */
    public CompletableFuture<Long> submit(InputStream data, String savePath, String saveName, boolean overwrite) {
        Objects.requireNonNull(data, "data");
        try {
            checkPath(savePath, saveName);
            acquireSlot();
            Path temp = null;
            try {
                checkFreeDisk();
                temp = Files.createTempFile(tempDir, TEMP_PREFIX, TEMP_SUFFIX);
                spool(data, temp);
                return enqueue(new Job(temp, true, savePath, saveName, overwrite));
            } catch (IOException | RuntimeException e) {
                slots.release();
                if (temp != null) {
                    S2FileUtil.deleteQuietly(temp);
                }
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new S2RuntimeException("Cannot save the upload to a temporary file: " + saveName, e);
            }
        } finally {
            // 닫다 난 오류로 실패를 알리면 안 된다 (이미 큐에 넣었을 수 있음): 조용히 닫는다
            S2StreamUtil.closeStream(data);
        }
    }

    /**
     * Queues an upload of a file that is already on the local disk, without copying it. The file must stay in place
     * until the future completes.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 이미 로컬 디스크에 있는 파일을 복사하지 않고 큐에 넣습니다. future 가 끝날 때까지 파일을 지우거나 옮기면 안 됩니다.
     * @param source             Local file to upload | 올릴 로컬 파일
     * @param savePath           Directory to store in | 저장 경로
     * @param saveName           File name, which cannot leave {@code savePath} | 파일명 (저장 경로를 벗어날 수 없음)
     * @param overwrite          true to replace an existing file | true 면 같은 이름의 파일을 덮어씀
     * @param deleteWhenFinished true to delete {@code source} when the upload ends (success or failure) | true 면 업로드가
     *                           끝날 때(성공·실패 모두) {@code source}를 지움
     * @return Future completed with the stored size in bytes, or with the failure | 저장된 크기(바이트)나 실패로 끝나는 future
     * @throws RejectedExecutionException The queue is full or closed | 큐가 가득 찼거나 닫혔을 때
     * @throws S2RuntimeException         The path is invalid or {@code source} is not a readable file | 경로가 잘못됐거나
     *                                    {@code source}가 읽을 수 있는 파일이 아닐 때
     */
    public CompletableFuture<Long> submit(Path source, String savePath, String saveName, boolean overwrite,
            boolean deleteWhenFinished) {
        Objects.requireNonNull(source, "source");
        if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new S2RuntimeException("Not a readable file: " + source);
        }
        checkPath(savePath, saveName);
        acquireSlot();
        try {
            return enqueue(new Job(source, deleteWhenFinished, savePath, saveName, overwrite));
        } catch (RuntimeException e) {
            slots.release();
            throw e;
        }
    }

    /**
     * Number of accepted uploads that have not finished yet, including running ones.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 받아 두고 아직 끝나지 않은 업로드 수 (전송 중 포함).
     * @return Unfinished uploads | 끝나지 않은 업로드 수
     */
    public int getQueuedCount() {
        return maxQueued - slots.availablePermits();
    }

    /**
     * Stops accepting uploads and waits up to 30 seconds for the queued ones to finish; the rest are cancelled.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 새 업로드를 받지 않고, 큐에 있는 업로드가 끝나기를 최대 30초 기다립니다. 남은 것은 취소합니다.
     */
    @Override
    public void close() {
        close(Duration.ofSeconds(30));
    }

    /**
     * Stops accepting uploads and waits for the queued ones to finish; uploads still unfinished after
     * {@code timeout} are cancelled (their futures fail with {@link CancellationException}).
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 새 업로드를 받지 않고 큐에 있는 업로드가 끝나기를 기다립니다. {@code timeout} 뒤에도 끝나지 않은 업로드는 취소합니다
     * (future 는 {@link CancellationException}으로 실패).
     * @param timeout Time to wait | 기다릴 시간
     */
    public void close(Duration timeout) {
        closed = true;
        var deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!jobs.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdownNow();
        for (var job : jobs) {
            finish(job, null, new CancellationException("Upload queue closed before the upload finished: " + job.saveName));
        }
    }

    /* 큐에 넣는다. 실행기가 받지 않으면 예외로 알린다 (슬롯·임시 파일 정리는 호출 측이 한다) */
    private CompletableFuture<Long> enqueue(Job job) {
        if (closed) {
            throw new RejectedExecutionException("Upload queue is closed");
        }
        jobs.add(job);
        try {
            executor.execute(() -> run(job));
        } catch (RejectedExecutionException e) {
            jobs.remove(job);
            throw e;
        }
        return job.future;
    }

    private void run(Job job) {
        if (job.future.isDone()) {
            // 호출 측이 future 를 취소함: 전송하지 않고 자리와 임시 파일만 정리한다
            finish(job, null, new CancellationException("Upload cancelled: " + job.saveName));
            return;
        }
        job.attempt++;
        try (var in = Files.newInputStream(job.source)) {
            var size = fileManager.writeFile(in, job.savePath, job.saveName, job.overwrite);
            finish(job, size, null);
        } catch (Exception e) {
            if (closed || job.attempt >= maxAttempts || !isRetryable(e)) {
                logger.error("Upload failed ({} attempt(s)): {}/{}", job.attempt, job.savePath, job.saveName, e);
                finish(job, null, e);
                return;
            }
            var delay = retryDelay(job.attempt);
            logger.warn("Upload failed, retrying in {}ms (attempt {}/{}): {}/{} - {}", delay, job.attempt, maxAttempts,
                    job.savePath, job.saveName, e.toString());
            try {
                RETRY_TIMER.schedule(() -> resubmit(job), delay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException rejected) {
                finish(job, null, e);
            }
        }
    }

    private void resubmit(Job job) {
        try {
            executor.execute(() -> run(job));
        } catch (RejectedExecutionException e) {
            finish(job, null, new CancellationException("Upload queue closed before the upload finished: " + job.saveName));
        }
    }

    /* 업로드를 끝낸다 (한 번만): 결과를 알리고 슬롯을 돌려주고 임시 파일을 지운다 */
    private void finish(Job job, Long size, Throwable error) {
        if (!job.finished.compareAndSet(false, true)) {
            return;
        }
        jobs.remove(job);
        slots.release();
        if (job.deleteSource) {
            S2FileUtil.deleteQuietly(job.source);
        }
        if (error == null) {
            job.future.complete(size);
        } else {
            job.future.completeExceptionally(error);
        }
    }

    /*
     * 같은 이름의 파일(덮어쓰지 않을 때)과 잘못된 인자는 다시 해도 같으므로 재시도하지 않는다. 그 밖(연결 끊김, 풀 대기 시간 초과,
     * 서버 일시 오류 등)은 재시도한다. 단, 앞 시도의 전송이 서버에 다 들어간 뒤 응답만 실패했다면 다음 시도가 "이미 존재"로 끝날 수
     * 있다 (덮어쓰지 않는 업로드에서 드묾).
     */
    private static boolean isRetryable(Throwable error) {
        for (var t = error; t != null; t = t.getCause()) {
            if (t instanceof S2FileExistsException || t instanceof FileAlreadyExistsException
                    || t instanceof IllegalArgumentException) {
                return false;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return true;
    }

    /* 시도할수록 두 배로 늘리고(상한 1분) ±50% 흩뜨려, 실패한 업로드들이 같은 순간에 다시 몰리지 않게 한다 */
    private long retryDelay(int attempt) {
        var base = Math.min(MAX_RETRY_DELAY_MILLIS, retryDelayMillis << Math.min(attempt - 1, 16));
        return Math.max(1L, base / 2 + ThreadLocalRandom.current().nextLong(Math.max(1L, base)));
    }

    private void acquireSlot() {
        if (closed) {
            throw new RejectedExecutionException("Upload queue is closed");
        }
        if (!slots.tryAcquire()) {
            throw new RejectedExecutionException("Upload queue is full (" + maxQueued + " uploads waiting); try again later");
        }
    }

    /* 큐에 넣기 전에 경로를 검사한다 (잘못된 경로가 큐에서 실패하도록 두지 않음) */
    private void checkPath(String savePath, String saveName) {
        if (savePath == null || savePath.isBlank()) {
            throw new IllegalArgumentException("savePath is required");
        }
        if (fileManager.isRemote()) {
            S2FileUtil.resolveRemoteWithin(savePath, saveName);
        } else {
            S2FileUtil.resolveWithin(Paths.get(savePath), saveName);
        }
    }

    /* 임시 파일로 디스크가 가득 차 애플리케이션 서버가 멈추지 않도록 여유 공간을 확인한다 */
    private void checkFreeDisk() throws IOException {
        if (minFreeDiskBytes <= 0) {
            return;
        }
        var usable = Files.getFileStore(tempDir).getUsableSpace();
        if (usable < minFreeDiskBytes) {
            throw new RejectedExecutionException("Not enough disk space for upload temp files: " + usable + " bytes free, "
                    + minFreeDiskBytes + " required");
        }
    }

    private void spool(InputStream in, Path temp) throws IOException {
        try (var out = Files.newOutputStream(temp)) {
            var buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (maxFileBytes > 0 && total > maxFileBytes) {
                    throw new S2RuntimeException("Upload is larger than " + maxFileBytes + " bytes");
                }
                out.write(buffer, 0, read);
            }
        }
    }

    /* 비정상 종료로 남은 오래된 임시 파일을 지운다 (지금 다른 큐가 쓰는 파일은 지우지 않도록 하루 지난 것만) */
    private void deleteStaleTempFiles() {
        var cutoff = Instant.now().minus(STALE_TEMP_AGE);
        try (var files = Files.newDirectoryStream(tempDir, TEMP_PREFIX + "*" + TEMP_SUFFIX)) {
            for (var file : files) {
                try {
                    if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException e) {
                    logger.debug("Cannot delete a stale upload temp file: {}", file);
                }
            }
        } catch (IOException e) {
            logger.debug("Cannot list the upload temp directory: {}", tempDir);
        }
    }

    private static final class Job {
        final Path source;
        final boolean deleteSource;
        final String savePath;
        final String saveName;
        final boolean overwrite;
        final CompletableFuture<Long> future = new CompletableFuture<>();
        final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
        volatile int attempt = 0;

        Job(Path source, boolean deleteSource, String savePath, String saveName, boolean overwrite) {
            this.source = source;
            this.deleteSource = deleteSource;
            this.savePath = savePath;
            this.saveName = saveName;
            this.overwrite = overwrite;
        }
    }

    /**
     * Builder for {@link S2UploadQueue}.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * {@link S2UploadQueue} 빌더.
     */
    public static final class Builder {
        private final FileManager fileManager;
        private int concurrency = 8;
        private int maxQueued = 1000;
        private int maxAttempts = 3;
        private Duration retryDelay = Duration.ofSeconds(2);
        private Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "s2-upload-queue");
        private long maxFileBytes = 0;
        private long minFreeDiskBytes = 1024L * 1024 * 1024;

        private Builder(FileManager fileManager) {
            this.fileManager = Objects.requireNonNull(fileManager, "fileManager");
        }

        /**
         * Parallel transfers (default 8, sized for a typical file server with a 1Gbps link). Keep it well below the SFTP pool size so other
         * requests still get sessions. Raise it when transfers are small and the server has spare CPU and bandwidth; lower
         * it when large files saturate the link (more parallel transfers only make each one slower).
         * <p>
         * <b>[한국어 설명]</b>
         * </p>
         * 동시 전송 수 (기본 8, 1Gbps 일반 파일 서버 기준). 다른 요청도 세션을 얻을 수 있도록 SFTP 풀 크기보다 충분히 작게 둡니다. 작은
         * 파일이 많고 서버 CPU·대역폭에 여유가 있으면 올리고, 큰 파일로 회선이 꽉 차면 낮춥니다 (동시 전송을 늘려도 한 건씩
         * 느려질 뿐입니다).
         * @param concurrency Parallel transfers (1 or more) | 동시 전송 수 (1 이상)
         * @return This builder | 이 빌더
         */
        public Builder concurrency(int concurrency) {
            if (concurrency < 1) {
                throw new IllegalArgumentException("concurrency must be at least 1: " + concurrency);
            }
            this.concurrency = concurrency;
            return this;
        }

        /**
         * Accepted uploads not finished yet, including running ones (default 1000); more are rejected at once. Each waits
         * as a temporary file, so keep {@code maxQueued × typical file size} within the temp disk.
         * <p>
         * <b>[한국어 설명]</b>
         * </p>
         * 받아 둘 수 있는 끝나지 않은 업로드 수 (실행 중 포함, 기본 1000). 넘으면 바로 거절합니다. 대기 중인 업로드는 임시
         * 파일로 있으므로 {@code maxQueued × 보통 파일 크기}가 임시 디스크 안에 들어가게 둡니다.
         * @param maxQueued Limit (1 or more) | 상한 (1 이상)
         * @return This builder | 이 빌더
         */
        public Builder maxQueued(int maxQueued) {
            if (maxQueued < 1) {
                throw new IllegalArgumentException("maxQueued must be at least 1: " + maxQueued);
            }
            this.maxQueued = maxQueued;
            return this;
        }

        /**
         * Attempts per upload, including the first (default 3).
         * <p>
         * <b>[한국어 설명]</b>
         * </p>
         * 업로드당 시도 횟수 (첫 시도 포함, 기본 3).
         * @param maxAttempts Attempts (1 or more) | 시도 횟수 (1 이상)
         * @return This builder | 이 빌더
         */
        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Delay before the first retry (default 2 seconds); it doubles on each retry, up to one minute, randomized
         * by ±50%.
         * <p>
         * <b>[한국어 설명]</b>
         * </p>
         * 첫 재시도 전 대기 시간 (기본 2초). 재시도마다 두 배(최대 1분)가 되며 ±50% 흩뜨립니다.
         * @param retryDelay Delay (positive) | 대기 시간 (양수)
         * @return This builder | 이 빌더
         */
        public Builder retryDelay(Duration retryDelay) {
            if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
                throw new IllegalArgumentException("retryDelay must be positive: " + retryDelay);
            }
            this.retryDelay = retryDelay;
            return this;
        }

        /**
         * Directory for temporary files (default {@code java.io.tmpdir/s2-upload-queue}). Files older than a day
         * left there by a crash are deleted when a queue is created.
         * <p>
         * <b>[한국어 설명]</b>
         * </p>
         * 임시 파일 폴더 (기본 {@code java.io.tmpdir/s2-upload-queue}). 비정상 종료로 남은 하루 지난 파일은 큐를 만들 때
         * 지웁니다.
         * @param tempDir Directory | 폴더
         * @return This builder | 이 빌더
         */
        public Builder tempDir(Path tempDir) {
            this.tempDir = Objects.requireNonNull(tempDir, "tempDir");
            return this;
        }

        /**
         * Largest upload accepted, in bytes (default 0: no limit).
         * <p>
         * <b>[한국어 설명]</b>
         * </p>
         * 받을 수 있는 최대 업로드 크기(바이트, 기본 0: 제한 없음).
         * @param maxFileBytes Limit (0: none) | 상한 (0: 없음)
         * @return This builder | 이 빌더
         */
        public Builder maxFileBytes(long maxFileBytes) {
            this.maxFileBytes = Math.max(0, maxFileBytes);
            return this;
        }

        /**
         * Free space the temp directory must keep (default 1GB); uploads are rejected below it so temp files cannot
         * fill the disk.
         * <p>
         * <b>[한국어 설명]</b>
         * </p>
         * 임시 폴더에 남겨 둘 여유 공간 (기본 1GB). 이보다 적으면 업로드를 거절해 임시 파일이 디스크를 채우지 않게 합니다.
         * @param minFreeDiskBytes Free bytes to keep (0: no check) | 남길 바이트 (0: 확인 안 함)
         * @return This builder | 이 빌더
         */
        public Builder minFreeDiskBytes(long minFreeDiskBytes) {
            this.minFreeDiskBytes = Math.max(0, minFreeDiskBytes);
            return this;
        }

        /**
         * Creates the queue. Create one per application and {@link S2UploadQueue#close() close} it on shutdown.
         * <p>
         * <b>[한국어 설명]</b>
         * </p>
         * 큐를 만듭니다. 애플리케이션마다 하나를 만들고 종료할 때 {@link S2UploadQueue#close() 닫습니다}.
         * @return Queue | 큐
         */
        public S2UploadQueue build() {
            return new S2UploadQueue(this);
        }
    }

}
