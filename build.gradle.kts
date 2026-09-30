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

/*
 * [Version Catalog]
 * 의존성 및 플러그인 버전은 'gradle/libs.versions.toml' 파일에서 통합 관리
 * 별도 설정 없이 Gradle이 기본 경로(gradle/libs.versions.toml)를 자동으로 인식하여 'libs' 접근자로 제공
 */
plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.s2.build.support)

    /*
     * ⭐ [Shadow / Relocation 스위치 — 이 alias와 아래의 shadedPackagePrefix는 반드시 함께 켜고 꺼야 한다]
     * 이 alias만 단독으로 켜면 S2BuildUtils는 이를 사용하지 않는다 (shadowJar 태스크는 등록되지만
     * assemble/publish 파이프라인과 무관하게 방치됨). shadedPackagePrefix까지 함께 설정해야만
     * S2BuildUtils가 shadowJar를 실제 패키징에 편입시킨다.
     *
     * - 둘 다 꺼짐(기본값, 지금 상태): 배포 시 Standard JAR(의존성 미포함, POM으로 전이),
     *   로컬 빌드 시 Fat JAR(runtimeClasspath 전체 병합, relocation 없음) — Shadow 플러그인 미관여.
     * - 둘 다 켜짐: 배포 시 Shaded JAR(implementation/runtimeOnly는 relocate되어 JAR에 포함,
     *   api는 JAR에서 빠지고 POM에만 compile scope로 추가됨),
     *   로컬 빌드 시 Relocated Fat JAR(api 포함 전체를 담되 api를 제외한 나머지만 relocate) — shadowJar 사용.
     *
     * 자세한 4가지 조합 표는 S2BuildUtils 클래스 최상단 Javadoc(Packaging Strategies) 참고.
     */
    // alias(libs.plugins.shadow)
}

import io.github.devers2.buildsupport.S2BuildUtils

/*
 * Maven 좌표 설정
 * - group: 프로젝트의 그룹 ID (예: io.github.devers2, com.example)
 * - version: 프로젝트 버전
 *
 * ❗중요: 동일한 소스를 다른 조직/목적으로 배포하는 경우 group을 다르게 설정해야 한다.
 *   예시) 원본: io.github.devers2, 포크: com.company
 *   이렇게 하면 의존성 관리 도구가 서로 다른 아티팩트로 인식하여 같은 리포지토리라도 별도 아티팩트로 취급된다.
 *
 * 💡 [문서 버전 자동 동기화 안내]
 * Gradle 빌드 또는 태스크 실행 시, 아래 version에 지정된 값으로
 * S2BuildUtils.updateReadmeWithVersionAndDependencies()에 설정된 대상 파일들
 * (README.md, README.ko.md 등)의 의존성 코드 블록(Gradle/Maven) 및 인라인 버전이
 * 현재 version 값으로 자동 동기화됩니다.
 * 하단 버전 고지(Version: x.x.x)의 경우, 기존 문서의 버전과 다를 때(버전 변경 시)
 * 새 버전 번호와 실행 당일의 릴리즈 날짜(YYYY-MM-DD)로 함께 자동 갱신됩니다.
 */
group = "io.github.devers2.internal"
version = "2.0.0"

// Shadow Plugin - Relocation 패키지 설정
// ⚠️ 이 값을 설정해도 위 plugins{} 블록의 shadow alias가 함께 켜져 있지 않으면 아무 효과가 없다 (둘 다 켜야 함).
// extra["shadedPackagePrefix"] = "io.github.devers2.s2util.shaded"

// ========================================================================
// ⭐ [사용자 설정 (User Configuration)]
// 개발자가 프로젝트 상황에 맞춰 자주 변경하거나 확인해야 하는 설정
// ========================================================================

/**
 * 기본 Java 버전 설정: JavaVersion.current() 또는 JavaVersion.VERSION_17, JavaVersion.VERSION_25 등 설정 가능
 * (JavaVersion.current(): 현재 실행 중인 JVM 버전)
 *
 * Java 버전에 따른 의존성 설정
 * JavaVersion.VERSION_10 이하: javax.servlet
 * JavaVersion.VERSION_11 이상: jakarta.servlet
 *
 * Jakarta EE 버전   서블릿 버전     패키지 명칭        일반적으로 사용되는 Java 버전
 * -----------------------------------------------------------------------------
 * Jakarta EE 8      Servlet 4.0    javax.servlet      Java 8, Java 11 등
 * Jakarta EE 9      Servlet 5.0    jakarta.servlet    Java 11, Java 17 등
 * Jakarta EE 10     Servlet 6.0    jakarta.servlet    Java 17, Java 21 등
 */
