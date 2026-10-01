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

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
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
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import io.github.devers2.s2util.core.S2StringUtil;
import io.github.devers2.s2util.core.S2ThreadUtil;
import io.github.devers2.s2util.core.S2Util;
import io.github.devers2.s2util.exception.S2RuntimeException;
import io.github.devers2.s2util.file.S2ResourceInputStream;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * <h1>S2PdfUtil (고성능 PDF 엔진 &amp; 범용 멀티 포맷 병합기)</h1>
 * <p>
 * HTML, 이미지(PNG/JPG/GIF/BMP/TIFF, WebP 는 imageio-webp 추가 시), 일반 텍스트, SVG 및 원격 URL 리소스를 고품질 PDF 문서로 변환하고,<br>
 * 다중 이종(異種) 문서 소스를 사용자가 지정한 순서 그대로 메모리 누수 없이 단일 PDF로 병합(Merge)하는 유틸리티 클래스입니다.
 * </p>
 *
 * <h2>✨ 핵심 기능 요약 (Key Features)</h2>
 * <ul>
 *   <li><b>HTML &rarr; PDF 변환:</b> 외부 CSS 파일, TTF/OTF 폰트, 인라인 Base64 이미지 및 CSS background-image 완벽 지원</li>
 *   <li><b>범용 다중 포맷 병합 (Universal Merge):</b> PDF, HTML, 이미지, 텍스트, SVG, URL 소스를 원하는 순서대로 1개의 PDF로 결합</li>
 *   <li><b>원격 URL 비동기 분산 프리페치 (Async Distributed Pre-fetch):</b> 복수의 URL 소스를 {@link S2ThreadUtil#getCommonExecutor()} 기반
 *       백그라운드 병렬 다운로드하여 네트워크 대기 시간을 최소화하면서도, 원래의 리스트 순서를 100% 보장 결합</li>
 *   <li><b>메모리 및 자원 누수 제로 (Zero Leak Architecture):</b>
 *     <ul>
 *       <li>대용량 병합 시 PDFBox 디스크 캐시(createTempFileOnlyStreamCache) 사용으로 JVM Heap OOM 원천 방지</li>
 *       <li>변환 시 사용된 중간 임시 파일은 성공/실패 여부와 무관하게 즉시 삭제 (반환 스트림의 임시 파일은 close 또는 GC 시 삭제)</li>
 *       <li>HTML 렌더링 시 이미지·CSS 는 클래스패스에서 읽어 인라인하며, 렌더러는 HTML 에 적힌 원격/로컬 URL 을 가져오지 않는다 (SSRF 방지).
 *           URL 로 받은 HTML 페이지는 예외로, 화면 그대로 나오도록 페이지의 이미지·스타일시트를 받아 넣는다 (같은 출처 또는 공개 주소만, 내부망 차단)</li>
 *       <li>최종 결과는 {@link S2ResourceInputStream}으로 반환되어 호출자가 {@code close()} 시 결과 임시 파일 자동 삭제</li>
 *       <li>이미지 렌더링 후 {@link java.awt.image.BufferedImage#flush()}를 호출하여 네이티브 메모리 버퍼 즉시 해제</li>
 *     </ul>
 *   </li>
 *   <li><b>PDF &rarr; 이미지 변환 (Preview / Thumbnail):</b> 고해상도 DPI 지정 및 다중 페이지 콜백 렌더링 지원</li>
 *   <li><b>페이지 번호 자동 각인 (Page Numbering):</b> 원하는 위치 및 TTF 폰트로 "1 / N" 형식 페이지 번호 일괄 삽입</li>
 * </ul>
 *
 * <h2>🚀 빠른 사용 예시 (Quick Start)</h2>
 * <pre>{@code
 * // [예제 1] 원격 표지 JSP/HTML URL + 본문 PDF 스트림 병합
 * try (InputStream merged = S2PdfUtil.mergeHtmlUrlsAndPdfs(
 *         List.of("https://example.com/report/cover.jsp?noteId=123"),
 *         List.of(notePdfStream1, notePdfStream2)
 * )) {
 *     // merged 스트림을 클라이언트에 다운로드 응답 (close 시 임시 파일 자동 정리)
 *     IOUtils.copy(merged, response.getOutputStream());
 * }
 *
 * // [예제 2] 이종(異種) 문서 소스 순서 보장 병합
 * try (InputStream merged = S2PdfUtil.merge(
 *         PdfSource.ofHtml("<h1>1. 보고서 표지</h1>"),
 *         PdfSource.ofPdf(pdfFile),
 *         PdfSource.ofImage(chartImageFile),
 *         PdfSource.ofText("3. 첨부 텍스트 로그"),
 *         PdfSource.ofUrl("https://api.example.com/chart.png")
 * )) {
 *     // 5가지 타입의 문서가 지정된 순서대로 정확하게 결합됨
 * }
 * }</pre>
 *
 * @author devers2
 * @version 1.5
 * @since 1.0
 * @see S2ResourceInputStream
 * @see PdfSource
 */
public class S2PdfUtil {

    static {
        // S2PdfUtil 관련 필수 의존성 유무를 체크
        S2Util.checkDependency("S2PdfUtil", "org.jsoup.Jsoup", "org.jsoup:jsoup:1.23.2");
        S2Util.checkDependency("S2PdfUtil", "com.openhtmltopdf.pdfboxout.PdfRendererBuilder",
                "io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.85");
    }

    private static final S2Logger logger = S2LogManager.getLogger(S2PdfUtil.class);

    private static final String DEFAULT_FONT_FAMILY = "ConvertPDF";
    private static final String DEFAULT_FONT_SIZE = "10pt";

    private static final String HTML_TEMPLATE = "<!DOCTYPE html>\n" +
            "<html lang=\"ko\">\n" +
            "    <head>\n" +
            "        <style>%s\n" +
            "            body {\n" +
            "                font-family: " + DEFAULT_FONT_FAMILY + ";\n" +
            "                font-size: " + DEFAULT_FONT_SIZE + ";\n" +
            "            }\n" +
            // Monospace text falls back to the default font for Korean glyphs | 고정폭 글자도 한글은 기본 폰트로 대체
            "            pre, code, kbd, samp, tt {\n" +
            "                font-family: monospace, " + DEFAULT_FONT_FAMILY + ";\n" +
            "            }\n" +
            "        </style>\n" +
            "    </head>\n" +
            "    <body>%s</body>\n" +
            "</html>";

    /**
     * HTML InputStream 을 Base64 인코딩된 PDF 문자열로 변환한다.
     * <p>
     * HTML 내의 이미지 경로(img src, background-image)를 Base64 인라인 데이터로 자동 치환하며,
     * 외부 CSS 및 TTF/OTF 폰트를 적용하여 표준 A4 규격 PDF로 렌더링합니다.<br>
     * 입력 스트림은 작업 완료 후 {@code finally} 블록에서 자동으로 닫힙니다.
     * </p>
     *
     * @param htmlInputStream                          변환할 HTML 입력 스트림 (작업 완료 후 자동 close)
     * @param staticResourceBasePath                   정적 자원 웹 기본 경로 (예: 이미지 경로가 {@code /static/images/a.png}인 경우 {@code "/static"})
     * @param cssPath                                  적용할 CSS 클래스패스 파일 경로 (복수 개인 경우 쉼표(,)로 구분)
     * @param fontPath                                 적용할 한글/영문 TTF/OTF 폰트 클래스패스 경로
     * @param clazz                                    정적 자원(CSS, 폰트)을 클래스패스에서 로드하기 위한 기준 Class (보통 {@code getClass()})
     * @param convertCssBackgroundImageTargetSelectors CSS {@code background-image}를 Base64로 치환할 특정 CSS 셀렉터 (생략 시 전체 대상)
     * @return Base64 인코딩된 PDF 문자열 (웹 화면에서 {@code data:application/pdf;base64,...} 등으로 바로 사용 가능)
     * @throws IOException 폰트 로드 실패 또는 변환 중 오류 발생 시
     * @apiNote
     * <pre>{@code
     * String base64Pdf = S2PdfUtil.convertHtmlToPdf(
     *         htmlInputStream,
     *         "/static/public",
     *         "/static/css/style.css",
     *         "/static/font/NanumGothic.ttf",
     *         getClass(),
     *         ".cover-image-container"
     * );
     * }</pre>
     */
    public static <T> String convertHtmlToPdf(InputStream htmlInputStream, String staticResourceBasePath,
            String cssPath, String fontPath, Class<T> clazz, String... convertCssBackgroundImageTargetSelectors)
            throws IOException {
        try {
            var htmlContent = new String(IOUtils.toByteArray(htmlInputStream), StandardCharsets.UTF_8);
            return convertHtmlToPdf(htmlContent, staticResourceBasePath, cssPath, fontPath, clazz,
                    convertCssBackgroundImageTargetSelectors);
        } finally {
            if (htmlInputStream != null) {
                try {
                    htmlInputStream.close();
                } catch (IOException e) {
                    // 로그 처리
                    logger.error("HTML InputStream 닫기 실패: ", e);
                }
            }
        }
    }

    /**
     * HTML 문자열을 Base64 인코딩된 PDF 문자열로 변환한다.
     * <p>
     * HTML 내의 이미지 경로(img src, background-image)를 Base64 인라인 데이터로 자동 치환하며,
     * 외부 CSS 및 TTF/OTF 폰트를 적용하여 표준 A4 규격 PDF로 렌더링합니다.
     * </p>
     *
     * @param htmlContent                              변환할 원본 HTML 문자열
     * @param staticResourceBasePath                   정적 자원 웹 기본 경로 (예: 이미지 경로가 {@code /static/images/a.png}인 경우 {@code "/static"})
     * @param cssPath                                  적용할 CSS 클래스패스 파일 경로 (복수 개인 경우 쉼표(,)로 구분)
     * @param fontPath                                 적용할 한글/영문 TTF/OTF 폰트 클래스패스 경로
     * @param clazz                                    정적 자원(CSS, 폰트)을 클래스패스에서 로드하기 위한 기준 Class (보통 {@code getClass()})
     * @param convertCssBackgroundImageTargetSelectors CSS {@code background-image}를 Base64로 치환할 특정 CSS 셀렉터 (생략 시 전체 대상)
     * @return Base64 인코딩된 PDF 문자열
     * @throws IOException 폰트 로드 실패 또는 변환 중 오류 발생 시
     * @apiNote
     * <pre>{@code
     * String base64Pdf = S2PdfUtil.convertHtmlToPdf(
     *         "<h1>보고서</h1><p>내용...</p>",
     *         "/static/public",
     *         "/static/css/style.css",
     *         "/static/font/NanumGothic.ttf",
     *         getClass(),
     *         ".cover-image-container"
     * );
     * }</pre>
     */
    public static <T> String convertHtmlToPdf(String htmlContent, String staticResourceBasePath, String cssPath,
            String fontPath, Class<T> clazz, String... convertCssBackgroundImageTargetSelectors) throws IOException {
        // 이미지 Base64 인코딩 및 HTML 포함
        var htmlWithImages = embedImages(htmlContent, staticResourceBasePath, convertCssBackgroundImageTargetSelectors);

        var cssContent = S2Util.isNotEmpty(cssPath)
                ? loadCssContent(clazz, cssPath, staticResourceBasePath, convertCssBackgroundImageTargetSelectors)
                : "";

        var completeHtml = composeHtml(htmlContent, htmlWithImages, cssContent);

        var xhtmlContent = convertToXhtml(completeHtml);
        var pdfBytes = createPdf(xhtmlContent, clazz, fontPath);
        return Base64.getEncoder().encodeToString(pdfBytes);
    }

    private static String embedImages(String htmlContent, String staticResourceBasePath,
            String... convertCssBackgroundImageTargetSelectors) {
        try {
            var doc = Jsoup.parse(htmlContent);

            // 1. <img> 태그 처리
            for (var img : doc.select("img")) {
                var src = img.attr("src");
                src = embedImage(src, staticResourceBasePath);
                if (src != null) {
                    img.attr("src", src);
                }
            }

            // 2. style 태그 내의 background-image 처리
            for (var style : doc.select("[style]")) {
                var styleAttr = style.attr("style");
                if (styleAttr.contains("background-image")) {
                    styleAttr = processCssBackgroundImages(styleAttr, staticResourceBasePath,
                            convertCssBackgroundImageTargetSelectors);
                    style.attr("style", styleAttr);
                }
            }

            // 3. <style> 태그 내의 background-image 처리
            for (var styleTag : doc.select("style")) {
                var cssContent = styleTag.html();
                cssContent = processCssBackgroundImages(cssContent, staticResourceBasePath,
                        convertCssBackgroundImageTargetSelectors);
                styleTag.html(cssContent);
            }

            return doc.html();
        } catch (Exception e) {
            logger.error("HTML 파싱 오류: ", e);
            return htmlContent;
        }
    }

    private static String embedImage(String src, String staticResourceBasePath) { // 이미지 처리 공통 로직
        if (src == null || src.isBlank() || src.startsWith("data:")) {
            return src; // 이미 Base64 인코딩된 이미지, 빈 값(받지 못해 뺀 원격 이미지 등) 또는 null
        }

        if (src.contains(":") || src.startsWith("//")) {
            // Remote (http:, https:, //host) and other schemes are not embedded, and the renderer blocks them
            // | 원격(http:, https:, //host) 및 기타 스킴은 인라인하지 않으며 렌더러도 차단함
            logger.warn("원격/스킴 이미지는 PDF 에 포함하지 않습니다: {}", src);
            return null;
        }
        // Drop "?v=1" / "#x" so the extension check sees the file name | 확장자 확인을 위해 쿼리·프래그먼트 제거
        var resourcePath = src.replaceFirst("[?#].*$", "");
        var mimeType = getMimeType(resourcePath.toLowerCase());
        if (mimeType == null) {
            logger.warn("이미지 확장자가 아니어서 PDF 에 포함하지 않습니다: {}", src);
            return null;
        }

        var absolutePath = classpathImagePath(resourcePath);

        // 로컬 이미지 처리 (클래스패스 기준)
        try (InputStream imageStream = S2PdfUtil.class
                .getResourceAsStream(S2FileUtil.joinPaths(staticResourceBasePath, absolutePath))) {
            if (imageStream == null) {
                logger.error("이미지 파일을 찾을 수 없습니다: {}", absolutePath);
                return null;
            }
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(IOUtils.toByteArray(imageStream));
        } catch (IOException e) {
            logger.error("이미지 처리 오류: {}", src, e);
            return null;
        }
    }

    /** A relative image path as a classpath path | 상대 이미지 경로를 클래스패스 경로로 */
    private static String classpathImagePath(String resourcePath) {
        var absolutePath = resourcePath.startsWith("../") ? resourcePath.replace("../", "/") : resourcePath;
        return absolutePath.startsWith("/") ? absolutePath : "/" + absolutePath;
    }

    /**
     * Whether a relative image path of a remote page is served from the classpath (staticResourceBasePath set), so it
     * is not fetched | 원격 페이지의 상대 이미지 경로가 클래스패스에 있는지 (있으면 받지 않음)
     */
    private static boolean classpathImageExists(String ref, String staticResourceBasePath) {
        if (S2Util.isEmpty(staticResourceBasePath)) {
            return false;
        }
        var resourcePath = ref.replaceFirst("[?#].*$", "");
        return S2PdfUtil.class.getResource(S2FileUtil.joinPaths(staticResourceBasePath, classpathImagePath(resourcePath))) != null;
    }

    /**
     * css 파일의 배경 이미지를 처리한다.
     *
     * @param css CSS 파일
     * @return 처리 문자열
     */
    private static String processCssBackgroundImages(String css, String staticResourceBasePath,
            String... convertCssBackgroundImageTargetSelectors) {
        // 셀렉터가 없으면 전체 CSS에서 background 및 background-image 처리
        if (convertCssBackgroundImageTargetSelectors == null || convertCssBackgroundImageTargetSelectors.length == 0) {
            return processBackgroundImageUrls(css, staticResourceBasePath);
        }

        // 특정 셀렉터에 대해서만 처리
        var result = new StringBuilder(css);

        // 모든 대상 셀렉터에 대해 처리
        for (var selector : convertCssBackgroundImageTargetSelectors) {
            // CSS 블록을 찾는 정규식
            var blockRegex = selector + "\\s*\\{[^}]*\\}";
            var blockPattern = Pattern.compile(blockRegex);
            var blockMatcher = blockPattern.matcher(result);

            var tempResult = new StringBuilder();
            while (blockMatcher.find()) {
                var cssBlock = blockMatcher.group();
                var processedBlock = processBackgroundImageUrls(cssBlock, staticResourceBasePath);
                blockMatcher.appendReplacement(tempResult, S2StringUtil.replaceChars(processedBlock, "\\$", '$'));
            }
            blockMatcher.appendTail(tempResult);

            // 처리된 결과로 업데이트
            result = new StringBuilder(tempResult);
        }

        return result.toString();
    }

    /**
     * CSS 문자열에서 background-image 또는 background URL 을 찾아 Base64로 변환한다.
     *
     * @param css                    CSS 문자열
     * @param staticResourceBasePath 정적 자원 시작 경로 (이미지 경로가 /static/images 라면 "/static")
     * @return 처리된 CSS 문자열
     */
    private static String processBackgroundImageUrls(String css, String staticResourceBasePath) {
        // background-image와 background 속성을 모두 처리하는 정규식
        var regex = "(background-image|background):\\s*([^;}]*)";
        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(css);

        var result = new StringBuilder();
        while (matcher.find()) {
            var property = matcher.group(1); // "background-image" 또는 "background"
            var value = matcher.group(2).trim(); // 속성 값

            if (property.equals("background-image")) {
                // background-image 처리: 단일 URL만 포함 가능
                var urlRegex = "url\\(['\"]?(.*?)['\"]?\\)";
                var urlPattern = Pattern.compile(urlRegex);
                var urlMatcher = urlPattern.matcher(value);
                if (urlMatcher.find()) {
                    var imagePath = urlMatcher.group(1);
                    var embeddedImage = embedImage(imagePath, staticResourceBasePath);
                    if (embeddedImage != null) {
                        matcher.appendReplacement(result, "background-image: url('"
                                + S2StringUtil.replaceChars(embeddedImage, "\\$", '$') + "')");
                    }
                }
            } else if (property.equals("background")) {
                // background 처리: gradient와 여러 URL 포함 가능
                var processedValue = processGradientUrls(value, staticResourceBasePath);
                matcher.appendReplacement(result, "background: " + processedValue);
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * gradient 함수 내의 url()을 처리하여 Base64로 변환한다.
     *
     * @param value                  CSS 속성 값 (background 값)
     * @param staticResourceBasePath 정적 자원 시작 경로
     * @return 처리된 값
     */
    private static String processGradientUrls(String value, String staticResourceBasePath) {
        // gradient 패턴: linear-gradient(...) 등
        var gradientRegex = "(linear-gradient\\([^)]*\\))";
        var gradientPattern = Pattern.compile(gradientRegex);
        var gradientMatcher = gradientPattern.matcher(value);

        var tempValue = new StringBuilder();
        var lastEnd = 0;

        while (gradientMatcher.find()) {
            var gradient = gradientMatcher.group(1); // linear-gradient(...)
            tempValue.append(value.substring(lastEnd, gradientMatcher.start())); // gradient 전 부분 추가

            // gradient 내부의 url() 처리
            var urlRegex = "url\\(['\"]?(.*?)['\"]?\\)";
            var urlPattern = Pattern.compile(urlRegex);
            var urlMatcher = urlPattern.matcher(gradient);

            var gradientBuffer = new StringBuilder();
            var gradientLastEnd = 0;
            while (urlMatcher.find()) {
                gradientBuffer.append(gradient.substring(gradientLastEnd, urlMatcher.start()));
                var imagePath = urlMatcher.group(1);
                var embeddedImage = embedImage(imagePath, staticResourceBasePath);
                if (embeddedImage != null) {
                    gradientBuffer.append("url('").append(S2StringUtil.replaceChars(embeddedImage, "\\$", '$'))
                            .append("')");
                } else {
                    gradientBuffer.append(urlMatcher.group(0)); // 변환 실패 시 원래 값 유지
                }
                gradientLastEnd = urlMatcher.end();
            }
            gradientBuffer.append(gradient.substring(gradientLastEnd));
            tempValue.append(gradientBuffer.toString());

            lastEnd = gradientMatcher.end();
        }
        tempValue.append(value.substring(lastEnd));

        // gradient 외부의 url() 처리
        var urlRegex = "url\\(['\"]?(.*?)['\"]?\\)";
        var urlPattern = Pattern.compile(urlRegex);
        var urlMatcher = urlPattern.matcher(tempValue.toString());

        var finalValue = new StringBuilder();
        lastEnd = 0;
        while (urlMatcher.find()) {
            finalValue.append(tempValue.substring(lastEnd, urlMatcher.start()));
            var imagePath = urlMatcher.group(1);
            var embeddedImage = embedImage(imagePath, staticResourceBasePath);
            if (embeddedImage != null) {
                finalValue.append("url('").append(S2StringUtil.replaceChars(embeddedImage, "\\$", '$')).append("')");
            } else {
                finalValue.append(urlMatcher.group(0)); // 변환 실패 시 원래 값 유지
            }
            lastEnd = urlMatcher.end();
        }
        finalValue.append(tempValue.substring(lastEnd));

        return finalValue.toString();
    }

    private static <T> String loadCssContent(Class<T> clazz, String cssPath, String staticResourceBasePath,
            String... convertCssBackgroundImageTargetSelectors)
            throws IOException {
        var combinedCssContent = new StringBuilder();

        if (cssPath != null) {
            String[] cssPaths = cssPath.split(",");
            for (var path : cssPaths) {
                if (S2Util.isNotEmpty(path)) {
                    path = path.trim();
                    try (var inputStream = clazz.getResourceAsStream(path)) {
                        if (inputStream == null) {
                            throw new IOException("CSS 파일을 찾을 수 없습니다: " + path);
                        }
                        var cssContent = new String(IOUtils.toByteArray(inputStream), StandardCharsets.UTF_8);
                        // !!s2!! clear: both; 속성 제거 (해당 속성이 있는 경우 PDF 변환 오류 발생)
                        cssContent = S2StringUtil.replaceAll(cssContent, "clear\\s*:\\s*both\\s*;", "");
                        combinedCssContent.append(processCssBackgroundImages(cssContent, staticResourceBasePath,
                                convertCssBackgroundImageTargetSelectors));
                    }
                }
            }
        }

        return combinedCssContent.toString();
    }

    /**
     * XML namespace prefixes that HTML never declares break the XHTML parse: prefixed attributes ({@code v-on:click},
     * {@code :class}) are removed ({@code xlink:} inside {@code <svg>} is kept and declared), and prefixed elements
     * ({@code <o:p>} in HTML saved from Word) are unwrapped, keeping their content | 선언되지 않은 XML 접두어는 XHTML 파싱을
     * 깨뜨린다. 접두어 속성은 지우고(svg 안의 {@code xlink:} 는 선언하고 유지), 접두어 요소(워드에서 저장한 HTML 의 {@code <o:p>})는 내용을
     * 남기고 벗긴다
     */
    private static void removeUnboundPrefixes(Document document) {
        for (var element : document.getAllElements()) {
            var prefixed = new ArrayList<String>();
            for (var attribute : element.attributes()) {
                var key = attribute.getKey();
                if (key.indexOf(':') >= 0 && !key.startsWith("xml:") && !key.startsWith("xmlns")) {
                    prefixed.add(key);
                }
            }
            for (var key : prefixed) {
                // SVG drawing (Batik, SVG 1.1) needs xlink:href, so inside <svg> it stays with its prefix declared
                // | SVG 그리기(Batik, SVG 1.1)는 xlink:href 가 필요하므로 svg 안에서는 접두어를 선언하고 유지
                var svg = element.tagName().equalsIgnoreCase("svg") ? element
                        : element.parents().stream().filter(p -> p.tagName().equalsIgnoreCase("svg")).reduce((a, b) -> b)
                                .orElse(null);
                if (key.toLowerCase(Locale.ROOT).startsWith("xlink:") && svg != null) {
                    svg.attr("xmlns:xlink", "http://www.w3.org/1999/xlink");
                    continue;
                }
                element.removeAttr(key);
            }
        }
        for (var element : document.getAllElements()) {
            if (element.tagName().indexOf(':') > 0 && element.parent() != null) {
                element.unwrap();
            }
        }
        removeExternalSvgReferences(document);
    }

    /**
     * SVG elements that point outside the document ({@code <image>}, {@code <use>}, {@code <feImage>} with an http: or
     * file: address) are removed: the drawer refuses them, and one refused reference blanks the whole SVG. The rest get
     * {@code xlink:href} | 문서 밖을 가리키는 SVG 요소를 지운다. 그리기 도구가 거부하며, 거부된 참조 하나가 SVG 전체를 비운다. 나머지는
     * xlink:href 로 맞춘다
     */
    static void removeExternalSvgReferences(org.jsoup.nodes.Element root) {
        for (var element : root.select("svg image, svg use, svg feImage, svg feimage")) {
            var href = element.hasAttr("xlink:href") ? element.attr("xlink:href") : element.attr("href");
            var trimmed = href.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("data:")) {
                element.remove();
                continue;
            }
            // The drawer (SVG 1.1) reads xlink:href only, not the SVG 2 href | 그리기 도구(SVG 1.1)는 SVG 2 의 href 가 아닌 xlink:href 만 읽음
            if (element.hasAttr("href") && !element.hasAttr("xlink:href")) {
                element.attr("xlink:href", href);
                element.removeAttr("href");
            }
            element.parents().stream().filter(p -> p.tagName().equalsIgnoreCase("svg"))
                    .forEach(svg -> svg.attr("xmlns:xlink", "http://www.w3.org/1999/xlink"));
        }
    }

    /** A whole page (not a fragment): it has its own html, head or body element | 조각이 아닌 전체 문서 */
    private static final Pattern FULL_DOCUMENT = Pattern.compile("(?i)<(html|head|body)[\\s>]");

    /** Base rules placed first in a whole page's head, so the page's own CSS wins | 전체 문서의 head 맨 앞 기본 규칙 */
    private static final String PAGE_BASE_CSS = "body { font-family: " + DEFAULT_FONT_FAMILY + ", sans-serif; }\n"
            + "pre, code, kbd, samp, tt { font-family: monospace, " + DEFAULT_FONT_FAMILY + "; }\n";

    /**
     * Fragments go into the template body. Whole pages keep their own head (their CSS would otherwise end up inside
     * the template body): the base rules go first and the configured CSS last, so it overrides the page. Every
     * font-family also falls back to the default font so fonts the server lacks do not turn Korean into "#".
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 조각은 템플릿 body 에 넣는다. 전체 문서는 자신의 head 를 유지한다(템플릿 body 에 넣으면 페이지 CSS 가 body 안에 들어감). 기본 규칙은
     * 맨 앞, 지정한 CSS 는 맨 뒤에 두어 페이지를 덮어쓴다. 모든 font-family 에 기본 폰트를 대체 폰트로 붙여, 서버에 없는 폰트로 지정된 한글이
     * "#" 으로 나오지 않게 한다.
     */
    private static String composeHtml(String originalHtml, String htmlWithImages, String cssContent) {
        Document doc;
        if (originalHtml != null && FULL_DOCUMENT.matcher(originalHtml).find()) {
            doc = Jsoup.parse(htmlWithImages);
            doc.head().prependElement("style").appendChild(new DataNode(PAGE_BASE_CSS));
            if (S2Util.isNotEmpty(cssContent)) {
                doc.head().appendElement("style").appendChild(new DataNode(cssContent));
            }
        } else {
            doc = Jsoup.parse(String.format(HTML_TEMPLATE, cssContent, htmlWithImages));
        }
        for (var style : doc.select("style")) {
            var css = addFontFallback(style.data());
            style.empty();
            style.appendChild(new DataNode(css));
        }
        for (var styled : doc.select("[style]")) {
            styled.attr("style", addFontFallback(styled.attr("style")));
        }
        return doc.outerHtml();
    }

    private static final Pattern FONT_DECLARATION = Pattern.compile("(?i)(?<![\\w-])(font-family|font)(\\s*:\\s*)([^;{}]*)");
    private static final Pattern IMPORTANT = Pattern.compile("(?i)\\s*!\\s*important\\s*$");

    /**
     * Appends the default font to font-family lists (and to font shorthands with a size) | font-family 목록(크기가 있는
     * font 단축 속성 포함)에 기본 폰트를 덧붙임
     */
    static String addFontFallback(String css) {
        var matcher = FONT_DECLARATION.matcher(css);
        var out = new StringBuilder();
        while (matcher.find()) {
            var value = matcher.group(3);
            var important = IMPORTANT.matcher(value);
            var suffix = "";
            if (important.find()) {
                suffix = value.substring(important.start());
                value = value.substring(0, important.start());
            }
            var trimmed = value.trim().toLowerCase(Locale.ROOT);
            var keywordOnly = trimmed.isEmpty() || trimmed.matches("inherit|initial|unset|revert|revert-layer"
                    + "|caption|icon|menu|message-box|small-caption|status-bar");
            var shorthandWithoutSize = matcher.group(1).equalsIgnoreCase("font") && !trimmed.matches(".*\\d.*");
            var replacement = matcher.group(0);
            if (!keywordOnly && !shorthandWithoutSize && !trimmed.contains(DEFAULT_FONT_FAMILY.toLowerCase(Locale.ROOT))) {
                var end = value.length();
                while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
                    end--;
                }
                replacement = matcher.group(1) + matcher.group(2) + value.substring(0, end) + ", " + DEFAULT_FONT_FAMILY
                        + value.substring(end) + suffix;
            }
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * HTML to well-formed XHTML for the renderer. Parsed as HTML, so void elements such as {@code <img>} are closed;
     * an XML parse would nest everything after an unclosed {@code <img>} inside it, and that content vanished
     * | 렌더러용 XHTML 로 변환. HTML 로 파싱해 {@code <img>} 같은 빈 요소를 닫는다. XML 로 파싱하면 닫히지 않은 {@code <img>} 뒤의
     * 내용이 모두 그 안으로 들어가 사라졌음
     */
    private static String convertToXhtml(String html) {
        var document = Jsoup.parse(html);
        removeUnboundPrefixes(document);
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml);
        return document.html();
    }

    private static <T> byte[] createPdf(String htmlContent, Class<T> clazz, String fontPath) throws IOException {
        try (var outputStream = new ByteArrayOutputStream()) {
            createPdf(htmlContent, clazz, fontPath, outputStream);
            return outputStream.toByteArray();
        }
    }

    /** Renders straight into the stream (a file in merge), so the PDF is not held in memory | 스트림(병합 시 파일)으로 바로 렌더링 */
    private static void createPdf(String htmlContent, Class<?> clazz, String fontPath, OutputStream outputStream)
            throws IOException {
        var builder = new PdfRendererBuilder();
        builder.withHtmlContent(htmlContent, null);
        // Only inline data: URIs are loaded; images and CSS were already embedded, so the renderer never fetches
        // http:, file: or other URLs written in the HTML (SSRF, local file read) | data: URI 만 로드. 이미지·CSS 는 이미 인라인되어
        // 있으므로 렌더러가 HTML 의 http:, file: 등 URL 을 직접 가져오지 않음 (SSRF, 로컬 파일 읽기 방지)
        builder.useUriResolver((baseUri, uri) -> uri != null && uri.startsWith("data:") ? uri : null);
        var svgDrawer = newSvgDrawer();
        if (svgDrawer != null) {
            builder.useSVGDrawer(svgDrawer);
        } else if (htmlContent.contains("<svg") || htmlContent.contains("image/svg+xml")) {
            if (SVG_WARNED.compareAndSet(false, true)) {
                logger.warn("SVG 는 그리지 않고 비워 둡니다. " + SVG_SUPPORT_REQUIRED);
            }
        }

        var font = resolveFont(clazz, fontPath);
        requireFontFor(htmlContent, font != null);
        if (font != null) {
            var fontBytes = font.bytes();
            builder.useFont(() -> new ByteArrayInputStream(fontBytes), DEFAULT_FONT_FAMILY);
        }

        builder.toStream(outputStream);
        builder.run();
    }

    // ------------------------------------------------------------------------
    // Web pages through a browser (optional s2-chrome) | 브라우저로 웹 페이지 변환 (선택 s2-chrome)

    /** Set by setBrowserCommand; never re-detected | setBrowserCommand 로 지정한 명령 */
    private static volatile List<String> configuredBrowserCommand;
    private static volatile List<String> detectedBrowserCommand;
    private static volatile boolean browserEnabled = true;
    private static volatile Duration browserTimeout = Duration.ofSeconds(60);

    /**
     * 웹 페이지(URL 로 받은 HTML)를 PDF 로 인쇄할 브라우저 명령을 정한다. 지정하지 않으면 환경 변수 {@code S2_CHROME}, PATH 의 {@code s2-chrome},
     * {@code /usr/local/bin/s2-chrome} 순으로 찾는다 ({@code s2-chrome} 은 _devtools2
     * {@code scripts/linux/setup-projects/s2/s2-office-converter/setup-s2-office-converter.sh} 가 설치하는, 네트워크가 끊긴 컨테이너의 Chromium).
     * <p>
     * 명령은 {@code <명령> --print-to-pdf <출력 PDF> <입력 HTML>} 형식을 받아야 한다. 입력 HTML 은 이미지·CSS 를 모두 넣은 파일이며, 명령이 네트워크에
     * 접근하지 않아야 한다 (페이지의 JavaScript 가 내부망을 조회하지 못하도록). 그래서 PATH 의 일반 chrome/chromium 은 자동으로 쓰지 않는다.
     * </p>
     *
     * @param command 명령과 앞부분 인자
     */
    public static void setBrowserCommand(String... command) {
        if (command == null || command.length == 0 || command[0] == null || command[0].isBlank()) {
            throw new IllegalArgumentException("명령이 비었습니다.");
        }
        configuredBrowserCommand = List.of(command);
    }

    /** 지정한 브라우저 명령을 지우고 자동 탐색으로 되돌린다. */
    public static void resetBrowserCommand() {
        configuredBrowserCommand = null;
        detectedBrowserCommand = null;
    }

    /**
     * 브라우저 변환을 쓸지 정한다 (기본 true). false 이면 브라우저가 있어도 내장 렌더러(openhtmltopdf)로 변환한다.
     *
     * @param enabled 사용 여부
     */
    public static void setBrowserRenderingEnabled(boolean enabled) {
        browserEnabled = enabled;
    }

    /**
     * 페이지 한 건의 브라우저 변환 제한 시간 (기본 60초). 넘으면 브라우저를 끝내고 내장 렌더러로 변환한다.
     *
     * @param timeout 제한 시간
     */
    public static void setBrowserTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("제한 시간은 0 보다 커야 합니다: " + timeout);
        }
        browserTimeout = timeout;
    }

    /**
     * 웹 페이지를 브라우저로 변환할 수 있는지 (명령이 있는지만 보며 실행하지는 않음). 없으면 내장 렌더러로 변환한다.
     *
     * @return 브라우저 명령이 있고 사용하도록 되어 있으면 true
     */
    public static boolean isBrowserRenderingAvailable() {
        var command = resolveBrowserCommand();
        return browserEnabled && command != null && commandExists(command.get(0));
    }

    private static List<String> resolveBrowserCommand() {
        var configured = configuredBrowserCommand;
        if (configured != null) {
            return configured;
        }
        var detected = detectedBrowserCommand;
        if (detected != null && commandExists(detected.get(0))) {
            return detected;
        }
        detected = detectBrowserCommand();
        if (detected != null && !detected.equals(detectedBrowserCommand)) {
            logger.info("웹 페이지 변환 명령: {}", detected);
        }
        detectedBrowserCommand = detected;
        return detected;
    }

    private static List<String> detectBrowserCommand() {
        var fromEnv = System.getenv("S2_CHROME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return List.of(fromEnv.trim());
        }
        var found = findOnPath("s2-chrome");
        if (found != null) {
            return List.of(found.toString());
        }
        var fixed = Path.of("/usr/local/bin/s2-chrome");
        return Files.isExecutable(fixed) ? List.of(fixed.toString()) : null;
    }

    /** Browser print setup: A4, and backgrounds kept as on screen | 브라우저 인쇄 설정: A4, 배경은 화면처럼 유지 */
    private static final String BROWSER_PAGE_CSS = "@page { size: A4; }\n"
            + "html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }\n";

    /**
     * Renders a web page: through the browser when one is available, otherwise, or when the browser fails, with the
     * built-in renderer | 웹 페이지 변환. 브라우저가 있으면 브라우저로, 없거나 실패하면 내장 렌더러로
     */
    private static boolean renderWebPageToPdfFile(Path target, String htmlContent, String staticResourceBasePath,
            String cssPath, String fontPath, Class<?> clazz, String... convertCssBackgroundImageTargetSelectors)
            throws IOException {
        var command = browserEnabled ? resolveBrowserCommand() : null;
        if (command != null && commandExists(command.get(0))) {
            var htmlWithImages = embedImages(htmlContent, staticResourceBasePath, convertCssBackgroundImageTargetSelectors);
            var cssContent = S2Util.isNotEmpty(cssPath)
                    ? loadCssContent(clazz, cssPath, staticResourceBasePath, convertCssBackgroundImageTargetSelectors)
                    : "";
            var doc = Jsoup.parse(composeHtml(htmlContent, htmlWithImages, cssContent));
            doc.head().prependElement("style").appendChild(new DataNode(BROWSER_PAGE_CSS));
            if (doc.selectFirst("meta[charset]") == null) {
                doc.head().prependElement("meta").attr("charset", "UTF-8");
            }
            try {
                printWithBrowser(command, doc.outerHtml(), target);
                return true;
            } catch (IOException e) {
                // A broken or outdated browser must not stop the merge | 브라우저 문제로 병합이 멈추지 않도록
                logger.warn("브라우저 변환에 실패해 내장 렌더러로 변환합니다: {}", e.getMessage());
            }
            renderHtmlToPdfFile(target, htmlContent, staticResourceBasePath, cssPath, fontPath, clazz,
                    convertCssBackgroundImageTargetSelectors);
            // A fallback result is not cached, so the browser's is used once it works again
            // | 대체 결과는 캐시하지 않음 (브라우저가 복구되면 브라우저 결과를 씀)
            return false;
        }
        renderHtmlToPdfFile(target, htmlContent, staticResourceBasePath, cssPath, fontPath, clazz,
                convertCssBackgroundImageTargetSelectors);
        return true;
    }

    private static void printWithBrowser(List<String> command, String html, Path target) throws IOException {
        var work = Files.createTempDirectory("s2_browser_");
        try {
            var input = work.resolve("page.html");
            Files.writeString(input, html, StandardCharsets.UTF_8);
            var output = work.resolve("page.pdf");
            var args = new ArrayList<>(command);
            args.addAll(List.of("--print-to-pdf", output.toString(), input.toString()));
            var log = work.resolve("browser.log");
            var process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            var timeout = browserTimeout;
            boolean finished;
            try {
                finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                destroyTree(process);
                Thread.currentThread().interrupt();
                throw new IOException("브라우저 변환이 중단되었습니다.", e);
            }
            if (!finished) {
                destroyTree(process);
                throw new IOException("브라우저 변환 제한 시간(" + timeout.toSeconds() + "초)을 넘었습니다");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
                throw new IOException("종료 코드 " + process.exitValue() + ", 명령 " + args.get(0) + ", 출력: " + readTail(log, 2000));
            }
            try (var in = Files.newInputStream(output)) {
                var head = in.readNBytes(1024);
                if (indexOf(head, PDF_HEADER, 1024) < 0) {
                    throw new IOException("PDF 가 아닌 결과, 명령 " + args.get(0));
                }
            }
            Files.move(output, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            S2FileUtil.deleteQuietly(work);
        }
    }

    // ------------------------------------------------------------------------
    // SVG (optional openhtmltopdf-svg-support) | SVG (선택 의존성 openhtmltopdf-svg-support)

    private static final String SVG_DRAWER = "com.openhtmltopdf.svgsupport.BatikSVGDrawer";
    private static final String SVG_SUPPORT_REQUIRED = "SVG 를 그리려면 io.github.openhtmltopdf:openhtmltopdf-svg-support 의존성이 필요합니다.";
    private static final AtomicBoolean SVG_WARNED = new AtomicBoolean();

    /**
     * Whether SVG ({@code ofSvg}, inline {@code <svg>}, SVG images in HTML) can be drawn: openhtmltopdf-svg-support
     * (Batik) is on the classpath. Without it the renderer leaves SVG blank, so {@code ofSvg} fails instead.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * SVG({@code ofSvg}, HTML 의 {@code <svg>}·SVG 이미지)를 그릴 수 있는지. openhtmltopdf-svg-support(Batik)가 클래스패스에 있어야 한다.
     * 없으면 렌더러가 SVG 를 비워 두므로 {@code ofSvg} 는 예외를 낸다.
     *
     * @return SVG 를 그릴 수 있으면 true
     */
    public static boolean isSvgSupported() {
        return newSvgDrawer() != null;
    }

    /**
     * A Batik drawer with scripts off and only data: resources (an SVG can point at http: or file: addresses), or null
     * when the module is absent | 스크립트를 끄고 data: 리소스만 허용한 Batik 그리기 도구 (SVG 도 http:, file: 주소를 가리킬 수 있음). 없으면 null
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static com.openhtmltopdf.extend.SVGDrawer newSvgDrawer() {
        try {
            var loader = PdfRendererBuilder.class.getClassLoader();
            var drawer = Class.forName(SVG_DRAWER, true, loader);
            var scriptMode = (Class<Enum>) Class.forName(SVG_DRAWER + "$SvgScriptMode", true, loader);
            // Only data: (images already embedded); http:, file: and the rest stay refused
            // | data: 만 허용 (이미 넣은 이미지). http:, file: 등은 계속 거부
            return (com.openhtmltopdf.extend.SVGDrawer) drawer.getConstructor(scriptMode, java.util.Set.class)
                    .newInstance(Enum.valueOf(scriptMode, "SECURE"), java.util.Set.of("data"));
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("openhtmltopdf-svg-support 를 초기화할 수 없습니다: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------------
    // Office and Hangul documents (LibreOffice) | 오피스·한글 문서 (LibreOffice)

    /** Extensions accepted by {@link PdfSource#ofDocument(Path)}; hwp/hwpx need the H2Orestart extension | 문서 확장자 */
    public static final java.util.Set<String> DOCUMENT_EXTENSIONS = java.util.Set.of(
            "doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "hwp", "hwpx");

    private static final java.util.Set<String> HWP_EXTENSIONS = java.util.Set.of("hwp", "hwpx");

    private static final String CONVERTER_REQUIRED = "오피스·한글 문서를 변환하려면 s2-office-converter 설치가 필요합니다.";

    /** Set by setOfficeCommand; never re-detected | setOfficeCommand 로 지정한 명령 (다시 찾지 않음) */
    private static volatile List<String> configuredOfficeCommand;
    /** Last detected command; "not found" is not remembered, so an install is picked up without a restart | 마지막으로 찾은 명령. "없음"은 기억하지 않아 설치 후 재시작 없이 반영 */
    private static volatile List<String> detectedOfficeCommand;
    private static volatile Duration officeTimeout = Duration.ofMinutes(3);

    /**
     * 문서 변환에 쓸 LibreOffice 호환 명령을 정한다. 지정하지 않으면 다음 순서로 찾는다.
     * <ol>
     * <li>환경 변수 {@code S2_SOFFICE}</li>
     * <li>PATH 의 {@code s2-soffice} (_devtools2 {@code scripts/linux/setup-projects/s2/s2-office-converter/setup-s2-office-converter.sh}가 설치하는 Podman 변환기)</li>
     * <li>PATH 의 {@code soffice}, {@code libreoffice}</li>
     * <li>고정 경로 {@code /usr/local/bin/s2-soffice}(PATH 에 없을 때), 운영체제별 기본 설치 경로 (Windows {@code C:\Program Files\LibreOffice\program\soffice.exe}, macOS
     * {@code /Applications/LibreOffice.app/Contents/MacOS/soffice}, Linux {@code /opt/libreoffice}*{@code /program/soffice})</li>
     * </ol>
     * 명령은 {@code --headless --convert-to pdf --outdir <폴더> <파일>} 형식을 받아야 한다.
     *
     * @param command 명령과 앞부분 인자 (예: {@code "s2-soffice"}, {@code "/opt/libreoffice/program/soffice"})
     */
    public static void setOfficeCommand(String... command) {
        if (command == null || command.length == 0 || command[0] == null || command[0].isBlank()) {
            throw new IllegalArgumentException("명령이 비었습니다.");
        }
        configuredOfficeCommand = List.of(command);
    }

    /** 지정한 명령을 지우고 자동 탐색으로 되돌린다. */
    public static void resetOfficeCommand() {
        configuredOfficeCommand = null;
        detectedOfficeCommand = null;
    }

    /**
     * 문서 한 건의 변환 제한 시간 (기본 3분). 넘으면 변환 프로세스를 강제로 끝내고 예외를 던진다.
     *
     * @param timeout 제한 시간
     */
    public static void setOfficeTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("제한 시간은 0 보다 커야 합니다: " + timeout);
        }
        officeTimeout = timeout;
    }

    /**
     * 오피스·한글 문서를 변환할 수 있는지 확인한다 (변환 명령이 있는지만 보며 실행하지는 않음). 화면에서 문서 첨부 기능을 보여 줄지 정할 때 쓴다.
     *
     * @return 변환 명령이 있으면 true
     */
    public static boolean isOfficeConversionAvailable() {
        var command = resolveOfficeCommand();
        return command != null && commandExists(command.get(0));
    }

    /** A path must be executable; a bare name must be on the PATH | 경로는 실행 가능해야 하고, 이름만 있으면 PATH 에 있어야 함 */
    private static boolean commandExists(String command) {
        if (command.contains("/") || command.contains("\\")) {
            return Files.isExecutable(Path.of(command));
        }
        return findOnPath(command) != null;
    }

    /**
     * 사용할 변환 명령. 없으면 비어 있다.
     *
     * @return 명령과 앞부분 인자
     */
    public static java.util.Optional<List<String>> officeCommand() {
        return java.util.Optional.ofNullable(resolveOfficeCommand());
    }

    /**
     * The configured command, or the detected one. Detection is repeated while nothing is found and when the
     * detected command disappears, so installing or removing the converter needs no restart (it only checks a few
     * paths).
     */
    private static List<String> resolveOfficeCommand() {
        var configured = configuredOfficeCommand;
        if (configured != null) {
            return configured;
        }
        var detected = detectedOfficeCommand;
        if (detected != null && commandExists(detected.get(0))) {
            return detected;
        }
        detected = detectOfficeCommand();
        if (detected != null && !detected.equals(detectedOfficeCommand)) {
            logger.info("문서 변환 명령: {}", detected);
        }
        detectedOfficeCommand = detected;
        return detected;
    }

    private static List<String> detectOfficeCommand() {
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
            // Where s2-office-converter/setup-s2-office-converter.sh (_devtools2) installs it, for PATHs without /usr/local/bin (cron, trimmed services)
            // | 설치 스크립트가 두는 위치. /usr/local/bin 이 PATH 에 없는 환경(cron, PATH 를 좁힌 서비스) 대비
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

    private static Path findOnPath(String name) {
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

    /**
     * Converts one document with LibreOffice in a private temporary folder (its own user profile, so conversions can
     * run concurrently) and moves the PDF to {@code target}.
     */
    private static void convertDocumentToPdf(PdfSource source, Path target) throws IOException {
        var command = resolveOfficeCommand();
        if (command == null || !commandExists(command.get(0))) {
            throw new IOException(CONVERTER_REQUIRED);
        }
        var extension = S2FileUtil.getExtension(source.documentName, true);
        var work = Files.createTempDirectory("s2_office_");
        try {
            // A plain ASCII name avoids encoding issues in the command line | 명령줄 인코딩 문제를 피하려고 ASCII 이름 사용
            var input = work.resolve("document." + extension);
            if (source.pathData != null) {
                Files.copy(source.pathData, input);
            } else if (source.byteData != null) {
                Files.write(input, source.byteData);
            } else {
                try (var out = Files.newOutputStream(input)) {
                    source.inputStream.transferTo(out);
                }
            }
            var outDir = Files.createDirectories(work.resolve("out"));

            var args = new ArrayList<>(command);
            var wrapper = Path.of(command.get(0)).getFileName().toString().startsWith("s2-soffice");
            if (!wrapper) {
                // A separate LibreOffice profile per call lets conversions run at the same time | 호출마다 별도 프로필 → 동시 변환 가능
                args.add("-env:UserInstallation=" + work.resolve("profile").toUri());
            }
            args.addAll(List.of("--headless", "--norestore", "--nolockcheck", "--convert-to", "pdf", "--outdir",
                    outDir.toString(), input.toString()));

            var log = work.resolve("soffice.log");
            Process process;
            try {
                process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            } catch (IOException e) {
                throw new IOException(CONVERTER_REQUIRED, e);
            }
            var timeout = officeTimeout;
            boolean finished;
            try {
                finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                destroyTree(process);
                Thread.currentThread().interrupt();
                throw new IOException("문서 변환이 중단되었습니다.", e);
            }
            if (!finished) {
                destroyTree(process);
                throw new IOException("문서 변환 제한 시간(" + timeout.toSeconds() + "초)을 넘었습니다: " + source.documentName);
            }
            var output = outDir.resolve("document.pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
                // Short message for users; the exit code and LibreOffice output go to the cause and the log
                // | 사용자에게는 짧은 메시지, 종료 코드와 LibreOffice 출력은 원인(cause)과 로그로
                var detail = new IOException("soffice 종료 코드 " + process.exitValue() + ", 명령 " + args.get(0) + ", 출력: "
                        + readTail(log, 2000));
                logger.warn("문서 변환 실패: {} ({})", source.documentName, detail.getMessage());
                // Plain LibreOffice without the H2Orestart extension cannot read Hangul files | H2Orestart 없는 LibreOffice 는 한글 파일을 못 읽음
                var message = HWP_EXTENSIONS.contains(extension) && !wrapper ? CONVERTER_REQUIRED
                        : "문서를 PDF로 변환하지 못했습니다: " + source.documentName;
                throw new IOException(message, detail);
            }
            Files.move(output, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            S2FileUtil.deleteQuietly(work);
        }
    }

    private static void destroyTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static String readTail(Path log, int maxChars) {
        try {
            var text = Files.readString(log, StandardCharsets.UTF_8).strip();
            return text.length() > maxChars ? "..." + text.substring(text.length() - maxChars) : text;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------------
    // Fonts | 폰트

    private record FontSource(String description, byte[] bytes) {
        /**
         * Fonts that map control characters to the space glyph (NanumGothic) would make spaces copy and search as U+0000
         * | 제어 문자를 공백 글리프에 연결한 폰트(나눔고딕)는 PDF 에서 복사·검색한 공백이 U+0000 이 되므로 정리
         */
        FontSource {
            bytes = S2FontCmap.withoutControlCharacters(bytes);
        }
    }

    /**
     * The built-in PDF fonts have no Korean glyphs, so without a font every such character would silently become '#'
     * | 기본 PDF 폰트에는 한글 글리프가 없어, 폰트 없이는 모든 글자가 조용히 '#'으로 바뀜
     */
    static void requireFontFor(String content, boolean hasFont) throws IOException {
        if (!hasFont && content != null && HANGUL_OR_CJK.matcher(content).find()) {
            throw new IOException("한글(CJK)을 표시할 폰트가 없습니다. S2PdfUtil.setDefaultFont(Path)로 한글 TTF 폰트를 지정하거나, "
                    + "서버에 한글 폰트(예: fonts-nanum 의 NanumGothic.ttf)를 설치하십시오. 자동 탐색 경로: " + SYSTEM_FONT_CANDIDATES);
        }
    }

    /** Hangul (Jamo, compatibility Jamo, syllables) and CJK ideographs/kana | 한글과 CJK 문자 */
    private static final Pattern HANGUL_OR_CJK = Pattern
            .compile("[\\u1100-\\u11FF\\u3040-\\u30FF\\u3130-\\u318F\\u4E00-\\u9FFF\\uAC00-\\uD7A3]");

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

    private static volatile FontSource defaultFont;

    /** Looked up once, on first use without a configured font | 폰트 미지정으로 처음 쓸 때 한 번만 탐색 */
    private static final class SystemFont {
        static final FontSource FONT = detect();

        private static FontSource detect() {
            for (var candidate : SYSTEM_FONT_CANDIDATES) {
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
     * 폰트를 지정하지 않은 모든 변환(HTML, 텍스트, SVG, URL, 병합)에 쓸 기본 폰트를 정한다. 한글을 쓰려면 한글 글리프가 있는 TrueType(.ttf) 폰트를
     * 지정한다. 지정하지 않으면 서버에 설치된 한글 폰트({@link #SYSTEM_FONT_CANDIDATES})를 자동으로 찾는다.
     *
     * @param ttfFile TrueType 폰트 파일
     * @throws IOException 파일을 읽을 수 없을 때
     */
    public static void setDefaultFont(Path ttfFile) throws IOException {
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
    public static void setDefaultFont(Class<?> clazz, String resourcePath) throws IOException {
        defaultFont = loadClasspathFont(Objects.requireNonNull(clazz, "clazz"), resourcePath);
    }

    /** 기본 폰트 설정을 지우고 시스템 폰트 자동 탐색으로 되돌린다. */
    public static void resetDefaultFont() {
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

    /** Source font, then the default font, then an installed Korean font | 소스 폰트 → 기본 폰트 → 설치된 한글 폰트 */
    private static FontSource resolveFont(Class<?> clazz, String fontPath) throws IOException {
        if (S2Util.isNotEmpty(fontPath)) {
            return loadClasspathFont(clazz != null ? clazz : S2PdfUtil.class, fontPath);
        }
        var configured = defaultFont;
        return configured != null ? configured : SystemFont.FONT;
    }

    private static String getMimeType(String fileName) {
        if (fileName.endsWith(".png"))
            return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg"))
            return "image/jpeg";
        if (fileName.endsWith(".gif"))
            return "image/gif";
        if (fileName.endsWith(".svg"))
            return "image/svg+xml";
        if (fileName.endsWith(".webp"))
            return "image/webp";
        if (fileName.endsWith(".bmp"))
            return "image/bmp";
        return null;
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
    public static boolean pdfToImage(InputStream pdfData, int pageNo, Path imagePath, Integer dpi, String imageFormat,
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
    public static boolean pdfToImage(byte[] pdfData, int pageNo, Path imagePath, Integer dpi, String imageFormat) {
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
    public static InputStream pdfToImage(Path pdfFile, int pageNo, Integer dpi, String imageFormat,
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
    public static InputStream pdfToImage(InputStream pdfData, int pageNo, Integer dpi, String imageFormat,
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
    public static InputStream pdfToImage(byte[] pdfData, int pageNo, Integer dpi, String imageFormat,
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
    public static InputStream addPageNumbers(Path pdfFile, int numberOfPagesToInsert, Integer fontSize, File fontFile)
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
    public static InputStream addPageNumbers(InputStream pdfData, int numberOfPagesToInsert, Integer fontSize,
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
    public static InputStream addPageNumbers(InputStream pdfData, int numberOfPagesToInsert, Integer fontSize,
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
    public static InputStream addPageNumbers(byte[] pdfData, int numberOfPagesToInsert, Integer fontSize, File fontFile)
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
        var totalPages = document.getNumberOfPages();
        if (numberOfPagesToInsert < 1 || numberOfPagesToInsert > totalPages) {
            throw new IllegalArgumentException(
                    "번호를 넣을 쪽 수는 1 이상, 전체 쪽 수(" + totalPages + ") 이하여야 합니다: " + numberOfPagesToInsert);
        }
        stampPageNumbers(document, totalPages - numberOfPagesToInsert, numberOfPagesToInsert,
                fontSize != null ? fontSize : 10, font != null ? font : new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                "%d / %d");
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
     * PDF 에 페이지 번호를 추가한다.
     *
     * @param document              PDF 문서
     * @param numberOfPagesToInsert 추가할 페이지 수 (PDF 전체 페이지 수보다 작은 경우 적은 페이지 만큼 비우고 페이지 번호를 추가한다.)
     * @param fontSize              페이지 폰트 사이즈
     * @param fontStream            페이지 폰트 InputStream (null 이면 기본 폰트 사용)
     * @return 페이지 번호를 추가한 InputStream
     * @throws IOException IOException
     */


    // ========================================================================
    // 📑 PDF / HTML / Image / Text / SVG 통합 병합 (Merge) API
    // ========================================================================

    /**
     * PDF 병합에 사용할 문서 소스 정의 클래스.
     * <p>PDF, HTML, 이미지(PNG/JPG/GIF/BMP/TIFF, WebP 는 {@code com.twelvemonkeys.imageio:imageio-webp} 추가 시), 일반 텍스트, SVG 문서를 통합 관리합니다.</p>
     * <p>한글은 폰트가 있어야 표시된다. {@link S2PdfUtil#setDefaultFont(Path)}로 지정하거나 서버에 한글 폰트를 설치한다
     * ({@link S2PdfUtil#SYSTEM_FONT_CANDIDATES}). 폰트 없이 한글을 넣으면 '#'으로 깨지는 대신 예외가 난다.</p>
     */
    /** Default limit for a download or an in-memory source (100MB) | 다운로드·메모리 소스 기본 한도 (100MB) */
    public static final long DEFAULT_MAX_SOURCE_BYTES = 100L * 1024 * 1024;

    /**
     * 병합 결과에 적용할 옵션. {@code MergeOptions.create().bookmarks(true).pageNumbers(true).title("보고서")}
     */
    public static final class MergeOptions {
        private boolean bookmarks;
        private boolean pageNumbers;
        private int pageNumberSkipFirst;
        private int pageNumberSkipLast;
        private float pageNumberFontSize = 10f;
        private String pageNumberFormat = "%d / %d";
        private String title;
        private String author;
        private Watermark watermark;
        private boolean cache;
        private int imageDpi;
        private int pdfImageDpi;

        private MergeOptions() {
        }

        /**
         * @return 기본 옵션 (책갈피·쪽 번호 없음)
         */
        public static MergeOptions create() {
            return new MergeOptions();
        }

        /**
         * 소스마다 책갈피를 만든다. 원래 PDF 에 있던 책갈피는 그 소스의 책갈피 아래로 들어간다. 제목은 {@link PdfSource#title(String)}, 없으면
         * 파일명·URL·"문서 N".
         *
         * @param bookmarks 책갈피 생성 여부
         * @return 이 옵션
         */
        public MergeOptions bookmarks(boolean bookmarks) {
            this.bookmarks = bookmarks;
            return this;
        }

        /**
         * 모든 쪽 아래 가운데에 쪽 번호를 넣는다 (기본 형식 {@code "%d / %d"}: 현재 쪽 / 전체 쪽).
         *
         * @param pageNumbers 쪽 번호 여부
         * @return 이 옵션
         */
        public MergeOptions pageNumbers(boolean pageNumbers) {
            this.pageNumbers = pageNumbers;
            return this;
        }

        /**
         * 앞쪽 {@code skipFirst} 쪽(표지, 목차 등)과 뒤쪽 {@code skipLast} 쪽(부록, 뒤표지 등)을 빼고 나머지 쪽에만 번호를 넣는다. 번호는 나머지 쪽 기준이다
         * (전체 10쪽에서 앞 1쪽, 뒤 2쪽을 빼면 2~8번째 쪽에 {@code "1 / 7"} ~ {@code "7 / 7"}). 쪽 번호를 켠다.
         *
         * @param skipFirst 번호를 넣지 않을 앞쪽 수 (0 이상)
         * @param skipLast  번호를 넣지 않을 뒤쪽 수 (0 이상)
         * @return 이 옵션
         * @throws IllegalArgumentException 음수일 때. 병합 결과에 번호를 넣을 쪽이 남지 않으면 병합이 예외로 끝난다
         */
        public MergeOptions pageNumbers(int skipFirst, int skipLast) {
            if (skipFirst < 0 || skipLast < 0) {
                throw new IllegalArgumentException("제외할 쪽 수는 0 이상이어야 합니다: 앞 " + skipFirst + ", 뒤 " + skipLast);
            }
            this.pageNumberSkipFirst = skipFirst;
            this.pageNumberSkipLast = skipLast;
            this.pageNumbers = true;
            return this;
        }

        /**
         * 쪽 번호 형식과 글자 크기를 정하고 쪽 번호를 켠다. 형식은 {@link String#format}이며 인자는 (현재 쪽, 전체 쪽). 기본 PDF 폰트를 쓰므로 숫자·영문·기호만
         * 쓴다.
         *
         * @param format   형식 (예: {@code "- %d -"}, {@code "%d / %d"})
         * @param fontSize 글자 크기 (pt)
         * @return 이 옵션
         * @throws java.util.IllegalFormatException 형식이 잘못되었을 때
         */
        public MergeOptions pageNumberStyle(String format, float fontSize) {
            String.format(Objects.requireNonNull(format, "format"), 1, 1); // Fail now on a bad format | 잘못된 형식은 지금 실패
            this.pageNumberFormat = format;
            this.pageNumberFontSize = fontSize;
            this.pageNumbers = true;
            return this;
        }

        /**
         * @param title 문서 정보의 제목
         * @return 이 옵션
         */
        public MergeOptions title(String title) {
            this.title = title;
            return this;
        }

        /**
         * @param author 문서 정보의 작성자
         * @return 이 옵션
         */
        public MergeOptions author(String author) {
            this.author = author;
            return this;
        }

        /**
         * 모든 쪽에 워터마크 이미지를 넣는다. 쪽 번호는 워터마크 위에 그려진다.
         *
         * @param watermark 워터마크 ({@link Watermark#of(Path)} 등). null 이면 넣지 않음
         * @return 이 옵션
         */
        public MergeOptions watermark(Watermark watermark) {
            this.watermark = watermark;
            return this;
        }

        /**
         * 이 요청에서 변환 결과 캐시를 쓴다 (기본 false). 변환이 필요한 소스(문서, HTML, 웹 페이지, 이미지, 텍스트, SVG)의 변환 결과를 저장해 두었다가 같은
         * 소스가 다시 오면 변환 없이 쓴다. PDF 소스와 JPEG 이미지는 변환이 거의 없어 캐시하지 않는다 (병합 결과 PDF 자체도
         * 저장하지 않음). 저장 위치·한도는
         * {@link S2PdfUtil#setConversionCache(Path, long, Duration, long)}.
         * <p>
         * 변환 결과가 디스크에 남으므로 개인정보가 든 문서라면 보관 기간을 짧게 두는 것이 안전하다.
         * </p>
         *
         * @param cache 캐시 사용 여부
         * @return 이 옵션
         */
        public MergeOptions cache(boolean cache) {
            this.cache = cache;
            return this;
        }

        /**
         * 이미지 소스와 PDF 소스 안의 이미지를 이 해상도(쪽 크기 기준 dpi)에 맞게 줄인다 (기본 0 = 원본 유지). 사진이 많은 문서의 결과 크기와 다운로드 시간이
         * 크게 준다. 목표보다 충분히 클 때(1.2 배 이상)만 줄이고, JPEG 는 JPEG(품질 0.9)로, 그 외는 무손실로 다시 넣는다.
         * <p>
         * 기록물·증빙처럼 확대해서 세부를 봐야 하는 이미지가 있으면 쓰지 않거나 높게 둔다. 화면용은 150, 화면·인쇄 겸용은 200, 인쇄용은 300 정도가 알맞다.
         * 1비트(흑백 스캔), JBIG2·CCITT·JPEG2000, 투명 마스크가 있는 PDF 이미지는 건드리지 않는다. 캐시를 켜면 줄인 결과를 저장한다.
         * </p>
         *
         * @param dpi 72 이상, 0 이면 줄이지 않음 (이미지 소스와 PDF 소스 모두에 적용)
         * @return 이 옵션
         */
        public MergeOptions imageDpi(int dpi) {
            return imageDpi(dpi, dpi);
        }

        /** The same conversion settings with the cache on | 같은 변환 설정에 캐시를 켠 사본 */
        private MergeOptions forPreparing() {
            var copy = new MergeOptions();
            copy.cache = true;
            copy.imageDpi = imageDpi;
            copy.pdfImageDpi = pdfImageDpi;
            return copy;
        }

        /**
         * 이미지 소스와 PDF 소스 안의 이미지를 따로 정한다 ({@link #imageDpi(int)} 참고). 예: 사진만 줄이고 스캔 PDF 는 원본 유지 {@code imageDpi(150, 0)}.
         *
         * @param imageSourceDpi 이미지 소스({@code ofImage}, 이미지 URL)의 해상도. 0 이면 원본 유지
         * @param pdfImageDpi    PDF 소스({@code ofPdf}, PDF URL) 안 이미지의 해상도. 0 이면 원본 유지
         * @return 이 옵션
         */
        public MergeOptions imageDpi(int imageSourceDpi, int pdfImageDpi) {
            for (var dpi : new int[] { imageSourceDpi, pdfImageDpi }) {
                if (dpi != 0 && dpi < 72) {
                    throw new IllegalArgumentException("이미지 해상도는 72 dpi 이상이어야 합니다 (0 은 원본 유지): " + dpi);
                }
            }
            this.imageDpi = imageSourceDpi;
            this.pdfImageDpi = pdfImageDpi;
            return this;
        }
    }

    /**
     * 변환 결과 캐시의 위치와 한도를 정한다 ({@link MergeOptions#cache(boolean)}로 켠 요청에만 쓰임). 앱 시작 때 한 번 호출하며, 호출하지 않으면 기본값을
     * 쓴다. 정리는 요청이 올 때 마지막 정리가 오늘 이전이면, 또는 크기 상한을 넘으면 백그라운드에서 한 번만 한다.
     *
     * @param directory    저장 폴더 (null 이면 {@code java.io.tmpdir/s2-pdf-cache}). 앱 실행 계정만 읽을 수 있게 만든다
     * @param maxBytes     캐시 폴더의 최대 크기. 넘으면 오래 안 쓴 것부터 지운다. 0 이하이면 기본값(최소 여유 공간의 50%)
     * @param maxAge       보관 기간 (기본 1일 = 24시간). 마지막으로 쓴 뒤 이 시간이 지나면 쓰지 않고, 다음 정리 때 지운다
     * @param minFreeBytes 디스크 전체에 남길 최소 여유 공간. 저장 후 이보다 적어지면 저장하지 않는다. 0 이하이면 기본값(디스크 용량의 10% 와 20GB 중
     *                     작은 값. 200GB 이상 디스크에서 20GB 이고 캐시 최대 크기는 그 50% 인 10GB)
     */
    public static void setConversionCache(Path directory, long maxBytes, Duration maxAge, long minFreeBytes) {
        S2PdfCache.configure(directory, maxBytes, maxAge, minFreeBytes);
    }

    /**
     * 변환 결과 캐시의 저장 폴더만 바꾼다 (나머지 설정은 그대로).
     *
     * @param directory 저장 폴더. null 이면 기본값({@code java.io.tmpdir/s2-pdf-cache})
     */
    public static void setConversionCacheDirectory(Path directory) {
        S2PdfCache.directory(directory);
    }

    /**
     * 변환 결과 캐시 폴더의 최대 크기만 바꾼다 (나머지 설정은 그대로). 넘으면 오래 안 쓴 것부터 지운다.
     *
     * @param maxBytes 최대 크기. 0 이하이면 기본값(최소 여유 공간의 50%)
     */
    public static void setConversionCacheMaxBytes(long maxBytes) {
        S2PdfCache.maxBytes(maxBytes);
    }

    /**
     * 변환 결과의 보관 기간만 바꾼다 (나머지 설정은 그대로). 마지막으로 쓴 뒤 이 기간이 지나면 쓰지 않고, 다음 정리 때 지운다.
     *
     * @param maxAge 보관 기간 (기본 1일 = 24시간)
     * @throws IllegalArgumentException null 이거나 0 이하일 때
     */
    public static void setConversionCacheMaxAge(Duration maxAge) {
        S2PdfCache.maxAge(maxAge);
    }

    /**
     * 디스크에 남길 최소 여유 공간만 바꾼다 (나머지 설정은 그대로). 저장 후 이보다 적어지면 저장하지 않는다. 최대 크기를 정하지 않았으면 최대 크기는 이
     * 값의 50% 가 된다.
     *
     * @param minFreeBytes 최소 여유 공간. 0 이하이면 기본값(디스크 용량의 10% 와 20GB 중 작은 값)
     */
    public static void setConversionCacheMinFreeBytes(long minFreeBytes) {
        S2PdfCache.minFreeBytes(minFreeBytes);
    }

    /** 변환 결과 캐시 설정을 기본값으로 되돌린다 (저장된 항목은 지우지 않음). */
    public static void resetConversionCache() {
        S2PdfCache.configure(null, S2PdfCache.DEFAULT_MAX_BYTES, S2PdfCache.DEFAULT_MAX_AGE, S2PdfCache.DEFAULT_MIN_FREE_BYTES);
    }

    /**
     * 변환 결과 캐시를 모두 지운다 (변환기나 폰트를 바꾼 뒤 바로 반영하고 싶을 때 등).
     *
     * @throws IOException 지울 수 없을 때
     */
    public static void clearConversionCache() throws IOException {
        S2PdfCache.clear();
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
     * S2PdfUtil.merge(sources, MergeOptions.create().watermark(mark));
     * }</pre>
     */
    public static final class Watermark {

        /**
         * 워터마크 기준 위치. 가장자리 기준이면 {@link #offset}만큼 안쪽으로 들어오고, 가운데 기준인 축은 가운데에서 x 는 오른쪽, y 는 아래쪽으로 옮긴다.
         */
        public enum Position {
            TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
        }

        private final byte[] image;
        private Float width;
        private Float height;
        private Position position = Position.CENTER;
        private float offsetX;
        private float offsetY;
        private float opacity = 1f;

        private Watermark(byte[] image) {
            if (image == null || image.length == 0) {
                throw new IllegalArgumentException("워터마크 이미지가 비었습니다.");
            }
            this.image = image;
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
    }

    public static class PdfSource implements AutoCloseable {
        public enum SourceType {
            PDF, HTML, IMAGE, TEXT, SVG, URL, DOCUMENT
        }

        private final SourceType type;
        private InputStream inputStream;
        private byte[] byteData;
        private File fileData;
        private Path pathData;
        private BufferedImage imageObj;
        private String textOrHtmlContent;

        // URL 소스 옵션
        private String urlString;
        private SourceType urlExpectedType;
        private Map<String, String> httpHeaders;
        private Duration timeout = Duration.ofSeconds(30);

        // HTML / SVG 렌더링 옵션
        private String staticResourceBasePath;
        private String cssPath;
        private String fontPath;
        private Class<?> resourceClass;
        private String[] cssSelectors;

        private boolean autoCloseStream = true;

        // DOCUMENT: original file name (the extension selects the LibreOffice import filter) | 원래 파일명 (확장자로 변환 필터 결정)
        private String documentName;

        // Merge options | 병합 옵션
        private String title;
        private long maxBytes = DEFAULT_MAX_SOURCE_BYTES;

        private PdfSource(SourceType type) {
            this.type = type;
        }

        /** PDF InputStream 소스 생성 */
        public static PdfSource ofPdf(InputStream pdfStream) {
            var src = new PdfSource(SourceType.PDF);
            src.inputStream = Objects.requireNonNull(pdfStream, "[PdfSource] pdfStream must not be null");
            return src;
        }

        /** PDF File 소스 생성 */
        public static PdfSource ofPdf(File pdfFile) {
            var src = new PdfSource(SourceType.PDF);
            src.fileData = Objects.requireNonNull(pdfFile, "[PdfSource] pdfFile must not be null");
            return src;
        }

        /** PDF Path 소스 생성 */
        public static PdfSource ofPdf(Path pdfPath) {
            var src = new PdfSource(SourceType.PDF);
            src.pathData = Objects.requireNonNull(pdfPath, "[PdfSource] pdfPath must not be null");
            return src;
        }

        /** PDF byte[] 소스 생성 */
        public static PdfSource ofPdf(byte[] pdfBytes) {
            var src = new PdfSource(SourceType.PDF);
            src.byteData = Objects.requireNonNull(pdfBytes, "[PdfSource] pdfBytes must not be null");
            return src;
        }

        /** 단순 HTML 소스 생성 */
        public static PdfSource ofHtml(String htmlContent) {
            var src = new PdfSource(SourceType.HTML);
            src.textOrHtmlContent = Objects.requireNonNull(htmlContent, "[PdfSource] htmlContent must not be null");
            return src;
        }

        /** 정적 리소스(CSS, Font, 이미지 등)를 포함하는 HTML 소스 생성 */
        public static PdfSource ofHtml(String htmlContent, String staticResourceBasePath, String cssPath,
                String fontPath, Class<?> resourceClass, String... cssSelectors) {
            var src = new PdfSource(SourceType.HTML);
            src.textOrHtmlContent = Objects.requireNonNull(htmlContent, "[PdfSource] htmlContent must not be null");
            src.staticResourceBasePath = staticResourceBasePath;
            src.cssPath = cssPath;
            src.fontPath = fontPath;
            src.resourceClass = resourceClass;
            src.cssSelectors = cssSelectors;
            return src;
        }

        /** HTML InputStream 소스 생성 */
        public static PdfSource ofHtml(InputStream htmlStream, String staticResourceBasePath, String cssPath,
                String fontPath, Class<?> resourceClass, String... cssSelectors) throws IOException {
            try {
                var html = new String(S2StreamUtil.streamToByteArray(htmlStream, false, DEFAULT_MAX_SOURCE_BYTES), StandardCharsets.UTF_8);
                return ofHtml(html, staticResourceBasePath, cssPath, fontPath, resourceClass, cssSelectors);
            } finally {
                S2StreamUtil.closeStream(htmlStream);
            }
        }

        /** HTML File 소스 생성 */
        public static PdfSource ofHtml(File htmlFile) throws IOException {
            return ofHtml(
                    Files.readString(Objects.requireNonNull(htmlFile, "[PdfSource] htmlFile must not be null").toPath(),
                            StandardCharsets.UTF_8));
        }

        /** HTML Path 소스 생성 */
        public static PdfSource ofHtml(Path htmlPath) throws IOException {
            return ofHtml(Files.readString(Objects.requireNonNull(htmlPath, "[PdfSource] htmlPath must not be null"),
                    StandardCharsets.UTF_8));
        }

        /** 이미지 InputStream 소스 생성 (PNG, JPG, JPEG, GIF, BMP, TIFF, WebP 는 imageio-webp 추가 시) */
        public static PdfSource ofImage(InputStream imageStream) {
            var src = new PdfSource(SourceType.IMAGE);
            src.inputStream = Objects.requireNonNull(imageStream, "[PdfSource] imageStream must not be null");
            return src;
        }

        /** 이미지 File 소스 생성 */
        public static PdfSource ofImage(File imageFile) {
            var src = new PdfSource(SourceType.IMAGE);
            src.fileData = Objects.requireNonNull(imageFile, "[PdfSource] imageFile must not be null");
            return src;
        }

        /** 이미지 Path 소스 생성 */
        public static PdfSource ofImage(Path imagePath) {
            var src = new PdfSource(SourceType.IMAGE);
            src.pathData = Objects.requireNonNull(imagePath, "[PdfSource] imagePath must not be null");
            return src;
        }

        /** 이미지 byte[] 소스 생성 */
        public static PdfSource ofImage(byte[] imageBytes) {
            var src = new PdfSource(SourceType.IMAGE);
            src.byteData = Objects.requireNonNull(imageBytes, "[PdfSource] imageBytes must not be null");
            return src;
        }

        /** 이미지 BufferedImage 소스 생성 */
        public static PdfSource ofImage(BufferedImage image) {
            var src = new PdfSource(SourceType.IMAGE);
            src.imageObj = Objects.requireNonNull(image, "[PdfSource] image must not be null");
            return src;
        }

        /** 일반 텍스트 소스 생성 (A4 코드/텍스트 뷰어로 자동 서식화) */
        public static PdfSource ofText(String plainText) {
            var src = new PdfSource(SourceType.TEXT);
            src.textOrHtmlContent = Objects.requireNonNull(plainText, "[PdfSource] plainText must not be null");
            return src;
        }

        /** 일반 텍스트 InputStream 소스 생성 */
        public static PdfSource ofText(InputStream textStream) throws IOException {
            try {
                var text = new String(S2StreamUtil.streamToByteArray(textStream, false, DEFAULT_MAX_SOURCE_BYTES), StandardCharsets.UTF_8);
                return ofText(text);
            } finally {
                S2StreamUtil.closeStream(textStream);
            }
        }

        /** 일반 텍스트 File 소스 생성 */
        public static PdfSource ofText(File textFile) throws IOException {
            return ofText(
                    Files.readString(Objects.requireNonNull(textFile, "[PdfSource] textFile must not be null").toPath(),
                            StandardCharsets.UTF_8));
        }

        /** 일반 텍스트 Path 소스 생성 */
        public static PdfSource ofText(Path textPath) throws IOException {
            return ofText(Files.readString(Objects.requireNonNull(textPath, "[PdfSource] textPath must not be null"),
                    StandardCharsets.UTF_8));
        }

        /** SVG 벡터 그래픽 소스 생성 (단일 페이지 벡터 PDF로 자동 변환) */
        public static PdfSource ofSvg(String svgContent) {
            var src = new PdfSource(SourceType.SVG);
            src.textOrHtmlContent = Objects.requireNonNull(svgContent, "[PdfSource] svgContent must not be null");
            return src;
        }

        /** SVG 벡터 그래픽 InputStream 소스 생성 */
        public static PdfSource ofSvg(InputStream svgStream) throws IOException {
            try {
                var svg = new String(S2StreamUtil.streamToByteArray(svgStream, false, DEFAULT_MAX_SOURCE_BYTES), StandardCharsets.UTF_8);
                return ofSvg(svg);
            } finally {
                S2StreamUtil.closeStream(svgStream);
            }
        }

        /** SVG File 소스 생성 */
        public static PdfSource ofSvg(File svgFile) throws IOException {
            return ofSvg(
                    Files.readString(Objects.requireNonNull(svgFile, "[PdfSource] svgFile must not be null").toPath(),
                            StandardCharsets.UTF_8));
        }

        /** SVG Path 소스 생성 */
        public static PdfSource ofSvg(Path svgPath) throws IOException {
            return ofSvg(Files.readString(Objects.requireNonNull(svgPath, "[PdfSource] svgPath must not be null"),
                    StandardCharsets.UTF_8));
        }

        /**
         * 오피스·한글 문서 소스를 만든다. 병합할 때 LibreOffice({@link S2PdfUtil#setOfficeCommand(String...)} 참고)로 PDF 로 변환한다.
         * LibreOffice 가 없으면 이 소스를 병합할 때 원인을 알려 주는 예외가 나며, 다른 소스만 병합하는 데는 영향이 없다.
         *
         * @param path 문서 파일 ({@link S2PdfUtil#DOCUMENT_EXTENSIONS}, hwp·hwpx 는 H2Orestart 확장 필요)
         * @return 문서 소스
         * @throws IllegalArgumentException 지원하지 않는 확장자일 때
         */
        public static PdfSource ofDocument(Path path) {
            var src = new PdfSource(SourceType.DOCUMENT);
            src.pathData = Objects.requireNonNull(path, "[PdfSource] path must not be null");
            src.documentName = checkDocumentName(String.valueOf(path.getFileName()));
            return src;
        }

        /**
         * @param file 문서 파일
         * @return 문서 소스
         * @see #ofDocument(Path)
         */
        public static PdfSource ofDocument(File file) {
            return ofDocument(Objects.requireNonNull(file, "[PdfSource] file must not be null").toPath());
        }

        /**
         * 업로드 등으로 받은 문서 스트림으로 소스를 만든다. 형식은 파일명의 확장자로 정한다.
         *
         * @param stream   문서 스트림 ({@link #autoClose(boolean)} 설정에 따라 병합 후 닫힘)
         * @param fileName 원래 파일명 (예: {@code 보고서.hwp})
         * @return 문서 소스
         * @see #ofDocument(Path)
         */
        public static PdfSource ofDocument(InputStream stream, String fileName) {
            var src = new PdfSource(SourceType.DOCUMENT);
            src.inputStream = Objects.requireNonNull(stream, "[PdfSource] stream must not be null");
            src.documentName = checkDocumentName(fileName);
            return src;
        }

        /**
         * @param bytes    문서 내용
         * @param fileName 원래 파일명 (예: {@code 보고서.docx})
         * @return 문서 소스
         * @see #ofDocument(Path)
         */
        public static PdfSource ofDocument(byte[] bytes, String fileName) {
            var src = new PdfSource(SourceType.DOCUMENT);
            src.byteData = Objects.requireNonNull(bytes, "[PdfSource] bytes must not be null");
            src.documentName = checkDocumentName(fileName);
            return src;
        }

        private static String checkDocumentName(String fileName) {
            var extension = S2FileUtil.getExtension(fileName == null ? "" : fileName, true);
            if (!DOCUMENT_EXTENSIONS.contains(extension)) {
                throw new IllegalArgumentException(
                        "[PdfSource] 지원하지 않는 문서 형식입니다: " + fileName + " (지원: " + DOCUMENT_EXTENSIONS + ")");
            }
            return fileName;
        }

        /**
         * URL 소스 생성 (원격 리소스 다운로드 및 Content-Type/Magic-Byte 기반 자동 감지)
         * <p>
         * 서버가 이 URL 로 직접 요청하므로, 사용자 입력을 그대로 넘기면 내부망 주소를 조회하는 SSRF 가 된다. 사용자 입력이 섞이면 호출자가
         * 허용 호스트를 검사해야 한다.
         * </p>
         * <p>
         * HTML 페이지이면 화면 그대로 나오도록 페이지의 이미지({@code <img>}, 지연 로딩 {@code data-src}, CSS 배경)와 스타일시트({@code <link>},
         * {@code @import})를 받아 넣는다.
         * </p>
         * <ul>
         * <li>페이지와 같은 출처는 그대로 받고, 요청 헤더(로그인 쿠키 등)도 같은 출처에만 보낸다.</li>
         * <li>다른 호스트(CDN 등)는 공개 주소일 때만 받는다. 내부망·루프백 주소는 리다이렉트를 거쳐도 막는다.</li>
         * <li>리소스는 최대 {@value S2HtmlResources#MAX_RESOURCES}개, 하나당 20MB, 전체 {@link #maxBytes(long)} 까지 받는다.</li>
         * <li>받지 못한 리소스는 경고 로그를 남기고 빼며, PDF 는 그대로 만든다.</li>
         * <li>브라우저({@code s2-chrome}, {@link S2PdfUtil#setBrowserCommand})가 있으면 브라우저로 인쇄해 flex·grid·JavaScript 까지 화면 그대로 나온다
         * (네트워크 없는 컨테이너에서 실행). 없거나 실패하면 경고 로그를 남기고 내장 렌더러(openhtmltopdf)로 변환하며, 이때는 JavaScript 를 실행하지 않고
         * CSS 는 CSS 2.1 수준으로 적용되며 웹 폰트 대신 기본 폰트를 쓴다.</li>
         * </ul>
         *
         * @param url http 또는 https URL
         * @return URL 소스
         * @throws IllegalArgumentException http/https 가 아닌 URL
         */
        public static PdfSource ofUrl(String url) {
            Objects.requireNonNull(url, "[PdfSource] url must not be null");
            var scheme = URI.create(url.trim()).getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("[PdfSource] http/https URL 만 허용됩니다: " + url);
            }
            var src = new PdfSource(SourceType.URL);
            src.urlString = url.trim();
            return src;
        }

        /** URI 기반 소스 생성 (자동 감지) */
        public static PdfSource ofUrl(URI uri) {
            return ofUrl(Objects.requireNonNull(uri, "[PdfSource] uri must not be null").toString());
        }

        /** URL 소스 생성 (헤더 및 타임아웃 지정) */
        public static PdfSource ofUrl(String url, Map<String, String> httpHeaders, Duration timeout) {
            var src = ofUrl(url);
            src.httpHeaders = httpHeaders;
            if (timeout != null) {
                src.timeout = timeout;
            }
            return src;
        }

        /** HTML URL 소스 생성 (표지 JSP/HTML 등 원격 렌더링용) */
        public static PdfSource ofHtmlUrl(String url) {
            var src = ofUrl(url);
            src.urlExpectedType = SourceType.HTML;
            return src;
        }

        /** HTML URL 소스 생성 (CSS/Font/정적자원 설정 포함) */
        public static PdfSource ofHtmlUrl(String url, String staticResourceBasePath, String cssPath, String fontPath,
                Class<?> resourceClass, String... cssSelectors) {
            var src = ofHtmlUrl(url);
            src.staticResourceBasePath = staticResourceBasePath;
            src.cssPath = cssPath;
            src.fontPath = fontPath;
            src.resourceClass = resourceClass;
            src.cssSelectors = cssSelectors;
            return src;
        }

        /** PDF URL 소스 생성 (원격 PDF 파일 다운로드 후 병합) */
        public static PdfSource ofPdfUrl(String url) {
            var src = ofUrl(url);
            src.urlExpectedType = SourceType.PDF;
            return src;
        }

        /** 이미지 URL 소스 생성 (원격 이미지 파일 다운로드 후 PDF 페이지 변환) */
        public static PdfSource ofImageUrl(String url) {
            var src = ofUrl(url);
            src.urlExpectedType = SourceType.IMAGE;
            return src;
        }

        /** HTTP 헤더 추가/설정 */
        public PdfSource headers(Map<String, String> headers) {
            this.httpHeaders = headers;
            return this;
        }

        /** HTTP 요청 타임아웃 설정 */
        public PdfSource timeout(Duration timeout) {
            if (timeout != null) {
                this.timeout = timeout;
            }
            return this;
        }

        /**
         * 병합 결과의 책갈피 제목 ({@link MergeOptions#bookmarks(boolean)}). 지정하지 않으면 파일명, URL, 또는 "문서 N"을 쓴다.
         *
         * @param title 책갈피 제목
         * @return 이 소스
         */
        public PdfSource title(String title) {
            this.title = title;
            return this;
        }

        /**
         * 이 소스에서 읽을 최대 바이트 수 (URL 다운로드, 스트림 이미지). 기본 {@value S2PdfUtil#DEFAULT_MAX_SOURCE_BYTES}바이트.
         *
         * @param maxBytes 최대 바이트 수
         * @return 이 소스
         */
        public PdfSource maxBytes(long maxBytes) {
            if (maxBytes < 1) {
                throw new IllegalArgumentException("maxBytes 는 1 이상이어야 합니다: " + maxBytes);
            }
            this.maxBytes = maxBytes;
            return this;
        }

        /** Readable description for error messages | 오류 메시지용 설명 */
        private String describe() {
            if (documentName != null) {
                return type + " " + documentName;
            }
            if (urlString != null) {
                return type + " " + urlString;
            }
            if (pathData != null) {
                return type + " " + pathData.getFileName();
            }
            if (fileData != null) {
                return type + " " + fileData.getName();
            }
            return type.toString();
        }

        private String bookmarkTitle(int index) {
            if (title != null && !title.isBlank()) {
                return title;
            }
            if (documentName != null) {
                return documentName;
            }
            if (pathData != null) {
                return String.valueOf(pathData.getFileName());
            }
            if (fileData != null) {
                return fileData.getName();
            }
            if (urlString != null) {
                return urlString;
            }
            return "문서 " + (index + 1);
        }

        /** 스트림 자동 닫기 여부 설정 (기본값: true) */
        public PdfSource autoClose(boolean autoClose) {
            this.autoCloseStream = autoClose;
            return this;
        }

        @Override
        public void close() {
            if (autoCloseStream && inputStream != null) {
                S2StreamUtil.closeStream(inputStream);
            }
        }
    }

    /**
     * 다중 소스(PDF, HTML, 이미지, 텍스트, SVG)를 단일 PDF 문서로 병합한다.
     * <p>대용량 문서 병합 시에도 메모리 누수(OOM)가 발생하지 않도록 PDFBox 임시 파일 스트림 캐시를 활용하며,
     * 반환되는 InputStream이 닫힐 때 모든 임시 리소스가 디스크에서 자동으로 삭제됩니다.</p>
     *
     * @param sources 병합할 문서 소스 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream - close 시 임시 파일 자동 정리)
     * @throws IOException 입출력 또는 변환 오류 시
     */
    /**
     * 임시 파일을 생성하고 추적 목록에 등록한다. deleteOnExit 는 JVM 이 끝날 때까지 경로를 메모리에 쌓으므로 쓰지 않는다
     * (정리는 작업 종료 시 및 {@link S2ResourceInputStream}이 담당).
     */
    private static Path createTrackedTempFile(String prefix, String suffix, List<Path> trackingList)
            throws IOException {
        var tempFile = Files.createTempFile(S2Uuid.generateUuidV7() + "_" + prefix + "_", suffix);
        if (trackingList != null) {
            trackingList.add(tempFile);
        }
        return tempFile;
    }

    /**
     * 다중 소스(PDF, HTML, 이미지, 텍스트, SVG, URL)를 사용자가 지정한 순서 그대로 단일 PDF 문서로 병합한다.
     * <p>
     * <b>순서 보장:</b> 여러 소스 타입이 혼합되어 있어도 입력된 {@code sources} 리스트의 인덱스 순서대로 정확하게 결합됩니다.<br>
     * <b>분산 병렬 I/O 최적화:</b> 원격 URL 리소스들이 다수 포함되어 있을 경우, {@link S2ThreadUtil#getCommonExecutor()}를
     * 활용하여 백그라운드에서 비동기 병렬로 미리 다운로드(Pre-fetch)하면서도, 최종 병합 시에는 원래의 순서를 100% 유지합니다.<br>
     * <b>메모리 누수 방지:</b> 모든 중간 임시 파일은 작업 완료(성공/실패 무관) 즉시 삭제되며, 최종 스트림은 {@link S2ResourceInputStream}으로
     * 반환되어 호출자가 닫을 때 자동으로 임시 파일이 삭제됩니다.
     * </p>
     *
     * @param sources 병합할 문서 소스 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream - close 시 임시 파일 자동 정리)
     * @throws IOException 입출력 또는 변환 오류 시
     */
    public static InputStream merge(List<PdfSource> sources) throws IOException {
        return merge(sources, MergeOptions.create());
    }

    /**
     * 다중 소스를 사용자가 지정한 순서대로 단일 PDF 로 병합하고 옵션(책갈피, 쪽 번호, 문서 정보)을 적용한다.
     * <p>
     * 소스 하나라도 실패하면 "병합 소스 #N (종류 이름) 처리 실패"로 어느 소스인지 알려 주는 예외를 던지며, 그때까지 만든 임시 파일은 모두 지운다.
     * </p>
     *
     * @param sources 병합할 문서 소스 목록
     * @param options 병합 옵션 ({@link MergeOptions#create()})
     * @return 병합된 PDF 스트림 (S2ResourceInputStream - close 시 임시 파일 자동 정리)
     * @throws IOException 입출력 또는 변환 오류 시
     */
    private static volatile int conversionParallelism = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    /** Shared by every merge, so the limit holds for the whole server | 모든 병합이 함께 써서 서버 전체 한도가 됨 */
    private static volatile java.util.concurrent.Semaphore conversionSlots = new java.util.concurrent.Semaphore(conversionParallelism, true);

    /**
     * 서버 전체에서 동시에 실행할 변환 수 (기본: CPU 수와 4 중 작은 값). 요청이 몇 건이 오든 이 수만큼만 동시에 변환하고 나머지는 차례를 기다린다. 문서·웹
     * 페이지 변환은 건마다 컨테이너(LibreOffice·Chromium, 수백 MB 메모리)를 띄우므로 서버 메모리에 맞춰 정한다. 1 이면 한 번에 하나씩 변환한다.
     *
     * @param parallelism 1 이상
     */
    public static void setConversionParallelism(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("동시 변환 수는 1 이상이어야 합니다: " + parallelism);
        }
        conversionParallelism = parallelism;
        conversionSlots = new java.util.concurrent.Semaphore(parallelism, true);
    }

    /** Writes a converted PDF; returns false when the result must not be cached (a fallback) | 변환 결과를 쓴다. 대체 결과면 false */
    @FunctionalInterface
    private interface PdfWriter {
        boolean write(Path target) throws IOException;
    }

    /**
     * Converts into a tracked temporary file, through the conversion cache when {@code cacheKey} is set | 변환해 임시 파일로.
     * cacheKey 가 있으면 변환 결과 캐시를 거친다
     */
    private static File converted(String prefix, List<Path> temps, S2PdfCache.Key cacheKey, PdfWriter writer)
            throws IOException {
        var tempFile = createTrackedTempFile(prefix, ".pdf", temps);
        var key = cacheKey != null ? cacheKey.hex() : null;
        if (key != null && S2PdfCache.restore(key, tempFile)) {
            return tempFile.toFile();
        }
        var cacheable = writer.write(tempFile);
        if (key != null && cacheable) {
            S2PdfCache.store(key, tempFile);
        }
        return tempFile.toFile();
    }

    /** Everything that shapes an HTML rendering | HTML 렌더링 결과를 정하는 모든 것 */
    private static S2PdfCache.Key htmlKey(String kind, String html, String staticResourceBasePath, String cssPath,
            String fontPath, Class<?> clazz, String[] selectors) throws IOException {
        var font = resolveFont(clazz, fontPath);
        var key = S2PdfCache.key(kind).add(html).add(staticResourceBasePath).add(cssPath).add(fontPath)
                .add(clazz != null ? clazz.getName() : null)
                .add(selectors != null ? String.join("\u0000", selectors) : null)
                .add(font != null ? font.description() + ":" + font.bytes().length : "no-font")
                .add(String.valueOf(isSvgSupported()));
        // Classpath images, CSS and fonts can change with a redeploy: valid for this run only
        // | 클래스패스 이미지·CSS·폰트는 재배포로 바뀔 수 있으므로 이번 실행 동안만 유효
        if (S2Util.isNotEmpty(staticResourceBasePath) || S2Util.isNotEmpty(cssPath) || S2Util.isNotEmpty(fontPath)) {
            key.add(S2PdfCache.RUN);
        }
        return key;
    }

    /**
     * Turns one source into a PDF file (converting when needed); runs concurrently for the sources of a merge
     * | 소스 하나를 PDF 파일로 만든다 (필요하면 변환). 병합의 소스들에 대해 동시에 실행된다
     */
    private static File prepareSource(PdfSource source, MergeOptions options, List<Path> intermediateTempFiles,
            CompletableFuture<UrlFetchResult> future) throws IOException {
        File pdfFileToMerge = null;
        switch (source.type) {
            case PDF -> {
                if (source.fileData != null) {
                    pdfFileToMerge = source.fileData;
                } else if (source.pathData != null) {
                    pdfFileToMerge = source.pathData.toFile();
                } else if (source.byteData != null) {
                    var tempFile = createTrackedTempFile("src", ".pdf", intermediateTempFiles);
                    Files.write(tempFile, source.byteData);
                    pdfFileToMerge = tempFile.toFile();
                } else if (source.inputStream != null) {
                    var tempFile = createTrackedTempFile("src", ".pdf", intermediateTempFiles);
                    Files.copy(source.inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    pdfFileToMerge = tempFile.toFile();
                }
                if (pdfFileToMerge != null && options.pdfImageDpi > 0) {
                    pdfFileToMerge = withSmallerImages(pdfFileToMerge.toPath(), options, intermediateTempFiles);
                }
            }
            case HTML -> {
                var clazz = source.resourceClass != null ? source.resourceClass : S2PdfUtil.class;
                var key = options.cache ? htmlKey("HTML", source.textOrHtmlContent, source.staticResourceBasePath,
                        source.cssPath, source.fontPath, clazz, source.cssSelectors) : null;
                pdfFileToMerge = converted("html", intermediateTempFiles, key, target -> {
                    renderHtmlToPdfFile(target, source.textOrHtmlContent, source.staticResourceBasePath,
                            source.cssPath, source.fontPath, clazz, source.cssSelectors);
                    return true;
                });
            }
            case IMAGE -> {
                if (source.imageObj != null) {
                    var tempFile = createTrackedTempFile("img", ".pdf", intermediateTempFiles);
                    renderImageToPdfFile(source.imageObj, null, tempFile, options.imageDpi);
                    pdfFileToMerge = tempFile.toFile();
                } else {
                    byte[] imageBytes;
                    if (source.fileData != null) {
                        imageBytes = Files.readAllBytes(source.fileData.toPath());
                    } else if (source.pathData != null) {
                        imageBytes = Files.readAllBytes(source.pathData);
                    } else if (source.byteData != null) {
                        imageBytes = source.byteData;
                    } else {
                        imageBytes = S2StreamUtil.streamToByteArray(source.inputStream, false, source.maxBytes);
                    }
                    // JPEG kept as is is embedded in milliseconds, so caching it would only take space; a shrunk one is worth keeping
                    // | 그대로 넣는 JPEG 는 몇 ms 면 끝나 캐시하면 공간만 차지함. 줄이는 경우는 저장할 가치가 있음
                    var key = options.cache && (!isJpeg(imageBytes) || options.imageDpi > 0)
                            ? S2PdfCache.key("IMAGE").add(String.valueOf(options.imageDpi)).add(imageBytes) : null;
                    pdfFileToMerge = converted("img", intermediateTempFiles, key, target -> {
                        renderImageToPdfFile(null, imageBytes, target, options.imageDpi);
                        return true;
                    });
                }
            }
            case TEXT -> {
                var html = convertTextToHtml(source.textOrHtmlContent);
                var key = options.cache ? htmlKey("TEXT", html, null, null, null, S2PdfUtil.class, null) : null;
                pdfFileToMerge = converted("txt", intermediateTempFiles, key, target -> {
                    renderHtmlToPdfFile(target, html, null, null, null, S2PdfUtil.class);
                    return true;
                });
            }
            case DOCUMENT -> {
                S2PdfCache.Key key = null;
                if (options.cache) {
                    // A stream is kept in a file first so it can be hashed and then converted
                    // | 스트림은 해시한 뒤 변환할 수 있도록 먼저 파일로
                    if (source.pathData == null && source.byteData == null) {
                        var copy = createTrackedTempFile("doc_src", "." + S2FileUtil.getExtension(source.documentName, true),
                                intermediateTempFiles);
                        Files.copy(source.inputStream, copy, StandardCopyOption.REPLACE_EXISTING);
                        source.pathData = copy;
                    }
                    key = S2PdfCache.key("DOCUMENT").add(S2FileUtil.getExtension(source.documentName, true).toLowerCase(Locale.ROOT))
                            .add(S2PdfCache.commandIdentity(resolveOfficeCommand()));
                    if (source.pathData != null) {
                        key.addFile(source.pathData);
                    } else {
                        key.add(source.byteData);
                    }
                }
                pdfFileToMerge = converted("doc", intermediateTempFiles, key, target -> {
                    convertDocumentToPdf(source, target);
                    return true;
                });
            }
            case SVG -> {
                var html = convertSvgToHtml(source.textOrHtmlContent);
                var key = options.cache ? htmlKey("SVG", html, null, null, null, S2PdfUtil.class, null) : null;
                pdfFileToMerge = converted("svg", intermediateTempFiles, key, target -> {
                    renderHtmlToPdfFile(target, html, null, null, null, S2PdfUtil.class);
                    return true;
                });
            }
            case URL -> {
                UrlFetchResult fetched;
                try {
                    fetched = (future != null) ? future.join() : fetchUrlContent(source);
                } catch (CompletionException ce) {
                    if (ce.getCause() instanceof IOException ioe) {
                        throw ioe;
                    }
                    throw new IOException("URL 병합 처리 오류: " + ce.getMessage(), ce);
                }

                var effectiveType = source.urlExpectedType != null
                        ? source.urlExpectedType
                        : detectTypeFromUrlAndContent(fetched.contentType, fetched.data, source.urlString);

                var responseCharset = parseCharsetFromContentType(fetched.contentType, StandardCharsets.UTF_8);

                switch (effectiveType) {
                    case PDF -> {
                        var tempFile = createTrackedTempFile("url_pdf", ".pdf", intermediateTempFiles);
                        Files.write(tempFile, fetched.data);
                        pdfFileToMerge = options.pdfImageDpi > 0 ? withSmallerImages(tempFile, options, intermediateTempFiles)
                                : tempFile.toFile();
                    }
                    case HTML -> {
                        // Images and stylesheets of the page are fetched and embedded (same origin, or
                        // public hosts) | 페이지의 이미지·스타일시트를 받아 넣음 (같은 출처 또는 공개 호스트)
                        var htmlContent = S2HtmlResources.inline(new String(fetched.data, responseCharset),
                                fetched.uri, source.httpHeaders, source.timeout, source.maxBytes,
                                ref -> classpathImageExists(ref, source.staticResourceBasePath));
                        var clazz = source.resourceClass != null ? source.resourceClass : S2PdfUtil.class;
                        // The page as fetched, with its images and CSS, decides the key: a changed page is
                        // converted again | 받아 온 페이지(이미지·CSS 포함)가 키. 페이지가 바뀌면 다시 변환
                        var key = options.cache ? htmlKey("WEB", htmlContent, source.staticResourceBasePath,
                                source.cssPath, source.fontPath, clazz, source.cssSelectors)
                                .add(browserEnabled ? S2PdfCache.commandIdentity(resolveBrowserCommand()) : "no-browser")
                                : null;
                        pdfFileToMerge = converted("url_html", intermediateTempFiles, key,
                                target -> renderWebPageToPdfFile(target, htmlContent, source.staticResourceBasePath,
                                        source.cssPath, source.fontPath, clazz, source.cssSelectors));
                    }
                    case IMAGE -> {
                        var key = options.cache && (!isJpeg(fetched.data) || options.imageDpi > 0)
                                ? S2PdfCache.key("IMAGE").add(String.valueOf(options.imageDpi)).add(fetched.data) : null;
                        pdfFileToMerge = converted("url_img", intermediateTempFiles, key, target -> {
                            renderImageToPdfFile(null, fetched.data, target, options.imageDpi);
                            return true;
                        });
                    }
                    case SVG -> {
                        var html = convertSvgToHtml(new String(fetched.data, responseCharset));
                        var key = options.cache ? htmlKey("SVG", html, null, null, null, S2PdfUtil.class, null) : null;
                        pdfFileToMerge = converted("url_svg", intermediateTempFiles, key, target -> {
                            renderHtmlToPdfFile(target, html, null, null, null, S2PdfUtil.class);
                            return true;
                        });
                    }
                    case TEXT -> {
                        var html = convertTextToHtml(new String(fetched.data, responseCharset));
                        var key = options.cache ? htmlKey("TEXT", html, null, null, null, S2PdfUtil.class, null) : null;
                        pdfFileToMerge = converted("url_txt", intermediateTempFiles, key, target -> {
                            renderHtmlToPdfFile(target, html, null, null, null, S2PdfUtil.class);
                            return true;
                        });
                    }
                    default -> throw new IOException("URL 콘텐츠의 타입을 처리할 수 없습니다: " + source.urlString);
                }
            }
        }
        return pdfFileToMerge;
    }

    /**
     * Converts the sources in the background and keeps the results in the conversion cache, so a later
     * {@code merge} with {@code cache(true)} and the same image settings is fast even the first time it is viewed.
     * Call it right after an upload; it does not merge, number or stamp anything.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 소스를 백그라운드에서 변환해 변환 결과 캐시에 넣어 둔다. 나중에 {@code cache(true)}와 같은 이미지 설정으로 {@code merge}하면 처음 볼 때도 빠르다.
     * 업로드 직후에 호출하며, 병합·쪽 번호·워터마크는 하지 않는다.
     * <ul>
     * <li>{@code options}에서는 이미지 설정({@link MergeOptions#imageDpi(int, int)})만 쓰고 캐시는 항상 켠다. 미리보기용과 다운로드용 설정이 다르면 각각
     * 호출한다.</li>
     * <li>변환은 서버 전체 동시 변환 한도({@link #setConversionParallelism(int)})를 병합과 함께 쓴다.</li>
     * <li>실패해도 예외를 던지지 않고 경고 로그를 남기며, 돌려준 Future 가 그 예외로 끝난다 (업로드 처리에 영향 없음).</li>
     * <li>소스는 끝난 뒤 닫힌다 ({@code merge}와 같음). 변환이 없는 소스(PDF, JPEG)는 건너뛴다.</li>
     * </ul>
     *
     * <pre>{@code
     * // 업로드 처리 직후
     * S2PdfUtil.prepare(List.of(PdfSource.ofDocument(saved)), MergeOptions.create());
     * // 나중에 미리보기 → 캐시에서 바로
     * S2PdfUtil.merge(sources, MergeOptions.create().cache(true));
     * }</pre>
     *
     * @param sources 미리 변환할 소스
     * @param options 이미지 설정 (null 이면 원본 유지)
     * @return 모든 변환이 끝나면 완료되는 Future
     */
    public static CompletableFuture<Void> prepare(List<PdfSource> sources, MergeOptions options) {
        Objects.requireNonNull(sources, "sources");
        var settings = (options != null ? options : MergeOptions.create()).forPreparing();
        var temps = java.util.Collections.synchronizedList(new ArrayList<Path>());
        var executor = S2ThreadUtil.newExecutor(conversionParallelism);
        var slots = conversionSlots;
        var conversions = new ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i);
            if (source == null) {
                continue;
            }
            final var index = i;
            conversions.add(CompletableFuture.runAsync(() -> {
                slots.acquireUninterruptibly();
                try {
                    prepareSource(source, settings, temps, null);
                } catch (IOException | RuntimeException e) {
                    logger.warn("미리 변환하지 못했습니다: 소스 #{} ({}) {}", index + 1, source.describe(), e.getMessage());
                    throw new CompletionException(e);
                } finally {
                    slots.release();
                    source.close();
                }
            }, executor));
        }
        return CompletableFuture.allOf(conversions.toArray(CompletableFuture[]::new)).whenComplete((done, error) -> {
            executor.shutdown();
            for (var temp : temps) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Left for the system temp cleanup | 시스템 임시 폴더 정리에 맡김
                }
            }
        });
    }

    /**
     * 소스를 백그라운드에서 원본 해상도로 미리 변환해 캐시에 넣어 둔다 ({@link #prepare(List, MergeOptions)} 참고).
     *
     * @param sources 미리 변환할 소스
     * @return 모든 변환이 끝나면 완료되는 Future
     */
    public static CompletableFuture<Void> prepare(PdfSource... sources) {
        return prepare(Arrays.asList(Objects.requireNonNull(sources, "sources")), null);
    }

    public static InputStream merge(List<PdfSource> sources, MergeOptions options) throws IOException {
        Objects.requireNonNull(options, "options");
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("[merge] sources must not be null or empty.");
        }

        // Filled from concurrent conversions | 동시 변환에서 채워짐
        var intermediateTempFiles = java.util.Collections.synchronizedList(new ArrayList<Path>());
        var merger = new PDFMergerUtility();
        Path finalMergedTempFile = null;
        boolean success = false;

        // 원격 URL 소스 비동기 분산 병렬 다운로드 (원래 인덱스를 키로 보존하여 순서 보장)
        var urlFutures = new HashMap<Integer, CompletableFuture<UrlFetchResult>>();
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i);
            if (source != null && source.type == PdfSource.SourceType.URL) {
                final var s = source;
                urlFutures.put(i, CompletableFuture.supplyAsync(() -> {
                    try {
                        return fetchUrlContent(s);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, S2ThreadUtil.getCommonExecutor()));
            }
        }

        // Conversions run concurrently on a pool of their own (blocking converters must not fill the shared pool the URL
        // downloads use), and are added in the original order | 변환은 전용 풀에서 동시에 실행하고(외부 변환기가 URL 다운로드용 공용
        // 풀을 채우지 않도록) 원래 순서대로 붙인다
        var cancelled = new AtomicBoolean();
        var parallelism = conversionParallelism;
        var converters = S2ThreadUtil.newExecutor(parallelism);
        var slots = conversionSlots;
        var prepared = new ArrayList<CompletableFuture<File>>();
        try {
            for (int i = 0; i < sources.size(); i++) {
                var source = sources.get(i);
                if (source == null) {
                    prepared.add(CompletableFuture.completedFuture(null));
                    continue;
                }
                final var index = i;
                final var future = urlFutures.get(i);
                prepared.add(CompletableFuture.supplyAsync(() -> {
                    slots.acquireUninterruptibly();
                    try {
                        if (cancelled.get()) {
                            return null;
                        }
                        return prepareSource(source, options, intermediateTempFiles, future);
                    } catch (IOException | RuntimeException e) {
                        throw new CompletionException(new IOException(
                                "병합 소스 #" + (index + 1) + " (" + source.describe() + ") 처리 실패: " + e.getMessage(), e));
                    } finally {
                        slots.release();
                    }
                }, converters));
            }

            for (int i = 0; i < sources.size(); i++) {
                var source = sources.get(i);
                File pdfFileToMerge;
                try {
                    pdfFileToMerge = prepared.get(i).join();
                } catch (CompletionException e) {
                    cancelled.set(true);
                    if (e.getCause() instanceof IOException ioe) {
                        throw ioe;
                    }
                    throw new IOException("병합 소스 #" + (i + 1) + " 처리 실패: " + e.getMessage(), e);
                }
                if (pdfFileToMerge == null) {
                    continue;
                }
                if (options.bookmarks) {
                    try {
                        pdfFileToMerge = withBookmark(pdfFileToMerge, source.bookmarkTitle(i), intermediateTempFiles);
                    } catch (IOException | RuntimeException e) {
                        throw new IOException("병합 소스 #" + (i + 1) + " (" + source.describe() + ") 처리 실패: " + e.getMessage(), e);
                    }
                }
                merger.addSource(pdfFileToMerge);
            }

            // 최종 병합 대상 임시 파일 생성
            finalMergedTempFile = Files.createTempFile(S2Uuid.generateUuidV7() + "_merged_", ".pdf");
            S2FileUtil.makeDirectory(finalMergedTempFile.getParent());

            if (options.title != null || options.author != null) {
                var info = new PDDocumentInformation();
                info.setTitle(options.title);
                info.setAuthor(options.author);
                merger.setDestinationDocumentInformation(info);
            }
            try (var out = new BufferedOutputStream(Files.newOutputStream(finalMergedTempFile))) {
                merger.setDestinationStream(out);
                // 힙 메모리 OOM 방지: 임시 파일 기반 디스크 스트림 캐시 사용
                merger.mergeDocuments(IOUtils.createTempFileOnlyStreamCache());
            }
            if (options.pageNumbers || options.watermark != null) {
                var numbered = createTrackedTempFile("numbered", ".pdf", intermediateTempFiles);
                try (var doc = Loader.loadPDF(finalMergedTempFile.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
                    // Watermark first, so page numbers stay on top | 워터마크를 먼저 그려 쪽 번호가 위에 오게 함
                    if (options.watermark != null) {
                        stampWatermark(doc, options.watermark);
                    }
                    if (options.pageNumbers) {
                        var total = doc.getNumberOfPages();
                        var numberedPages = total - options.pageNumberSkipFirst - options.pageNumberSkipLast;
                        if (numberedPages < 1) {
                            throw new IllegalArgumentException("쪽 번호를 넣을 쪽이 없습니다: 전체 " + total + "쪽에서 앞 "
                                    + options.pageNumberSkipFirst + "쪽, 뒤 " + options.pageNumberSkipLast + "쪽 제외");
                        }
                        stampPageNumbers(doc, options.pageNumberSkipFirst, numberedPages, options.pageNumberFontSize,
                                new PDType1Font(Standard14Fonts.FontName.HELVETICA), options.pageNumberFormat);
                    }
                    doc.save(numbered.toFile());
                }
                Files.move(numbered, finalMergedTempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            var resultStream = new S2ResourceInputStream(
                    new BufferedInputStream(Files.newInputStream(finalMergedTempFile)), finalMergedTempFile);
            success = true;
            return resultStream;
        } finally {
            // Conversions still running finish (each has its own time limit) before their temporary files are removed;
            // ones not started yet are skipped | 진행 중인 변환은 끝난 뒤(각자 제한 시간 있음) 임시 파일을 지운다. 시작 전인 것은 건너뜀
            cancelled.set(true);
            for (var conversion : prepared) {
                try {
                    conversion.join();
                } catch (RuntimeException ignored) {
                    // Already reported, or not needed after a failure | 이미 보고했거나 실패 후라 필요 없음
                }
            }
            converters.shutdown();
            // 실패 또는 작업 종료 시 미완료된 백그라운드 비동기 다운로드 작업 즉시 취소
            for (var future : urlFutures.values()) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
            if (!success && finalMergedTempFile != null) {
                try {
                    Files.deleteIfExists(finalMergedTempFile);
                } catch (Exception ignore) {
                }
            }
            // 중간에 생성된 변환 임시 파일들은 병합 완료 즉시 모두 삭제하여 디스크 누수 방지
            for (var tempPath : intermediateTempFiles) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignore) {
                }
            }
            // 소스 스트림 자동 닫기
            for (var source : sources) {
                if (source != null) {
                    source.close();
                }
            }
        }
    }

    /**
     * 다중 소스(PDF, HTML, 이미지, 텍스트, SVG)를 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param sources 병합할 문서 소스 목록 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream merge(PdfSource... sources) throws IOException {
        if (sources == null || sources.length == 0) {
            throw new IllegalArgumentException("[merge] sources must not be null or empty.");
        }
        return merge(Arrays.asList(sources));
    }

    /**
     * 여러 개의 PDF InputStream 을 단일 PDF 로 병합한다.
     *
     * @param pdfStreams         병합할 PDF InputStream 목록
     * @param shouldCloseStreams 병합 후 입력 스트림들을 자동으로 닫을지 여부
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfs(List<InputStream> pdfStreams, boolean shouldCloseStreams) throws IOException {
        if (pdfStreams == null || pdfStreams.isEmpty()) {
            throw new IllegalArgumentException("[mergePdfs] pdfStreams must not be null or empty.");
        }
        var sources = new ArrayList<PdfSource>();
        for (var stream : pdfStreams) {
            if (stream != null) {
                sources.add(PdfSource.ofPdf(stream).autoClose(shouldCloseStreams));
            }
        }
        return merge(sources);
    }

    /**
     * 여러 개의 PDF InputStream 을 단일 PDF 로 병합한다. (입력 스트림 자동 close)
     *
     * @param pdfStreams 병합할 PDF InputStream 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfs(InputStream... pdfStreams) throws IOException {
        if (pdfStreams == null || pdfStreams.length == 0) {
            throw new IllegalArgumentException("[mergePdfs] pdfStreams must not be null or empty.");
        }
        return mergePdfs(Arrays.asList(pdfStreams), true);
    }

    /**
     * 여러 개의 PDF 파일들을 단일 PDF 로 병합한다.
     *
     * @param pdfFiles 병합할 PDF 파일 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfFiles(List<File> pdfFiles) throws IOException {
        if (pdfFiles == null || pdfFiles.isEmpty()) {
            throw new IllegalArgumentException("[mergePdfFiles] pdfFiles must not be null or empty.");
        }
        var sources = new ArrayList<PdfSource>();
        for (var file : pdfFiles) {
            if (file != null && file.exists()) {
                sources.add(PdfSource.ofPdf(file));
            }
        }
        return merge(sources);
    }

    /**
     * 여러 개의 PDF 파일들을 단일 PDF 로 병합한다. (가변인자)
     *
     * @param pdfFiles 병합할 PDF 파일 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfFiles(File... pdfFiles) throws IOException {
        if (pdfFiles == null || pdfFiles.length == 0) {
            throw new IllegalArgumentException("[mergePdfFiles] pdfFiles must not be null or empty.");
        }
        return mergePdfFiles(Arrays.asList(pdfFiles));
    }

    /**
     * 여러 개의 PDF Path 들을 단일 PDF 로 병합한다.
     *
     * @param pdfPaths 병합할 PDF Path 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfPaths(List<Path> pdfPaths) throws IOException {
        if (pdfPaths == null || pdfPaths.isEmpty()) {
            throw new IllegalArgumentException("[mergePdfPaths] pdfPaths must not be null or empty.");
        }
        var sources = new ArrayList<PdfSource>();
        for (var path : pdfPaths) {
            if (path != null && Files.exists(path)) {
                sources.add(PdfSource.ofPdf(path));
            }
        }
        return merge(sources);
    }

    /**
     * 여러 개의 PDF Path 들을 단일 PDF 로 병합한다. (가변인자)
     *
     * @param pdfPaths 병합할 PDF Path 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 입출력 오류 시
     */
    public static InputStream mergePdfPaths(Path... pdfPaths) throws IOException {
        if (pdfPaths == null || pdfPaths.length == 0) {
            throw new IllegalArgumentException("[mergePdfPaths] pdfPaths must not be null or empty.");
        }
        return mergePdfPaths(Arrays.asList(pdfPaths));
    }

    /**
     * 표지 HTML 과 본문 PDF 파일들을 한 번에 단일 PDF 로 병합한다. (편의 메서드)
     *
     * @param coverHtml          표지 HTML 문자열
     * @param notePdfStreams     본문 PDF InputStream 목록
     * @param fontPath           표지 렌더링에 사용할 폰트 경로 (null 가능)
     * @param clazz              리소스 참조 클래스 (null 가능)
     * @param shouldCloseStreams 본문 PDF 스트림들을 자동으로 닫을지 여부
     * @return 병합된 최종 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 또는 병합 오류 시
     */
    public static InputStream mergeHtmlAndPdfs(String coverHtml, List<InputStream> notePdfStreams, String fontPath,
            Class<?> clazz, boolean shouldCloseStreams) throws IOException {
        var sources = new ArrayList<PdfSource>();

        if (coverHtml != null && !coverHtml.isBlank()) {
            sources.add(PdfSource.ofHtml(coverHtml, null, null, fontPath, clazz));
        }

        if (notePdfStreams != null) {
            for (var stream : notePdfStreams) {
                if (stream != null) {
                    sources.add(PdfSource.ofPdf(stream).autoClose(shouldCloseStreams));
                }
            }
        }

        return merge(sources);
    }

    /**
     * 여러 이미지를 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다.
     *
     * @param imageStreams       이미지 InputStream 목록 (PNG, JPG, GIF 등)
     * @param shouldCloseStreams 입력 스트림 자동 닫기 여부
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagesToPdf(List<InputStream> imageStreams, boolean shouldCloseStreams)
            throws IOException {
        if (imageStreams == null || imageStreams.isEmpty()) {
            throw new IllegalArgumentException("[mergeImagesToPdf] imageStreams must not be null or empty.");
        }
        var sources = new ArrayList<PdfSource>();
        for (var stream : imageStreams) {
            if (stream != null) {
                sources.add(PdfSource.ofImage(stream).autoClose(shouldCloseStreams));
            }
        }
        return merge(sources);
    }

    /**
     * 여러 이미지를 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (입력 스트림 자동 close)
     *
     * @param imageStreams 이미지 InputStream 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagesToPdf(InputStream... imageStreams) throws IOException {
        if (imageStreams == null || imageStreams.length == 0) {
            throw new IllegalArgumentException("[mergeImagesToPdf] imageStreams must not be null or empty.");
        }
        return mergeImagesToPdf(Arrays.asList(imageStreams), true);
    }

    /**
     * 여러 이미지 파일(File)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다.
     *
     * @param imageFiles 이미지 파일 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImageFiles(List<File> imageFiles) throws IOException {
        if (imageFiles == null || imageFiles.isEmpty()) {
            throw new IllegalArgumentException("[mergeImageFiles] imageFiles must not be null or empty.");
        }
        var sources = new ArrayList<PdfSource>();
        for (var file : imageFiles) {
            if (file != null && file.exists()) {
                sources.add(PdfSource.ofImage(file));
            }
        }
        return merge(sources);
    }

    /**
     * 여러 이미지 파일(File)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param imageFiles 이미지 파일 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImageFiles(File... imageFiles) throws IOException {
        if (imageFiles == null || imageFiles.length == 0) {
            throw new IllegalArgumentException("[mergeImageFiles] imageFiles must not be null or empty.");
        }
        return mergeImageFiles(Arrays.asList(imageFiles));
    }

    /**
     * 여러 이미지 경로(Path)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다.
     *
     * @param imagePaths 이미지 경로 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagePaths(List<Path> imagePaths) throws IOException {
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new IllegalArgumentException("[mergeImagePaths] imagePaths must not be null or empty.");
        }
        var sources = new ArrayList<PdfSource>();
        for (var path : imagePaths) {
            if (path != null && Files.exists(path)) {
                sources.add(PdfSource.ofImage(path));
            }
        }
        return merge(sources);
    }

    /**
     * 여러 이미지 경로(Path)들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param imagePaths 이미지 경로 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagePaths(Path... imagePaths) throws IOException {
        if (imagePaths == null || imagePaths.length == 0) {
            throw new IllegalArgumentException("[mergeImagePaths] imagePaths must not be null or empty.");
        }
        return mergeImagePaths(Arrays.asList(imagePaths));
    }

    /**
     * 여러 BufferedImage 객체들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다.
     *
     * @param images BufferedImage 목록
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagesToPdf(List<BufferedImage> images) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("[mergeImagesToPdf] images must not be null or empty.");
        }
        var sources = new ArrayList<PdfSource>();
        for (var image : images) {
            if (image != null) {
                sources.add(PdfSource.ofImage(image));
            }
        }
        return merge(sources);
    }

    /**
     * 여러 BufferedImage 객체들을 A4 용지 규격에 맞춰 단일 PDF 문서로 병합한다. (가변인자)
     *
     * @param images BufferedImage 가변인자
     * @return 병합된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream mergeImagesToPdf(BufferedImage... images) throws IOException {
        if (images == null || images.length == 0) {
            throw new IllegalArgumentException("[mergeImagesToPdf] images must not be null or empty.");
        }
        return mergeImagesToPdf(Arrays.asList(images));
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
    public static InputStream convertImageToPdf(byte[] imageBytes) throws IOException {
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
    public static InputStream convertImageToPdf(BufferedImage image) throws IOException {
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
    public static InputStream convertImageToPdf(File imageFile) throws IOException {
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
    public static InputStream convertImageToPdf(Path imagePath) throws IOException {
        return convertImageToPdf(Files
                .readAllBytes(Objects.requireNonNull(imagePath, "[convertImageToPdf] imagePath must not be null")));
    }

    /**
     * SVG 벡터 그래픽 문자열을 단일 벡터 PDF 스트림으로 변환한다.
     *
     * @param svgContent SVG XML 문자열
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream convertSvgToPdf(String svgContent) throws IOException {
        var safeHtml = convertSvgToHtml(svgContent);
        return convertHtmlToPdfStream(safeHtml, null, null, null, S2PdfUtil.class);
    }

    /**
     * SVG 벡터 그래픽 스트림을 단일 벡터 PDF 스트림으로 변환한다.
     *
     * @param svgStream         SVG InputStream
     * @param shouldCloseStream 입력 스트림 자동 닫기 여부
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream convertSvgToPdf(InputStream svgStream, boolean shouldCloseStream) throws IOException {
        try {
            var svg = new String(S2StreamUtil.streamToByteArray(svgStream, false, DEFAULT_MAX_SOURCE_BYTES), StandardCharsets.UTF_8);
            return convertSvgToPdf(svg);
        } finally {
            if (shouldCloseStream) {
                S2StreamUtil.closeStream(svgStream);
            }
        }
    }

    /**
     * SVG 파일 경로(Path)를 단일 벡터 PDF 스트림으로 변환한다.
     *
     * @param svgPath SVG 파일 경로
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream convertSvgToPdf(Path svgPath) throws IOException {
        return convertSvgToPdf(Files.readString(
                Objects.requireNonNull(svgPath, "[convertSvgToPdf] svgPath must not be null"), StandardCharsets.UTF_8));
    }

    /**
     * 일반 텍스트를 A4 서식 PDF 스트림으로 변환한다.
     *
     * @param plainText 일반 텍스트 내용
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream convertTextToPdfStream(String plainText) throws IOException {
        var safeHtml = convertTextToHtml(plainText);
        return convertHtmlToPdfStream(safeHtml, null, null, null, S2PdfUtil.class);
    }

    /**
     * 일반 텍스트 스트림을 A4 서식 PDF 스트림으로 변환한다.
     *
     * @param textStream         텍스트 InputStream
     * @param shouldCloseStream 입력 스트림 자동 닫기 여부
     * @return 생성된 PDF 스트림 (S2ResourceInputStream)
     * @throws IOException 변환 오류 시
     */
    public static InputStream convertTextToPdfStream(InputStream textStream, boolean shouldCloseStream)
            throws IOException {
        try {
            var text = new String(S2StreamUtil.streamToByteArray(textStream, false, DEFAULT_MAX_SOURCE_BYTES), StandardCharsets.UTF_8);
            return convertTextToPdfStream(text);
        } finally {
            if (shouldCloseStream) {
                S2StreamUtil.closeStream(textStream);
            }
        }
    }

    /**
     * HTML 문자열을 PDF {@link InputStream} 으로 직접 변환한다.
     * <p>
     * 반환되는 스트림은 {@link S2ResourceInputStream} 인스턴스이며,
     * 스트림을 모두 사용한 후 {@code close()}를 호출하면 백킹(backing) 임시 파일이 디스크에서 100% 자동 삭제됩니다.<br>
     * 웹 애플리케이션에서 클라이언트에 PDF 파일을 바로 스트리밍 다운로드할 때 매우 유용합니다.
     * </p>
     *
     * @param htmlContent                              변환할 HTML 문자열
     * @param staticResourceBasePath                   정적 자원 웹 기본 경로 (예: {@code "/static"}, null 가능)
     * @param cssPath                                  CSS 파일 경로 (복수 개인 경우 쉼표(,) 구분, null 가능)
     * @param fontPath                                 한글/영문 TTF/OTF 폰트 파일 경로 (null 가능)
     * @param clazz                                    정적 자원 로드 기준 Class (보통 {@code getClass()})
     * @param convertCssBackgroundImageTargetSelectors CSS {@code background-image} 변환 대상 셀렉터 (생략 시 전체)
     * @return 변환된 PDF 스트림 ({@link S2ResourceInputStream} - close 시 임시 파일 자동 정리)
     * @throws IOException HTML 렌더링 또는 PDF 생성 오류 시
     * @apiNote
     * <pre>{@code
     * try (InputStream pdfStream = S2PdfUtil.convertHtmlToPdfStream(
     *         "<h1>영수증 / 인쇄물</h1>",
     *         "/static",
     *         "/static/css/print.css",
     *         "/static/font/NanumGothic.ttf",
     *         getClass()
     * )) {
     *     IOUtils.copy(pdfStream, response.getOutputStream());
     * }
     * }</pre>
     */
    public static InputStream convertHtmlToPdfStream(String htmlContent, String staticResourceBasePath, String cssPath,
            String fontPath, Class<?> clazz, String... convertCssBackgroundImageTargetSelectors) throws IOException {
        var pdfBytes = renderHtmlToPdfBytes(htmlContent, staticResourceBasePath, cssPath, fontPath, clazz,
                convertCssBackgroundImageTargetSelectors);
        var tempFile = Files.createTempFile(S2Uuid.generateUuidV7() + "_html_", ".pdf");
        boolean success = false;
        try {
            Files.write(tempFile, pdfBytes);
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
     * 다중 소스(PDF, HTML, 이미지, 텍스트, SVG, URL)를 단일 PDF 로 병합한 후, 전체 문서에 통일된 페이지 번호를 일괄 추가한다.
     * <p>
     * 이종 문서들을 병합하면 페이지마다 번호가 없거나 제각각일 수 있습니다. 이 메서드는 전체 병합 문서를 순회하며
     * 중앙 하단에 "1 / N" 형식의 일관된 페이지 번호를 일괄 각인합니다.
     * </p>
     *
     * @param sources               병합할 문서 소스 목록
     * @param numberOfPagesToInsert 페이지 번호를 매길 대상 페이지 수 (예: 총 10페이지 중 표지 1장을 제외하고 본문 9장에만 매기려면 9 전달)
     * @param fontSize              페이지 번호 폰트 크기 (null 시 기본 10pt)
     * @param fontStream            페이지 번호용 TTF 폰트 스트림 (null 시 기본 Helvetica 폰트 사용, 작업 완료 후 자동 close)
     * @return 병합 및 페이지 번호가 추가된 최종 PDF 스트림 ({@link S2ResourceInputStream} - close 시 임시 파일 자동 정리)
     * @throws IOException 입출력 또는 변환 오류 시
     * @apiNote
     * <pre>{@code
     * try (InputStream finalPdf = S2PdfUtil.mergeAndAddPageNumbers(
     *         List.of(PdfSource.ofHtml(coverHtml), PdfSource.ofPdf(bodyPdf)),
     *         10, // 10페이지에 번호 부여
     *         9,  // 9pt 크기
     *         fontStream
     * )) {
     *     // 다운로드 응답
     * }
     * }</pre>
     */
    public static InputStream mergeAndAddPageNumbers(List<PdfSource> sources, int numberOfPagesToInsert,
            Integer fontSize, InputStream fontStream) throws IOException {
        InputStream mergedStream = null;
        try {
            mergedStream = merge(sources);
            return addPageNumbers(mergedStream, numberOfPagesToInsert, fontSize, fontStream, true);
        } finally {
            if (mergedStream != null) {
                S2StreamUtil.closeStream(mergedStream);
            }
        }
    }

    // ========================================================================
    // 🛠️ 내부 변환 헬퍼 메서드
    // ========================================================================

    private static byte[] renderHtmlToPdfBytes(String htmlContent, String staticResourceBasePath, String cssPath,
            String fontPath, Class<?> clazz, String... convertCssBackgroundImageTargetSelectors) throws IOException {
        try (var out = new ByteArrayOutputStream()) {
            renderHtmlToPdf(out, htmlContent, staticResourceBasePath, cssPath, fontPath, clazz,
                    convertCssBackgroundImageTargetSelectors);
            return out.toByteArray();
        }
    }

    private static void renderHtmlToPdf(OutputStream out, String htmlContent, String staticResourceBasePath,
            String cssPath, String fontPath, Class<?> clazz, String... convertCssBackgroundImageTargetSelectors)
            throws IOException {
        var htmlWithImages = embedImages(htmlContent, staticResourceBasePath, convertCssBackgroundImageTargetSelectors);
        var cssContent = S2Util.isNotEmpty(cssPath)
                ? loadCssContent(clazz, cssPath, staticResourceBasePath, convertCssBackgroundImageTargetSelectors)
                : "";
        var completeHtml = composeHtml(htmlContent, htmlWithImages, cssContent);
        createPdf(convertToXhtml(completeHtml), clazz, fontPath, out);
    }

    /** Renders HTML into a file without holding the PDF in memory | PDF 를 메모리에 두지 않고 파일로 렌더링 */
    private static void renderHtmlToPdfFile(Path target, String htmlContent, String staticResourceBasePath,
            String cssPath, String fontPath, Class<?> clazz, String... convertCssBackgroundImageTargetSelectors)
            throws IOException {
        try (var out = new BufferedOutputStream(Files.newOutputStream(target))) {
            renderHtmlToPdf(out, htmlContent, staticResourceBasePath, cssPath, fontPath, clazz,
                    convertCssBackgroundImageTargetSelectors);
        }
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
     */
    private static void renderImageToPdfFile(BufferedImage bimg, byte[] rawBytes, Path targetPdfFile, int dpi)
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
     * A PDF source with its images shrunk to the requested dpi, through the conversion cache; the original when
     * nothing needed shrinking | 이미지를 요청한 dpi 에 맞게 줄인 PDF 소스 (변환 결과 캐시 거침). 줄일 것이 없으면 원본
     */
    private static File withSmallerImages(Path pdf, MergeOptions options, List<Path> intermediateTempFiles)
            throws IOException {
        var key = options.cache ? S2PdfCache.key("PDF-IMAGES").add(String.valueOf(options.pdfImageDpi)).addFile(pdf).hex() : null;
        var target = createTrackedTempFile("pdf_img", ".pdf", intermediateTempFiles);
        if (key != null && S2PdfCache.restore(key, target)) {
            return target.toFile();
        }
        if (!shrinkPdfImages(pdf, target, options.pdfImageDpi)) {
            return pdf.toFile();
        }
        if (key != null) {
            S2PdfCache.store(key, target);
        }
        return target.toFile();
    }

    /**
     * Shrinks images inside a PDF that are clearly larger than their page needs at {@code dpi} (an image never needs
     * more than its whole page). Returns false, writing nothing, when no image changed | PDF 안의 이미지 중 쪽에 dpi 로 필요한 것보다 확실히
     * 큰 것을 줄인다 (이미지는 쪽 전체보다 클 필요가 없음). 바뀐 것이 없으면 아무것도 쓰지 않고 false
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

    private static boolean isJpeg(byte[] bytes) {
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

    private static String convertTextToHtml(String plainText) {
        if (plainText == null) {
            return "<pre></pre>";
        }
        var escaped = plainText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");

        return "<div style=\"padding: 24px; font-family: monospace, " + DEFAULT_FONT_FAMILY + "; font-size: 10pt; line-height: 1.5; white-space: pre-wrap; word-break: break-all;\">"
                + escaped
                + "</div>";
    }

    private static String convertSvgToHtml(String svgContent) throws IOException {
        if (newSvgDrawer() == null) {
            throw new IOException(SVG_SUPPORT_REQUIRED);
        }
        if (S2Util.isEmpty(svgContent)) {
            return "<div></div>";
        }
        // S2StringUtil.replaceAll: 캐싱된 패턴 재사용으로 String.replaceAll() 대비 성능 우수
        var cleaned = S2StringUtil.replaceAll(svgContent, "<\\?xml[^>]*\\?>", "");
        cleaned = S2StringUtil.replaceAll(cleaned, "<!DOCTYPE[^>]*>", "").trim();
        return "<div style=\"padding: 24px; text-align: center;\">"
                + cleaned
                + "</div>";
    }

    // ========================================================================
    // 🌐 원격 URL 병합 (URL Merge) 편의 API
    // ========================================================================

    /**
     * 여러 개의 원격 웹 URL 리소스(PDF, HTML, 이미지, SVG, 텍스트)를 다운로드하여 사용자가 전달한 순서대로 단일 PDF 로 병합한다.
     * <p>
     * <b>비동기 분산 프리페치:</b> 모든 URL 리소스는 {@link S2ThreadUtil#getCommonExecutor()} 기반 백그라운드 병렬 다운로드되어
     * 대기 시간을 획기적으로 줄이며, 최종 병합 시에는 {@code urls} 목록의 원래 인덱스 순서를 100% 보장합니다.
     * </p>
     *
     * @param urls 병합할 원격 웹 리소스 URL 목록
     * @return 병합된 PDF 스트림 ({@link S2ResourceInputStream} - close 시 임시 파일 자동 정리)
     * @throws IOException 네트워크 오류, HTTP 4xx/5xx 응답 또는 변환 실패 시
     * @apiNote
     * <pre>{@code
     * try (InputStream merged = S2PdfUtil.mergeUrls(List.of(
     *         "https://example.com/cover.jsp",
     *         "https://example.com/report.pdf",
     *         "https://example.com/chart.png"
     * ))) {
     *     IOUtils.copy(merged, response.getOutputStream());
     * }
     * }</pre>
     */
    public static InputStream mergeUrls(List<String> urls) throws IOException {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("[mergeUrls] urls must not be null or empty.");
        }
        var sources = new ArrayList<PdfSource>();
        for (var url : urls) {
            if (S2Util.isNotEmpty(url)) {
                sources.add(PdfSource.ofUrl(url));
            }
        }
        return merge(sources);
    }

    /**
     * 여러 개의 원격 웹 URL 리소스(PDF, HTML, 이미지, SVG, 텍스트)를 다운로드하여 단일 PDF 로 병합한다. (가변인자)
     *
     * @param urls 병합할 URL 리소스 가변인자
     * @return 병합된 PDF 스트림 ({@link S2ResourceInputStream} - close 시 임시 파일 자동 정리)
     * @throws IOException 입출력 또는 변환 오류 시
     * @see #mergeUrls(List)
     */
    public static InputStream mergeUrls(String... urls) throws IOException {
        if (urls == null || urls.length == 0) {
            throw new IllegalArgumentException("[mergeUrls] urls must not be null or empty.");
        }
        return mergeUrls(Arrays.asList(urls));
    }

    /**
     * 단일 웹 URL 리소스(HTML, 이미지, SVG, PDF 등)를 다운로드하여 PDF 스트림으로 변환한다.
     *
     * @param url 변환할 웹 리소스 URL
     * @return 변환된 PDF 스트림 ({@link S2ResourceInputStream} - close 시 임시 파일 자동 정리)
     * @throws IOException 입출력 또는 변환 오류 시
     * @apiNote
     * <pre>{@code
     * try (InputStream pdfStream = S2PdfUtil.convertUrlToPdf("https://example.com/print/invoice.jsp?id=100")) {
     *     IOUtils.copy(pdfStream, response.getOutputStream());
     * }
     * }</pre>
     */
    public static InputStream convertUrlToPdf(String url) throws IOException {
        return mergeUrls(url);
    }

    /**
     * 여러 원격 HTML/JSP URL(표지, 프로젝트 정보 등)과 본문 PDF 스트림들을 단일 PDF 로 병합한다.
     * <p>
     * 원격 URL들은 비동기 병렬로 미리 프리페치되어 순서대로 결합됩니다.
     * </p>
     *
     * @param htmlUrls           원격 표지/정보 HTML 또는 JSP URL 목록 (null 허용)
     * @param notePdfStreams     본문 PDF InputStream 목록 (null 허용)
     * @param shouldCloseStreams 병합 완료 후 {@code notePdfStreams}를 자동으로 닫을지 여부
     * @return 병합된 최종 PDF 스트림 ({@link S2ResourceInputStream} - close 시 임시 파일 자동 정리)
     * @throws IOException 네트워크 오류 또는 PDF 변환/병합 오류 시
     * @apiNote
     * <pre>{@code
     * // 원격 표지 HTML URL + 로컬 PDF 병합:
     * List<String> coverUrls = List.of(
     *         "http://localhost:8080/report/cover.jsp?noteId=10",
     *         "http://localhost:8080/report/projectInfo.jsp?noteId=10"
     * );
     * List<InputStream> notePdfStreams = List.of(
     *         new FileInputStream("/data/notes/note_page1.pdf"),
     *         new FileInputStream("/data/notes/note_page2.pdf")
     * );
     *
     * try (InputStream merged = S2PdfUtil.mergeHtmlUrlsAndPdfs(coverUrls, notePdfStreams, true)) {
     *     IOUtils.copy(merged, response.getOutputStream());
     * }
     * }</pre>
     */
    public static InputStream mergeHtmlUrlsAndPdfs(List<String> htmlUrls, List<InputStream> notePdfStreams,
            boolean shouldCloseStreams) throws IOException {
        var sources = new ArrayList<PdfSource>();

        if (htmlUrls != null) {
            for (var url : htmlUrls) {
                if (S2Util.isNotEmpty(url)) {
                    sources.add(PdfSource.ofHtmlUrl(url));
                }
            }
        }

        if (notePdfStreams != null) {
            for (var stream : notePdfStreams) {
                if (stream != null) {
                    sources.add(PdfSource.ofPdf(stream).autoClose(shouldCloseStreams));
                }
            }
        }

        return merge(sources);
    }

    /**
     * 여러 원격 HTML/JSP URL(표지, 프로젝트 정보 등)과 본문 PDF 스트림들을 단일 PDF 로 병합한다. (본문 스트림 자동 close)
     *
     * @param htmlUrls       원격 표지/정보 HTML 또는 JSP URL 목록 (null 허용)
     * @param notePdfStreams 본문 PDF InputStream 목록 (null 허용, 작업 완료 후 자동 close)
     * @return 병합된 최종 PDF 스트림 ({@link S2ResourceInputStream} - close 시 임시 파일 자동 정리)
     * @throws IOException 입출력 또는 변환 오류 시
     * @see #mergeHtmlUrlsAndPdfs(List, List, boolean)
     */
    public static InputStream mergeHtmlUrlsAndPdfs(List<String> htmlUrls, List<InputStream> notePdfStreams)
            throws IOException {
        return mergeHtmlUrlsAndPdfs(htmlUrls, notePdfStreams, true);
    }

    /**
     * 표지 HTML 문자열과 본문 PDF 스트림 목록을 단일 PDF 로 병합한다. (본문 스트림 자동 close)
     *
     * @param coverHtml      표지 HTML 문자열 (null 허용)
     * @param notePdfStreams 본문 PDF InputStream 목록 (null 허용, 작업 완료 후 자동 close)
     * @return 병합된 최종 PDF 스트림 ({@link S2ResourceInputStream} - close 시 임시 파일 자동 정리)
     * @throws IOException 변환 또는 병합 오류 시
     * @apiNote
     * <pre>{@code
     * String coverHtml = "<h1>실험 보고서 표지</h1><p>작성자: 홍길동</p>";
     * try (InputStream merged = S2PdfUtil.mergeHtmlAndPdfs(coverHtml, bodyPdfStreams)) {
     *     IOUtils.copy(merged, response.getOutputStream());
     * }
     * }</pre>
     */
    public static InputStream mergeHtmlAndPdfs(String coverHtml, List<InputStream> notePdfStreams) throws IOException {
        return mergeHtmlAndPdfs(coverHtml, notePdfStreams, null, null, true);
    }

    /**
     * Copies a source PDF with one top-level bookmark to its first page; the PDF's own bookmarks move under it.
     * PDFMergerUtility appends each source's outline in order, so every source gets its own entry.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 소스 PDF 를 첫 쪽을 가리키는 최상위 책갈피 하나를 가진 사본으로 만든다. 원래 책갈피는 그 아래로 옮긴다. 원본 파일은 바꾸지 않는다.
     */
    private static File withBookmark(File pdf, String title, List<Path> trackingList) throws IOException {
        var copy = createTrackedTempFile("bookmarked", ".pdf", trackingList);
        try (var doc = Loader.loadPDF(pdf, IOUtils.createTempFileOnlyStreamCache())) {
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
        return copy.toFile();
    }

    /**
     * Draws the watermark image on every page; the image is embedded once and shared. JPEG is embedded as is, other
     * formats losslessly with their transparency | 모든 쪽에 워터마크를 그린다. 이미지는 한 번만 넣어 공유한다. JPEG 는 그대로, 그 외는 투명도를
     * 유지해 무손실로 넣는다
     */
    private static void stampWatermark(PDDocument document, Watermark watermark) throws IOException {
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
        for (var page : document.getPages()) {
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
                contentStream.drawImage(image, origin[0], origin[1], size[0], size[1]);
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
     */
    private static void stampPageNumbers(PDDocument document, int startIndex, int count, float fontSize, PDFont font,
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

    // ========================================================================
    // 🌐 URL 리소스 다운로드 및 타입 감지 내부 헬퍼
    // ========================================================================

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(S2ThreadUtil.getCommonExecutor()) // s2-core 공용 실행기 재사용 (스레드 절약)
            .build();

    private static class UrlFetchResult {
        final byte[] data;
        final String contentType;
        /** Address after redirects; relative paths in an HTML page resolve against it | 리다이렉트 후 주소 (HTML 상대 경로의 기준) */
        final URI uri;

        UrlFetchResult(byte[] data, String contentType, URI uri) {
            this.data = data;
            this.contentType = contentType;
            this.uri = uri;
        }
    }

    private static UrlFetchResult fetchUrlContent(PdfSource source) throws IOException {
        try {
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(source.urlString))
                    .timeout(source.timeout != null ? source.timeout : Duration.ofSeconds(30))
                    .GET();

            if (source.httpHeaders != null) {
                source.httpHeaders.forEach(requestBuilder::header);
            }

            var response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException(
                            String.format("URL 요청 실패 [HTTP %d]: %s", response.statusCode(), source.urlString));
                }
                // Refuse early when the server announces a larger body; the read below enforces the limit anyway
                // | 서버가 더 큰 크기를 알리면 바로 거부 (아래 읽기도 한도를 지킴)
                var declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declared > source.maxBytes) {
                    throw new IOException("URL 응답이 최대 크기(" + source.maxBytes + " bytes)를 넘습니다: " + declared + " bytes");
                }
                var contentType = response.headers().firstValue("Content-Type").orElse("");
                return new UrlFetchResult(S2StreamUtil.streamToByteArray(body, false, source.maxBytes), contentType,
                        response.uri());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("URL 다운로드 중 인터럽트 발생: " + source.urlString, e);
        } catch (Exception e) {
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("URL 리소스 다운로드 오류: " + source.urlString + " - " + e.getMessage(), e);
        }
    }

    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    /** Position of {@code pattern} within the first {@code limit} bytes, or -1 | 앞 limit 바이트 안의 위치 (없으면 -1) */
    private static int indexOf(byte[] data, byte[] pattern, int limit) {
        var last = Math.min(data.length, limit) - pattern.length;
        outer: for (int i = 0; i <= last; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static PdfSource.SourceType detectTypeFromUrlAndContent(String contentType, byte[] data, String url) {
        var lowerCt = contentType != null ? contentType.toLowerCase() : "";

        // 1. Content-Type 헤더 기반 감지
        if (lowerCt.contains("application/pdf")) {
            return PdfSource.SourceType.PDF;
        }
        if (lowerCt.contains("text/html") || lowerCt.contains("application/xhtml+xml")) {
            return PdfSource.SourceType.HTML;
        }
        if (lowerCt.contains("image/svg+xml")) {
            return PdfSource.SourceType.SVG;
        }
        if (lowerCt.startsWith("image/")) {
            return PdfSource.SourceType.IMAGE;
        }
        if (lowerCt.contains("text/plain")) {
            return PdfSource.SourceType.TEXT;
        }

        // 2. 바이너리 매직 바이트 / 텍스트 시작부 기반 감지
        if (data != null && data.length >= 4) {
            // PDF header "%PDF-" (25 50 44 46 2D). The spec lets it start within the first 1024 bytes. The old check compared
            // the fourth byte with '-', so it never matched and every PDF without a Content-Type was rendered as HTML
            // | PDF 헤더 "%PDF-". 규격상 앞 1024 바이트 안에서 시작할 수 있음. 이전 검사는 네 번째 바이트를 '-'와 비교해 한 번도 맞지 않았고,
            // Content-Type 없는 PDF 가 모두 HTML 로 렌더링되었음
            if (indexOf(data, PDF_HEADER, 1024) >= 0) {
                return PdfSource.SourceType.PDF;
            }
            // PNG: 89 50 4E 47
            if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
                return PdfSource.SourceType.IMAGE;
            }
            // JPEG: FF D8 FF
            if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) {
                return PdfSource.SourceType.IMAGE;
            }
            // GIF: GIF8
            if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
                return PdfSource.SourceType.IMAGE;
            }
            // BMP: BM
            if (data[0] == 'B' && data[1] == 'M') {
                return PdfSource.SourceType.IMAGE;
            }
            // WebP: RIFF....WEBP
            if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
                return PdfSource.SourceType.IMAGE;
            }

            // 앞부분 텍스트 검사 (최대 512바이트)
            int checkLen = Math.min(data.length, 512);
            var headStr = new String(data, 0, checkLen, StandardCharsets.UTF_8).toLowerCase();
            if (headStr.contains("<svg")) {
                return PdfSource.SourceType.SVG;
            }
            if (headStr.contains("<html") || headStr.contains("<!doctype html") || headStr.contains("<body")
                    || headStr.contains("<div")) {
                return PdfSource.SourceType.HTML;
            }
        }

        // 3. URL 확장자 기반 감지
        var lowerUrl = url != null ? url.toLowerCase() : "";
        var cleanUrl = lowerUrl.contains("?") ? lowerUrl.substring(0, lowerUrl.indexOf('?')) : lowerUrl;
        if (cleanUrl.endsWith(".pdf")) {
            return PdfSource.SourceType.PDF;
        }
        if (cleanUrl.endsWith(".html") || cleanUrl.endsWith(".htm") || cleanUrl.endsWith(".jsp")
                || cleanUrl.endsWith(".do")) {
            return PdfSource.SourceType.HTML;
        }
        if (cleanUrl.endsWith(".png") || cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg")
                || cleanUrl.endsWith(".gif") || cleanUrl.endsWith(".bmp") || cleanUrl.endsWith(".webp")) {
            return PdfSource.SourceType.IMAGE;
        }
        if (cleanUrl.endsWith(".svg")) {
            return PdfSource.SourceType.SVG;
        }
        if (cleanUrl.endsWith(".txt") || cleanUrl.endsWith(".log")) {
            return PdfSource.SourceType.TEXT;
        }

        // 기본 fallback은 HTML (서버 측 동적 URL 렌더링 결과)
        return PdfSource.SourceType.HTML;
    }

    /**
     * HTTP 응답 Content-Type 헤더에서 charset 인코딩을 추출한다. (지정되지 않았거나 유효하지 않으면 기본값 반환)
     */
    private static Charset parseCharsetFromContentType(String contentType, Charset defaultCharset) {
        if (S2Util.isEmpty(contentType)) {
            return defaultCharset;
        }
        var lower = contentType.toLowerCase();
        int idx = lower.indexOf("charset=");
        if (idx != -1) {
            String csName = contentType.substring(idx + 8).trim();
            int semiIdx = csName.indexOf(';');
            if (semiIdx != -1) {
                csName = csName.substring(0, semiIdx).trim();
            }
            csName = S2StringUtil.replaceChars(csName, "", '"', '\'');
            try {
                return Charset.forName(csName);
            } catch (Exception ignore) {
            }
        }
        return defaultCharset;
    }

}
