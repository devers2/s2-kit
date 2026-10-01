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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.github.devers2.s2util.core.S2StringUtil;
import io.github.devers2.s2util.core.S2Util;
import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2020. 07. 08.
 */
public class S2FileUtil {

    private static final S2Logger logger = S2LogManager.getLogger(S2FileUtil.class);

    /**
     * 파일 시스템 경로 및 URL/URI 를 결합하는 범용 메서드
     *
     * @param paths 결합할 경로들
     * @return 결합된 경로 문자열
     * @details
     *          <dl>
     *          <dd>경로 사이의 슬래시를 적절히 처리하고, URL 스키마를 보존합니다.</dd>
     *          <dd>모든 백슬래시(\)는 슬래시(/)로 변환됩니다.</dd>
     *          <dd>file:// 프로토콜의 경우 특수한 규칙을 적용합니다:</dd>
     *          <dd>- file:// + /root + dir → file:///root/dir</dd>
     *          <dd>- file:// + root + dir → file:///root/dir</dd>
     *          <dd>- file://dir + dir2 → file:///dir/dir2 (절대 경로 강제)</dd>
     *          <dd>- file:/ → file:///</dd>
     *          <dd>모든 URI 스키마(http://, https://, ftp://, sftp:// 등)는 표준 형식을 유지:</dd>
     *          <dd>- http:/// → http://</dd>
     *          <dd>- http:/ → http://</dd>
     *          <dd>URI 스키마가 없는 경우, 모든 슬래시(/)와 백슬래시(\) 혼합(//, \\, \/)을 단일 슬래시(/)로 정규화합니다.</dd>
     *          </dl>
     */
    public static String joinPaths(String... paths) {
        if (paths == null || paths.length == 0)
            return "";

        // Work on a copy so the caller's array is not modified | 호출자의 배열을 바꾸지 않도록 복사본 사용
        paths = paths.clone();
        // 초기 변환
        for (int i = 0; i < paths.length; i++) {
            paths[i] = (paths[i] == null) ? "" : S2StringUtil.replaceChars(paths[i], "/", '\\');
        }

        var firstPath = paths[0];
        var query = extractQueryAndFragment(paths[paths.length - 1], paths);

        // 스키마 및 authority 파싱
        String[] schemes = { "http://", "https://", "ftp://", "sftp://", "file://" };
        String[] singleSlash = { "http:/", "https:/", "ftp:/", "sftp:/", "file:/" };
        String[] tripleSlash = { "http:///", "https:///", "ftp:///", "sftp:///", "file:///" };

        var scheme = "";
        var pathPart = firstPath;
        var isFileProtocol = false;

        for (int i = 0; i < schemes.length; i++) {
            if (firstPath.startsWith(schemes[i])) {
                scheme = schemes[i];
                pathPart = firstPath.substring(schemes[i].length());
                isFileProtocol = schemes[i].equals("file://");
                break;
            } else if (firstPath.startsWith(singleSlash[i])) {
                scheme = schemes[i];
                pathPart = firstPath.substring(singleSlash[i].length());
                isFileProtocol = schemes[i].equals("file://");
                break;
            } else if (firstPath.startsWith(tripleSlash[i])) {
                scheme = schemes[i];
                pathPart = firstPath.substring(tripleSlash[i].length());
                isFileProtocol = schemes[i].equals("file://");
                break;
            }
        }

        var authority = "";
        if (!scheme.isBlank()) {
            var authorityEnd = pathPart.indexOf('/');
            if (authorityEnd >= 0) {
                authority = S2StringUtil.replaceAll(pathPart.substring(0, authorityEnd), "[/]+", "");
                pathPart = pathPart.substring(authorityEnd).replaceFirst("^/+", "");
            } else {
                authority = S2StringUtil.replaceAll(pathPart, "[/]+", "");
                pathPart = "";
            }
        }

        // 경로 결합
        List<String> pathParts = new ArrayList<>();
        if (!pathPart.isBlank())
            pathParts.add(pathPart);
        for (int i = 1; i < paths.length; i++) {
            if (!paths[i].isBlank())
                pathParts.add(paths[i]);
        }

        var path = joinAndNormalizePath(pathParts);

        // 결과 구성
        if (isFileProtocol || firstPath.startsWith("file:/") || firstPath.startsWith("file:///")) {
            return path.isBlank() ? "file://" : "file:///" + path.replaceFirst("^/+", "") + query;
        } else if (scheme.isBlank()) {
            return path + query;
        } else {
            return (authority.isBlank() ? scheme : scheme + authority + "/") +
                    path.replaceFirst("^/+", "") + query;
        }
    }

    private static String extractQueryAndFragment(String lastPath, String[] paths) {
        var query = "";
        var fragment = "";
        var queryIndex = lastPath.indexOf('?');
        if (queryIndex >= 0) {
            var fragmentIndex = lastPath.indexOf('#', queryIndex);
            if (fragmentIndex >= 0) {
                fragment = lastPath.substring(fragmentIndex);
                query = lastPath.substring(queryIndex, fragmentIndex);
                paths[paths.length - 1] = lastPath.substring(0, queryIndex);
            } else {
                query = lastPath.substring(queryIndex);
                paths[paths.length - 1] = lastPath.substring(0, queryIndex);
            }
        } else {
            var fragmentIndex = lastPath.indexOf('#');
            if (fragmentIndex >= 0) {
                fragment = lastPath.substring(fragmentIndex);
                paths[paths.length - 1] = lastPath.substring(0, fragmentIndex);
            }
        }
        return query + fragment;
    }

    private static String joinAndNormalizePath(List<String> pathParts) {
        if (pathParts == null || pathParts.isEmpty()) {
            return "";
        }

        // 1. 첫 번째 요소 처리
        var firstPart = S2StringUtil.replaceChars(pathParts.get(0), "/", '\\').trim();

        if (firstPart.matches("^[\\s/]*$")) {
            // 첫 번째 요소가 슬래시나 공백만으로 이루어져 있다면, 슬래시가 있을 경우에만 유지하고 아니면 제외한다.
            firstPart = firstPart.contains("/") ? "/" : "";
        } else {
            // 유효한 요소라면 후행 '/' 제거
            firstPart = S2StringUtil.replaceAll(firstPart, "/+$", "");
        }

        // 2. 나머지 요소 처리 (skip(1) 사용)
        var joinedRest = pathParts.stream()
                .skip(1)
                .filter(Objects::nonNull)
                .map(p -> S2StringUtil.replaceChars(p, "/", '\\').trim())
                .filter(s -> !s.isBlank() && !s.matches("^[\\s/]*$")) // 공백이나 '/'만 남은 무의미한 요소를 제거
                .map(p -> S2StringUtil.replaceAll(p, "/+$", "")) // 나머지 유효한 요소에 대해 후행 '/' 제거
                .collect(Collectors.joining("/"));

        var finalPath = firstPart + (!firstPart.isBlank() && !joinedRest.isBlank() ? "/" + joinedRest : "");
        return S2StringUtil.replaceAll(finalPath, "[/]+", "/");
    }

