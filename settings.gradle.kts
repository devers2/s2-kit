pluginManagement {
    // pluginManagement 블록 평가 시점에 사용자 홈의 gradle.properties 를 읽어올 수 없어 직접 읽어온다.
    val globalProperties = java.util.Properties()
    val globalFile = java.io.File(gradle.gradleUserHomeDir, "gradle.properties")
    if (globalFile.exists()) {
        globalFile.inputStream().use { stream ->
            globalProperties.load(stream)
        }
    }

    repositories {
        mavenCentral() // Maven Central
        gradlePluginPortal() // 기본 Gradle 플러그인 저장소
        mavenLocal()
    }
}

plugins {
    // Gradle이 필요한 JDK를 자동으로 찾고 다운로드하는 플러그인
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// JAR 파일의 기본 이름(BaseName, artifactId를 의미) 지정
rootProject.name = "s2-kit"

// 로컬 개발: 옆 폴더의 소스가 있으면 배포본 대신 그대로 사용한다 (composite build).
// 배포본(Maven Central·Gradle Plugin Portal)만으로 확인하려면 -Ps2.localBuilds=false
val useLocalBuilds = providers.gradleProperty("s2.localBuilds").orNull != "false"

val s2BuildSupportDir = file("../s2-build-support")
if (useLocalBuilds && s2BuildSupportDir.exists()) {
    // 로컬 개발용: s2-build-support 디렉토리가 존재하는 경우에만 포함 (없어도 빌드 오류 발생 방지)
    includeBuild(s2BuildSupportDir)
}

val s2UtilDir = file("../s2-util")
if (useLocalBuilds && s2UtilDir.exists()) {
    // 로컬 개발용: s2-util 디렉토리가 존재하는 경우에만 포함 (멀티 프로젝트 참조)
    includeBuild(s2UtilDir)
}
