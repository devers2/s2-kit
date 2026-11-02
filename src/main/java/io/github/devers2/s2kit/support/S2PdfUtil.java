/**
 * S2 Kit Library
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
package io.github.devers2.s2kit.support;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import io.nayuki.qrcodegen.QrCode;
import io.github.devers2.s2util.core.S2Util;
import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2kit.file.S2ResourceInputStream;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * <h1>S2PdfUtil (PDF 후처리: 스탬프 · 쪽 번호 · 워터마크 · 병합)</h1>
 * <p>
 * 이미 만들어진 PDF 에 텍스트·쪽 번호·이미지·QR 스탬프와 워터마크를 넣고, PDF 와 이미지를 하나로 합치며, 쪽 수 조회와 쪽 이미지 변환을
 * 제공한다. Apache PDFBox 3.x 만으로 동작하며 외부 프로그램(LibreOffice, 브라우저)을 쓰지 않는다. 그래서 어떤 도구로 만든 PDF 에도
 * (다른 변환·병합 서버의 결과물 포함) 그대로 쓸 수 있다.
 * </p>
 *
 * <h2>의존성</h2>
 * <p>
 * PDFBox 는 s2-kit 에 포함되지 않으므로 사용하는 쪽에서 추가한다. 없으면 처음 사용할 때 안내 예외가 난다.
 * </p>
 * <pre>{@code
 * implementation("org.apache.pdfbox:pdfbox:3.0.4")
 * // WebP 이미지를 넣으려면 (선택)
 * implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.2")
 * }</pre>
 *
 * <h2>주요 기능</h2>
 * <ul>
 *   <li><b>스탬프:</b> {@link #stamp(Path, StampOptions)} — 텍스트(고정·쪽별 동적), 쪽 번호, 이미지, QR 코드, 워터마크를 9 방향 위치·회전·
 *       투명도·격자 반복·{@link PageSelector 대상 쪽} 지정으로 한 번에 넣는다</li>
 *   <li><b>쪽 번호:</b> {@link #addPageNumbers(Path, int, Integer, File)} — "1 / N" 형식, 앞쪽 표지 제외</li>
 *   <li><b>병합:</b> {@link #mergePdfs(InputStream...)}, {@link #mergeImagesToPdf(InputStream...)} — PDF·이미지를 순서대로 하나로
 *       (PDFBox 디스크 캐시 사용으로 큰 문서도 힙을 적게 씀)</li>
 *   <li><b>조회·변환:</b> {@link #getPageCount(Path)}, {@link #pdfToImage(Path, int, Path, Integer, String)}, {@link #convertImageToPdf(Path)}</li>
 *   <li><b>한글 폰트:</b> {@link #setDefaultFont(Path)} 로 지정하거나 서버에 설치된 한글 폰트를 자동으로 찾는다</li>
 * </ul>
 * <p>
 * 결과 스트림은 {@link S2ResourceInputStream} 이며, 닫으면 임시 파일이 지워진다.
 * </p>
 *
 * <h2>사용 예</h2>
 * <pre>{@code
 * var options = StampOptions.builder()
 *         .addText(Text.of("CONFIDENTIAL").position(Position.TOP_CENTER).pages(PageSelector.exceptFirst()))
 *         .addImage(ImageStamp.of(Path.of("seal.png")).topRight(25, 25).size(60, 60).pages(PageSelector.first()))
 *         .addQr(QrStamp.ofUrl("https://verify.example.com/doc/1").bottomRight(20, 20).size(75f).pages(PageSelector.last()))
 *         .pageNumber(PageNumber.of("%d / %d").position(Position.BOTTOM_CENTER))
 *         .build();
 * try (InputStream stamped = S2PdfUtil.stamp(Path.of("report.pdf"), options)) {
 *     stamped.transferTo(response.getOutputStream());
 * }
 * }</pre>
 *
 * @author devers2
 * @version 1.6
 * @since 1.0
 * @see S2ResourceInputStream
 * @see StampOptions
 */
public class S2PdfUtil {

    /** Default limit for an in-memory source read from a stream (100MB) | 스트림에서 읽는 소스 기본 한도 (100MB) */
    public static final long DEFAULT_MAX_SOURCE_BYTES = 100L * 1024 * 1024;

    /** Apache PDFBox, an optional dependency | 선택 의존성 Apache PDFBox */
    private static final String PDFBOX_CLASS = "org.apache.pdfbox.pdmodel.PDDocument";
    private static final String PDFBOX_COORDINATE = "org.apache.pdfbox:pdfbox:3.0.4";
    private static final boolean PDFBOX_PRESENT = isPdfBoxPresent();

