# s2-support — 실무 보조 유틸리티 라이브러리

🌐 [English](README.md) | **한국어**

[![Java CI](https://github.com/devers2/s2-support/actions/workflows/ci.yml/badge.svg)](https://github.com/devers2/s2-support/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.devers2.internal/s2-support?color=brightgreen&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.devers2.internal/s2-support)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](./LICENSE)

> `s2-core` 및 `s2-validator` 기반으로 구축된 **주관적(Opinionated) 보조 라이브러리**로, 유용한 헬퍼 클래스와 편의 유틸리티 모음을 제공합니다.  
> 주로 **개인 개발 워크플로** 간소화와 반복적인 애플리케이션 패턴 지원을 목적으로 설계되었습니다.

---

## 📖 개요 (Overview)

`s2-support`는 실용적인 Java/Spring 애플리케이션 개발에 맞춰 자주 사용하는 헬퍼 모듈과 반복적인 보일러플레이트를 모아둔 라이브러리입니다. 파일 관리, 페이징, Spring 컨텍스트 유틸리티, JSON/암호화/이미지 헬퍼 등을 포함합니다.

---

## ✨ 주요 유틸리티

- **📑 PDF 엔진 & 멀티 포맷 병합** — `S2PdfUtil`을 통한 HTML/이미지/텍스트/SVG → PDF 변환, 이종 포맷 순서 보장 병합, 원격 URL 비동기 분산 프리페치, 메모리 누수 방지 디스크 캐시 및 페이지 번호 각인
- **📁 파일 관리** — `FileManager`, `S2File`, `S2RemoteFile`을 통한 로컬 및 원격(SFTP/JSch) 파일 처리
- **📄 페이징** — 목록/검색 UI를 위한 `S2PaginationInfo`, `S2PaginationTag`, `S2SearchVO` 제공
- **🍃 Spring 유틸리티** — Spring 기반 앱을 위한 `S2ContextUtil`(AOP 조인포인트 파라미터), `S2AnnotationResolver`, `S2RestApiUtil`
- **🔧 범용 헬퍼** — `S2HashUtil`, `S2EncryptionUtil`, `S2ImageUtil`, `S2TypeUtil`, `S2CollectionUtil`, `S2StreamUtil`, `S2ServletUtil`, `S2QueryStringUtil`, `S2Uuid`
- **🗂️ 자료구조** — `S2LruMap` (`LinkedHashMap` 기반 LRU 캐시)

---

## 🚀 빠른 시작 가이드 (Quick Start)

### 1. 설치 (Installation)

`build.gradle` 또는 `pom.xml`에 다음 의존성을 추가합니다.

**[Gradle]**

```groovy
dependencies {
    implementation 'io.github.devers2.internal:s2-support:2.0.0'
}
```

**[Maven]**

```xml
<dependency>
    <groupId>io.github.devers2.internal</groupId>
    <artifactId>s2-support</artifactId>
    <version>2.0.0</version>
</dependency>
```

#### 선택적 확장 모듈 (`s2-validator`, `s2-validator-plugin`, `s2-jpa`)

> [!NOTE]
> `s2-support`는 핵심 모듈인 **`s2-core`**를 `api` 전이 의존성으로 기본 포함하고 있으므로, 고성능 리플렉션, 지능형 캐시, 날짜/문자열 유틸리티 등은 별도 선언 없이 즉시 사용할 수 있습니다.

애플리케이션 요구사항에 따라 **[s2-util 제품군](https://github.com/devers2/s2-util)**의 동반 모듈을 선택적으로 추가하여 기능을 확장할 수 있습니다:

| 모듈 | 유형 및 좌표 | 주요 특징 및 기능 |
| :--- | :--- | :--- |
| **`s2-validator`** | 라이브러리<br>`io.github.devers2:s2-validator:2.0.0` | **서버/클라이언트 크로스 플랫폼 통합 검증**<br>• Java에서 작성한 검증 규칙을 클라이언트(JavaScript `s2.validator.js`)와 완벽 동기화.<br>• 30여 종 기본 규칙(이메일, 연락처, 날짜 등) 및 한국어 조사 자동 보정(`{0|은/는}`).<br>• Fluent 체이닝 API, 조건부 검증(`when`/`and`), 중첩/컬렉션 객체 검증 지원.<br>• `S2BindValidator`를 통한 Spring MVC `BindingResult` 완벽 연동. |
| **`s2-validator-plugin`** | Gradle 플러그인<br>`id 'io.github.devers2.validator' version '1.1.3'` | **빌드 시점 필드 유효성 검증** *(`s2-validator`의 동반 플러그인)*<br>• AST 기반 정적 코드 분석으로 빌드 시점(`compileJava`)에 대상 DTO의 필드 유효성 검사.<br>• `S2Validator.<DTO>builder().field("...")`에 지정된 필드가 실제 DTO 클래스에 존재하는지 대조 검증하여, 필드명 불일치나 리팩토링 누락을 빌드 단계에서 사전에 차단.<br>• 별도 설정 없는 Zero-Configuration 지원 (Gradle 전용). |
| **`s2-jpa`** | 라이브러리<br>`io.github.devers2:s2-jpa:2.0.0` | **JPA 동적 JPQL 쿼리 빌더**<br>• `S2Jpql` 및 `{{=key}}` 플레이스홀더를 활용한 템플릿 기반 동적 쿼리 생성.<br>• 조건부 파라미터 및 절 바인딩(`bindClause`, `bindParameter`, `bindOrderBy`).<br>• `LikeMode`(ANYWHERE, START, END)를 통한 안전한 LIKE 검색 및 인젝션 방지. |
| **`s2-util`** *(통합 번들)* | 라이브러리<br>`io.github.devers2:s2-util:2.0.0` | **올인원 전체 유틸리티 제품군**<br>• `s2-core`, `s2-validator`, `s2-jpa` 라이브러리를 모두 포함하여 모든 기능을 한 번에 사용하고 싶을 때 권장.<br>• *(⚠️ 주의: 전체 번들을 사용하더라도 빌드 시점 필드 검증 플러그인은 위 `plugins {}` 블록에 별도로 추가해야 합니다)* |

**Gradle 설정 예시:**

```groovy
// build.gradle
plugins {
    id 'java'
    // [선택] S2Validator 빌드 시점 필드 정적 검증 플러그인 (Gradle 전용, 라이브러리와 별도 선언 필요)
    id 'io.github.devers2.validator' version '1.1.3'
}

dependencies {
    // 기본: s2-support (s2-core 자동 포함)
    implementation 'io.github.devers2.internal:s2-support:2.0.0'

    // [선택] 서버/클라이언트 통합 검증 기능이 필요한 경우
    implementation 'io.github.devers2:s2-validator:2.0.0'

    // [선택] 동적 JPQL 쿼리 작성이 필요한 경우
    implementation 'io.github.devers2:s2-jpa:2.0.0'

    // 또는 개별 모듈 대신 전체 번들을 한 번에 추가하는 경우:
    // (⚠️ 전체 번들을 사용하더라도 필드 검증 플러그인은 위 plugins {}에 별도 추가해야 함)
    // implementation 'io.github.devers2:s2-util:2.0.0'
}
```

**Maven 의존성 설정 예시:**

```xml
<!-- 기본: s2-support (s2-core 자동 포함) -->
<dependency>
    <groupId>io.github.devers2.internal</groupId>
    <artifactId>s2-support</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- [선택] s2-validator -->
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-validator</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- [선택] s2-jpa -->
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-jpa</artifactId>
    <version>2.0.0</version>
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

#### PDF 병합 (`S2PdfUtil`)

```java
// 한글은 한글 TrueType 폰트가 필요. 지정하지 않으면 설치된 폰트를 찾고(SYSTEM_FONT_CANDIDATES),
// 폰트가 전혀 없으면 '#'으로 깨지는 대신 예외
S2PdfUtil.setDefaultFont(Path.of("/usr/share/fonts/truetype/nanum/NanumGothic.ttf"));

var options = S2PdfUtil.MergeOptions.create().bookmarks(true).pageNumbers(true).title("보고서");
try (InputStream pdf = S2PdfUtil.merge(List.of(
        S2PdfUtil.PdfSource.ofHtml("<h1>표지</h1>").title("표지"),
        S2PdfUtil.PdfSource.ofPdf(Path.of("body.pdf")),
        S2PdfUtil.PdfSource.ofUrl("https://example.com/chart.png").maxBytes(10_000_000)), options)) {
    pdf.transferTo(response.getOutputStream());
}

// 오피스·한글 문서(docx, xlsx, pptx, hwp, hwpx 등)는 LibreOffice 가 설치되어 있으면 변환됨
// (s2-soffice, soffice, libreoffice 를 자동으로 찾음). 없어도 다른 소스는 그대로 병합됨
if (S2PdfUtil.isOfficeConversionAvailable()) {
    S2PdfUtil.merge(List.of(S2PdfUtil.PdfSource.ofDocument(Path.of("계획서.docx")),
            S2PdfUtil.PdfSource.ofDocument(uploadedStream, "보고서.hwp")));
}
```

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

2.0.0 부터 `S2JsonUtil`은 `s2-core`(`io.github.devers2.s2util.json.S2JsonUtil`)에 있습니다. `s2-support`가 `s2-core`를 함께
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

s2-support Version: 2.0.0 (2026-09-30)

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
    implementation 'org.jsoup:jsoup:1.23.2'
    implementation 'io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.85'
}
```

[//]: # 'S2_DEPS_INFO_END'
