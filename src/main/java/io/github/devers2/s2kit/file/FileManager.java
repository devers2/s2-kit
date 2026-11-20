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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2kit.file.impl.S2FileManagerImpl;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;
import io.github.devers2.s2kit.support.S2FileUtil;

/**
 * s2's utilities
 * 파일 관리 유틸리티 클래스 파일 업로드, 다운로드, 파일 정보 확인 및 파일 시스템 관련 기능 제공
 *
 * @author devers2
 * @version 1.0
 * @since 2025. 02. 01.
 */
public interface FileManager {

    S2Logger logger = S2LogManager.getLogger(FileManager.class);

    /**
     * 원격 여부
     *
     * @return 원격 여부
     */
    boolean isRemote();

    /**
     * 파일을 지정된 저장 경로에 작성합니다. 같은 이름의 파일이 이미 있으면 덮어쓰지 않고 예외를 던집니다.
     * <p>
     * 모든 구현(로컬, SFTP)이 같은 규칙을 따릅니다. 덮어쓰려면 {@link #writeFile(InputStream, String, String, boolean)}에
     * {@code overwrite = true}를 넘깁니다.
     * </p>
     *
     * @param fileData 저장할 파일 데이터 (InputStream, 이 메서드가 닫음)
     * @param savePath 저장 경로
     * @param saveName 저장할 파일명 (저장 경로를 벗어날 수 없음)
     * @return 저장된 파일의 크기(바이트 단위)
     * @throws io.github.devers2.s2util.exception.S2RuntimeException 같은 이름의 파일이 있거나, 경로가 저장 경로를 벗어나거나, 저장에 실패했을 때
     */
    default long writeFile(InputStream fileData, String savePath, String saveName) {
        return writeFile(fileData, savePath, saveName, false);
    }

    /**
     * 파일을 지정된 저장 경로에 작성합니다.
     *
     * @param fileData  저장할 파일 데이터 (InputStream, 이 메서드가 닫음)
     * @param savePath  저장 경로
     * @param saveName  저장할 파일명 (저장 경로를 벗어날 수 없음)
     * @param overwrite true 면 같은 이름의 파일을 덮어쓰고, false 면 예외를 던짐
     * @return 저장된 파일의 크기(바이트 단위)
     * @throws io.github.devers2.s2util.exception.S2RuntimeException 덮어쓰지 않는데 같은 이름의 파일이 있거나, 경로가 저장 경로를 벗어나거나,
     *                                                                저장에 실패했을 때
     */
    long writeFile(InputStream fileData, String savePath, String saveName, boolean overwrite);

    /**
     * Writes a local file so that a later call with the same {@code resumeKey} continues where a failed call stopped
     * instead of sending the whole file again. Remote managers write to a hidden partial file
     * ({@code .<name>.<resumeKey>.s2part}) next to the target, keep it when the transfer fails, and rename it to
     * {@code saveName} when it is complete. Managers without resume support (the default, and the local manager) simply
     * write the file. {@link S2UploadQueue} uses this for its retries.
     * <p>
     * Without {@code overwrite}, an existing target is checked just before the rename; another writer creating the same
     * name in that instant can still be replaced, because SFTP servers usually rename over existing files.
     * </p>
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 로컬 파일을 쓰되, 실패한 뒤 같은 {@code resumeKey}로 다시 부르면 처음부터가 아니라 멈춘 곳부터 이어 씁니다. 원격 관리자는 대상
     * 옆의 숨김 부분 파일({@code .<이름>.<resumeKey>.s2part})에 쓰고, 전송이 실패하면 남겨 두며, 다 받으면 {@code saveName}으로
     * 이름을 바꿉니다. 이어 쓰기를 지원하지 않는 관리자(기본 구현, 로컬 관리자)는 그냥 씁니다. {@link S2UploadQueue}가 재시도에
     * 씁니다.
     * <p>
     * 덮어쓰지 않을 때는 이름을 바꾸기 직전에 대상이 있는지 확인합니다. SFTP 서버는 보통 이름을 바꿀 때 기존 파일을 덮으므로, 바로 그
     * 순간 다른 쪽이 같은 이름을 만들면 덮일 수 있습니다.
     * </p>
     * @param source    Local file to write | 쓸 로컬 파일
     * @param savePath  Directory to store in | 저장 경로
     * @param saveName  File name, which cannot leave {@code savePath} | 파일명 (저장 경로를 벗어날 수 없음)
     * @param overwrite true to replace an existing file | true 면 같은 이름의 파일을 덮어씀
     * @param resumeKey Key that identifies this upload across attempts (letters, digits, {@code -} and {@code _}; null:
     *                  no resume) | 시도 사이에 이 업로드를 가리키는 키 (영문·숫자·{@code -}·{@code _}, null: 이어 쓰지 않음)
     * @return Stored size in bytes | 저장된 크기 (바이트)
     * @throws S2FileExistsException the file exists and {@code overwrite} is false | 덮어쓰지 않는데 파일이 있을 때
     * @throws S2RuntimeException    the path is invalid or the write failed | 경로가 잘못됐거나 쓰기에 실패했을 때
     */
    default long writeFile(java.nio.file.Path source, String savePath, String saveName, boolean overwrite,
            String resumeKey) {
        try {
            return writeFile(java.nio.file.Files.newInputStream(source), savePath, saveName, overwrite);
        } catch (IOException e) {
            throw new S2RuntimeException("Cannot read the file to upload: " + source, e);
        }
    }

