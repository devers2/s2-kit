package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tag content removal with nesting and unclosed tags.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 중첩·미종료 태그가 있을 때의 태그 내용 제거를 확인합니다.
 */
class S2MarkupUtilTest {

    @Test
    void removesNestedAndUnclosedTags() {
        assertEquals("a<p>b</p>c", S2MarkupUtil.removeTagContent("a<div x=\"1\"><div>in</div>out</div><p>b</p>c", "div"));
        assertEquals("safe ", S2MarkupUtil.removeTagContent("safe <script>alert(1)", "script"));
        assertEquals("x", S2MarkupUtil.removeTagContent("x<SCRIPT>a</script >", "script"));
        assertEquals("keep</script>", S2MarkupUtil.removeTagContent("keep</script>", "script"));
    }

    @Test
    void topLevelTag() {
        assertTrue(S2MarkupUtil.isTopLevelTag("<html><body><html></html></body></html>", "html"));
        assertFalse(S2MarkupUtil.isTopLevelTag("<html></html><p/>", "html"));
        assertEquals("<b>x</b>", S2MarkupUtil.removeTopLevelTag("<html lang=\"ko\"><b>x</b></html>", "html"));
    }
}
