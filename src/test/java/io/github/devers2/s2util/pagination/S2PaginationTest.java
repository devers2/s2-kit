package io.github.devers2.s2util.pagination;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import io.github.devers2.s2util.pagination.vo.S2SearchVO;

/**
 * Pagination rendering, sort columns, paging bounds and the JSP tag library descriptors.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 페이지네이션 렌더링, 정렬 컬럼, 페이지 범위, JSP 태그 라이브러리 설정을 확인합니다.
 */
class S2PaginationTest {

    private static S2PaginationTag tag(String jsFunction, int pageNo, long total) {
        var searchVO = new S2SearchVO();
        searchVO.setPageNo(pageNo);
        var tag = new S2PaginationTag();
        tag.setPaginationInfo(new S2PaginationInfo<>(searchVO, new ArrayList<>(), total));
        tag.setJsFunction(jsFunction);
        return tag;
    }

    @Test
    void renderEscapesParametersAndKeepsNumbers() {
        var html = tag("app.list", 2, 30).renderPagination("x');alert(1);//\"<", "7");
        assertTrue(html.contains("onclick=\"app.list('x\\u0027\\u0029\\u003balert\\u00281\\u0029\\u003b\\u002f\\u002f\\u0022\\u003c', 7, 1);"),
                html);
        assertFalse(html.contains("alert(1)"), html);
        // Current page is plain text | 현재 페이지는 링크가 아님
        assertTrue(html.contains("<strong title=\"2 페이지(현재 페이지)\">2</strong>"), html);
        assertTrue(html.contains("title=\"3 페이지 이동\">3</a>"), html);
    }

    @Test
    void renderRejectsScriptAsFunctionName() {
        for (var bad : new String[] { "alert(1);f", "f()", "a b", "", "1fn" }) {
            assertThrows(IllegalArgumentException.class, () -> tag(bad, 1, 30).renderPagination(), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> tag(null, 1, 30).renderPagination());
    }

    @Test
    void orderByKeepsOnlyIdentifiers() {
        var vo = new S2SearchVO();
        vo.setOrderBy("reg_dt desc, (select 1) asc, name, a.b ASC, x;drop table t");
        assertEquals(List.of(Map.of("column", "REG_DT", "sort", "DESC"), Map.of("column", "NAME", "sort", "ASC"),
                Map.of("column", "A.B", "sort", "ASC")), vo.getOrderByList());
        assertEquals(List.of(Map.of("column", "NAME", "sort", "ASC")), vo.getOrderByList(List.of("name")));

        vo.setOrderBy(" ");
        assertNull(vo.getOrderByList());
    }

    @Test
    void pagingIsBounded() {
        var vo = new S2SearchVO();
        vo.setPageNo(-5);
        vo.setPageUnit(0);
        assertEquals(1, vo.getPageNo());
        assertEquals(1, vo.getPageUnit());
        assertEquals(0, vo.getFirstIndex());

        vo.setPageNo(Integer.MAX_VALUE);
        vo.setPageUnit(1000);
        assertTrue(vo.getFirstIndex() > 0, "no overflow to a negative offset");
        assertTrue(vo.getLastIndex() > 0);
    }

    @Test
    void tldClassesAndFunctionsExist() throws Exception {
        var dir = Path.of("src/main/resources/META-INF/tld");
        var factory = DocumentBuilderFactory.newInstance();
        try (var files = Files.list(dir)) {
            for (var tld : (Iterable<Path>) files::iterator) {
                var doc = factory.newDocumentBuilder().parse(tld.toFile());
                var tags = doc.getElementsByTagName("tag-class");
                for (int i = 0; i < tags.getLength(); i++) {
                    Class.forName(tags.item(i).getTextContent().trim());
                }
                var functions = doc.getElementsByTagName("function");
                for (int i = 0; i < functions.getLength(); i++) {
                    var fn = (Element) functions.item(i);
                    var type = Class.forName(fn.getElementsByTagName("function-class").item(0).getTextContent().trim());
                    var signature = fn.getElementsByTagName("function-signature").item(0).getTextContent().trim();
                    var name = signature.substring(signature.indexOf(' ') + 1, signature.indexOf('(')).trim();
                    var params = signature.substring(signature.indexOf('(') + 1, signature.indexOf(')')).split(",");
                    var types = new ArrayList<Class<?>>();
                    for (var param : params) {
                        if (!param.isBlank()) {
                            types.add(switch (param.trim()) {
                                case "int" -> int.class;
                                case "long" -> long.class;
                                default -> Class.forName(param.trim());
                            });
                        }
                    }
                    var method = type.getMethod(name, types.toArray(Class<?>[]::new));
                    assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()), tld + " " + signature);
                }
            }
        }
    }
}
