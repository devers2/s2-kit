# Changelog

**English** | [한국어](./CHANGELOG.ko.md)

All notable changes to `s2-kit` (formerly `s2-support`) are recorded here. The `s2-support` 1.x history continues in this file.

## [Unreleased]

### Dependencies

- Apache PDFBox 3.0.4 → 3.0.8 (security and bug-fix patches), AspectJ Weaver 1.9.25.1 → 1.9.26, lz4-java 1.12.0 → 1.12.1,
  Caffeine 3.2.3 → 3.3.1, jackson-databind 3.0.3 → 3.2.4. Applications using `S2PdfUtil` should use PDFBox 3.0.8 (any 3.0.x
  still works).

## [1.2.0]

### ⚠️ Compatibility

- ⚠️ **`S2PdfUtil` is back in `s2-kit` as a PDF post-processing utility.** It stamps text, page numbers, images, QR codes
  and watermarks (`stamp`, `StampOptions`, `PageSelector`), adds page numbers (`addPageNumbers`), merges PDFs and images
  (`mergePdfs`, `mergePdfFiles`, `mergePdfPaths`, `mergeImagesToPdf`, `mergeImageFiles`, `mergeImagePaths`), adds a
  per-document bookmark (`addBookmark`), counts pages and renders pages to images. It uses Apache PDFBox only and runs
  no external program, so it works on PDFs made by any tool.
- ⚠️ **Apache PDFBox is an optional dependency.** Add `org.apache.pdfbox:pdfbox:3.0.4` to use `S2PdfUtil`; a missing
  dependency is reported on first use.
- ⚠️ **Document conversion is no longer part of this project.** Converting HTML, text, SVG, Markdown, Office and Hangul
  documents and URLs to PDF and merging them (`PdfSource`, `MergeOptions`, `merge`, `prepare`, the conversion cache),
  `S2OfficeConverter`, `S2MarkdownUtil`, the web PDF viewer and the converter setup scripts were removed. The
  `s2-kit-pdf` module is discontinued; its last published version (1.1.0) stays on Maven Central but receives no updates.

### Added

- Multi-line stamp text alignment: `Text`/`PageNumber` `.multiLineTextAlign(TextAlign.LEFT | CENTER | RIGHT)` aligns each
  line against the longest one, also on rotated text.

## [1.1.0]

### 📑 Module Extraction

- PDF and document conversion (`S2PdfUtil`, `S2OfficeConverter`, `S2MarkdownUtil`), the web PDF viewer and the converter
  setup scripts moved out of the root `s2-kit` JAR into a separate module, `s2-kit-pdf`. The root JAR no longer depends
  on PDFBox, OpenHTMLtoPDF or CommonMark. (Discontinued later; see 1.2.0.)

## [1.0.0] - 2026-10-02

The successor of `s2-support` 1.1.5 and the first release under the name `s2-kit`. Changes are against 1.1.5 and break
compatibility, so check the ⚠️ items before upgrading. See "Moving from s2-support" in the README.

### ⚠️ Compatibility

- ⚠️ **Renamed to `s2-kit`.**
  - Coordinates: `io.github.devers2.internal:s2-support` → `io.github.devers2.internal:s2-kit` (same group).
  - Packages: `io.github.devers2.s2util.{support,file,pagination,spring,model}` → `io.github.devers2.s2kit.{...}`.
    The `s2-core` packages (`io.github.devers2.s2util.core`, `.json`, `.log`, ...) are unchanged.
  - Front-end resource paths: `/s2-util/js/`, `/s2-util/css/` → `/s2-kit/js/`, `/s2-kit/css/`. File names (`s2.util.js`, ...),
    the JS API (`S2Util`, ...) and the JSP tag URIs are unchanged. `s2.validator.js` belongs to `s2-validator` and stays under
    `/s2-util/js/`.
  - Repository: `https://github.com/devers2/s2-kit` (the old address redirects).
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
- Document conversion and multi-source merge: `S2PdfUtil.merge`/`prepare` (HTML, text, SVG, Markdown, images, Office
  and Hangul documents through LibreOffice, and URLs, with bookmarks, page numbers, a conversion cache and concurrent
  conversion), `S2OfficeConverter` and `S2MarkdownUtil`. They moved to `s2-kit-pdf` in 1.1.0 and are no longer part of
  this project (see 1.2.0).
