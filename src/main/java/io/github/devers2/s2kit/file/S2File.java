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
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.devers2.s2util.core.S2Util;
import io.github.devers2.s2kit.support.S2FileUtil;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2020. 07. 08.
 */
public class S2File implements Serializable {

    private static final long serialVersionUID = -2153812958442984593L;

    private boolean isFile = false;
    // java.nio.file.Path 구현체(UnixPath 등)는 Serializable 이 아니므로, 이 필드가 채워진 채로
    // 이 클래스를 직렬화하면 NotSerializableException 이 발생한다. transient 로 표시해 직렬화
    // 시에는 제외하고, 나머지 파생 필드(directoryPath/baseName/extension/size)들로 메타데이터를
    // 보존한다 - 어차피 Path 는 로컬 파일시스템에 종속적이라 원격/역직렬화 환경에서 그대로 복원해도
    // 의미가 없다.
    private transient Path file;

    /** 디렉토리 경로 */
    private String directoryPath = "";
    /** 확장자 제외 파일명 */
    private String baseName = "";
    /** 확장자 제외 원본파일명 */
    private String originalBaseName = "";
    /** 파일 확장자 */
    private String extension = "";
    /** 파일 용량 */
    private long size = 0;

    /**
     * 생성자
     *
     * @param file             파일
     * @param extension        파일 확장자(확장자 없이 파일을 저장/수정한 경우 파일명에 확장자가 없더라도 확장자를 구분하도록 추가)
     * @param originalBaseName 확장자 제외 원본파일명(null이면 확장자 제외 파일명과 동일)
     */
    public S2File(Path file, String extension, String originalBaseName) {
        this.file = file; // Path 객체 저장
        if (this.file != null) {
            if (Files.exists(this.file) && Files.isRegularFile(this.file)) {
                this.isFile = true;
            }

            // 다른 String 필드들과 마찬가지로 부모 디렉토리가 없을 때도 null 대신 "" 를 사용해,
            // 호출 측이 null 체크 없이 .isBlank() 등을 호출해도 안전하도록 한다.
            this.directoryPath = this.file.getParent() != null ? this.file.getParent().toString() : ""; // 디렉토리 경로 추출

            this.baseName = S2FileUtil.getBaseName(this.file); // 확장자 제외 파일명

            this.originalBaseName = originalBaseName; // 확장자 제외 원본파일명

            this.extension = S2FileUtil.getExtension(this.file); // 파일 확장자
            if ((this.extension == null || this.extension.isBlank()) && (extension != null && !extension.isBlank())) {
                this.extension = extension;
            }

            try {
                this.size = Files.size(this.file);
            } catch (IOException e) {
                this.size = 0;
            }
        }
    }

    /**
     * 파일 여부 getter
     */
    public boolean isFile() {
        return isFile;
    }

    /**
     * 파일 getter
     */
    public Path getFile() {
        return file;
    }

    /**
     * 디렉토리 경로 getter
     */
    public String getDirectoryPath() {
        return directoryPath;
    }

    /**
     * 확장자 제외 파일명 getter
     */
    public String getBaseName() {
        return baseName;
    }

    /**
     * 확장자 제외 원본파일명 getter
     */
    public String getOriginalBaseName() {
        return S2Util.cast(originalBaseName, baseName);
    }

    /**
     * 파일 확장자 getter
     */
    public String getExtension() {
        return extension;
    }

    /**
     * 확장자 포함 파일명 getter
     */
    public String getName() {
        return baseName + (extension != null && !extension.isBlank() ? "." + extension : "");
    }

    /**
     * 확장자 포함 원본파일명 getter
     */
    public String getOriginalName() {
        return getOriginalBaseName() + (extension != null && !extension.isBlank() ? "." + extension : "");
    }

    /**
     * 파일 용량 getter
     */
    public long getSize() {
        return size;
    }

}