    private static boolean isPdfBoxPresent() {
        try {
            Class.forName(PDFBOX_CLASS, false, S2PdfUtil.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Fails with a message naming the missing dependency before any PDFBox class is touched. The work itself lives in
     * {@code S2PdfEngine}, which the JVM cannot even load without PDFBox (it would throw a bare
     * {@code NoClassDefFoundError: org/apache/pdfbox/...})
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * PDFBox 클래스를 건드리기 전에, 빠진 의존성을 알려 주는 오류를 낸다. 실제 작업은 {@code S2PdfEngine}이 하며, 그 클래스는 PDFBox 가 없으면
     * JVM 이 불러오지도 못해 안내 없이 {@code NoClassDefFoundError: org/apache/pdfbox/...}가 난다.
     *
     * @throws NoClassDefFoundError PDFBox 가 클래스패스에 없을 때 (추가할 의존성 좌표 안내)
     */
    static void requirePdfBox() {
        if (!PDFBOX_PRESENT) {
            throw new NoClassDefFoundError("오류: S2PdfUtil 기능을 사용하기 위한 필수 의존성(" + PDFBOX_COORDINATE + ")이 누락되었습니다.\n"
                    + "Gradle 또는 Maven 설정에 해당 라이브러리를 implementation 으로 추가해야 합니다. "
                    + "(Gradle: implementation(\"" + PDFBOX_COORDINATE + "\"))");
        }
    }

    // ------------------------------------------------------------------------
    // Fonts | 폰트

    /**
     * A resolved font: where it came from (for logs and cache keys) and its TrueType bytes. Shared with modules that
     * render HTML to PDF, so they use the same Korean font as the stamps | 찾은 폰트 (출처 설명과 TTF 바이트). HTML 을 PDF 로 그리는
     * 다른 모듈도 스탬프와 같은 한글 폰트를 쓰도록 공개한다
     *
     * @param description 출처 (파일 경로 또는 클래스패스 경로)
     * @param bytes       TrueType 폰트 바이트
     */
    public record FontSource(String description, byte[] bytes) {
        /**
         * Fonts that map control characters to the space glyph (NanumGothic) would make spaces copy and search as U+0000
         * | 제어 문자를 공백 글리프에 연결한 폰트(나눔고딕)는 PDF 에서 복사·검색한 공백이 U+0000 이 되므로 정리
         */
        public FontSource {
            bytes = S2FontCmap.withoutControlCharacters(bytes);
        }
    }

    /** Korean TrueType fonts looked up when no font is configured (first readable wins) | 폰트 미지정 시 찾는 한글 TTF (먼저 찾은 것 사용) */
    public static final List<String> SYSTEM_FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/malgun.ttf",
            "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
            "/usr/share/fonts/nanum/NanumGothic.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansKR-Regular.ttf",
            "/usr/share/fonts/noto/NotoSansKR-Regular.ttf",
            "/usr/share/fonts/google-noto-sans-kr/NotoSansKR-Regular.ttf",
            "/Library/Fonts/NanumGothic.ttf",
            "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
            "/mnt/c/Windows/Fonts/malgun.ttf");

    /**
     * The built-in PDF fonts have no Korean glyphs, so without a font every such character would silently become '#'
     * | 기본 PDF 폰트에는 한글 글리프가 없어, 폰트 없이는 모든 글자가 조용히 '#'으로 바뀜
     *
     * @param content 그릴 내용 (한글·CJK 가 있는지 본다)
     * @param hasFont 한글 폰트를 찾았는지
     * @throws IOException 한글이 있는데 폰트가 없을 때 (설정 방법 안내)
     */
    public static void requireFontFor(String content, boolean hasFont) throws IOException {
        requirePdfBox();
        S2PdfEngine.requireFontFor(content, hasFont);
    }

    /**
     * 폰트를 지정하지 않은 스탬프·쪽 번호(그리고 이 폰트를 함께 쓰는 HTML 렌더링 모듈)에 쓸 기본 폰트를 정한다. 한글을 쓰려면 한글 글리프가 있는 TrueType(.ttf) 폰트를
     * 지정한다. 지정하지 않으면 서버에 설치된 한글 폰트({@link #SYSTEM_FONT_CANDIDATES})를 자동으로 찾는다.
     *
     * @param ttfFile TrueType 폰트 파일
     * @throws IOException 파일을 읽을 수 없을 때
     */
    public static void setDefaultFont(Path ttfFile) throws IOException {
        requirePdfBox();
        S2PdfEngine.setDefaultFont(ttfFile);
    }

    /**
     * 클래스패스의 TrueType 폰트를 기본 폰트로 정한다 ({@link #setDefaultFont(Path)} 참고).
     *
     * @param clazz        리소스를 읽을 기준 클래스
     * @param resourcePath 클래스패스 경로 (예: {@code /fonts/NanumGothic.ttf})
     * @throws IOException 리소스가 없거나 읽을 수 없을 때
     */
    public static void setDefaultFont(Class<?> clazz, String resourcePath) throws IOException {
        requirePdfBox();
        S2PdfEngine.setDefaultFont(clazz, resourcePath);
    }

    public static void resetDefaultFont() {
        requirePdfBox();
        S2PdfEngine.resetDefaultFont();
    }

    /**
     * Source font, then the default font, then an installed Korean font | 소스 폰트 → 기본 폰트 → 설치된 한글 폰트
     *
     * @param clazz    클래스패스 폰트를 읽을 기준 클래스 (null 이면 S2PdfUtil)
     * @param fontPath 클래스패스 폰트 경로 (비면 기본 폰트, 그다음 설치된 한글 폰트)
     * @return 찾은 폰트, 없으면 null
     * @throws IOException 지정한 폰트 리소스를 읽을 수 없을 때
     */
    public static FontSource resolveFont(Class<?> clazz, String fontPath) throws IOException {
        requirePdfBox();
        return S2PdfEngine.resolveFont(clazz, fontPath);
    }

    /**
     * Gets the total page count of a PDF file.
     * <p>
     * PDF 파일의 총 페이지 수를 반환합니다.
     * {@code try-with-resources}를 통해 {@link PDDocument}를 안전하게 닫아 메모리 및 파일 핸들 누수를 방지합니다.
     *
     * @param pdfFile PDF file path | 페이지 수를 확인할 PDF 파일 경로 (Path)
     * @return Total page count, or 0 if null or loading fails | 총 페이지 수 (null이거나 로드 실패 시 0)
     */
    public static int getPageCount(Path pdfFile) {
        requirePdfBox();
        return S2PdfEngine.getPageCount(pdfFile);
    }

    /**
     * Gets the total page count of a PDF file.
     * <p>
     * PDF 파일의 총 페이지 수를 반환합니다.
     * {@code try-with-resources}를 통해 {@link PDDocument}를 안전하게 닫아 메모리 및 파일 핸들 누수를 방지합니다.
     *
     * @param pdfFile PDF File object | 페이지 수를 확인할 PDF File 객체
     * @return Total page count, or 0 if null, nonexistent, or loading fails | 총 페이지 수 (파일이 null이거나 존재하지 않거나 로드 실패 시 0)
     */
    public static int getPageCount(File pdfFile) {
        requirePdfBox();
        return S2PdfEngine.getPageCount(pdfFile);
    }

    /**
     * Gets the total page count of a PDF input stream.
     * <p>
     * PDF 입력 스트림의 총 페이지 수를 반환합니다.
     * 대용량 PDF 처리 시 JVM 힙 메모리 고갈(OOM)을 방지하기 위해 스트림을 디스크 임시 파일로
     * 스트리밍한 후 페이지 수를 파악하며, 작업 완료 후 임시 파일은 즉시 안전하게 삭제됩니다.
     *
     * @param pdfData           PDF InputStream | PDF 입력 스트림
     * @param shouldCloseStream Whether to close the inputStream after completion | 작업 완료 후 inputStream을 닫을지 여부
     * @return Total page count, or 0 if null or loading fails | 총 페이지 수 (스트림이 null이거나 로드 실패 시 0)
     */
    public static int getPageCount(InputStream pdfData, boolean shouldCloseStream) {
        requirePdfBox();
        return S2PdfEngine.getPageCount(pdfData, shouldCloseStream);
    }

    /**
     * Gets the total page count of a PDF input stream (closes stream automatically).
     * <p>
     * PDF 입력 스트림의 총 페이지 수를 반환합니다. (기본적으로 작업 완료 후 inputStream을 자동으로 닫습니다.)
     *
     * @param pdfData PDF InputStream | PDF 입력 스트림
     * @return Total page count, or 0 if null or loading fails | 총 페이지 수 (스트림이 null이거나 로드 실패 시 0)
     * @see #getPageCount(InputStream, boolean)
     */
    public static int getPageCount(InputStream pdfData) {
        requirePdfBox();
        return S2PdfEngine.getPageCount(pdfData);
    }

    /**
     * Gets the total page count of PDF byte array.
     * <p>
     * PDF 바이트 배열의 총 페이지 수를 반환합니다.
     * {@code try-with-resources}를 통해 {@link PDDocument}를 안전하게 닫아 메모리 누수를 방지합니다.
     *
     * @param pdfData PDF byte array | PDF 바이트 배열
     * @return Total page count, or 0 if null or loading fails | 총 페이지 수 (데이터가 null이거나 로드 실패 시 0)
     */
    public static int getPageCount(byte[] pdfData) {
        requirePdfBox();
        return S2PdfEngine.getPageCount(pdfData);
    }

    /**
     * PDF 를 이미지로 변환한다.
     *
     * @param pdfFile     PDF 파일
     * @param pageNo      변환할 페이지 번호
     * @param imagePath   변환 결과 이미지 경로
     * @param dpi         변환 이미지 해상도
     * @param imageFormat 변환 이미지 포멧(확장자)
     * @return 변환한 페이지의 다음 페이지가 존재하는지 여부
     */
    public static boolean pdfToImage(Path pdfFile, int pageNo, Path imagePath, Integer dpi, String imageFormat) {
        requirePdfBox();
        return S2PdfEngine.pdfToImage(pdfFile, pageNo, imagePath, dpi, imageFormat);
    }

    /**
     * PDF 를 이미지로 변환한다.
     *
     * @param pdfData           PDF InputStream
     * @param pageNo            변환할 페이지 번호
     * @param imagePath         변환 결과 이미지 경로
     * @param dpi               변환 이미지 해상도
     * @param imageFormat       변환 이미지 포멧(확장자)
     * @param shouldCloseStream inputStream 을 닫을지 여부
     * @return 변환한 페이지의 다음 페이지가 존재하는지 여부
     */
    public static boolean pdfToImage(InputStream pdfData, int pageNo, Path imagePath, Integer dpi, String imageFormat,
            boolean shouldCloseStream) {
        requirePdfBox();
        return S2PdfEngine.pdfToImage(pdfData, pageNo, imagePath, dpi, imageFormat, shouldCloseStream);
    }

    /**
     * PDF 를 이미지로 변환한다.
     *
     * @param pdfData     PDF byte[]
     * @param pageNo      변환할 페이지 번호
     * @param imagePath   변환 결과 이미지 경로
     * @param dpi         변환 이미지 해상도
     * @param imageFormat 변환 이미지 포멧(확장자)
     * @return 변환한 페이지의 다음 페이지가 존재하는지 여부
     */
    public static boolean pdfToImage(byte[] pdfData, int pageNo, Path imagePath, Integer dpi, String imageFormat) {
        requirePdfBox();
        return S2PdfEngine.pdfToImage(pdfData, pageNo, imagePath, dpi, imageFormat);
    }

    /**
     * PDF 를 이미지로 변환한다.
     *
     * @param pdfFile             PDF 파일
     * @param pageNo              변환할 페이지 번호
     * @param dpi                 변환 이미지 해상도
     * @param imageFormat         변환 이미지 포멧(확장자)
     * @param hasNextPageConsumer 함수형 인터페이스(변환한 페이지의 다음 페이지가 존재하는지 여부)
     * @return 변환한 이미지 InputStream
     */
    public static InputStream pdfToImage(Path pdfFile, int pageNo, Integer dpi, String imageFormat,
            BiConsumer<Boolean, Integer> hasNextPageConsumer) {
        requirePdfBox();
        return S2PdfEngine.pdfToImage(pdfFile, pageNo, dpi, imageFormat, hasNextPageConsumer);
    }

    /**
     * PDF 를 이미지로 변환한다.
     *
     * @param pdfData             PDF InputStream
     * @param pageNo              변환할 페이지 번호
     * @param dpi                 변환 이미지 해상도
     * @param imageFormat         변환 이미지 포멧(확장자)
     * @param hasNextPageConsumer 함수형 인터페이스(변환한 페이지의 다음 페이지가 존재하는지 여부)
     * @param shouldCloseStream   inputStream 을 닫을지 여부
     * @return 변환한 이미지 InputStream
     */
    public static InputStream pdfToImage(InputStream pdfData, int pageNo, Integer dpi, String imageFormat,
            BiConsumer<Boolean, Integer> hasNextPageConsumer, boolean shouldCloseStream) {
        requirePdfBox();
        return S2PdfEngine.pdfToImage(pdfData, pageNo, dpi, imageFormat, hasNextPageConsumer, shouldCloseStream);
    }

    /**
     * PDF 를 이미지로 변환한다.
     *
     * @param pdfData             PDF byte[]
     * @param pageNo              변환할 페이지 번호
     * @param dpi                 변환 이미지 해상도
     * @param imageFormat         변환 이미지 포멧(확장자)
     * @param hasNextPageConsumer 함수형 인터페이스(변환한 페이지의 다음 페이지가 존재하는지 여부)
     * @return 변환한 이미지 InputStream
     */
    public static InputStream pdfToImage(byte[] pdfData, int pageNo, Integer dpi, String imageFormat,
            BiConsumer<Boolean, Integer> hasNextPageConsumer) {
        requirePdfBox();
        return S2PdfEngine.pdfToImage(pdfData, pageNo, dpi, imageFormat, hasNextPageConsumer);
    }

    /**
     * PDF 에 페이지 번호를 추가한다.
     *
     * @param pdfFile               PDF 파일
     * @param numberOfPagesToInsert 추가할 페이지 수 (PDF 전체 페이지 수보다 작은 경우 적은 페이지 만큼 비우고 페이지 번호를 추가한다.)
     * @param fontSize              페이지 폰트 사이즈
     * @param fontFile              페이지 폰프 파일
     * @return 페이지 번호를 추가한 InputStream
     * @throws IOException              PDF 를 읽거나 쓸 수 없을 때
     * @throws IllegalArgumentException 번호를 넣을 쪽 수가 1 미만이거나 전체 쪽 수보다 클 때
     */
    public static InputStream addPageNumbers(Path pdfFile, int numberOfPagesToInsert, Integer fontSize, File fontFile)
            throws IOException {
        requirePdfBox();
        return S2PdfEngine.addPageNumbers(pdfFile, numberOfPagesToInsert, fontSize, fontFile);
    }

    /**
     * PDF 에 페이지 번호를 추가한다.
     *
     * @param pdfData               PDF InputStream
     * @param numberOfPagesToInsert 추가할 페이지 수 (PDF 전체 페이지 수보다 작은 경우 적은 페이지 만큼 비우고 페이지 번호를 추가한다.)
     * @param fontSize              페이지 폰트 사이즈
     * @param fontFile              페이지 폰프 파일
     * @param shouldCloseStream     inputStream 을 닫을지 여부
     * @return 페이지 번호를 추가한 InputStream
     * @throws IOException              PDF 를 읽거나 쓸 수 없을 때
     * @throws IllegalArgumentException 번호를 넣을 쪽 수가 1 미만이거나 전체 쪽 수보다 클 때
     */
    public static InputStream addPageNumbers(InputStream pdfData, int numberOfPagesToInsert, Integer fontSize,
            File fontFile, boolean shouldCloseStream) throws IOException {
        requirePdfBox();
        return S2PdfEngine.addPageNumbers(pdfData, numberOfPagesToInsert, fontSize, fontFile, shouldCloseStream);
    }

    /**
     * PDF 에 페이지 번호를 추가한다. (JAR/WAR 배포 환경에서 ClassPathResource.getInputStream() 사용 가능)
     *
     * @param pdfData               PDF InputStream
     * @param numberOfPagesToInsert 추가할 페이지 수 (PDF 전체 페이지 수보다 작은 경우 적은 페이지 만큼 비우고 페이지 번호를 추가한다.)
     * @param fontSize              페이지 폰트 사이즈
     * @param fontStream            페이지 폰트 InputStream (null 이면 기본 폰트 사용, 작업 완료 후 자동 close)
     * @param shouldCloseStream     inputStream 을 닫을지 여부
     * @return 페이지 번호를 추가한 InputStream
     * @throws IOException              PDF 를 읽거나 쓸 수 없을 때
     * @throws IllegalArgumentException 번호를 넣을 쪽 수가 1 미만이거나 전체 쪽 수보다 클 때
     */
    public static InputStream addPageNumbers(InputStream pdfData, int numberOfPagesToInsert, Integer fontSize,
            InputStream fontStream, boolean shouldCloseStream) throws IOException {
        requirePdfBox();
        return S2PdfEngine.addPageNumbers(pdfData, numberOfPagesToInsert, fontSize, fontStream, shouldCloseStream);
    }

    /**
     * PDF 에 페이지 번호를 추가한다.
     *
     * @param pdfData               PDF byte[]
     * @param numberOfPagesToInsert 추가할 페이지 수 (PDF 전체 페이지 수보다 작은 경우 적은 페이지 만큼 비우고 페이지 번호를 추가한다.)
     * @param fontSize              페이지 폰트 사이즈
     * @param fontFile              페이지 폰프 파일
     * @return 페이지 번호를 추가한 InputStream
     * @throws IOException              PDF 를 읽거나 쓸 수 없을 때
     * @throws IllegalArgumentException 번호를 넣을 쪽 수가 1 미만이거나 전체 쪽 수보다 클 때
     */
    public static InputStream addPageNumbers(byte[] pdfData, int numberOfPagesToInsert, Integer fontSize, File fontFile)
            throws IOException {
        requirePdfBox();
        return S2PdfEngine.addPageNumbers(pdfData, numberOfPagesToInsert, fontSize, fontFile);
    }

    /**
     * PDF 문서에 스탬프(텍스트, 페이지 번호, 이미지 워터마크 등)를 적용한다.
     *
     * <p>사용 예시:
     * <pre>{@code
     * S2PdfUtil.StampOptions options = S2PdfUtil.StampOptions.builder()
     *     .addText(S2PdfUtil.Text.of(pageNo -> "연구노트 제목 - " + pageNo)
     *         .position(S2PdfUtil.Position.TOP_CENTER)
     *         .offset(0, 20)      // 상단 중앙 가장자리에서 아래로 20pt
     *         .fontSize(11)
     *         .font(fontFile))
     *     .pageNumber(S2PdfUtil.PageNumber.of("%d / %d")
     *         .position(S2PdfUtil.Position.BOTTOM_CENTER)
     *         .offset(0, 20)      // 하단 중앙 가장자리에서 위로 20pt
     *         .fontSize(10))
     *     .build();
     *
     * InputStream stampedPdf = S2PdfUtil.stamp(pdfFile, options);
     * }</pre>
     * </p>
     *
     * @param pdfFile 스탬프를 적용할 대상 PDF 파일
     * @param options 스탬프 설정 옵션 (null인 경우 원본 유지)
     * @return 스탬프가 적용된 PDF {@link InputStream}
     * @throws IOException 파일 읽기/쓰기 오류 발생 시
     */
    public static InputStream stamp(Path pdfFile, StampOptions options) throws IOException {
        requirePdfBox();
        return S2PdfEngine.stamp(pdfFile, options);
    }

    /**
     * PDF 문서에 스탬프(텍스트, 페이지 번호, 이미지 워터마크 등)를 적용한다.
     *
     * @param pdfFile 스탬프를 적용할 대상 PDF 파일
     * @param options 스탬프 설정 옵션 (null인 경우 원본 유지)
     * @return 스탬프가 적용된 PDF {@link InputStream}
     * @throws IOException 파일 읽기/쓰기 오류 발생 시
     */
    public static InputStream stamp(File pdfFile, StampOptions options) throws IOException {
        requirePdfBox();
        return S2PdfEngine.stamp(pdfFile, options);
    }

    /**
     * PDF 문서에 스탬프(텍스트, 페이지 번호, 이미지 워터마크 등)를 적용한다.
     *
     * @param pdfData           스탬프를 적용할 대상 PDF {@link InputStream}
     * @param options           스탬프 설정 옵션 (null인 경우 원본 유지)
     * @param shouldCloseStream 작업 완료 후 전달받은 {@code pdfData} 스트림을 닫을지 여부
     * @return 스탬프가 적용된 PDF {@link InputStream}
     * @throws IOException 스트림 읽기/쓰기 오류 발생 시
     */
    public static InputStream stamp(InputStream pdfData, StampOptions options, boolean shouldCloseStream) throws IOException {
        requirePdfBox();
        return S2PdfEngine.stamp(pdfData, options, shouldCloseStream);
    }

    /**
     * PDF 문서에 스탬프(텍스트, 페이지 번호, 이미지 워터마크 등)를 적용한다.
     *
     * @param pdfData 스탬프를 적용할 대상 PDF 바이트 배열
     * @param options 스탬프 설정 옵션 (null인 경우 원본 유지)
     * @return 스탬프가 적용된 PDF {@link InputStream}
     * @throws IOException PDF 파싱/저장 오류 발생 시
     */
    public static InputStream stamp(byte[] pdfData, StampOptions options) throws IOException {
        requirePdfBox();
        return S2PdfEngine.stamp(pdfData, options);
    }

    /**
     * PDF 문서에 스탬프(텍스트, 페이지 번호, 워터마크)를 실제로 적용한다.
     *
     * @param document 대상 PDF 문서
     * @param options  스탬프 종합 옵션
     * @return 스탬프가 적용된 임시 파일 기반 {@link S2ResourceInputStream}
     * @throws IOException PDF 조작 또는 임시 파일 저장 실패 시
     */
    public static InputStream stamp(PDDocument document, StampOptions options) throws IOException {
        requirePdfBox();
        return S2PdfEngine.stamp(document, options);
    }

    /**
     * 여러 개의 PDF InputStream 을 단일 PDF 로 병합한다.
     *
     * @param pdfStreams         병합할 PDF InputStream 목록 (null 항목은 건너뜀)
     * @param shouldCloseStreams 병합 후 입력 스트림들을 자동으로 닫을지 여부
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 또는 암호·권한 제한 문서일 때
     */
    public static InputStream mergePdfs(List<InputStream> pdfStreams, boolean shouldCloseStreams) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergePdfs(pdfStreams, shouldCloseStreams);
    }

    /**
     * 여러 개의 PDF InputStream 을 단일 PDF 로 병합한다. (입력 스트림 자동 close)
     *
     * @param pdfStreams 병합할 PDF InputStream 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfs(InputStream... pdfStreams) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergePdfs(pdfStreams);
    }

    /**
     * 여러 개의 PDF 파일들을 단일 PDF 로 병합한다. 없는 파일은 건너뛴다.
     *
     * @param pdfFiles 병합할 PDF 파일 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfFiles(List<File> pdfFiles) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergePdfFiles(pdfFiles);
    }

    /**
     * 여러 개의 PDF 파일들을 단일 PDF 로 병합한다. (가변인자)
     *
     * @param pdfFiles 병합할 PDF 파일 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfFiles(File... pdfFiles) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergePdfFiles(pdfFiles);
    }

    /**
     * 여러 개의 PDF 파일 경로(Path)들을 단일 PDF 로 병합한다. 없는 파일은 건너뛴다.
     *
     * @param pdfPaths 병합할 PDF 파일 경로 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfPaths(List<Path> pdfPaths) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergePdfPaths(pdfPaths);
    }

    /**
     * 여러 개의 PDF 파일 경로(Path)들을 단일 PDF 로 병합한다. (가변인자)
     *
     * @param pdfPaths 병합할 PDF 파일 경로 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfPaths(Path... pdfPaths) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergePdfPaths(pdfPaths);
    }

    /**
     * 여러 이미지를 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다.
     *
     * @param imageStreams       이미지 InputStream 목록 (PNG, JPG, GIF 등, null 항목은 건너뜀)
     * @param shouldCloseStreams 입력 스트림 자동 닫기 여부
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagesToPdf(List<InputStream> imageStreams, boolean shouldCloseStreams)
            throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergeImagesToPdf(imageStreams, shouldCloseStreams);
    }

    /**
     * 여러 이미지를 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (입력 스트림 자동 close)
     *
     * @param imageStreams 이미지 InputStream 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagesToPdf(InputStream... imageStreams) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergeImagesToPdf(imageStreams);
    }

    /**
     * 여러 이미지 파일(File)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. 없는 파일은 건너뛴다.
     *
     * @param imageFiles 이미지 파일 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImageFiles(List<File> imageFiles) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergeImageFiles(imageFiles);
    }

    /**
     * 여러 이미지 파일(File)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param imageFiles 이미지 파일 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImageFiles(File... imageFiles) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergeImageFiles(imageFiles);
    }

    /**
     * 여러 이미지 경로(Path)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. 없는 파일은 건너뛴다.
     *
     * @param imagePaths 이미지 경로 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagePaths(List<Path> imagePaths) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergeImagePaths(imagePaths);
    }

    /**
     * 여러 이미지 경로(Path)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param imagePaths 이미지 경로 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagePaths(Path... imagePaths) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergeImagePaths(imagePaths);
    }

    /**
     * 메모리상의 이미지(BufferedImage)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다.
     *
     * @param images BufferedImage 목록 (null 항목은 건너뜀)
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagesToPdf(List<BufferedImage> images) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergeImagesToPdf(images);
    }

    /**
     * 메모리상의 이미지(BufferedImage)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param images BufferedImage 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagesToPdf(BufferedImage... images) throws IOException {
        requirePdfBox();
        return S2PdfEngine.mergeImagesToPdf(images);
    }

    /**
     * 단일 이미지 스트림을 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param imageStream       이미지 InputStream
     * @param shouldCloseStream 입력 스트림 자동 닫기 여부
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    public static InputStream convertImageToPdf(InputStream imageStream, boolean shouldCloseStream) throws IOException {
        requirePdfBox();
        return S2PdfEngine.convertImageToPdf(imageStream, shouldCloseStream);
    }

    /**
     * 단일 이미지 byte[] 데이터를 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param imageBytes 이미지 byte 배열
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    public static InputStream convertImageToPdf(byte[] imageBytes) throws IOException {
        requirePdfBox();
        return S2PdfEngine.convertImageToPdf(imageBytes);
    }

    /**
     * 단일 BufferedImage 객체를 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param image BufferedImage 객체
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    public static InputStream convertImageToPdf(BufferedImage image) throws IOException {
        requirePdfBox();
        return S2PdfEngine.convertImageToPdf(image);
    }

    /**
     * 단일 이미지 File 을 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param imageFile 이미지 파일
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    public static InputStream convertImageToPdf(File imageFile) throws IOException {
        requirePdfBox();
        return S2PdfEngine.convertImageToPdf(imageFile);
    }

    /**
     * 단일 이미지 Path 를 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param imagePath 이미지 경로
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    public static InputStream convertImageToPdf(Path imagePath) throws IOException {
        requirePdfBox();
        return S2PdfEngine.convertImageToPdf(imagePath);
    }

    /**
     * Places an image on an A4 page (landscape for wide images, 20pt margin, centered), upright per its EXIF
     * orientation. A JPEG kept at its size is embedded as is (no re-encoding; turned by the page transform); with
     * {@code dpi} > 0 an image clearly larger than the page needs is shrunk first.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 이미지를 A4 한 쪽에 넣는다(가로가 길면 가로 방향, 20pt 여백, 가운데). EXIF 방향대로 바로 세운다. 크기를 유지하는 JPEG 는 다시 압축하지 않고 그대로
     * 넣고 쪽 변환으로 돌린다. {@code dpi} 가 0 보다 크면 쪽에 필요한 것보다 확실히 큰 이미지는 먼저 줄인다. 해상도가 {@link S2ImageUtil#MAX_PIXELS}를
     * 넘으면 디코딩 전에 거부한다.
     *
     * @param bimg          디코딩된 이미지 (rawBytes 와 둘 중 하나)
     * @param rawBytes      이미지 파일 바이트 (bimg 와 둘 중 하나)
     * @param targetPdfFile 저장할 PDF 경로
     * @param dpi           0 보다 크면 쪽에 필요한 것보다 확실히 큰 이미지를 이 해상도로 줄임
     * @throws IOException 이미지를 읽을 수 없거나 너무 클 때
     */
    public static void renderImageToPdfFile(BufferedImage bimg, byte[] rawBytes, Path targetPdfFile, int dpi)
            throws IOException {
        requirePdfBox();
        S2PdfEngine.renderImageToPdfFile(bimg, rawBytes, targetPdfFile, dpi);
    }

    /**
     * Shrinks images inside a PDF that are clearly larger than their page needs at {@code dpi} (an image never needs
     * more than its whole page). Returns false, writing nothing, when no image changed | PDF 안의 이미지 중 쪽에 dpi 로 필요한 것보다 확실히
     * 큰 것을 줄인다 (이미지는 쪽 전체보다 클 필요가 없음). 바뀐 것이 없으면 아무것도 쓰지 않고 false
     *
     * @param source 원본 PDF
     * @param target 줄인 PDF 를 저장할 경로 (바뀐 것이 없으면 쓰지 않음)
     * @param dpi    목표 해상도
     * @return 바뀐 이미지가 있어 target 에 저장했으면 true
     * @throws IOException 읽기·쓰기 오류 시
     */
    public static boolean shrinkPdfImages(Path source, Path target, int dpi) throws IOException {
        requirePdfBox();
        return S2PdfEngine.shrinkPdfImages(source, target, dpi);
    }

    /**
     * Whether the bytes start with a JPEG signature | JPEG 시그니처로 시작하는지
     *
     * @param bytes 이미지 바이트
     * @return JPEG 이면 true
     */
    public static boolean isJpeg(byte[] bytes) {
        requirePdfBox();
        return S2PdfEngine.isJpeg(bytes);
    }

    /**
     * Check document encryption and access permissions | 문서 암호화 및 접근 권한 검사
     *
     * @param pdfFile    검사할 PDF (없으면 아무것도 하지 않음)
     * @param sourceHint 오류 메시지에 쓸 이름
     * @throws IOException 암호가 걸렸거나 내용 추출이 제한된 문서일 때
     */
    public static void assertDocumentAccessible(File pdfFile, String sourceHint) throws IOException {
        requirePdfBox();
        S2PdfEngine.assertDocumentAccessible(pdfFile, sourceHint);
    }

    /**
     * Check PDDocument access permission | 열려 있는 PDDocument 권한 상태 검사
     *
     * @param document   검사할 문서 (null 이면 아무것도 하지 않음)
     * @param sourceHint 오류 메시지에 쓸 이름
     * @throws IOException 내용 추출이 제한된 문서일 때
     */
    public static void assertDocumentAccessible(PDDocument document, String sourceHint) throws IOException {
        requirePdfBox();
        S2PdfEngine.assertDocumentAccessible(document, sourceHint);
    }

    /**
     * Copies a source PDF with one top-level bookmark to its first page; the PDF's own bookmarks move under it.
     * PDFMergerUtility appends each source's outline in order, so every source gets its own entry.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 소스 PDF 를 첫 쪽을 가리키는 최상위 책갈피 하나를 가진 사본으로 만든다. 원래 책갈피는 그 아래로 옮긴다. 원본 파일은 바꾸지 않는다.
     * 여러 PDF 를 병합하기 전에 각각에 적용하면 병합 결과에서 문서마다 책갈피가 하나씩 생긴다.
     *
     * @param pdf   원본 PDF (바꾸지 않음)
     * @param title 책갈피 제목
     * @param copy  책갈피를 넣은 사본을 저장할 경로
     * @throws IOException 읽기·쓰기 오류 또는 암호·권한 제한 문서일 때
     */
    public static void addBookmark(File pdf, String title, Path copy) throws IOException {
        requirePdfBox();
        S2PdfEngine.addBookmark(pdf, title, copy);
    }

    /**
     * Draws the watermark image on every page; the image is embedded once and shared. JPEG is embedded as is, other
     * formats losslessly with their transparency | 모든 쪽에 워터마크를 그린다. 이미지는 한 번만 넣어 공유한다. JPEG 는 그대로, 그 외는 투명도를
     * 유지해 무손실로 넣는다
     *
     * @param document  워터마크를 넣을 문서
     * @param watermark 워터마크 설정
     * @throws IOException 워터마크 이미지를 읽을 수 없을 때
     */
    public static void stampWatermark(PDDocument document, Watermark watermark) throws IOException {
        requirePdfBox();
        S2PdfEngine.stampWatermark(document, watermark);
    }

    /**
     * Writes "current / total" (or the given format) centered at the bottom of {@code count} pages from
     * {@code startIndex}.
     *
     * @param document   쪽 번호를 넣을 문서
     * @param startIndex 첫 번호를 넣을 쪽 (0부터)
     * @param count      번호를 넣을 쪽 수 (전체 수로도 쓰임)
     * @param fontSize   글자 크기
     * @param font       글꼴
     * @param format     {@link String#format} 형식 (현재 번호, 전체 수)
     * @throws IOException 그리기 오류 시
     */
    public static void stampPageNumbers(PDDocument document, int startIndex, int count, float fontSize, PDFont font,
            String format) throws IOException {
        requirePdfBox();
        S2PdfEngine.stampPageNumbers(document, startIndex, count, fontSize, font, format);
    }

    // ========================================================================
    // 🏷️ StampOptions & Sub-Classes (Position, Text, PageNumber, ImageStamp, QrStamp)
    // ========================================================================

    // ========================================================================
    // 📌 페이지 선택기 (PageSelector) - 스탬프 적용 대상 페이지 지정
    // ========================================================================

    /**
     * 스탬프를 적용할 페이지를 유연하게 지정하는 선택기.
     *
     * <p>정적 팩토리 메서드로 생성하며, {@code includes(int, int)} 메서드로 특정 페이지가 대상인지 확인한다.</p>
     *
     * <pre>{@code
     * PageSelector.all()            // 전체 페이지
     * PageSelector.first()          // 첫 페이지만
     * PageSelector.last()           // 마지막 페이지만
     * PageSelector.range(2, 5)      // 2~5 페이지 (1-based, 양 끝 포함)
     * PageSelector.only(1, 3, 5)    // 특정 페이지만
     * PageSelector.exceptFirst()    // 첫 페이지 제외
     * PageSelector.exceptFirst(2)   // 앞 2페이지 제외
     * PageSelector.exceptLast()     // 마지막 페이지 제외
     * PageSelector.exceptLast(2)    // 뒤 2페이지 제외
     * }</pre>
     */
    public static final class PageSelector {

        /** 전체 페이지에 적용하는 싱글턴 인스턴스 */
        private static final PageSelector ALL = new PageSelector(Type.ALL, 0, 0, null);

        private enum Type { ALL, FIRST, LAST, RANGE, ONLY, EXCEPT_FIRST, EXCEPT_LAST }

        private final Type type;
        private final int from; // 1-based (inclusive)
        private final int to;   // 1-based (inclusive)
        private final int[] pages; // for ONLY type

        private PageSelector(Type type, int from, int to, int[] pages) {
            this.type = type;
            this.from = from;
            this.to = to;
            this.pages = pages;
        }

        /** 전체 페이지에 적용 */
        public static PageSelector all() { return ALL; }

        /** 첫 페이지에만 적용 */
        public static PageSelector first() { return new PageSelector(Type.FIRST, 1, 1, null); }

        /** 마지막 페이지에만 적용 */
        public static PageSelector last() { return new PageSelector(Type.LAST, 0, 0, null); }

        /**
         * 지정 범위 페이지에 적용 (1-based, 양 끝 포함).
         *
         * @param from 시작 페이지 (1-based, 포함)
         * @param to   끝 페이지 (1-based, 포함)
         * @return PageSelector
         */
        public static PageSelector range(int from, int to) {
            if (from < 1) throw new IllegalArgumentException("시작 페이지는 1 이상이어야 합니다: " + from);
            if (to < from) throw new IllegalArgumentException("끝 페이지(" + to + ")가 시작 페이지(" + from + ")보다 작습니다.");
            return new PageSelector(Type.RANGE, from, to, null);
        }

        /**
         * 지정된 페이지에만 적용 (1-based).
         *
         * @param pages 적용할 페이지 번호 목록 (1-based)
         * @return PageSelector
         */
        public static PageSelector only(int... pages) {
            if (pages == null || pages.length == 0) throw new IllegalArgumentException("적용할 페이지를 하나 이상 지정해야 합니다.");
            for (int p : pages) {
                if (p < 1) throw new IllegalArgumentException("페이지 번호는 1 이상이어야 합니다: " + p);
            }
            return new PageSelector(Type.ONLY, 0, 0, pages.clone());
        }

        /** 첫 페이지를 제외한 나머지 페이지에 적용 */
        public static PageSelector exceptFirst() { return exceptFirst(1); }

        /**
         * 앞쪽 n 페이지를 제외한 나머지 페이지에 적용.
         *
         * @param count 제외할 앞쪽 페이지 수
         * @return PageSelector
         */
        public static PageSelector exceptFirst(int count) {
            if (count < 1) throw new IllegalArgumentException("제외할 페이지 수는 1 이상이어야 합니다: " + count);
            return new PageSelector(Type.EXCEPT_FIRST, count, 0, null);
        }

        /** 마지막 페이지를 제외한 나머지 페이지에 적용 */
        public static PageSelector exceptLast() { return exceptLast(1); }

        /**
         * 뒤쪽 n 페이지를 제외한 나머지 페이지에 적용.
         *
         * @param count 제외할 뒤쪽 페이지 수
         * @return PageSelector
         */
        public static PageSelector exceptLast(int count) {
            if (count < 1) throw new IllegalArgumentException("제외할 페이지 수는 1 이상이어야 합니다: " + count);
            return new PageSelector(Type.EXCEPT_LAST, 0, count, null);
        }

        /**
         * 지정된 페이지가 이 선택기에 포함되는지 확인한다.
         *
         * @param pageIndex  페이지 인덱스 (0-based)
         * @param totalPages 전체 페이지 수
         * @return 포함 여부
         */
        public boolean includes(int pageIndex, int totalPages) {
            int pageNo = pageIndex + 1; // 1-based
            return switch (type) {
                case ALL -> true;
                case FIRST -> pageNo == 1;
                case LAST -> pageNo == totalPages;
                case RANGE -> pageNo >= from && pageNo <= Math.min(to, totalPages);
                case ONLY -> {
                    for (int p : pages) {
                        if (p == pageNo) yield true;
                    }
                    yield false;
                }
                case EXCEPT_FIRST -> pageNo > from;
                case EXCEPT_LAST -> pageNo <= totalPages - to;
            };
        }
    }

    /**
     * 스탬프 요소(워터마크, 텍스트, 페이지 번호)의 8방향 기준 위치 및 정중앙 Enum.
     *
     * <p>화면상 기준점 구분:
     * <ul>
     *   <li><b>4대 모서리 (Corner):</b> {@link #TOP_LEFT}, {@link #TOP_RIGHT}, {@link #BOTTOM_LEFT}, {@link #BOTTOM_RIGHT}</li>
     *   <li><b>4대 가장자리 (Edge):</b> {@link #TOP_CENTER}(또는 {@link #TOP}), {@link #BOTTOM_CENTER}(또는 {@link #BOTTOM}),
     *       {@link #CENTER_LEFT}(또는 {@link #LEFT}), {@link #CENTER_RIGHT}(또는 {@link #RIGHT})</li>
     *   <li><b>페이지 정중앙 (Center):</b> {@link #CENTER}</li>
     * </ul>
     * </p>
     */
    public enum Position {
        /** 좌측 상단 모서리 (Top-Left Corner) */
        TOP_LEFT,
        /** 상단 중앙 가장자리 (Top-Center Edge) */
        TOP_CENTER,
        /** 상단 중앙 (TOP_CENTER 동일) */
        TOP,
        /** 우측 상단 모서리 (Top-Right Corner) */
        TOP_RIGHT,
        /** 좌측 중앙 가장자리 (Center-Left Edge) */
        CENTER_LEFT,
        /** 좌측 중앙 (CENTER_LEFT 동일) */
        LEFT,
        /** 페이지 정중앙 (Center) */
        CENTER,
        /** 우측 중앙 가장자리 (Center-Right Edge) */
        CENTER_RIGHT,
        /** 우측 중앙 (CENTER_RIGHT 동일) */
        RIGHT,
        /** 좌측 하단 모서리 (Bottom-Left Corner) */
        BOTTOM_LEFT,
        /** 하단 중앙 가장자리 (Bottom-Center Edge) */
        BOTTOM_CENTER,
        /** 하단 중앙 (BOTTOM_CENTER 동일) */
        BOTTOM,
        /** 우측 하단 모서리 (Bottom-Right Corner) */
        BOTTOM_RIGHT
    }

    // ========================================================================
    // 📌 모든 스탬프 요소의 공통 기반 (BaseStampElement)
    // ========================================================================

    /**
     * 모든 스탬프 요소(텍스트, 이미지, QR 등)의 공통 기반 추상 클래스.
     *
     * <p>위치(Position), 오프셋(Offset), 불투명도(Opacity), 회전(Rotation), 페이지 선택기(PageSelector) 등의
     * 공통 속성 및 플루언트(Fluent) 빌더 메서드를 제공합니다.</p>
     *
     * @param <T> 구체 하위 클래스 타입 (플루언트 체이닝 반환용)
     */
    public abstract static class BaseStampElement<T extends BaseStampElement<T>> {
        protected Position position;
        protected float offsetX = 0f;
        protected float offsetY = 20f;
        protected boolean customOffset = false;
        protected float opacity = 1.0f;
        protected PageSelector pageSelector;

        protected BaseStampElement(Position defaultPosition) {
            this.position = defaultPosition != null ? defaultPosition : Position.CENTER;
        }

        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        /**
         * 기준 위치를 설정한다.
         *
         * @param position 기준 위치
         * @return this
         */
        public T position(Position position) {
            this.position = position != null ? position : Position.CENTER;
            if (!this.customOffset) {
                if (this.position == Position.CENTER || this.position == Position.CENTER_LEFT || this.position == Position.LEFT
                        || this.position == Position.CENTER_RIGHT || this.position == Position.RIGHT) {
                    this.offsetY = 0f;
                } else {
                    this.offsetY = 20f;
                }
            }
            return self();
        }

        /**
         * 기준 위치로부터의 가로/세로 상대 오프셋(pt)을 설정한다.
         *
         * @param x 가로 상대 오프셋 (pt)
         * @param y 세로 상대 오프셋 (pt)
         * @return this
         */
        public T offset(float x, float y) {
            this.offsetX = x;
            this.offsetY = y;
            this.customOffset = true;
            return self();
        }

        /**
         * 좌측 하단(BOTTOM_LEFT)을 기준으로 상대 오프셋을 지정한다.
         *
         * @param offsetX 좌측으로부터의 여백(pt)
         * @param offsetY 하단으로부터의 여백(pt)
         * @return this
         */
        public T bottomLeft(float offsetX, float offsetY) {
            this.position = Position.BOTTOM_LEFT;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.customOffset = true;
            return self();
        }

        /** 좌측 하단(BOTTOM_LEFT, 기본 여백: 좌 20pt, 하 20pt)에 배치한다. */
        public T bottomLeft() { return bottomLeft(20f, 20f); }

        /**
         * 하단 중앙(BOTTOM_CENTER)을 기준으로 상대 오프셋을 지정한다.
         *
         * @param offsetX 중앙으로부터의 가로 오프셋(pt)
         * @param offsetY 하단으로부터의 세로 여백(pt)
         * @return this
         */
        public T bottomCenter(float offsetX, float offsetY) {
            this.position = Position.BOTTOM_CENTER;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.customOffset = true;
            return self();
        }

        /** 하단 중앙(BOTTOM_CENTER, 기본 여백: 하 20pt)에 배치한다. */
        public T bottomCenter() { return bottomCenter(0f, 20f); }

        /**
         * 우측 하단(BOTTOM_RIGHT)을 기준으로 상대 오프셋을 지정한다.
         *
         * @param offsetX 우측으로부터의 여백(pt)
         * @param offsetY 하단으로부터의 여백(pt)
         * @return this
         */
        public T bottomRight(float offsetX, float offsetY) {
            this.position = Position.BOTTOM_RIGHT;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.customOffset = true;
            return self();
        }

        /** 우측 하단(BOTTOM_RIGHT, 기본 여백: 우 20pt, 하 20pt)에 배치한다. */
        public T bottomRight() { return bottomRight(20f, 20f); }

        /**
         * 좌측 상단(TOP_LEFT)을 기준으로 상대 오프셋을 지정한다.
         *
         * @param offsetX 좌측으로부터의 여백(pt)
         * @param offsetY 상단으로부터의 여백(pt)
         * @return this
         */
        public T topLeft(float offsetX, float offsetY) {
            this.position = Position.TOP_LEFT;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.customOffset = true;
            return self();
        }

        /** 좌측 상단(TOP_LEFT, 기본 여백: 좌 20pt, 상 20pt)에 배치한다. */
        public T topLeft() { return topLeft(20f, 20f); }

        /**
         * 상단 중앙(TOP_CENTER)을 기준으로 상대 오프셋을 지정한다.
         *
         * @param offsetX 중앙으로부터의 가로 오프셋(pt)
         * @param offsetY 상단으로부터의 여백(pt)
         * @return this
         */
        public T topCenter(float offsetX, float offsetY) {
            this.position = Position.TOP_CENTER;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.customOffset = true;
            return self();
        }

        /** 상단 중앙(TOP_CENTER, 기본 여백: 상 20pt)에 배치한다. */
        public T topCenter() { return topCenter(0f, 20f); }

        /**
         * 우측 상단(TOP_RIGHT)을 기준으로 상대 오프셋을 지정한다.
         *
         * @param offsetX 우측으로부터의 여백(pt)
         * @param offsetY 상단으로부터의 여백(pt)
         * @return this
         */
        public T topRight(float offsetX, float offsetY) {
            this.position = Position.TOP_RIGHT;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.customOffset = true;
            return self();
        }

        /** 우측 상단(TOP_RIGHT, 기본 여백: 우 20pt, 상 20pt)에 배치한다. */
        public T topRight() { return topRight(20f, 20f); }

        /** 페이지 정중앙(CENTER)에 배치한다. */
        public T center() {
            this.position = Position.CENTER;
            this.offsetX = 0f;
            this.offsetY = 0f;
            this.customOffset = true;
            return self();
        }

        /**
         * 불투명도를 설정한다. (0.0f 완전 투명 ~ 1.0f 완전 불투명, 기본값: 1.0f)
         *
         * @param opacity 불투명도
         * @return this
         */
        public T opacity(float opacity) {
            this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
            return self();
        }

        /**
         * 스탬프를 적용할 페이지를 지정한다. (null이면 전체 페이지 적용)
         *
         * @param pageSelector 페이지 선택기
         * @return this
         */
        public T pages(PageSelector pageSelector) {
            this.pageSelector = pageSelector;
            return self();
        }

        public Position getPosition() { return position; }
        public float getOffsetX() { return offsetX; }
        public float getOffsetY() { return offsetY; }
        public float getOpacity() { return opacity; }
        public PageSelector getPageSelector() { return pageSelector; }

        /**
         * 이 요소가 지정된 페이지에 적용되어야 하는지 확인한다.
         *
         * @param pageIndex  페이지 인덱스 (0-based)
         * @param totalPages 전체 페이지 수
         * @return 적용 여부
         */
        public boolean appliesToPage(int pageIndex, int totalPages) {
            return pageSelector == null || pageSelector.includes(pageIndex, totalPages);
        }
    }

    /**
     * PDF 텍스트 기반 스탬프 요소(텍스트 스탬프, 페이지 번호 등)의 공통 기반 추상 클래스.
     *
     * <p>{@link BaseStampElement}의 위치/불투명도/페이지 선택 기능을 상속받고,
     * 글자 크기, 글자 색상, 폰트 파일/객체, 선 두께/볼드 등의 텍스트 전용 속성을 추가합니다.</p>
     *
     * @param <T> 구체 하위 클래스 타입 (플루언트 체이닝 반환용)
     */
    /**
     * 여러 줄 텍스트의 줄 정렬 방식.
     *
     * <p>여러 줄 텍스트에서 각 줄의 길이가 다를 때, 가장 긴 줄 대비 짧은 줄을 어느 쪽으로 정렬할지 결정한다.
     * 한 줄 텍스트에서는 효과가 없다.</p>
     *
     * <pre>{@code
     * // LEFT (기본값):         CENTER:              RIGHT:
     * // |첫째줄           |   |   첫째줄      |    |           첫째줄|
     * // |둘째줄 길게 작성  |   |둘째줄 길게 작성|    |둘째줄 길게 작성|
     * // |셋째줄            |   |    셋째줄     |    |            셋째줄|
     * }</pre>
     */
    public enum TextAlign {
        /** 왼쪽 맞춤 (기본값). 각 줄의 시작이 왼쪽으로 정렬된다. */
        LEFT,
        /** 가운데 맞춤. 각 줄이 가장 긴 줄의 중앙에 정렬된다. */
        CENTER,
        /** 오른쪽 맞춤. 각 줄의 끝이 오른쪽으로 정렬된다. */
        RIGHT
    }

    public abstract static class BaseTextElement<T extends BaseTextElement<T>> extends BaseStampElement<T> {
        protected float fontSize = 10f;
        protected Color color = Color.BLACK;
        protected File fontFile;
        protected PDFont font;
        protected float strokeWidth = 0f;
        protected boolean bold = false;
        protected TextAlign textAlign = TextAlign.LEFT;

        protected BaseTextElement(Position defaultPosition) {
            super(defaultPosition != null ? defaultPosition : Position.BOTTOM_CENTER);
        }

        /**
         * 폰트 크기(pt)를 설정한다. (기본값: 10)
         *
         * @param fontSize 폰트 크기
         * @return this
         */
        public T fontSize(float fontSize) {
            this.fontSize = fontSize;
            return self();
        }

        /**
         * 글자 색상을 설정한다. (기본값: {@link Color#BLACK})
         *
         * @param color 글자 색상
         * @return this
         */
        public T color(Color color) {
            if (color != null) this.color = color;
            return self();
        }

        /**
         * 글자 색상을 RGB 정수 값(0~255)으로 설정한다.
         *
         * @param r Red (0-255)
         * @param g Green (0-255)
         * @param b Blue (0-255)
         * @return this
         */
        public T color(int r, int g, int b) {
            this.color = new Color(r, g, b);
            return self();
        }

        /**
         * 한글/특수문자 등을 출력하기 위한 폰트 파일(TTF/OTF)을 설정한다.
         *
         * @param fontFile 폰트 파일
         * @return this
         */
        public T font(File fontFile) {
            this.fontFile = fontFile;
            return self();
        }

        /**
         * 한글/특수문자 등을 출력하기 위한 폰트 경로(TTF/OTF)를 설정한다.
         *
         * @param fontPath 폰트 파일 경로
         * @return this
         */
        public T font(Path fontPath) {
            this.fontFile = fontPath != null ? fontPath.toFile() : null;
            return self();
        }

        /**
         * 사전에 로드된 PDFont 객체를 직접 설정한다.
         *
         * @param font PDFont 객체
         * @return this
         */
        public T font(PDFont font) {
            this.font = font;
            return self();
        }

        /**
         * 선 두께(굵기)를 설정한다. (0보다 크면 외곽선을 채움과 동시에 선을 그어 볼드/두께 효과를 냄)
         *
         * @param thickness 텍스트 두께/선 굵기 (pt 단위, 예: 0.3f, 0.5f 등)
         * @return this
         */
        public T thickness(float thickness) {
            this.strokeWidth = Math.max(0f, thickness);
            this.bold = this.strokeWidth > 0f;
            return self();
        }

        /**
         * 선 굵기(strokeWidth)를 설정한다. ({@link #thickness(float)}와 동일)
         *
         * @param strokeWidth 선 굵기 (pt 단위)
         * @return this
         */
        public T strokeWidth(float strokeWidth) {
            return thickness(strokeWidth);
        }

        /**
         * 볼드(굵게) 처리할지 여부를 설정한다.
         *
         * @param bold 볼드 활성화 여부
         * @return this
         */
        public T bold(boolean bold) {
            this.bold = bold;
            return self();
        }

        /**
         * 볼드(굵게) 처리한다.
         *
         * @return this
         */
        public T bold() {
            return bold(true);
        }

        /**
         * 여러 줄 텍스트의 줄 정렬 방식을 설정한다. (기본값: {@link TextAlign#LEFT})
         *
         * <p>여러 줄 텍스트에서 줄마다 길이가 다를 때, 가장 긴 줄을 기준으로 짧은 줄을 어디에 정렬할지 지정한다.
         * 한 줄 텍스트에서는 효과가 없다.</p>
         *
         * @param textAlign 줄 정렬 방식 ({@link TextAlign#LEFT}, {@link TextAlign#CENTER}, {@link TextAlign#RIGHT})
         * @return this
         */
        public T multiLineTextAlign(TextAlign textAlign) {
            if (textAlign != null) this.textAlign = textAlign;
            return self();
        }

        public float getFontSize() { return fontSize; }
        public Color getColor() { return color; }
        public File getFontFile() { return fontFile; }
        public PDFont getFont() { return font; }
        public float getThickness() { return strokeWidth; }
        public float getStrokeWidth() { return strokeWidth; }
        public boolean isBold() { return bold; }
        public TextAlign getTextAlign() { return textAlign; }
    }

    /**
     * PDF 페이지에 표시할 텍스트 스탬프 설정 (연구노트 제목, 헤더, 푸터, 보안등급, 텍스트 워터마크 등).
     *
     * <p>고정 문자열({@link #of(String)}) 또는 페이지별 동적 문자열 함수({@link #of(Function)}, {@link #of(BiFunction)})를 지원하며,
     * 폰트, 크기, 색상, 투명도, 회전 각도, 격자 반복(워터마크) 기능을 제공합니다.</p>
     */
    public static class Text extends BaseTextElement<Text> {
        private String text;
        private Function<Integer, String> textProvider; // pageNo (1-based) -> String
        private BiFunction<Integer, Integer, String> pageTextProvider; // (pageNo, totalPages) -> String
        private float rotation = 0f;
        private boolean repeat = false;
        private float repeatGapX = 150f;
        private float repeatGapY = 150f;

        public Text() {
            super(Position.TOP_CENTER);
        }

        /**
         * 모든 대상 페이지에 동일하게 출력할 고정 문자열로 텍스트 스탬프를 생성한다.
         *
         * @param text 출력할 텍스트 문자열
         * @return Text 설정 객체
         */
        public static Text of(String text) {
            var t = new Text();
            t.text = text;
            return t;
        }

        /**
         * 페이지 번호(1-based)에 따라 동적으로 다른 문자열을 출력하는 함수형 인터페이스로 텍스트 스탬프를 생성한다.
         *
         * @param textProvider 페이지 번호(1-based)를 받아 해당 페이지에 표시할 텍스트를 반환하는 함수
         * @return Text 설정 객체
         */
        public static Text of(Function<Integer, String> textProvider) {
            var t = new Text();
            t.textProvider = textProvider;
            return t;
        }

        /**
         * 현재 페이지 번호와 전체 페이지 수((currentPage, totalPages) -> String)에 따라 동적으로 문자열을 생성하는 함수형 인터페이스로 텍스트 스탬프를 생성한다.
         *
         * @param pageTextProvider 현재 페이지(1-based)와 전체 페이지 수를 받아 해당 페이지에 표시할 텍스트를 반환하는 함수
         * @return Text 설정 객체
         */
        public static Text of(BiFunction<Integer, Integer, String> pageTextProvider) {
            var t = new Text();
            t.pageTextProvider = pageTextProvider;
            return t;
        }

        /**
         * 현재 페이지 번호와 전체 페이지 수((currentPage, totalPages) -> String)에 따라 동적으로 문자열을 생성하는 함수를 설정한다.
         *
         * @param pageTextProvider 현재 페이지와 전체 페이지 수를 받아 해당 페이지에 표시할 텍스트를 반환하는 함수
         * @return this
         */
        public Text text(BiFunction<Integer, Integer, String> pageTextProvider) {
            this.pageTextProvider = pageTextProvider;
            return this;
        }

        /**
         * 텍스트 회전 각도를 설정한다. (도 단위, 시계 반대 방향 회전, 기본값: 0도)
         *
         * @param rotation 회전 각도 (도)
         * @return this
         */
        public Text rotation(float rotation) {
            this.rotation = rotation;
            return this;
        }

        /**
         * 페이지 전체에 일정한 간격(기본 150pt)으로 바둑판(격자) 반복 패턴으로 텍스트를 출력할지 여부를 설정한다.
         *
         * @param repeat 반복 활성화 여부
         * @return this
         */
        public Text repeat(boolean repeat) {
            this.repeat = repeat;
            return this;
        }

        /**
         * 페이지 전체에 지정된 간격으로 바둑판(격자) 반복 패턴으로 텍스트를 출력한다.
         *
         * @param repeat 반복 활성화 여부
         * @param gapX   가로 반복 간격(pt)
         * @param gapY   세로 반복 간격(pt)
         * @return this
         */
        public Text repeat(boolean repeat, float gapX, float gapY) {
            this.repeat = repeat;
            this.repeatGapX = gapX;
            this.repeatGapY = gapY;
            return this;
        }

        public String resolveText(int pageNo, int totalPages) {
            if (pageTextProvider != null) {
                return pageTextProvider.apply(pageNo, totalPages);
            }
            if (textProvider != null) {
                return textProvider.apply(pageNo);
            }
            return text;
        }

        public String resolveText(int pageNo) {
            return resolveText(pageNo, pageNo);
        }

        public float getRotation() { return rotation; }
        public boolean isRepeat() { return repeat; }
        public float getRepeatGapX() { return repeatGapX; }
        public float getRepeatGapY() { return repeatGapY; }
    }

    /**
     * PDF 페이지에 표시할 페이지 번호 스탬프 설정.
     */
    public static class PageNumber extends BaseTextElement<PageNumber> {
        private String format = "%d / %d";
        private String prefix;
        private String suffix;
        private String separator; // null 이면 기본 " / "
        private boolean showTotalPage = true;
        private BiFunction<Integer, Integer, String> customFormatProvider;

        // 현재 페이지(n) 전용 스타일
        private Float currentPageFontSize;
        private Color currentPageColor;
        private Float currentPageStrokeWidth;
        private Boolean currentPageBold;
        private File currentPageFontFile;
        private PDFont currentPageFont;

        // 전체 페이지(m) 전용 스타일
        private Float totalPageFontSize;
        private Color totalPageColor;
        private Float totalPageStrokeWidth;
        private Boolean totalPageBold;
        private File totalPageFontFile;
        private PDFont totalPageFont;

        // 구분자(/) 전용 스타일
        private Float separatorFontSize;
        private Color separatorColor;
        private Float separatorStrokeWidth;
        private Boolean separatorBold;
        private File separatorFontFile;
        private PDFont separatorFont;

        // 접두사/접미사 전용 스타일
        private Float prefixFontSize;
        private Color prefixColor;
        private Float prefixStrokeWidth;
        private Boolean prefixBold;
        private File prefixFontFile;
        private PDFont prefixFont;

        private Float suffixFontSize;
        private Color suffixColor;
        private Float suffixStrokeWidth;
        private Boolean suffixBold;
        private File suffixFontFile;
        private PDFont suffixFont;

        public PageNumber() {
            super(Position.BOTTOM_CENTER);
        }

        /**
         * 기본 포맷("%d / %d")의 페이지 번호 설정 객체를 생성한다.
         *
         * @return PageNumber 설정 객체
         */
        public static PageNumber of() {
            return new PageNumber();
        }

        /**
         * 지정된 포맷의 페이지 번호 설정 객체를 생성한다. (예: "%d / %d", "- %d -", "Page %d of %d")
         *
         * @param format {@link String#format} 형식의 포맷 문자열
         * @return PageNumber 설정 객체
         */
        public static PageNumber of(String format) {
            var p = new PageNumber();
            if (format != null) p.format = format;
            return p;
        }

        /**
         * 동적 포맷터 함수((currentPage, totalPages) -> String)를 사용하여 페이지 번호 설정 객체를 생성한다.
         *
         * @param customFormatProvider 현재 페이지(1-based)와 전체 페이지 수를 받아 포맷팅된 문자열을 반환하는 함수
         * @return PageNumber 설정 객체
         */
        public static PageNumber of(BiFunction<Integer, Integer, String> customFormatProvider) {
            var p = new PageNumber();
            p.customFormatProvider = customFormatProvider;
            return p;
        }

        /**
         * 현재 페이지 및 전체 페이지 번호를 기반으로 표시할 문자열을 계산한다.
         */
        public String resolveText(int pageNo, int totalPages) {
            if (customFormatProvider != null) {
                return customFormatProvider.apply(pageNo, totalPages);
            }
            return String.format(format, pageNo, totalPages);
        }

        /**
         * 현재 페이지 번호(n)의 폰트 크기(pt)를 개별 설정한다.
         *
         * @param fontSize 폰트 크기
         * @return this
         */
        public PageNumber currentPageFontSize(float fontSize) {
            this.currentPageFontSize = fontSize;
            return this;
        }

        /**
         * 현재 페이지 번호(n)의 글자 색상을 개별 설정한다.
         *
         * @param color 글자 색상
         * @return this
         */
        public PageNumber currentPageColor(Color color) {
            this.currentPageColor = color;
            return this;
        }

        /**
         * 현재 페이지 번호(n)의 글자 색상을 RGB 정수 값(0~255)으로 설정한다.
         *
         * @param r Red (0-255)
         * @param g Green (0-255)
         * @param b Blue (0-255)
         * @return this
         */
        public PageNumber currentPageColor(int r, int g, int b) {
            return currentPageColor(new Color(r, g, b));
        }

        /**
         * 현재 페이지 번호(n)의 볼드(굵게) 처리 여부를 개별 설정한다.
         *
         * @param bold 볼드 활성화 여부
         * @return this
         */
        public PageNumber currentPageBold(boolean bold) {
            this.currentPageBold = bold;
            return this;
        }

        /**
         * 현재 페이지 번호(n)를 볼드(굵게) 처리한다.
         *
         * @return this
         */
        public PageNumber currentPageBold() {
            return currentPageBold(true);
        }

        /**
         * 현재 페이지 번호(n)의 선 두께(굵기)를 설정한다.
         *
         * @param thickness 두께 (pt 단위)
         * @return this
         */
        public PageNumber currentPageThickness(float thickness) {
            this.currentPageStrokeWidth = Math.max(0f, thickness);
            this.currentPageBold = this.currentPageStrokeWidth > 0f;
            return this;
        }

        /**
         * 현재 페이지 번호(n)의 선 굵기를 설정한다.
         *
         * @param strokeWidth 선 굵기 (pt 단위)
         * @return this
         */
        public PageNumber currentPageStrokeWidth(float strokeWidth) {
            return currentPageThickness(strokeWidth);
        }

        /**
         * 현재 페이지 번호(n)의 폰트 파일을 개별 설정한다.
         *
         * @param fontFile 폰트 파일
         * @return this
         */
        public PageNumber currentPageFont(File fontFile) {
            this.currentPageFontFile = fontFile;
            return this;
        }

        /**
         * 현재 페이지 번호(n)의 폰트 경로를 개별 설정한다.
         *
         * @param fontPath 폰트 파일 경로
         * @return this
         */
        public PageNumber currentPageFont(Path fontPath) {
            this.currentPageFontFile = fontPath != null ? fontPath.toFile() : null;
            return this;
        }

        /**
         * 현재 페이지 번호(n)의 PDFont 객체를 직접 설정한다.
         *
         * @param font PDFont 객체
         * @return this
         */
        public PageNumber currentPageFont(PDFont font) {
            this.currentPageFont = font;
            return this;
        }

        /**
         * 전체 페이지 수(m)의 폰트 크기(pt)를 개별 설정한다.
         *
         * @param fontSize 폰트 크기
         * @return this
         */
        public PageNumber totalPageFontSize(float fontSize) {
            this.totalPageFontSize = fontSize;
            return this;
        }

        /**
         * 전체 페이지 수(m)의 글자 색상을 개별 설정한다.
         *
         * @param color 글자 색상
         * @return this
         */
        public PageNumber totalPageColor(Color color) {
            this.totalPageColor = color;
            return this;
        }

        /**
         * 전체 페이지 수(m)의 글자 색상을 RGB 정수 값(0~255)으로 설정한다.
         *
         * @param r Red (0-255)
         * @param g Green (0-255)
         * @param b Blue (0-255)
         * @return this
         */
        public PageNumber totalPageColor(int r, int g, int b) {
            return totalPageColor(new Color(r, g, b));
        }

        /**
         * 전체 페이지 수(m)의 볼드(굵게) 처리 여부를 개별 설정한다.
         *
         * @param bold 볼드 활성화 여부
         * @return this
         */
        public PageNumber totalPageBold(boolean bold) {
            this.totalPageBold = bold;
            return this;
        }

        /**
         * 전체 페이지 수(m)를 볼드(굵게) 처리한다.
         *
         * @return this
         */
        public PageNumber totalPageBold() {
            return totalPageBold(true);
        }

        /**
         * 전체 페이지 수(m)의 선 두께(굵기)를 설정한다.
         *
         * @param thickness 두께 (pt 단위)
         * @return this
         */
        public PageNumber totalPageThickness(float thickness) {
            this.totalPageStrokeWidth = Math.max(0f, thickness);
            this.totalPageBold = this.totalPageStrokeWidth > 0f;
            return this;
        }

        /**
         * 전체 페이지 수(m)의 선 굵기를 설정한다.
         *
         * @param strokeWidth 선 굵기 (pt 단위)
         * @return this
         */
        public PageNumber totalPageStrokeWidth(float strokeWidth) {
            return totalPageThickness(strokeWidth);
        }

        /**
         * 전체 페이지 수(m)의 폰트 파일을 개별 설정한다.
         *
         * @param fontFile 폰트 파일
         * @return this
         */
        public PageNumber totalPageFont(File fontFile) {
            this.totalPageFontFile = fontFile;
            return this;
        }

        /**
         * 전체 페이지 수(m)의 폰트 경로를 개별 설정한다.
         *
         * @param fontPath 폰트 파일 경로
         * @return this
         */
        public PageNumber totalPageFont(Path fontPath) {
            this.totalPageFontFile = fontPath != null ? fontPath.toFile() : null;
            return this;
        }

        /**
         * 전체 페이지 수(m)의 PDFont 객체를 직접 설정한다.
         *
         * @param font PDFont 객체
         * @return this
         */
        public PageNumber totalPageFont(PDFont font) {
            this.totalPageFont = font;
            return this;
        }

        /**
         * 페이지 번호 구분자 문자열을 설정한다. (기본값: " / ")
         *
         * @param separator 구분자 (예: " / ", " | ", " of ", "-")
         * @return this
         */
        public PageNumber separator(String separator) {
            this.separator = separator;
            return this;
        }

        /**
         * 페이지 번호 구분자의 폰트 크기(pt)를 개별 설정한다.
         *
         * @param fontSize 폰트 크기
         * @return this
         */
        public PageNumber separatorFontSize(float fontSize) {
            this.separatorFontSize = fontSize;
            return this;
        }

        /**
         * 페이지 번호 구분자의 글자 색상을 개별 설정한다.
         *
         * @param color 글자 색상
         * @return this
         */
        public PageNumber separatorColor(Color color) {
            this.separatorColor = color;
            return this;
        }

        /**
         * 페이지 번호 구분자의 글자 색상을 RGB 정수 값(0~255)으로 설정한다.
         *
         * @param r Red (0-255)
         * @param g Green (0-255)
         * @param b Blue (0-255)
         * @return this
         */
        public PageNumber separatorColor(int r, int g, int b) {
            return separatorColor(new Color(r, g, b));
        }

        /**
         * 페이지 번호 구분자의 볼드(굵게) 처리 여부를 개별 설정한다.
         *
         * @param bold 볼드 활성화 여부
         * @return this
         */
        public PageNumber separatorBold(boolean bold) {
            this.separatorBold = bold;
            return this;
        }

        /**
         * 페이지 번호 구분자를 볼드(굵게) 처리한다.
         *
         * @return this
         */
        public PageNumber separatorBold() {
            return separatorBold(true);
        }

        /**
         * 페이지 번호 구분자의 선 두께(굵기)를 설정한다.
         *
         * @param thickness 두께 (pt 단위)
         * @return this
         */
        public PageNumber separatorThickness(float thickness) {
            this.separatorStrokeWidth = Math.max(0f, thickness);
            this.separatorBold = this.separatorStrokeWidth > 0f;
            return this;
        }

        /**
         * 페이지 번호 구분자의 선 굵기를 설정한다.
         *
         * @param strokeWidth 선 굵기 (pt 단위)
         * @return this
         */
        public PageNumber separatorStrokeWidth(float strokeWidth) {
            return separatorThickness(strokeWidth);
        }

        /**
         * 페이지 번호 구분자의 폰트 파일을 개별 설정한다.
         *
         * @param fontFile 폰트 파일
         * @return this
         */
        public PageNumber separatorFont(File fontFile) {
            this.separatorFontFile = fontFile;
            return this;
        }

        /**
         * 페이지 번호 구분자의 폰트 경로를 개별 설정한다.
         *
         * @param fontPath 폰트 파일 경로
         * @return this
         */
        public PageNumber separatorFont(Path fontPath) {
            this.separatorFontFile = fontPath != null ? fontPath.toFile() : null;
            return this;
        }

        /**
         * 페이지 번호 구분자의 PDFont 객체를 직접 설정한다.
         *
         * @param font PDFont 객체
         * @return this
         */
        public PageNumber separatorFont(PDFont font) {
            this.separatorFont = font;
            return this;
        }

        /**
         * 전체 페이지 수(m) 표시 여부를 설정한다. (false로 설정 시 현재 페이지 n만 단독 표시)
         *
         * @param show 전체 페이지 표시 여부 (기본값: true)
         * @return this
         */
        public PageNumber showTotalPage(boolean show) {
            this.showTotalPage = show;
            return this;
        }

        /**
         * 페이지 번호 앞에 붙는 접두사(Prefix)를 설정한다. (예: "[", "Page ")
         *
         * @param prefix 접두사 문자열
         * @return this
         */
        public PageNumber prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * 페이지 번호 뒤에 붙는 접미사(Suffix)를 설정한다. (예: "]", " 쪽")
         *
         * @param suffix 접미사 문자열
         * @return this
         */
        public PageNumber suffix(String suffix) {
            this.suffix = suffix;
            return this;
        }

        /**
         * 접두사 글자 색상을 설정한다.
         *
         * @param color 글자 색상
         * @return this
         */
        public PageNumber prefixColor(Color color) {
            this.prefixColor = color;
            return this;
        }

        /**
         * 접미사 글자 색상을 설정한다.
         *
         * @param color 글자 색상
         * @return this
         */
        public PageNumber suffixColor(Color color) {
            this.suffixColor = color;
            return this;
        }

        /**
         * 접두사 폰트 크기(pt)를 설정한다.
         *
         * @param fontSize 폰트 크기
         * @return this
         */
        public PageNumber prefixFontSize(float fontSize) {
            this.prefixFontSize = fontSize;
            return this;
        }

        /**
         * 접미사 폰트 크기(pt)를 설정한다.
         *
         * @param fontSize 폰트 크기
         * @return this
         */
        public PageNumber suffixFontSize(float fontSize) {
            this.suffixFontSize = fontSize;
            return this;
        }

        /**
         * 접두사 볼드 여부를 설정한다.
         *
         * @param bold 볼드 활성화 여부
         * @return this
         */
        public PageNumber prefixBold(boolean bold) {
            this.prefixBold = bold;
            return this;
        }

        /**
         * 접두사를 볼드 처리한다.
         *
         * @return this
         */
        public PageNumber prefixBold() {
            return prefixBold(true);
        }

        /**
         * 접미사 볼드 여부를 설정한다.
         *
         * @param bold 볼드 활성화 여부
         * @return this
         */
        public PageNumber suffixBold(boolean bold) {
            this.suffixBold = bold;
            return this;
        }

        /**
         * 접미사를 볼드 처리한다.
         *
         * @return this
         */
        public PageNumber suffixBold() {
            return suffixBold(true);
        }

        /**
         * 접두사 선 두께(pt)를 설정한다.
         *
         * @param thickness 두께 (pt 단위)
         * @return this
         */
        public PageNumber prefixThickness(float thickness) {
            this.prefixStrokeWidth = Math.max(0f, thickness);
            this.prefixBold = this.prefixStrokeWidth > 0f;
            return this;
        }

        /**
         * 접미사 선 두께(pt)를 설정한다.
         *
         * @param thickness 두께 (pt 단위)
         * @return this
         */
        public PageNumber suffixThickness(float thickness) {
            this.suffixStrokeWidth = Math.max(0f, thickness);
            this.suffixBold = this.suffixStrokeWidth > 0f;
            return this;
        }

        /**
         * 접두사 폰트 파일(TTF/OTF)을 설정한다.
         *
         * @param fontFile 폰트 파일
         * @return this
         */
        public PageNumber prefixFont(File fontFile) {
            this.prefixFontFile = fontFile;
            return this;
        }

        /**
         * 접두사 폰트 경로를 설정한다.
         *
         * @param fontPath 폰트 파일 경로
         * @return this
         */
        public PageNumber prefixFont(Path fontPath) {
            this.prefixFontFile = fontPath != null ? fontPath.toFile() : null;
            return this;
        }

        /**
         * 접두사 PDFont 객체를 직접 설정한다.
         *
         * @param font PDFont 객체
         * @return this
         */
        public PageNumber prefixFont(PDFont font) {
            this.prefixFont = font;
            return this;
        }

        /**
         * 접미사 폰트 파일(TTF/OTF)을 설정한다.
         *
         * @param fontFile 폰트 파일
         * @return this
         */
        public PageNumber suffixFont(File fontFile) {
            this.suffixFontFile = fontFile;
            return this;
        }

        /**
         * 접미사 폰트 경로를 설정한다.
         *
         * @param fontPath 폰트 파일 경로
         * @return this
         */
        public PageNumber suffixFont(Path fontPath) {
            this.suffixFontFile = fontPath != null ? fontPath.toFile() : null;
            return this;
        }

        /**
         * 접미사 PDFont 객체를 직접 설정한다.
         *
         * @param font PDFont 객체
         * @return this
         */
        public PageNumber suffixFont(PDFont font) {
            this.suffixFont = font;
            return this;
        }

        /**
         * 동적 포맷터 함수((currentPage, totalPages) -> String)를 설정한다.
         *
         * @param customFormatProvider 현재 페이지와 전체 페이지 수를 받아 포맷팅된 문자열을 반환하는 함수
         * @return this
         */
        public PageNumber format(BiFunction<Integer, Integer, String> customFormatProvider) {
            this.customFormatProvider = customFormatProvider;
            return this;
        }

        /**
         * n, m, 구분자 또는 접두사/접미사에 개별 스타일이 적용되어 있는지 여부를 확인한다.
         *
         * @return 개별 스타일 적용 여부
         */
        public boolean hasDistinctStyles() {
            return currentPageFontSize != null || totalPageFontSize != null || separatorFontSize != null
                    || currentPageColor != null || totalPageColor != null || separatorColor != null
                    || currentPageBold != null || totalPageBold != null || separatorBold != null
                    || currentPageStrokeWidth != null || totalPageStrokeWidth != null || separatorStrokeWidth != null
                    || currentPageFont != null || totalPageFont != null || separatorFont != null
                    || currentPageFontFile != null || totalPageFontFile != null || separatorFontFile != null
                    || separator != null || prefix != null || suffix != null
                    || prefixColor != null || suffixColor != null
                    || prefixFontSize != null || suffixFontSize != null
                    || prefixBold != null || suffixBold != null
                    || prefixStrokeWidth != null || suffixStrokeWidth != null
                    || prefixFont != null || suffixFont != null
                    || prefixFontFile != null || suffixFontFile != null
                    || !showTotalPage;
        }

        public String getFormat() { return format; }
        public String getPrefix() { return prefix; }
        public String getSuffix() { return suffix; }
        public String getSeparator() { return separator; }
        public boolean isShowTotalPage() { return showTotalPage; }
        public BiFunction<Integer, Integer, String> getCustomFormatProvider() { return customFormatProvider; }

        public Float getCurrentPageFontSize() { return currentPageFontSize; }
        public Color getCurrentPageColor() { return currentPageColor; }
        public Float getCurrentPageStrokeWidth() { return currentPageStrokeWidth; }
        public Boolean isCurrentPageBold() { return currentPageBold; }
        public File getCurrentPageFontFile() { return currentPageFontFile; }
        public PDFont getCurrentPageFont() { return currentPageFont; }

        public Float getTotalPageFontSize() { return totalPageFontSize; }
        public Color getTotalPageColor() { return totalPageColor; }
        public Float getTotalPageStrokeWidth() { return totalPageStrokeWidth; }
        public Boolean isTotalPageBold() { return totalPageBold; }
        public File getTotalPageFontFile() { return totalPageFontFile; }
        public PDFont getTotalPageFont() { return totalPageFont; }

        public Float getSeparatorFontSize() { return separatorFontSize; }
        public Color getSeparatorColor() { return separatorColor; }
        public Float getSeparatorStrokeWidth() { return separatorStrokeWidth; }
        public Boolean isSeparatorBold() { return separatorBold; }
        public File getSeparatorFontFile() { return separatorFontFile; }
        public PDFont getSeparatorFont() { return separatorFont; }

        public Float getPrefixFontSize() { return prefixFontSize; }
        public Color getPrefixColor() { return prefixColor; }
        public Float getPrefixStrokeWidth() { return prefixStrokeWidth; }
        public Boolean isPrefixBold() { return prefixBold; }
        public File getPrefixFontFile() { return prefixFontFile; }
        public PDFont getPrefixFont() { return prefixFont; }

        public Float getSuffixFontSize() { return suffixFontSize; }
        public Color getSuffixColor() { return suffixColor; }
        public Float getSuffixStrokeWidth() { return suffixStrokeWidth; }
        public Boolean isSuffixBold() { return suffixBold; }
        public File getSuffixFontFile() { return suffixFontFile; }
        public PDFont getSuffixFont() { return suffixFont; }
    }

    /**
     * PDF 페이지에 표시할 이미지 스탬프 설정 (회사 로고, 전자 직인, 인증 마크, 서명 등).
     *
     * <p>9대 기준 위치({@link Position}) 및 상대 오프셋(pt), 불투명도(0.0~1.0), 회전 각도,
     * 크기(가로/세로 독립 또는 종횡비 유지), 대상 페이지 선택기({@link PageSelector})를 지원합니다.</p>
     */
    public static class ImageStamp extends BaseStampElement<ImageStamp> {
        private final byte[] imageBytes;
        private Float width;
        private Float height;
        private float rotation = 0f;

        protected ImageStamp(byte[] imageBytes) {
            super(Position.CENTER);
            if (imageBytes == null || imageBytes.length == 0) {
                throw new IllegalArgumentException("이미지 데이터가 비어 있습니다.");
            }
            this.imageBytes = imageBytes;
        }

        /**
         * 이미지 바이트 배열로부터 이미지 스탬프를 생성한다.
         *
         * @param imageBytes 이미지 파일 바이트 배열 (PNG, JPG 등)
         * @return ImageStamp 인스턴스
         */
        public static ImageStamp of(byte[] imageBytes) {
            return new ImageStamp(imageBytes == null ? null : imageBytes.clone());
        }

        /**
         * 이미지 파일({@link File})로부터 이미지 스탬프를 생성한다.
         *
         * @param file 이미지 파일
         * @return ImageStamp 인스턴스
         * @throws IOException 파일 읽기 실패 시
         */
        public static ImageStamp of(File file) throws IOException {
            return of(file.toPath());
        }

        /**
         * 이미지 파일 경로({@link Path})로부터 이미지 스탬프를 생성한다.
         *
         * @param path 이미지 파일 경로
         * @return ImageStamp 인스턴스
         * @throws IOException 파일 읽기 실패 시
         */
        public static ImageStamp of(Path path) throws IOException {
            return new ImageStamp(Files.readAllBytes(path));
        }

        /**
         * 이미지 입력 스트림({@link InputStream})으로부터 이미지 스탬프를 생성한다. (스트림은 읽은 후 자동 종료)
         *
         * @param inputStream 이미지 입력 스트림
         * @return ImageStamp 인스턴스
         * @throws IOException 스트림 읽기 실패 시
         */
        public static ImageStamp of(InputStream inputStream) throws IOException {
            try (inputStream) {
                return new ImageStamp(S2StreamUtil.streamToByteArray(inputStream, false, DEFAULT_MAX_SOURCE_BYTES));
            }
        }

        /**
         * 메모리 상의 {@link BufferedImage}로부터 이미지 스탬프를 생성한다.
         *
         * @param image BufferedImage 객체
         * @return ImageStamp 인스턴스
         * @throws IOException 이미지 인코딩 실패 시
         */
        public static ImageStamp of(BufferedImage image) throws IOException {
            try (var baos = new ByteArrayOutputStream()) {
                ImageIO.write(image, "PNG", baos);
                return new ImageStamp(baos.toByteArray());
            }
        }

        /**
         * 이미지의 가로·세로 크기(pt)를 모두 지정한다. (원본 종횡비가 변경될 수 있음)
         *
         * @param width  가로 크기 (pt)
         * @param height 세로 크기 (pt)
         * @return this
         */
        public ImageStamp size(float width, float height) {
            if (width <= 0f || height <= 0f) {
                throw new IllegalArgumentException("이미지 크기는 0보다 커야 합니다.");
            }
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * 이미지의 가로 크기(pt)만 지정한다. 세로 크기는 원본 비율에 맞춰 자동 조정된다.
         *
         * @param width 가로 크기 (pt)
         * @return this
         */
        public ImageStamp width(float width) {
            if (width <= 0f) {
                throw new IllegalArgumentException("이미지 너비는 0보다 커야 합니다.");
            }
            this.width = width;
            this.height = null;
            return this;
        }

        /**
         * 이미지의 세로 크기(pt)만 지정한다. 가로 크기는 원본 비율에 맞춰 자동 조정된다.
         *
         * @param height 세로 크기 (pt)
         * @return this
         */
        public ImageStamp height(float height) {
            if (height <= 0f) {
                throw new IllegalArgumentException("이미지 높이는 0보다 커야 합니다.");
            }
            this.height = height;
            this.width = null;
            return this;
        }

        /**
         * 이미지 회전 각도를 설정한다. (도 단위, 시계 반대 방향 회전, 기본값: 0도)
         *
         * @param rotation 회전 각도 (도)
         * @return this
         */
        public ImageStamp rotation(float rotation) {
            this.rotation = rotation;
            return this;
        }

        /**
         * 원본 이미지 크기 대비 실제로 그려질 너비와 높이를 계산한다.
         *
         * @param naturalWidth  원본 가로 폭
         * @param naturalHeight 원본 세로 높이
         * @return float[2] {width, height}
         */
        public float[] drawSize(float naturalWidth, float naturalHeight) {
            if (width != null && height != null) {
                return new float[] { width, height };
            }
            if (width != null) {
                return new float[] { width, width * naturalHeight / naturalWidth };
            }
            if (height != null) {
                return new float[] { height * naturalWidth / naturalHeight, height };
            }
            return new float[] { naturalWidth, naturalHeight };
        }

        public byte[] getImageBytes() {
            return imageBytes == null ? null : imageBytes.clone();
        }

        public Float getWidth() { return width; }
        public Float getHeight() { return height; }
        public float getRotation() { return rotation; }
    }

    /**
     * PDF 페이지에 표시할 QR 코드 스탬프 설정 (진위확인 URL, 문서 고유 식별자, 인증 링크 등).
     *
     * <p>Nayuki QR Code Generator를 내장하여 순수 Java(Zero Dependency, MIT License)로 고품질 QR 이미지를 동적 생성하며,
     * 위치, 크기(pt 단위), 오프셋, 불투명도, 전경/배경 색상, 오류 복원율, 페이지 선택기({@link PageSelector})를 지원합니다.</p>
     */
    public static class QrStamp extends BaseStampElement<QrStamp> {
        private final String content;
        private float size = 100f; // PDF 상 표시 크기 (pt)
        private Float height;      // 지정되지 않으면 size와 동일 (정사각형)
        private int resolution = 300; // QR 비트맵 해상도 픽셀
        private QrCode.Ecc ecc = QrCode.Ecc.MEDIUM;
        private Color darkColor = Color.BLACK;
        private Color lightColor = Color.WHITE;
        private float rotation = 0f;

        protected QrStamp(String content) {
            super(Position.BOTTOM_RIGHT);
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException("QR 코드 내용(content)은 비어 있을 수 없습니다.");
            }
            this.content = content;
        }

        /**
         * 일반 문자열 또는 식별값으로 QR 코드 스탬프를 생성한다.
         *
         * @param content QR 코드에 담을 내용
         * @return QrStamp 인스턴스
         */
        public static QrStamp of(String content) {
            return new QrStamp(content);
        }

        /**
         * 진위확인 또는 웹 링크 URL로 QR 코드 스탬프를 생성한다.
         *
         * @param url QR 코드에 담을 URL
         * @return QrStamp 인스턴스
         */
        public static QrStamp ofUrl(String url) {
            return new QrStamp(url);
        }

        /**
         * 텍스트 본문으로 QR 코드 스탬프를 생성한다.
         *
         * @param text QR 코드에 담을 텍스트
         * @return QrStamp 인스턴스
         */
        public static QrStamp ofText(String text) {
            return new QrStamp(text);
        }

        /**
         * PDF 상에 표시될 QR 코드의 한 변 크기(pt, 정사각형)를 설정한다. (기본값: 100pt)
         *
         * @param size 한 변의 크기 (pt)
         * @return this
         */
        public QrStamp size(float size) {
            if (size <= 0f) throw new IllegalArgumentException("크기는 0보다 커야 합니다.");
            this.size = size;
            this.height = size;
            return this;
        }

        /**
         * PDF 상에 표시될 QR 코드의 가로 및 세로 크기(pt)를 설정한다.
         *
         * @param width  가로 크기 (pt)
         * @param height 세로 크기 (pt)
         * @return this
         */
        public QrStamp size(float width, float height) {
            if (width <= 0f || height <= 0f) throw new IllegalArgumentException("크기는 0보다 커야 합니다.");
            this.size = width;
            this.height = height;
            return this;
        }

        /**
         * 생성할 QR 비트맵 이미지의 해상도(픽셀)를 설정한다. (기본값: 300px, 32~4096)
         *
         * @param pixelSize 비트맵 해상도 (픽셀)
         * @return this
         */
        public QrStamp resolution(int pixelSize) {
            if (pixelSize < 32 || pixelSize > 4096) {
                throw new IllegalArgumentException("해상도는 32~4096 픽셀 범위여야 합니다: " + pixelSize);
            }
            this.resolution = pixelSize;
            return this;
        }

        /**
         * QR 코드 오류 복원 수준(Error Correction Level)을 설정한다. (기본값: {@link QrCode.Ecc#MEDIUM})
         *
         * @param ecc 오류 복원 레벨 (LOW, MEDIUM, QUARTILE, HIGH)
         * @return this
         */
        public QrStamp errorCorrection(QrCode.Ecc ecc) {
            if (ecc != null) this.ecc = ecc;
            return this;
        }

        /**
         * QR 코드의 전경색(어두운 모듈)과 배경색(밝은 모듈)을 설정한다. (기본값: 검정 / 흰색)
         *
         * @param darkColor  전경 색상
         * @param lightColor 배경 색상
         * @return this
         */
        public QrStamp colors(Color darkColor, Color lightColor) {
            if (darkColor != null) this.darkColor = darkColor;
            if (lightColor != null) this.lightColor = lightColor;
            return this;
        }

        /**
         * QR 코드 회전 각도를 설정한다. (도 단위, 시계 반대 방향 회전, 기본값: 0도)
         *
         * @param rotation 회전 각도 (도)
         * @return this
         */
        public QrStamp rotation(float rotation) {
            this.rotation = rotation;
            return this;
        }

        /**
         * QR 코드 비트맵 이미지를 동적 렌더링하여 PNG 바이트 배열로 생성한다.
         *
         * @return PNG 이미지 바이트 배열
         * @throws IOException 이미지 인코딩 실패 시
         */
        public byte[] generateImageBytes() throws IOException {
            var qr = QrCode.encodeText(content, ecc);
            int qrModules = qr.size;
            int border = 4; // QR 표준 Quiet zone
            int totalModules = qrModules + border * 2;
            int scale = Math.max(1, resolution / totalModules);
            int imgSize = totalModules * scale;

            var image = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);
            int darkRgb = darkColor.getRGB();
            int lightRgb = lightColor.getRGB();

            for (int y = 0; y < imgSize; y++) {
                for (int x = 0; x < imgSize; x++) {
                    int moduleX = (x / scale) - border;
                    int moduleY = (y / scale) - border;
                    boolean isDark = moduleX >= 0 && moduleX < qrModules && moduleY >= 0 && moduleY < qrModules && qr.getModule(moduleX, moduleY);
                    image.setRGB(x, y, isDark ? darkRgb : lightRgb);
                }
            }

            try (var baos = new ByteArrayOutputStream()) {
                ImageIO.write(image, "PNG", baos);
                return baos.toByteArray();
            }
        }

        public String getContent() { return content; }
        public float getSize() { return size; }
        public float getDisplayWidth() { return size; }
        public float getDisplayHeight() { return height != null ? height : size; }
        public int getResolution() { return resolution; }
        public QrCode.Ecc getEcc() { return ecc; }
        public Color getDarkColor() { return darkColor; }
        public Color getLightColor() { return lightColor; }
        public float getRotation() { return rotation; }
    }

