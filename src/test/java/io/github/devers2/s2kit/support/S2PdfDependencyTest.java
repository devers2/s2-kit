package io.github.devers2.s2kit.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.devers2.s2util.core.S2Util;

/**
 * Without Apache PDFBox (an optional dependency) {@link S2PdfUtil} names the missing dependency instead of failing
 * with a bare {@code NoClassDefFoundError: org/apache/pdfbox/...}, and stamp options can still be built.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 선택 의존성 Apache PDFBox 가 없을 때 {@link S2PdfUtil}이 안내 없는 {@code NoClassDefFoundError: org/apache/pdfbox/...} 대신 빠진
 * 의존성을 알려 주고, 스탬프 옵션은 그대로 만들 수 있는지 확인한다.
 */
class S2PdfDependencyTest {

    /** s2-kit and s2-core classes only, without PDFBox | PDFBox 없이 s2-kit 과 s2-core 클래스만 */
    private static URLClassLoader withoutPdfBox() throws Exception {
        URL kit = S2PdfUtil.class.getProtectionDomain().getCodeSource().getLocation();
        URL core = S2Util.class.getProtectionDomain().getCodeSource().getLocation();
        return new URLClassLoader(new URL[] { kit, core }, ClassLoader.getPlatformClassLoader());
    }

    @Test
    void callsNameTheMissingDependencyEveryTime() throws Throwable {
        try (var loader = withoutPdfBox()) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.apache.pdfbox.pdmodel.PDDocument"));
            var util = Class.forName(S2PdfUtil.class.getName(), true, loader);
            // Linked like a compiled call site (one method), not through Class.getMethod, which resolves the types of
            // every method | 컴파일된 호출처럼 메서드 하나만 연결 (Class.getMethod 는 모든 메서드의 타입을 해석함)
            var getPageCount = MethodHandles.publicLookup().findStatic(util, "getPageCount",
                    MethodType.methodType(int.class, Path.class));
            for (int i = 0; i < 2; i++) { // the second call must say the same, not "Could not initialize class"
                var e = assertThrows(NoClassDefFoundError.class, () -> {
                    var ignored = (int) getPageCount.invokeExact(Path.of("x.pdf"));
                });
                assertTrue(e.getMessage().contains("org.apache.pdfbox:pdfbox"), e.getMessage());
            }
        }
    }

    @Test
    void optionsCanBeBuiltWithoutPdfBox() throws Throwable {
        try (var loader = withoutPdfBox()) {
            var text = Class.forName(S2PdfUtil.class.getName() + "$Text", true, loader);
            var of = MethodHandles.publicLookup().findStatic(text, "of", MethodType.methodType(text, String.class));
            Object built = of.invoke("CONFIDENTIAL");
            assertEquals(text, built.getClass());
        }
    }
}
