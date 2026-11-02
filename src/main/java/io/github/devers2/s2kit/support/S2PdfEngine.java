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
import io.github.devers2.s2kit.support.S2PdfUtil.FontSource;
import io.github.devers2.s2kit.support.S2PdfUtil.ImageStamp;
import io.github.devers2.s2kit.support.S2PdfUtil.PageNumber;
import io.github.devers2.s2kit.support.S2PdfUtil.PageSelector;
import io.github.devers2.s2kit.support.S2PdfUtil.Position;
import io.github.devers2.s2kit.support.S2PdfUtil.QrStamp;
import io.github.devers2.s2kit.support.S2PdfUtil.StampOptions;
import io.github.devers2.s2kit.support.S2PdfUtil.Text;
import io.github.devers2.s2kit.support.S2PdfUtil.TextAlign;
import io.github.devers2.s2kit.support.S2PdfUtil.Watermark;
import io.github.devers2.s2kit.support.S2PdfUtil.BaseTextElement;
import io.github.devers2.s2kit.support.S2PdfUtil.BaseStampElement;

/**
 * The PDFBox work behind {@link S2PdfUtil}. Kept apart so that {@code S2PdfUtil} can check for the optional PDFBox
 * dependency and report it clearly: the JVM verifies a class as a whole and fails with a bare
 * {@code NoClassDefFoundError} when a type it needs is missing. Not public API; call {@link S2PdfUtil}.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * {@link S2PdfUtil} 뒤에서 PDFBox 작업을 하는 클래스. JVM 은 클래스를 통째로 검증하며 필요한 타입이 없으면 안내 없이
 * {@code NoClassDefFoundError}를 내므로, {@code S2PdfUtil}이 선택 의존성 PDFBox 를 먼저 확인해 알기 쉽게 알릴 수 있도록 따로 둔다.
 * 공개 API 가 아니며 {@link S2PdfUtil}을 쓴다.
 */
final class S2PdfEngine {

    private static final S2Logger logger = S2LogManager.getLogger(S2PdfUtil.class);

    private S2PdfEngine() {
    }




    // ------------------------------------------------------------------------
    // Fonts | 폰트


    /**
     * The built-in PDF fonts have no Korean glyphs, so without a font every such character would silently become '#'
     * | 기본 PDF 폰트에는 한글 글리프가 없어, 폰트 없이는 모든 글자가 조용히 '#'으로 바뀜
     *
     * @param content 그릴 내용 (한글·CJK 가 있는지 본다)
     * @param hasFont 한글 폰트를 찾았는지
     * @throws IOException 한글이 있는데 폰트가 없을 때 (설정 방법 안내)
     */
    static void requireFontFor(String content, boolean hasFont) throws IOException {
        if (!hasFont && content != null && HANGUL_OR_CJK.matcher(content).find()) {
            throw new IOException("한글(CJK)을 표시할 폰트가 없습니다. S2PdfUtil.setDefaultFont(Path)로 한글 TTF 폰트를 지정하거나, "
                    + "서버에 한글 폰트(예: fonts-nanum 의 NanumGothic.ttf)를 설치하십시오. 자동 탐색 경로: " + S2PdfUtil.SYSTEM_FONT_CANDIDATES);
        }
    }

    /** Hangul (Jamo, compatibility Jamo, syllables) and CJK ideographs/kana | 한글과 CJK 문자 */
    private static final Pattern HANGUL_OR_CJK = Pattern
            .compile("[\\u1100-\\u11FF\\u3040-\\u30FF\\u3130-\\u318F\\u4E00-\\u9FFF\\uAC00-\\uD7A3]");


    private static volatile FontSource defaultFont;

    /** Looked up once, on first use without a configured font | 폰트 미지정으로 처음 쓸 때 한 번만 탐색 */
    private static final class SystemFont {
        static final FontSource FONT = detect();

        private static FontSource detect() {
            for (var candidate : S2PdfUtil.SYSTEM_FONT_CANDIDATES) {
                var path = Path.of(candidate);
                try {
                    if (Files.isReadable(path)) {
                        logger.info("PDF 기본 폰트로 시스템 폰트를 사용합니다: {}", path);
                        return new FontSource(candidate, Files.readAllBytes(path));
                    }
                } catch (IOException | RuntimeException e) {
                    logger.warn("시스템 폰트를 읽지 못했습니다: {} ({})", path, e.getMessage());
                }
            }
            return null;
        }
    }

    /**
     * 폰트를 지정하지 않은 스탬프·쪽 번호(그리고 이 폰트를 함께 쓰는 HTML 렌더링 모듈)에 쓸 기본 폰트를 정한다. 한글을 쓰려면 한글 글리프가 있는 TrueType(.ttf) 폰트를
     * 지정한다. 지정하지 않으면 서버에 설치된 한글 폰트({@link #S2PdfUtil.SYSTEM_FONT_CANDIDATES})를 자동으로 찾는다.
     *
     * @param ttfFile TrueType 폰트 파일
     * @throws IOException 파일을 읽을 수 없을 때
     */
    static void setDefaultFont(Path ttfFile) throws IOException {
        var bytes = Files.readAllBytes(Objects.requireNonNull(ttfFile, "ttfFile"));
        if (bytes.length == 0) {
            throw new IOException("빈 폰트 파일입니다: " + ttfFile);
        }
        defaultFont = new FontSource(ttfFile.toString(), bytes);
    }

    /**
     * 클래스패스의 TrueType 폰트를 기본 폰트로 정한다 ({@link #setDefaultFont(Path)} 참고).
     *
     * @param clazz        리소스를 읽을 기준 클래스
     * @param resourcePath 클래스패스 경로 (예: {@code /fonts/NanumGothic.ttf})
     * @throws IOException 리소스가 없거나 읽을 수 없을 때
     */
    static void setDefaultFont(Class<?> clazz, String resourcePath) throws IOException {
        defaultFont = loadClasspathFont(Objects.requireNonNull(clazz, "clazz"), resourcePath);
    }

    /** 기본 폰트 설정을 지우고 시스템 폰트 자동 탐색으로 되돌린다. */
    static void resetDefaultFont() {
        defaultFont = null;
    }