    /**
     * PDF 스탬프 종합 옵션 (페이지 번호, 텍스트 목록, 이미지 목록, QR 목록, 워터마크, 적용 대상 페이지 수 등).
     *
     * <p>빌더({@link #builder()})를 통해 필요한 스탬프 요소만 선택적으로 조합하여 생성할 수 있습니다.</p>
     */
    public static class StampOptions {
        private Integer numberOfPagesToApply;
        private PageNumber pageNumber;
        private final List<Text> texts = new ArrayList<>();
        private final List<ImageStamp> images = new ArrayList<>();
        private final List<QrStamp> qrCodes = new ArrayList<>();
        private Watermark watermark;

        public static StampOptions create() {
            return new StampOptions();
        }

        public static Builder builder() {
            return new Builder();
        }

        public Integer getNumberOfPagesToApply() { return numberOfPagesToApply; }
        public void setNumberOfPagesToApply(Integer numberOfPagesToApply) { this.numberOfPagesToApply = numberOfPagesToApply; }
        public PageNumber getPageNumber() { return pageNumber; }
        public void setPageNumber(PageNumber pageNumber) { this.pageNumber = pageNumber; }
        public List<Text> getTexts() { return texts; }
        public List<ImageStamp> getImages() { return images; }
        public List<QrStamp> getQrCodes() { return qrCodes; }
        public Watermark getWatermark() { return watermark; }
        public void setWatermark(Watermark watermark) { this.watermark = watermark; }

