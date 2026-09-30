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
- **jsch** moved to the maintained fork `com.github.mwiede:jsch` (same `com.jcraft.jsch` package; supports rsa-sha2 and
  current OpenSSH servers).

### Security

- `S2FileUtil.unzipFiles` rejects entries that point outside the target directory (Zip Slip) and limits the number of
  entries and the extracted size (default 10,000 entries, 1 GiB; an overload takes custom limits).
- SFTP `writeFile`/`readFile`/`deleteFile` reject a `saveName` that leaves `savePath` (`../`, absolute paths).
- `S2SearchVO.getOrderByList()` keeps only identifiers (`COLUMN`, `ALIAS.COLUMN`); `getOrderByList(allowedColumns)`
  also checks an allow list. The result is safe for MyBatis `${column}`.
- `S2PaginationTag` escapes `jsParam` inside `onclick` and rejects a `jsFunction` that is not a function name.
- `S2PdfUtil`: the HTML renderer loads only `data:` URIs, so URLs written in the HTML (`http:`, `file:`, `//host`) are
  never fetched (SSRF). `PdfSource.ofUrl` accepts only http/https.
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
- `S2PdfUtil` no longer calls `deleteOnExit` for every temporary file (the JVM kept every path until shutdown).
