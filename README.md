# s2-kit — Shared Java and Web Modules

🌐 **English** | [한국어](README.ko.md)

[![Java CI](https://github.com/devers2/s2-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/devers2/s2-kit/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.devers2.internal/s2-kit?color=brightgreen&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.devers2.internal/s2-kit)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](./LICENSE)

> **Shared team modules** built on [s2-util](https://github.com/devers2/s2-util) (`s2-core`): files, pagination,
> Spring and servlet helpers, and front-end JS/CSS. (For PDF conversion, merging, and the web viewer, see the **[`s2-kit-pdf`](s2-kit-pdf/README.md)** submodule). Formerly `s2-support`.

---

## 📖 Overview

`s2-kit` consolidates frequently used helper modules and boilerplate reductions tailored to practical Java/Spring application development. It covers file management, pagination, Spring context utilities, JSON/encryption/image helpers, and more.

---

## 🧭 About This Library

- **What it is:** common modules shared across personal and team projects. The repository is public and the artifact is on
  Maven Central, but it does not aim to support every environment like a general-purpose library. That is what `internal`
  in the group ID means.
- **Compatibility:** versions follow `major.minor.patch`. Patch releases fix, minor releases add features and stay compatible.
  Breaking changes happen only in major releases and are listed in the [CHANGELOG](./CHANGELOG.md) with ⚠️ and how to move.
- **Foundation:** brings in `s2-core` (s2-util). See the [s2-util CHANGELOG](https://github.com/devers2/s2-util/blob/main/CHANGELOG.md)
  for its changes.
- **What some features need:**
  - PDF engine, Office/Hangul document conversion, and Web PDF Viewer: see the **[`s2-kit-pdf`](s2-kit-pdf/README.md)** submodule.
  - Optional dependencies (Spring, Servlet, SFTP, etc.): add only what you use. The list is at the end of this document.
- **Contact:** GitHub Issues or [eseungsu.dev@gmail.com](mailto:eseungsu.dev@gmail.com).

### 🔀 Moving from s2-support

Projects on `s2-support` 1.x change the following. `s2-support` 1.x stays on Maven Central but receives no more fixes.

| Item | Before (`s2-support` 1.x) | Now (`s2-kit` 1.0.0) |
| :--- | :--- | :--- |
| Coordinates | `io.github.devers2.internal:s2-support` | `io.github.devers2.internal:s2-kit` |
| Packages | `io.github.devers2.s2util.{support,file,pagination,spring,model}` | `io.github.devers2.s2kit.{...}` |
| Front-end resources | `/s2-util/js/s2.util.js`, `/s2-util/css/...` | `/s2-kit/js/s2.util.js`, `/s2-kit/css/...` |

- Unchanged: the `s2-core` packages (`io.github.devers2.s2util.core`, `.json`, `.log`, ...), class names, JS file names and API
  (`S2Util`, ...), JSP tag URIs, and the `s2.validator.js` path (`/s2-util/js/`, part of `s2-validator`).
- Packages and paths can be changed in one go:

```bash
grep -rlE 'devers2\.s2util\.(support|file|pagination|spring|model)\b|/s2-util/(js/s2\.(util|date|loading|dropzone)|css/)' src \
  | xargs sed -i -E 's/devers2\.s2util\.(support|file|pagination|spring|model)\b/devers2.s2kit.\1/g;
                     s#/s2-util/(js/s2\.(util|date|loading|dropzone)\.js|css/)#/s2-kit/\1#g'
```

- For the other changes since 1.1.5 (including ⚠️ compatibility), see the [CHANGELOG](./CHANGELOG.md).

---

## ✨ Key Utilities

- **📁 File Management** — Local and remote (SFTP/JSch) file operations via `FileManager`, `S2File`, `S2RemoteFile`
- **📄 Pagination** — Ready-to-use `S2PaginationInfo`, `S2PaginationTag`, and `S2SearchVO` for list/search UIs
- **🍃 Spring Utilities** — `S2ContextUtil` (AOP join point parameters), `S2AnnotationResolver`, `S2RestApiUtil` for Spring-based apps
- **🔧 General Helpers** — `S2HashUtil`, `S2EncryptionUtil`, `S2ImageUtil`, `S2TypeUtil`, `S2CollectionUtil`, `S2StreamUtil`, `S2ServletUtil`, `S2QueryStringUtil`, `S2Uuid`
- **🗂️ Data Structures** — `S2LruMap` (LRU cache backed by `LinkedHashMap`)
- **📑 PDF & Web Viewer Extension** — [`s2-kit-pdf`](s2-kit-pdf/README.md) submodule (PDF merge/stamp, Office conversion, Podman scripts, embedded web viewer)

---

## 🚀 Quick Start

### 1. Installation

Add the following dependency to your `build.gradle` or `pom.xml`.

**[Gradle]**

```groovy
dependencies {
    implementation 'io.github.devers2.internal:s2-kit:1.0.0'
}
```

**[Maven]**

```xml
<dependency>
    <groupId>io.github.devers2.internal</groupId>
    <artifactId>s2-kit</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Optional Companion Modules (`s2-kit-pdf`, `s2-validator`, `s2-validator-plugin`, `s2-jpa`)

> [!NOTE]
> `s2-kit` automatically includes **`s2-core`** as an `api` (transitive) dependency, so core reflection, caching, date/string, and thread utilities are immediately available out-of-the-box.

Depending on your application's requirements, you can optionally include the **`s2-kit-pdf`** submodule and companion modules from the **[s2-util suite](https://github.com/devers2/s2-util)**:

| Module | Type & Coordinates | Key Features & Purpose |
| :--- | :--- | :--- |
| **`s2-kit-pdf`** | Submodule Library<br>`io.github.devers2:s2-kit-pdf:1.0.0` | **PDF Engine + Office/Hangul Conversion + Web Viewer + Podman Scripts**<br>• Multi-format (HTML, Office, Hangul, images, Markdown) PDF merge, watermarking, and stamps.<br>• Built-in front-end web PDF viewer assets (`/s2-kit/pdf-viewer/pdf-viewer.js`, `pdf-viewer.css`).<br>• 100% LGPL-Free Chromium Headless printing support & Podman online/offline converter installer.<br>• Includes `s2-kit` and `s2-core` automatically (`api` dependency). |
| **`s2-validator`** | Library<br>`io.github.devers2:s2-validator:2.0.0` | **Cross-Platform Dynamic Validator**<br>• Author validation rules once in Java and synchronize seamlessly with client-side JavaScript (`s2.validator.js`).<br>• 30+ built-in rules (email, phone, date, etc.) with smart Korean particle interpolation (`{0|은/는}`).<br>• Fluent chaining API, conditional validation (`when`/`and`), nested/collection object validation.<br>• Seamless Spring MVC integration via `S2BindValidator` (`BindingResult`). |
| **`s2-validator-plugin`** | Gradle Plugin<br>`id 'io.github.devers2.validator' version '2.0.0'` | **Compile-Time Field Validation** *(Optional companion for `s2-validator`)*<br>• AST-based static analysis during build (`compileJava`).<br>• Inspects `.field("fieldName")` in `S2Validator.<DTO>builder()` to verify fields exist on the target DTO class, preventing field mismatches or refactoring regressions before runtime.<br>• Zero configuration required (Gradle only). |
| **`s2-jpa`** | Library<br>`io.github.devers2:s2-jpa:2.0.0` | **Dynamic JPQL Query Builder**<br>• Template-based dynamic query construction using `S2Jpql` with `{{=key}}` placeholders.<br>• Fluent conditional parameter and clause binding (`bindClause`, `bindParameter`, `bindOrderBy`).<br>• Safe LIKE search with `LikeMode` (ANYWHERE, START, END) preventing injection. |
| **`s2-util`** *(Bundle)* | Library<br>`io.github.devers2:s2-util:2.0.0` | **Do not combine with s2-kit.**<br>• The bundle carries the `s2-core` classes inside its JAR, so they would sit on the classpath twice next to the `s2-core` that `s2-kit` brings.<br>• With `s2-kit`, add the individual modules above (`s2-validator`, `s2-jpa`). |

**Example Dependency Setup (Gradle):**

```groovy
// build.gradle
plugins {
    id 'java'
    // [Optional] Compile-time field validation plugin for S2Validator (Gradle only, declared separately from libraries)
    id 'io.github.devers2.validator' version '2.0.0'
}

dependencies {
    // Base: s2-kit (s2-core is included automatically)
    implementation 'io.github.devers2.internal:s2-kit:1.0.0'

    // [Optional] When PDF processing, Office conversion, and Web PDF Viewer are needed (includes s2-kit automatically)
    // implementation 'io.github.devers2:s2-kit-pdf:1.0.0'

    // [Optional] Server & Client Unified Validation
    implementation 'io.github.devers2:s2-validator:2.0.0'

    // [Optional] Dynamic JPQL Queries
    implementation 'io.github.devers2:s2-jpa:2.0.0'

    // ⚠️ Not the s2-util bundle: it carries s2-core inside and would overlap with s2-kit
}
```

**Example Dependency Setup (Maven):**

```xml
<!-- Base: s2-kit (s2-core is included automatically) -->
<dependency>
    <groupId>io.github.devers2.internal</groupId>
    <artifactId>s2-kit</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- [Optional] s2-kit-pdf (PDF engine, Office conversion, Web PDF Viewer) -->
<!--
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-kit-pdf</artifactId>
    <version>1.0.0</version>
</dependency>
-->

<!-- [Optional] s2-validator -->
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-validator</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- [Optional] s2-jpa -->
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-jpa</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 2. Usage Examples

#### Pagination (`S2SearchVO`, `S2PaginationInfo`, `<s2:pagination>`)

```java
// S2SearchVO binds pageNo, pageUnit, orderBy, searchKeyword ... from the request
List<Board> list = boardMapper.selectList(searchVO);   // LIMIT #{pageUnit} OFFSET #{firstIndex}
long total = boardMapper.selectCount(searchVO);
S2PaginationInfo<Board> page = new S2PaginationInfo<>(searchVO, list, total);

// Sort columns: only identifiers survive; pass the allowed columns for ORDER BY ${column}
List<Map<String, String>> orderBy = searchVO.getOrderByList(List.of("REG_DT", "TITLE"));
```

```jsp
<%@ taglib prefix="s2" uri="http://ext.s2.kr/taglib/tags" %>
<s2:pagination paginationInfo="${page}" jsFunction="fn_list" jsParam="${boardType}" />
<%-- renders onclick="fn_list('notice', 2);" with jsParam escaped --%>
```

#### Encryption and password hashing (`S2EncryptionUtil`, `S2HashUtil`)

```java
// Many values (DB columns, list pages): a random key kept in a secret store, fast (< 1 ms per value)
SecretKey key = S2EncryptionUtil.keyFromBase64(System.getenv("APP_ENCRYPTION_KEY")); // made once by generateKey()
String encrypted = S2EncryptionUtil.encrypt("010-1234-5678", key); // "s2k1:default:..." (AES-256-GCM)
String plain = S2EncryptionUtil.decrypt(encrypted, key);           // wrong key → GeneralSecurityException

// Key rotation without downtime: the ciphertext records its key name
var keys = S2EncryptionUtil.KeyRing.builder()
        .add("default", key)                                        // old key (single-key API writes "default")
        .add("2027", newKey)
        .primary("2027")                                            // new values use the new key
        .build();
String value = keys.decrypt(encrypted);                             // old ciphertexts still read
String moved = keys.needsReencrypt(encrypted) ? keys.reencrypt(encrypted) : encrypted; // migrate gradually

// A password typed by a person, occasionally: deliberately slow (PBKDF2, ~70 ms per value)
String sealed = S2EncryptionUtil.encrypt("secret text", password); // "s2v2:..."

String stored = S2HashUtil.hash(password);                          // PBKDF2, iteration count stored
if (S2HashUtil.verify(input, stored) && S2HashUtil.needsRehash(stored)) {
    stored = S2HashUtil.hash(input);                                // upgrade 1.x hashes after login
}
```

#### PDF & Office Document Conversion, Web PDF Viewer (`s2-kit-pdf`)

When PDF processing (HTML/Office/Image merging, watermarking, page number stamps), Office/Hangul document conversion, or the front-end web PDF viewer are needed, use the dedicated submodule **[`s2-kit-pdf`](s2-kit-pdf/README.md)**.

```groovy
dependencies {
    // Declaring s2-kit-pdf instead of s2-kit automatically brings in s2-kit and s2-core
    implementation 'io.github.devers2:s2-kit-pdf:1.0.0'
}
```

- **Backend Document Processing**: `S2PdfUtil.merge()`, `S2PdfUtil.stamp()`, `S2OfficeConverter` (Office/Hangul document conversion)
- **Front-end Web Viewer**: `/s2-kit/pdf-viewer/pdf-viewer.js`, `pdf-viewer.css` (Embedded standalone ES module viewer powered by React)
- **Infrastructure Scripts**: Podman-based LibreOffice/Chromium online & offline converter installer (`scripts/setup-s2-office-converter.sh`)

For full details, API references, and complete code examples, see the **[s2-kit-pdf README](s2-kit-pdf/README.md)**.

#### SFTP (`S2SftpFileManagerImpl`)

```java
// The server host key is verified against known_hosts (~/.ssh/known_hosts when null)
FileManager sftp = new S2SftpFileManagerImpl("sftp.example.com", 22, "app", "/keys/id_ed25519", null, null,
        null, null, null, "/etc/ssh/known_hosts_sftp", false);
sftp.writeFile(inputStream, "/upload/2026", "report.pdf");          // "../x" as the name is rejected
```

#### AOP parameters (`S2ContextUtil`)

```java
@Before("@annotation(audited)")
public void audit(JoinPoint joinPoint, Audited audited) {
    // A parameter named userId, or a userId field of a VO/Map argument
    Object userId = S2ContextUtil.getJoinPointParameter(joinPoint, "userId");
}
```

#### JSON Helpers (`S2JsonUtil`, s2-core)

Since `s2-core` 2.0.0, `S2JsonUtil` lives in `s2-core` (`io.github.devers2.s2util.json.S2JsonUtil`). `s2-kit` brings `s2-core` along,
so no extra dependency is needed. See the "Lightweight JSON" section of the s2-util MANUAL for details.

```java
import io.github.devers2.s2util.json.S2JsonUtil;

String json = S2JsonUtil.toJson(myObject);
MyDto dto = S2JsonUtil.fromJson(json, MyDto.class);   // throws S2JsonException on failure (never returns null)
```

---

## ⚙️ Requirements

This project is built with **JDK 21**, but it can be used reliably in all environments running **Java 17 or higher**.

---

## 📜 License & Copyright

This library is provided under the **Apache License 2.0**. You are free to use, modify, and distribute this software, provided that you comply with the obligations of the license (such as copyright notice and source code disclosure requirements). For detailed terms and conditions, please refer to the **[LICENSE](./LICENSE)** file.

- **Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)**
- Contact: [eseungsu.dev@gmail.com](mailto:eseungsu.dev@gmail.com)

**Third-party Notice:** This project uses external libraries. For detailed third-party license notices, please refer to the **[licenses/NOTICE](./licenses/NOTICE)** file.

---

s2-kit Version: 1.0.0 (2026-10-02)

[//]: # 'S2_DEPS_INFO_START'

---

**To use certain functionalities (e.g., S2BindValidator), the end-user project must explicitly add the following dependencies to be available at runtime.** Failure to include these dependencies will result in a `java.lang.NoClassDefFoundError` at runtime.

**[For Gradle Users]**

```groovy
dependencies {
    // Essential runtime dependencies for optional functionalities
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