        /**
         * {@link StampOptions} 생성을 위한 빌더 클래스.
         */
        public static class Builder {
            private final StampOptions options = new StampOptions();

            /**
             * 스탬프를 적용할 페이지 수를 설정한다.
             *
             * @param numberOfPagesToApply 적용할 페이지 수 (null이면 전체 페이지 적용)
             * @return this
             */
            public Builder numberOfPagesToApply(Integer numberOfPagesToApply) {
                options.numberOfPagesToApply = numberOfPagesToApply;
                return this;
            }

            /**
             * 페이지 번호 스탬프를 설정한다.
             *
             * @param pageNumber 페이지 번호 설정 객체
             * @return this
             */
            public Builder pageNumber(PageNumber pageNumber) {
                options.pageNumber = pageNumber;
                return this;
            }

            /**
             * 텍스트 스탬프(제목, 헤더, 푸터, 보안문구 등)를 추가한다.
             *
             * @param text 추가할 텍스트 스탬프 설정 객체
             * @return this
             */
            public Builder addText(Text text) {
                if (text != null) {
                    options.texts.add(text);
                }
                return this;
            }

            /**
             * 이미지 스탬프(인증 마크, 직인, 로고 등)를 추가한다.
             *
             * @param image 추가할 이미지 스탬프 설정 객체
             * @return this
             */
            public Builder addImage(ImageStamp image) {
                if (image != null) {
                    options.images.add(image);
                }
                return this;
            }

