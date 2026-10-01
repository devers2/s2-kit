/**
 * S2 Support Library
 *
 * Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)
 * Contact: eseungsu.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For more information, please see the LICENSE file in the root directory.
 */
package io.github.devers2.s2util.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Tells which kind of merge source a file is, from a hint (file name, extension or MIME type), then its own name,
 * then its content.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 파일이 어떤 병합 소스인지 판별한다. 힌트(파일명, 확장자, MIME 타입) → 파일 자체의 이름 → 내용 순으로 본다.
 */
final class S2FileKind {

    enum Kind {
        PDF, IMAGE, SVG, HTML, TEXT, MARKDOWN, DOCUMENT
    }

    /** The kind and, for documents, the extension the converter needs | 종류와 (문서이면) 변환기에 줄 확장자 */
    record Detected(Kind kind, String extension) {
    }

    private static final Map<String, Kind> EXTENSIONS = Map.ofEntries(
            Map.entry("pdf", Kind.PDF),
            Map.entry("jpg", Kind.IMAGE), Map.entry("jpeg", Kind.IMAGE), Map.entry("png", Kind.IMAGE),
            Map.entry("gif", Kind.IMAGE), Map.entry("bmp", Kind.IMAGE), Map.entry("webp", Kind.IMAGE),
            Map.entry("tif", Kind.IMAGE), Map.entry("tiff", Kind.IMAGE),
            Map.entry("svg", Kind.SVG),
            Map.entry("html", Kind.HTML), Map.entry("htm", Kind.HTML), Map.entry("xhtml", Kind.HTML),
            Map.entry("txt", Kind.TEXT), Map.entry("text", Kind.TEXT), Map.entry("log", Kind.TEXT),
            Map.entry("md", Kind.MARKDOWN), Map.entry("markdown", Kind.MARKDOWN));

