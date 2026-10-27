# s2-kit-pdf — PDF and Office Document Solution Module (s2-kit)

**English** | [한국어](README.ko.md)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.devers2/s2-kit-pdf?color=brightgreen&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.devers2/s2-kit-pdf)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

> 📦 Dedicated PDF and document viewer extension module for **[`s2-kit`](../README.md)**.
> Provides an all-in-one solution from server infrastructure (Podman converter installer) to backend (PDF merge/stamp/converter) and frontend (web PDF viewer component).

---

## 📖 Module Architecture

`s2-kit-pdf` contains all 3 tiers needed for end-to-end document processing:

```
s2-kit-pdf/
├── scripts/                        # 1. Server converter installer (Podman online / offline)
│   ├── setup-s2-office-converter.sh
│   └── README.md
├── src/main/java/                  # 2. Backend PDF & Office conversion engine
│   └── io/github/devers2/s2kit/support/
│       ├── S2PdfUtil.java          # PDF merge, watermark/stamp, rendering
│       ├── S2OfficeConverter.java  # LibreOffice + H2Orestart Hangul conversion
│       └── S2MarkdownUtil.java     # Markdown → HTML/PDF conversion
└── src/main/resources/             # 3. Frontend Web PDF viewer assets
    └── META-INF/resources/s2-kit/pdf-viewer/
        ├── pdf-viewer.js           # Standalone web viewer (bundled React, full JSDoc)
        ├── pdf-viewer.css          # Viewer stylesheet
        └── pdfjs/                  # Mozilla pdf.js worker, fonts, wasm decoders
```

---

## 🚀 Quick Start

### 1. Add Dependency (Gradle / Maven)

Declaring `s2-kit-pdf` transitively provides the core `s2-kit` and `s2-core` libraries automatically.

**Gradle (Kotlin DSL)**
```kotlin
implementation("io.github.devers2:s2-kit-pdf:1.0.0")
```

**Gradle (Groovy)**
```groovy
implementation 'io.github.devers2:s2-kit-pdf:1.0.0'
```

---

### 2. Frontend Web PDF Viewer (HTML / Thymeleaf / Vanilla JS)

All static assets are packaged inside the JAR (`META-INF/resources/`), so you can use it immediately without npm or build steps:

```html
<!-- 1. Load stylesheet -->
<link rel="stylesheet" th:href="@{/s2-kit/pdf-viewer/pdf-viewer.css}">

<!-- 2. Target container -->
<div id="pdf-viewer-container" style="width: 100%; height: 800px;"></div>

<!-- 3. ES module import & initialization -->
<script type="module" th:inline="javascript">
    import { S2PdfViewer } from /*[[@{/s2-kit/pdf-viewer/pdf-viewer.js}]]*/ '/s2-kit/pdf-viewer/pdf-viewer.js';

    const viewer = new S2PdfViewer('pdf-viewer-container', {
        src: '/api/v1/files/download?id=123',
        fileName: 'report.pdf',
        config: {
            zoom: 'page-width', // 'auto' | 'page-fit' | 'page-width' | number (e.g. 1.2)
            lazy: true          // Render only viewport pages (optimized for large documents)
        },
        timestamp: {
            timestamped: true,
            issuedAt: '2026-10-27T15:30:00Z',
            authority: 'KICA TSA',
            verified: true
        }
    });

    viewer.on('load', ({ numPages }) => {
        console.log(`Document loaded: ${numPages} pages`);
    });

    viewer.on('error', ({ error, phase }) => {
        console.error(`[${phase}] error:`, error);
    });

    // Cleanup when unmounting or closing modal:
    // viewer.destroy();
</script>
```

---

### 3. Backend PDF Merge and Stamping (`S2PdfUtil`)

```java
import io.github.devers2.s2kit.support.S2PdfUtil;
import io.github.devers2.s2kit.support.S2PdfUtil.PdfSource;
import io.github.devers2.s2kit.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2kit.support.S2PdfUtil.StampOptions;

var options = MergeOptions.create()
        .bookmarks(true)
        .pageNumbers(true)
        .title("Merged Report");

try (InputStream pdf = S2PdfUtil.merge(List.of(
        PdfSource.ofHtml("<h1>Cover</h1>").title("Cover"),
        PdfSource.ofPdf(Path.of("body.pdf")),
        PdfSource.ofUrl("https://example.com/chart.png").maxBytes(10_000_000)
), options)) {
    pdf.transferTo(response.getOutputStream());
}
```

---

### 4. Server Engine Setup (Podman Scripts)

Use the scripts in `s2-kit-pdf/scripts/` to set up containerized LibreOffice and headless Chromium without manual server configuration:

- **Online installation**:
  ```bash
  cd scripts/
  ./setup-s2-office-converter.sh
  ```
- **Self-contained offline installer creation (`--make-offline`)**:
  ```bash
  ./setup-s2-office-converter.sh --make-offline --target el9
  ```

See [scripts/README.md](scripts/README.md) for full details.

---

## ⚖️ License

- `s2-kit-pdf` is released under the **[Apache License 2.0](LICENSE)**.
- HTML-to-PDF rendering operates **100% LGPL-Free (Apache 2.0 / BSD)** using headless Chromium (`s2-chrome`).
- See [README-LGPL-2.1-PDF.md](README-LGPL-2.1-PDF.md) for guidelines on the optional pure-Java OpenHTMLtoPDF (LGPL 2.1) fallback.


[//]: # 'S2_DEPS_INFO_START'

---

**To use certain functionalities (e.g., S2BindValidator), the end-user project must explicitly add the following dependencies to be available at runtime.** Failure to include these dependencies will result in a `java.lang.NoClassDefFoundError` at runtime.

**[For Gradle Users]**

```groovy
dependencies {
    // Essential runtime dependencies for optional functionalities
    implementation 'io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.87'
    implementation 'io.github.openhtmltopdf:openhtmltopdf-svg-support:1.1.87'
    implementation 'org.commonmark:commonmark:0.30.0'
    implementation 'org.commonmark:commonmark-ext-gfm-tables:0.30.0'
    implementation 'org.commonmark:commonmark-ext-gfm-strikethrough:0.30.0'
    implementation 'org.commonmark:commonmark-ext-autolink:0.30.0'
    implementation 'org.commonmark:commonmark-ext-task-list-items:0.30.0'
    implementation 'org.commonmark:commonmark-ext-heading-anchor:0.30.0'
    implementation 'jakarta.servlet:jakarta.servlet-api:6.1.0'
    implementation 'com.github.ben-manes.caffeine:caffeine:3.2.3'
}
```

[//]: # 'S2_DEPS_INFO_END'