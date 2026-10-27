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

### 2. Frontend Web PDF Viewer (HTML / Thymeleaf / JSP / Vanilla JS)

All static web assets are packaged inside the JAR (`META-INF/resources/s2-kit/pdf-viewer/`), allowing instant integration in modern web applications without npm or extra build steps. In addition, `pdf-viewer.js` includes comprehensive **JSDoc and TypeScript definitions** at the top of the bundle for seamless IDE auto-completion.

#### Basic Integration Example (Thymeleaf / JSP / HTML)

```html
<!-- 1. Load stylesheet -->
<link rel="stylesheet" th:href="@{/s2-kit/pdf-viewer/pdf-viewer.css}" href="/s2-kit/pdf-viewer/pdf-viewer.css">

<!-- 2. Target container element -->
<div id="pdf-viewer-container" style="width: 100%; height: 800px;"></div>

<!-- 3. ES module import & initialization -->
<script type="module">
    import { S2PdfViewer } from '/s2-kit/pdf-viewer/pdf-viewer.js';

    const viewer = new S2PdfViewer('pdf-viewer-container', {
        src: '/api/v1/files/download?id=123', // Download URL or Uint8Array / ArrayBuffer
        fileName: 'report.pdf',
        config: {
            zoom: 'page-width', // 'auto' | 'page-fit' | 'page-width' | number (e.g. 1.2)
            lazy: true          // Render only viewport pages (optimized for large documents)
        },
        // (Optional) Timestamp Authority (TSA) / Digital signature verification badge
        timestamp: {
            timestamped: true,
            issuedAt: '2026-10-27T15:30:00Z',
            authority: 'KICA TSA',
            verified: true
        }
    });

    // Event subscriptions
    viewer.on('load', ({ numPages, src }) => {
        console.log(`Document loaded: ${numPages} pages`);
    });

    viewer.on('error', ({ error, phase, src }) => {
        console.error(`[${phase}] error:`, error);
    });

    // Await ready
    await viewer.ready;

    // Cleanup resources upon unmount or closing modal (prevents memory leaks):
    // viewer.destroy();
</script>
```

#### Options Reference (`S2PdfViewerOptions`)

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `src` **(Required)** | `string \| ArrayBuffer \| Uint8Array` | - | PDF document URL path or binary byte buffer |
| `fileName` | `string` | `undefined` | Original file name displayed on toolbar and download action |
| `config.zoom` | `'auto' \| 'page-fit' \| 'page-width' \| number` | `'page-width'` | Initial zoom level (number e.g. 1.0 = 100%, 1.5 = 150%) |
| `config.lazy` | `boolean` | `true` | Viewport-aware virtualization (renders only pages near viewport) |
| `config.scrollMode` | `'vertical' \| 'horizontal' \| 'wrapped'` | `'vertical'` | Document scroll layout direction |
| `timestamp` | `object` | `undefined` | TSA timestamp / digital signature verification badge |
| `timestamp.timestamped` | `boolean` | - | Whether TSA timestamp is present |
| `timestamp.issuedAt` | `string` | - | Issuance date/time in ISO-8601 format |
| `timestamp.authority` | `string` | - | Certified Timestamp Authority name |
| `timestamp.verified` | `boolean` | - | Whether signature verification succeeded |

#### Key Methods & Lifecycle API

- **`await viewer.ready`**: `Promise<{ numPages: number }>` — Resolves when document parsing is complete and ready.
- **`viewer.on(event, handler)` / `viewer.off(event, handler)`**:
  - `'load'`: `({ numPages, src }) => void` — Triggered when document finishes loading.
  - `'error'`: `({ error, phase, src }) => void` — Triggered on load (`'load'`) or rendering (`'render'`) error.
- **`viewer.destroy()`**: Unmounts React root, removes DOM listeners, and terminates Mozilla pdf.js worker threads to prevent memory leaks in SPAs.
- **`getViewerInstance(elementId)`**: Retrieves an active `S2PdfViewer` instance by its container element ID.

---

### 3. Backend PDF Merge and Office Conversion (`S2PdfUtil`, `S2OfficeConverter`)

