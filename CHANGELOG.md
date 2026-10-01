# Changelog

**English** | [한국어](./CHANGELOG.ko.md)

All notable changes to `s2-support` are recorded here.

## [2.0.0] - Unreleased

Compared with 1.1.5. This is a **major release** that does not keep backward compatibility; read the items marked ⚠️
before upgrading.

### ⚠️ Compatibility

- **Requires `s2-core` 2.0.0.** `S2JsonUtil` was removed from `s2-support`; use `io.github.devers2.s2util.json.S2JsonUtil`
  in `s2-core`.
- ⚠️ **SFTP (`S2SftpFileManagerImpl`, `JschSessionFactory`) verifies the server host key by default.** The key is checked
  against `~/.ssh/known_hosts`, or the file given as `knownHostsPath`; a server whose key is not listed is refused.
  Register the key (`ssh-keyscan -p 22 host >> known_hosts`) or, only on a trusted network, pass
  `allowUnknownHosts = true`. Previously `StrictHostKeyChecking=no` was hard-coded (open to man-in-the-middle attacks).
- ⚠️ **`s2.util.js` escapes by default.**
  - `S2Util.template`: `{{=key}}` now inserts the HTML-escaped value. Use `{{-key}}` for trusted HTML.
  - `S2Util.alert`, `confirm`, `showToast`: a string message is shown as text (`\n` becomes a line break). Pass a DOM
    node for HTML. The toast and modal titles are text.
  - `S2Util.pagination` throws when `jsFunction` is not a function name.
- ⚠️ **Failures are no longer hidden.** These methods used to return `null`, `""`, `-1` or their input on failure and
  now throw (with the cause attached):
  - `S2EncryptionUtil.decrypt` threw nothing and returned the ciphertext on a wrong password; it now throws
    `GeneralSecurityException` (`AEADBadTagException` for a wrong password or tampered data). `encrypt`/`decrypt` declare
    `GeneralSecurityException`.
  - `S2StreamUtil.streamToByteArray` (over 50MB or on I/O errors), `convertStreamToString`.
  - `S2HashUtil.generateSHA256/512/512To256`, `generateXXHash64`: `null` input throws `NullPointerException`; an empty or
    blank string is hashed (it used to return `""`); a missing file throws.
  - `S2ImageUtil.convertImage`, `imageResize`, `convertImageExtension`, `encodeImageToBase64`.
  - `S2QueryStringUtil`: malformed percent-encoding throws `IllegalArgumentException`.
  - `S2FileUtil.deleteTemporaryFilesOlderThan` requires a prefix (the system temp directory is shared with other programs).
  - `S2FileUtil` file operations: `streamToFile`, `streamToTempFile` (were `-1`/`null`; a failed write now also removes
    its partial file), `processStreamWithTempFile` (called the processor with `null`), `makeDirectory` (now `true` when
    created, `false` when it already existed), `delete` (`true` when deleted, `false` when absent; a path that cannot be
    deleted throws after the rest is removed), `getSize`, `fileToReader`/`fileToInputStream` (blank paths). Cleanup in
    `finally` blocks and cleaners uses the new `deleteQuietly`, which logs instead of throwing.
  - `S2PdfUtil`: Korean (CJK) text with no Korean font available throws instead of printing `#` (install a Korean
    font or call `setDefaultFont`); a missing font resource throws; `addPageNumbers` throws instead of returning `null`.
  - `FileManager.downloadRemoteFile` without a file manager follows the `writeFile` rule and does not overwrite.
- **New encryption and password hash formats** (`s2v2:` prefix). `S2EncryptionUtil` uses AES-256-GCM, so a wrong password
  or modified data always fails; `S2HashUtil.hash` uses 310,000 PBKDF2 iterations and stores the count. Values written by
  1.x (no prefix) are still decrypted and verified; `S2EncryptionUtil.isLegacyFormat` and `S2HashUtil.needsRehash` tell
  when to re-save them.
- **`S2AutoConfiguration` was removed** with `META-INF/spring.factories` and the `AutoConfiguration.imports` entry. It only
  created a logger; nothing needs to be configured.
