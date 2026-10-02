package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.devers2.s2util.exception.S2RuntimeException;

/**
 * Image resizing and conversion: sizes, transparency, source deletion and explicit failures.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 이미지 크기·형식 변환의 크기 계산, 투명도, 원본 삭제, 명시적 실패를 확인합니다.
 */
class S2ImageUtilTest {

    @TempDir
    Path dir;

    private static byte[] png(int width, int height, int type) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, type), "png", out);
        return out.toByteArray();
    }

    private static BufferedImage resize(int width, int height, Integer maxWidth, Integer maxHeight, boolean fixed)
            throws IOException {
        return S2ImageUtil.imageResize(new ByteArrayInputStream(png(width, height, BufferedImage.TYPE_INT_RGB)),
                maxWidth, maxHeight, fixed);
    }

    private static String size(BufferedImage image) {
        return image.getWidth() + "x" + image.getHeight();
    }

    @Test
    void resizeLimitsEachDimension() throws IOException {
        // Not fixed rate: only the side over its limit shrinks | 비율 무시: 넘는 쪽만 줄어듦
        assertEquals("200x100", size(resize(400, 100, 200, 300, false)));
        assertEquals("200x300", size(resize(400, 500, 200, 300, false)));
        assertEquals("100x50", size(resize(100, 50, 200, 300, false)));
        // Fixed rate: fits both limits with the same scale | 비율 고정: 같은 비율로 두 제한 안에
        assertEquals("200x50", size(resize(400, 100, 200, 300, true)));
        assertEquals("240x300", size(resize(400, 500, 300, 300, true)));
        assertEquals("100x50", size(resize(100, 50, null, null, true)));
    }

    @Test
    void transparentImagesKeepAlphaInPngAndFlattenInJpg() throws IOException {
        var source = Files.write(dir.resolve("alpha.png"), png(40, 40, BufferedImage.TYPE_INT_ARGB));
        var small = S2ImageUtil.imageResize(source, 20, 20, true, null, "small.png", false);
        assertTrue(ImageIO.read(small.getFile().toFile()).getColorModel().hasAlpha());

        var jpg = S2ImageUtil.convertImageExtension(source, "jpg", null, null, false);
        assertEquals("jpg", jpg.getExtension());
        assertEquals(40, ImageIO.read(jpg.getFile().toFile()).getWidth());
        assertTrue(Files.exists(source));
    }

    @Test
    void sourceIsDeletedImmediatelyWhenMoved() throws IOException {
        var source = Files.write(dir.resolve("a.png"), png(40, 40, BufferedImage.TYPE_INT_RGB));
        var result = S2ImageUtil.convertImage(source, 20, 20, true, null, null, "jpg", dir.resolve("out").toString(),
                null, true);
        assertEquals(dir.resolve("out/a.jpg"), result.getFile());
        assertTrue(Files.exists(result.getFile()));
        assertFalse(Files.exists(source), "deleted now, not at JVM shutdown");
    }

    @Test
    void failuresAreExplicit() throws IOException {
        var text = Files.writeString(dir.resolve("not-image.png"), "hello");
        assertThrows(S2RuntimeException.class, () -> S2ImageUtil.imageResize(text, 10, 10, true, null, null, false));
        assertThrows(S2RuntimeException.class,
                () -> S2ImageUtil.convertImageExtension(dir.resolve("missing.png"), "jpg", null, null, false));
        var image = Files.write(dir.resolve("b.png"), png(4, 4, BufferedImage.TYPE_INT_RGB));
        assertThrows(S2RuntimeException.class, () -> S2ImageUtil.convertImageExtension(image, "nosuchformat", null, null, false));
        assertThrows(NullPointerException.class, () -> S2ImageUtil.encodeImageToBase64(null));
    }

    @Test
    void hugeDeclaredSizeIsRefusedBeforeDecoding() throws IOException {
        // PNG signature + IHDR declaring 50000x50000 (2.5 gigapixels) and nothing else | 25억 픽셀을 선언한 IHDR 만 있는 PNG
        var ihdr = java.nio.ByteBuffer.allocate(17).put("IHDR".getBytes()).putInt(50_000).putInt(50_000)
                .put(new byte[] { 8, 2, 0, 0, 0 }).array();
        var crc = new java.util.zip.CRC32();
        crc.update(ihdr);
        var bytes = java.nio.ByteBuffer.allocate(8 + 4 + 17 + 4)
                .put(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a }).putInt(13).put(ihdr)
                .putInt((int) crc.getValue()).array();
        var e = assertThrows(S2RuntimeException.class,
                () -> S2ImageUtil.imageResize(new ByteArrayInputStream(bytes), 10, 10, true));
        assertTrue(e.getCause().getMessage().contains("50000x50000"), e.getCause().getMessage());
    }
}