            /**
             * QR 코드 스탬프(진위확인 URL, 문서 고유 코드 등)를 추가한다.
             *
             * @param qr 추가할 QR 코드 스탬프 설정 객체
             * @return this
             */
            public Builder addQr(QrStamp qr) {
                if (qr != null) {
                    options.qrCodes.add(qr);
                }
                return this;
            }

            /**
             * 이미지 워터마크를 설정한다.
             *
             * @param watermark 이미지 워터마크 설정 객체
             * @return this
             */
            public Builder watermark(Watermark watermark) {
                options.watermark = watermark;
                return this;
            }

            /**
             * 구성된 {@link StampOptions} 인스턴스를 반환한다.
             *
             * @return StampOptions 인스턴스
             */
            public StampOptions build() {
                return options;
            }
        }
    }


    /**
     * An image stamped on every page: size (both sides, or one side with the other kept in ratio), one of nine
     * positions, an offset and an opacity. Lengths are in points (1/72 inch; A4 is 595 x 842).
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 모든 쪽에 찍는 이미지. 크기(가로·세로 둘 다, 또는 한쪽만 정하고 나머지는 비율대로), 9 곳 중 위치, 간격, 불투명도를 정한다. 길이 단위는 pt(1/72 인치,
     * A4 는 595 x 842, 1mm 는 약 2.83pt).
     *
     * <pre>{@code
     * var mark = Watermark.of(Path.of("logo.png")).width(200).position(Watermark.Position.CENTER).opacity(0.2f);
     * var stamp = Watermark.of(Path.of("seal.png")).size(60, 60).position(Watermark.Position.BOTTOM_RIGHT).offset(40, 40);
     * S2PdfUtil.stamp(Path.of("report.pdf"), StampOptions.builder().watermark(mark).build());
     * }</pre>
     */
    public static final class Watermark {