- ⚠️ **`S2ServletUtil.getClientIp` / `getRealServerName` no longer trust request headers.** They return
  `request.getRemoteAddr()` / `request.getServerName()`, which a client cannot forge. Behind a reverse proxy, let the
  container apply the forwarded headers (Spring Boot `server.forward-headers-strategy=native`, Tomcat `RemoteIpValve`), or
  use the new overloads that take the trusted proxy addresses (`getClientIp(request, Set.of("10.0.0.5"))`), which read
  `X-Forwarded-For` from the right and skip only trusted hops.
- ⚠️ **`S2ServletUtil.getPrevServletPath` returns `""` for a Referer from another origin or context** (it was returned
  as is, an open redirect when used for redirects).
- ⚠️ **Errors instead of arbitrary results:** `S2AnnotationResolver.resolveType` throws `IllegalStateException` when
  several classes match (it picked the first); `S2TypeUtil.compare` throws `IllegalArgumentException` for values that
  cannot be compared (it returned 0, "equal"); `S2TypeUtil.castByName` throws `TypeMismatchException` explaining a class
  loader mismatch (the `ClassCastException` used to appear later in the caller); `S2CollectionUtil.listSort` throws for an
  `orderBy` other than ASC/DESC and always returns a new list; `S2LruMap.createSynchronizedLRUMap` rejects a capacity
  below 1.
- **`S2RestApiUtil.callApi` returns the response body as is** (unicode escapes used to be decoded, which broke JSON). POST
  sends `application/x-www-form-urlencoded` unless a parameter is a file (`Resource`, `byte[]`); a new overload takes
  request headers.
- `s2.util.js` `S2Util.validate(form)` is deprecated in favor of `S2Validator` in s2-validator.
- Compile-time Spring versions: Spring Framework 6.2.19, Spring Integration 6.5.10.
- ⚠️ **`FileManager.writeFile` no longer overwrites an existing file.** The local and Spring SFTP managers replaced it
  silently while the JSch SFTP manager refused it; all three now throw `S2RuntimeException` and leave the existing file
  unchanged. Pass `overwrite = true` to the new `writeFile(data, savePath, saveName, overwrite)` to replace it. Custom
  `FileManager` implementations implement that four-argument method (the three-argument one is a default method). The
  local manager creates the file atomically (`CREATE_NEW`), so concurrent writes of one name leave a single winner, and
  a failed write removes its partial file. Failures throw instead of returning `-1`.
- **jsch** moved to the maintained fork `com.github.mwiede:jsch` (same `com.jcraft.jsch` package; supports rsa-sha2 and
  current OpenSSH servers).

### Added

- Key-based encryption in `S2EncryptionUtil` for many values: `generateKey()`, `keyToBase64`/`keyFromBase64`,
  `encrypt(text, SecretKey)`/`decrypt(text, SecretKey)` (AES-256-GCM, `s2k1:<key name>:` prefix, under 1 ms per value;
  1,000 round trips take about 0.1 s). The key name is authenticated, and `KeyRing` rotates keys without downtime:
  it encrypts with a primary key, decrypts with the key named in the ciphertext, and `needsReencrypt`/`reencrypt` move
  old values to the primary key. The single-key API writes the name `default`, so its values keep working after a
  rotation. The password-based API stays deliberately slow (PBKDF2, about 70 ms per value) for occasional
  use with a password typed by a person. The two formats cannot be mixed; decrypting with the other API throws a message
  naming the right one.
- `S2PdfUtil.merge(sources, MergeOptions)`: a bookmark per source (a PDF's own bookmarks move under it;
  `PdfSource.title`), page numbers (`pageNumbers`, `pageNumberStyle`), and title/author metadata. `pageNumbers(skipFirst, skipLast)` leaves out leading and trailing
  pages (cover, contents, appendix) and numbers the rest among themselves (`1 / N`). Page numbers sit upright at the
  bottom center of the page as shown, also on rotated pages (`/Rotate`).