    /**
     * 파일 시스템의 디렉토리를 만든다. 상위 디렉토리도 함께 만든다.
     *
     * @param path 생성할 경로(문자열)
     * @return 새로 만들었으면 true, 이미 있었으면 false
     * @throws IllegalArgumentException 경로가 비었을 때
     * @throws S2RuntimeException       만들지 못했을 때 (권한, 같은 이름의 파일 등, 원인 포함)
     */
    public static boolean makeDirectory(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("디렉토리 경로가 비었습니다.");
        }
        return makeDirectory(Paths.get(path));
    }

    /**
     * 파일 시스템의 디렉토리를 만든다. 상위 디렉토리도 함께 만든다.
     *
     * @param path 생성할 경로(Path)
     * @return 새로 만들었으면 true, 이미 있었으면 false
     * @throws NullPointerException path 가 null 일 때
     * @throws S2RuntimeException   만들지 못했을 때 (권한, 같은 이름의 파일 등, 원인 포함)
     */
    public static boolean makeDirectory(Path path) {
        Objects.requireNonNull(path, "path");
        if (Files.isDirectory(path)) {
            return false;
        }
        try {
            Files.createDirectories(path);
            return true;
        } catch (IOException e) {
            throw new S2RuntimeException("디렉토리 생성 실패: " + path + " (" + e + ")", e);
        }
    }

    /**
     * 실제 파일의 내용을 문자열로 읽어온다.
     *
     * @param filePath 파일 경로
     * @return 내용
     * @throws IOException IOException
     */
    public static String readFile(String filePath) throws IOException {
        return Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
    }

    /**
     * 실제 파일에 해당 문자열을 작성한다.
     *
     * @param filePath 파일 경로
     * @param content  내용
     * @return 작성 성공 여부
     * @throws IOException IOException
     */
    public static boolean writeFile(String filePath, String content) throws IOException {
        return writeFile(filePath, content, false);
    }

    /**
     * 실제 파일에 해당 문자열을 작성한다.
     *
     * @param filePath 파일 경로
     * @param content  내용
     * @param isAppend (true: 기존 내용에 추가)
     * @return 결과
     * @throws IOException IOException
     */
    public static boolean writeFile(String filePath, String content, boolean isAppend) throws IOException {
        var openOptions = isAppend
                ? new StandardOpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.APPEND }
                : new StandardOpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE };
        Files.writeString(Paths.get(filePath), content, StandardCharsets.UTF_8, openOptions);
        return true;
    }

    /**
     * 파일 또는 디렉토리를 지정한 위치로 복사
     *
     * @param source    복사할 원본 파일 또는 디렉토리 경로
     * @param target    복사 대상 경로 (파일 또는 디렉토리)
     * @param overwrite true: 대상에 동일 이름이 존재할 경우 덮어쓰기, false: 이미 존재하면 예외 발생
     * @throws IOException 복사 중 I/O 오류, 권한 문제, 파일 시스템 오류 등이 발생할 경우
     * @details
     *          <dl>
     *          <dd>- source가 단일 파일이면 해당 파일을 target 위치로 복사</dd>
     *          <dd>- source가 디렉토리면 하위 모든 파일과 디렉토리를 재귀적으로 target 위치로 복사</dd>
     *          <dd>- overwrite가 true면 target에 동일 이름의 파일/디렉토리가 존재할 경우 덮어씀</dd>
     *          <dd>- 복사 시 파일의 속성(메타데이터)도 함께 복사</dd>
     *          </dl>
     * @apiNote source.html 파일을 target.html 파일로 sample 디렉토리에 복사(중복 파일 존재 시 덮어쓰기)
     *
     *          <pre>{@code
     * S2FileUtil.copy(Paths.get("c:/source.html"), Paths.get("c:/sample"), true);
     * }</pre>
     *
     * @see java.nio.file.Files#copy(Path, Path, CopyOption...)
     * @see java.nio.file.Files#walkFileTree(Path, java.nio.file.FileVisitor)
     */
    public static void copy(Path source, Path target, boolean overwrite) throws IOException {
        if (source == null || target == null || !Files.exists(source) || Files.isSameFile(source, target)) {
            return;
        }

        var parent = target.getParent();
        if (parent != null && Files.notExists(parent)) {
            // 대상의 부모 디렉토리 자동 생성(모든 상위 디렉토리)
            Files.createDirectories(parent);
        }

        // StandardCopyOption.COPY_ATTRIBUTES: 파일의 속성(메타데이터)까지 함께 복사, StandardCopyOption.REPLACE_EXISTING: 파일이 이미 존재할 때 덮어쓰기를 허용
        var options = overwrite
                ? new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING }
                : new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES };

        if (Files.isRegularFile(source)) {
            // 단일 파일 복사
            Files.copy(source, target, options);
        } else {
            // 디렉토리 복사 (재귀적)
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    var targetDir = target.resolve(source.relativize(dir));
                    try {
                        Files.createDirectories(targetDir);
                    } catch (FileAlreadyExistsException e) {
                        if (!Files.isDirectory(targetDir)) {
                            throw e;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, target.resolve(source.relativize(file)), options);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * 파일 또는 디렉토리를 지정한 위치로 이동
     *
     * @param source    이동할 원본 파일 또는 디렉토리 경로
     * @param target    이동 대상 경로 (파일 또는 디렉토리)
     * @param overwrite true: 대상에 동일 이름이 존재할 경우 덮어쓰기, false: 이미 존재하면 예외 발생
     * @throws IOException 이동 중 I/O 오류, 권한 문제, 파일 시스템 오류 등이 발생할 경우
     * @details
     *          <dl>
     *          <dd>- source가 단일 파일이면 해당 파일을 target 위치로 이동</dd>
     *          <dd>- source가 디렉토리면 하위 모든 파일과 디렉토리를 target 위치로 이동</dd>
     *          <dd>- overwrite가 true면 target에 동일 이름의 파일/디렉토리가 존재할 경우 덮어씀</dd>
     *          <dd>- 파일 시스템 간 이동이거나 디렉토리 이동이 실패할 경우 복사 후 원본 삭제 방식으로 동작</dd>
     *          <dd>- 단일 파일 이동 시 원자적 이동(ATOMIC_MOVE)을 우선 시도</dd>
     *          </dl>
     * @apiNote source.html 파일을 target.html 파일로 sample 디렉토리에 이동(중복 파일 존재 시 덮어쓰기)
     *
     *          <pre>{@code
     * S2FileUtil.move(Paths.get("c:/source.html"), Paths.get("c:/sample"), true);
     * }</pre>
     *
     * @see java.nio.file.Files#move(Path, Path, CopyOption...)
     * @see java.nio.file.Files#walkFileTree(Path, java.nio.file.FileVisitor)
     */
    public static void move(Path source, Path target, boolean overwrite) throws IOException {
        if (source == null || target == null || !Files.exists(source) || Files.isSameFile(source, target)) {
            return;
        }

        var parent = target.getParent();
        if (parent != null && Files.notExists(parent)) {
            // 대상의 부모 디렉토리 자동 생성(모든 상위 디렉토리)
            Files.createDirectories(parent);
        }

        var options = overwrite
                ? new CopyOption[] { StandardCopyOption.REPLACE_EXISTING } // 파일이 이미 존재할 때 덮어쓰기를 허용
                : new CopyOption[0];

        if (Files.isRegularFile(source)) {
            // 단일 파일 이동
            try {
                // 원자적 이동 시도 (성능 최적화, move 전용)
                var options2 = overwrite
                        ? new CopyOption[] { StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING }
                        : new CopyOption[] { StandardCopyOption.ATOMIC_MOVE };
                Files.move(source, target, options2);
            } catch (AtomicMoveNotSupportedException e) {
                // 원자적 이동이 지원되지 않는 경우 일반 이동
                Files.move(source, target, options);
            }
        } else {
            // 디렉토리 이동
            try {
                // 먼저 간단한 이동 시도 (같은 파일 시스템 내에서는 효율적)
                Files.move(source, target, options);
            } catch (DirectoryNotEmptyException e) {
                // 이동 실패 시 복사 후 삭제 방식 사용[7][14]
                copy(source, target, overwrite);

                // 원본 삭제 (모든 파일과 디렉토리 삭제)
                delete(source);
            }
        }
    }

    /**
     * 주어진 경로(파일 또는 디렉토리)와 그 하위 모든 콘텐츠를 삭제한다.
     *
     * @param delPath 삭제할 경로 (파일 또는 디렉토리 경로, null 이면 아무것도 하지 않음)
     * @return 삭제했으면 true, 경로가 원래 없었으면(또는 null) false
     * @throws S2RuntimeException 삭제하지 못한 항목이 있을 때 (나머지는 삭제를 시도한 뒤, 첫 원인 포함)
     */
    public static boolean delete(String delPath) {
        return delPath != null && delete(Paths.get(delPath));
    }

    /**
     * 주어진 경로(파일 또는 디렉토리)와 그 하위 모든 콘텐츠를 삭제한다.
     * <p>
     * 디렉토리는 하위 항목부터 모두 삭제를 시도하고, 하나라도 실패하면 끝까지 시도한 뒤 예외를 던진다. 권한 문제 등으로 남은 파일이 있는데
     * 성공으로 알리지 않는다.
     * </p>
     *
     * @param delPath 삭제할 경로 (파일 또는 디렉토리 경로, null 이면 아무것도 하지 않음)
     * @return 삭제했으면 true, 경로가 원래 없었으면(또는 null) false
     * @throws S2RuntimeException 삭제하지 못한 항목이 있을 때 (첫 원인 포함, 나머지는 suppressed)
     */
    public static boolean delete(Path delPath) {
        if (delPath == null || !Files.exists(delPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        var failures = new ArrayList<IOException>();
        if (Files.isDirectory(delPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(delPath)) {
                // Children before parents | 하위 항목부터
                for (var path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        failures.add(e);
                    }
                }
            } catch (IOException | java.io.UncheckedIOException e) {
                failures.add(e instanceof IOException io ? io : ((java.io.UncheckedIOException) e).getCause());
            }
        } else {
            try {
                Files.deleteIfExists(delPath);
            } catch (IOException e) {
                failures.add(e);
            }
        }
        if (!failures.isEmpty()) {
            var error = new S2RuntimeException("삭제 실패: " + delPath + " (" + failures.get(0) + ")", failures.get(0));
            failures.stream().skip(1).forEach(error::addSuppressed);
            throw error;
        }
        return true;
    }

    /**
     * 지정된 디렉토리와 그 하위 디렉토리에서 지정된 기간보다 오래된 파일을 삭제
     *
     * @param deletionPath       삭제를 시작할 디렉토리 경로
     * @param deletionThreshold  삭제 기준 기간 (Duration.ofDays(1): 만 1일을 포함한 그 이전 파일 삭제, Duration.ofHours(0): 시간과 상관없이 즉시 삭제)
     * @param filePrefixToDelete 삭제할 파일의 접두사 ("s2_tmp_": 해당 접두사로 시작하는 파일만 삭제, null: 파일명과 상관없이 삭제)
     * @details
     *          <dl>
     *          <dd>- 접두사가 있다면 접두사로 시작하는 파일만 삭제하며, 디렉토리는 지우지 않는다</dd>
     *          <dd>- 접두사가 없으면 하위 디렉토리가 비면 삭제한다. 시작 디렉토리(deletionPath)는 지우지 않는다</dd>
     *          </dl>
     */
    public static void deleteFilesOlderThan(Path deletionPath, Duration deletionThreshold, String filePrefixToDelete) {
        if (deletionPath == null || deletionThreshold == null) {
            return;
        }

        try {
            // 디렉토리 트리를 깊이 우선 순서로 탐색하며, 최하위 파일부터 처리(같은 레벨의 파일 방문 순서는 보장되지 않음)
            Files.walkFileTree(deletionPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    var fileName = file.getFileName().toString();
                    // 접두사가 비어 있거나 파일 이름이 접두사로 시작하는 경우
                    if (filePrefixToDelete == null || filePrefixToDelete.isBlank()
                            || fileName.startsWith(filePrefixToDelete)) {
                        var creationTime = attrs.creationTime();
                        var lastModifiedTime = attrs.lastModifiedTime();
                        // 수정 시간이 생성 시간보다 나중이면 사용
                        var targetTime = lastModifiedTime.compareTo(creationTime) > 0 ? lastModifiedTime : creationTime;
                        var now = Instant.now();
                        var targetInstant = targetTime.toInstant();
                        var fileDuration = Duration.between(targetInstant, now);
                        // 파일이 지정된 기간을 포함한 그보다 오래된 경우 삭제
                        if (fileDuration.compareTo(deletionThreshold) >= 0) {
                            try {
                                Files.deleteIfExists(file);
                            } catch (IOException e) {
                                logger.error("파일 삭제 실패: {} {}", file, e.getMessage(), e);
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    // Keep the start directory, and directories when only prefixed files are targeted | 시작 디렉토리와, 접두사 대상일 때의 디렉토리는 유지
                    if (dir.equals(deletionPath) || (filePrefixToDelete != null && !filePrefixToDelete.isBlank())) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        Files.delete(dir);
                    } catch (DirectoryNotEmptyException e) {
                        // 디렉토리가 비어 있지 않으면 삭제하지 않음
                    } catch (IOException e) {
                        logger.error("디렉토리 삭제 실패: {} {}", dir, e.getMessage(), e);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    logger.error("파일 접근 실패: {} {}", file, exc.getMessage(), exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.error("디렉토리 탐색 실패: {} {}", deletionPath, e.getMessage(), e);
        }
    }

    /**
     * 특정 기간보다 오래된 임시 파일을 삭제한다
     *
     * @param deletionThreshold  삭제 기준 기간 (Duration.ofDays(1): 만 1일을 포함한 그 이전 파일 삭제, Duration.ofHours(0): 시간과 상관없이 즉시 삭제)
     * @param filePrefixToDelete 삭제할 파일의 접두사 (필수, 예: "s2_tmp_")
     * @throws IllegalArgumentException 접두사가 비었을 때 (시스템 임시 디렉토리는 다른 프로그램도 쓰므로 전체 삭제를 허용하지 않음)
     */
    public static void deleteTemporaryFilesOlderThan(Duration deletionThreshold, String filePrefixToDelete) {
        if (filePrefixToDelete == null || filePrefixToDelete.isBlank()) {
            throw new IllegalArgumentException("임시 파일 삭제에는 접두사가 필요합니다 (다른 프로그램의 임시 파일 보호).");
        }
        var temporaryPath = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"));
        deleteFilesOlderThan(temporaryPath, deletionThreshold, filePrefixToDelete);
    }

    /**
     * 파일 존재 여부
     *
     * @param path 파일 경로
     * @return 파일 존재 여부
     */
    public static boolean exists(String path) {
        if (path != null && !path.isBlank()) {
            Path filePath = Paths.get(path);
            if (Files.exists(filePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 파일을 Reader 로 변환한다.(문자 스트림: .txt, .csv, .xml, .json 파일과 같은 텍스트 기반 파일)
     *
     * @param fileFullPath 파일 전체 경로(문자열)
     * @return InputStream
     * @throws S2RuntimeException 파일을 열 수 없을 때 (원인 포함)
     */
    public static Reader fileToReader(String fileFullPath) {
        if (fileFullPath == null || fileFullPath.isBlank()) {
            throw new IllegalArgumentException("파일 경로가 비었습니다.");
        }
        return fileToReader(Paths.get(fileFullPath));
    }

    /**
     * 파일을 Reader 로 변환한다.(문자 스트림: .txt, .csv, .xml, .json 파일과 같은 텍스트 기반 파일)
     *
     * @param fileFullPath 파일 전체 경로(Path)
     * @return InputStream
     * @throws S2RuntimeException 파일을 열 수 없을 때 (원인 포함)
     */
    public static Reader fileToReader(Path fileFullPath) {
        Objects.requireNonNull(fileFullPath, "fileFullPath");
        try {
            return Files.newBufferedReader(fileFullPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new S2RuntimeException("파일을 열지 못했습니다: " + fileFullPath + " (" + e + ")", e);
        }
    }

    /**
     * 파일을 InputStream 으로 변환한다.(바이트 스트림: 이미지, 오디오, 비디오 파일 또는 실행 파일 같은 바이너리 데이터)
     *
     * @param fileFullPath 파일 전체 경로(문자열)
     * @return InputStream
     * @throws S2RuntimeException 파일을 열 수 없을 때 (원인 포함)
     */
    public static InputStream fileToInputStream(String fileFullPath) {
        if (fileFullPath == null || fileFullPath.isBlank()) {
            throw new IllegalArgumentException("파일 경로가 비었습니다.");
        }
        return fileToInputStream(Paths.get(fileFullPath));
    }

    /**
     * 파일을 InputStream 으로 변환한다.(바이트 스트림: 이미지, 오디오, 비디오 파일 또는 실행 파일 같은 바이너리 데이터)
     *
     * @param fileFullPath 파일 전체 경로(Path)
     * @return InputStream
     * @throws S2RuntimeException 파일을 열 수 없을 때 (원인 포함)
     */
    public static InputStream fileToInputStream(Path fileFullPath) {
        Objects.requireNonNull(fileFullPath, "fileFullPath");
        if (!Files.isRegularFile(fileFullPath)) {
            throw new S2RuntimeException("파일이 존재하지 않습니다: " + fileFullPath);
        }
        try {
            return Files.newInputStream(fileFullPath);
        } catch (IOException e) {
            throw new S2RuntimeException("파일을 열지 못했습니다: " + fileFullPath + " (" + e + ")", e);
        }
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceStream 처리할 InputStream
     * @return 임시 파일
     * @throws S2RuntimeException 임시 파일 생성 또는 저장 실패 시 (원인 포함)
     */
    public static Path streamToTempFile(InputStream sourceStream) {
        return streamToTempFile(sourceStream, false);
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceReader 처리할 Reader
     * @return 임시 파일
     * @throws S2RuntimeException 임시 파일 생성 또는 저장 실패 시 (원인 포함)
     */
    public static Path streamToTempFile(Reader sourceReader) {
        return streamToTempFile(sourceReader, false);
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceStream  처리할 InputStream
     * @param fileExtension 파일 확장자
     * @return 임시 파일
     * @throws S2RuntimeException 임시 파일 생성 또는 저장 실패 시 (원인 포함)
     */
    public static Path streamToTempFile(InputStream sourceStream, String fileExtension) {
        return streamToTempFile(sourceStream, fileExtension, false);
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceReader  처리할 Reader
     * @param fileExtension 파일 확장자
     * @return 임시 파일
     * @throws S2RuntimeException 임시 파일 생성 또는 저장 실패 시 (원인 포함)
     */
    public static Path streamToTempFile(Reader sourceReader, String fileExtension) {
        return streamToTempFile(sourceReader, fileExtension, false);
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceStream      처리할 InputStream
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 임시 파일
     * @throws S2RuntimeException 임시 파일 생성 또는 저장 실패 시 (원인 포함)
     */
    public static Path streamToTempFile(InputStream sourceStream, boolean shouldCloseStream) {
        return streamToTempFile(sourceStream, null, shouldCloseStream);
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceReader      처리할 Reader
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 임시 파일
     * @throws S2RuntimeException 임시 파일 생성 또는 저장 실패 시 (원인 포함)
     */
    public static Path streamToTempFile(Reader sourceReader, boolean shouldCloseStream) {
        return streamToTempFile(sourceReader, null, shouldCloseStream);
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceStream      처리할 InputStream
     * @param fileExtension     파일 확장자
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 임시 파일
     * @throws S2RuntimeException 임시 파일 생성 또는 저장 실패 시 (원인 포함)
     */
    public static Path streamToTempFile(InputStream sourceStream, String fileExtension, boolean shouldCloseStream) {
        return closeableToTempFile(sourceStream, fileExtension, shouldCloseStream);
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceReader      처리할 Reader
     * @param fileExtension     파일 확장자
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 임시 파일
     * @throws S2RuntimeException 임시 파일 생성 또는 저장 실패 시 (원인 포함)
     */
    public static Path streamToTempFile(Reader sourceReader, String fileExtension, boolean shouldCloseStream) {
        return closeableToTempFile(sourceReader, fileExtension, shouldCloseStream);
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceStream      처리할 스트림 (InputStream 또는 Reader)
     * @param fileExtension     파일 확장자
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 임시 파일
     * @throws S2RuntimeException 임시 파일 생성 또는 저장 실패 시 (원인 포함)
     */
    private static Path closeableToTempFile(Closeable sourceStream, String fileExtension, boolean shouldCloseStream) {
        Objects.requireNonNull(sourceStream, "sourceStream");
        Path tempFile;
        try {
            tempFile = Files.createTempFile("s2_tmp_" + S2Uuid.generateUuidV7() + "_",
                    "." + (fileExtension == null || fileExtension.isBlank() ? "tmp" : fileExtension));
        } catch (IOException e) {
            if (shouldCloseStream) {
                S2StreamUtil.closeStream(sourceStream);
            }
            throw new S2RuntimeException("임시 파일 생성 실패 (" + e + ")", e);
        }
        closeableToFile(sourceStream, tempFile, shouldCloseStream); // Deletes the file itself on failure | 실패하면 파일을 지움
        return tempFile;
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceStream   처리할 InputStream
     * @param targetFilePath 대상 파일 경로
     * @return 저장한 파일 경로
     * @throws S2RuntimeException 저장 실패 시 (원인 포함, 쓰다 만 파일은 삭제)
     */
    public static Path streamToFile(InputStream sourceStream, String targetFilePath) {
        return streamToFile(sourceStream, targetFilePath, false);
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceReader   처리할 Reader
     * @param targetFilePath 대상 파일 경로
     * @return 저장한 파일 경로
     * @throws S2RuntimeException 저장 실패 시 (원인 포함, 쓰다 만 파일은 삭제)
     */
    public static Path streamToFile(Reader sourceReader, String targetFilePath) {
        return streamToFile(sourceReader, targetFilePath, false);
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceStream      처리할 InputStream
     * @param targetFilePath    대상 파일 경로
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 저장한 파일 경로
     * @throws S2RuntimeException 저장 실패 시 (원인 포함, 쓰다 만 파일은 삭제)
     */
    public static Path streamToFile(InputStream sourceStream, String targetFilePath, boolean shouldCloseStream) {
        if (targetFilePath == null || targetFilePath.isBlank()) {
            throw new IllegalArgumentException("대상 파일 경로가 비었습니다.");
        }
        var targetFile = Paths.get(targetFilePath);
        streamToFile(sourceStream, targetFile, shouldCloseStream);
        return targetFile;
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceReader      처리할 Reader
     * @param targetFilePath    대상 파일 경로
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     *
     * @return 저장한 파일 경로
     * @throws S2RuntimeException 저장 실패 시 (원인 포함, 쓰다 만 파일은 삭제)
     */
    public static Path streamToFile(Reader sourceReader, String targetFilePath, boolean shouldCloseStream) {
        if (targetFilePath == null || targetFilePath.isBlank()) {
            throw new IllegalArgumentException("대상 파일 경로가 비었습니다.");
        }
        var targetFile = Paths.get(targetFilePath);
        streamToFile(sourceReader, targetFile, shouldCloseStream);
        return targetFile;
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceStream 처리할 InputStream
     * @param targetFile   대상 파일
     * @return 생성된 파일의 크기(바이트 단위)
     * @throws S2RuntimeException 저장 실패 시 (원인 포함, 쓰다 만 파일은 삭제)
     */
    public static long streamToFile(InputStream sourceStream, Path targetFile) {
        return streamToFile(sourceStream, targetFile, false);
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceReader 처리할 Reader
     * @param targetFile   대상 파일
     * @return 생성된 파일의 크기(바이트 단위)
     * @throws S2RuntimeException 저장 실패 시 (원인 포함, 쓰다 만 파일은 삭제)
     */
    public static long streamToFile(Reader sourceReader, Path targetFile) {
        return streamToFile(sourceReader, targetFile, false);
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceStream      처리할 InputStream
     * @param targetFile        대상 파일
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 생성된 파일의 크기(바이트 단위)
     * @throws S2RuntimeException 저장 실패 시 (원인 포함, 쓰다 만 파일은 삭제)
     */
    public static long streamToFile(InputStream sourceStream, Path targetFile, boolean shouldCloseStream) {
        return closeableToFile(sourceStream, targetFile, shouldCloseStream);
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceReader      처리할 Reader
     * @param targetFile        대상 파일
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 생성된 파일의 크기(바이트 단위)
     * @throws S2RuntimeException 저장 실패 시 (원인 포함, 쓰다 만 파일은 삭제)
     */
    public static long streamToFile(Reader sourceReader, Path targetFile, boolean shouldCloseStream) {
        return closeableToFile(sourceReader, targetFile, shouldCloseStream);
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceStream      처리할 스트림 (InputStream 또는 Reader)
     * @param targetFile        대상 파일
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     * @return 생성된 파일의 크기(바이트 단위)
     * @throws S2RuntimeException 저장 실패 시 (원인 포함, 쓰다 만 파일은 삭제)
     */
    private static long closeableToFile(Closeable sourceStream, Path targetFile, boolean shouldCloseStream) {
        Objects.requireNonNull(sourceStream, "sourceStream");
        Objects.requireNonNull(targetFile, "targetFile");
        try {
            var parent = targetFile.toAbsolutePath().getParent();
            if (parent != null) {
                makeDirectory(parent);
            }
            if (sourceStream instanceof InputStream inputStream) {
                return Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            if (sourceStream instanceof Reader reader) {
                try (var writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
                    reader.transferTo(writer);
                }
                return Files.size(targetFile);
            }
            throw new IllegalArgumentException("InputStream 또는 Reader 만 지원합니다: " + sourceStream.getClass());
        } catch (IOException e) {
            // Do not leave a truncated file behind | 쓰다 만 파일을 남기지 않음
            try {
                Files.deleteIfExists(targetFile);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new S2RuntimeException("파일 저장 실패: " + targetFile + " (" + e + ")", e);
        } finally {
            if (shouldCloseStream) {
                S2StreamUtil.closeStream(sourceStream);
            }
        }
    }

    /**
     * 바이트 배열 등을 사용하는 메모리 누수 방지를 위해 InputStream 을 임시 파일로 저장하고 처리한 후 자동으로 삭제하는 유틸리티
     *
     * @param sourceStream  처리할 InputStream
     * @param fileExtension 임시 파일 확장자 (없으면 "tmp")
     * @param processor     임시 파일을 처리하고 결과를 반환하는 함수
     * @param &lt;T&gt;     반환할 결과의 타입
     * @return processor 의 처리 결과
     * @throws IOException IO 예외 발생 시
     */
    public static <T> T processStreamWithTempFile(InputStream sourceStream, String fileExtension,
            Function<Path, T> processor) throws IOException {
        return processStreamWithTempFile(sourceStream, fileExtension, processor, false);
    }

    /**
     * 바이트 배열 등을 사용하는 메모리 누수 방지를 위해 InputStream 을 임시 파일로 저장하고 처리한 후 자동으로 삭제하는 유틸리티
     *
     * @param sourceReader  처리할 Reader
     * @param fileExtension 임시 파일 확장자 (없으면 "tmp")
     * @param processor     임시 파일을 처리하고 결과를 반환하는 함수
     * @param &lt;T&gt;     반환할 결과의 타입
     * @return processor 의 처리 결과
     * @throws IOException IO 예외 발생 시
     */
    public static <T> T processStreamWithTempFile(Reader sourceReader, String fileExtension,
            Function<Path, T> processor) throws IOException {
        return processStreamWithTempFile(sourceReader, fileExtension, processor, false);
    }

    /**
     * 바이트 배열 등을 사용하는 메모리 누수 방지를 위해 InputStream 을 임시 파일로 저장하고 처리한 후 자동으로 삭제하는 유틸리티
     *
     * @param sourceStream      처리할 InputStream
     * @param fileExtension     임시 파일 확장자 (없으면 "tmp")
     * @param processor         임시 파일을 처리하고 결과를 반환하는 함수
     * @param &lt;T&gt;         반환할 결과의 타입
     * @param shouldCloseStream inputStream 을 닫을지 여부
     * @return processor 의 처리 결과
     * @throws IOException IO 예외 발생 시
     */
    public static <T> T processStreamWithTempFile(InputStream sourceStream, String fileExtension,
            Function<Path, T> processor, boolean shouldCloseStream) throws IOException {
        Path tempFile = null;
        try {
            tempFile = streamToTempFile(sourceStream, fileExtension, shouldCloseStream);
            return processor.apply(tempFile);
        } finally {
            // Temp file cleanup must not hide the result or the processor's own exception | 임시 파일 정리 실패가 결과나 원래 예외를 가리지 않도록 로그만 남김
            deleteQuietly(tempFile);
            if (shouldCloseStream) {
                S2StreamUtil.closeStream(sourceStream);
            }
        }
    }

    /**
     * 바이트 배열 등을 사용하는 메모리 누수 방지를 위해 InputStream 을 임시 파일로 저장하고 처리한 후 자동으로 삭제하는 유틸리티
     *
     * @param sourceReader      처리할 Reader
     * @param fileExtension     임시 파일 확장자 (없으면 "tmp")
     * @param processor         임시 파일을 처리하고 결과를 반환하는 함수
     * @param &lt;T&gt;         반환할 결과의 타입
     * @param shouldCloseStream inputStream 을 닫을지 여부
     * @return processor 의 처리 결과
     * @throws IOException IO 예외 발생 시
     */
    public static <T> T processStreamWithTempFile(Reader sourceReader, String fileExtension,
            Function<Path, T> processor, boolean shouldCloseStream) throws IOException {
        Path tempFile = null;
        try {
            tempFile = streamToTempFile(sourceReader, fileExtension, shouldCloseStream);
            return processor.apply(tempFile);
        } finally {
            // Temp file cleanup must not hide the result or the processor's own exception | 임시 파일 정리 실패가 결과나 원래 예외를 가리지 않도록 로그만 남김
            deleteQuietly(tempFile);
            if (shouldCloseStream) {
                S2StreamUtil.closeStream(sourceReader);
            }
        }
    }

    /**
     * 확장자를 제외한 파일명을 가져온다.
     *
     * @param sourceFile 대상 파일
     * @return 확장자를 제외한 파일명
     */
    public static String getBaseName(Path sourceFile) {
        String name = "";
        if (sourceFile != null) {
            name = getBaseName(sourceFile.getFileName().toString());
        }
        return name;
    }

    /**
     * 확장자를 제외한 파일명을 가져온다.
     *
     * @param fileName 파일 이름
     * @return 확장자를 제외한 파일명
     */
    public static String getBaseName(String fileName) {
        String name = "";
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf(".");
            name = dotIndex != -1 ? fileName.substring(0, dotIndex) : fileName;
        }
        return name;
    }

    /**
     * 파일 확장자를 가져온다.
     *
     * @param sourceFile 대상 파일
     * @return 확장자(소문자)
     */
    public static String getExtension(Path sourceFile) {
        return getExtension(sourceFile, false);
    }

    /**
     * 파일 확장자를 가져온다.
     *
     * @param sourceFile  대상 파일
     * @param toLowerCase 소문자 변경 여부
     * @return 확장자(소문자)
     */
    public static String getExtension(Path sourceFile, boolean toLowerCase) {
        String extension = "";
        if (sourceFile != null) {
            extension = getExtension(sourceFile.getFileName().toString(), toLowerCase);
        }
        return extension;
    }

    /**
     * 파일 확장자를 가져온다.
     *
     * @param fileName 파일 이름
     * @return 확장자
     */
    public static String getExtension(String fileName) {
        return getExtension(fileName, false);
    }

    /**
     * 파일 확장자를 가져온다.
     *
     * @param fileName    파일 이름
     * @param toLowerCase 소문자 변경 여부
     * @return 확장자
     */
    public static String getExtension(String fileName, boolean toLowerCase) {
        String extension = "";
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex != -1 && dotIndex < fileName.length() - 1) { // 확장자가 없는 경우를 방지
                extension = fileName.substring(dotIndex + 1);
            }
        }
        return toLowerCase ? extension.toLowerCase() : extension;
    }

    /**
     * 파일 크기를 가져온다.
     *
     * @param sourceFile 대상 파일
     * @return 파일 크기
     * @throws S2RuntimeException 크기를 확인할 수 없을 때 (없는 파일 등, 원인 포함)
     */
    public static long getSize(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        try {
            return Files.size(sourceFile);
        } catch (IOException e) {
            throw new S2RuntimeException("파일 크기 확인 실패: " + sourceFile + " (" + e + ")", e);
        }
    }

    /**
     * Deletes and logs a failure instead of throwing, for cleanup in finally blocks and cleaners | finally·Cleaner 정리용: 실패는 로그만
     *
     * @param path 삭제할 경로 (null 허용)
     */
    public static void deleteQuietly(Path path) {
        try {
            delete(path);
        } catch (RuntimeException e) {
            logger.warn("임시 파일 삭제 실패: {} ({})", path, e.getMessage());
        }
    }

    /**
     * MIME TYPE 에 매핑되는 기본 확장자 맵 (MIME TYPE 에서 확인되지 않으면 확장자로 확인). 한글은 흔히 쓰이는 비표준 이름도 포함한다.
     */
    // 매 호출마다 새로 만들 필요 없이 한 번만 초기화해서 재사용한다.
    private static final Map<String, String> MIME_TYPE_TO_EXTENSION = Map.ofEntries(
            Map.entry("audio/aac", "aac"),
            Map.entry("application/x-abiword", "abw"),
            Map.entry("video/x-msvideo", "avi"),
            Map.entry("application/vnd.amazon.ebook", "azw"),
            Map.entry("application/octet-stream", "bin"),
            Map.entry("image/bmp", "bmp"),
            Map.entry("image/x-ms-bmp", "bmp"),
            Map.entry("application/x-bzip", "bz"),
            Map.entry("application/x-bzip2", "bz2"),
            Map.entry("application/x-csh", "csh"),
            Map.entry("text/css", "css"),
            Map.entry("text/csv", "csv"),
            Map.entry("application/csv", "csv"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/epub+zip", "epub"),
            Map.entry("image/gif", "gif"),
            Map.entry("text/html", "html"),
            Map.entry("application/x-hwp", "hwp"),
            Map.entry("application/haansofthwp", "hwp"),
            Map.entry("application/vnd.hancom.hwp", "hwp"),
            Map.entry("application/hwp", "hwp"),
            Map.entry("application/x-hwpx", "hwpx"),
            Map.entry("application/haansofthwpx", "hwpx"),
            Map.entry("application/vnd.hancom.hwpx", "hwpx"),
            Map.entry("application/hwp+zip", "hwpx"),
            Map.entry("image/x-icon", "ico"),
            Map.entry("text/calendar", "ics"),
            Map.entry("application/java-archive", "jar"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/jpg", "jpg"),
            Map.entry("image/pjpeg", "jpg"),
            Map.entry("text/javascript", "js"),
            Map.entry("application/json", "json"),
            Map.entry("text/markdown", "md"),
            Map.entry("text/x-markdown", "md"),
            Map.entry("audio/midi", "midi"),
            Map.entry("video/mpeg", "mpeg"),
            Map.entry("video/mp4", "mp4"),
            Map.entry("application/vnd.apple.installer+xml", "mpkg"),
            Map.entry("application/vnd.oasis.opendocument.presentation", "odp"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", "ods"),
            Map.entry("application/vnd.oasis.opendocument.text", "odt"),
            Map.entry("audio/ogg", "oga"),
            Map.entry("video/ogg", "ogv"),
            Map.entry("application/ogg", "ogx"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("image/png", "png"),
            Map.entry("application/vnd.ms-powerpoint", "ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
            Map.entry("application/x-rar-compressed", "rar"),
            Map.entry("application/rtf", "rtf"),
            Map.entry("text/rtf", "rtf"),
            Map.entry("application/x-sh", "sh"),
            Map.entry("image/svg+xml", "svg"),
            Map.entry("application/x-shockwave-flash", "swf"),
            Map.entry("application/x-tar", "tar"),
            Map.entry("image/tiff", "tif"),
            Map.entry("application/x-font-ttf", "ttf"),
            Map.entry("text/plain", "txt"),
            Map.entry("application/vnd.visio", "vsd"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("audio/webm", "weba"),
            Map.entry("video/webm", "webm"),
            Map.entry("image/webp", "webp"),
            Map.entry("application/x-font-woff", "woff"),
            Map.entry("application/xhtml+xml", "xhtml"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("application/xml", "xml"),
            Map.entry("text/xml", "xml"),
            Map.entry("application/zip", "zip"),
            Map.entry("application/x-zip-compressed", "zip"));

    /** The usual MIME type of an extension | 확장자의 대표 MIME 타입 */
    private static final Map<String, String> EXTENSION_TO_MIME_TYPE = Map.ofEntries(
            Map.entry("bmp", "image/bmp"), Map.entry("css", "text/css"), Map.entry("csv", "text/csv"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("gif", "image/gif"), Map.entry("htm", "text/html"), Map.entry("html", "text/html"),
            Map.entry("hwp", "application/x-hwp"), Map.entry("hwpx", "application/vnd.hancom.hwpx"),
            Map.entry("ico", "image/x-icon"), Map.entry("jpeg", "image/jpeg"), Map.entry("jpg", "image/jpeg"),
            Map.entry("js", "text/javascript"), Map.entry("json", "application/json"), Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"), Map.entry("mp4", "video/mp4"),
            Map.entry("odp", "application/vnd.oasis.opendocument.presentation"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"), Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"), Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("rtf", "application/rtf"), Map.entry("svg", "image/svg+xml"), Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"), Map.entry("txt", "text/plain"), Map.entry("webp", "image/webp"),
            Map.entry("xhtml", "application/xhtml+xml"), Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("xml", "application/xml"), Map.entry("zip", "application/zip"));

    /**
     * 파일의 확장자를 MIME 타입으로 판별한다. OS 가 판별하지 못하면(확장자 없는 파일 등) 파일 내용으로 판별한다 ({@link #detectExtension(Path)}).
     *
     * @param sourceFile 대상 파일
     * @return 확장자 (모르면 빈 문자열)
     */
    public static String getExtensionByMimeType(Path sourceFile) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            return "";
        }
        var extension = getExtensionByMimeType(getContentType(sourceFile));
        return extension.isEmpty() || extension.equals("bin") ? detectExtension(sourceFile) : extension;
    }

    /**
     * MIME 타입의 확장자 ({@code application/pdf} → {@code pdf}). 매개변수({@code ; charset=...})와 대소문자는 무시한다. 한글의 비표준 이름
     * ({@code application/x-hwp}, {@code application/haansofthwp} 등)도 받는다.
     *
     * @param mimeType MIME 타입
     * @return 확장자 (모르면 빈 문자열)
     */
    public static String getExtensionByMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "";
        }
        var type = mimeType.trim().toLowerCase(java.util.Locale.ROOT).replaceFirst("\\s*;.*$", "");
        return MIME_TYPE_TO_EXTENSION.getOrDefault(type, "");
    }

    /**
     * 확장자(또는 파일명)의 대표 MIME 타입 ({@code hwp} → {@code application/x-hwp}).
     *
     * @param extensionOrFileName 확장자({@code png}) 또는 파일명({@code a.png})
     * @return MIME 타입 (모르면 빈 문자열)
     */
    public static String getMimeTypeByExtension(String extensionOrFileName) {
        if (extensionOrFileName == null || extensionOrFileName.isBlank()) {
            return "";
        }
        var value = extensionOrFileName.trim();
        var extension = value.contains(".") ? getExtension(value, true) : value.toLowerCase(java.util.Locale.ROOT);
        return EXTENSION_TO_MIME_TYPE.getOrDefault(extension, "");
    }

    /**
     * 파일명, 확장자, MIME 타입 중 무엇이든 받아 확장자를 돌려준다. DB 에 원래 파일명이나 MIME 타입만 있을 때 쓴다.
     * <ul>
     * <li>MIME 타입({@code application/x-hwp}) → {@link #getExtensionByMimeType(String)}</li>
     * <li>파일명({@code 보고서.HWP}, {@code /a/b/c.pdf}) → 소문자 확장자</li>
     * <li>확장자만({@code hwp}) → 소문자 그대로</li>
     * </ul>
     *
     * @param fileNameExtensionOrMimeType 파일명, 확장자 또는 MIME 타입
     * @return 소문자 확장자 (모르면 빈 문자열, {@code application/octet-stream} 은 {@code bin})
     */
    public static String getExtensionByHint(String fileNameExtensionOrMimeType) {
        if (fileNameExtensionOrMimeType == null || fileNameExtensionOrMimeType.isBlank()) {
            return "";
        }
        var value = fileNameExtensionOrMimeType.trim();
        // A MIME type: listed, or under a standard top-level type (its subtype may end like ".document")
        // | MIME 타입: 표에 있거나 표준 최상위 유형으로 시작 (하위 유형이 ".document" 처럼 끝날 수 있음)
        var mimeExtension = getExtensionByMimeType(value);
        if (!mimeExtension.isEmpty()
                || value.toLowerCase(java.util.Locale.ROOT).matches("^(application|image|text|audio|video|font|model|multipart|message)/[a-z0-9.+-]+(\\s*;.*)?$")) {
            return mimeExtension;
        }
        var name = value.substring(Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\')) + 1);
        if (name.contains(".")) {
            return getExtension(name, true);
        }
        return name.matches("[A-Za-z0-9]{1,10}") ? name.toLowerCase(java.util.Locale.ROOT) : "";
    }

    /**
     * 파일의 ContentType 을 가져온다. OS 가 판별하지 못하면(확장자 없는 파일 등) 내용으로 판별한 형식의 MIME 타입을 돌려준다.
     *
     * @param sourceFile 대상 파일
     * @return ContentType (모르면 빈 문자열)
     */
    public static String getContentType(Path sourceFile) {
        var mimeType = "";
        if (sourceFile != null && Files.exists(sourceFile)) {
            try {
                mimeType = S2Util.cast(Files.probeContentType(sourceFile), "");
            } catch (IOException e) {
                logger.error("MIME 타입 추출 실패: ", e);
                mimeType = "";
            }
            if ((mimeType.isEmpty() || mimeType.equals("application/octet-stream")) && Files.isRegularFile(sourceFile)) {
                var detected = getMimeTypeByExtension(detectExtension(sourceFile));
                mimeType = detected.isEmpty() ? mimeType : detected;
            }
        }
        return mimeType;
    }

    /**
     * 파일 내용으로 형식을 판별해 확장자를 돌려준다. 확장자가 없거나 믿을 수 없는 파일(UUID 로 저장한 업로드 등)에 쓴다.
     * <ul>
     * <li>앞부분 서명: pdf, png, jpg, gif, bmp, tif, webp, 그리고 텍스트 시작으로 svg, html, rtf</li>
     * <li>압축 파일 안의 항목: docx, xlsx, pptx, hwpx, odt, ods, odp (그 외 압축 파일은 zip)</li>
     * <li>OLE 복합 문서의 스트림 이름: doc, xls, ppt, hwp</li>
     * </ul>
     * 일반 텍스트(txt, md, csv 등)는 내용만으로 구분할 수 없어 빈 문자열이다.
     *
     * @param sourceFile 대상 파일
     * @return 확장자 (모르면 빈 문자열)
     * @throws S2RuntimeException 읽을 수 없을 때 (원인 포함)
     */
    public static String detectExtension(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        try {
            byte[] head;
            try (var in = Files.newInputStream(sourceFile)) {
                head = in.readNBytes(4096);
            }
            var simple = FileSignature.fromHead(head);
            if (!simple.isEmpty()) {
                return simple;
            }
            if (FileSignature.isZip(head)) {
                try (var zip = new java.util.zip.ZipFile(sourceFile.toFile())) {
                    var names = new StringBuilder();
                    String mimetype = null;
                    for (var entries = zip.entries(); entries.hasMoreElements();) {
                        var entry = entries.nextElement();
                        names.append(entry.getName()).append('\n');
                        if (entry.getName().equals("mimetype")) {
                            try (var in = zip.getInputStream(entry)) {
                                mimetype = new String(in.readNBytes(200), StandardCharsets.US_ASCII).trim();
                            }
                        }
                    }
                    return FileSignature.fromZip(names.toString(), mimetype);
                } catch (java.util.zip.ZipException e) {
                    return "";
                }
            }
            if (FileSignature.isOle(head)) {
                try (var in = Files.newInputStream(sourceFile)) {
                    return FileSignature.fromOle(in);
                }
            }
            return "";
        } catch (IOException e) {
            throw new S2RuntimeException("파일 형식을 판별할 수 없습니다: " + sourceFile, e);
        }
    }

    /**
     * 메모리의 내용으로 형식을 판별해 확장자를 돌려준다 ({@link #detectExtension(Path)} 참고).
     *
     * @param content 파일 내용
     * @return 확장자 (모르면 빈 문자열)
     */
    public static String detectExtension(byte[] content) {
        Objects.requireNonNull(content, "content");
        var head = java.util.Arrays.copyOf(content, Math.min(content.length, 4096));
        var simple = FileSignature.fromHead(head);
        if (!simple.isEmpty()) {
            return simple;
        }
        try {
            if (FileSignature.isZip(head)) {
                try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(content))) {
                    var names = new StringBuilder();
                    String mimetype = null;
                    for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        names.append(entry.getName()).append('\n');
                        if (entry.getName().equals("mimetype")) {
                            mimetype = new String(zip.readNBytes(200), StandardCharsets.US_ASCII).trim();
                        }
                    }
                    return FileSignature.fromZip(names.toString(), mimetype);
                }
            }
            return FileSignature.isOle(head) ? FileSignature.fromOle(new java.io.ByteArrayInputStream(content)) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** File signatures for {@link #detectExtension} | 파일 서명 판별 */
    private static final class FileSignature {

        private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

        private FileSignature() {
        }

        static String fromHead(byte[] b) {
            var pdf = indexOf(b, PDF);
            if (pdf >= 0 && pdf < 1024) {
                return "pdf";
            }
            if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
                return "png";
            }
            if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return "jpg";
            }
            if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
                return "gif";
            }
            if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
                    && b[10] == 'B' && b[11] == 'P') {
                return "webp";
            }
            if (b.length >= 4 && ((b[0] == 'I' && b[1] == 'I' && b[2] == 42 && b[3] == 0)
                    || (b[0] == 'M' && b[1] == 'M' && b[2] == 0 && b[3] == 42))) {
                return "tif";
            }
            if (b.length >= 14 && b[0] == 'B' && b[1] == 'M') {
                return "bmp";
            }
            var text = new String(b, 0, Math.min(b.length, 1024), StandardCharsets.UTF_8).replace("\uFEFF", "")
                    .stripLeading().toLowerCase(java.util.Locale.ROOT);
            if (text.startsWith("{\\rtf")) {
                return "rtf";
            }
            if (text.startsWith("<svg") || (text.startsWith("<?xml") && text.contains("<svg"))) {
                return "svg";
            }
            if (text.startsWith("<!doctype html") || text.startsWith("<html")) {
                return "html";
            }
            return "";
        }

        /** Office Open XML, OpenDocument and HWPX by their entries | 압축 항목으로 구분 */
        static String fromZip(String names, String mimetype) {
            if (mimetype != null) {
                switch (mimetype) {
                case "application/hwp+zip" -> {
                    return "hwpx";
                }
                case "application/vnd.oasis.opendocument.text" -> {
                    return "odt";
                }
                case "application/vnd.oasis.opendocument.spreadsheet" -> {
                    return "ods";
                }
                case "application/vnd.oasis.opendocument.presentation" -> {
                    return "odp";
                }
                default -> {
                    // Other zip files: look at the entries | 그 외: 항목을 봄
                }
                }
            }
            if (names.contains("word/")) {
                return "docx";
            }
            if (names.contains("xl/")) {
                return "xlsx";
            }
            if (names.contains("ppt/")) {
                return "pptx";
            }
            if (names.contains("Contents/section")) {
                return "hwpx";
            }
            return "zip";
        }

        /** Word, Excel, PowerPoint 97-2003 and HWP 5 by stream names (UTF-16) | 스트림 이름(UTF-16)으로 구분 */
        static String fromOle(InputStream in) throws IOException {
            var hwp = "HwpSummaryInformation".getBytes(StandardCharsets.UTF_16LE);
            var word = "WordDocument".getBytes(StandardCharsets.UTF_16LE);
            var workbook = "Workbook".getBytes(StandardCharsets.UTF_16LE);
            var book = "Book".getBytes(StandardCharsets.UTF_16LE);
            var powerPoint = "PowerPoint Document".getBytes(StandardCharsets.UTF_16LE);
            var buffer = new byte[64 * 1024];
            var carry = new byte[0];
            boolean sawWord = false, sawSheet = false, sawSlides = false;
            int read;
            while ((read = in.readNBytes(buffer, 0, buffer.length)) > 0) {
                // The end of the previous chunk is kept so names across chunks are found | 경계에 걸친 이름도 찾도록 앞 조각 끝을 붙임
                var chunk = new byte[carry.length + read];
                System.arraycopy(carry, 0, chunk, 0, carry.length);
                System.arraycopy(buffer, 0, chunk, carry.length, read);
                if (indexOf(chunk, hwp) >= 0) {
                    return "hwp";
                }
                sawWord |= indexOf(chunk, word) >= 0;
                sawSheet |= indexOf(chunk, workbook) >= 0 || indexOf(chunk, book) >= 0;
                sawSlides |= indexOf(chunk, powerPoint) >= 0;
                carry = java.util.Arrays.copyOfRange(chunk, Math.max(0, chunk.length - 64), chunk.length);
            }
            return sawWord ? "doc" : sawSlides ? "ppt" : sawSheet ? "xls" : "";
        }

        static boolean isZip(byte[] b) {
            return b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4;
        }

        static boolean isOle(byte[] b) {
            return b.length >= 8 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF && (b[2] & 0xFF) == 0x11
                    && (b[3] & 0xFF) == 0xE0 && (b[4] & 0xFF) == 0xA1 && (b[5] & 0xFF) == 0xB1 && (b[6] & 0xFF) == 0x1A
                    && (b[7] & 0xFF) == 0xE1;
        }

        private static int indexOf(byte[] data, byte[] pattern) {
            outer: for (int i = 0; i <= data.length - pattern.length; i++) {
                for (int j = 0; j < pattern.length; j++) {
                    if (data[i + j] != pattern[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }

    /**
     * 입력 스트림 목록을 ZIP 파일로 압축하여 지정된 출력 스트림에 작성합니다.
     *
     * @param sourceFileList 압축할 파일 내용을 포함하는 파일명과 입력 스트림 목록
     * @param outputStream   ZIP 파일이 작성될 출력 스트림. 호출자가 제공해야 하며, 이 메서드에서 닫히지 않습니다.
     * @throws IOException              ZIP 생성 또는 스트림 처리 중 I/O 오류가 발생할 경우
     * @throws IllegalArgumentException sourceFileList 또는 outputStream 가 null 이거나 비어 있을때
     * @apiNote
     *
     *          <pre>{@code
     * List<Entry<String, InputStream>> sourceFileList = Arrays.asList(
     *     Map.entry("file1.txt", new ByteArrayInputStream("파일1 내용".getBytes())),
     *     Map.entry("file2.txt", new ByteArrayInputStream("파일2 내용".getBytes()))
     * );
     *
     * // 파일 경로에 ZIP 파일 작성
     * Path zipFilePath = Paths.get("/zipFile.zip");
     * S2FileUtil.zipFiles(sourceFileList, Files.newOutputStream(zipFilePath));
     *
     * // ZIP 파일로 압축 후 다운로드
     * response.setContentType("application/zip");
     * response.setHeader("Content-Disposition", "attachment; filename=\"" + zipFileName + ".zip\"");
     * S2FileUtil.zipFiles(sourceFileList, response.getOutputStream());
     * }</pre>
     */
    public static void zipFiles(List<Entry<String, InputStream>> sourceFileList, OutputStream outputStream)
            throws IOException {
        if (S2Util.isEmpty(sourceFileList) || outputStream == null) {
            throw new IllegalArgumentException("대상 파일 및 출력 스트림이 없습니다.");
        }

        // ZIP 출력 스트림 생성
        try (var zipOut = new ZipOutputStream(outputStream)) {
            // 각 InputStream과 파일명 처리
            for (var inputStreamEntry : sourceFileList) {
                try (var inputStream = inputStreamEntry.getValue()) {
                    var fileName = inputStreamEntry.getKey();

                    // ZIP 엔트리 생성
                    var zipEntry = new ZipEntry(fileName);
                    zipOut.putNextEntry(zipEntry);

                    // InputStream 데이터를 ZIP에 쓰기
                    var buffer = new byte[S2StreamUtil.getBufferSize()];
                    int len;
                    while ((len = inputStream.read(buffer)) > 0) {
                        zipOut.write(buffer, 0, len);
                    }

                    // 엔트리 닫기
                    zipOut.closeEntry();
                }
            }
            // ZIP 스트림 완료
            zipOut.finish();
        }
        // outputStream은 닫지 않음 (호출자가 관리)
    }

    /**
     * 디렉토리를 압축한다.
     *
     * @param sourceDir    압축할 디렉토리
     * @param outputStream ZIP 파일이 작성될 출력 스트림. 호출자가 제공해야 하며, 이 메서드에서 닫히지 않습니다.
     * @throws IOException IOException
     */
    public static void zipDirectory(Path sourceDir, OutputStream outputStream) throws IOException {
        // Close the walk stream; entry names always use '/' as the ZIP spec requires | walk 스트림을 닫고, 항목 이름은 ZIP 규격대로 항상 '/' 사용
        try (var zos = new ZipOutputStream(outputStream); var paths = Files.walk(sourceDir)) {
            for (var path : (Iterable<Path>) paths::iterator) {
                if (path.equals(sourceDir)) {
                    continue;
                }
                var entryName = sourceDir.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
                if (Files.isDirectory(path)) {
                    zos.putNextEntry(new ZipEntry(entryName + "/"));
                    zos.closeEntry();
                } else {
                    zos.putNextEntry(new ZipEntry(entryName));
                    try (var fis = Files.newInputStream(path)) {
                        fis.transferTo(zos);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    /**
     * Content-Disposition 헤더에서 파일명을 꺼낸다. RFC 6266 에 따라 {@code filename*}(RFC 5987 인코딩)를 {@code filename}보다
     * 우선하며, 따옴표와 이스케이프를 풀고 경로 부분({@code ../}, 디렉토리)은 버린다.
     *
     * @param contentDisposition Content-Disposition 헤더 값
     * @return 파일명 (없으면 빈 문자열)
     * @apiNote
     *
     *          <pre>{@code
     * parseContentDispositionFilename("attachment; filename=\"a.txt\"; filename*=UTF-8''%ED%95%9C.txt"); // "한.txt"
     * }</pre>
     */
    public static String parseContentDispositionFilename(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return "";
        }
        var extended = Pattern.compile("(?i)(?:^|;)\\s*filename\\*\\s*=\\s*([^']*)'[^']*'([^;\\s]*)").matcher(contentDisposition);
        if (extended.find()) {
            try {
                var charset = extended.group(1).isBlank() ? StandardCharsets.UTF_8 : Charset.forName(extended.group(1).trim());
                return lastPathSegment(percentDecode(extended.group(2), charset));
            } catch (IllegalArgumentException e) {
                // Unknown charset or bad encoding: fall back to filename= | 알 수 없는 문자셋·잘못된 인코딩이면 filename= 사용
            }
        }
        var plain = Pattern.compile("(?i)(?:^|;)\\s*filename\\s*=\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^;]*))").matcher(contentDisposition);
        if (plain.find()) {
            var name = plain.group(1) != null ? plain.group(1).replaceAll("\\\\(.)", "$1") : plain.group(2).trim();
            return lastPathSegment(name);
        }
        return "";
    }

    /** Percent-decoding without URLDecoder's '+' to space | URLDecoder 와 달리 '+'를 공백으로 바꾸지 않는 퍼센트 디코딩 */
    private static String percentDecode(String value, Charset charset) {
        var out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < value.length(); i++) {
            var ch = value.charAt(i);
            if (ch == '%') {
                if (i + 2 >= value.length()) {
                    throw new IllegalArgumentException("잘못된 퍼센트 인코딩: " + value);
                }
                out.write(Integer.parseInt(value.substring(i + 1, i + 3), 16)); // NumberFormatException is an IllegalArgumentException
                i += 2;
            } else {
                var bytes = String.valueOf(ch).getBytes(charset);
                out.write(bytes, 0, bytes.length);
            }
        }
        return out.toString(charset);
    }

    /** Keeps only the file name so a header cannot choose the directory | 헤더가 저장 디렉토리를 정하지 못하도록 파일명만 남김 */
    private static String lastPathSegment(String name) {
        var slashed = name.replace('\\', '/');
        var segment = slashed.substring(slashed.lastIndexOf('/') + 1).trim();
        return segment.equals(".") || segment.equals("..") ? "" : segment;
    }

    /** Default maximum number of entries for {@link #unzipFiles(InputStream, Path)} | 기본 최대 항목 수 */
    public static final int DEFAULT_UNZIP_MAX_ENTRIES = 10_000;
    /** Default maximum total uncompressed size (1 GiB) for {@link #unzipFiles(InputStream, Path)} | 기본 최대 해제 크기 (1 GiB) */
    public static final long DEFAULT_UNZIP_MAX_BYTES = 1L << 30;

    /**
     * 압축 파일을 풀어준다. 기본 제한({@value #DEFAULT_UNZIP_MAX_ENTRIES}개 항목, 1 GiB)을 적용한다.
     *
     * @param zipData 압축 파일 데이터
     * @param destDir 압축 해제할 디렉토리
     * @throws IOException 입출력 오류, 대상 디렉토리를 벗어나는 항목(Zip Slip), 제한 초과(압축 폭탄)
     * @see #unzipFiles(InputStream, Path, int, long)
     */
    public static void unzipFiles(InputStream zipData, Path destDir) throws IOException {
        unzipFiles(zipData, destDir, DEFAULT_UNZIP_MAX_ENTRIES, DEFAULT_UNZIP_MAX_BYTES);
    }

    /**
     * 압축 파일을 풀어준다.
     * <ul>
     * <li>항목 경로가 {@code destDir} 밖을 가리키면(예: {@code ../evil.sh}, 절대 경로) 아무것도 쓰지 않고 예외를 던진다. (Zip Slip 방지)</li>
     * <li>항목 수나 해제된 전체 크기가 제한을 넘으면 예외를 던진다. (압축 폭탄 방지) 이미 풀린 파일은 남는다.</li>
     * <li>상위 디렉토리가 없으면 만든다.</li>
     * </ul>
     *
     * @param zipData    압축 파일 데이터
     * @param destDir    압축 해제할 디렉토리
     * @param maxEntries 최대 항목 수
     * @param maxBytes   해제된 전체 최대 크기(바이트)
     * @throws IOException 입출력 오류, 대상 디렉토리를 벗어나는 항목, 제한 초과
     */
    public static void unzipFiles(InputStream zipData, Path destDir, int maxEntries, long maxBytes) throws IOException {
        var baseDir = destDir.toAbsolutePath().normalize();
        Files.createDirectories(baseDir);
        long totalBytes = 0;
        int entries = 0;
        try (var in = S2StreamUtil.getBufferedInputStream(zipData);
                var zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > maxEntries) {
                    throw new IOException("압축 파일의 항목 수가 제한(" + maxEntries + ")을 넘습니다.");
                }
                var newFile = resolveWithin(baseDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(newFile);
                    continue;
                }
                Files.createDirectories(newFile.getParent());
                try (var bos = new BufferedOutputStream(Files.newOutputStream(newFile))) {
                    var buffer = new byte[S2StreamUtil.getBufferSize()];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        totalBytes += len;
                        if (totalBytes > maxBytes) {
                            throw new IOException("압축 해제 크기가 제한(" + maxBytes + " bytes)을 넘습니다.");
                        }
                        bos.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    /**
     * {@code baseDir} 아래의 {@code name} 경로를 반환한다. {@code ..}나 절대 경로로 {@code baseDir} 밖을 가리키면 예외를 던진다. (Path Traversal 방지)
     *
     * @param baseDir 기준 디렉토리
     * @param name    기준 디렉토리 아래의 상대 경로 (파일명 또는 하위 경로)
     * @return 정규화된 절대 경로
     * @throws S2RuntimeException 경로가 기준 디렉토리를 벗어날 때
     */
    public static Path resolveWithin(Path baseDir, String name) {
        Objects.requireNonNull(baseDir, "baseDir");
        if (name == null || name.isBlank()) {
            throw new S2RuntimeException("잘못된 파일 경로입니다: " + name);
        }
        var base = baseDir.toAbsolutePath().normalize();
        // Treat backslashes as separators on every OS so "..\x" is also caught | 모든 OS 에서 역슬래시도 구분자로 보아 "..\x"도 차단
        var target = base.resolve(name.replace('\\', '/')).normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            throw new S2RuntimeException("잘못된 파일 경로입니다: " + name);
        }
        return target;
    }

    /**
     * 원격(SFTP 등, '/' 구분자) 경로에서 {@code baseDir} 아래의 {@code name} 경로를 반환한다. {@code ..}나 절대 경로로 {@code baseDir} 밖을 가리키면
     * 예외를 던진다. 로컬 파일 시스템과 무관하게 문자열로만 계산한다.
     *
     * @param baseDir 원격 기준 디렉토리 (예: {@code /upload/2026})
     * @param name    기준 디렉토리 아래의 상대 경로
     * @return 정규화된 원격 경로 (예: {@code /upload/2026/a.txt})
     * @throws S2RuntimeException 경로가 기준 디렉토리를 벗어날 때
     */
    public static String resolveRemoteWithin(String baseDir, String name) {
        if (baseDir == null || name == null || name.isBlank()) {
            throw new S2RuntimeException("잘못된 파일 경로입니다: " + name);
        }
        var nameSlashed = name.replace('\\', '/');
        if (nameSlashed.startsWith("/")) {
            throw new S2RuntimeException("잘못된 파일 경로입니다: " + name);
        }
        var base = normalizeRemotePath(baseDir);
        var target = normalizeRemotePath(base.isEmpty() ? nameSlashed : base + "/" + nameSlashed);
        var prefix = base.isEmpty() || base.endsWith("/") ? base : base + "/";
        if (target == null || target.isEmpty() || target.equals("..") || target.startsWith("../")
                || !target.startsWith(prefix) || target.length() == prefix.length()) {
            throw new S2RuntimeException("잘못된 파일 경로입니다: " + name);
        }
        return target;
    }

    /** Collapses ".", ".." and repeated slashes; null when ".." climbs above the root | ".", "..", 중복 슬래시 정리. 루트 위로 올라가면 null */
    private static String normalizeRemotePath(String path) {
        var absolute = path.startsWith("/");
        var segments = new ArrayList<String>();
        for (var segment : path.replace('\\', '/').split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty() || segments.get(segments.size() - 1).equals("..")) {
                    if (absolute) {
                        return null;
                    }
                    segments.add(segment);
                } else {
                    segments.remove(segments.size() - 1);
                }
                continue;
            }
            segments.add(segment);
        }
        var joined = String.join("/", segments);
        return absolute ? "/" + joined : joined;
    }

    /**
     * 파일 크기(바이트)를 B, KB, MB, GB, TB, PB로 자동 변환하고 단위 정보를 반환한다.
     */
    public static class FileSizeFormat {
        private static final long KB_IN_BYTES = 1024L;
        private static final long MB_IN_BYTES = 1024L * KB_IN_BYTES;
        private static final long GB_IN_BYTES = 1024L * MB_IN_BYTES;
        private static final long TB_IN_BYTES = 1024L * GB_IN_BYTES;
        private static final long PB_IN_BYTES = 1024L * TB_IN_BYTES;

        public static final String CD_FILE_SIZE_UNIT_B = "B";
        public static final String CD_FILE_SIZE_UNIT_KB = "KB";
        public static final String CD_FILE_SIZE_UNIT_MB = "MB";
        public static final String CD_FILE_SIZE_UNIT_GB = "GB";
        public static final String CD_FILE_SIZE_UNIT_TB = "TB";
        public static final String CD_FILE_SIZE_UNIT_PB = "PB";

        private final double value;
        private final String unit;
        private final String formattedValue;

        /**
         * 바이트(Byte) 값을 받아 B, KB, MB, GB로 자동 변환하고 객체를 초기화한다.
         *
         * @param bytes 변환할 파일 크기 (long 타입의 바이트 단위)
         */
        public FileSizeFormat(Long bytes) {
            String formatPattern;

            if (bytes == null || bytes <= 0) {
                this.value = 0;
                this.unit = CD_FILE_SIZE_UNIT_B;
                formatPattern = "#,##0";
            } else if (bytes >= PB_IN_BYTES) { // PB 단위 조건 추가 (가장 큰 단위)
                this.value = (double) bytes / PB_IN_BYTES;
                this.unit = CD_FILE_SIZE_UNIT_PB;
                formatPattern = "#,##0.00";
            } else if (bytes >= TB_IN_BYTES) {
                this.value = (double) bytes / TB_IN_BYTES;
                this.unit = CD_FILE_SIZE_UNIT_TB;
                formatPattern = "#,##0.00";
            } else if (bytes >= GB_IN_BYTES) {
                this.value = (double) bytes / GB_IN_BYTES;
                this.unit = CD_FILE_SIZE_UNIT_GB;
                formatPattern = "#,##0.00";
            } else if (bytes >= MB_IN_BYTES) {
                this.value = (double) bytes / MB_IN_BYTES;
                this.unit = CD_FILE_SIZE_UNIT_MB;
                formatPattern = "#,##0.00";
            } else if (bytes >= KB_IN_BYTES) {
                this.value = (double) bytes / KB_IN_BYTES;
                this.unit = CD_FILE_SIZE_UNIT_KB;
                formatPattern = "#,##0.0";
            } else {
                this.value = (double) bytes;
                this.unit = CD_FILE_SIZE_UNIT_B;
                formatPattern = "#,##0";
            }

            // 쉼표와 소수점 처리를 위해 DecimalFormat 사용 (ko-KR 로케일)
            DecimalFormat df = new DecimalFormat(formatPattern, new java.text.DecimalFormatSymbols(Locale.KOREA));
            this.formattedValue = df.format(this.value);
        }

        /**
         * 변환된 숫자 값을 반환한다.
         *
         * @return 변환된 값 (double)
         */
        public double getValue() {
            return value;
        }

        /**
         * 적용된 단위 문자열을 반환한다. (예: "KB")
         *
         * @return 단위 문자열
         */
        public String getUnit() {
            return unit;
        }

        /**
         * 로케일이 적용되어 쉼표가 포함된 최종 표시 문자열을 반환한다.
         *
         * @return 포맷된 값
         */
        public String getFormattedValue() {
            return formattedValue;
        }
    }

    /**
     * 클래스가 로드된 위치의 파일 시스템 경로를 가져온다. 클래스 디렉토리에서 실행하면 그 디렉토리(예: {@code build/classes/java/main}),
     * jar 에서 실행하면 jar 가 있는 디렉토리이다. 경로의 공백·한글은 디코딩된다.
     *
     * @param clazz class
     * @return 클래스 디렉토리 또는 jar 가 있는 디렉토리
     * @throws NullPointerException  clazz 가 null 일 때
     * @throws IllegalStateException 파일 시스템 경로로 나타낼 수 없는 위치일 때 (JDK 클래스, 중첩 jar 등)
     */
    public static String getApplicationRootPath(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        var codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("클래스의 위치를 알 수 없습니다: " + clazz.getName());
        }
        try {
            var location = Paths.get(codeSource.getLocation().toURI());
            return (Files.isRegularFile(location) ? location.getParent() : location).toString();
        } catch (java.net.URISyntaxException | IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            throw new IllegalStateException("파일 시스템 경로가 아닌 위치입니다: " + codeSource.getLocation(), e);
        }
    }

}