    /** MIME types, with the common non-standard ones for Hangul | MIME 타입 (한글은 흔히 쓰이는 비표준 이름 포함) */
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry("application/pdf", "pdf"),
            Map.entry("image/jpeg", "jpg"), Map.entry("image/jpg", "jpg"), Map.entry("image/pjpeg", "jpg"),
            Map.entry("image/png", "png"), Map.entry("image/gif", "gif"), Map.entry("image/bmp", "bmp"),
            Map.entry("image/x-ms-bmp", "bmp"), Map.entry("image/webp", "webp"), Map.entry("image/tiff", "tif"),
            Map.entry("image/svg+xml", "svg"),
            Map.entry("text/html", "html"), Map.entry("application/xhtml+xml", "html"),
            Map.entry("text/plain", "txt"),
            Map.entry("text/markdown", "md"), Map.entry("text/x-markdown", "md"),
            Map.entry("text/csv", "csv"), Map.entry("application/csv", "csv"),
            Map.entry("application/rtf", "rtf"), Map.entry("text/rtf", "rtf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("application/vnd.ms-powerpoint", "ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
            Map.entry("application/vnd.oasis.opendocument.text", "odt"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", "ods"),
            Map.entry("application/vnd.oasis.opendocument.presentation", "odp"),
            Map.entry("application/x-hwp", "hwp"), Map.entry("application/haansofthwp", "hwp"),
            Map.entry("application/vnd.hancom.hwp", "hwp"), Map.entry("application/hwp", "hwp"),
            Map.entry("application/x-hwpx", "hwpx"), Map.entry("application/haansofthwpx", "hwpx"),
            Map.entry("application/vnd.hancom.hwpx", "hwpx"), Map.entry("application/hwp+zip", "hwpx"));

    private static final Set<String> GENERIC_MIME_TYPES = Set.of("application/octet-stream", "binary/octet-stream",
            "application/zip", "application/x-zip-compressed", "application/x-ole-storage", "application/x-cfb");

    private S2FileKind() {
    }

    /** Supported names for messages | 메시지용 지원 형식 */
    static String supported() {
        return "pdf, jpg, png, gif, bmp, tif, webp, svg, html, txt, md, " + String.join(", ", S2PdfUtil.DOCUMENT_EXTENSIONS);
    }

    /**
     * From a file name, an extension or a MIME type; null when it says nothing usable (empty, generic or unknown)
     * | 파일명·확장자·MIME 타입으로. 쓸 만한 정보가 없으면(빈 값, 일반 타입, 모르는 형식) null
     */
    static Detected fromHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return null;
        }
        var value = hint.trim().toLowerCase(Locale.ROOT);
        if (value.matches("^[a-z0-9.+-]+/[a-z0-9.+-]+(\\s*;.*)?$")) {
            var mime = value.replaceFirst("\\s*;.*$", "");
            if (GENERIC_MIME_TYPES.contains(mime)) {
                return null;
            }
            var extension = MIME_TYPES.get(mime);
            if (extension == null && mime.startsWith("image/")) {
                return new Detected(Kind.IMAGE, mime.substring(6));
            }
            return extension == null ? null : fromExtension(extension);
        }
        var dot = value.lastIndexOf('.');
        var slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return fromExtension(dot > slash ? value.substring(dot + 1) : (slash < 0 ? value : ""));
    }

    private static Detected fromExtension(String extension) {
        var kind = EXTENSIONS.get(extension);
        if (kind != null) {
            return new Detected(kind, extension);
        }
        return S2PdfUtil.DOCUMENT_EXTENSIONS.contains(extension) ? new Detected(Kind.DOCUMENT, extension) : null;
    }

    /** From the content of a file; null when it is not recognized | 파일 내용으로. 알 수 없으면 null */
    static Detected fromContent(Path file) throws IOException {
        byte[] head;
        try (var in = Files.newInputStream(file)) {
            head = in.readNBytes(4096);
        }
        var simple = fromHead(head);
        if (simple != null) {
            return simple;
        }
        if (isZip(head)) {
            try (var zip = new ZipFile(file.toFile())) {
                var names = new StringBuilder();
                String mimetype = null;
                for (var entries = zip.entries(); entries.hasMoreElements();) {
                    var entry = entries.nextElement();
                    names.append(entry.getName()).append('\n');
                    if (entry.getName().equals("mimetype")) {
                        try (var in = zip.getInputStream(entry)) {
                            mimetype = new String(in.readNBytes(200), StandardCharsets.US_ASCII).trim();
                        }
                    }
                }
                return fromZip(names.toString(), mimetype);
            } catch (IOException e) {
                return null;
            }
        }
        if (isOle(head)) {
            try (var in = Files.newInputStream(file)) {
                return fromOle(in);
            }
        }
        return null;
    }

    /** From content in memory; null when it is not recognized | 메모리의 내용으로. 알 수 없으면 null */
    static Detected fromContent(byte[] bytes) throws IOException {
        var head = java.util.Arrays.copyOf(bytes, Math.min(bytes.length, 4096));
        var simple = fromHead(head);
        if (simple != null) {
            return simple;
        }
        if (isZip(head)) {
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                var names = new StringBuilder();
                String mimetype = null;
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    names.append(entry.getName()).append('\n');
                    if (entry.getName().equals("mimetype")) {
                        mimetype = new String(zip.readNBytes(200), StandardCharsets.US_ASCII).trim();
                    }
                }
                return fromZip(names.toString(), mimetype);
            } catch (IOException e) {
                return null;
            }
        }
        return isOle(head) ? fromOle(new ByteArrayInputStream(bytes)) : null;
    }

    /** Formats told by their first bytes | 앞부분 바이트로 아는 형식 */
    private static Detected fromHead(byte[] b) {
        if (b.length >= 5 && indexOf(b, "%PDF-".getBytes(StandardCharsets.US_ASCII)) >= 0
                && indexOf(b, "%PDF-".getBytes(StandardCharsets.US_ASCII)) < 1024) {
            return new Detected(Kind.PDF, "pdf");
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return new Detected(Kind.IMAGE, "png");
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new Detected(Kind.IMAGE, "jpg");
        }
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return new Detected(Kind.IMAGE, "gif");
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
                && b[10] == 'B' && b[11] == 'P') {
            return new Detected(Kind.IMAGE, "webp");
        }
        if (b.length >= 4 && ((b[0] == 'I' && b[1] == 'I' && b[2] == 42 && b[3] == 0)
                || (b[0] == 'M' && b[1] == 'M' && b[2] == 0 && b[3] == 42))) {
            return new Detected(Kind.IMAGE, "tif");
        }
        if (b.length >= 14 && b[0] == 'B' && b[1] == 'M') {
            return new Detected(Kind.IMAGE, "bmp");
        }
        var text = new String(b, 0, Math.min(b.length, 1024), StandardCharsets.UTF_8).replace("﻿", "")
                .stripLeading().toLowerCase(Locale.ROOT);
        if (text.startsWith("{\\rtf")) {
            return new Detected(Kind.DOCUMENT, "rtf");
        }
        if (text.startsWith("<svg") || (text.startsWith("<?xml") && text.contains("<svg"))) {
            return new Detected(Kind.SVG, "svg");
        }
        if (text.startsWith("<!doctype html") || text.startsWith("<html")) {
            return new Detected(Kind.HTML, "html");
        }
        return null;
    }

    /** Office Open XML, OpenDocument and HWPX are zip files told apart by their entries | 압축 파일 안의 항목으로 구분 */
    private static Detected fromZip(String names, String mimetype) {
        if (mimetype != null) {
            switch (mimetype) {
            case "application/hwp+zip" -> {
                return new Detected(Kind.DOCUMENT, "hwpx");
            }
            case "application/vnd.oasis.opendocument.text" -> {
                return new Detected(Kind.DOCUMENT, "odt");
            }
            case "application/vnd.oasis.opendocument.spreadsheet" -> {
                return new Detected(Kind.DOCUMENT, "ods");
            }
            case "application/vnd.oasis.opendocument.presentation" -> {
                return new Detected(Kind.DOCUMENT, "odp");
            }
            default -> {
                // Other zip files: look at the entries | 그 외: 항목을 봄
            }
            }
        }
        if (names.contains("word/")) {
            return new Detected(Kind.DOCUMENT, "docx");
        }
        if (names.contains("xl/")) {
            return new Detected(Kind.DOCUMENT, "xlsx");
        }
        if (names.contains("ppt/")) {
            return new Detected(Kind.DOCUMENT, "pptx");
        }
        if (names.contains("Contents/section")) {
            return new Detected(Kind.DOCUMENT, "hwpx");
        }
        return null;
    }

    /**
     * Word, Excel, PowerPoint 97-2003 and HWP 5 are OLE compound files told apart by their stream names (UTF-16)
     * | OLE 복합 문서는 내부 스트림 이름(UTF-16)으로 구분
     */
    private static Detected fromOle(InputStream in) throws IOException {
        var hwp = utf16("HwpSummaryInformation");
        var word = utf16("WordDocument");
        var workbook = utf16("Workbook");
        var book = utf16("Book");
        var powerPoint = utf16("PowerPoint Document");
        var buffer = new byte[64 * 1024];
        var carry = new byte[0];
        boolean sawWorkbook = false, sawBook = false, sawWord = false, sawPowerPoint = false;
        int read;
        while ((read = in.readNBytes(buffer, 0, buffer.length)) > 0) {
            // Keep the end of the previous chunk so names split across chunks are found | 경계에 걸친 이름도 찾도록 앞 조각 끝을 붙임
            var chunk = new byte[carry.length + read];
            System.arraycopy(carry, 0, chunk, 0, carry.length);
            System.arraycopy(buffer, 0, chunk, carry.length, read);
            if (indexOf(chunk, hwp) >= 0) {
                return new Detected(Kind.DOCUMENT, "hwp");
            }
            sawWord |= indexOf(chunk, word) >= 0;
            sawWorkbook |= indexOf(chunk, workbook) >= 0;
            sawBook |= indexOf(chunk, book) >= 0;
            sawPowerPoint |= indexOf(chunk, powerPoint) >= 0;
            carry = java.util.Arrays.copyOfRange(chunk, Math.max(0, chunk.length - 64), chunk.length);
        }
        if (sawWord) {
            return new Detected(Kind.DOCUMENT, "doc");
        }
        if (sawPowerPoint) {
            return new Detected(Kind.DOCUMENT, "ppt");
        }
        if (sawWorkbook || sawBook) {
            return new Detected(Kind.DOCUMENT, "xls");
        }
        return null;
    }

    private static boolean isZip(byte[] b) {
        return b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4;
    }

    private static boolean isOle(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF && (b[2] & 0xFF) == 0x11
                && (b[3] & 0xFF) == 0xE0 && (b[4] & 0xFF) == 0xA1 && (b[5] & 0xFF) == 0xB1 && (b[6] & 0xFF) == 0x1A
                && (b[7] & 0xFF) == 0xE1;
    }

    private static byte[] utf16(String name) {
        return name.getBytes(StandardCharsets.UTF_16LE);
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer: for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