        /**
         * 워터마크 기준 위치. 가장자리 기준이면 {@link #offset}만큼 안쪽으로 들어오고, 가운데 기준인 축은 가운데에서 x 는 오른쪽, y 는 아래쪽으로 옮긴다.
         */
        public enum Position {
            TOP_LEFT, TOP, TOP_CENTER, TOP_RIGHT, LEFT, CENTER_LEFT, CENTER, RIGHT, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_CENTER, BOTTOM_RIGHT
        }

        final byte[] image; // read by S2PdfEngine | S2PdfEngine 이 읽음
        private Float width;
        private Float height;
        private Position position = Position.CENTER;
        private float offsetX;
        private float offsetY;
        float opacity = 1f; // read by S2PdfEngine | S2PdfEngine 이 읽음
        private float rotation = 0f;
        private PageSelector pageSelector;

        private Watermark(byte[] image) {
            if (image == null || image.length == 0) {
                throw new IllegalArgumentException("워터마크 이미지가 비었습니다.");
            }
            this.image = image;
        }

        /**
         * @param image 이미지 파일 (PNG, JPG, GIF, BMP, WebP 는 imageio-webp 추가 시)
         * @return 워터마크 (가운데, 원래 크기, 불투명)
         * @throws IOException 파일을 읽을 수 없을 때
         */
        public static Watermark of(File image) throws IOException {
            return of(image.toPath());
        }

