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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;
import io.github.devers2.s2util.support.S2FileUtil;

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
     * 파일을 지정된 저장 경로에 작성합니다.
     *
     * @param fileData 저장할 파일 데이터 (InputStream)
     * @param savePath 저장 경로
     * @param saveName 저장할 파일명
     * @return 저장된 파일의 크기(바이트 단위), 실패 시 -1
     */
    long writeFile(InputStream fileData, String savePath, String saveName);

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
     * @param saveName    저장 명 (저장 경로를 벗어날 수 없음)
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
        var localTarget = fileManager == null ? S2FileUtil.resolveWithin(Paths.get(savePath), saveName) : null;

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
                var writeFileSize = fileManager != null ? fileManager.writeFile(inputStream, savePath, saveName)
                        : S2FileUtil.streamToFile(inputStream, localTarget);
                if (writeFileSize == -1) {
                    throw new S2RuntimeException("원격 파일을 저장할 수 없습니다: " + fileUrl);
                }
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
