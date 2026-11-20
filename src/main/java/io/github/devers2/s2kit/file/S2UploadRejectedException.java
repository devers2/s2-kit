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

import java.util.concurrent.RejectedExecutionException;

import io.github.devers2.s2util.message.S2Localizable;
import io.github.devers2.s2util.message.S2MessageKey;

/**
 * Thrown by {@link S2UploadQueue#submit} when it does not accept an upload (queue full or closed, disk nearly full).
 * It is a {@link RejectedExecutionException}, and its key renders a message in the user's language.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * {@link S2UploadQueue#submit}이 업로드를 받지 않을 때(큐가 가득 차거나 닫힘, 디스크 부족) 던집니다.
 * {@link RejectedExecutionException}이며, 키가 사용자 언어의 문구를 만듭니다.
 */
public class S2UploadRejectedException extends RejectedExecutionException implements S2Localizable {

    private static final long serialVersionUID = -2871649318823641108L;

    private final S2MessageKey messageKey;

    /**
     * Creates the exception.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 예외를 만듭니다.
     * @param message    English message for logs | 로그용 영어 메시지
     * @param messageKey Key of the user-facing message | 사용자용 문구 키
     */
    public S2UploadRejectedException(String message, S2MessageKey messageKey) {
        super(message);
        this.messageKey = messageKey;
    }

    @Override
    public S2MessageKey getMessageKey() {
        return messageKey;
    }

}