    private static FontSource loadClasspathFont(Class<?> clazz, String resourcePath) throws IOException {
        try (var in = clazz.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("폰트 리소스를 찾을 수 없습니다: " + resourcePath + " (기준 클래스: " + clazz.getName() + ")");
            }
            return new FontSource(resourcePath, in.readAllBytes());
        }
    }

    /**
     * Source font, then the default font, then an installed Korean font | 소스 폰트 → 기본 폰트 → 설치된 한글 폰트
     *
     * @param clazz    클래스패스 폰트를 읽을 기준 클래스 (null 이면 S2PdfUtil)
     * @param fontPath 클래스패스 폰트 경로 (비면 기본 폰트, 그다음 설치된 한글 폰트)
     * @return 찾은 폰트, 없으면 null
     * @throws IOException 지정한 폰트 리소스를 읽을 수 없을 때
     */
    static FontSource resolveFont(Class<?> clazz, String fontPath) throws IOException {
        if (S2Util.isNotEmpty(fontPath)) {
            return loadClasspathFont(clazz != null ? clazz : S2PdfUtil.class, fontPath);
        }
        var configured = defaultFont;
        return configured != null ? configured : SystemFont.FONT;
    }

    /** Image MIME type of a file name, or null when it is not an image | 파일명의 이미지 MIME 타입 (이미지가 아니면 null) */
    private static String getMimeType(String fileName) {
        var mime = S2FileUtil.getMimeTypeByExtension(fileName);
        return mime.startsWith("image/") ? mime : null;
    }

    // ========================================================================
    // PDF 페이지 수 조회 (Page Count)
    // ========================================================================

    /**
     * Gets the total page count of a PDF file.
     * <p>
     * PDF 파일의 총 페이지 수를 반환합니다.
     * {@code try-with-resources}를 통해 {@link PDDocument}를 안전하게 닫아 메모리 및 파일 핸들 누수를 방지합니다.
     *
     * @param pdfFile PDF file path | 페이지 수를 확인할 PDF 파일 경로 (Path)
     * @return Total page count, or 0 if null or loading fails | 총 페이지 수 (null이거나 로드 실패 시 0)
     */
    static int getPageCount(Path pdfFile) {
        if (pdfFile == null) {
            return 0;
        }
        return getPageCount(pdfFile.toFile());
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
    static int getPageCount(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) {
            return 0;
        }
        try (var document = Loader.loadPDF(pdfFile)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            logger.error("PDF 페이지 수 조회 오류[File]: ", e);
            return 0;
        }
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
    static int getPageCount(InputStream pdfData, boolean shouldCloseStream) {
        if (pdfData == null) {
            return 0;
        }
        final var pageCount = new int[]{0};
        try {
            S2FileUtil.processStreamWithTempFile(pdfData, null, tempFile -> {
                try (var document = Loader.loadPDF(tempFile.toFile())) {
                    if (document != null) {
                        pageCount[0] = document.getNumberOfPages();
                    }
                } catch (IOException e) {
                    logger.error("PDF 페이지 수 조회 오류[InputStream1]: ", e);
                }
                return null;
            }, shouldCloseStream);
        } catch (IOException e) {
            logger.error("PDF 페이지 수 조회 오류[InputStream2]: ", e);
        }
        return pageCount[0];
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
    static int getPageCount(InputStream pdfData) {
        return getPageCount(pdfData, true);
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
    static int getPageCount(byte[] pdfData) {
        if (pdfData == null || pdfData.length == 0) {
            return 0;
        }
        try (var document = Loader.loadPDF(pdfData)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            logger.error("PDF 페이지 수 조회 오류[byteArray]: ", e);
            return 0;
        }
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
    static boolean pdfToImage(Path pdfFile, int pageNo, Path imagePath, Integer dpi, String imageFormat) {
        var hasNextPage = false;
        try (var document = Loader.loadPDF(pdfFile.toFile())) {
            hasNextPage = pdfToImage(document, pageNo, imagePath, dpi, imageFormat);
        } catch (IOException e) {
            logger.error("PDF to Image 변환 오류[Path]: ", e);
        }
        return hasNextPage;
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
    static boolean pdfToImage(InputStream pdfData, int pageNo, Path imagePath, Integer dpi, String imageFormat,
            boolean shouldCloseStream) {
        var hasNextPage = new AtomicBoolean(false);
        try {
            S2FileUtil.processStreamWithTempFile(pdfData, null, tempFile -> {
                try (var document = Loader.loadPDF(tempFile.toFile())) {
                    hasNextPage.set(pdfToImage(document, pageNo, imagePath, dpi, imageFormat));
                } catch (IOException e) {
                    logger.error("PDF to Image 변환 오류[InputStream1]: ", e);
                }

                return null;
            }, shouldCloseStream);
        } catch (IOException e) {
            logger.error("PDF to Image 변환 오류[InputStream2]: ", e);
        }
        return hasNextPage.get();
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
    static boolean pdfToImage(byte[] pdfData, int pageNo, Path imagePath, Integer dpi, String imageFormat) {
        var hasNextPage = false;
        try (var document = Loader.loadPDF(pdfData)) {
            hasNextPage = pdfToImage(document, pageNo, imagePath, dpi, imageFormat);
        } catch (IOException e) {
            logger.error("PDF to Image 변환 오류[byteArray]: ", e);
        }
        return hasNextPage;
    }

    /**
     * PDF 를 이미지로 변환한다.
     *
     * @param document    PDF 문서
     * @param pageNo      변환할 페이지 번호
     * @param imagePath   변환 결과 이미지 경로
     * @param dpi         변환 이미지 해상도
     * @param imageFormat 변환 이미지 포멧(확장자)
     * @return 변환한 페이지의 다음 페이지가 존재하는지 여부
     * @throws IOException IOException
     */
    private static boolean pdfToImage(PDDocument document, int pageNo, Path imagePath, Integer dpi, String imageFormat)
            throws IOException {
        var hasNextPage = false;
        if (document == null) {
            throw new S2RuntimeException("[pdfToImage] PDF Document is null.");
        }

        var totalPages = document.getNumberOfPages();

        if (pageNo < 1) {
            pageNo = 1;
        } else if (pageNo > totalPages) {
            pageNo = totalPages;
        }

        if (pageNo < totalPages) {
            hasNextPage = true;
        }

        var pdfRenderer = new PDFRenderer(document);
        var image = pdfRenderer.renderImageWithDPI(pageNo - 1, dpi == null ? 300 : dpi, ImageType.RGB);
        ImageIO.write(
                image, !"jpeg".equalsIgnoreCase(imageFormat) && !"jpg".equalsIgnoreCase(imageFormat)
                        ? "png"
                        : imageFormat,
                imagePath.toFile());
        return hasNextPage;
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
    static InputStream pdfToImage(Path pdfFile, int pageNo, Integer dpi, String imageFormat,
            BiConsumer<Boolean, Integer> hasNextPageConsumer) {
        InputStream result = null;
        try (var document = Loader.loadPDF(pdfFile.toFile())) {
            result = pdfToImage(document, pageNo, dpi, imageFormat, hasNextPageConsumer);
        } catch (IOException e) {
            logger.error("PDF to Image InputStream 변환 오류[Path]: ", e);
        }
        return result;
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
    static InputStream pdfToImage(InputStream pdfData, int pageNo, Integer dpi, String imageFormat,
            BiConsumer<Boolean, Integer> hasNextPageConsumer, boolean shouldCloseStream) {
        var result = new AtomicReference<InputStream>();
        try {
            S2FileUtil.processStreamWithTempFile(pdfData, null, tempFile -> {
                try (var document = Loader.loadPDF(tempFile.toFile())) {
                    result.set(pdfToImage(document, pageNo, dpi, imageFormat, hasNextPageConsumer));
                } catch (IOException e) {
                    logger.error("PDF to Image InputStream 변환 오류[InputStream1]: ", e);
                }

                return null;
            }, shouldCloseStream);
        } catch (IOException e) {
            logger.error("PDF to Image InputStream 변환 오류[InputStream2]: ", e);
        }
        return result.get();
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
    static InputStream pdfToImage(byte[] pdfData, int pageNo, Integer dpi, String imageFormat,
            BiConsumer<Boolean, Integer> hasNextPageConsumer) {
        InputStream result = null;
        try (var document = Loader.loadPDF(pdfData)) {
            result = pdfToImage(document, pageNo, dpi, imageFormat, hasNextPageConsumer);
        } catch (IOException e) {
            logger.error("PDF to Image InputStream 변환 오류[byteArray]: ", e);
        }
        return result;
    }

    /**
     * PDF 를 이미지로 변환한다.
     *
     * @param document            PDF 문서
     * @param pageNo              변환할 페이지 번호
     * @param dpi                 변환 이미지 해상도
     * @param imageFormat         변환 이미지 포멧(확장자)
     * @param hasNextPageConsumer 함수형 인터페이스(변환한 페이지의 다음 페이지가 존재하는지 여부)
     * @return 변환한 이미지 InputStream
     * @throws IOException IOException
     */
    private static InputStream pdfToImage(PDDocument document, int pageNo, Integer dpi, String imageFormat,
            BiConsumer<Boolean, Integer> hasNextPageConsumer) throws IOException {
        InputStream result = null;
        var hasNextPage = false;
        if (document == null) {
            throw new S2RuntimeException("[pdfToImage InputStream] PDF Document is null.");
        }

        var totalPages = document.getNumberOfPages();

        if (pageNo < 1) {
            pageNo = 1;
        } else if (pageNo > totalPages) {
            pageNo = totalPages;
        }

        if (pageNo < totalPages) {
            hasNextPage = true;
        }

        var pdfRenderer = new PDFRenderer(document);
        var image = pdfRenderer.renderImageWithDPI(pageNo - 1, dpi == null ? 300 : dpi, ImageType.RGB);

        try (var outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(
                    image, !"jpeg".equalsIgnoreCase(imageFormat) && !"jpg".equalsIgnoreCase(imageFormat)
                            ? "png"
                            : imageFormat,
                    outputStream);
            result = new ByteArrayInputStream(outputStream.toByteArray());
        }

        if (hasNextPageConsumer != null) {
            // BiConsumer 호출
            hasNextPageConsumer.accept(hasNextPage, totalPages);
        }

        return result;
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
    static InputStream addPageNumbers(Path pdfFile, int numberOfPagesToInsert, Integer fontSize, File fontFile)
            throws IOException {
        try (var document = Loader.loadPDF(pdfFile.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            return addPageNumbers(document, numberOfPagesToInsert, fontSize,
                    fontFile != null ? PDType0Font.load(document, fontFile) : null);
        }
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
    static InputStream addPageNumbers(InputStream pdfData, int numberOfPagesToInsert, Integer fontSize,
            File fontFile, boolean shouldCloseStream) throws IOException {
        try {
            return S2FileUtil.processStreamWithTempFile(pdfData, null, tempFile -> {
                try {
                    return addPageNumbers(tempFile, numberOfPagesToInsert, fontSize, fontFile);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }, shouldCloseStream);
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
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
    static InputStream addPageNumbers(InputStream pdfData, int numberOfPagesToInsert, Integer fontSize,
            InputStream fontStream, boolean shouldCloseStream) throws IOException {
        try {
            return S2FileUtil.processStreamWithTempFile(pdfData, null, tempFile -> {
                try (var document = Loader.loadPDF(tempFile.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
                    return addPageNumbers(document, numberOfPagesToInsert, fontSize,
                            fontStream != null ? PDType0Font.load(document, fontStream) : null);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }, shouldCloseStream);
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        } finally {
            S2StreamUtil.closeStream(fontStream);
        }
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
    static InputStream addPageNumbers(byte[] pdfData, int numberOfPagesToInsert, Integer fontSize, File fontFile)
            throws IOException {
        try (var document = Loader.loadPDF(pdfData)) {
            return addPageNumbers(document, numberOfPagesToInsert, fontSize,
                    fontFile != null ? PDType0Font.load(document, fontFile) : null);
        }
    }

    /**
     * PDF 에 페이지 번호를 추가한다.
     *
     * @param document              PDF 문서
     * @param numberOfPagesToInsert 추가할 페이지 수 (PDF 전체 페이지 수보다 작은 경우 적은 페이지 만큼 비우고 페이지 번호를 추가한다.)
     * @param fontSize              페이지 폰트 사이즈
     * @param fontFile              페이지 폰프 파일
     * @return 페이지 번호를 추가한 InputStream
     * @throws IOException IOException
     */
    /**
     * Numbers the last {@code numberOfPagesToInsert} pages ("1 / N") and returns the result as a temporary-file
     * stream | 마지막 numberOfPagesToInsert 쪽에 번호를 넣고 임시 파일 스트림으로 반환
     */
    private static InputStream addPageNumbers(PDDocument document, int numberOfPagesToInsert, Integer fontSize,
            PDFont font) throws IOException {
        var options = StampOptions.builder()
                .numberOfPagesToApply(numberOfPagesToInsert)
                .pageNumber(PageNumber.of("%d / %d")
                        .position(Position.BOTTOM_CENTER)
                        .offset(0f, 20f)
                        .fontSize(fontSize != null ? fontSize : 10f)
                        .font(font))
                .build();
        return stamp(document, options);
    }

    // ========================================================================
    // 📑 PDF Stamp API (Text, PageNumber, Watermark 통합 스탬프 기능)
    // ========================================================================

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
    static InputStream stamp(Path pdfFile, StampOptions options) throws IOException {
        try (var document = Loader.loadPDF(pdfFile.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            return stamp(document, options);
        }
    }

    /**
     * PDF 문서에 스탬프(텍스트, 페이지 번호, 이미지 워터마크 등)를 적용한다.
     *
     * @param pdfFile 스탬프를 적용할 대상 PDF 파일
     * @param options 스탬프 설정 옵션 (null인 경우 원본 유지)
     * @return 스탬프가 적용된 PDF {@link InputStream}
     * @throws IOException 파일 읽기/쓰기 오류 발생 시
     */
    static InputStream stamp(File pdfFile, StampOptions options) throws IOException {
        return stamp(pdfFile.toPath(), options);
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
    static InputStream stamp(InputStream pdfData, StampOptions options, boolean shouldCloseStream) throws IOException {
        try {
            return S2FileUtil.processStreamWithTempFile(pdfData, null, tempFile -> {
                try (var document = Loader.loadPDF(tempFile.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
                    return stamp(document, options);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }, shouldCloseStream);
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * PDF 문서에 스탬프(텍스트, 페이지 번호, 이미지 워터마크 등)를 적용한다.
     *
     * @param pdfData 스탬프를 적용할 대상 PDF 바이트 배열
     * @param options 스탬프 설정 옵션 (null인 경우 원본 유지)
     * @return 스탬프가 적용된 PDF {@link InputStream}
     * @throws IOException PDF 파싱/저장 오류 발생 시
     */
    static InputStream stamp(byte[] pdfData, StampOptions options) throws IOException {
        try (var document = Loader.loadPDF(pdfData)) {
            return stamp(document, options);
        }
    }

    /**
     * PDF 문서에 스탬프(텍스트, 페이지 번호, 워터마크)를 실제로 적용한다.
     *
     * @param document 대상 PDF 문서
     * @param options  스탬프 종합 옵션
     * @return 스탬프가 적용된 임시 파일 기반 {@link S2ResourceInputStream}
     * @throws IOException PDF 조작 또는 임시 파일 저장 실패 시
     */
    static InputStream stamp(PDDocument document, StampOptions options) throws IOException {
        if (document == null) {
            throw new S2RuntimeException("[stamp] PDF Document is null.");
        }
        assertDocumentAccessible(document, "stamp");
        if (options == null) {
            return saveToResourceInputStream(document);
        }

        int totalPages = document.getNumberOfPages();
        Integer applyCount = options.getNumberOfPagesToApply();
        if (applyCount != null && (applyCount < 1 || applyCount > totalPages)) {
            throw new IllegalArgumentException(
                    "적용할 쪽 수는 1 이상, 전체 쪽 수(" + totalPages + ") 이하여야 합니다: " + applyCount);
        }
        int count = (applyCount != null) ? applyCount : totalPages;
        int startIdx = totalPages - count;

        // 1. 이미지 워터마크 사전 준비
        PDImageXObject watermarkImage = null;
        float[] watermarkSize = null;
        var watermark = options.getWatermark();
        if (watermark != null && watermark.getImageBytes() != null && watermark.getImageBytes().length > 0) {
            try {
                var bytes = watermark.getImageBytes();
                if (isJpeg(bytes)) {
                    checkImageSize(bytes);
                    watermarkImage = JPEGFactory.createFromByteArray(document, bytes);
                } else {
                    watermarkImage = LosslessFactory.createFromImage(document, decodeImage(bytes));
                }
                float imgW = watermarkImage.getWidth();
                float imgH = watermarkImage.getHeight();
                var size = watermark.drawSize(imgW, imgH);
                watermarkSize = size;
            } catch (Exception e) {
                logger.error("워터마크 이미지 변환 실패: ", e);
                if (e instanceof IOException ioe) {
                    throw ioe;
                }
                throw new IOException("워터마크 이미지를 읽을 수 없습니다: " + e.getMessage(), e);
            }
        }

        // 2. 이미지 스탬프 사전 준비 (XObject 재사용 캐시)
        Map<ImageStamp, PDImageXObject> imageCache = new HashMap<>();
        Map<ImageStamp, float[]> imageSizeCache = new HashMap<>();
        if (options.getImages() != null) {
            for (var imgStamp : options.getImages()) {
                if (imgStamp.getImageBytes() != null && imgStamp.getImageBytes().length > 0) {
                    try {
                        var bytes = imgStamp.getImageBytes();
                        PDImageXObject xobj;
                        if (isJpeg(bytes)) {
                            checkImageSize(bytes);
                            xobj = JPEGFactory.createFromByteArray(document, bytes);
                        } else {
                            xobj = LosslessFactory.createFromImage(document, decodeImage(bytes));
                        }
                        imageCache.put(imgStamp, xobj);
                        imageSizeCache.put(imgStamp, imgStamp.drawSize(xobj.getWidth(), xobj.getHeight()));
                    } catch (Exception e) {
                        logger.error("이미지 스탬프 변환 실패: ", e);
                    }
                }
            }
        }

        // 3. QR 코드 스탬프 사전 준비 (XObject 재사용 캐시)
        Map<QrStamp, PDImageXObject> qrCache = new HashMap<>();
        if (options.getQrCodes() != null) {
            for (var qrStamp : options.getQrCodes()) {
                try {
                    var bytes = qrStamp.generateImageBytes();
                    var xobj = LosslessFactory.createFromImage(document, decodeImage(bytes));
                    qrCache.put(qrStamp, xobj);
                } catch (Exception e) {
                    logger.error("QR 스탬프 생성 실패: ", e);
                }
            }
        }

        // 4. 폰트 캐시 (File -> PDFont 매핑 재사용)
        Map<File, PDFont> fontCache = new HashMap<>();

        // 5. 페이지 순회 및 스탬핑
        int pageNum = 0;
        for (int i = 0; i < totalPages; i++) {
            if (i < startIdx) {
                continue;
            }
            pageNum++; // 적용 대상 페이지 번호 (1-based index)

            var page = document.getPage(i);
            var box = page.getCropBox() != null ? page.getCropBox() : page.getMediaBox();
            int rotation = ((page.getRotation() % 360) + 360) % 360;
            boolean isQuarterTurn = rotation == 90 || rotation == 270;
            float shownWidth = isQuarterTurn ? box.getHeight() : box.getWidth();
            float shownHeight = isQuarterTurn ? box.getWidth() : box.getHeight();

            try (var contentStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                // A. 이미지 워터마크 렌더링 (배경)
                if (watermark != null && watermarkImage != null && watermarkSize != null && watermark.appliesToPage(i, totalPages)) {
                    contentStream.saveGraphicsState();
                    try {
                        contentStream.transform(shownToPage(box, rotation));
                        if (watermark.getOpacity() < 1.0f) {
                            var gs = new PDExtendedGraphicsState();
                            gs.setNonStrokingAlphaConstant(watermark.getOpacity());
                            gs.setStrokingAlphaConstant(watermark.getOpacity());
                            contentStream.setGraphicsStateParameters(gs);
                        }
                        var shown = new PDRectangle(shownWidth, shownHeight);
                        var origin = watermark.origin(shown, watermarkSize[0], watermarkSize[1]);
                        float drawW = watermarkSize[0];
                        float drawH = watermarkSize[1];
                        float rot = watermark.getRotation();
                        if (rot != 0f) {
                            float cx = origin[0] + drawW / 2.0f;
                            float cy = origin[1] + drawH / 2.0f;
                            contentStream.transform(Matrix.getTranslateInstance(cx, cy));
                            contentStream.transform(Matrix.getRotateInstance(Math.toRadians(rot), 0, 0));
                            contentStream.transform(Matrix.getTranslateInstance(-drawW / 2.0f, -drawH / 2.0f));
                            contentStream.drawImage(watermarkImage, 0, 0, drawW, drawH);
                        } else {
                            contentStream.drawImage(watermarkImage, origin[0], origin[1], drawW, drawH);
                        }
                    } finally {
                        contentStream.restoreGraphicsState();
                    }
                }

                // B. 이미지 스탬프 목록 렌더링
                if (options.getImages() != null) {
                    for (var imgStamp : options.getImages()) {
                        if (imgStamp.appliesToPage(i, totalPages)) {
                            var xobj = imageCache.get(imgStamp);
                            var size = imageSizeCache.get(imgStamp);
                            if (xobj != null && size != null) {
                                renderImageElement(contentStream, imgStamp.getPosition(), imgStamp.getOffsetX(), imgStamp.getOffsetY(),
                                        imgStamp.getOpacity(), imgStamp.getRotation(), xobj, size[0], size[1],
                                        shownWidth, shownHeight, box, rotation);
                            }
                        }
                    }
                }

                // C. QR 코드 스탬프 목록 렌더링
                if (options.getQrCodes() != null) {
                    for (var qrStamp : options.getQrCodes()) {
                        if (qrStamp.appliesToPage(i, totalPages)) {
                            var xobj = qrCache.get(qrStamp);
                            if (xobj != null) {
                                renderImageElement(contentStream, qrStamp.getPosition(), qrStamp.getOffsetX(), qrStamp.getOffsetY(),
                                        qrStamp.getOpacity(), qrStamp.getRotation(), xobj, qrStamp.getDisplayWidth(), qrStamp.getDisplayHeight(),
                                        shownWidth, shownHeight, box, rotation);
                            }
                        }
                    }
                }

                // D. 텍스트 스탬프 목록 렌더링
                if (options.getTexts() != null) {
                    for (var textOpt : options.getTexts()) {
                        if (textOpt.appliesToPage(i, totalPages)) {
                            renderTextElement(document, contentStream, textOpt, textOpt.resolveText(pageNum, count),
                                    textOpt.getRotation(), textOpt.isRepeat(), textOpt.getRepeatGapX(), textOpt.getRepeatGapY(),
                                    shownWidth, shownHeight, box, rotation, fontCache);
                        }
                    }
                }

                // E. 페이지 번호 렌더링
                var pageNumber = options.getPageNumber();
                if (pageNumber != null && pageNumber.appliesToPage(i, totalPages)) {
                    if (pageNumber.hasDistinctStyles() && pageNumber.getCustomFormatProvider() == null) {
                        renderDistinctPageNumber(document, contentStream, pageNumber, pageNum, count, shownWidth, shownHeight, box, rotation, fontCache);
                    } else {
                        var pageText = pageNumber.resolveText(pageNum, count);
                        renderTextElement(document, contentStream, pageNumber, pageText,
                                0f, false, 0f, 0f,
                                shownWidth, shownHeight, box, rotation, fontCache);
                    }
                }
            }
        }

        return saveToResourceInputStream(document);
    }

    /**
     * 이미지 객체({@link PDImageXObject})를 지정된 좌표, 크기, 회전 각도, 투명도에 맞춰 페이지에 렌더링한다.
     */
    private static void renderImageElement(PDPageContentStream contentStream,
                                           Position position, float offsetX, float offsetY,
                                           float opacity, float rotationDegrees,
                                           PDImageXObject image, float drawW, float drawH,
                                           float shownWidth, float shownHeight,
                                           PDRectangle box, int pageRotation) throws IOException {
        contentStream.saveGraphicsState();
        try {
            contentStream.transform(shownToPage(box, pageRotation));
            if (opacity < 1.0f) {
                var gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(opacity);
                gs.setStrokingAlphaConstant(opacity);
                contentStream.setGraphicsStateParameters(gs);
            }
            var origin = calculateOrigin(position, shownWidth, shownHeight, drawW, drawH, offsetX, offsetY);
            float x = origin[0];
            float y = origin[1];

            if (rotationDegrees != 0f) {
                float cx = x + drawW / 2.0f;
                float cy = y + drawH / 2.0f;
                contentStream.transform(Matrix.getTranslateInstance(cx, cy));
                contentStream.transform(Matrix.getRotateInstance(Math.toRadians(rotationDegrees), 0, 0));
                contentStream.transform(Matrix.getTranslateInstance(-drawW / 2.0f, -drawH / 2.0f));
                contentStream.drawImage(image, 0, 0, drawW, drawH);
            } else {
                contentStream.drawImage(image, x, y, drawW, drawH);
            }
        } finally {
            contentStream.restoreGraphicsState();
        }
    }

    private static InputStream saveToResourceInputStream(PDDocument document) throws IOException {
        var tempFile = Files.createTempFile(S2Uuid.generateUuidV7() + "_", ".pdf");
        try {
            document.save(tempFile.toFile());
            return new S2ResourceInputStream(new BufferedInputStream(Files.newInputStream(tempFile)), tempFile);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    /**
     * 단일 텍스트 요소({@link BaseTextElement})를 지정된 좌표계 및 스타일에 맞춰 페이지에 렌더링한다.
     */
    private static void renderTextElement(PDDocument document, PDPageContentStream contentStream,
                                          BaseTextElement<?> element, String str, float rotationDegrees,
                                          boolean repeat, float gapX, float gapY,
                                          float shownWidth, float shownHeight, PDRectangle box,
                                          int pageRotation, Map<File, PDFont> fontCache) throws IOException {
        if (str == null || str.trim().isEmpty()) {
            return;
        }

        var font = resolveFont(document, element.getFont(), element.getFontFile(), fontCache);
        float fontSize = element.getFontSize();
        float strokeWidth = element.getStrokeWidth() > 0f ? element.getStrokeWidth()
                : (element.isBold() ? Math.max(0.3f, fontSize * 0.035f) : 0f);
        float textWidth = getTextWidth(font, str, fontSize) + strokeWidth;
        var lines = str.split("\\r?\\n");
        float leading = fontSize * 1.2f;
        float textHeight = (lines.length > 1 ? (lines.length - 1) * leading + fontSize : fontSize) + strokeWidth;

        contentStream.saveGraphicsState();
        try {
            contentStream.transform(shownToPage(box, pageRotation));
            if (element.getOpacity() < 1.0f) {
                var gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(element.getOpacity());
                gs.setStrokingAlphaConstant(element.getOpacity());
                contentStream.setGraphicsStateParameters(gs);
            }
            contentStream.setNonStrokingColor(element.getColor());

            if (repeat) {
                float actualGapX = gapX > 0 ? gapX : 150f;
                float actualGapY = gapY > 0 ? gapY : 150f;
                for (float curX = -shownWidth * 0.1f; curX < shownWidth * 1.2f; curX += actualGapX) {
                    for (float curY = -shownHeight * 0.1f; curY < shownHeight * 1.2f; curY += actualGapY) {
                        renderRotatedText(contentStream, font, fontSize, str, curX, curY, textWidth, textHeight, rotationDegrees, true, strokeWidth, element.getColor(), element.getTextAlign());
                    }
                }
            } else {
                float[] origin = calculateOrigin(element.getPosition(), shownWidth, shownHeight,
                        textWidth, textHeight, element.getOffsetX(), element.getOffsetY());
                boolean pivotCenter = (element.getPosition() == Position.CENTER);
                renderRotatedText(contentStream, font, fontSize, str, origin[0], origin[1], textWidth, textHeight, rotationDegrees, pivotCenter, strokeWidth, element.getColor(), element.getTextAlign());
            }
        } finally {
            contentStream.restoreGraphicsState();
        }
    }

    private static class PageNumberPart {
        final String text;
        final PDFont font;
        final float fontSize;
        final float strokeWidth;
        final Color color;
        float width;
        float height;

        PageNumberPart(String text, PDFont font, float fontSize, float strokeWidth, Color color) {
            this.text = text;
            this.font = font;
            this.fontSize = fontSize;
            this.strokeWidth = strokeWidth;
            this.color = color;
        }
    }

    private static void addIfNotNull(List<PageNumberPart> list, PageNumberPart part) {
        if (part != null) {
            list.add(part);
        }
    }

    private static PageNumberPart createPagePart(
            String text, PDFont partFont, File partFontFile,
            Float partFontSize, Boolean partBold, Float partStrokeWidth, Color partColor,
            PageNumber base, PDDocument document, Map<File, PDFont> fontCache) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        var font = resolveFont(document,
                partFont != null ? partFont : base.getFont(),
                partFontFile != null ? partFontFile : base.getFontFile(),
                fontCache);
        float fontSize = partFontSize != null ? partFontSize : base.getFontSize();
        Color color = partColor != null ? partColor : base.getColor();
        float strokeWidth = 0f;
        if (partStrokeWidth != null) {
            strokeWidth = partStrokeWidth;
        } else if (Boolean.TRUE.equals(partBold) || (partBold == null && base.isBold())) {
            strokeWidth = Math.max(0.3f, fontSize * 0.035f);
        } else {
            strokeWidth = base.getStrokeWidth();
        }
        return new PageNumberPart(text, font, fontSize, strokeWidth, color);
    }

    private static void renderDistinctPageNumber(PDDocument document, PDPageContentStream contentStream,
                                                 PageNumber pageNumber, int pageNum, int count,
                                                 float shownWidth, float shownHeight, PDRectangle box,
                                                 int rotation, Map<File, PDFont> fontCache) throws IOException {
        var parts = new ArrayList<PageNumberPart>();

        // 1. Prefix (접두사)
        addIfNotNull(parts, createPagePart(pageNumber.getPrefix(),
                pageNumber.getPrefixFont(), pageNumber.getPrefixFontFile(),
                pageNumber.getPrefixFontSize(), pageNumber.isPrefixBold(), pageNumber.getPrefixStrokeWidth(),
                pageNumber.getPrefixColor(), pageNumber, document, fontCache));

        // 2. Current Page (현재 페이지 n)
        addIfNotNull(parts, createPagePart(String.valueOf(pageNum),
                pageNumber.getCurrentPageFont(), pageNumber.getCurrentPageFontFile(),
                pageNumber.getCurrentPageFontSize(), pageNumber.isCurrentPageBold(), pageNumber.getCurrentPageStrokeWidth(),
                pageNumber.getCurrentPageColor(), pageNumber, document, fontCache));

        // 3. Separator (구분자) & Total Page (전체 페이지 m)
        if (pageNumber.isShowTotalPage()) {
            String sepText = pageNumber.getSeparator() != null ? pageNumber.getSeparator() : " / ";
            addIfNotNull(parts, createPagePart(sepText,
                    pageNumber.getSeparatorFont(), pageNumber.getSeparatorFontFile(),
                    pageNumber.getSeparatorFontSize(), pageNumber.isSeparatorBold(), pageNumber.getSeparatorStrokeWidth(),
                    pageNumber.getSeparatorColor(), pageNumber, document, fontCache));

            addIfNotNull(parts, createPagePart(String.valueOf(count),
                    pageNumber.getTotalPageFont(), pageNumber.getTotalPageFontFile(),
                    pageNumber.getTotalPageFontSize(), pageNumber.isTotalPageBold(), pageNumber.getTotalPageStrokeWidth(),
                    pageNumber.getTotalPageColor(), pageNumber, document, fontCache));
        }

        // 4. Suffix (접미사)
        addIfNotNull(parts, createPagePart(pageNumber.getSuffix(),
                pageNumber.getSuffixFont(), pageNumber.getSuffixFontFile(),
                pageNumber.getSuffixFontSize(), pageNumber.isSuffixBold(), pageNumber.getSuffixStrokeWidth(),
                pageNumber.getSuffixColor(), pageNumber, document, fontCache));

        // 5. 너비 및 높이 계산
        float totalWidth = 0f;
        float maxHeight = 0f;
        for (var part : parts) {
            part.width = getTextWidth(part.font, part.text, part.fontSize) + part.strokeWidth;
            part.height = part.fontSize + part.strokeWidth;
            totalWidth += part.width;
            if (part.height > maxHeight) {
                maxHeight = part.height;
            }
        }
        if (totalWidth <= 0f) {
            return;
        }

        // 6. 위치 및 오프셋 계산
        float[] origin = calculateOrigin(pageNumber.getPosition(), shownWidth, shownHeight,
                totalWidth, maxHeight, pageNumber.getOffsetX(), pageNumber.getOffsetY());

        contentStream.saveGraphicsState();
        try {
            contentStream.transform(shownToPage(box, rotation));
            if (pageNumber.getOpacity() < 1.0f) {
                var gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(pageNumber.getOpacity());
                gs.setStrokingAlphaConstant(pageNumber.getOpacity());
                contentStream.setGraphicsStateParameters(gs);
            }

            float curX = origin[0];
            float curY = origin[1];

            for (var part : parts) {
                renderRotatedText(contentStream, part.font, part.fontSize, part.text,
                        curX, curY, part.width, part.height, 0f, false, part.strokeWidth, part.color);
                curX += part.width;
            }
        } finally {
            contentStream.restoreGraphicsState();
        }
    }

    /**
     * 기준 위치 및 상대 오프셋을 바탕으로 화면 좌표계 기준 좌측 하단 원점(origin x, y)을 계산한다.
     *
     * @param position    기준 위치
     * @param shownWidth  화면상 가로 너비
     * @param shownHeight 화면상 세로 높이
     * @param itemWidth   요소 너비
     * @param itemHeight  요소 높이
     * @param offsetX     가로 상대 오프셋
     * @param offsetY     세로 상대 오프셋
     * @return float[2] {x, y}
     */
    private static float[] calculateOrigin(Position position, float shownWidth, float shownHeight,
                                           float itemWidth, float itemHeight, float offsetX, float offsetY) {
        var name = (position != null ? position : Position.CENTER).name();
        float x;
        if (name.endsWith("LEFT")) {
            x = offsetX;
        } else if (name.endsWith("RIGHT")) {
            x = shownWidth - itemWidth - offsetX;
        } else {
            x = (shownWidth - itemWidth) / 2.0f + offsetX;
        }

        float y;
        if (name.startsWith("TOP")) {
            y = shownHeight - itemHeight - offsetY;
        } else if (name.startsWith("BOTTOM")) {
            y = offsetY;
        } else {
            y = (shownHeight - itemHeight) / 2.0f + offsetY;
        }
        return new float[]{x, y};
    }

    /**
     * 폰트 객체를 결정하여 반환한다. (명시적 font -> fontFile 파일 로드 -> 기본 HELVETICA 순서)
     */
    private static PDFont resolveFont(PDDocument document, PDFont explicitFont, File fontFile, Map<File, PDFont> fontCache) {
        if (explicitFont != null) {
            return explicitFont;
        }
        if (fontFile != null) {
            if (fontCache.containsKey(fontFile)) {
                return fontCache.get(fontFile);
            }
            try {
                var loaded = PDType0Font.load(document, fontFile);
                fontCache.put(fontFile, loaded);
                return loaded;
            } catch (Exception e) {
                logger.warn("폰트 파일 로드 실패, 기본 폰트로 대체: " + fontFile, e);
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    /**
     * 텍스트의 가로 너비를 계산한다.
     */
    private static float getTextWidth(PDFont font, String text, float fontSize) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        var lines = text.split("\\r?\\n");
        float maxWidth = 0f;
        for (var line : lines) {
            float w;
            try {
                w = font.getStringWidth(line) / 1000f * fontSize;
            } catch (Exception e) {
                w = line.length() * fontSize * 0.6f;
            }
            if (w > maxWidth) {
                maxWidth = w;
            }
        }
        return maxWidth;
    }

    /**
     * 텍스트를 지정된 회전 각도와 위치에 안전하게 렌더링한다. (개행 문자 멀티라인 및 선 두께/볼드 렌더링 지원)
     */
    private static void renderRotatedText(PDPageContentStream contentStream, PDFont font, float fontSize,
                                          String text, float x, float y, float textWidth, float textHeight,
                                          float rotationDegrees, boolean pivotCenter, float strokeWidth,
                                          Color color, TextAlign textAlign) throws IOException {
        var lines = text.split("\\r?\\n");
        float leading = fontSize * 1.2f;
        float startY = lines.length > 1 ? y + (lines.length - 1) * leading : y;

        if (color != null) {
            contentStream.setNonStrokingColor(color);
        }
        if (strokeWidth > 0f) {
            contentStream.setStrokingColor(color != null ? color : Color.BLACK);
            contentStream.setLineWidth(strokeWidth);
        }

        // 줄별 정렬 오프셋 계산 (LEFT이거나 한 줄이면 모두 0)
        var align = textAlign != null ? textAlign : TextAlign.LEFT;
        float maxLineWidth = textWidth - strokeWidth;
        var lineOffsets = new float[lines.length];
        if (align != TextAlign.LEFT && lines.length > 1) {
            for (int i = 0; i < lines.length; i++) {
                float lineWidth;
                try {
                    lineWidth = font.getStringWidth(lines[i]) / 1000f * fontSize;
                } catch (Exception e) {
                    lineWidth = lines[i].length() * fontSize * 0.6f;
                }
                float gap = maxLineWidth - lineWidth;
                lineOffsets[i] = (align == TextAlign.CENTER) ? gap / 2f : gap;
            }
        }

        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.setLeading(leading);
        if (strokeWidth > 0f) {
            contentStream.setRenderingMode(RenderingMode.FILL_STROKE);
        } else {
            contentStream.setRenderingMode(RenderingMode.FILL);
        }
        if (rotationDegrees != 0f) {
            float rad = (float) Math.toRadians(rotationDegrees);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float tx = x;
            float ty = startY;
            if (pivotCenter) {
                float cx = x + textWidth / 2f;
                float cy = y + textHeight / 2f;
                float x0 = -textWidth / 2f;
                float y0 = (startY - y) - textHeight / 2f;
                tx = cx + (x0 * cos - y0 * sin);
                ty = cy + (x0 * sin + y0 * cos);
            }
            // 첫 번째 줄 정렬 오프셋을 회전된 좌표계 방향으로 적용
            tx += lineOffsets[0] * cos;
            ty += lineOffsets[0] * sin;
            contentStream.setTextMatrix(new Matrix(cos, sin, -sin, cos, tx, ty));
        } else {
            contentStream.newLineAtOffset(x + lineOffsets[0], startY);
        }

        for (int i = 0; i < lines.length; i++) {
            try {
                contentStream.showText(lines[i]);
            } catch (IllegalArgumentException e) {
                logger.error("PDF 텍스트 출력 실패(한글 등 지원 폰트 지정 필요): " + lines[i], e);
            }
            if (i < lines.length - 1) {
                float deltaX = lineOffsets[i + 1] - lineOffsets[i];
                contentStream.newLineAtOffset(deltaX, -leading);
            }
        }
        contentStream.endText();
    }

    private static void renderRotatedText(PDPageContentStream contentStream, PDFont font, float fontSize,
                                          String text, float x, float y, float textWidth, float textHeight,
                                          float rotationDegrees, boolean pivotCenter, float strokeWidth,
                                          Color color) throws IOException {
        renderRotatedText(contentStream, font, fontSize, text, x, y, textWidth, textHeight, rotationDegrees, pivotCenter, strokeWidth, color, TextAlign.LEFT);
    }

    private static void renderRotatedText(PDPageContentStream contentStream, PDFont font, float fontSize,
                                          String text, float x, float y, float textWidth, float textHeight,
                                          float rotationDegrees, boolean pivotCenter) throws IOException {
        renderRotatedText(contentStream, font, fontSize, text, x, y, textWidth, textHeight, rotationDegrees, pivotCenter, 0f, null, TextAlign.LEFT);
    }



    /**
     * 여러 개의 PDF InputStream 을 단일 PDF 로 병합한다.
     *
     * @param pdfStreams         병합할 PDF InputStream 목록 (null 항목은 건너뜀)
     * @param shouldCloseStreams 병합 후 입력 스트림들을 자동으로 닫을지 여부
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 또는 암호·권한 제한 문서일 때
     */
    static InputStream mergePdfs(List<InputStream> pdfStreams, boolean shouldCloseStreams) throws IOException {
        if (pdfStreams == null || pdfStreams.isEmpty()) {
            throw new IllegalArgumentException("[mergePdfs] pdfStreams must not be null or empty.");
        }
        var temps = new ArrayList<Path>();
        try {
            var files = new ArrayList<File>();
            for (var stream : pdfStreams) {
                if (stream != null) {
                    var temp = createTempFile("src", ".pdf", temps);
                    Files.copy(stream, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    files.add(temp.toFile());
                }
            }
            return mergeFiles(files);
        } finally {
            if (shouldCloseStreams) {
                pdfStreams.forEach(S2StreamUtil::closeStream);
            }
            deleteQuietly(temps);
        }
    }

    /**
     * 여러 개의 PDF InputStream 을 단일 PDF 로 병합한다. (입력 스트림 자동 close)
     *
     * @param pdfStreams 병합할 PDF InputStream 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    static InputStream mergePdfs(InputStream... pdfStreams) throws IOException {
        if (pdfStreams == null || pdfStreams.length == 0) {
            throw new IllegalArgumentException("[mergePdfs] pdfStreams must not be null or empty.");
        }
        return mergePdfs(Arrays.asList(pdfStreams), true);
    }

    /**
     * 여러 개의 PDF 파일들을 단일 PDF 로 병합한다. 없는 파일은 건너뛴다.
     *
     * @param pdfFiles 병합할 PDF 파일 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    static InputStream mergePdfFiles(List<File> pdfFiles) throws IOException {
        if (pdfFiles == null || pdfFiles.isEmpty()) {
            throw new IllegalArgumentException("[mergePdfFiles] pdfFiles must not be null or empty.");
        }
        var files = new ArrayList<File>();
        for (var file : pdfFiles) {
            if (file != null && file.exists()) {
                files.add(file);
            }
        }
        return mergeFiles(files);
    }

    /**
     * 여러 개의 PDF 파일들을 단일 PDF 로 병합한다. (가변인자)
     *
     * @param pdfFiles 병합할 PDF 파일 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    static InputStream mergePdfFiles(File... pdfFiles) throws IOException {
        if (pdfFiles == null || pdfFiles.length == 0) {
            throw new IllegalArgumentException("[mergePdfFiles] pdfFiles must not be null or empty.");
        }
        return mergePdfFiles(Arrays.asList(pdfFiles));
    }

    /**
     * 여러 개의 PDF 파일 경로(Path)들을 단일 PDF 로 병합한다. 없는 파일은 건너뛴다.
     *
     * @param pdfPaths 병합할 PDF 파일 경로 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    static InputStream mergePdfPaths(List<Path> pdfPaths) throws IOException {
        if (pdfPaths == null || pdfPaths.isEmpty()) {
            throw new IllegalArgumentException("[mergePdfPaths] pdfPaths must not be null or empty.");
        }
        var files = new ArrayList<File>();
        for (var path : pdfPaths) {
            if (path != null && Files.exists(path)) {
                files.add(path.toFile());
            }
        }
        return mergeFiles(files);
    }

    /**
     * 여러 개의 PDF 파일 경로(Path)들을 단일 PDF 로 병합한다. (가변인자)
     *
     * @param pdfPaths 병합할 PDF 파일 경로 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    static InputStream mergePdfPaths(Path... pdfPaths) throws IOException {
        if (pdfPaths == null || pdfPaths.length == 0) {
            throw new IllegalArgumentException("[mergePdfPaths] pdfPaths must not be null or empty.");
        }
        return mergePdfPaths(Arrays.asList(pdfPaths));
    }

    /**
     * 여러 이미지를 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다.
     *
     * @param imageStreams       이미지 InputStream 목록 (PNG, JPG, GIF 등, null 항목은 건너뜀)
     * @param shouldCloseStreams 입력 스트림 자동 닫기 여부
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    static InputStream mergeImagesToPdf(List<InputStream> imageStreams, boolean shouldCloseStreams)
            throws IOException {
        if (imageStreams == null || imageStreams.isEmpty()) {
            throw new IllegalArgumentException("[mergeImagesToPdf] imageStreams must not be null or empty.");
        }
        try {
            var images = new ArrayList<byte[]>();
            for (var stream : imageStreams) {
                if (stream != null) {
                    images.add(S2StreamUtil.streamToByteArray(stream, false, S2PdfUtil.DEFAULT_MAX_SOURCE_BYTES));
                }
            }
            return mergeImages(images, null);
        } finally {
            if (shouldCloseStreams) {
                imageStreams.forEach(S2StreamUtil::closeStream);
            }
        }
    }

    /**
     * 여러 이미지를 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (입력 스트림 자동 close)
     *
     * @param imageStreams 이미지 InputStream 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    static InputStream mergeImagesToPdf(InputStream... imageStreams) throws IOException {
        if (imageStreams == null || imageStreams.length == 0) {
            throw new IllegalArgumentException("[mergeImagesToPdf] imageStreams must not be null or empty.");
        }
        return mergeImagesToPdf(Arrays.asList(imageStreams), true);
    }

    /**
     * 여러 이미지 파일(File)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. 없는 파일은 건너뛴다.
     *
     * @param imageFiles 이미지 파일 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    static InputStream mergeImageFiles(List<File> imageFiles) throws IOException {
        if (imageFiles == null || imageFiles.isEmpty()) {
            throw new IllegalArgumentException("[mergeImageFiles] imageFiles must not be null or empty.");
        }
        var images = new ArrayList<byte[]>();
        for (var file : imageFiles) {
            if (file != null && file.exists()) {
                images.add(Files.readAllBytes(file.toPath()));
            }
        }
        return mergeImages(images, null);
    }

    /**
     * 여러 이미지 파일(File)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param imageFiles 이미지 파일 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    static InputStream mergeImageFiles(File... imageFiles) throws IOException {
        if (imageFiles == null || imageFiles.length == 0) {
            throw new IllegalArgumentException("[mergeImageFiles] imageFiles must not be null or empty.");
        }
        return mergeImageFiles(Arrays.asList(imageFiles));
    }

    /**
     * 여러 이미지 경로(Path)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. 없는 파일은 건너뛴다.
     *
     * @param imagePaths 이미지 경로 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    static InputStream mergeImagePaths(List<Path> imagePaths) throws IOException {
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new IllegalArgumentException("[mergeImagePaths] imagePaths must not be null or empty.");
        }
        var images = new ArrayList<byte[]>();
        for (var path : imagePaths) {
            if (path != null && Files.exists(path)) {
                images.add(Files.readAllBytes(path));
            }
        }
        return mergeImages(images, null);
    }

    /**
     * 여러 이미지 경로(Path)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param imagePaths 이미지 경로 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    static InputStream mergeImagePaths(Path... imagePaths) throws IOException {
        if (imagePaths == null || imagePaths.length == 0) {
            throw new IllegalArgumentException("[mergeImagePaths] imagePaths must not be null or empty.");
        }
        return mergeImagePaths(Arrays.asList(imagePaths));
    }

    /**
     * 메모리상의 이미지(BufferedImage)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다.
     *
     * @param images BufferedImage 목록 (null 항목은 건너뜀)
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    static InputStream mergeImagesToPdf(List<BufferedImage> images) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("[mergeImagesToPdf] images must not be null or empty.");
        }
        var present = new ArrayList<BufferedImage>();
        for (var image : images) {
            if (image != null) {
                present.add(image);
            }
        }
        return mergeImages(null, present);
    }

    /**
     * 메모리상의 이미지(BufferedImage)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param images BufferedImage 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    static InputStream mergeImagesToPdf(BufferedImage... images) throws IOException {
        if (images == null || images.length == 0) {
            throw new IllegalArgumentException("[mergeImagesToPdf] images must not be null or empty.");
        }
        return mergeImagesToPdf(Arrays.asList(images));
    }

    /** Each image on its own A4 page, in order | 이미지마다 A4 한 쪽씩 순서대로 */
    private static InputStream mergeImages(List<byte[]> encoded, List<BufferedImage> decoded) throws IOException {
        var temps = new ArrayList<Path>();
        try {
            var files = new ArrayList<File>();
            if (encoded != null) {
                for (var bytes : encoded) {
                    var temp = createTempFile("img", ".pdf", temps);
                    renderImageToPdfFile(null, bytes, temp);
                    files.add(temp.toFile());
                }
            }
            if (decoded != null) {
                for (var image : decoded) {
                    var temp = createTempFile("img", ".pdf", temps);
                    renderImageToPdfFile(image, null, temp);
                    files.add(temp.toFile());
                }
            }
            return mergeFiles(files);
        } finally {
            deleteQuietly(temps);
        }
    }

    /**
     * Merges PDF files in order through PDFBox's disk-backed cache, so large documents stay off the heap
     * | PDF 파일들을 순서대로 병합한다. PDFBox 디스크 캐시를 써서 큰 문서도 힙을 적게 쓴다
     */
    private static InputStream mergeFiles(List<File> files) throws IOException {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("병합할 문서가 없습니다.");
        }
        var merger = new PDFMergerUtility();
        for (var file : files) {
            assertDocumentAccessible(file, file.getName());
            merger.addSource(file);
        }
        var result = Files.createTempFile(S2Uuid.generateUuidV7() + "_merged_", ".pdf");
        try {
            try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(result))) {
                merger.setDestinationStream(out);
                merger.mergeDocuments(IOUtils.createTempFileOnlyStreamCache());
            }
            return new S2ResourceInputStream(new BufferedInputStream(Files.newInputStream(result)), result);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(result);
            throw e;
        }
    }

    private static Path createTempFile(String prefix, String suffix, List<Path> tracking) throws IOException {
        var temp = Files.createTempFile(S2Uuid.generateUuidV7() + "_" + prefix + "_", suffix);
        tracking.add(temp);
        return temp;
    }

    private static void deleteQuietly(List<Path> paths) {
        for (var path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Left for the system temp cleanup | 시스템 임시 폴더 정리에 맡김
            }
        }
    }

    /**
     * 단일 이미지 스트림을 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param imageStream       이미지 InputStream
     * @param shouldCloseStream 입력 스트림 자동 닫기 여부
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    static InputStream convertImageToPdf(InputStream imageStream, boolean shouldCloseStream) throws IOException {
        try {
            var imageBytes = IOUtils.toByteArray(imageStream);
            return convertImageToPdf(imageBytes);
        } finally {
            if (shouldCloseStream) {
                S2StreamUtil.closeStream(imageStream);
            }
        }
    }

    /**
     * 단일 이미지 byte[] 데이터를 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param imageBytes 이미지 byte 배열
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    static InputStream convertImageToPdf(byte[] imageBytes) throws IOException {
        var tempFile = Files.createTempFile(S2Uuid.generateUuidV7() + "_img_", ".pdf");
        boolean success = false;
        try {
            renderImageToPdfFile(null, imageBytes, tempFile);
            var is = new S2ResourceInputStream(new BufferedInputStream(Files.newInputStream(tempFile)), tempFile);
            success = true;
            return is;
        } finally {
            if (!success) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignore) {
                }
            }
        }
    }

    /**
     * 단일 BufferedImage 객체를 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param image BufferedImage 객체
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    static InputStream convertImageToPdf(BufferedImage image) throws IOException {
        var tempFile = Files.createTempFile(S2Uuid.generateUuidV7() + "_img_", ".pdf");
        boolean success = false;
        try {
            renderImageToPdfFile(image, null, tempFile);
            var is = new S2ResourceInputStream(new BufferedInputStream(Files.newInputStream(tempFile)), tempFile);
            success = true;
            return is;
        } finally {
            if (!success) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignore) {
                }
            }
        }
    }

    /**
     * 단일 이미지 File 을 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param imageFile 이미지 파일
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    static InputStream convertImageToPdf(File imageFile) throws IOException {
        return convertImageToPdf(Files.readAllBytes(
                Objects.requireNonNull(imageFile, "[convertImageToPdf] imageFile must not be null").toPath()));
    }

    /**
     * 단일 이미지 Path 를 A4 규격(비율 유지 및 여백 포함) PDF 스트림으로 변환한다.
     *
     * @param imagePath 이미지 경로
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 이미지 읽기 또는 PDF 생성 오류 시
     */
    static InputStream convertImageToPdf(Path imagePath) throws IOException {
        return convertImageToPdf(Files
                .readAllBytes(Objects.requireNonNull(imagePath, "[convertImageToPdf] imagePath must not be null")));
    }


    /** Largest image placed on a page (width × height), as in S2ImageUtil | 페이지에 넣을 최대 이미지 크기 (S2ImageUtil 과 동일) */
    private static final long MAX_IMAGE_PIXELS = S2ImageUtil.MAX_PIXELS;

    private static void renderImageToPdfFile(BufferedImage bimg, byte[] rawBytes, Path targetPdfFile)
            throws IOException {
        renderImageToPdfFile(bimg, rawBytes, targetPdfFile, 0);
    }

    /** Content box of an A4 page with a 20pt margin, long and short side | 20pt 여백을 둔 A4 본문 영역 (긴 변, 짧은 변) */
    private static final float IMAGE_BOX_LONG = PDRectangle.A4.getHeight() - 40;
    private static final float IMAGE_BOX_SHORT = PDRectangle.A4.getWidth() - 40;
    /** Shrinks only when the image is clearly larger than needed | 필요보다 확실히 클 때만 줄임 */
    private static final double SHRINK_MARGIN = 1.2;

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
    static void renderImageToPdfFile(BufferedImage bimg, byte[] rawBytes, Path targetPdfFile, int dpi)
            throws IOException {
        if (bimg == null && (rawBytes == null || rawBytes.length == 0)) {
            throw new IllegalArgumentException("[renderImageToPdfFile] image must not be null or empty.");
        }
        try (var doc = new PDDocument()) {
            PDImageXObject pdImage;
            var orientation = 1;
            if (bimg != null) {
                if (dpi > 0) {
                    var limit = pixelLimit(bimg.getWidth(), bimg.getHeight(), dpi);
                    if (needsShrinking(bimg.getWidth(), bimg.getHeight(), limit)) {
                        bimg = S2ImageUtil.scaleToFit(bimg, limit[0], limit[1]);
                    }
                }
                pdImage = LosslessFactory.createFromImage(doc, bimg);
            } else {
                var bytes = rawBytes;
                if (isJpeg(bytes)) {
                    checkImageSize(bytes);
                    orientation = S2ImageUtil.exifOrientation(bytes);
                }
                if (dpi > 0) {
                    var size = imageSize(bytes);
                    var quarter = orientation >= 5;
                    int shownWidth = quarter ? size[1] : size[0];
                    int shownHeight = quarter ? size[0] : size[1];
                    var limit = pixelLimit(shownWidth, shownHeight, dpi);
                    if (needsShrinking(shownWidth, shownHeight, limit)) {
                        // Returned upright, so no orientation is left to apply | 바로 세워서 돌려주므로 더 돌릴 것이 없음
                        bytes = S2ImageUtil.resizeToFit(bytes, limit[0], limit[1]);
                        orientation = 1;
                    }
                }
                pdImage = isJpeg(bytes) ? JPEGFactory.createFromByteArray(doc, bytes)
                        : LosslessFactory.createFromImage(doc, decodeImage(bytes));
            }

            var quarter = orientation >= 5;
            float imgWidth = quarter ? pdImage.getHeight() : pdImage.getWidth();
            float imgHeight = quarter ? pdImage.getWidth() : pdImage.getHeight();
            var pageSize = imgWidth > imgHeight
                    ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                    : PDRectangle.A4;
            var margin = 20f;
            var maxContentWidth = pageSize.getWidth() - margin * 2;
            var maxContentHeight = pageSize.getHeight() - margin * 2;
            var scale = Math.min(maxContentWidth / imgWidth, maxContentHeight / imgHeight);
            var drawWidth = imgWidth * scale;
            var drawHeight = imgHeight * scale;
            var x = margin + (maxContentWidth - drawWidth) / 2;
            var y = margin + (maxContentHeight - drawHeight) / 2;

            var page = new PDPage(pageSize);
            doc.addPage(page);
            try (var contentStream = new PDPageContentStream(doc, page)) {
                contentStream.drawImage(pdImage, orientedImageMatrix(orientation, x, y, drawWidth, drawHeight));
            }
            doc.save(targetPdfFile.toFile());
        }
    }

    /** Pixel limit {width, height} for an image shown at this size on the A4 content box | A4 본문 영역에 맞춘 픽셀 한도 */
    private static int[] pixelLimit(int shownWidth, int shownHeight, int dpi) {
        var landscape = shownWidth > shownHeight;
        var boxWidth = landscape ? IMAGE_BOX_LONG : IMAGE_BOX_SHORT;
        var boxHeight = landscape ? IMAGE_BOX_SHORT : IMAGE_BOX_LONG;
        return new int[] { Math.max(1, Math.round(boxWidth / 72f * dpi)), Math.max(1, Math.round(boxHeight / 72f * dpi)) };
    }

    private static boolean needsShrinking(int width, int height, int[] limit) {
        return Math.min((double) limit[0] / width, (double) limit[1] / height) < 1 / SHRINK_MARGIN;
    }

    private static int[] imageSize(byte[] bytes) throws IOException {
        return withImageReader(bytes, reader -> new int[] { reader.getWidth(0), reader.getHeight(0) });
    }

    /**
     * Maps the image's unit square to the box (x, y, w, h) on the page so the stored image appears upright for its EXIF
     * orientation | 이미지 단위 사각형을 쪽의 (x, y, w, h) 로 옮기며 EXIF 방향대로 바로 보이게 함
     */
    private static org.apache.pdfbox.util.Matrix orientedImageMatrix(int orientation, float x, float y, float w, float h) {
        return switch (orientation) {
        case 2 -> new org.apache.pdfbox.util.Matrix(-w, 0, 0, h, x + w, y);
        case 3 -> new org.apache.pdfbox.util.Matrix(-w, 0, 0, -h, x + w, y + h);
        case 4 -> new org.apache.pdfbox.util.Matrix(w, 0, 0, -h, x, y + h);
        case 5 -> new org.apache.pdfbox.util.Matrix(0, -h, -w, 0, x + w, y + h);
        case 6 -> new org.apache.pdfbox.util.Matrix(0, -h, w, 0, x, y + h);
        case 7 -> new org.apache.pdfbox.util.Matrix(0, h, w, 0, x, y);
        case 8 -> new org.apache.pdfbox.util.Matrix(0, h, -w, 0, x + w, y);
        default -> new org.apache.pdfbox.util.Matrix(w, 0, 0, h, x, y);
        };
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
    static boolean shrinkPdfImages(Path source, Path target, int dpi) throws IOException {
        try (var doc = Loader.loadPDF(source.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            var done = new java.util.IdentityHashMap<org.apache.pdfbox.cos.COSBase, PDImageXObject>();
            var changed = false;
            for (var page : doc.getPages()) {
                var box = page.getMediaBox();
                var longSide = Math.round(Math.max(box.getWidth(), box.getHeight()) / 72f * dpi);
                var shortSide = Math.round(Math.min(box.getWidth(), box.getHeight()) / 72f * dpi);
                changed |= shrinkImages(doc, page.getResources(), longSide, shortSide, done, 0);
            }
            if (changed) {
                doc.save(target.toFile());
            }
            return changed;
        }
    }

    private static boolean shrinkImages(PDDocument doc, org.apache.pdfbox.pdmodel.PDResources resources, int longSide,
            int shortSide, java.util.Map<org.apache.pdfbox.cos.COSBase, PDImageXObject> done, int depth)
            throws IOException {
        if (resources == null || depth > 8) {
            return false;
        }
        var changed = false;
        for (var name : resources.getXObjectNames()) {
            var xobject = resources.getXObject(name);
            if (xobject instanceof org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject form) {
                changed |= shrinkImages(doc, form.getResources(), longSide, shortSide, done, depth + 1);
            } else if (xobject instanceof PDImageXObject image) {
                // Shared images are shrunk once | 함께 쓰는 이미지는 한 번만 줄임
                var replacement = done.get(image.getCOSObject());
                if (replacement == null) {
                    var shrunk = shrinkPdfImage(doc, image, longSide, shortSide);
                    replacement = shrunk != null ? shrunk : image;
                    done.put(image.getCOSObject(), replacement);
                }
                if (replacement != image) {
                    resources.put(name, replacement);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static PDImageXObject shrinkPdfImage(PDDocument doc, PDImageXObject image, int longSide, int shortSide) {
        try {
            var cos = image.getCOSObject();
            // Left alone: masks and transparency, 1-bit scans, and codecs already compact or not decodable here
            // | 그대로 둠: 마스크·투명도, 1비트 스캔, 이미 작거나 여기서 풀 수 없는 코덱
            if (image.isStencil() || image.getBitsPerComponent() == 1 || cos.getItem(COSName.SMASK) != null
                    || cos.getItem(COSName.MASK) != null) {
                return null;
            }
            var filters = image.getStream().getFilters();
            if (filters.contains(COSName.JBIG2_DECODE) || filters.contains(COSName.CCITTFAX_DECODE)
                    || filters.contains(COSName.JPX_DECODE)) {
                return null;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            var scale = Math.min((double) longSide / Math.max(width, height), (double) shortSide / Math.min(width, height));
            if (scale >= 1 / SHRINK_MARGIN) {
                return null;
            }
            var step = Integer.highestOneBit(Math.max(1, (int) Math.floor(1 / scale)));
            var decoded = image.getImage(null, step);
            decoded = S2ImageUtil.scaleToFit(decoded, (int) Math.max(1, Math.round(width * scale)),
                    (int) Math.max(1, Math.round(height * scale)));
            var shrunk = filters.contains(COSName.DCT_DECODE)
                    ? JPEGFactory.createFromImage(doc, decoded, S2ImageUtil.JPEG_QUALITY)
                    : LosslessFactory.createFromImage(doc, decoded);
            // Keep the original when re-encoding did not make it smaller | 다시 넣어도 작아지지 않으면 원본 유지
            return shrunk.getCOSObject().getLength() < cos.getLength() ? shrunk : null;
        } catch (IOException | RuntimeException e) {
            logger.warn("PDF 안의 이미지를 줄이지 못해 그대로 둡니다: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Whether the bytes start with a JPEG signature | JPEG 시그니처로 시작하는지
     *
     * @param bytes 이미지 바이트
     * @return JPEG 이면 true
     */
    static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    /** Reads only the header to refuse huge images before decoding | 헤더만 읽어 거대한 이미지는 디코딩 전에 거부 */
    private static void checkImageSize(byte[] bytes) throws IOException {
        withImageReader(bytes, reader -> {
            var pixels = (long) reader.getWidth(0) * reader.getHeight(0);
            if (pixels > MAX_IMAGE_PIXELS) {
                throw new IOException("이미지 해상도가 너무 큽니다: " + reader.getWidth(0) + "x" + reader.getHeight(0));
            }
            return null;
        });
    }

    private static BufferedImage decodeImage(byte[] bytes) throws IOException {
        return withImageReader(bytes, reader -> {
            var pixels = (long) reader.getWidth(0) * reader.getHeight(0);
            if (pixels > MAX_IMAGE_PIXELS) {
                throw new IOException("이미지 해상도가 너무 큽니다: " + reader.getWidth(0) + "x" + reader.getHeight(0));
            }
            return reader.read(0);
        });
    }

    private interface ReaderAction<T> {
        T apply(javax.imageio.ImageReader reader) throws IOException;
    }

    private static <T> T withImageReader(byte[] bytes, ReaderAction<T> action) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new IOException(isWebp(bytes)
                        ? "WebP 이미지를 읽으려면 com.twelvemonkeys.imageio:imageio-webp 를 의존성에 추가하십시오."
                        : "지원되지 않는 이미지 포맷이거나 손상된 이미지 파일입니다. (지원: " + String.join(", ", ImageIO.getReaderFormatNames()) + ")");
            }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return action.apply(reader);
            } finally {
                reader.dispose();
            }
        }
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    /**
     * Check document encryption and access permissions | 문서 암호화 및 접근 권한 검사
     *
     * @param pdfFile    검사할 PDF (없으면 아무것도 하지 않음)
     * @param sourceHint 오류 메시지에 쓸 이름
     * @throws IOException 암호가 걸렸거나 내용 추출이 제한된 문서일 때
     */
    static void assertDocumentAccessible(File pdfFile, String sourceHint) throws IOException {
        if (pdfFile == null || !pdfFile.exists()) {
            return;
        }
        try (var doc = Loader.loadPDF(pdfFile, IOUtils.createTempFileOnlyStreamCache())) {
            assertDocumentAccessible(doc, sourceHint);
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            var name = S2Util.isNotEmpty(sourceHint) ? sourceHint : pdfFile.getName();
            throw new IOException("암호로 보호된 PDF 문서는 처리할 수 없습니다: " + name + " (암호를 해제한 후 다시 시도하십시오)", e);
        }
    }

    /**
     * Check PDDocument access permission | 열려 있는 PDDocument 권한 상태 검사
     *
     * @param document   검사할 문서 (null 이면 아무것도 하지 않음)
     * @param sourceHint 오류 메시지에 쓸 이름
     * @throws IOException 내용 추출이 제한된 문서일 때
     */
    static void assertDocumentAccessible(PDDocument document, String sourceHint) throws IOException {
        if (document == null) {
            return;
        }
        if (document.isEncrypted()) {
            var permission = document.getCurrentAccessPermission();
            if (permission != null && !permission.canExtractContent()) {
                var name = S2Util.isNotEmpty(sourceHint) ? sourceHint : "PDF 문서";
                throw new IOException("암호화 또는 권한 제한으로 인해 처리할 수 없는 PDF 문서입니다: " + name
                        + " (문서 보안 또는 인쇄/추출 제한을 해제한 후 다시 시도하십시오)");
            }
        }
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
    static void addBookmark(File pdf, String title, Path copy) throws IOException {
        try (var doc = Loader.loadPDF(pdf, IOUtils.createTempFileOnlyStreamCache())) {
            assertDocumentAccessible(doc, title);
            var catalog = doc.getDocumentCatalog();
            var existing = catalog.getDocumentOutline();
            var item = new PDOutlineItem();
            item.setTitle(title);
            if (doc.getNumberOfPages() > 0) {
                item.setDestination(doc.getPage(0));
            }
            if (existing != null && existing.hasChildren()) {
                // Re-parent the existing top-level items under the new item (closed) | 기존 최상위 항목을 새 항목 아래로 (접힌 상태)
                var children = new ArrayList<PDOutlineItem>();
                existing.children().forEach(children::add);
                item.getCOSObject().setItem(COSName.FIRST, children.get(0));
                item.getCOSObject().setItem(COSName.LAST, children.get(children.size() - 1));
                for (var child : children) {
                    child.getCOSObject().setItem(COSName.PARENT, item);
                }
                item.getCOSObject().setInt(COSName.COUNT, -children.size());
            }
            var outline = new PDDocumentOutline();
            outline.addLast(item);
            catalog.setDocumentOutline(outline);
            doc.save(copy.toFile());
        }
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
    static void stampWatermark(PDDocument document, Watermark watermark) throws IOException {
        PDImageXObject image;
        try {
            if (isJpeg(watermark.image)) {
                checkImageSize(watermark.image);
                image = JPEGFactory.createFromByteArray(document, watermark.image);
            } else {
                image = LosslessFactory.createFromImage(document, decodeImage(watermark.image));
            }
        } catch (IOException e) {
            throw new IOException("워터마크 이미지를 읽을 수 없습니다: " + e.getMessage(), e);
        }
        var size = watermark.drawSize(image.getWidth(), image.getHeight());
        var state = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(watermark.opacity);
        state.setStrokingAlphaConstant(watermark.opacity);
        int totalPages = document.getNumberOfPages();
        int pageIdx = 0;
        for (var page : document.getPages()) {
            if (!watermark.appliesToPage(pageIdx++, totalPages)) {
                continue;
            }
            var box = page.getCropBox();
            var rotation = ((page.getRotation() % 360) + 360) % 360;
            var quarter = rotation == 90 || rotation == 270;
            // Position on the page as shown (width and height swap for a quarter turn) | 화면에 보이는 쪽 기준 위치 (90·270도는 가로·세로가 바뀜)
            var shown = new PDRectangle(quarter ? box.getHeight() : box.getWidth(),
                    quarter ? box.getWidth() : box.getHeight());
            var origin = watermark.origin(shown, size[0], size[1]);
            try (var contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND,
                    true, true)) {
                contentStream.setGraphicsStateParameters(state);
                contentStream.transform(shownToPage(box, rotation));
                float rot = watermark.getRotation();
                if (rot != 0f) {
                    float cx = origin[0] + size[0] / 2.0f;
                    float cy = origin[1] + size[1] / 2.0f;
                    contentStream.transform(Matrix.getTranslateInstance(cx, cy));
                    contentStream.transform(Matrix.getRotateInstance(Math.toRadians(rot), 0, 0));
                    contentStream.transform(Matrix.getTranslateInstance(-size[0] / 2.0f, -size[1] / 2.0f));
                    contentStream.drawImage(image, 0, 0, size[0], size[1]);
                } else {
                    contentStream.drawImage(image, origin[0], origin[1], size[0], size[1]);
                }
            }
        }
    }

    /**
     * Maps coordinates on the page as shown (origin at the shown lower-left corner) to the page's own space, for a page
     * displayed turned clockwise by {@code rotation} degrees, so a watermark lands where it is meant to and stays
     * upright | 시계 방향으로 rotation 도 돌려 보이는 쪽에서, 보이는 쪽 좌표(보이는 왼쪽 아래가 원점)를 쪽 자체 좌표로 바꾼다. 워터마크가 의도한 곳에
     * 바로 선 채로 놓인다
     */
    private static org.apache.pdfbox.util.Matrix shownToPage(PDRectangle box, int rotation) {
        float llx = box.getLowerLeftX(), lly = box.getLowerLeftY(), w = box.getWidth(), h = box.getHeight();
        return switch (rotation) {
        case 90 -> new org.apache.pdfbox.util.Matrix(0, 1, -1, 0, llx + w, lly);
        case 180 -> new org.apache.pdfbox.util.Matrix(-1, 0, 0, -1, llx + w, lly + h);
        case 270 -> new org.apache.pdfbox.util.Matrix(0, -1, 1, 0, llx, lly + h);
        default -> new org.apache.pdfbox.util.Matrix(1, 0, 0, 1, llx, lly);
        };
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
    static void stampPageNumbers(PDDocument document, int startIndex, int count, float fontSize, PDFont font,
            String format) throws IOException {
        for (int n = 0; n < count; n++) {
            var page = document.getPage(startIndex + n);
            var text = String.format(format, n + 1, count);
            var textWidth = font.getStringWidth(text) / 1000 * fontSize;
            var box = page.getCropBox();
            var rotation = ((page.getRotation() % 360) + 360) % 360;
            // Bottom center of the page as shown, also on rotated pages | 회전된 쪽도 화면에 보이는 쪽의 아래 가운데
            var shownWidth = rotation == 90 || rotation == 270 ? box.getHeight() : box.getWidth();
            try (var contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND,
                    true, true)) {
                contentStream.transform(shownToPage(box, rotation));
                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                contentStream.newLineAtOffset((shownWidth - textWidth) / 2, 20F); // 하단 20pt, 가운데
                contentStream.showText(text);
                contentStream.endText();
            }
        }
    }


}
