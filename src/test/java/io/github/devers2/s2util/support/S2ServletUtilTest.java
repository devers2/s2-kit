package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Forwarded headers are trusted only from known proxies, Referer only from the same origin, and value extraction
 * survives cycles.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 전달 헤더는 신뢰하는 프록시에서만, Referer 는 같은 출처에서만 쓰는지, 순환 참조에서도 값 추출이 끝나는지 확인합니다.
 */
class S2ServletUtilTest {

    /** A request stub answering only what these tests call | 이 시험에서 부르는 것만 답하는 요청 스텁 */
    private static HttpServletRequest request(String remoteAddr, Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(S2ServletUtilTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getRemoteAddr" -> remoteAddr;
                    case "getHeader" -> headers.get((String) args[0]);
                    case "getServerName" -> "app.example.com";
                    case "getServerPort" -> 443;
                    case "getScheme" -> "https";
                    case "getContextPath" -> "/ctx";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void clientIpIgnoresForwardedHeadersFromUntrustedClients() {
        var spoofed = request("203.0.113.9", Map.of("X-Forwarded-For", "1.2.3.4"));
        assertEquals("203.0.113.9", S2ServletUtil.getClientIp(spoofed));
        assertEquals("203.0.113.9", S2ServletUtil.getClientIp(spoofed, Set.of("10.0.0.5")));

        // Behind a trusted proxy: the right-most untrusted hop, not the spoofable first one | 앞쪽의 위조 가능한 값이 아닌 오른쪽 첫 비신뢰 주소
        var proxied = request("10.0.0.5", Map.of("X-Forwarded-For", "6.6.6.6, 198.51.100.7, 10.0.0.6"));
        assertEquals("198.51.100.7", S2ServletUtil.getClientIp(proxied, Set.of("10.0.0.5", "10.0.0.6")));
        assertEquals("10.0.0.5", S2ServletUtil.getClientIp(proxied));
    }

    @Test
    void serverNameUsesForwardedHostOnlyFromTrustedProxies() {
        var spoofed = request("203.0.113.9", Map.of("X-Forwarded-Host", "evil.example"));
        assertEquals("app.example.com", S2ServletUtil.getRealServerName(spoofed));
        assertEquals("app.example.com", S2ServletUtil.getRealServerName(spoofed, Set.of("10.0.0.5")));

        assertEquals("public.example",
                S2ServletUtil.getRealServerName(request("10.0.0.5", Map.of("X-Forwarded-Host", "public.example:8443")),
                        Set.of("10.0.0.5")));
        assertEquals("2001:db8::1",
                S2ServletUtil.getRealServerName(request("10.0.0.5", Map.of("Host", "[2001:db8::1]:8080")), Set.of("10.0.0.5")));
    }

    @Test
    void previousPathOnlyFromTheSameOriginAndContext() {
        assertEquals("/board/list?page=2", S2ServletUtil.getPrevServletPath(
                request("1.1.1.1", Map.of("Referer", "https://app.example.com/ctx/board/list?page=2"))));
        for (var foreign : List.of("https://evil.example/ctx/board", "http://app.example.com/ctx/board",
                "https://app.example.com:8443/ctx/board", "https://app.example.com/other/board", "//evil.example/x",
                "not a url")) {
            assertEquals("", S2ServletUtil.getPrevServletPath(request("1.1.1.1", Map.of("Referer", foreign))), foreign);
        }
        assertEquals("", S2ServletUtil.getPrevServletPath(request("1.1.1.1", Map.of())));
    }

    @Test
    void valueExtractionSurvivesCycles() {
        Map<String, Object> parent = new HashMap<>();
        List<Object> children = new ArrayList<>();
        Map<String, Object> child = new HashMap<>();
        child.put("id", "c1");
        child.put("parent", parent);
        children.add(child);
        parent.put("id", "p1");
        parent.put("children", children);

        var ids = S2ServletUtil.getValueAll(parent, null, "id");
        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of("p1", "c1")));
    }
}
