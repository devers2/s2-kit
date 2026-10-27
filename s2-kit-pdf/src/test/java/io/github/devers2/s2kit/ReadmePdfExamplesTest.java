package io.github.devers2.s2kit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.devers2.s2kit.support.S2OfficeConverter;
import io.github.devers2.s2kit.support.S2PdfUtil;

/**
 * PDF examples verification for s2-kit-pdf.
 */
class ReadmePdfExamplesTest {

    @Test
    void testPdfAvailability() {
        assertNotNull(S2PdfUtil.class);
        assertNotNull(S2OfficeConverter.class);
    }

    /** Compiled, not run: it needs a font file and a network | 컴파일만 확인 (폰트 파일과 네트워크 필요) */
    @SuppressWarnings("unused")
    private static void pdfMerge(java.io.OutputStream responseOutput) throws Exception {
        S2PdfUtil.setDefaultFont(Path.of("/usr/share/fonts/truetype/nanum/NanumGothic.ttf"));

        var options = S2PdfUtil.MergeOptions.create().bookmarks(true).pageNumbers(true)
                .title("보고서");
        try (InputStream pdf = S2PdfUtil.merge(List.of(
                S2PdfUtil.PdfSource.ofHtml("<h1>표지</h1>").title("표지"),
                S2PdfUtil.PdfSource.ofPdf(Path.of("body.pdf")),
                S2PdfUtil.PdfSource.ofUrl("https://example.com/chart.png")
                        .maxBytes(10_000_000)),
                options)) {
            pdf.transferTo(responseOutput);
        }

        var sessionId = "abc";
        S2PdfUtil.merge(S2PdfUtil.PdfSource.ofUrl(
                "https://intra.example.com/report/view?id=10", Map.of("Cookie", "JSESSIONID=" + sessionId),
                java.time.Duration.ofSeconds(30)));

        InputStream uploadedStream = InputStream.nullInputStream();
        if (S2OfficeConverter.isAvailable()) {
            S2PdfUtil.merge(List.of(
                    S2PdfUtil.PdfSource.ofDocument(Path.of("계획서.docx")),
                    S2PdfUtil.PdfSource.ofDocument(uploadedStream, "보고서.hwp")));
        }
    }
}
