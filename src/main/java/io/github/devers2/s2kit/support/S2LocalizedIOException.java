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
package io.github.devers2.s2kit.support;

import java.io.IOException;

import io.github.devers2.s2util.message.S2Localizable;
import io.github.devers2.s2util.message.S2MessageKey;

/**
 * An {@link IOException} that a user may see (for example an unreadable image or a password-protected PDF): the
 * message stays English, and the key renders it in the user's language ({@link S2Localizable}).
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 사용자에게 보일 수 있는 {@link IOException}입니다(읽을 수 없는 이미지, 암호가 걸린 PDF 등). 메시지는 영어로 두고, 키가 사용자
 * 언어의 문구를 만듭니다({@link S2Localizable}).
 */
public class S2LocalizedIOException extends IOException implements S2Localizable {

    private static final long serialVersionUID = 4187236054318279911L;

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
    public S2LocalizedIOException(String message, S2MessageKey messageKey) {
        super(message);
        this.messageKey = messageKey;
    }

    /**
     * Creates the exception with its cause.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 원인과 함께 예외를 만듭니다.
     * @param message    English message for logs | 로그용 영어 메시지
     * @param cause      Cause | 원인
     * @param messageKey Key of the user-facing message | 사용자용 문구 키
     */
    public S2LocalizedIOException(String message, Throwable cause, S2MessageKey messageKey) {
        super(message, cause);
        this.messageKey = messageKey;
    }

    @Override
    public S2MessageKey getMessageKey() {
        return messageKey;
    }

}
