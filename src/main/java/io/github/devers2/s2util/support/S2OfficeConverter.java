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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.imageio.ImageIO;

import io.github.devers2.s2util.file.S2ResourceInputStream;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * Office and Hangul documents through LibreOffice (the s2-soffice converter or an installed soffice): to PDF, to HTML
 * for web editors, or to another format.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * LibreOffice(s2-soffice 변환기 또는 설치된 soffice)로 오피스·한글 문서를 PDF, 웹에디터용 HTML, 다른 형식으로 바꾼다.
 * <ul>
 * <li>변환마다 별도 임시 폴더와 프로필을 써서 동시에 변환할 수 있고, 제한 시간({@link #setTimeout(Duration)})을 넘으면 프로세스를 끝낸다.</li>
 * <li>동시 변환 수는 서버 전체 한도({@link #setParallelism(int)})를 따르며, {@code S2PdfUtil} 병합과 같은 한도를 쓴다.</li>
 * <li>변환기가 없으면 "s2-office-converter 설치가 필요합니다" 예외를 낸다 ({@link #isAvailable()}로 미리 확인).</li>
 * </ul>
 *
 * <pre>{@code
 * InputStream pdf = S2OfficeConverter.toPdf(Path.of("보고서.hwp"));
 *
 * OfficeHtml doc = S2OfficeConverter.toHtml(Path.of("보고서.hwp"), HtmlOptions.create().maxImageWidth(1600));
 * for (var image : doc.images()) { storage.save(image.name(), image.bytes()); }
 * String html = doc.html(name -> "/files/editor/" + name);   // 에디터에 넣을 본문
 * }</pre>
 */
public final class S2OfficeConverter {

    private static final S2Logger logger = S2LogManager.getLogger(S2OfficeConverter.class);

    /** Extensions LibreOffice converts; hwp/hwpx need the H2Orestart extension | 변환할 수 있는 확장자 (hwp·hwpx 는 H2Orestart 필요) */
    public static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "hwp", "hwpx");

    private static final Set<String> HWP_EXTENSIONS = Set.of("hwp", "hwpx");

    static final String CONVERTER_REQUIRED = "오피스·한글 문서를 변환하려면 s2-office-converter 설치가 필요합니다.";

    /** Set by setCommand; never re-detected | setCommand 로 지정한 명령 (다시 찾지 않음) */
    private static volatile List<String> configuredCommand;
    /** Last detected command; "not found" is not remembered, so an install is picked up without a restart | 마지막으로 찾은 명령. "없음"은 기억하지 않음 */
    private static volatile List<String> detectedCommand;
    private static volatile Duration timeout = Duration.ofMinutes(3);
    private static volatile int parallelism = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    /** Shared with S2PdfUtil merges, so the limit holds for the whole server | S2PdfUtil 병합과 함께 써서 서버 전체 한도가 됨 */
    private static volatile Semaphore slots = new Semaphore(parallelism, true);

    private S2OfficeConverter() {
    }

    // ------------------------------------------------------------------ settings

    /**
     * 변환에 쓸 LibreOffice 호환 명령을 정한다. 지정하지 않으면 다음 순서로 찾는다.
     * <ol>
     * <li>환경 변수 {@code S2_SOFFICE}</li>
     * <li>PATH 의 {@code s2-soffice} (_devtools2 {@code scripts/linux/setup-projects/s2/s2-office-converter/setup-s2-office-converter.sh}가 설치하는
     * Podman 변환기)</li>
     * <li>PATH 의 {@code soffice}, {@code libreoffice}</li>
     * <li>고정 경로 {@code /usr/local/bin/s2-soffice}(PATH 에 없을 때), 운영체제별 기본 설치 경로 (Windows
     * {@code C:\Program Files\LibreOffice\program\soffice.exe}, macOS {@code /Applications/LibreOffice.app/Contents/MacOS/soffice},
     * Linux {@code /opt/libreoffice}*{@code /program/soffice})</li>
     * </ol>
     * 명령은 {@code --headless --convert-to <형식> --outdir <폴더> <파일>} 형식을 받아야 한다.
     *
     * @param command 명령과 앞부분 인자 (예: {@code "s2-soffice"}, {@code "/opt/libreoffice/program/soffice"})
     */
    public static void setCommand(String... command) {
        if (command == null || command.length == 0 || command[0] == null || command[0].isBlank()) {
            throw new IllegalArgumentException("명령이 비었습니다.");
        }
        configuredCommand = List.of(command);
    }

    /** 지정한 명령을 지우고 자동 탐색으로 되돌린다. */
    public static void resetCommand() {
        configuredCommand = null;
        detectedCommand = null;
    }

    /**
     * 문서 한 건의 변환 제한 시간 (기본 3분). 넘으면 변환 프로세스를 강제로 끝내고 예외를 던진다.
     *
     * @param timeout 제한 시간
     */
    public static void setTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("제한 시간은 0 보다 커야 합니다: " + timeout);
        }
        S2OfficeConverter.timeout = timeout;
    }

    /**
     * 서버 전체에서 동시에 실행할 변환 수 (기본: CPU 수와 4 중 작은 값). {@code S2PdfUtil} 병합의 변환과 함께 센다. 문서 변환은 건마다
     * LibreOffice(컨테이너, 수백 MB 메모리)를 띄우므로 서버 메모리에 맞춰 정한다.
     *
     * @param parallelism 1 이상
     */
    public static void setParallelism(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("동시 변환 수는 1 이상이어야 합니다: " + parallelism);
        }
        S2OfficeConverter.parallelism = parallelism;
        slots = new Semaphore(parallelism, true);
    }

    static int parallelism() {
        return parallelism;
    }

    static Semaphore slots() {
        return slots;
    }

    /**
     * 변환할 수 있는지 (명령이 있는지만 보며 실행하지는 않음). 화면에서 문서 첨부·가져오기 기능을 보여 줄지 정할 때 쓴다.
     *
     * @return 변환 명령이 있으면 true
     */
    public static boolean isAvailable() {
        var command = resolveCommand();
        return command != null && commandExists(command.get(0));
    }

    /**
     * 사용할 변환 명령. 없으면 비어 있다.
     *
     * @return 명령과 앞부분 인자
     */
    public static Optional<List<String>> command() {
        return Optional.ofNullable(resolveCommand());
    }

    /**
     * The configured command, or the detected one. Detection is repeated while nothing is found and when the
     * detected command disappears, so installing or removing the converter needs no restart | 지정한 명령 또는 찾은 명령.
     * 없거나 사라지면 다시 찾으므로 설치·제거에 재시작이 필요 없음
     */
    static List<String> resolveCommand() {
        var configured = configuredCommand;
        if (configured != null) {
            return configured;
        }
        var detected = detectedCommand;
        if (detected != null && commandExists(detected.get(0))) {
            return detected;
        }
        detected = detectCommand();
        if (detected != null && !detected.equals(detectedCommand)) {
            logger.info("문서 변환 명령: {}", detected);
        }
        detectedCommand = detected;
        return detected;
    }

    private static List<String> detectCommand() {
        var fromEnv = System.getenv("S2_SOFFICE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return List.of(fromEnv.trim());
        }
        var windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (var name : windows ? List.of("soffice.exe", "soffice.com") : List.of("s2-soffice", "soffice", "libreoffice")) {
            var found = findOnPath(name);
            if (found != null) {
                return List.of(found.toString());
            }
        }
        var candidates = new ArrayList<Path>();
        if (windows) {
            for (var base : new String[] { System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)") }) {
                if (base != null) {
                    candidates.add(Path.of(base, "LibreOffice", "program", "soffice.exe"));
                }
            }
        } else {
            // Where setup-s2-office-converter.sh (_devtools2) installs it, for PATHs without /usr/local/bin (cron, trimmed
            // services) | 설치 스크립트가 두는 위치. /usr/local/bin 이 PATH 에 없는 환경(cron, PATH 를 좁힌 서비스) 대비
            candidates.add(Path.of("/usr/local/bin/s2-soffice"));
            candidates.add(Path.of("/Applications/LibreOffice.app/Contents/MacOS/soffice"));
            candidates.add(Path.of("/usr/lib/libreoffice/program/soffice"));
            try (var dirs = Files.newDirectoryStream(Path.of("/opt"), "libreoffice*")) {
                for (var dir : dirs) {
                    candidates.add(dir.resolve("program").resolve("soffice"));
                }
            } catch (IOException | RuntimeException ignored) {
                // No /opt or not readable | /opt 가 없거나 읽을 수 없음
            }
        }
        for (var candidate : candidates) {
            if (Files.isExecutable(candidate)) {
                return List.of(candidate.toString());
            }
        }
        return null;
    }

    // ------------------------------------------------------------- process helpers

    /** A path must be executable; a bare name must be on the PATH | 경로는 실행 가능해야 하고, 이름만 있으면 PATH 에 있어야 함 */
    static boolean commandExists(String command) {
        if (command.contains("/") || command.contains("\\")) {
            return Files.isExecutable(Path.of(command));
        }
        return findOnPath(command) != null;
    }

    static Path findOnPath(String name) {
        var path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (var dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            try {
                var candidate = Path.of(dir, name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // Invalid PATH entry | 잘못된 PATH 항목
            }
        }
        return null;
    }

    static void destroyTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    static String readTail(Path log, int maxChars) {
        try {
            var text = Files.readString(log, StandardCharsets.UTF_8).strip();
            return text.length() > maxChars ? "..." + text.substring(text.length() - maxChars) : text;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------- core

    /** Where the input comes from | 입력 */
    interface Input {
        void writeTo(Path target) throws IOException;
    }

    static Input input(Path file) {
        return target -> Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    static Input input(byte[] bytes) {
        return target -> Files.write(target, bytes);
    }

    static Input input(InputStream stream) {
        return target -> {
            try (var out = Files.newOutputStream(target)) {
                stream.transferTo(out);
            }
        };
    }

    /**
     * Converts one document in a private temporary folder (its own LibreOffice profile, so conversions run
     * concurrently) and hands the output folder to {@code result} before the folder is removed. The caller holds a
     * conversion slot | 문서 하나를 전용 임시 폴더에서 변환하고(별도 프로필 → 동시 변환 가능), 폴더를 지우기 전에 결과 폴더를 넘긴다. 호출자가 변환 자리를
     * 잡고 있어야 한다
     */
    static <T> T convert(Input source, String name, String format, OutputReader<T> result) throws IOException {
        var command = resolveCommand();
        if (command == null || !commandExists(command.get(0))) {
            throw new IOException(CONVERTER_REQUIRED);
        }
        var extension = S2FileUtil.getExtension(name, true);
        if (!DOCUMENT_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("지원하지 않는 문서 형식입니다: " + name + " (지원: " + DOCUMENT_EXTENSIONS + ")");
        }
        var work = Files.createTempDirectory("s2_office_");
        try {
            // A plain ASCII name avoids encoding issues in the command line | 명령줄 인코딩 문제를 피하려고 ASCII 이름 사용
            var input = work.resolve("document." + extension);
            source.writeTo(input);
            var outDir = Files.createDirectories(work.resolve("out"));

            var args = new ArrayList<>(command);
            var wrapper = Path.of(command.get(0)).getFileName().toString().startsWith("s2-soffice");
            if (!wrapper) {
                // A separate LibreOffice profile per call lets conversions run at the same time | 호출마다 별도 프로필 → 동시 변환 가능
                args.add("-env:UserInstallation=" + work.resolve("profile").toUri());
            }
            args.addAll(List.of("--headless", "--norestore", "--nolockcheck", "--convert-to", format, "--outdir",
                    outDir.toString(), input.toString()));

            var log = work.resolve("soffice.log");
            Process process;
            try {
                process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            } catch (IOException e) {
                throw new IOException(CONVERTER_REQUIRED, e);
            }
            var limit = timeout;
            boolean finished;
            try {
                finished = process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                destroyTree(process);
                Thread.currentThread().interrupt();
                throw new IOException("문서 변환이 중단되었습니다.", e);
            }
            if (!finished) {
                destroyTree(process);
                throw new IOException("문서 변환 제한 시간(" + limit.toSeconds() + "초)을 넘었습니다: " + name);
            }
            var formatExtension = format.replaceFirst(":.*$", "");
            var output = outDir.resolve("document." + formatExtension);
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
                // Short message for users; the exit code and LibreOffice output go to the cause and the log
                // | 사용자에게는 짧은 메시지, 종료 코드와 LibreOffice 출력은 원인(cause)과 로그로
                var detail = new IOException("soffice 종료 코드 " + process.exitValue() + ", 명령 " + args.get(0) + ", 출력: "
                        + readTail(log, 2000));
                logger.warn("문서 변환 실패: {} → {} ({})", name, formatExtension, detail.getMessage());
                // Plain LibreOffice without the H2Orestart extension cannot read Hangul files | H2Orestart 없는 LibreOffice 는 한글 파일을 못 읽음
                var message = HWP_EXTENSIONS.contains(extension) && !wrapper ? CONVERTER_REQUIRED
                        : "문서를 " + formatExtension.toUpperCase(Locale.ROOT) + "로 변환하지 못했습니다: " + name;
                throw new IOException(message, detail);
            }
            return result.read(output, outDir);
        } finally {
            S2FileUtil.deleteQuietly(work);
        }
    }

    @FunctionalInterface
    interface OutputReader<T> {
        T read(Path mainFile, Path outDir) throws IOException;
    }

    /** Runs a conversion in one of the server-wide slots | 서버 전체 변환 자리 하나에서 실행 */
    private static <T> T inSlot(IoSupplier<T> work) throws IOException {
        var semaphore = slots;
        semaphore.acquireUninterruptibly();
        try {
            return work.get();
        } finally {
            semaphore.release();
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    /** Converts to a PDF at {@code target}; the caller holds a slot | target 에 PDF 로 변환 (호출자가 자리를 잡고 있음) */
    static void convertToPdf(Input source, String name, Path target) throws IOException {
        convert(source, name, "pdf", (pdf, outDir) -> {
            Files.move(pdf, target, StandardCopyOption.REPLACE_EXISTING);
            return null;
        });
    }

    // ------------------------------------------------------------------- PDF

    /**
     * 문서를 PDF 로 변환한다.
     *
     * @param document 문서 파일 ({@link #DOCUMENT_EXTENSIONS})
     * @return PDF 스트림 (닫으면 임시 파일 삭제)
     * @throws IOException 변환기가 없거나 변환에 실패했을 때
     */
    public static InputStream toPdf(Path document) throws IOException {
        Objects.requireNonNull(document, "document");
        return toPdf(input(document), String.valueOf(document.getFileName()));
    }

    /**
     * 문서 내용을 PDF 로 변환한다.
     *
     * @param document 문서 내용
     * @param fileName 원래 파일명 (형식을 확장자로 정함, 예: {@code 보고서.hwp})
     * @return PDF 스트림 (닫으면 임시 파일 삭제)
     * @throws IOException 변환기가 없거나 변환에 실패했을 때
     */
    public static InputStream toPdf(byte[] document, String fileName) throws IOException {
        return toPdf(input(Objects.requireNonNull(document, "document")), fileName);
    }

    /**
     * 문서 스트림을 PDF 로 변환한다 (스트림은 닫지 않음).
     *
     * @param document 문서 스트림
     * @param fileName 원래 파일명 (형식을 확장자로 정함)
     * @return PDF 스트림 (닫으면 임시 파일 삭제)
     * @throws IOException 변환기가 없거나 변환에 실패했을 때
     */
    public static InputStream toPdf(InputStream document, String fileName) throws IOException {
        return toPdf(input(Objects.requireNonNull(document, "document")), fileName);
    }

    private static InputStream toPdf(Input source, String fileName) throws IOException {
        var target = Files.createTempFile(S2Uuid.generateUuidV7() + "_office_", ".pdf");
        try {
            inSlot(() -> {
                convertToPdf(source, fileName, target);
                return null;
            });
            return new S2ResourceInputStream(new BufferedInputStream(Files.newInputStream(target)), target);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }
    }

    // ----------------------------------------------------------------- other

    /**
     * 문서를 다른 형식으로 바꿔 {@code outDir}에 원래 이름으로 저장한다 (예: hwp → docx, docx → odt, xlsx → csv).
     *
     * @param document 문서 파일
     * @param format   LibreOffice {@code --convert-to} 형식 (예: {@code "docx"}, {@code "odt"}, {@code "txt:Text (encoded):UTF8"})
     * @param outDir   저장 폴더 (없으면 만듦)
     * @return 저장한 파일
     * @throws IOException 변환기가 없거나 변환에 실패했을 때
     */
    public static Path convert(Path document, String format, Path outDir) throws IOException {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(outDir, "outDir");
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("변환할 형식이 비었습니다.");
        }
        var name = String.valueOf(document.getFileName());
        var extension = format.replaceFirst(":.*$", "");
        var target = Files.createDirectories(outDir).resolve(S2FileUtil.getBaseName(document) + "." + extension);
        return inSlot(() -> convert(input(document), name, format, (main, dir) -> {
            Files.move(main, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }));
    }

    // ------------------------------------------------------------------- HTML

    /** {@link #toHtml(Path, HtmlOptions)} 옵션. */
    public static final class HtmlOptions {
        private int maxImageWidth;

        private HtmlOptions() {
        }

        /**
         * @return 기본 옵션 (이미지 원래 크기)
         */
        public static HtmlOptions create() {
            return new HtmlOptions();
        }

        /**
         * 이미지 가로를 이 픽셀 이하로 줄인다 (비율 유지, 기본 0 = 원래 크기). 에디터에는 1600 정도가 알맞다.
         *
         * @param maxImageWidth 최대 가로 (픽셀, 0 이면 줄이지 않음)
         * @return 이 옵션
         */
        public HtmlOptions maxImageWidth(int maxImageWidth) {
            if (maxImageWidth < 0) {
                throw new IllegalArgumentException("최대 가로는 0 이상이어야 합니다: " + maxImageWidth);
            }
            this.maxImageWidth = maxImageWidth;
            return this;
        }
    }

    /**
     * An image of a converted document | 변환한 문서의 이미지
     *
     * @param name     HTML 에서 가리키는 이름 (예: {@code image-1.png})
     * @param mimeType MIME 타입
     * @param bytes    내용
     */
    public record OfficeImage(String name, String mimeType, byte[] bytes) {
    }

    /**
     * A document as HTML for a web editor: the body and its images | 웹에디터용 HTML 문서 (본문과 이미지)
     */
    public static final class OfficeHtml {
        private final String html;
        private final List<OfficeImage> images;

        OfficeHtml(String html, List<OfficeImage> images) {
            this.html = html;
            this.images = Collections.unmodifiableList(images);
        }

        /**
         * 본문 HTML ({@code <body>} 안쪽). 이미지는 {@link OfficeImage#name()}으로 가리킨다.
         *
         * @return 본문 HTML
         */
        public String html() {
            return html;
        }

        /**
         * 이미지 주소를 바꾼 본문 HTML. 이미지를 앱 저장소에 올린 뒤 그 주소로 바꿀 때 쓴다.
         *
         * @param imageUrl 이미지 이름 → 주소
         * @return 본문 HTML
         */
        public String html(Function<String, String> imageUrl) {
            Objects.requireNonNull(imageUrl, "imageUrl");
            var doc = org.jsoup.Jsoup.parseBodyFragment(html);
            for (var img : doc.select("img[src]")) {
                var src = img.attr("src");
                for (var image : images) {
                    if (image.name().equals(src)) {
                        img.attr("src", Objects.requireNonNull(imageUrl.apply(src), "image url"));
                        break;
                    }
                }
            }
            return doc.body().html();
        }

        /**
         * 이미지를 data URI 로 넣은 본문 HTML (저장소 없이 간단히 쓸 때. 이미지가 많으면 커짐).
         *
         * @return 본문 HTML
         */
        public String htmlWithEmbeddedImages() {
            Map<String, String> uris = new LinkedHashMap<>();
            for (var image : images) {
                uris.put(image.name(), "data:" + image.mimeType() + ";base64," + Base64.getEncoder().encodeToString(image.bytes()));
            }
            return html(uris::get);
        }

        /**
         * @return 이미지 목록 (HTML 에 나오는 순서)
         */
        public List<OfficeImage> images() {
            return images;
        }
    }

    /**
     * 문서를 웹에디터에 넣을 HTML 로 변환한다 (원래 크기 이미지).
     *
     * @param document 문서 파일
     * @return 본문 HTML 과 이미지
     * @throws IOException 변환기가 없거나 변환에 실패했을 때
     * @see #toHtml(Path, HtmlOptions)
     */
    public static OfficeHtml toHtml(Path document) throws IOException {
        return toHtml(document, HtmlOptions.create());
    }

    /**
     * 문서를 웹에디터에 넣을 HTML 로 변환한다.
     * <ul>
     * <li>본문({@code <body>} 안쪽)만 돌려준다. 스크립트, 프레임, 이벤트 속성({@code onclick} 등), {@code javascript:}·{@code data:} 링크를 지운다.
     * 표, 굵게, 색, 정렬 같은 서식은 남기고 {@code <font>} 는 {@code <span style>} 로 바꾼다.</li>
     * <li>이미지는 따로 모아 {@code image-1.png} 처럼 이름을 붙인다. 웹에서 못 쓰는 BMP 는 PNG 로 바꾸고, {@code maxImageWidth} 를 주면 줄인다.
     * 브라우저가 못 그리는 형식(WMF 등)은 빼고 경고 로그를 남긴다.</li>
     * <li>한글(hwp)은 XHTML 필터에서 LibreOffice 가 비정상 종료하므로 일반 HTML 필터로 변환한다.</li>
     * <li>다단, 글상자, 머리말·꼬리말, 각주 위치 같은 배치는 HTML 이 표현할 수 없어 단순해진다. 엑셀은 표, 파워포인트는 슬라이드 내용 위주로 나온다.</li>
     * </ul>
     *
     * @param document 문서 파일
     * @param options  옵션
     * @return 본문 HTML 과 이미지
     * @throws IOException 변환기가 없거나 변환에 실패했을 때
     */
    public static OfficeHtml toHtml(Path document, HtmlOptions options) throws IOException {
        Objects.requireNonNull(document, "document");
        return toHtml(input(document), String.valueOf(document.getFileName()), options);
    }

    /**
     * 문서 내용을 웹에디터에 넣을 HTML 로 변환한다 ({@link #toHtml(Path, HtmlOptions)} 참고).
     *
     * @param document 문서 내용
     * @param fileName 원래 파일명 (형식을 확장자로 정함)
     * @param options  옵션
     * @return 본문 HTML 과 이미지
     * @throws IOException 변환기가 없거나 변환에 실패했을 때
     */
    public static OfficeHtml toHtml(byte[] document, String fileName, HtmlOptions options) throws IOException {
        return toHtml(input(Objects.requireNonNull(document, "document")), fileName, options);
    }

    /**
     * 문서 스트림을 웹에디터에 넣을 HTML 로 변환한다 (스트림은 닫지 않음, {@link #toHtml(Path, HtmlOptions)} 참고).
     *
     * @param document 문서 스트림
     * @param fileName 원래 파일명 (형식을 확장자로 정함)
     * @param options  옵션
     * @return 본문 HTML 과 이미지
     * @throws IOException 변환기가 없거나 변환에 실패했을 때
     */
    public static OfficeHtml toHtml(InputStream document, String fileName, HtmlOptions options) throws IOException {
        return toHtml(input(Objects.requireNonNull(document, "document")), fileName, options);
    }

    private static OfficeHtml toHtml(Input source, String fileName, HtmlOptions options) throws IOException {
        var settings = options != null ? options : HtmlOptions.create();
        return inSlot(() -> convert(source, fileName, "html", (html, outDir) -> editorHtml(html, outDir, settings)));
    }

    /** Body HTML cleaned for editors, with images collected | 에디터용으로 정리한 본문과 모은 이미지 */
    private static OfficeHtml editorHtml(Path htmlFile, Path outDir, HtmlOptions options) throws IOException {
        var doc = org.jsoup.Jsoup.parse(htmlFile.toFile(), StandardCharsets.UTF_8.name());
        var body = doc.body();
        body.select("script, noscript, iframe, frame, frameset, object, embed, applet, form, input, button, select, "
                + "textarea, link, meta, style, base").remove();
        for (var element : body.getAllElements()) {
            var remove = new ArrayList<String>();
            for (var attribute : element.attributes()) {
                var key = attribute.getKey().toLowerCase(Locale.ROOT);
                if (key.startsWith("on") || key.equals("name") && element.tagName().equals("img")) {
                    remove.add(attribute.getKey());
                } else if ((key.equals("href") || key.equals("src") || key.equals("action")) && !isSafeUrl(attribute.getValue())) {
                    remove.add(attribute.getKey());
                }
            }
            remove.forEach(element::removeAttr);
        }
        // Pages do not exist in an editor: print-only declarations go | 에디터에는 쪽이 없으므로 인쇄 전용 스타일 제거
        for (var styled : body.select("[style]")) {
            var style = styled.attr("style").replaceAll("(?i)(page-break-[a-z]+|break-(before|after|inside)|widows|orphans)\\s*:[^;]*;?\\s*", "").trim();
            if (style.isEmpty()) {
                styled.removeAttr("style");
            } else {
                styled.attr("style", style);
            }
        }
        // <center> is obsolete and some editors drop it | <center> 는 폐기된 태그라 일부 에디터가 버림
        for (var center : body.select("center")) {
            center.tagName("div").attr("style", "text-align: center");
        }
        for (var font : body.select("font")) {
            var style = new StringBuilder();
            if (font.hasAttr("color")) {
                style.append("color: ").append(font.attr("color")).append("; ");
            }
            if (font.hasAttr("face")) {
                style.append("font-family: ").append(font.attr("face")).append("; ");
            }
            if (font.hasAttr("size")) {
                style.append("font-size: ").append(fontSize(font.attr("size"))).append("; ");
            }
            if (font.hasAttr("style")) {
                style.append(font.attr("style"));
            }
            var span = new org.jsoup.nodes.Element("span");
            if (!style.isEmpty()) {
                span.attr("style", style.toString().trim());
            }
            span.insertChildren(0, new ArrayList<>(font.childNodes()));
            font.replaceWith(span);
        }

        var images = new ArrayList<OfficeImage>();
        var named = new LinkedHashMap<String, String>();
        var outRoot = outDir.toRealPath();
        for (var img : body.select("img")) {
            var src = img.attr("src");
            if (src.isEmpty() || src.startsWith("data:") || src.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")) {
                continue;
            }
            var name = named.get(src);
            if (name == null) {
                var image = officeImage(outRoot, src, images.size() + 1, options);
                if (image == null) {
                    img.remove();
                    continue;
                }
                images.add(image);
                name = image.name();
                named.put(src, name);
            }
            img.attr("src", name);
        }
        return new OfficeHtml(body.html(), images);
    }

    /** One image file of the conversion, web-ready, or null | 변환 결과의 이미지 하나를 웹에서 쓸 수 있게 (못 쓰면 null) */
    private static OfficeImage officeImage(Path outRoot, String src, int index, HtmlOptions options) {
        try {
            var file = outRoot.resolve(java.net.URLDecoder.decode(src, StandardCharsets.UTF_8)).normalize();
            if (!file.startsWith(outRoot) || !Files.isRegularFile(file)) {
                logger.warn("HTML 의 이미지를 찾을 수 없어 뺍니다: {}", src);
                return null;
            }
            var bytes = Files.readAllBytes(file);
            var extension = S2FileUtil.detectExtension(bytes);
            if (extension.equals("bmp") || extension.equals("tif")) {
                // Browsers do not show these: re-encode as PNG | 브라우저가 못 보여 주는 형식은 PNG 로
                var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
                if (decoded == null) {
                    return null;
                }
                var out = new ByteArrayOutputStream();
                ImageIO.write(decoded, "png", out);
                bytes = out.toByteArray();
                extension = "png";
            }
            if (!Set.of("png", "jpg", "gif", "webp", "svg").contains(extension)) {
                logger.warn("웹에서 쓸 수 없는 이미지 형식이라 뺍니다: {} ({})", src, extension.isEmpty() ? "알 수 없음" : extension);
                return null;
            }
            if (options.maxImageWidth > 0 && !extension.equals("svg") && !extension.equals("gif")) {
                bytes = S2ImageUtil.resizeToFit(bytes, options.maxImageWidth, Integer.MAX_VALUE);
            }
            return new OfficeImage("image-" + index + "." + extension, S2FileUtil.getMimeTypeByExtension(extension), bytes);
        } catch (IOException | RuntimeException e) {
            logger.warn("HTML 의 이미지를 읽지 못해 뺍니다: {} ({})", src, e.getMessage());
            return null;
        }
    }

    /** {@code <font size>} 1..7 as CSS | font size 1~7 을 CSS 로 */
    private static String fontSize(String size) {
        return switch (size.trim()) {
        case "1" -> "x-small";
        case "2" -> "small";
        case "3" -> "medium";
        case "4" -> "large";
        case "5" -> "x-large";
        case "6" -> "xx-large";
        case "7" -> "48px";
        default -> "medium";
        };
    }

    /** http, https, mailto, relative paths and anchors | http, https, mailto, 상대 경로, 앵커 */
    private static boolean isSafeUrl(String url) {
        var value = url.trim().toLowerCase(Locale.ROOT);
        var colon = value.indexOf(':');
        var slash = value.indexOf('/');
        if (colon < 0 || (slash >= 0 && slash < colon) || value.startsWith("#") || value.startsWith("?")) {
            return true;
        }
        var scheme = value.substring(0, colon);
        return scheme.equals("http") || scheme.equals("https") || scheme.equals("mailto");
    }

}
