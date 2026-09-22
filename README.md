# s2-support — Opinionated Application Utilities

🌐 **English** | [한국어](README.ko.md)

[![Java CI](https://github.com/devers2/s2-support/actions/workflows/ci.yml/badge.svg)](https://github.com/devers2/s2-support/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.devers2.internal/s2-support?color=brightgreen&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.devers2.internal/s2-support)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](./LICENSE)

> An **opinionated companion library** providing a curated collection of helper classes and convenience utilities built on top of `s2-core` and `s2-validator`.
> Primarily designed to streamline **personal development workflows** and support recurring application patterns across author-specific projects.

---

## 📖 Overview

`s2-support` consolidates frequently used helper modules and boilerplate reductions tailored to practical Java/Spring application development. It covers file management, pagination, Spring context utilities, JSON/encryption/image helpers, and more.

---

## ✨ Key Utilities

- **📑 PDF Engine & Multi-Format Merge** — High-fidelity HTML/image/text/SVG to PDF conversion, sequence-guaranteed multi-format merging, async distributed URL pre-fetch, zero-leak disk stream caching, and batch page numbering via `S2PdfUtil`
- **📁 File Management** — Local and remote (SFTP/JSch) file operations via `FileManager`, `S2File`, `S2RemoteFile`
- **📄 Pagination** — Ready-to-use `S2PaginationInfo`, `S2PaginationTag`, and `S2SearchVO` for list/search UIs
- **🍃 Spring Utilities** — `S2ContextUtil`, `S2AutoConfiguration`, `S2AnnotationResolver`, `S2RestApiUtil` for Spring-based apps
- **🔧 General Helpers** — `S2JsonUtil`, `S2HashUtil`, `S2EncryptionUtil`, `S2ImageUtil`, `S2TypeUtil`, `S2CollectionUtil`, `S2StreamUtil`, `S2ServletUtil`, `S2QueryStringUtil`, `S2Uuid`
- **🗂️ Data Structures** — `S2LruMap` (LRU cache backed by `LinkedHashMap`)

---

## 🚀 Quick Start

### 1. Installation

Add the following dependency to your `build.gradle` or `pom.xml`.

**[Gradle]**

```groovy
dependencies {
    implementation 'io.github.devers2.internal:s2-support:1.1.5'
}
```

**[Maven]**

```xml
<dependency>
    <groupId>io.github.devers2.internal</groupId>
    <artifactId>s2-support</artifactId>
    <version>1.1.5</version>
</dependency>
```

#### Optional Companion Modules (`s2-validator`, `s2-validator-plugin`, `s2-jpa`)

> [!NOTE]
> `s2-support` automatically includes **`s2-core`** as an `api` (transitive) dependency, so core reflection, caching, date/string, and thread utilities are immediately available out-of-the-box.

Depending on your application's requirements, you can optionally include companion modules from the **[s2-util suite](https://github.com/devers2/s2-util)**:

| Module | Type & Coordinates | Key Features & Purpose |
| :--- | :--- | :--- |
| **`s2-validator`** | Library<br>`io.github.devers2:s2-validator:1.2.0` | **Cross-Platform Dynamic Validator**<br>• Author validation rules once in Java and synchronize seamlessly with client-side JavaScript (`s2.validator.js`).<br>• 30+ built-in rules (email, phone, date, etc.) with smart Korean particle interpolation (`{0|은/는}`).<br>• Fluent chaining API, conditional validation (`when`/`and`), nested/collection object validation.<br>• Seamless Spring MVC integration via `S2BindValidator` (`BindingResult`). |
| **`s2-validator-plugin`** | Gradle Plugin<br>`id 'io.github.devers2.validator' version '1.1.3'` | **Compile-Time Field Validation** *(Optional companion for `s2-validator`)*<br>• AST-based static analysis during build (`compileJava`).<br>• Inspects `.field("fieldName")` in `S2Validator.<DTO>builder()` to verify fields exist on the target DTO class, preventing field mismatches or refactoring regressions before runtime.<br>• Zero configuration required (Gradle only). |
| **`s2-jpa`** | Library<br>`io.github.devers2:s2-jpa:1.2.0` | **Dynamic JPQL Query Builder**<br>• Template-based dynamic query construction using `S2Jpql` with `{{=key}}` placeholders.<br>• Fluent conditional parameter and clause binding (`bindClause`, `bindParameter`, `bindOrderBy`).<br>• Safe LIKE search with `LikeMode` (ANYWHERE, START, END) preventing injection. |
| **`s2-util`** *(Bundle)* | Library<br>`io.github.devers2:s2-util:1.2.0` | **All-in-One Suite**<br>• Full bundle containing `s2-core`, `s2-validator`, and `s2-jpa` libraries together.<br>• *(⚠️ Note: Even with the full bundle, the compile-time validation Gradle plugin must still be added to the `plugins {}` block separately)* |

**Example Dependency Setup (Gradle):**

```groovy
// build.gradle
plugins {
    id 'java'
    // [Optional] Compile-time field validation plugin for S2Validator (Gradle only, declared separately from libraries)
    id 'io.github.devers2.validator' version '1.1.3'
}

dependencies {
    // Base: s2-support (s2-core is included automatically)
    implementation 'io.github.devers2.internal:s2-support:1.1.5'

    // [Optional] Server & Client Unified Validation
    implementation 'io.github.devers2:s2-validator:1.2.0'

    // [Optional] Dynamic JPQL Queries
    implementation 'io.github.devers2:s2-jpa:1.2.0'

    // Or simply use the full bundle instead of individual modules:
    // (⚠️ Note: The Gradle plugin above must still be added to plugins {} separately)
    // implementation 'io.github.devers2:s2-util:1.2.0'
}
```

**Example Dependency Setup (Maven):**

```xml
<!-- Base: s2-support (s2-core is included automatically) -->
<dependency>
    <groupId>io.github.devers2.internal</groupId>
    <artifactId>s2-support</artifactId>
    <version>1.1.5</version>
</dependency>

<!-- [Optional] s2-validator -->
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-validator</artifactId>
    <version>1.2.0</version>
</dependency>

<!-- [Optional] s2-jpa -->
<dependency>
    <groupId>io.github.devers2</groupId>
    <artifactId>s2-jpa</artifactId>
    <version>1.2.0</version>
</dependency>
```

### 2. Usage Examples

#### Pagination (`S2PaginationInfo`)

```java
S2PaginationInfo pagination = new S2PaginationInfo();
pagination.setCurrentPageNo(1);
pagination.setRecordCountPerPage(10);
pagination.setPageSize(5);
pagination.setTotalRecordCount(150);

int offset = pagination.getFirstRecordIndex(); // 0
```

#### Spring Context Access (`S2ContextUtil`)

```java
// Access Spring-managed beans statically anywhere in your application
MyService service = S2ContextUtil.getBean(MyService.class);
```

#### JSON Helpers (`S2JsonUtil`)

```java
// Fast serialization and deserialization
String json = S2JsonUtil.toJson(myObject);
MyDto dto = S2JsonUtil.fromJson(json, MyDto.class);
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

s2-support Version: 1.1.5 (2026-09-11)

[//]: # 'S2_DEPS_INFO_START'

---

**To use certain functionalities (e.g., S2BindValidator), the end-user project must explicitly add the following dependencies to be available at runtime.** Failure to include these dependencies will result in a `java.lang.NoClassDefFoundError` at runtime.

**[For Gradle Users]**

```groovy
dependencies {
    // Essential runtime dependencies for optional functionalities
    implementation 'com.google.code.findbugs:jsr305:3.0.2'
    implementation 'org.springframework:spring-context:6.1.1'
    implementation 'org.springframework:spring-web:6.1.1'
    implementation 'org.springframework.integration:spring-integration-sftp:6.1.1'
    implementation 'jakarta.servlet:jakarta.servlet-api:6.1.0'
    implementation 'jakarta.servlet.jsp:jakarta.servlet.jsp-api:4.0.0'
    implementation 'org.aspectj:aspectjweaver:1.9.25.1'
    implementation 'org.jsoup:jsoup:1.23.2'
    implementation 'io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.85'
}
```

[//]: # 'S2_DEPS_INFO_END'