extra["javaVersion"] = JavaVersion.VERSION_21

/**
 * [배포 바이트코드 타겟 (Release Compatibility)]
 * javaVersion(툴체인 JDK)과 다르게 설정하면, 최신 JDK로 컴파일하면서도 이전 Java 버전과
 * 호환되는 바이트코드를 생성한다 (S2BuildUtils.configureJavaCompatibility 참고).
 * 현재는 Java 21로 컴파일하되 Java 17에서도 실행 가능하도록 17로 고정한다.
 */
extra["releaseCompatibility"] = JavaVersion.VERSION_17

/*
 * ========================================================================
 * ⭐ [동적 기능 활성화 설정 (Active Features Toggle)]
 * ========================================================================
 * 아래 'dynamicSourceInfoMap'에 정의된 기능 키(예: 'S2PdfUtil', 'licensesInfo')를
 * 'activeFeatures' Set에 추가하면, 해당 기능에 매핑된:
 *   1) 소스 파일 (sources: 예, S2PdfUtil.java)
 *   2) 라이브러리 의존성 (dependencies: 예, jsoup, openhtmltopdf-pdfbox)
 *   3) 라이선스 문서 (licenses: 예, README-LGPL-2.1-PDF.md)
 * 가 빌드 파이프라인에 자동으로 활성화(포함)되어 컴파일 및 패키징됩니다.
 *
 * 💡 반대로 'activeFeatures'에서 해당 기능 키를 제외(제거)하면:
 *   - 관련 소스 파일과 라이브러리 의존성, 라이선스가 빌드에서 완전히 비활성화(제외)되어
 *   - 특정 대용량 의존성이나 불필요한 서드파티 라이브러리를 배제한 초경량 배포용 아티팩트를 구성할 수 있습니다.
 *
 * 설정 예시)
 *   - S2PdfUtil 포함 (기본값): setOf("licensesInfo", "S2PdfUtil")
 *   - S2PdfUtil 제외 (경량화): setOf("licensesInfo")
 */
extra["activeFeatures"] = setOf("licensesInfo", "S2PdfUtil")

/**
 * [동적 기능 소스 정보 (Feature Toggles Definition)]
 * - 각 기능(Feature) 키별로 결합될 소스 파일(sources), 라이브러리 의존성(dependencies), 라이선스(licenses) 정보 정의
 * - 'activeFeatures'에 등록된 키의 항목들만 선별적으로 빌드 파이프라인에 동적 주입됨
 */
extra["dynamicSourceInfoMap"] = mapOf(
    "licensesInfo" to mapOf(
        "licenses" to listOf(
            "README.md",
            "README.ko.md",
            "LICENSE",
            "licenses/LICENSE-APACHE-2.0",
            "licenses/LICENSE-EPL-2.0",
            "licenses/LICENSE-LGPL-2.1",
            "licenses/LICENSE-MIT",
            "licenses/LICENSE-MPL-2.0",
            "licenses/LICENSE-JSCH-BSD",
            "licenses/NOTICE"
        )
    ),
    "S2PdfUtil" to mapOf(
        /*
         * OpenHTML to PDF (LGPL 2.1) - S2Pdf 관련 의존성으로 LGPL 2.1 라이선스 준수를 위해 compileOnly로 사용(Shadow/Bundle 방지)
         * 최종 사용자가 의존성을 직접 추가해야 하며 Shadow JAR에서 쉐이딩/번들링 되지 않도록 방지해야 함
         */
        /* "variantId" to "pdf", */
        "sources" to listOf("io/github/devers2/s2util/support/S2PdfUtil.java"),
        "dependencies" to listOf(
            mapOf( // (jsoup은 MIT이지만 openhtmltopdf와 함께 동작하므로 동일하게 처리)
                "configuration" to "compileOnly",
                "group" to "org.jsoup",
                "name" to "jsoup",
                "version" to "1.23.2"
            ),
            mapOf( // LGPL 2.1 라이선스
                "configuration" to "compileOnly",
                "group" to "io.github.openhtmltopdf",
                "name" to "openhtmltopdf-pdfbox",
                "version" to "1.1.85"
            )
        ),
        "licenses" to listOf("README-LGPL-2.1-PDF.md")
    )
)

