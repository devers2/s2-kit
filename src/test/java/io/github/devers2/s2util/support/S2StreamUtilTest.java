package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.devers2.s2util.exception.S2RuntimeException;

/**
 * Stream conversion fails loudly and encodes characters across buffer boundaries correctly.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 스트림 변환이 실패를 숨기지 않고, 버퍼 경계에 걸친 문자를 올바르게 인코딩하는지 확인합니다.
 */
class S2StreamUtilTest {

    @Test
    void surrogatePairAcrossTheBufferBoundaryIsPreserved() {
        for (int offset = -2; offset <= 2; offset++) {
            var text = "a".repeat(32 * 1024 - 1 + offset) + "😀b" + "가".repeat(10);
            var bytes = S2StreamUtil.streamToByteArray(new StringReader(text), true);
            assertEquals(text, new String(bytes, StandardCharsets.UTF_8), "offset " + offset);
        }
    }

    @Test
    void limitIsEnforcedWithAnException() {
        var data = new byte[1001];
        assertEquals(1001, S2StreamUtil.streamToByteArray(new ByteArrayInputStream(data), true, 1001).length);
        var e = assertThrows(S2RuntimeException.class,
                () -> S2StreamUtil.streamToByteArray(new ByteArrayInputStream(data), true, 1000));
        assertTrue(e.getMessage().contains("1000"));
        assertThrows(S2RuntimeException.class,
                () -> S2StreamUtil.streamToByteArray(new StringReader("가".repeat(400)), true, 1000));
    }

    @Test
    void failuresKeepTheCause() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk gone");
            }
        };
        var e = assertThrows(S2RuntimeException.class, () -> S2StreamUtil.streamToByteArray(broken, true));
        assertEquals("disk gone", e.getCause().getMessage());
        var e2 = assertThrows(S2RuntimeException.class, () -> S2StreamUtil.convertStreamToString(broken));
        assertInstanceOf(IOException.class, e2.getCause());

        assertThrows(NullPointerException.class, () -> S2StreamUtil.streamToByteArray((InputStream) null, true));
        assertEquals("한글", S2StreamUtil.convertStreamToString(new ByteArrayInputStream("한글".getBytes(StandardCharsets.UTF_8))));
    }
}
