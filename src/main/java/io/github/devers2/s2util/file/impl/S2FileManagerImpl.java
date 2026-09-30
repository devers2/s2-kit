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
package io.github.devers2.s2util.file.impl;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.file.FileManager;
import io.github.devers2.s2util.support.S2FileUtil;

/**
 * s2's utilities
 * 파일 관리 유틸리티 클래스 파일 업로드, 다운로드, 파일 정보 확인 및 파일 시스템 관련 기능 제공
 *
 * @author devers2
 * @version 1.0
 * @since 2025. 02. 01.
 */
public class S2FileManagerImpl implements FileManager {

    // private static final S2Logger logger = S2LogManager.getLogger(S2FileManagerImpl.class);

    /**
     * 원격 여부
     *
     * @return 원격 여부
     */
    public boolean isRemote() {
        return false;
    }

    /**
     * 파일을 지정된 저장 경로에 작성합니다.
     *
     * @param fileData 저장할 파일 데이터 (InputStream)
     * @param savePath 저장 경로
     * @param saveName 저장할 파일명
     * @return 저장된 파일의 크기(바이트 단위), 실패 시 -1
     */
    public long writeFile(InputStream fileData, String savePath, String saveName) {
        var fileSize = -1L;
        if (fileData != null && savePath != null && !savePath.isBlank() && saveName != null && !saveName.isBlank()) {
            fileSize = S2FileUtil.streamToFile(fileData, resolveSafePath(savePath, saveName), true);
        }
        return fileSize;
    }

    /**
     * 작성된 파일로부터 데이터를 읽어온다.
     *
     * @param savePath 저장 경로
     * @param saveName 저장 파일명
     * @return 파일 내용을 담은 InputStream
     */
    public InputStream readFile(String savePath, String saveName) {
        return S2FileUtil.fileToInputStream(resolveSafePath(savePath, saveName));
    }

    /**
     * 지정된 경로의 파일을 삭제한다.
     *
     * @param savePath 저장 경로
     * @param saveName 저장 파일명
     */
    @Override
    public void deleteFile(String savePath, String saveName) {
        S2FileUtil.delete(resolveSafePath(savePath, saveName));
    }

    /**
     * savePath 하위로 경로가 벗어나지 않도록 검증한 뒤 대상 파일 경로를 반환한다. (Path Traversal 방지)
     *
     * @param savePath 저장 경로
     * @param saveName 저장 파일명
     * @return 검증된 대상 파일 경로
     */
    private static Path resolveSafePath(String savePath, String saveName) {
        return S2FileUtil.resolveWithin(Paths.get(savePath), saveName);
    }

}
