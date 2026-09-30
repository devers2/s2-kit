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
     * 파일 시스템의 디렉토리 생성
     *
     * @param path 생성할 경로(문자열)
     * @return 생성 여부
     */
    public static boolean makeDirectory(String path) {
        var result = false;
        if (path != null && !path.isBlank()) {
            result = makeDirectory(Paths.get(path));
        }
        return result;
    }

    /**
     * 파일 시스템의 디렉토리 생성
     *
     * @param path 생성할 경로(Path)
     * @return 생성 여부
     */
    public static boolean makeDirectory(Path path) {
        boolean result = false;

        if (path != null) {
            try {
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                    if (Files.exists(path)) {
                        result = true;
                    } else {
                        logger.error("디렉토리 생성 확인 실패: {}", path);
                    }
                } else {
                    result = true;
                }
            } catch (NoSuchFileException e) {
                logger.error("디렉토리 생성 실패 (상위 디렉토리 없음): {}", path, e);
            } catch (AccessDeniedException e) {
                logger.error("디렉토리 생성 실패 (권한 없음): {}", path, e);
            } catch (IOException e) {
                logger.error("디렉토리 생성 실패: {}", path, e);
            }
        }
        return result;
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
     * 주어진 경로(파일 또는 디렉토리)와 그 하위 모든 콘텐츠를 삭제
     *
     * @param delPath 삭제할 경로 (파일 또는 디렉토리 경로)
     * @return 삭제 성공 여부 (true: 하나 이상 삭제 성공 또는 경로 없음, false: 삭제 실패 또는 null 입력)
     * @details
     *          <dl>
     *          <dd>- 단일 파일이면 파일만 삭제.</dd>
     *          <dd>- 디렉토리면 디렉토리와 하위 모든 파일/폴더를 재귀적으로 삭제.</dd>
     *          <dd>- 경로가 존재하지 않으면 성공(true)으로 처리.</dd>
     *          <dd>- 하나라도 삭제 성공 시 true 반환, 실패 시 로깅 후 false 반환 가능.</dd>
     *          </dl>
     */
    public static boolean delete(String delPath) {
        if (delPath == null) {
            return false;
        }

        return delete(Paths.get(delPath));
    }

    /**
     * 주어진 경로(파일 또는 디렉토리)와 그 하위 모든 콘텐츠를 삭제
     *
     * @param delPath 삭제할 경로 (파일 또는 디렉토리 경로)
     * @return 삭제 성공 여부 (true: 하나 이상 삭제 성공 또는 경로 없음, false: 삭제 실패 또는 null 입력)
     * @details
     *          <dl>
     *          <dd>- 단일 파일이면 파일만 삭제.</dd>
     *          <dd>- 디렉토리면 디렉토리와 하위 모든 파일/폴더를 재귀적으로 삭제.</dd>
     *          <dd>- 경로가 존재하지 않으면 성공(true)으로 처리.</dd>
     *          <dd>- 하나라도 삭제 성공 시 true 반환, 실패 시 로깅 후 false 반환 가능.</dd>
     *          </dl>
     */
    public static boolean delete(Path delPath) {
        var success = new AtomicBoolean(false);
        if (delPath == null) {
            return false;
        }

        if (!Files.exists(delPath)) {
            success.set(true); // 존재하지 않는 경로는 성공으로 처리
            return success.get();
        }

        // 단일 파일이면 직접 삭제
        if (Files.isRegularFile(delPath)) {
            try {
                Files.deleteIfExists(delPath);
                success.set(true);
            } catch (IOException e) {
                logger.error("File 삭제 실패: {} {}", delPath.toString(), e.getMessage(), e);
            }
            return success.get();
        }

        // 디렉토리면 재귀 삭제
        try (var sPath = Files.walk(delPath)) {
            // 하위 항목부터 상위 항목 순으로 정렬하여 삭제
            sPath.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p); // 파일 또는 빈 디렉토리 삭제
                    success.set(true);
                } catch (IOException e) {
                    logger.error("Path 삭제 실패: {} {}", p, e.getMessage(), e);
                }
            });
        } catch (IOException e) {
            logger.error("Path 탐색 또는 삭제 중 오류: {} {}", delPath.toString(), e.getMessage(), e);
        }

        return success.get();
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
     */
    public static Reader fileToReader(String fileFullPath) {
        Reader result = null;
        if (fileFullPath != null && !fileFullPath.isBlank()) {
            result = fileToReader(Paths.get(fileFullPath));
        }
        return result;
    }

    /**
     * 파일을 Reader 로 변환한다.(문자 스트림: .txt, .csv, .xml, .json 파일과 같은 텍스트 기반 파일)
     *
     * @param fileFullPath 파일 전체 경로(Path)
     * @return InputStream
     */
    public static Reader fileToReader(Path fileFullPath) {
        Reader result = null;
        if (fileFullPath != null) {
            if (S2Util.isNotEmpty(fileFullPath)) {
                try {
                    result = Files.newBufferedReader(fileFullPath, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    logger.error("파일을 열지 못했습니다.", e);
                    throw new S2RuntimeException("파일을 열지 못했습니다.");
                }
            }
        }
        return result;
    }

    /**
     * 파일을 InputStream 으로 변환한다.(바이트 스트림: 이미지, 오디오, 비디오 파일 또는 실행 파일 같은 바이너리 데이터)
     *
     * @param fileFullPath 파일 전체 경로(문자열)
     * @return InputStream
     */
    public static InputStream fileToInputStream(String fileFullPath) {
        InputStream result = null;
        if (fileFullPath != null && !fileFullPath.isBlank()) {
            result = fileToInputStream(Paths.get(fileFullPath));
        }
        return result;
    }

    /**
     * 파일을 InputStream 으로 변환한다.(바이트 스트림: 이미지, 오디오, 비디오 파일 또는 실행 파일 같은 바이너리 데이터)
     *
     * @param fileFullPath 파일 전체 경로(Path)
     * @return InputStream
     */
    public static InputStream fileToInputStream(Path fileFullPath) {
        InputStream result = null;
        if (fileFullPath != null) {
            if (!Files.exists(fileFullPath) || !Files.isRegularFile(fileFullPath)) {
                throw new S2RuntimeException("파일이 존재하지 않습니다.");
            }
            try {
                result = Files.newInputStream(fileFullPath);
            } catch (IOException e) {
                logger.error("파일을 열지 못했습니다.", e);
                throw new S2RuntimeException("파일을 열지 못했습니다.");
            }
        }
        return result;
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceStream 처리할 InputStream
     * @return 임시 파일
     */
    public static Path streamToTempFile(InputStream sourceStream) {
        return streamToTempFile(sourceStream, false);
    }

    /**
     * Stream 을 임시 파일로 저장한다.
     *
     * @param sourceReader 처리할 Reader
     * @return 임시 파일
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
     */
    private static Path closeableToTempFile(Closeable sourceStream, String fileExtension, boolean shouldCloseStream) {
        Path result = null;
        if (sourceStream != null)
            try {
                Path tempFile = Files.createTempFile("s2_tmp_" + S2Uuid.generateUuidV7() + "_",
                        "." + (fileExtension == null || fileExtension.isBlank() ? "tmp" : fileExtension));
                if (closeableToFile(sourceStream, tempFile, shouldCloseStream) != -1) {
                    result = tempFile;
                }
            } catch (IOException e) {
                logger.error("임시 파일 생성 실패: ", e);
            }
        return result;
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceStream   처리할 InputStream
     * @param targetFilePath 대상 파일 경로
     */
    public static Path streamToFile(InputStream sourceStream, String targetFilePath) {
        return streamToFile(sourceStream, targetFilePath, false);
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceReader   처리할 Reader
     * @param targetFilePath 대상 파일 경로
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
     */
    public static Path streamToFile(InputStream sourceStream, String targetFilePath, boolean shouldCloseStream) {
        Path result = null;
        if (targetFilePath != null && !targetFilePath.isBlank()) {
            Path targetFile = Paths.get(targetFilePath);
            if (streamToFile(sourceStream, targetFile, shouldCloseStream) != -1) {
                result = targetFile;
            }
        }
        return result;
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceReader      처리할 Reader
     * @param targetFilePath    대상 파일 경로
     * @param shouldCloseStream sourceStream 을 닫을지 여부
     *
     */
    public static Path streamToFile(Reader sourceReader, String targetFilePath, boolean shouldCloseStream) {
        Path result = null;
        if (targetFilePath != null && !targetFilePath.isBlank()) {
            Path targetFile = Paths.get(targetFilePath);
            if (streamToFile(sourceReader, targetFile, shouldCloseStream) != -1) {
                result = targetFile;
            }
        }
        return result;
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceStream 처리할 InputStream
     * @param targetFile   대상 파일
     * @return 생성된 파일의 크기(바이트 단위), 실패 시 -1
     */
    public static long streamToFile(InputStream sourceStream, Path targetFile) {
        return streamToFile(sourceStream, targetFile, false);
    }

    /**
     * Stream 을 파일로 복사한다.
     *
     * @param sourceReader 처리할 Reader
     * @param targetFile   대상 파일
     * @return 생성된 파일의 크기(바이트 단위), 실패 시 -1
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
     * @return 생성된 파일의 크기(바이트 단위), 실패 시 -1
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
     * @return 생성된 파일의 크기(바이트 단위), 실패 시 -1
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
     * @return 생성된 파일의 크기(바이트 단위), 실패 시 -1
     */
    private static long closeableToFile(Closeable sourceStream, Path targetFile, boolean shouldCloseStream) {
        var fileSize = -1L; // 실패 시 반환값
        if (sourceStream != null && targetFile != null) {
            BufferedReader reader = null;

            try {
                makeDirectory(targetFile.getParent()); // makeDirectory 호출 위치 수정
                if (sourceStream instanceof InputStream inputStream) {
                    fileSize = Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                } else if (sourceStream instanceof Reader r) {
                    reader = (r instanceof BufferedReader br) ? br : new BufferedReader(r);
                    try (var writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
                        var buffer = new char[S2StreamUtil.getBufferSize()];
                        int charsRead;
                        while ((charsRead = reader.read(buffer)) != -1) {
                            writer.write(buffer, 0, charsRead);
                        }
                    }
                    fileSize = Files.size(targetFile); // 실제 파일 크기 확인
                } else {
                    logger.error("지원하지 않는 스트림 타입(InputStream 또는 Reader 만 가능): {}", sourceStream.getClass());
                }
            } catch (IOException e) {
                logger.error("파일 복사 실패: {}", e.getMessage());
            } finally {
                if (shouldCloseStream) {
                    if (reader != null) {
                        S2StreamUtil.closeStream(reader);
                    } else {
                        S2StreamUtil.closeStream(sourceStream);
                    }
                }
            }
        }
        return fileSize;
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
            // 임시 파일 삭제
            delete(tempFile);
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
            // 임시 파일 삭제
            delete(tempFile);
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
     */
    public static long getSize(Path sourceFile) {
        long size = 0;
        if (sourceFile != null) {
            try {
                size = Files.size(sourceFile);
            } catch (IOException e) {
                logger.error("파일 크기 확인 실패: ", e);
            }
        }
        return size;
    }

    /**
     * MIME TYPE 에 매핑되는 기본 확장자 맵 (MIME TYPE 에서 확인되지 않으면 확장자로 확인)
     */
    // 매 호출마다 새로 만들 필요 없이 한 번만 초기화해서 재사용한다.
    private static final Map<String, String> MIME_TYPE_TO_EXTENSION = Map.ofEntries(
            Map.entry("audio/aac", "aac"),
            Map.entry("application/x-abiword", "abw"),
            Map.entry("video/x-msvideo", "avi"),
            Map.entry("application/vnd.amazon.ebook", "azw"),
            Map.entry("application/octet-stream", "bin"),
            Map.entry("application/x-bzip", "bz"),
            Map.entry("application/x-bzip2", "bz2"),
            Map.entry("application/x-csh", "csh"),
            Map.entry("text/css", "css"),
            Map.entry("text/csv", "csv"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/epub+zip", "epub"),
            Map.entry("image/gif", "gif"),
            Map.entry("text/html", "html"),
            Map.entry("image/x-icon", "ico"),
            Map.entry("text/calendar", "ics"),
            Map.entry("application/java-archive", "jar"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("text/javascript", "js"),
            Map.entry("application/json", "json"),
            Map.entry("audio/midi", "midi"),
            Map.entry("video/mpeg", "mpeg"),
            Map.entry("application/vnd.apple.installer+xml", "mpkg"),
            Map.entry("application/vnd.oasis.opendocument.presentation", "odp"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", "ods"),
            Map.entry("application/vnd.oasis.opendocument.text", "odt"),
            Map.entry("audio/ogg", "oga"),
            Map.entry("video/ogg", "ogv"),
            Map.entry("application/ogg", "ogx"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/vnd.ms-powerpoint", "ppt"),
            Map.entry("application/x-rar-compressed", "rar"),
            Map.entry("application/rtf", "rtf"),
            Map.entry("application/x-sh", "sh"),
            Map.entry("image/svg+xml", "svg"),
            Map.entry("application/x-shockwave-flash", "swf"),
            Map.entry("application/x-tar", "tar"),
            Map.entry("image/tiff", "tif"),
            Map.entry("application/x-font-ttf", "ttf"),
            Map.entry("application/vnd.visio", "vsd"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("audio/webm", "weba"),
            Map.entry("video/webm", "webm"),
            Map.entry("image/webp", "webp"),
            Map.entry("application/x-font-woff", "woff"),
            Map.entry("application/xhtml+xml", "xhtml"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"));

    public static String getExtensionByMimeType(Path sourceFile) {
        String extension = "";

        if (sourceFile != null && Files.exists(sourceFile) && Files.isRegularFile(sourceFile)) {
            var mimeType = getContentType(sourceFile);
            extension = MIME_TYPE_TO_EXTENSION.getOrDefault(mimeType, "");
        }

        return extension;
    }

    /**
     * 파일의 ContentType 을 가져온다.
     *
     * @param sourceFile 대상 파일
     * @return ContentType
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
            /*
             * JAVA 6 확장자가 없거나 판단하지 못할때는 "application/octet-stream"리턴 MimetypesFileTypeMap
             * fileTypeMap = new MimetypesFileTypeMap(); mimeType =
             * fileTypeMap.getContentType(sourceFile);
             */
        }
        return mimeType;
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
     * 애플리케이션의 루트 디렉토리에 해당하는 실제 파일 시스템 경로를 가져온다.
     *
     * @param clazz class
     * @return 어플리케이션 루트 경로
     */
    public static String getApplicationRootPath(Class<?> clazz) {
        String rootPath = "";
        if (clazz != null) {
            rootPath = Objects.requireNonNull(clazz.getClassLoader().getResource("")).getPath();
        }
        return rootPath;
    }

}
