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
package io.github.devers2.s2kit;

import io.github.devers2.s2util.message.S2MessageKey;

/**
 * Keys of the user-facing messages of s2-kit, in the bundle {@value #BASENAME} (English and Korean). Exceptions a user
 * may see carry one of these keys; render it with
 * {@link io.github.devers2.s2util.message.S2Localizable#findLocalizedMessage(Throwable, java.util.Locale)}. To change a
 * text, put the same key in the application's bundle ({@code S2ResourceBundle.setDefaultBasename}).
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * s2-kit 의 사용자용 문구 키입니다(번들 {@value #BASENAME}, 영어·한국어). 사용자에게 보일 수 있는 예외가 이 키를 담으며,
 * {@link io.github.devers2.s2util.message.S2Localizable#findLocalizedMessage(Throwable, java.util.Locale)}로 문구를 만듭니다. 문구를
 * 바꾸려면 앱 번들({@code S2ResourceBundle.setDefaultBasename})에 같은 키를 둡니다.
 */
public final class S2KitMessages {

    /** Bundle base name | 번들 기본 이름 */
    public static final String BASENAME = "io.github.devers2.s2kit.messages";

    private S2KitMessages() {
    }

    /**
     * Creates a message key in the s2-kit bundle.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * s2-kit 번들의 메시지 키를 만듭니다.
     * @param key  Key, for example {@code s2kit.file.exists} | 키
     * @param args Arguments; never pass server paths | 인자 (서버 경로는 넣지 않음)
     * @return Message key | 메시지 키
     */
    public static S2MessageKey key(String key, Object... args) {
        return S2MessageKey.of(BASENAME, key, args);
    }

}
