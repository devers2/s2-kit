## GNU Lesser General Public License, Version 2.1 (LGPL 2.1) Compliance & Architecture Guide

### 1. Dual Engine Architecture for `S2PdfUtil`

`S2PdfUtil` provides HTML-to-PDF rendering through a **dual-engine architecture** designed to give enterprise users complete control over licensing and system requirements:

1. **Option A: Headless Chromium Engine (Recommended, 100% LGPL-Free)**
   - When a browser executable (`s2-chrome`, `chromium`, `google-chrome`, `msedge`, etc.) is detected on the server or configured via `S2PdfUtil.setBrowserCommand(...)`, `S2PdfUtil` prints HTML and web pages using modern headless Chromium.
   - **License:** 100% **Apache 2.0 / BSD / MIT friendly**. No LGPL-licensed libraries are loaded or executed.
   - **Rendering Quality:** Full support for modern HTML5, CSS3 (Flexbox, CSS Grid), web fonts, SVG, and client-side JavaScript.
   - **Dependency:** No extra Java libraries required (`openhtmltopdf` can be completely excluded).

2. **Option B: Pure-Java In-Memory Engine (OpenHTMLtoPDF Fallback)**
   - When no browser executable is installed or available on the host, `S2PdfUtil` can fall back to the pure-Java OpenHTMLtoPDF library.
   - OpenHTMLtoPDF (modules: `openhtmltopdf-core`, `openhtmltopdf-pdfbox`) is licensed under **GNU LGPL, version 2.1 or later**.
   - If your deployment uses this fallback engine, the LGPL 2.1 compliance guidelines in Section 2 apply.

---

### 2. LGPL 2.1 Compliance When Using OpenHTMLtoPDF

`s2-kit` **does not include or redistribute** OpenHTMLtoPDF binary JAR files:
- `s2-kit` declares OpenHTMLtoPDF as a `compileOnly` dependency and accesses it only via dynamic/isolated bridges.
- `s2-kit` itself is released under the **Apache License 2.0**.
- The source code of OpenHTMLtoPDF is publicly available at:
  [https://github.com/openhtmltopdf/openhtmltopdf](https://github.com/openhtmltopdf/openhtmltopdf).

If **your application redistributes** OpenHTMLtoPDF (e.g., inside a Fat JAR, WAR, or installer), the LGPL obligations apply to your distribution:
- Include the LGPL text (`licenses/LICENSE-LGPL-2.1`).
- Provide or point to the library's source code.
- Allow the library to be replaced by the end user (e.g., keeping it as a separate JAR file on the classpath or facilitating relinking).

---

### 3. Dependency Configuration Guide

#### A. When Running with Chromium / `s2-office-converter` (LGPL-Free, Recommended)

You only need `jsoup` (MIT) for HTML sanitization and resource preprocessing. OpenHTMLtoPDF is **not needed**:

```groovy
dependencies {
    // HTML preprocessing (MIT License)
    implementation 'org.jsoup:jsoup:1.23.2'
    // No LGPL libraries required!
}
```

#### B. When Running without a Browser (OpenHTMLtoPDF Fallback)

If the server environment cannot run Chromium, explicitly add OpenHTMLtoPDF to the runtime classpath:

```groovy
dependencies {
    implementation 'org.jsoup:jsoup:1.23.2'
    // Optional pure-Java fallback engine (LGPL 2.1)
    implementation 'io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.87'
}
```

If neither a browser nor OpenHTMLtoPDF is available when an HTML-to-PDF conversion is requested, `S2PdfUtil` throws a descriptive `IllegalStateException` guiding the operator on how to enable either option.
