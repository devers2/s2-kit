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
- `S2PdfUtil` no longer calls `deleteOnExit` for every temporary file (the JVM kept every path until shutdown).
