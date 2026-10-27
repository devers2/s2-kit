# s2-kit — 팀 공통 Java·웹 모듈

🌐 [English](README.md) | **한국어**

[![Java CI](https://github.com/devers2/s2-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/devers2/s2-kit/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.devers2.internal/s2-kit?color=brightgreen&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.devers2.internal/s2-kit)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](./LICENSE)

> [s2-util](https://github.com/devers2/s2-util)(`s2-core`) 위에 만든 **팀 공통 모듈**입니다. 파일, 페이징, Spring·서블릿 헬퍼, 프런트 JS/CSS를 모았습니다.
> (PDF 변환·병합 및 웹 뷰어가 필요하다면 전용 서브모듈인 **[`s2-kit-pdf`](s2-kit-pdf/README.ko.md)**를 사용하십시오). `s2-support`의 새 이름입니다.

---

## 📖 개요 (Overview)

`s2-kit`은 실용적인 Java/Spring 애플리케이션 개발에 맞춰 자주 사용하는 헬퍼 모듈과 반복적인 보일러플레이트를 모아둔 라이브러리입니다. 파일 관리, 페이징, Spring 컨텍스트 유틸리티, JSON/암호화/이미지 헬퍼 등을 포함합니다.

---

## 🧭 이 라이브러리에 대해

- **성격:** 개인·팀 프로젝트에서 함께 쓰려고 만든 공통 모듈입니다. 공개 저장소이고 Maven Central 에 있지만, 범용 라이브러리처럼
  모든 환경을 지원하는 것을 목표로 하지 않습니다. 그룹 ID 의 `internal`이 이 뜻입니다.
- **호환 정책:** 버전은 `주.부.수`를 따릅니다. 수 버전은 고침, 부 버전은 기능 추가이며 호환을 유지합니다. 호환이 깨지는 변경은 주
  버전에서만 하고, [CHANGELOG](./CHANGELOG.ko.md)에 ⚠️ 표시와 옮기는 방법을 적습니다.
- **기반:** `s2-core`(s2-util)를 함께 가져옵니다. `s2-core`의 변경은 [s2-util CHANGELOG](https://github.com/devers2/s2-util/blob/main/CHANGELOG.ko.md)를
  보십시오.
- **기능별로 더 필요한 것:**
  - PDF 엔진, 오피스·한글 문서 변환 및 웹 PDF 뷰어: 전용 서브모듈인 **[`s2-kit-pdf`](s2-kit-pdf/README.ko.md)**를 참조하십시오.
  - 선택 의존성(Spring, 서블릿, SFTP 등): 쓰는 기능만 추가합니다. 목록은 이 문서 맨 아래에 있습니다.
- **문의:** GitHub Issues 또는 [eseungsu.dev@gmail.com](mailto:eseungsu.dev@gmail.com).

### 🔀 s2-support 에서 옮기기

`s2-support` 1.x 를 쓰던 프로젝트는 다음을 바꾸면 됩니다. 옛 `s2-support` 1.x 는 Maven Central 에 그대로 남지만 더 고치지 않습니다.

| 항목 | 이전 (`s2-support` 1.x) | 지금 (`s2-kit` 1.0.0) |
| :--- | :--- | :--- |
| 좌표 | `io.github.devers2.internal:s2-support` | `io.github.devers2.internal:s2-kit` |
| 패키지 | `io.github.devers2.s2util.{support,file,pagination,spring,model}` | `io.github.devers2.s2kit.{...}` |
| 프런트 자원 | `/s2-util/js/s2.util.js`, `/s2-util/css/...` | `/s2-kit/js/s2.util.js`, `/s2-kit/css/...` |

- 그대로인 것: `s2-core` 패키지(`io.github.devers2.s2util.core`, `.json`, `.log` 등), 클래스 이름, JS 파일 이름과 API(`S2Util` 등),
  JSP 태그 URI, `s2.validator.js` 경로(`/s2-util/js/`, `s2-validator` 소속).
- 패키지와 경로는 한 번에 바꿀 수 있습니다:

```bash
grep -rlE 'devers2\.s2util\.(support|file|pagination|spring|model)\b|/s2-util/(js/s2\.(util|date|loading|dropzone)|css/)' src \
  | xargs sed -i -E 's/devers2\.s2util\.(support|file|pagination|spring|model)\b/devers2.s2kit.\1/g;
                     s#/s2-util/(js/s2\.(util|date|loading|dropzone)\.js|css/)#/s2-kit/\1#g'
```

- 1.1.5 이후의 다른 변경(⚠️ 호환성 포함)은 [CHANGELOG](./CHANGELOG.ko.md)를 확인하십시오.

---

## ✨ 주요 유틸리티

- **📁 파일 관리** — `FileManager`, `S2File`, `S2RemoteFile`을 통한 로컬 및 원격(SFTP/JSch) 파일 처리
- **📄 페이징** — 목록/검색 UI를 위한 `S2PaginationInfo`, `S2PaginationTag`, `S2SearchVO` 제공
- **🍃 Spring 유틸리티** — Spring 기반 앱을 위한 `S2ContextUtil`(AOP 조인포인트 파라미터), `S2AnnotationResolver`, `S2RestApiUtil`
- **🔧 범용 헬퍼** — `S2HashUtil`, `S2EncryptionUtil`, `S2ImageUtil`, `S2TypeUtil`, `S2CollectionUtil`, `S2StreamUtil`, `S2ServletUtil`, `S2QueryStringUtil`, `S2Uuid`
- **🗂️ 자료구조** — `S2LruMap` (`LinkedHashMap` 기반 LRU 캐시)
- **📑 PDF & 웹 뷰어 확장** — [`s2-kit-pdf`](s2-kit-pdf/README.ko.md) 서브모듈 (PDF 병합/스탬프, 오피스 변환, Podman 스크립트, 웹 PDF 뷰어 내장)

---

## 🚀 빠른 시작 가이드 (Quick Start)

### 1. 설치 (Installation)

`build.gradle` 또는 `pom.xml`에 다음 의존성을 추가합니다.

**[Gradle]**

```groovy
dependencies {
    implementation 'io.github.devers2.internal:s2-kit:1.1.0'
}
```

**[Maven]**

```xml
<dependency>
    <groupId>io.github.devers2.internal</groupId>
    <artifactId>s2-kit</artifactId>
    <version>1.1.0</version>
</dependency>
```

#### 선택적 확장 모듈 (`s2-kit-pdf`, `s2-validator`, `s2-validator-plugin`, `s2-jpa`)

> [!NOTE]
> `s2-kit`은 핵심 모듈인 **`s2-core`**를 `api` 전이 의존성으로 기본 포함하고 있으므로, 고성능 리플렉션, 지능형 캐시, 날짜/문자열 유틸리티 등은 별도 선언 없이 즉시 사용할 수 있습니다.

애플리케이션 요구사항에 따라 **`s2-kit-pdf`** 서브모듈 및 **[s2-util 제품군](https://github.com/devers2/s2-util)**의 동반 모듈을 선택적으로 추가하여 기능을 확장할 수 있습니다:

| 모듈 | 유형 및 좌표 | 주요 특징 및 기능 |
| :--- | :--- | :--- |
| **`s2-kit-pdf`** | 서브모듈 라이브러리<br>`io.github.devers2:s2-kit-pdf:1.1.0` | **PDF 엔진 + 오피스/한글 변환 + 웹 뷰어 + Podman 스크립트**<br>• 다중 포맷(HTML, 오피스, 한글, 이미지, MD) PDF 병합 및 워터마크/스탬프.<br>• 프론트엔드 웹 PDF 뷰어 내장 (`/s2-kit/pdf-viewer/pdf-viewer.js`, `pdf-viewer.css`).<br>• Chromium Headless 기반 100% LGPL-Free 인쇄 지원 및 팟맨 온/오프라인 설치기 제공.<br>• `s2-kit` 및 `s2-core`를 자동으로 포함 (`api` 의존성). |
| **`s2-validator`** | 라이브러리<br>`io.github.devers2:s2-validator:2.1.0` | **서버/클라이언트 크로스 플랫폼 통합 검증**<br>• Java에서 작성한 검증 규칙을 클라이언트(JavaScript `s2.validator.js`)와 완벽 동기화.<br>• 30여 종 기본 규칙(이메일, 연락처, 날짜 등) 및 한국어 조사 자동 보정(`{0|은/는}`).<br>• Fluent 체이닝 API, 조건부 검증(`when`/`and`), 중첩/컬렉션 객체 검증 지원.<br>• `S2BindValidator`를 통한 Spring MVC `BindingResult` 완벽 연동. |
| **`s2-validator-plugin`** | Gradle 플러그인<br>`id 'io.github.devers2.validator' version '2.1.0'` | **빌드 시점 필드 유효성 검증** *(`s2-validator`의 동반 플러그인)*<br>• AST 기반 정적 코드 분석으로 빌드 시점(`compileJava`)에 대상 DTO의 필드 유효성 검사.<br>• `S2Validator.<DTO>builder().field("...")`에 지정된 필드가 실제 DTO 클래스에 존재하는지 대조 검증하여, 필드명 불일치나 리팩토링 누락을 빌드 단계에서 사전에 차단.<br>• 별도 설정 없는 Zero-Configuration 지원 (Gradle 전용). |
| **`s2-jpa`** | 라이브러리<br>`io.github.devers2:s2-jpa:2.1.0` | **JPA 동적 JPQL 쿼리 빌더**<br>• `S2Jpql` 및 `{{=key}}` 플레이스홀더를 활용한 템플릿 기반 동적 쿼리 생성.<br>• 조건부 파라미터 및 절 바인딩(`bindClause`, `bindParameter`, `bindOrderBy`).<br>• `LikeMode`(ANYWHERE, START, END)를 통한 안전한 LIKE 검색 및 인젝션 방지. |
| **`s2-util`** *(통합 번들)* | 라이브러리<br>`io.github.devers2:s2-util:2.1.0` | **s2-kit 과 함께 쓰지 마십시오.**<br>• 번들은 `s2-core` 클래스를 JAR 안에 담고 있어, `s2-kit`이 가져오는 `s2-core`와 같은 클래스가 클래스패스에 두 번 올라갑니다.<br>• `s2-kit`과 함께라면 위의 개별 모듈(`s2-validator`, `s2-jpa`)을 추가하십시오. |

**Gradle 설정 예시:**

```groovy
// build.gradle
plugins {
    id 'java'
    // [선택] S2Validator 빌드 시점 필드 정적 검증 플러그인 (Gradle 전용, 라이브러리와 별도 선언 필요)
    id 'io.github.devers2.validator' version '2.1.0'
}

dependencies {
    // 기본: s2-kit (s2-core 자동 포함)
    implementation 'io.github.devers2.internal:s2-kit:1.1.0'

    // [선택] PDF 처리, 오피스 문서 변환, 웹 PDF 뷰어가 필요한 경우 (s2-kit, s2-core 자동 포함)
    // implementation 'io.github.devers2:s2-kit-pdf:1.1.0'

    // [선택] 서버/클라이언트 통합 검증 기능이 필요한 경우
    implementation 'io.github.devers2:s2-validator:2.1.0'

    // [선택] 동적 JPQL 쿼리 작성이 필요한 경우
    implementation 'io.github.devers2:s2-jpa:2.1.0'

    // ⚠️ s2-util 통합 번들은 s2-core 를 안에 담고 있어 s2-kit 과 겹치므로 개별 모듈을 쓰십시오
}
```

**Maven 의존성 설정 예시:**

```xml
<!-- 기본: s2-kit (s2-core 자동 포함) -->
<dependency>
    <groupId>io.github.devers2.internal</groupId>
    <artifactId>s2-kit</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- [선택] s2-kit-pdf (PDF/오피스 변환/웹 뷰어) -->
<!--
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-kit-pdf</artifactId>
    <version>1.1.0</version>
</dependency>
-->

<!-- [선택] s2-validator -->
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-validator</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- [선택] s2-jpa -->
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-jpa</artifactId>
    <version>2.1.0</version>
</dependency>
```

### 2. 주요 사용법 (Usage Examples)

#### 페이징 (`S2SearchVO`, `S2PaginationInfo`, `<s2:pagination>`)

```java
// S2SearchVO 는 요청의 pageNo, pageUnit, orderBy, searchKeyword ... 를 바인딩
List<Board> list = boardMapper.selectList(searchVO);   // LIMIT #{pageUnit} OFFSET #{firstIndex}
long total = boardMapper.selectCount(searchVO);
S2PaginationInfo<Board> page = new S2PaginationInfo<>(searchVO, list, total);

// 정렬 컬럼: 식별자만 남으며, ORDER BY ${column}에 쓸 때는 허용 컬럼을 지정
List<Map<String, String>> orderBy = searchVO.getOrderByList(List.of("REG_DT", "TITLE"));
```

```jsp
<%@ taglib prefix="s2" uri="http://ext.s2.kr/taglib/tags" %>
<s2:pagination paginationInfo="${page}" jsFunction="fn_list" jsParam="${boardType}" />
<%-- onclick="fn_list('notice', 2);" 로 렌더링되며 jsParam 은 이스케이프됨 --%>
```

#### 암호화·비밀번호 해시 (`S2EncryptionUtil`, `S2HashUtil`)

```java
// 여러 건(DB 컬럼, 목록 화면): 비밀 저장소에 둔 무작위 키, 빠름 (1건 1ms 미만)
SecretKey key = S2EncryptionUtil.keyFromBase64(System.getenv("APP_ENCRYPTION_KEY")); // generateKey()로 한 번 생성
String encrypted = S2EncryptionUtil.encrypt("010-1234-5678", key); // "s2k1:default:..." (AES-256-GCM)
String plain = S2EncryptionUtil.decrypt(encrypted, key);           // 틀린 키 → GeneralSecurityException

// 서비스 중단 없는 키 교체: 암호문에 키 이름이 기록됨
var keys = S2EncryptionUtil.KeyRing.builder()
        .add("default", key)                                        // 옛 키 (단일 키 API 는 "default"로 기록)
        .add("2027", newKey)
        .primary("2027")                                            // 새 값은 새 키로
        .build();
String value = keys.decrypt(encrypted);                             // 옛 암호문도 읽힘
String moved = keys.needsReencrypt(encrypted) ? keys.reencrypt(encrypted) : encrypted; // 천천히 옮김

// 사람이 입력한 비밀번호로 가끔: 일부러 느림 (PBKDF2, 1건 약 70ms)
String sealed = S2EncryptionUtil.encrypt("비밀 문자열", password);   // "s2v2:..."

String stored = S2HashUtil.hash(password);                          // PBKDF2, 반복 횟수 함께 저장
if (S2HashUtil.verify(input, stored) && S2HashUtil.needsRehash(stored)) {
    stored = S2HashUtil.hash(input);                                // 로그인 성공 시 1.x 해시 갱신
}
```

#### PDF & 오피스 문서 변환, 웹 PDF 뷰어 (`s2-kit-pdf`)

PDF 처리(HTML/오피스/이미지 병합, 워터마크, 페이지 번호 스탬프), 오피스·한글 문서 변환 및 프론트엔드 웹 PDF 뷰어가 필요한 경우 전용 서브모듈인 **[`s2-kit-pdf`](s2-kit-pdf/README.ko.md)**를 사용합니다.

```groovy
dependencies {
    // s2-kit 대신 s2-kit-pdf 를 선언하면 s2-kit 과 s2-core 도 함께 포함됩니다
    implementation 'io.github.devers2:s2-kit-pdf:1.1.0'
}
```

- **백엔드 문서 처리**: `S2PdfUtil.merge()`, `S2PdfUtil.stamp()`, `S2OfficeConverter` (오피스/한글 문서 변환)
- **프론트엔드 웹 뷰어**: `/s2-kit/pdf-viewer/pdf-viewer.js`, `pdf-viewer.css` (React 내장 독립형 ES 모듈 뷰어)
- **인프라 스크립트**: Podman 기반 LibreOffice/Chromium 온·오프라인 변환기 ([`s2-kit-pdf/scripts/`](s2-kit-pdf/scripts/README.md))

##### 🛠️ 서버 문서 변환기 설치 (Podman 온/오프라인)
오피스·한글 문서 변환(`s2-soffice`) 및 웹 화면 무손실 PDF 인쇄(`s2-chrome`)를 위해 Podman 기반의 원클릭 변환기 설치 스크립트를 제공합니다.

- **온라인 환경 설치**:
  ```bash
  # 앱 실행 리눅스 계정(예: appuser)에 설치 (변환기 이미지 약 1.6GB 자동 구축)
  ./s2-kit-pdf/scripts/setup-s2-office-converter.sh --app-user appuser
  ```
- **인터넷 불가(폐쇄망) 환경 설치**:
  ```bash
  # 1. 인터넷 연결 PC에서 단일 오프라인 설치 파일 생성 (대상 서버 OS 지정, 예: rockylinux:9, ubuntu:22.04 등)
  ./s2-kit-pdf/scripts/setup-s2-office-converter.sh --export --target rockylinux:9

  # 2. 생성된 setup-s2-office-converter-offline.sh 파일을 폐쇄망 서버로 복사 후 실행
  bash setup-s2-office-converter-offline.sh --app-user appuser
  ```

자세한 사용법과 전체 예제 코드는 **[s2-kit-pdf README](s2-kit-pdf/README.ko.md)** 및 **[scripts README](s2-kit-pdf/scripts/README.md)**를 참고하십시오.

#### SFTP (`S2SftpFileManagerImpl`)

```java
// 서버 호스트 키를 known_hosts 로 검증 (null 이면 ~/.ssh/known_hosts)
FileManager sftp = new S2SftpFileManagerImpl("sftp.example.com", 22, "app", "/keys/id_ed25519", null, null,
        null, null, null, "/etc/ssh/known_hosts_sftp", false);
sftp.writeFile(inputStream, "/upload/2026", "report.pdf");          // 이름에 "../x" 는 거부됨
```

#### AOP 파라미터 (`S2ContextUtil`)

```java
@Before("@annotation(audited)")
public void audit(JoinPoint joinPoint, Audited audited) {
    // userId 라는 파라미터, 또는 VO/Map 인자의 userId 필드
    Object userId = S2ContextUtil.getJoinPointParameter(joinPoint, "userId");
}
```

#### JSON 헬퍼 (`S2JsonUtil`, s2-core)

`S2JsonUtil`은 `s2-core` 2.0.0 부터 `s2-core`(`io.github.devers2.s2util.json.S2JsonUtil`)에 있습니다. `s2-kit`이 `s2-core`를 함께
가져오므로 별도 의존성은 필요 없습니다. 자세한 내용은 s2-util MANUAL 의 "경량 JSON" 절을 참고하십시오.

```java
import io.github.devers2.s2util.json.S2JsonUtil;

String json = S2JsonUtil.toJson(myObject);
MyDto dto = S2JsonUtil.fromJson(json, MyDto.class);   // 실패 시 S2JsonException (null 을 돌려주지 않음)
```

---

## ⚙️ 요구사항 (Requirements)

본 프로젝트는 **JDK 21** 환경에서 빌드되었으나, **Java 17 이상**의 모든 환경에서 안정적으로 사용할 수 있습니다.

---

## 📜 라이선스 및 저작권 (License & Copyright)

본 라이브러리는 **Apache License 2.0** 하에 제공됩니다. 사용자는 라이선스의 의무 사항(저작권 고지, 소스 코드 공개 범위 등)을 준수하는 조건 하에 자유롭게 사용, 수정 및 재배포가 가능합니다. 상세한 조건은 **[LICENSE](./LICENSE)** 파일을 반드시 확인해 주세요.

- **저작권 2020 - 2026 devers2 (이승수, 대한민국 대전)**
- 문의: [eseungsu.dev@gmail.com](mailto:eseungsu.dev@gmail.com)

**제3자 라이브러리 고지:** 본 프로젝트는 외부 라이브러리를 사용합니다. 상세한 제3자 라이브러리 고지사항은 **[licenses/NOTICE](./licenses/NOTICE)** 파일을 참조해 주세요.

---

s2-kit Version: 1.1.0 (2026-10-28)

[//]: # 'S2_DEPS_INFO_START'

---

**특정 기능(예: S2BindValidator)을 사용하려면 런타임에 다음 의존성을 엔드유저 프로젝트에 명시적으로 추가해야 합니다.** 이 의존성이 누락되면 런타임에 `java.lang.NoClassDefFoundError`가 발생합니다.

**[Gradle 사용자]**

```groovy
dependencies {
    // 선택적 기능을 위한 필수 런타임 의존성
    implementation 'com.google.code.findbugs:jsr305:3.0.2'
    implementation 'org.springframework:spring-context:6.2.19'
    implementation 'org.springframework:spring-web:6.2.19'
    implementation 'org.springframework.integration:spring-integration-sftp:6.5.10'
    implementation 'jakarta.servlet:jakarta.servlet-api:6.1.0'
    implementation 'jakarta.servlet.jsp:jakarta.servlet.jsp-api:4.0.0'
    implementation 'org.aspectj:aspectjweaver:1.9.25.1'
}
```

[//]: # 'S2_DEPS_INFO_END'
