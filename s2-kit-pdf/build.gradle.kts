/**
 * S2 Kit Library - PDF Module
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

/*
 * [추가 소스 목록]
 * dynamicSourceInfoMap에 정의된 기능 키(예: 'licensesInfo')를 추가하여 관련된 소스 파일 및 라이브러리 의존성을 빌드에 자동으로 포함시킬 수 있다.
 */
extra["activeFeatures"] = setOf("licensesInfo")

/**
 * [동적 기능 소스 정보 (Feature Toggles)]
 * - 특정 기능(Feature)에 포함될 소스 파일과 라이선스 정보 정의
 */
extra["dynamicSourceInfoMap"] = mapOf(
    "licensesInfo" to mapOf(
        "licenses" to listOf(
            "README.md",
            "README.ko.md",
            "LICENSE",
            "licenses/LICENSE-APACHE-2.0",
            "licenses/LICENSE-MIT",
            "licenses/LICENSE-COMMONMARK-BSD",
            "licenses/LICENSE-AUTOLINK-MIT",
            "licenses/LICENSE-NAYUKI-QR-MIT",
            "licenses/NOTICE"
        )
    )
)

extra["excludedSources"] = emptySet<String>()
extra["artifactTestClassNames"] = emptyList<String>()

dependencies {
    api(rootProject)

    // Apache PDFBox 3.x (Apache License 2.0) - PDF 조작, 병합, 스탬프, 워터마크
    api("org.apache.pdfbox:pdfbox:3.0.4")

    // HTML 전처리 및 정리 (MIT)
    implementation("org.jsoup:jsoup:1.23.2")

    // 마크다운 파싱 (BSD / MIT) - S2MarkdownUtil
    compileOnly("org.commonmark:commonmark:0.31.0")
    compileOnly("org.commonmark:commonmark-ext-gfm-tables:0.31.0")
    compileOnly("org.commonmark:commonmark-ext-gfm-strikethrough:0.31.0")
    compileOnly("org.commonmark:commonmark-ext-autolink:0.31.0")
    compileOnly("org.commonmark:commonmark-ext-task-list-items:0.31.0")
    compileOnly("org.commonmark:commonmark-ext-heading-anchor:0.31.0")

    compileOnly(rootProject.libs.jakarta.servlet.api)
    compileOnly(rootProject.libs.caffeine)

    testImplementation(rootProject.libs.junit.jupiter.api)
    testImplementation(rootProject.libs.junit.jupiter.params)
    testRuntimeOnly(rootProject.libs.junit.jupiter.engine)
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)

    testImplementation("org.commonmark:commonmark:0.31.0")
    testImplementation("org.commonmark:commonmark-ext-gfm-tables:0.31.0")
    testImplementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.31.0")
    testImplementation("org.commonmark:commonmark-ext-autolink:0.31.0")
    testImplementation("org.commonmark:commonmark-ext-task-list-items:0.31.0")
    testImplementation("org.commonmark:commonmark-ext-heading-anchor:0.31.0")
    testImplementation(rootProject.libs.imageio.webp)
}
