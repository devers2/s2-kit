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
package io.github.devers2.s2util.spring;

import org.springframework.context.annotation.Configuration;

import io.github.devers2.s2util.log.S2LogManager;

/**
 * [🚀 S2Util 통합 자동 설정]
 *
 * Spring Boot 애플리케이션 기동 시 S2Util 라이브러리의 핵심 컴포넌트들을 자동으로 초기화한다.
 *
 * 💡 [META-INF 등록 파일 구성]
 * - META-INF/spring.factories (Spring Boot 2.x 대응)
 * - META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports (Spring Boot 3.x 대응)
 */
@Configuration
public class S2AutoConfiguration {

    public S2AutoConfiguration() {
        // Spring Bean 인스턴스화 시점에 로거 매니저 및 캐시 엔진 활성화
        S2LogManager.getLogger(S2AutoConfiguration.class);
    }

}