        /**
         * @param image 이미지 파일 (PNG, JPG, GIF, BMP, WebP 는 imageio-webp 추가 시). PNG 의 투명 부분은 그대로 유지된다
         * @return 워터마크 (가운데, 원래 크기, 불투명)
         * @throws IOException 파일을 읽을 수 없을 때
         */
        public static Watermark of(Path image) throws IOException {
            return new Watermark(Files.readAllBytes(image));
        }

        /**
         * @param image 이미지 바이트
         * @return 워터마크
         */
        public static Watermark of(byte[] image) {
            return new Watermark(image == null ? null : image.clone());
        }

        /**
         * @param image 이미지 스트림 (읽은 뒤 닫음, 최대 {@value #MAX_IMAGE_BYTES} 바이트)
         * @return 워터마크
         * @throws IOException 읽을 수 없을 때
         */
        public static Watermark of(InputStream image) throws IOException {
            try (image) {
                return new Watermark(S2StreamUtil.streamToByteArray(image, false, MAX_IMAGE_BYTES));
            } catch (S2RuntimeException e) {
                throw new IOException("워터마크 이미지를 읽을 수 없습니다: " + e.getMessage(), e);
            }
        }

        /** Largest watermark image read from a stream (20MB) | 스트림으로 받는 워터마크 이미지 최대 크기 */
        public static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;

