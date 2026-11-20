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

import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.message.S2MessageKey;

/**
 * Thrown by {@link FileManager#writeFile} when a file with the same name already exists and overwriting was not asked
 * for. Retrying cannot succeed, so {@link S2UploadQueue} does not retry it.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 덮어쓰지 않는 {@link FileManager#writeFile}에서 같은 이름의 파일이 이미 있을 때 던집니다. 다시 시도해도 성공할 수 없으므로
 * {@link S2UploadQueue}는 이 예외를 재시도하지 않습니다.
 */
public class S2FileExistsException extends S2RuntimeException {

    private static final long serialVersionUID = 6203571928846319201L;

    /**
     * Creates the exception.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 예외를 만듭니다.
     * @param message Error message | 오류 메시지
     */
    public S2FileExistsException(String message) {
        super(message);
    }

    /**
     * Creates the exception with its cause.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 원인과 함께 예외를 만듭니다.
     * @param message Error message | 오류 메시지
     * @param cause   Cause | 원인
     */
    public S2FileExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception with a key for the user-facing message.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 사용자용 문구 키와 함께 예외를 만듭니다.
     * @param message    English message for logs | 로그용 영어 메시지
     * @param cause      Cause ({@code null} allowed) | 원인 ({@code null} 가능)
     * @param messageKey Key of the user-facing message | 사용자용 문구 키
     */
    public S2FileExistsException(String message, Throwable cause, S2MessageKey messageKey) {
        super(message, cause, messageKey);
    }

}