- `S2ImageUtil.resizeToFit(bytes, maxWidth, maxHeight)` (keeps the ratio, subsampled reading, EXIF orientation, the
  same array when nothing changes), `scaleToFit`, `exifOrientation`, `applyOrientation`, `JPEG_QUALITY`.
- `S2FileUtil` file types: `detectExtension(Path | byte[])` (from the content, for uploads stored without an
  extension), `getExtensionByHint(name, extension or MIME type)`, `getExtensionByMimeType(String)` and
  `getMimeTypeByExtension(extension or name)`. The MIME table gains png, docx, pptx, md, txt and the Hangul (hwp, hwpx)
  variants, and the existing `getExtensionByMimeType(Path)` and `getContentType(Path)` fall back to the content when
  the OS cannot tell (files without an extension).
- Watermarks: `Watermark.of(image)...` (then through `MergeOptions.watermark`, now `StampOptions.watermark`) stamps an image on every page. Size both sides (`size`),
  the width only (`width`, height in ratio) or the height only (`height`, width in ratio); position at the center or
  one of eight directions (`Position`); `offset(x, y)` moves inward from an anchored edge, or right/down on a centered
  axis; `opacity`. Lengths in points. The image is embedded once and shared by all pages, PNG transparency is kept,
  and page numbers are drawn above it. On rotated pages (`/Rotate` 90, 180, 270) it is placed on the page as shown,
  upright.
- `S2PdfUtil.setDefaultFont(Path)` / `setDefaultFont(Class, String)` / `resetDefaultFont()`; without a configured font an
  installed Korean TrueType font is used (`SYSTEM_FONT_CANDIDATES`: Malgun Gothic, NanumGothic, Noto Sans KR, ...).

### Security

- `S2FileUtil.unzipFiles` rejects entries that point outside the target directory (Zip Slip) and limits the number of
  entries and the extracted size (default 10,000 entries, 1 GiB; an overload takes custom limits).
- SFTP `writeFile`/`readFile`/`deleteFile` reject a `saveName` that leaves `savePath` (`../`, absolute paths).
- `S2SearchVO.getOrderByList()` keeps only identifiers (`COLUMN`, `ALIAS.COLUMN`); `getOrderByList(allowedColumns)`
  also checks an allow list. The result is safe for MyBatis `${column}`.
- `S2PaginationTag` escapes `jsParam` inside `onclick` and rejects a `jsFunction` that is not a function name.
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
- Photo orientation: portrait phone photos (EXIF orientation) came out lying on their side in PDFs and in
  `S2ImageUtil` resize results. They are now upright; a JPEG kept at its size is not re-encoded but turned when drawn.
  `S2ImageUtil` writes JPEG at quality 0.9 instead of ImageIO's default 0.75.
- PDFs made with a font that maps control characters (U+0000 to U+001F) to its space glyph, such as NanumGothic, copied
  and searched spaces as U+0000. Control-character mappings are now removed when a font is loaded (it looks the same).
- Licenses: `licenses/NOTICE` pointed UUID Creator at jsoup's license file (`LICENSE-MIT`, Jonathan Hedley). Its own
  text (`LICENSE-UUID-CREATOR-MIT`, Fabio Lima) is now included, along with commonmark-java (`LICENSE-COMMONMARK-BSD`)
  and autolink-java (`LICENSE-AUTOLINK-MIT`). A lightweight build without `S2PdfUtil` failed to compile because its
  helpers (`S2HtmlResources` and others, which use jsoup) stayed; they are now part of the same feature.
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
- `S2PdfUtil` images: WebP was documented but unsupported; it works when `com.twelvemonkeys.imageio:imageio-webp` is on
  the classpath, and the error says so otherwise. Images are decoded once (JPEG is embedded as is), and images over
  100 megapixels are refused before decoding. HTML is rendered straight to a file instead of a byte array; HTML/text/SVG
  streams, stream images and URL downloads are limited (URL: `Content-Length` checked first).
- `S2PdfUtil.addPageNumbers` returned `null` on errors and when the page count was too large; it now throws. The page
  number is centered by its width.
- `S2PdfUtil` no longer calls `deleteOnExit` for every temporary file (the JVM kept every path until shutdown).