/*
 * 기본 제외 소스 목록 (항상 제외되는 파일들)
 */
extra["excludedSources"] = emptySet<String>()

/*
 * 빌드 완료 후 결과물을 테스트할 클래스 지정 (shadowJar 실행 후 testArtifact 태스크로 수행됨, 여러 개 지정 가능)
 */
extra["artifactTestClassNames"] = emptyList<String>()

/**
 * [기준 Java 버전: Artifact ID 생성 기준]
 * javaVersion이 baselineJavaVersion과 다를 경우 아티팩트 ID에 접미사 추가
 */
extra["baselineJavaVersion"] = JavaVersion.VERSION_21


// ========================================================================
// ⭐ [상수 및 환경 설정 (Constants & Environment)]
// 프로젝트 구조나 외부 환경과 관련된 설정 (변경 빈도 낮음)
// ========================================================================

val javaSrcRoot = "src/main/java"
val resourcesSrcRoot = "src/main/resources"
// JAVA_SRC_ROOT는 S2BuildUtils가 .java <-> .java.txt 소스 토글에 읽어가므로 extra로 노출한다.
extra["JAVA_SRC_ROOT"] = javaSrcRoot


// ========================================================================
// ⭐ [빌드 로직 (Build Logic & Calculations)]
// 위 설정값들을 기반으로 실제 빌드에 필요한 값 계산 및 유틸리티 호출
// ※ 특별한 이유가 없다면 수동 수정 지양
// ========================================================================

/*
 * [Java 버전 오버라이드]
 * -PtargetJavaVersion=21 옵션으로 덮어쓰기 가능
 */
if (project.hasProperty("targetJavaVersion")) {
    extra["javaVersion"] = JavaVersion.toVersion(project.findProperty("targetJavaVersion")!!)
}

/*
 * [동적 기능 모듈 오버라이드]
 * -PtargetSources=S2PdfUtil,OtherFeature 옵션으로 덮어쓰기 가능
 */
if (project.hasProperty("targetSources")) {
    val targetSourcesVal = project.findProperty("targetSources")
    val sources = targetSourcesVal.toString().split(",")
    extra["activeFeatures"] = sources.map { it.trim() }.toSet()
}


// ========================================================================
// ⭐ [단일 프로젝트 구성]
// ========================================================================

// JAR 패키징 명시적 필터링(아래)에서 사용하기 위해 activeFeatures를 별도로 캐스팅해 둔다.
@Suppress("UNCHECKED_CAST")
val projectActiveFeatures = extra["activeFeatures"] as Set<String>

repositories {
    mavenCentral()
}

sourceSets {
    main {
        java.setSrcDirs(listOf(javaSrcRoot))
        resources.setSrcDirs(listOf(resourcesSrcRoot))
    }
}

// 프로젝트 통합 설정
S2BuildUtils.configureProject(project)

// 저작권 연도 업데이트 (수정이 필요한 경우에만 파일 IO 발생)
S2BuildUtils.updateCopyright(
    project,
    arrayOf(
        javaSrcRoot,
        "src/main/kotlin",
        "src/main/python",
        resourcesSrcRoot,
        "src/main/webapp",
        "README.md",
        "README.ko.md",
        "build.gradle.kts"
    )
)

// 컴파일러 옵션(인코딩, -parameters)은 s2-build-support 플러그인이 apply 시 자동 처리한다.

/**
 * [JAR 패키징 명시적 필터링]
 * S2BuildUtils를 통해 자동 관리되지만, activeFeatures 설정에 따라
 * 특정 라이선스 파일이 중복 포함되는 것을 방지하기 위해 명시적으로 exclude 처리한다.
 */
tasks.withType<Jar>().configureEach {
    if (!projectActiveFeatures.contains("S2PdfUtil")) {
        exclude("README-LGPL-2.1-PDF.md")
    }
}