- Conversion result cache: requests with `MergeOptions.cache(true)` keep the PDF converted from a source that needs
  converting (document, HTML, web page, image, text, SVG) and reuse it when the same source comes again (4 documents:
  9.6 s → 0.12 s). Keys are the SHA-256 of the content and of the settings that shape the result (CSS and fonts,
  converter version, library version), so nobody can forge a key to get another user's result, and changing the
  converter converts again. A browser fallback is not kept. Off by default. `setConversionCache(folder, max size,
  max age, min free space)` (once at startup; default `java.io.tmpdir/s2-pdf-cache`, 1GB, 24 hours since last use,
  the larger of 10% of the disk and 5GB), `resetConversionCache`, `clearConversionCache`. An entry past its age is not
  served even before a cleanup removes it. Cleanup needs no batch job: a request starts it in the background, once, when the last one
  was before today or the size limit is exceeded (expired entries, then the least recently used). Nothing is stored
  when disk space is short, and the folder is readable only by the application account.
- Merge sources are converted concurrently (`setConversionParallelism`, default the smaller of the CPU count and 4).
  The order and the error message (the first failing source in order) stay the same; the first request for 4
  documents went from 9.8 s to 4.8 s (the slowest one). Conversions run on a pool made for each merge, so they never
  block the shared pool the URL downloads use.
- Watermarks: `MergeOptions.watermark(Watermark.of(image)...)` stamps an image on every page. Size both sides (`size`),
  the width only (`width`, height in ratio) or the height only (`height`, width in ratio); position at the center or
  one of eight directions (`Position`); `offset(x, y)` moves inward from an anchored edge, or right/down on a centered
  axis; `opacity`. Lengths in points. The image is embedded once and shared by all pages, PNG transparency is kept,
  and page numbers are drawn above it. On rotated pages (`/Rotate` 90, 180, 270) it is placed on the page as shown,
  upright.
- `S2PdfUtil.setDefaultFont(Path)` / `setDefaultFont(Class, String)` / `resetDefaultFont()`; without a configured font an
  installed Korean TrueType font is used (`SYSTEM_FONT_CANDIDATES`: Malgun Gothic, NanumGothic, Noto Sans KR, ...).
- `PdfSource.maxBytes(long)` (default 100MB for downloads and in-memory image sources).
- Office and Hangul documents in `S2PdfUtil.merge`: `PdfSource.ofDocument(...)` (doc, docx, odt, rtf, xls, xlsx, ods,
  csv, ppt, pptx, odp, hwp, hwpx) is converted by a LibreOffice-compatible command when one is installed:
  `setOfficeCommand`, the `S2_SOFFICE` environment variable, `s2-soffice` (a Podman converter with LibreOffice,
  H2Orestart and Korean fonts), `soffice`/`libreoffice` on the PATH, or the default install folders. No Java
  dependency is added. Without LibreOffice every other source still merges, and a document source fails with "오피스·한글 문서를 변환하려면
  s2-office-converter 설치가 필요합니다." (details in the cause and the log); `isOfficeConversionAvailable()` tells the caller in advance. Each conversion uses its own
  profile folder (conversions can run concurrently) and a time limit (`setOfficeTimeout`, default 3 minutes).
- HTML pages fetched by URL (`ofUrl`, `ofHtmlUrl`) come out as the browser shows them: the page's images (`<img>`, lazy
  `data-src`/`srcset`, CSS backgrounds) and stylesheets (`<link>`, `@import`) are fetched and embedded. The same origin is
  fetched as is, and request headers (login cookies) go only to it. Other hosts (CDNs) are fetched only when public;
  internal, loopback and link-local addresses are refused, through redirects too. Up to 300 resources, 20MB each and
  `maxBytes` in total are fetched concurrently; a resource that cannot be fetched is dropped with a warning (the PDF is
  still made). JavaScript does not run, and the default font stands in for web fonts. String HTML (`ofHtml`) still
  never fetches remote addresses. The renderer always matches the print medium, so media queries of URL pages are
  decided for a screen as wide as the A4 page (794px): `@media screen` applies, `@media print` (link addresses after
  links, hidden navigation) does not, and `min-width`/`max-width`, `not`, orientation and dark mode are decided so a
  responsive page gets the one layout that fits the page.
- Web pages through a browser: with `s2-chrome` (Chromium in a container without network, installed by _devtools2
  `s2-office-converter`), HTML pages fetched by URL are printed by Chromium, so flex, grid and JavaScript come out as on
  screen. The browser gets a file with the images and CSS already embedded under the rules above, so it never touches
  the network. When it is missing or fails (an error, over `setBrowserTimeout`, default 60 seconds, or a result that is
  not a PDF), the page is rendered by the built-in renderer with a warning. `setBrowserCommand`,
  `resetBrowserCommand`, `setBrowserRenderingEnabled`, `isBrowserRenderingAvailable`, environment variable
  `S2_CHROME`. A plain chrome/chromium with network access is never picked up automatically. No Java dependency is
  added.
