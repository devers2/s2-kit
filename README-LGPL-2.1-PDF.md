## GNU Lesser General Public License, Version 2.1 (LGPL 2.1) Compliance Guide

### 1. License Notice

`S2PdfUtil` uses the OpenHTMLToPDF library (modules: openhtmltopdf-core, openhtmltopdf-pdfbox), which is licensed under the
**GNU LGPL, version 2.1 or later**.

s2-support **does not include or redistribute** OpenHTMLToPDF (see section 2): the application adds it as its own
dependency from Maven Central, and s2-support only calls its public API. s2-support itself remains under the Apache
License 2.0, and the source code of OpenHTMLToPDF is available from its project:
[https://github.com/openhtmltopdf/openhtmltopdf](https://github.com/openhtmltopdf/openhtmltopdf).

If **your application redistributes** OpenHTMLToPDF (for example inside a Fat JAR, a WAR or an installer), the LGPL
obligations apply to your distribution: include the LGPL text (`licenses/LICENSE-LGPL-2.1`), provide or point to the
library's source code, and allow the library to be replaced (keep it as a separate JAR, or otherwise allow relinking).

---

### 2. ❗ Important: OpenHTMLToPDF Dependency Notice (`compileOnly` Method)

This S2Util Library uses the OpenHTMLToPDF library via the **`compileOnly`** method, which means the **OpenHTMLToPDF JAR files are NOT included in this product.**

**To use the S2PdfUtil functionality, the end-user project must explicitly add the following dependencies to be available at runtime.** Failure to include these dependencies will result in a `java.lang.NoClassDefFoundError` at runtime.

**[For Gradle Users]**

```groovy
dependencies {
  // Essential runtime dependencies for S2PdfUtil functionality
  implementation 'org.jsoup:jsoup:1.23.2'
  implementation 'io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.85'
}
```