    /**
     * Deletes the partial file a resumable {@link #writeFile(java.nio.file.Path, String, String, boolean, String)}
     * left behind, when the upload is given up. Failures are only logged. Does nothing by default.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 업로드를 포기할 때, 이어 쓰기용 {@link #writeFile(java.nio.file.Path, String, String, boolean, String)}가 남긴 부분 파일을
     * 지웁니다. 실패는 기록만 합니다. 기본 구현은 아무것도 하지 않습니다.
     * @param savePath  Directory | 저장 경로
     * @param saveName  File name | 파일명
     * @param resumeKey Key given to the write | 쓰기에 준 키
     */
    default void discardPartial(String savePath, String saveName, String resumeKey) {
    }

    /**
     * 작성된 파일로부터 데이터를 읽어온다.
     *
     * @param savePath 저장 경로
     * @param saveName 저장 파일명
     * @return 파일 내용을 담은 InputStream
     */
    InputStream readFile(String savePath, String saveName);

    /**
     * 지정된 경로의 파일을 삭제합니다.
     *
     * @param savePath 저장 경로
     * @param saveName 저장 파일명
     */
    void deleteFile(String savePath, String saveName);

    /** Connect timeout for {@link #downloadRemoteFile} | 연결 제한 시간 */
    int DOWNLOAD_CONNECT_TIMEOUT_MILLIS = 10_000;
    /** Read timeout (time without data) for {@link #downloadRemoteFile} | 읽기 제한 시간 (데이터 없는 시간) */
    int DOWNLOAD_READ_TIMEOUT_MILLIS = 60_000;

    /**
     * 원격 파일 다운로드 (원격 파일을 로컬에 저장)
     *
     * @param fileUrl  파일 URL (http/https)
     * @param savePath 저장 경로
     * @param saveName 저장 명 (저장 경로를 벗어날 수 없음)
     * @return 원격 파일 정보
     * @see #downloadRemoteFile(String, String, String, FileManager)
     */
    static S2RemoteFile downloadRemoteFile(String fileUrl, String savePath, String saveName) {
        return downloadRemoteFile(fileUrl, savePath, saveName, null);
    }

    /**
     * 원격 파일 다운로드 (파일 관리 유틸리티를 사용하여 원격 파일을 원격/로컬에 저장)
     * <p>
     * http/https URL 만 받으며({@code file:}, {@code jar:} 등은 로컬 파일을 읽을 수 있으므로 거부), 응답이 2xx 가 아니면 예외를 던진다. 요청은 한 번만
     * 보내며 파일 정보는 같은 응답의 헤더에서 읽는다. 서버가 이 URL 로 직접 요청하므로, 사용자 입력이 섞이면 호출자가 허용 호스트를 검사해야 한다 (SSRF).
     * </p>
     *
     * @param fileUrl     파일 URL (http/https)
     * @param savePath    저장 경로
     * @param saveName    저장 명 (저장 경로를 벗어날 수 없고, 같은 이름의 파일이 있으면 예외)
     * @param fileManager 파일 관리 유틸리티 (null: 로컬 파일 시스템)
     * @return 원격 파일 정보
     * @throws IllegalArgumentException 인자가 비었거나 http/https URL 이 아닐 때
     * @throws S2RuntimeException       다운로드 또는 저장에 실패했을 때
     */
    static S2RemoteFile downloadRemoteFile(String fileUrl, String savePath, String saveName, FileManager fileManager) {
        if (fileUrl == null || fileUrl.isBlank() || savePath == null || savePath.isBlank() || saveName == null
                || saveName.isBlank()) {
            throw new IllegalArgumentException("fileUrl, savePath, saveName 은 필수입니다.");
        }
        URL url;
        try {
            var uri = new URI(fileUrl.trim());
            var scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("http/https URL 만 허용됩니다: " + fileUrl);
            }
            url = uri.toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("유효하지 않은 URL 입니다: " + fileUrl, e);
        }

        // Check the save path before sending the request | 요청 전에 저장 경로 검사
        if (fileManager == null) {
            S2FileUtil.resolveWithin(Paths.get(savePath), saveName);
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MILLIS);
            var status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new S2RuntimeException("원격 파일 요청 실패 [HTTP " + status + "]: " + fileUrl);
            }
            var remoteFile = new S2RemoteFile(connection);
            try (var inputStream = connection.getInputStream()) {
                // Same rule as FileManager.writeFile: an existing file is not overwritten | writeFile 과 같은 규칙: 기존 파일은 덮어쓰지 않음
                (fileManager != null ? fileManager : new S2FileManagerImpl()).writeFile(inputStream, savePath, saveName);
            }
            return remoteFile;
        } catch (IOException e) {
            logger.error("원격 파일 다운로드 실패: {}", fileUrl, e);
            throw new S2RuntimeException("원격 파일을 다운로드할 수 없습니다: " + fileUrl, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}