```java
import io.github.devers2.s2kit.support.S2PdfUtil;
import io.github.devers2.s2kit.support.S2PdfUtil.PdfSource;
import io.github.devers2.s2kit.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2kit.support.S2PdfUtil.StampOptions;
import io.github.devers2.s2kit.support.S2OfficeConverter;

// 1. Korean/CJK Font Configuration
S2PdfUtil.setDefaultFont(Path.of("/usr/share/fonts/truetype/nanum/NanumGothic.ttf"));

// 2. Multi-Format PDF Merging
var options = MergeOptions.create()
        .bookmarks(true)          // Generate document outline bookmarks
        .pageNumber(S2PdfUtil.PageNumber.of("[%d / %d]").topRight(20, 20)) // 9-position custom page numbers (or .pageNumbers(true))
        // .stampOptions(stampOptions) // Direct composite stamping (text, images, QR, watermark) during merge
        .title("Consolidated Report")
        .imageDpi(200)            // Resize embedded photos for file size optimization
        .cache(true);             // Reuse intermediate converted cache

try (InputStream pdf = S2PdfUtil.merge(List.of(
        PdfSource.ofHtml("<h1>Cover</h1>").title("Cover"),
        PdfSource.ofPdf(Path.of("body.pdf")),
        PdfSource.ofDocument(Path.of("plan.docx")),  // Automated LibreOffice conversion
        PdfSource.ofDocument(Path.of("report.hwp")), // Automated H2Orestart Hangul conversion
        PdfSource.ofUrl("https://example.com/chart.png").maxBytes(10_000_000)
), options)) {
    pdf.transferTo(response.getOutputStream());
}

// 3. Composite Stamping (Text, Seal Image, Verification QR, Page Numbers, Page Selection)
var stampOptions = StampOptions.builder()
        // Header text on pages starting from page 2 (skips cover page)
        .addText(S2PdfUtil.Text.of("CONFIDENTIAL REPORT")
                .position(S2PdfUtil.Position.TOP_CENTER)
                .pages(S2PdfUtil.PageSelector.exceptFirst()))
        // Certification seal image on the first page only
        .addImage(S2PdfUtil.ImageStamp.of(Path.of("seal.png"))
                .topRight(25, 25)
                .size(60, 60)
                .opacity(0.85f)
                .pages(S2PdfUtil.PageSelector.first()))
        // Verification QR code on the last page only
        .addQr(S2PdfUtil.QrStamp.ofUrl("https://verify.example.com/doc/12345")
                .bottomRight(20, 20)
                .size(75f)
                .pages(S2PdfUtil.PageSelector.last()))
        // Page numbers centered at the bottom of all pages
        .pageNumber(S2PdfUtil.PageNumber.of("%d / %d")
                .position(S2PdfUtil.Position.BOTTOM_CENTER))
        .build();

try (InputStream stamped = S2PdfUtil.stamp(pdfStream, stampOptions)) {
    stamped.transferTo(response.getOutputStream());
}

// 4. Office/Hangul Document Web Editor HTML Extraction
if (S2OfficeConverter.isAvailable()) {
    var imported = S2OfficeConverter.toHtml(Path.of("report.hwp"),
            S2OfficeConverter.HtmlOptions.create().maxImageWidth(1600));
    String html = imported.html(imageName -> "/api/v1/files/view?name=" + imageName);
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

- `s2-kit-pdf` is released under the **[Apache License 2.0](LICENSE)** as a **100% LGPL-Free** library.
- HTML-to-PDF and SVG rendering operate **100% LGPL-Free (Apache 2.0 / BSD)** powered by headless Chromium (`s2-chrome` or system browser).


[//]: # 'S2_DEPS_INFO_START'

---

**To use certain functionalities (e.g., S2BindValidator), the end-user project must explicitly add the following dependencies to be available at runtime.** Failure to include these dependencies will result in a `java.lang.NoClassDefFoundError` at runtime.

**[For Gradle Users]**

```groovy
dependencies {
    // Essential runtime dependencies for optional functionalities
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