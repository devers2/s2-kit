package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2kit.support.S2PdfUtil.ImageStamp;
import io.github.devers2.s2kit.support.S2PdfUtil.StampOptions;
import io.github.devers2.s2kit.support.S2PdfUtil.Text;

/**
 * Stamping uses the default font and reports what it cannot draw instead of leaving it out.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 스탬프가 기본 폰트를 쓰고, 그리지 못한 것은 빠뜨리지 않고 예외로 알리는지 확인합니다.
 */
class S2PdfStampFailureTest {

    @TempDir
    Path dir;

    private Path blankPdf() throws IOException {
        var pdf = dir.resolve("blank.pdf");
        try (var document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        return pdf;
    }

    // 기본 폰트(setDefaultFont·시스템 한글 폰트)를 쓰지 않고 Helvetica 로 그리다 실패한 줄을 로그만 남기고 뺐다.
    @Test
    void koreanTextUsesDefaultFontOrFails() throws IOException {
        var options = StampOptions.builder().addText(Text.of("ABC 한글")).build();
        if (S2PdfUtil.resolveFont(null, null) == null) {
            assertThrows(IOException.class, () -> S2PdfUtil.stamp(blankPdf(), options).close());
            return;
        }
        try (var in = S2PdfUtil.stamp(blankPdf(), options); var document = Loader.loadPDF(in.readAllBytes())) {
            var text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("ABC") && text.contains("한글"), text);
        }
    }

    // 워터마크와 달리 이미지 스탬프는 읽지 못해도 로그만 남기고 빠졌다.
    @Test
    void unreadableImageStampFails() {
        var options = StampOptions.builder().addImage(ImageStamp.of(new byte[] { 1, 2, 3, 4 })).build();
        assertThrows(IOException.class, () -> S2PdfUtil.stamp(blankPdf(), options).close());
    }

    // 아주 작은 반복 간격은 float 덧셈이 나아가지 않아 끝나지 않았다.
    @Test
    void tinyRepeatGapFinishes() {
        var options = StampOptions.builder().addText(Text.of("A").repeat(true, 1e-6f, 1e-6f)).build();
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            try (var in = S2PdfUtil.stamp(blankPdf(), options)) {
                assertTrue(in.readAllBytes().length > 0);
            }
        });
        assertTrue(Files.isDirectory(dir));
    }
}
