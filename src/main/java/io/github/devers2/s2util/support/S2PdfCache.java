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
package io.github.devers2.s2util.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.github.devers2.s2util.core.S2ThreadUtil;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * Conversion result cache of {@link S2PdfUtil#merge}: the PDF made from a source that needs converting (document, HTML,
 * web page, image, text, SVG) is kept under the SHA-256 of everything that shapes it, and reused when the same source
 * comes again.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * {@link S2PdfUtil#merge}의 변환 결과 캐시. 변환이 필요한 소스(문서, HTML, 웹 페이지, 이미지, 텍스트, SVG)로 만든 PDF 를, 결과를 정하는 모든 것의
 * SHA-256 을 이름으로 저장해 두었다가 같은 소스가 다시 오면 재사용한다.
 * <ul>
 * <li>SHA-256: 여러 사용자가 함께 쓰는 캐시라, 같은 해시를 갖는 다른 파일을 만들어 남의 결과를 받아 보는 일이 없도록 위조할 수 없는 해시를 쓴다.</li>
 * <li>정리: 요청이 올 때 마지막 정리가 오늘 이전이면, 또는 크기 상한을 넘으면 백그라운드에서 한 번만 정리한다 (별도 배치 불필요). 보관 기간이 지난 것을
 * 지우고, 그래도 크면 오래 안 쓴 것부터 지운다.</li>
 * <li>디스크 여유 공간이 기준보다 적으면 저장하지 않는다 (변환은 그대로 됨).</li>
 * <li>폴더는 앱 실행 계정만 읽을 수 있게 만든다 (POSIX).</li>
 * </ul>
 */
final class S2PdfCache {

    private static final S2Logger logger = S2LogManager.getLogger(S2PdfCache.class);

    static final Path DEFAULT_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"), "s2-pdf-cache");
    /** 0 or less: half of the free-space reserve | 0 이하: 최소 여유 공간의 50% */
    static final long DEFAULT_MAX_BYTES = 0;
    static final Duration DEFAULT_MAX_AGE = Duration.ofDays(1);
    /** 0 or less: the smaller of 20% of the disk and 20GB | 0 이하: 디스크 용량의 20% 와 20GB 중 작은 값 */
    static final long DEFAULT_MIN_FREE_BYTES = 0;
    private static final long MIN_FREE_CAP = 20L * 1024 * 1024 * 1024;

    /** Changes with every application run; part of keys whose result depends on classpath resources | 앱 실행마다 바뀌는 값 */
    static final String RUN = UUID.randomUUID().toString();
    /** Library version; the run when unknown (development builds) | 라이브러리 버전 (모르면 실행 회차) */
    private static final String VERSION = version();
    private static final String FORMAT = "s2-pdf-cache-1";
    private static final String MARKER = ".last-cleanup";

    private static volatile Path directory = DEFAULT_DIRECTORY;
    private static volatile long maxBytes = DEFAULT_MAX_BYTES;
    private static volatile Duration maxAge = DEFAULT_MAX_AGE;
    private static volatile long minFreeBytes = DEFAULT_MIN_FREE_BYTES;

    private static final AtomicBoolean cleaning = new AtomicBoolean();
    /** Size since the last cleanup, -1 when unknown | 마지막 정리 이후 크기 (모르면 -1) */
    private static final AtomicLong knownSize = new AtomicLong(-1);

    private S2PdfCache() {
    }

    private static String version() {
        var version = S2PdfCache.class.getPackage() != null ? S2PdfCache.class.getPackage().getImplementationVersion() : null;
        return version != null ? version : "run-" + RUN;
    }

    static void configure(Path directory, long maxBytes, Duration maxAge, long minFreeBytes) {
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("캐시 설정이 올바르지 않습니다: 최대 크기 " + maxBytes + ", 보관 기간 " + maxAge
                    + ", 최소 여유 공간 " + minFreeBytes);
        }
        S2PdfCache.directory = directory != null ? directory : DEFAULT_DIRECTORY;
        S2PdfCache.maxBytes = maxBytes;
        S2PdfCache.maxAge = maxAge;
        S2PdfCache.minFreeBytes = minFreeBytes;
        knownSize.set(-1);
    }

    static Path directory() {
        return directory;
    }

    /** Free space to keep on the cache's disk | 캐시 디스크에 남길 여유 공간 */
    private static long minFree(java.nio.file.FileStore store) throws IOException {
        return minFreeBytes > 0 ? minFreeBytes : Math.min(MIN_FREE_CAP, store.getTotalSpace() / 5);
    }

    /** Size limit of the cache: as configured, or half of the free-space reserve | 캐시 최대 크기: 지정값 또는 최소 여유 공간의 50% */
    static long maxBytes() {
        if (maxBytes > 0) {
            return maxBytes;
        }
        try {
            var root = Files.isDirectory(directory) ? directory : directory.getParent();
            return Math.max(1, minFree(Files.getFileStore(root)) / 2);
        } catch (IOException | RuntimeException e) {
            return 1024L * 1024 * 1024;
        }
    }

    // ------------------------------------------------------------------ keys

    /** Builds a key from length-prefixed parts, so ("ab", "c") and ("a", "bc") differ | 길이를 앞에 붙여 부분을 이어 키를 만든다 */
    static final class Key {
        private final MessageDigest digest;

        private Key(String kind) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            add(FORMAT).add(VERSION).add(kind);
        }

        Key add(String value) {
            return add(value == null ? null : value.getBytes(StandardCharsets.UTF_8));
        }

        Key add(byte[] value) {
            if (value == null) {
                digest.update((byte) 0);
                return this;
            }
            digest.update((byte) 1);
            digest.update(longBytes(value.length));
            digest.update(value);
            return this;
        }

        Key addFile(Path file) throws IOException {
            digest.update((byte) 2);
            digest.update(longBytes(Files.size(file)));
            try (var in = Files.newInputStream(file)) {
                var buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return this;
        }

        String hex() {
            return HexFormat.of().formatHex(digest.digest());
        }

        private static byte[] longBytes(long value) {
            var bytes = new byte[8];
            for (int i = 7; i >= 0; i--) {
                bytes[i] = (byte) value;
                value >>>= 8;
            }
            return bytes;
        }
    }

    static Key key(String kind) {
        return new Key(kind);
    }

    /**
     * Identity of a command such as s2-soffice: its path and, for a small wrapper script, its content (which names the
     * container image), so reinstalling or upgrading the converter changes keys | s2-soffice 같은 명령의 정체: 경로와 (작은 래퍼면)
     * 내용. 변환기를 다시 설치·업그레이드하면 키가 바뀐다
     */
    static String commandIdentity(List<String> command) {
        if (command == null) {
            return "none";
        }
        var identity = new StringBuilder(String.join(" ", command));
        try {
            var file = Path.of(command.get(0));
            if (Files.isRegularFile(file)) {
                var size = Files.size(file);
                if (size <= 1024 * 1024) {
                    identity.append('|').append(HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
                } else {
                    identity.append('|').append(size).append('|').append(Files.getLastModifiedTime(file).toMillis());
                }
            }
        } catch (IOException | RuntimeException | NoSuchAlgorithmException e) {
            identity.append("|unknown-").append(RUN);
        }
        return identity.toString();
    }

    // ---------------------------------------------------------- read, write

    private static Path entry(String key) {
        return directory.resolve(key.substring(0, 2)).resolve(key + ".pdf");
    }

    /**
     * Puts the cached PDF for {@code key} at {@code target}; false when there is none. A hard link (or copy) keeps the
     * file usable even if a cleanup removes the entry meanwhile | 캐시된 PDF 를 target 에 둔다. 없으면 false. 하드 링크(또는 복사)라서 그사이
     * 정리가 항목을 지워도 쓸 수 있다
     */
    static boolean restore(String key, Path target) {
        maybeCleanup();
        var entry = entry(key);
        try {
            if (!Files.isRegularFile(entry)) {
                return false;
            }
            // Past its age it is not served, even before a cleanup removes it | 보관 기간이 지난 것은 정리 전이라도 쓰지 않음
            if (Files.getLastModifiedTime(entry).toInstant().isBefore(Instant.now().minus(maxAge))) {
                Files.deleteIfExists(entry);
                return false;
            }
            Files.deleteIfExists(target);
            try {
                Files.createLink(target, entry);
            } catch (IOException | UnsupportedOperationException e) {
                Files.copy(entry, target, StandardCopyOption.REPLACE_EXISTING);
            }
            // Recently used entries are removed last | 최근에 쓴 항목은 나중에 지움
            Files.setLastModifiedTime(entry, FileTime.from(Instant.now()));
            return true;
        } catch (IOException | RuntimeException e) {
            logger.warn("변환 결과 캐시를 읽지 못해 다시 변환합니다: {} ({})", entry, e.getMessage());
            return false;
        }
    }

    /** Stores a converted PDF; skipped (with a log) when space is short or writing fails | 변환한 PDF 를 저장 (공간 부족·실패 시 건너뜀) */
    static void store(String key, Path pdf) {
        var entry = entry(key);
        Path partial = null;
        try {
            var parent = Files.createDirectories(entry.getParent());
            prepareRoot();
            var size = Files.size(pdf);
            var store = Files.getFileStore(parent);
            var free = store.getUsableSpace();
            var required = minFree(store);
            if (free - size < required) {
                logger.info("디스크 여유 공간({} bytes)이 기준({} bytes)보다 적어 변환 결과를 캐시하지 않습니다.", free, required);
                return;
            }
            // Written aside, then renamed, so concurrent requests never see a partial file | 다른 이름으로 쓴 뒤 바꿔 반쯤 쓴 파일이 보이지 않게 함
            partial = Files.createTempFile(parent, key, ".part");
            Files.copy(pdf, partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, entry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            partial = null;
            if (knownSize.get() >= 0 && knownSize.addAndGet(size) > maxBytes()) {
                startCleanup();
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("변환 결과를 캐시하지 못했습니다: {} ({})", entry, e.getMessage());
        } finally {
            if (partial != null) {
                try {
                    Files.deleteIfExists(partial);
                } catch (IOException ignored) {
                    // Removed by the next cleanup | 다음 정리 때 지워짐
                }
            }
        }
    }

    /** The cache folder, readable only by the application account | 앱 실행 계정만 읽을 수 있는 캐시 폴더 */
    private static void prepareRoot() throws IOException {
        var root = directory;
        if (root.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            try {
                Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
                try (var dirs = Files.newDirectoryStream(root, Files::isDirectory)) {
                    for (var dir : dirs) {
                        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
                    }
                }
            } catch (IOException | UnsupportedOperationException e) {
                logger.warn("캐시 폴더 권한을 설정하지 못했습니다: {} ({})", root, e.getMessage());
            }
        }
    }

    // --------------------------------------------------------------- cleanup

    /** Starts a cleanup when the last one was before today | 마지막 정리가 오늘 이전이면 정리 시작 */
    static void maybeCleanup() {
        var marker = directory.resolve(MARKER);
        try {
            if (Files.isRegularFile(marker)) {
                var last = LocalDate.ofInstant(Files.getLastModifiedTime(marker).toInstant(), ZoneId.systemDefault());
                if (!last.isBefore(LocalDate.now()) && knownSize.get() >= 0 && knownSize.get() <= maxBytes()) {
                    return;
                }
            } else if (!Files.isDirectory(directory)) {
                return;
            }
        } catch (IOException | RuntimeException e) {
            return;
        }
        startCleanup();
    }

    /** One cleanup at a time, in the background, so requests never wait | 한 번에 하나, 백그라운드에서 (요청은 기다리지 않음) */
    private static void startCleanup() {
        if (!cleaning.compareAndSet(false, true)) {
            return;
        }
        try {
            S2ThreadUtil.getCommonExecutor().execute(() -> {
                try {
                    cleanup();
                } finally {
                    cleaning.set(false);
                }
            });
        } catch (RuntimeException e) {
            cleaning.set(false);
        }
    }

    /** Removes expired entries, then the least recently used ones while over the size limit | 기간이 지난 것, 그다음 크기 상한까지 오래 안 쓴 것부터 */
    static void cleanup() {
        var root = directory;
        if (!Files.isDirectory(root)) {
            return;
        }
        try {
            // Marked first, so other requests and other servers sharing the folder skip | 먼저 표시해 다른 요청·서버가 건너뛰게 함
            var marker = root.resolve(MARKER);
            try {
                Files.createFile(marker);
            } catch (FileAlreadyExistsException e) {
                Files.setLastModifiedTime(marker, FileTime.from(Instant.now()));
            }
            var cutoff = Instant.now().minus(maxAge);
            var entries = new ArrayList<Path>();
            var total = 0L;
            var removed = 0;
            try (var walk = Files.walk(root, 2)) {
                for (var file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    var name = file.getFileName().toString();
                    if (name.equals(MARKER)) {
                        continue;
                    }
                    var modified = Files.getLastModifiedTime(file).toInstant();
                    if (modified.isBefore(cutoff) || (name.endsWith(".part") && modified.isBefore(Instant.now().minusSeconds(3600)))) {
                        removed += Files.deleteIfExists(file) ? 1 : 0;
                    } else if (name.endsWith(".pdf")) {
                        entries.add(file);
                        total += Files.size(file);
                    }
                }
            }
            var limit = maxBytes();
            if (total > limit) {
                // Down to 80% so the next stores do not start another cleanup at once | 80% 까지 줄여 곧바로 다시 정리하지 않게 함
                entries.sort(Comparator.comparing(S2PdfCache::modifiedOrEpoch));
                var target = limit * 8 / 10;
                for (var file : entries) {
                    if (total <= target) {
                        break;
                    }
                    var size = Files.size(file);
                    if (Files.deleteIfExists(file)) {
                        total -= size;
                        removed++;
                    }
                }
            }
            knownSize.set(total);
            if (removed > 0) {
                logger.info("변환 결과 캐시 정리: {}개 삭제, 남은 크기 {} bytes", removed, total);
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("변환 결과 캐시를 정리하지 못했습니다: {} ({})", root, e.getMessage());
        }
    }

    private static Instant modifiedOrEpoch(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    /** Deletes every entry | 모든 항목 삭제 */
    static void clear() throws IOException {
        var root = directory;
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (var path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                if (!path.equals(root)) {
                    Files.deleteIfExists(path);
                }
            }
        }
        knownSize.set(0);
    }
}