        /**
         * 가로·세로를 모두 정한다 (비율이 달라질 수 있음).
         *
         * @param width  가로 (pt)
         * @param height 세로 (pt)
         * @return 이 워터마크
         */
        public Watermark size(float width, float height) {
            this.width = positive(width, "가로");
            this.height = positive(height, "세로");
            return this;
        }

        /**
         * 가로만 정한다. 세로는 이미지 비율대로.
         *
         * @param width 가로 (pt)
         * @return 이 워터마크
         */
        public Watermark width(float width) {
            this.width = positive(width, "가로");
            this.height = null;
            return this;
        }

        /**
         * 세로만 정한다. 가로는 이미지 비율대로.
         *
         * @param height 세로 (pt)
         * @return 이 워터마크
         */
        public Watermark height(float height) {
            this.height = positive(height, "세로");
            this.width = null;
            return this;
        }

        /**
         * @param position 기준 위치 (기본 {@link Position#CENTER})
         * @return 이 워터마크
         */
        public Watermark position(Position position) {
            this.position = Objects.requireNonNull(position, "position");
            return this;
        }

        /**
         * @param position 기준 위치 (기본 {@link Position#CENTER})
         * @return 이 워터마크
         */
        public Watermark position(S2PdfUtil.Position position) {
            Objects.requireNonNull(position, "position");
            this.position = Position.valueOf(position.name());
            return this;
        }

        /**
         * 기준 위치에서 옮길 거리. 왼쪽·오른쪽 기준이면 x 만큼, 위·아래 기준이면 y 만큼 그 가장자리에서 안쪽으로 들어온다. 가운데 기준인 축은 x 는 오른쪽,
         * y 는 아래쪽으로 옮긴다 (음수는 반대 방향).
         *
         * @param x 가로 거리 (pt)
         * @param y 세로 거리 (pt)
         * @return 이 워터마크
         */
        public Watermark offset(float x, float y) {
            this.offsetX = x;
            this.offsetY = y;
            return this;
        }

        /**
         * @param opacity 불투명도 0 (투명) ~ 1 (불투명, 기본)
         * @return 이 워터마크
         */
        public Watermark opacity(float opacity) {
            if (!(opacity >= 0f && opacity <= 1f)) {
                throw new IllegalArgumentException("불투명도는 0 ~ 1 이어야 합니다: " + opacity);
            }
            this.opacity = opacity;
            return this;
        }

        /**
         * 워터마크를 적용할 페이지를 지정한다. (null이면 전체 페이지 적용)
         *
         * @param pageSelector 페이지 선택기
         * @return 이 워터마크
         */
        public Watermark pages(PageSelector pageSelector) {
            this.pageSelector = pageSelector;
            return this;
        }

        /**
         * 워터마크 회전 각도를 설정한다. (도 단위, 시계 반대 방향 회전, 기본값: 0도)
         *
         * @param rotation 회전 각도 (도)
         * @return 이 워터마크
         */
        public Watermark rotation(float rotation) {
            this.rotation = rotation;
            return this;
        }

        private static float positive(float value, String name) {
            if (!(value > 0f) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("워터마크 " + name + " 크기는 0 보다 커야 합니다: " + value);
            }
            return value;
        }

        /** The drawn size: both given, or one side with the other in ratio, or the image's own size | 그릴 크기 */
        float[] drawSize(float imageWidth, float imageHeight) {
            if (width != null && height != null) {
                return new float[] { width, height };
            }
            if (width != null) {
                return new float[] { width, width * imageHeight / imageWidth };
            }
            if (height != null) {
                return new float[] { height * imageWidth / imageHeight, height };
            }
            return new float[] { imageWidth, imageHeight };
        }

        /** Lower-left corner on a page box | 쪽 영역에서의 왼쪽 아래 좌표 */
        float[] origin(PDRectangle box, float w, float h) {
            var name = position.name();
            float x;
            if (name.endsWith("LEFT")) {
                x = box.getLowerLeftX() + offsetX;
            } else if (name.endsWith("RIGHT")) {
                x = box.getUpperRightX() - w - offsetX;
            } else {
                x = box.getLowerLeftX() + (box.getWidth() - w) / 2 + offsetX;
            }
            float y;
            if (name.startsWith("TOP")) {
                y = box.getUpperRightY() - h - offsetY;
            } else if (name.startsWith("BOTTOM")) {
                y = box.getLowerLeftY() + offsetY;
            } else {
                y = box.getLowerLeftY() + (box.getHeight() - h) / 2 - offsetY;
            }
            return new float[] { x, y };
        }

        public byte[] getImageBytes() {
            return image == null ? null : image.clone();
        }

        public Float getWidth() {
            return width;
        }

        public Float getHeight() {
            return height;
        }

        public Position getPosition() {
            return position;
        }

        public float getOffsetX() {
            return offsetX;
        }

        public float getOffsetY() {
            return offsetY;
        }

        public float getOpacity() {
            return opacity;
        }

        public float getRotation() {
            return rotation;
        }

        public PageSelector getPageSelector() {
            return pageSelector;
        }

        public boolean appliesToPage(int pageIndex, int totalPages) {
            return pageSelector == null || pageSelector.includes(pageIndex, totalPages);
        }
    }
}
