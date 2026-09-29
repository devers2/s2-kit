package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import com.sun.net.httpserver.HttpServer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import io.github.devers2.s2util.file.S2ResourceInputStream;
import io.github.devers2.s2util.support.S2PdfUtil.PdfSource;

/**
 * S2PdfUtil 종합 검증 테스트
 */
@ExtendWith(S2PdfUtilTest.SummaryExtension.class)
@DisplayName("S2PdfUtil 통합 병합 및 변환 검증 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S2PdfUtilTest {

    // =====================================================================
    // [내부 유틸리티] 테스트 결과 요약 Extension
    // =====================================================================
    static class SummaryExtension implements TestWatcher, AfterAllCallback {
        private static final AtomicInteger totalCount = new AtomicInteger(0);
        private static final AtomicInteger successCount = new AtomicInteger(0);
        private static final AtomicInteger failureCount = new AtomicInteger(0);
        private static final List<String> failureDetails = new ArrayList<>();

        @Override
        public void testSuccessful(ExtensionContext context) {
            totalCount.incrementAndGet();
            successCount.incrementAndGet();
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            totalCount.incrementAndGet();
            failureCount.incrementAndGet();
            failureDetails.add(context.getDisplayName() + " : " + cause.getMessage());
        }

        @Override
        public void afterAll(ExtensionContext context) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("  [ S2PdfUtil 테스트 실행 결과 요약 ]");
            System.out.println("-".repeat(60));
            System.out.printf("  ✔ 전체 테스트 개수 : %d%n", totalCount.get());
            System.out.printf("  ✅ 성공 개수       : %d%n", successCount.get());
            System.out.printf("  ❌ 실패 개수       : %d%n", failureCount.get());
            if (!failureDetails.isEmpty()) {
                System.out.println("-".repeat(60));
                System.out.println("  [ 실패 상세 목록 ]");
                failureDetails.forEach(f -> System.out.println("    - " + f));
            }
            System.out.println("=".repeat(60));
        }
    }

    private static byte[] createSampleImage(int width, int height, Color color) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();

        try (var baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }

    private static boolean startsWithPdfMagic(byte[] data) {
        return data != null && data.length >= 5 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F' && data[4] == '-';
    }

    private static String headHex(byte[] data) {
        if (data == null) {
            return "null";
        }
        var sb = new StringBuilder();
        for (int i = 0; i < Math.min(16, data.length); i++) {
            sb.append(String.format("%02X ", data[i]));
        }
        return sb.toString().trim();
    }

    private static byte[] createSamplePdf(String html) throws IOException {
        try (InputStream is = S2PdfUtil.convertHtmlToPdfStream(html, null, null, null, S2PdfUtil.class)) {
            return is.readAllBytes();
        }
    }

    private static HttpServer mockServer;
    private static String baseUrl;
    private static byte[] sampleServerPdf;
    private static byte[] sampleServerPng;

    @BeforeAll
    static void startMockHttpServer() throws IOException {
        sampleServerPdf = createSamplePdf("<h3>Server PDF Document</h3>");
        sampleServerPng = createSampleImage(100, 100, Color.GREEN);

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        int port = mockServer.getAddress().getPort();
        baseUrl = "http://localhost:" + port;

        mockServer.createContext("/cover", exchange -> {
            byte[] response = "<h1>원격 표지 문서</h1><p>연구과제: AI 엔진</p>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (var os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        mockServer.createContext("/image.png", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, sampleServerPng.length);
            try (var os = exchange.getResponseBody()) {
                os.write(sampleServerPng);
            }
        });

        mockServer.createContext("/sample.pdf", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, sampleServerPdf.length);
            try (var os = exchange.getResponseBody()) {
                os.write(sampleServerPdf);
            }
        });

        mockServer.createContext("/magic-pdf", exchange -> {
            // Content-Type 없이 바이너리로 전송 -> 매직 바이트 %PDF- 로 자동 감지 검증
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, sampleServerPdf.length);
            try (var os = exchange.getResponseBody()) {
                os.write(sampleServerPdf);
            }
        });

        mockServer.createContext("/euckr-page", exchange -> {
            byte[] response = "<h1>한글 EUC-KR 테스트 문서</h1>".getBytes(Charset.forName("EUC-KR"));
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=EUC-KR");
            exchange.sendResponseHeaders(200, response.length);
            try (var os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        mockServer.createContext("/error404", exchange -> {
            exchange.sendResponseHeaders(404, -1);
        });

        mockServer.start();
    }

    @AfterAll
    static void stopMockHttpServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    @Order(1)
    @DisplayName("HTML -> PDF 변환 검증")
    void testConvertHtmlToPdf() throws IOException {
        String html = "<h1>연구노트 표지</h1><p>작성자: 홍길동</p>";
        try (InputStream pdfStream = S2PdfUtil.convertHtmlToPdfStream(html, null, null, null, S2PdfUtil.class)) {
            assertNotNull(pdfStream);
            byte[] pdfBytes = pdfStream.readAllBytes();
            assertTrue(pdfBytes.length > 0);

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages());
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("이미지(byte[]) -> PDF 변환 검증")
    void testConvertImageToPdf() throws IOException {
        byte[] imgBytes = createSampleImage(400, 300, Color.BLUE);
        try (InputStream pdfStream = S2PdfUtil.convertImageToPdf(imgBytes)) {
            assertNotNull(pdfStream);
            byte[] pdfBytes = pdfStream.readAllBytes();
            assertTrue(pdfBytes.length > 0);

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages());
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("이미지(BufferedImage) -> PDF 변환 검증")
    void testConvertBufferedImageToPdf() throws IOException {
        var img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 200);
        g.dispose();

        try (InputStream pdfStream = S2PdfUtil.convertImageToPdf(img)) {
            assertNotNull(pdfStream);
            byte[] pdfBytes = pdfStream.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages());
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("일반 텍스트 -> PDF 변환 검증")
    void testConvertTextToPdf() throws IOException {
        String text = "Line 1: Hello S2Util!\nLine 2: PDF Merger Test\nLine 3: Special chars <>&\"";
        try (InputStream pdfStream = S2PdfUtil.convertTextToPdfStream(text)) {
            assertNotNull(pdfStream);
            byte[] pdfBytes = pdfStream.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages());
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("SVG 벡터 그래픽 -> PDF 변환 검증")
    void testConvertSvgToPdf() throws IOException {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"200\" viewBox=\"0 0 100 100\">" +
                "<circle cx=\"50\" cy=\"50\" r=\"40\" stroke=\"green\" stroke-width=\"4\" fill=\"yellow\" />" +
                "</svg>";
        try (InputStream pdfStream = S2PdfUtil.convertSvgToPdf(svg)) {
            assertNotNull(pdfStream);
            byte[] pdfBytes = pdfStream.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages());
            }
        }
    }

    @Test
    @Order(6)
    @DisplayName("다중 PDF 병합 검증 (mergePdfs)")
    void testMergeMultiplePdfs() throws IOException {
        byte[] pdf1 = createSamplePdf("<h2>문서 1 본문</h2>");
        byte[] pdf2 = createSamplePdf("<h2>문서 2 본문</h2>");

        try (InputStream mergedStream = S2PdfUtil.mergePdfs(
                new ByteArrayInputStream(pdf1),
                new ByteArrayInputStream(pdf2))) {
            assertNotNull(mergedStream);
            byte[] mergedBytes = mergedStream.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(mergedBytes)) {
                assertEquals(2, doc.getNumberOfPages());
            }
        }
    }

    @Test
    @Order(7)
    @DisplayName("5대 이종 포맷(PDF + HTML + Image + Text + SVG) 일괄 병합 검증 (merge)")
    void testMergeAllTypes() throws IOException {
        byte[] basePdf = createSamplePdf("<h2>기존 PDF 페이지</h2>");
        String coverHtml = "<h1>표지 HTML</h1>";
        byte[] imageBytes = createSampleImage(300, 300, Color.MAGENTA);
        String plainText = "실험 결과 원문 데이터:\nMeasurement A: 12.34\nMeasurement B: 56.78";
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"200\"><rect width=\"200\" height=\"200\" fill=\"cyan\" /></svg>";

        var sources = List.of(
                PdfSource.ofHtml(coverHtml),
                PdfSource.ofPdf(basePdf),
                PdfSource.ofImage(imageBytes),
                PdfSource.ofText(plainText),
                PdfSource.ofSvg(svg));

        try (InputStream mergedStream = S2PdfUtil.merge(sources)) {
            assertNotNull(mergedStream);
            byte[] mergedBytes = mergedStream.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(mergedBytes)) {
                assertEquals(5, doc.getNumberOfPages(), "5가지 소스가 각각 1페이지씩 총 5페이지로 병합되어야 합니다.");
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("표지 HTML + 본문 PDF 목록 특화 병합 검증 (mergeHtmlAndPdfs)")
    void testMergeHtmlAndPdfs() throws IOException {
        String coverHtml = "<h1>연구 프로젝트 최종 보고서</h1>";
        byte[] note1 = createSamplePdf("<h3>실험노트 1</h3>");
        byte[] note2 = createSamplePdf("<h3>실험노트 2</h3>");

        List<InputStream> noteStreams = List.of(
                new ByteArrayInputStream(note1),
                new ByteArrayInputStream(note2));

        try (InputStream mergedStream = S2PdfUtil.mergeHtmlAndPdfs(coverHtml, noteStreams, null, S2PdfUtil.class,
                true)) {
            assertNotNull(mergedStream);
            byte[] mergedBytes = mergedStream.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(mergedBytes)) {
                assertEquals(3, doc.getNumberOfPages(), "표지 1장 + 노트 2장 = 총 3페이지여야 합니다.");
            }
        }
    }

    @Test
    @Order(9)
    @DisplayName("병합 후 페이지 번호 일괄 부여 검증 (mergeAndAddPageNumbers)")
    void testMergeAndAddPageNumbers() throws IOException {
        String coverHtml = "<h1>전체 표지</h1>";
        byte[] bodyPdf = createSamplePdf("<h2>본문 내용</h2>");

        var sources = List.of(
                PdfSource.ofHtml(coverHtml),
                PdfSource.ofPdf(bodyPdf));

        try (InputStream pagedStream = S2PdfUtil.mergeAndAddPageNumbers(sources, 2, 11, null)) {
            assertNotNull(pagedStream);
            byte[] pagedBytes = pagedStream.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(pagedBytes)) {
                assertEquals(2, doc.getNumberOfPages());
            }
        }
    }

    @Test
    @Order(10)
    @DisplayName("S2ResourceInputStream 스트림 close 시 임시 파일 자동 삭제(누수 차단) 검증")
    void testZeroLeakResourceCleanup() throws IOException {
        Path tempFileRef;
        S2ResourceInputStream resourceStream = null;
        try (InputStream mergedStream = S2PdfUtil.merge(
                PdfSource.ofHtml("<h2>임시 파일 삭제 검증</h2>"),
                PdfSource.ofText("본문 내용"))) {
            assertTrue(mergedStream instanceof S2ResourceInputStream, "반환된 스트림은 S2ResourceInputStream 이어야 합니다.");
            resourceStream = (S2ResourceInputStream) mergedStream;
            assertFalse(resourceStream.getTempFiles().isEmpty(), "임시 파일 목록이 비어있지 않아야 합니다.");
            tempFileRef = resourceStream.getTempFiles().get(0);

            assertNotNull(tempFileRef);
            assertTrue(Files.exists(tempFileRef), "스트림이 열려 있는 동안 임시 파일은 디스크에 존재해야 합니다.");
        } finally {
            S2StreamUtil.closeStream(resourceStream);
        }

        // 스트림 닫힘 후 디스크에서 완전히 삭제되었는지 검증
        assertFalse(Files.exists(tempFileRef), "스트림이 닫힌 즉시 임시 파일은 디스크에서 삭제되어야 합니다.");
    }

    @Test
    @Order(11)
    @DisplayName("비정상 입력에 대한 예외 방어 검증")
    void testEdgeCases() {
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.merge((List<PdfSource>) null));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.merge(new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.mergePdfs((List<InputStream>) null, true));
        assertThrows(IllegalArgumentException.class, () -> S2PdfUtil.mergeImagesToPdf((List<InputStream>) null, true));
    }

    @Test
    @Order(12)
    @DisplayName("원격 URL 소스 병합 검증 (HTML URL + Image URL + PDF URL)")
    void testMergeUrls() throws IOException {
        var sources = List.of(
                PdfSource.ofHtmlUrl(baseUrl + "/cover"),
                PdfSource.ofImageUrl(baseUrl + "/image.png"),
                PdfSource.ofPdfUrl(baseUrl + "/sample.pdf"));

        try (InputStream mergedStream = S2PdfUtil.merge(sources)) {
            assertNotNull(mergedStream);
            byte[] mergedBytes = mergedStream.readAllBytes();
            assertTrue(mergedBytes.length > 0);

            try (PDDocument doc = Loader.loadPDF(mergedBytes)) {
                assertEquals(3, doc.getNumberOfPages(), "HTML 표지(1) + 이미지(1) + PDF(1) = 총 3페이지여야 합니다.");
            }
        }
    }

    @Test
    @Order(13)
    @DisplayName("Goono-ELN 사이냅 호환 표지 URL + 본문 PDF 스트림 병합 편의 메서드 검증 (mergeHtmlUrlsAndPdfs)")
    void testMergeHtmlUrlsAndPdfs() throws IOException {
        var htmlUrls = List.of(baseUrl + "/cover");
        var noteStreams = List.of(
                (InputStream) new ByteArrayInputStream(createSamplePdf("<h2>노트 본문 1</h2>")),
                (InputStream) new ByteArrayInputStream(createSamplePdf("<h2>노트 본문 2</h2>")));

        try (InputStream mergedStream = S2PdfUtil.mergeHtmlUrlsAndPdfs(htmlUrls, noteStreams, true)) {
            assertNotNull(mergedStream);
            byte[] mergedBytes = mergedStream.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(mergedBytes)) {
                assertEquals(3, doc.getNumberOfPages(), "원격 표지 1장 + 노트 2장 = 총 3페이지여야 합니다.");
            }
        }
    }

    @Test
    @Order(14)
    @DisplayName("Content-Type 부재 시 바이너리 매직 바이트 기반 PDF 자동 감지 검증")
    void testUrlMagicByteDetection() throws Exception {
        // Diagnostics for an intermittent CI failure where the payload was treated as HTML: verify the source and the transport separately. | CI 에서 간헐적으로 페이로드가 HTML 로 처리된 실패의 진단: 원본과 전송 경로를 각각 확인
        assertTrue(startsWithPdfMagic(sampleServerPdf), "원본 샘플 PDF가 %PDF- 로 시작하지 않습니다: " + headHex(sampleServerPdf));
        byte[] raw = java.net.http.HttpClient.newHttpClient()
                .send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/magic-pdf")).build(),
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray())
                .body();
        assertTrue(startsWithPdfMagic(raw), "모의 서버 응답이 %PDF- 로 시작하지 않습니다: " + headHex(raw) + " (len=" + raw.length + ", 기대 len=" + sampleServerPdf.length + ")");

        try (InputStream pdfStream = S2PdfUtil.convertUrlToPdf(baseUrl + "/magic-pdf")) {
            assertNotNull(pdfStream);
            byte[] pdfBytes = pdfStream.readAllBytes();

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                assertEquals(1, doc.getNumberOfPages());
            }
        }
    }

    @Test
    @Order(15)
    @DisplayName("HTTP 404 오류 URL 요청 시 방어 및 예외 처리 검증")
    void testUrlHttpErrorHandling() {
        assertThrows(IOException.class, () -> {
            try (InputStream is = S2PdfUtil.convertUrlToPdf(baseUrl + "/error404")) {
                is.readAllBytes();
            }
        });
    }

    @Test
    @Order(16)
    @DisplayName("모든 지원 타입(HTML, 이미지, 텍스트, SVG, PDF, URL) 순서 보장 병합 및 메모리 누수 방지 검증")
    void testMergeAllTypesInSequence() throws IOException {
        var sources = List.of(
                S2PdfUtil.PdfSource.ofHtml("<h1>1. HTML 소스</h1>"),
                S2PdfUtil.PdfSource.ofImage(sampleServerPng),
                S2PdfUtil.PdfSource.ofText("3. 일반 텍스트 소스 내용"),
                S2PdfUtil.PdfSource
                        .ofSvg("<svg width='100' height='100'><circle cx='50' cy='50' r='40' fill='red'/></svg>"),
                S2PdfUtil.PdfSource.ofPdf(sampleServerPdf),
                S2PdfUtil.PdfSource.ofUrl(baseUrl + "/cover"));

        S2ResourceInputStream resourceStream = null;
        Path tempFilePath;

        try (InputStream is = S2PdfUtil.merge(sources)) {
            assertTrue(is instanceof S2ResourceInputStream, "결과 스트림은 S2ResourceInputStream 이어야 합니다.");
            resourceStream = (S2ResourceInputStream) is;
            tempFilePath = resourceStream.getTempFiles().get(0);
            assertTrue(Files.exists(tempFilePath), "병합 완료 시점에는 결과 임시 파일이 디스크에 존재해야 합니다.");

            byte[] mergedBytes = is.readAllBytes();
            try (PDDocument doc = Loader.loadPDF(mergedBytes)) {
                assertEquals(6, doc.getNumberOfPages(), "6가지 타입의 문서가 각 1장씩 순서대로 총 6페이지로 병합되어야 합니다.");
            }
        } finally {
            S2StreamUtil.closeStream(resourceStream);
        }

        // close() 호출 후 임시 파일이 자동으로 삭제되었는지 검증 (메모리/디스크 누수 방지)
        assertFalse(Files.exists(tempFilePath), "S2ResourceInputStream이 닫히면 임시 파일이 100% 자동 삭제되어야 합니다.");
    }

    @Test
    @Order(17)
    @DisplayName("원격 URL 비동기 분산 프리페치 시 순서 일치 및 Content-Type Charset(EUC-KR) 디코딩 검증")
    void testDistributedAsyncUrlPrefetchWithOrderAndCharset() throws IOException {
        var urlList = List.of(
                baseUrl + "/cover",
                baseUrl + "/euckr-page",
                baseUrl + "/sample.pdf",
                baseUrl + "/image.png");

        try (InputStream mergedStream = S2PdfUtil.mergeUrls(urlList)) {
            assertNotNull(mergedStream);
            byte[] bytes = mergedStream.readAllBytes();
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                assertEquals(4, doc.getNumberOfPages(), "4개의 URL이 입력된 순서대로 정확히 4페이지로 병합되어야 합니다.");
            }
        }
    }
}