- SVG drawing: with `io.github.openhtmltopdf:openhtmltopdf-svg-support` (Batik) on the classpath, `ofSvg`, `<svg>` in
  HTML and SVG images are drawn (`isSvgSupported()`). Scripts are off and only `data:` resources are allowed; external
  references inside an SVG (`<image>`, `<use>`) are removed (one refused reference blanks the whole SVG). Images inside
  the SVGs of a URL page are fetched and embedded under the same rules.

### Security

- `S2FileUtil.unzipFiles` rejects entries that point outside the target directory (Zip Slip) and limits the number of
  entries and the extracted size (default 10,000 entries, 1 GiB; an overload takes custom limits).
- SFTP `writeFile`/`readFile`/`deleteFile` reject a `saveName` that leaves `savePath` (`../`, absolute paths).
- `S2SearchVO.getOrderByList()` keeps only identifiers (`COLUMN`, `ALIAS.COLUMN`); `getOrderByList(allowedColumns)`
  also checks an allow list. The result is safe for MyBatis `${column}`.
- `S2PaginationTag` escapes `jsParam` inside `onclick` and rejects a `jsFunction` that is not a function name.
- `S2PdfUtil`: the HTML renderer loads only `data:` URIs, so URLs written in the HTML (`http:`, `file:`, `//host`) are
  never fetched (SSRF). `PdfSource.ofUrl` accepts only http/https. Resources of an HTML page fetched by URL follow the
  rule above (same origin or public addresses) only.
- `FileManager.downloadRemoteFile` accepts only http/https (no `file:`/`jar:`), sets connect/read timeouts, fails on a
  non-2xx response, checks the local save path, and sends a single request (file information comes from the same
  response).

### Fixed

- `s2fn.tld` and `s2tag.tld` pointed at classes that do not exist, so the JSP functions and the pagination tag could
  not be used. They now point at `s2-core` and `S2PaginationTag`; `formatter` was removed (no such method) and
  `paginationRecordNo` maps to `S2PaginationInfo.getRecordNo`.
- `Content-Disposition` parsing (`S2ServletUtil.getFilenameFromHeader`, `S2RemoteFile`): `filename*` wins over
  `filename` (RFC 6266), `+` is not turned into a space, quotes are removed, and directory parts are dropped. The parser
  is public as `S2FileUtil.parseContentDispositionFilename`.
- `S2FileUtil.zipDirectory` closes the directory stream and always uses `/` in entry names; `unzipFiles` creates missing
  parent directories.
- `S2SearchVO`: page number, unit and size are at least 1, and `getFirstIndex` no longer overflows to a negative value.
- `S2StreamUtil.streamToByteArray(Reader)` corrupted characters outside the BMP (emoji) that crossed the 32K buffer boundary.
- `S2QueryStringUtil.queryStringFromEntries` keeps parameter order, repeated keys and `#fragment`, and no longer
  double-encodes keys of the existing query (`a%5B0%5D` became `a%255B0%255D`).
- `S2ImageUtil`: without a fixed ratio, only the side over its limit is reduced (a 400×100 image with max width 200 was
  left unchanged); images of `TYPE_CUSTOM` no longer fail; transparency is kept in PNG and flattened on white for JPG;
  resizing uses bicubic interpolation; images over 100 megapixels are refused before decoding; the source file is deleted
  right after the result is written instead of in a JVM shutdown hook added per call.
- HTML to PDF: content after side-by-side `<img>` elements no longer vanishes (the XML re-parse put it inside the
  unclosed `<img>`). Whole documents (`<html>`, `<head>`) keep their own `<head>` CSS (it used to land inside the template
  body). Every `font-family` falls back to the default font, so Korean set in a font the server lacks (Malgun Gothic)
  no longer prints as `#`. Undeclared XML prefixes (Vue `v-on:click`/`:class`, Alpine `x-on:click`, `<o:p>` in HTML
  saved from Word) no longer fail the whole conversion (prefixed attributes are removed, prefixed elements unwrapped).
