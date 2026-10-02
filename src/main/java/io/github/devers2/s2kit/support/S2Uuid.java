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

import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2025. 05. 27.
 */
public class S2Uuid {

    /**
     * Unix Epoch 기반의 시간 순서 정렬이 가능한 UUID v7을 생성하여 반환한다.
     * (일회성 보안 토큰을 제외한 모든 부분에 사용 적합)
     * <p>
     * UUID v7은 시간 정보(Unix Epoch)와 난수가 결합된 형태로,
     * 생성된 순서대로 사전적 정렬(lexicographical sorting)이 가능하다.
     * 따라서 데이터베이스의 Primary Key(PK)로 사용될 때
     * UUID v4와 달리 인덱스 쓰기 성능 저하 문제를 크게 개선할 수 있다.
     * </p>
     *
     * @return 시간 순서가 보장되는 새로운 UUID v7 객체
     * @see <a href="https://github.com/f4b6a3/uuid-creator#uuidv7">UUID Creator Documentation for v7</a>
     */
    public static UUID generateUuidV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * 순수한 무작위 난수 기반의 UUID v4를 생성하여 반환한다.
     * (일회성 보안 토큰에 사용 적합)
     * <p>
     * UUID v4는 시간 정보 없이 난수로만 구성되어 극도의 예측 불가능성이 요구되는
     * 일회성 보안 토큰 (보안 토큰, 세션 ID, 비밀번호 재설정 링크의 ID 등)에 적합하다.
     * 다만, 시간 순서 정렬이 불가능하여 데이터베이스 PK로 사용 시 성능 문제를 유발할 수 있다.
     * </p>
     *
     * @return 순수한 무작위로 생성된 새로운 UUID v4 객체
     */
    public static UUID generateUuidV4() {
        return UUID.randomUUID();
    }

}