/*
 * [표준 라이브러리 배포 설정 (원콜)]
 * 아티팩트 ID 접미사, 툴체인/source-target 호환성(javaVersion → releaseCompatibility),
 * Javadoc/Sources JAR, "mavenJava" Publication(POM 라이선스/개발자/SCM 포함),
 * CentralPortal 리포지토리 등록(+서명)을 한 번에 처리한다.
 * [참고] GitHub Packages(s2-packages) 배포가 다시 필요해지면 아래 한 줄만 추가하면 된다:
 *   S2BuildUtils.configureGitHubPackagesRepository(project, "devers2", "s2-util")
 */
S2BuildUtils.configureLibraryPublishing(
    project,
    project.name,
    "S2Util Library - A comprehensive utility library for Java",
    "https://github.com/devers2/s2-util"
)

// JUnit 5(Jupiter) 플랫폼 사용 + 테스트 JVM 인코딩 강화 (S2BuildUtils.configureTestDefaults)
S2BuildUtils.configureTestDefaults(project)

// 'Tasks → other → copyDependencies' 실행 시 지정 디렉토리로 의존성 복사
S2BuildUtils.registerCopyDependenciesTask(project)


// --------------------------------------------------------------------------------------
// README 파일 버전 & 의존성 가이드 업데이트 (정규식 패턴 문자열로 전달)
// --------------------------------------------------------------------------------------
S2BuildUtils.updateReadmeWithVersionAndDependencies(project, "^README(\\..+)?\\.md$")


// ========================================================================
// ⭐ [의존성 설정]
// ========================================================================

dependencies {
    /**
     * implementation: 컴파일 및 런타임 시 모두 사용함
     * - 해당 의존성이 프로젝트의 빌드 결과물(JAR)에 직접적인 영향을 미침
     * - 이 라이브러리를 사용하는 다른 프로젝트(상위 모듈)에는 의존성이 노출되지 않음 (API 캡슐화)
     * - 런타임에 반드시 필요한 라이브러리인 경우 이 방식을 사용함
     */
    implementation(libs.jsch) // SFTP
    implementation(libs.commons.pool2) // Object Pooling

    // LZ4 압축 알고리즘: xxHash 사용을 위해 추가
    implementation(libs.lz4)

    /*
     * 표준 UUID 생성 라이브러리 (v1~v7 지원, RFC 9562)
     * v4: 완전 무작위 (java.util.UUID 호환)
     * v7: 시간순 정렬 가능 + 난수 (DB 성능/보안 최적)
     * v1: 시간순 정렬 가능하나 보안(MAC 노출) 취약 미사용 권장
     */
    implementation(libs.uuid.creator)

    /**
     * api: 컴파일 및 런타임 시 모두 사용하며, 소비자 프로젝트에도 transitive dependency로 노출됨
     * - s2-support 전체에서 핵심적으로 사용 (26개 파일, 80+ import)하므로 반드시 런타임에 필요함
     * - api로 선언함으로써 소비자가 s2-core를 별도로 선언하지 않아도 자동으로 포함됨
     * - JAR 크기에는 영향 없음 (Shadow 플러그인 미사용 상태이므로 번들링 없이 POM에만 기록됨)
     */
    api(libs.s2.core)
    compileOnly(libs.jsr305) // JSR-305 (@Nullable 등 Spring 애너테이션 메타데이터 인식 및 Javadoc When.MAYBE 경고 방지)
    compileOnly(libs.spring6.context) // Java 17 이상으로 개발하므로 Spring 6 및 Spring Boot 3 계열이 표준
    compileOnly(libs.spring6.web) // Java 17 이상으로 개발하므로 Spring 6 및 Spring Boot 3 계열이 표준
    compileOnly(libs.spring.integration.sftp)
    compileOnly(libs.jakarta.servlet.api) // Java 17 이상으로 개발하므로 jakarta 가 표준
    compileOnly(libs.jakarta.servlet.jsp) // Java 17 이상으로 개발하므로 jakarta 가 표준
    compileOnly(libs.aspectj.weaver)

    /**
     * testImplementation: 테스트 컴파일 및 런타임 시 모두 사용
     * - 테스트 코드에서 사용하는 라이브러리인 경우 이 방식을 사용
     */
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.s2.core)
    testImplementation("org.jsoup:jsoup:1.23.2")
    testImplementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.85")
}