- SVG was never drawn: no SVG drawer was attached to the renderer, so `ofSvg` produced a blank page. With the module
  above it is drawn; without it `ofSvg` fails naming the dependency.
- `S2FileUtil.deleteFilesOlderThan` no longer deletes directories when a prefix is given, and never deletes the start
  directory.
- `licenses/NOTICE` listed OpenHTMLtoPDF as "LGPL 2.1 / MPL 2.0"; it is LGPL 2.1 or later, and no dependency uses MPL
  2.0, so `LICENSE-MPL-2.0` was removed. The NOTICE also lists Spring Web, Spring Integration SFTP and JSR-305, marks
  compile-only and optional dependencies, and names the licenses bundled with the JSch fork (JZlib, jBCrypt).
- `README-LGPL-2.1-PDF.md` said OpenHTMLToPDF was included and offered its source code, while s2-support does not
  include it (`compileOnly`). It now states that the application adds it and what applies when the application
  redistributes it. A stray "All right reserved" line in `s2.dropzone.js` and a modal header logo that does not exist
  (`SEEK_logo.png`) in `s2.util.css` were removed.
- `S2SftpFileManagerImpl` implements `AutoCloseable`: `close()` shuts down its SSH session pool (there was no way to
  release the connections and pool threads). SFTP failures keep the cause and its message (for example "file already
  exists" or the rejected host key).
- `SpringSftpConfig` required `sftp.private-key-path` and ignored `sftp.password`; it now uses the private key when set
  and the password otherwise, and fails at startup when neither is set.
- Both SFTP managers are tested against an embedded SSH server (Apache MINA SSHD): transfers, rejection of unknown and
  changed host keys, `allowUnknownHosts`, and path containment.
- README examples used APIs that do not exist (`S2ContextUtil.getBean`, `S2PaginationInfo` setters). They were replaced,
  and a test compiles them.
- `S2AnnotationResolver` loads scanned classes without running static initializers, through the base class's class
  loader; its cache no longer keeps classes (and their class loader) alive after a DevTools restart; scanning a
  subpackage after its parent no longer returns the same class twice.
- `S2ServletUtil.getApplicationRootPath` no longer creates an HTTP session; `getValueAll` stops at cycles.
- `S2FileUtil.joinPaths` no longer modifies the caller's array; `getApplicationRootPath(Class)` works for a class in a
  jar (the jar's directory) instead of throwing `NullPointerException`.
- `S2TypeUtil.createInstance` finds constructors whose parameters accept subtypes and boxed values.
- `S2CollectionUtil.listSort` sorts `NaN` and infinity instead of failing.
- `S2MarkupUtil.removeTagContent` removes an unclosed tag to the end (`"<script>alert(1)"` used to survive). It is not
  an HTML sanitizer.
- `s2.util.js` `S2Util.options` put HTML-escaped text into option values and labels (`A&B` became `A&amp;B`).
- `S2PdfUtil` detected a PDF served without a PDF Content-Type as HTML: the header check compared the fourth byte of
  `%PDF-` with `-`, so it never matched, and the PDF bytes were rendered as HTML (a garbage page, or an XML error). This
  was the intermittent CI failure of the magic-byte test, which only counted pages; it now also checks the text.
- `S2PdfUtil` Korean text: text and SVG sources could not use a font, so Korean became `#`; `<pre>`/monospace text fell
  back to Courier. Monospace text now falls back to the default font for Korean glyphs, a missing font file throws,
  and Korean text without any font throws instead of printing `#`.
- `S2PdfUtil` images: WebP was documented but unsupported; it works when `com.twelvemonkeys.imageio:imageio-webp` is on
  the classpath, and the error says so otherwise. Images are decoded once (JPEG is embedded as is), and images over
  100 megapixels are refused before decoding. HTML is rendered straight to a file instead of a byte array; HTML/text/SVG
  streams, stream images and URL downloads are limited (URL: `Content-Length` checked first).
- `S2PdfUtil.merge` names the failing source ("병합 소스 #2 (IMAGE) 처리 실패: ...").
- `S2PdfUtil.addPageNumbers` returned `null` on errors and when the page count was too large; it now throws. The page
  number is centered by its width.
- `S2PdfUtil` no longer calls `deleteOnExit` for every temporary file (the JVM kept every path until shutdown).
