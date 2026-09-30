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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.devers2.s2util.core.S2Cache;
import io.github.devers2.s2util.core.S2Cache.MethodHandleResolver;
import io.github.devers2.s2util.core.S2Cache.MethodHandleResolver.LookupType;
import io.github.devers2.s2util.core.S2Cache.MethodHandleResolver.MethodKey;
import io.github.devers2.s2util.core.S2Util;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2025. 02. 21.
 */
public class S2ServletUtil {

    private static final S2Logger logger = S2LogManager.getLogger(S2ServletUtil.class);

    private S2ServletUtil() {
    }

    /**
     * 요청을 받은 서버 이름(호스트)을 가져온다. 서블릿 컨테이너가 정한 {@code request.getServerName()}을 쓴다.
     * <p>
     * 리버스 프록시 뒤라면 컨테이너가 {@code X-Forwarded-Host}를 반영하도록 설정하는 것이 정석이다(Spring Boot:
     * {@code server.forward-headers-strategy=native}, Tomcat: {@code RemoteIpValve}). 직접 처리하려면
     * {@link #getRealServerName(HttpServletRequest, Set)}에 신뢰하는 프록시 주소를 넘긴다.
     * </p>
     *
     * @param request HttpServletRequest
     * @return 서버 이름
     */
    public static String getRealServerName(HttpServletRequest request) {
        return request.getServerName();
    }

    /**
     * 요청이 신뢰하는 프록시에서 왔을 때만 {@code X-Forwarded-Host}(없으면 {@code Host})를 쓴다. 그 외에는
     * {@code request.getServerName()}을 쓴다. 클라이언트가 보낸 헤더로 호스트를 위조하지 못하게 한다.
     *
     * @param request        HttpServletRequest
     * @param trustedProxies 신뢰하는 프록시 IP 주소 (예: {@code Set.of("10.0.0.5")})
     * @return 서버 이름 (포트 제외, IPv6 는 대괄호 제외)
     */
    public static String getRealServerName(HttpServletRequest request, Set<String> trustedProxies) {
        if (trustedProxies != null && trustedProxies.contains(request.getRemoteAddr())) {
            var forwarded = firstListValue(request.getHeader("X-Forwarded-Host"));
            if (forwarded == null) {
                forwarded = request.getHeader("Host");
            }
            if (forwarded != null && !forwarded.isBlank()) {
                return stripPort(forwarded.trim());
            }
        }
        return request.getServerName();
    }

    /** "host:port", "[::1]:8080", "::1" → host without port or brackets | 포트와 IPv6 대괄호 제거 */
    private static String stripPort(String host) {
        if (host.startsWith("[")) {
            var end = host.indexOf(']');
            return end > 0 ? host.substring(1, end) : host;
        }
        var colon = host.indexOf(':');
        // More than one colon without brackets is a bare IPv6 address | 대괄호 없이 콜론이 여럿이면 IPv6 주소 자체
        return colon > 0 && colon == host.lastIndexOf(':') ? host.substring(0, colon) : host;
    }

    private static String firstListValue(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        var first = header.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }

    /**
     * 클라이언트 IP 를 가져온다. 서블릿 컨테이너가 정한 {@code request.getRemoteAddr()}를 쓴다.
     * <p>
     * {@code X-Forwarded-For} 같은 헤더는 클라이언트가 마음대로 보낼 수 있으므로 기본으로 믿지 않는다. 리버스 프록시 뒤라면 컨테이너가 헤더를
     * 반영하도록 설정하거나(Spring Boot: {@code server.forward-headers-strategy=native}, Tomcat: {@code RemoteIpValve}),
     * {@link #getClientIp(HttpServletRequest, Set)}에 신뢰하는 프록시 주소를 넘긴다.
     * </p>
     *
     * @param request HttpServletRequest
     * @return 클라이언트 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /**
     * 신뢰하는 프록시를 거친 요청의 실제 클라이언트 IP 를 가져온다.
     * <p>
     * 직접 연결한 주소({@code getRemoteAddr()})가 신뢰하는 프록시일 때만 {@code X-Forwarded-For}를 오른쪽(가장 가까운 프록시)부터 읽어,
     * 신뢰하는 프록시가 아닌 첫 주소를 돌려준다. 클라이언트가 헤더 앞쪽에 넣은 가짜 주소는 쓰이지 않는다.
     * </p>
     *
     * @param request        HttpServletRequest
     * @param trustedProxies 신뢰하는 프록시 IP 주소 (예: {@code Set.of("10.0.0.5", "10.0.0.6")})
     * @return 클라이언트 IP
     */
    public static String getClientIp(HttpServletRequest request, Set<String> trustedProxies) {
        var remoteAddr = request.getRemoteAddr();
        if (trustedProxies == null || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }
        var forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        var hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            var hop = hops[i].trim();
            if (!hop.isEmpty() && !trustedProxies.contains(hop)) {
                return hop;
            }
        }
        return remoteAddr;
    }

    /**
     * Referer 로 이전 페이지의 경로(컨텍스트 경로 이후 + 쿼리)를 가져온다. 같은 서버(스킴·호스트·포트)의 같은 컨텍스트에서 온 경우에만 돌려준다.
     * <p>
     * 다른 사이트의 Referer 를 돌려주면 이 값으로 리다이렉트할 때 외부 사이트로 이동하는 오픈 리다이렉트가 되므로 빈 문자열을 돌려준다.
     * </p>
     *
     * @param request HttpServletRequest 객체
     * @return 이전 경로 (예: {@code /board/list?page=2}), 없거나 외부·다른 컨텍스트면 빈 문자열
     */
    public static String getPrevServletPath(HttpServletRequest request) {
        var referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "";
        }
        URI uri;
        try {
            uri = new URI(referer.trim());
        } catch (URISyntaxException e) {
            return "";
        }
        if (uri.getScheme() == null || uri.getHost() == null || uri.getRawPath() == null
                || !uri.getScheme().equalsIgnoreCase(request.getScheme())
                || !stripPort(uri.getHost()).equalsIgnoreCase(stripPort(request.getServerName()))
                || effectivePort(uri.getPort(), uri.getScheme()) != request.getServerPort()) {
            return "";
        }
        var contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        var path = uri.getRawPath();
        if (!path.startsWith(contextPath + "/")) {
            return "";
        }
        var result = path.substring(contextPath.length());
        return uri.getRawQuery() != null ? result + "?" + uri.getRawQuery() : result;
    }

    private static int effectivePort(int port, String scheme) {
        if (port != -1) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    /**
     * request의 모든 파라미터를 Query String으로 변환한다.
     * <p>
     * 이 메서드는 하나의 키에 여러 값이 있는 파라미터{@code(e.g., `?a=1&a=2`)}를 올바르게 처리하며,
     * 모든 키와 값을 URL-safe하게 인코딩한다.
     *
     * @param request HttpServletRequest 객체
     * @return 모든 파라미터를 포함하는 URL-encoded 쿼리 문자열. 파라미터가 없으면 빈 문자열을 반환한다.
     */
    public static String parameterToQueryString(HttpServletRequest request) {
        if (request == null || request.getParameterMap().isEmpty()) {
            return "";
        }

        var queryStringBuilder = new StringJoiner("&");
        var characterEncoding = request.getCharacterEncoding();
        if (characterEncoding == null || characterEncoding.isBlank()) {
            characterEncoding = StandardCharsets.UTF_8.name();
        }

        try {
            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                var key = entry.getKey();
                var values = entry.getValue();

                if (values == null) {
                    continue;
                }

                var encodedKey = URLEncoder.encode(key, characterEncoding);
                for (var value : values) {
                    // 값이 null인 경우는 건너뛰고, 빈 문자열은 "key=" 형태로 인코딩
                    if (value != null) {
                        var encodedValue = URLEncoder.encode(value, characterEncoding);
                        queryStringBuilder.add(encodedKey + "=" + encodedValue);
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            // 예외 발생 시 안전하게 빈 문자열 반환 (StandardCharsets.UTF_8은 항상 지원되므로 이 예외는 거의 발생하지 않는다.)
            logger.error("Failed to encode query string parameters with encoding: {}", characterEncoding, e);
            return "";
        }

        return queryStringBuilder.toString();
    }

    /**
     * HTTP 요청 헤더를 Map으로 변환한다.
     *
     * @param request HttpServletRequest 객체. 헤더 정보를 포함
     * @return 헤더 이름과 값을 포함하는 Map. 값은 String 또는 List<String> 타입
     * @details
     *          <dl>
     *          <dd>단일 값: String</dd>
     *          <dd>다중 값: List<String></dd>
     *          <dd>빈 헤더 값: 빈 문자열("")</dd>
     *          </dl>
     */
    public static Map<String, Object> convertHeadersToMap(HttpServletRequest request) {
        return convertHeadersToMap(request, true);
    }

    /**
     * HTTP 요청 헤더를 Map으로 변환한다.
     *
     * @param request         HttpServletRequest 객체. 헤더 정보를 포함
     * @param useImmutableMap true: 수정 불가능한 Map, false: 수정 가능한 Map
     * @return 헤더 이름과 값을 포함하는 Map. 값은 String 또는 List<String> 타입
     * @details
     *          <dl>
     *          <dd>단일 값: String</dd>
     *          <dd>다중 값: List<String></dd>
     *          <dd>빈 헤더 값: 빈 문자열("")</dd>
     *          </dl>
     */
    public static Map<String, Object> convertHeadersToMap(HttpServletRequest request, boolean useImmutableMap) {
        if (request == null) {
            return useImmutableMap ? Collections.emptyMap() : new HashMap<>();
        }

        var headersMap = new HashMap<String, Object>();
        for (var name : Collections.list(request.getHeaderNames())) {
            var valueList = Collections.list(request.getHeaders(name));
            headersMap.put(name, valueList.isEmpty() ? "" : valueList.size() == 1 ? valueList.get(0) : valueList);
        }

        return useImmutableMap ? Collections.unmodifiableMap(headersMap) : headersMap;
    }

    /**
     * HTTP 응답 헤더를 Map으로 변환한다.
     *
     * @param response HttpServletResponse 객체. 헤더 정보를 포함
     * @return 헤더 이름과 값을 포함하는 Map. 값은 String 또는 List<String> 타입
     * @details
     *          <dl>
     *          <dd>단일 값: String</dd>
     *          <dd>다중 값: List<String></dd>
     *          <dd>빈 헤더 값: 빈 문자열("")</dd>
     *          </dl>
     */
    public static Map<String, Object> convertHeadersToMap(HttpServletResponse response) {
        return convertHeadersToMap(response, true);
    }

    /**
     * HTTP 응답 헤더를 Map으로 변환한다.
     *
     * @param response        HttpServletResponse 객체. 헤더 정보를 포함
     * @param useImmutableMap true: 수정 불가능한 Map, false: 수정 가능한 Map
     * @return 헤더 이름과 값을 포함하는 Map. 값은 String 또는 List<String> 타입
     * @details
     *          <dl>
     *          <dd>단일 값: String</dd>
     *          <dd>다중 값: List<String></dd>
     *          <dd>빈 헤더 값: 빈 문자열("")</dd>
     *          </dl>
     */
    public static Map<String, Object> convertHeadersToMap(HttpServletResponse response, boolean useImmutableMap) {
        if (response == null) {
            return useImmutableMap ? Collections.emptyMap() : new HashMap<>();
        }

        var headersMap = new HashMap<String, Object>();
        for (var name : response.getHeaderNames()) {
            var valueList = new ArrayList<>(response.getHeaders(name));
            headersMap.put(name, valueList.isEmpty() ? "" : valueList.size() == 1 ? valueList.get(0) : valueList);
        }

        return useImmutableMap ? Collections.unmodifiableMap(headersMap) : headersMap;
    }

    /**
     * 헤더에서 파일 이름을 가져온다.
     *
     * @param request HttpServletRequest 객체
     * @return 파일 이름
     */
    public static String getFilenameFromHeader(HttpServletRequest request) {
        return getFilenameFromHeader(request.getHeader("Content-Disposition"));
    }

    /**
     * 헤더에서 파일 이름을 가져온다.
     *
     * @param response HttpServletResponse 객체
     * @return 파일 이름
     */
    public static String getFilenameFromHeader(HttpServletResponse response) {
        return getFilenameFromHeader(response.getHeader("Content-Disposition"));
    }

    private static String getFilenameFromHeader(String contentDisposition) {
        return S2FileUtil.parseContentDispositionFilename(contentDisposition);
    }

    /**
     * AJAX 요청인지 확인한다.
     *
     * @param request HttpServletRequest 객체
     * @return AJAX 요청 여부
     */
    public static boolean isAjaxRequest(HttpServletRequest request) {
        return isAjaxRequest(request, null);
    }

    /**
     * AJAX 요청인지 확인한다.
     *
     * @param request             HttpServletRequest 객체
     * @param ajaxRequestUrlRegex AJAX 요청 URL 정규식 문자열 ("^\/api\/.*", ".*\\.api$" 등)
     * @return AJAX 요청 여부
     */
    public static boolean isAjaxRequest(HttpServletRequest request, String ajaxRequestUrlRegex) {
        var requestedWith = request.getHeader("X-Requested-With");
        var s2Request = request.getHeader("X-S2-Request");
        var isAjax = "XMLHttpRequest".equals(requestedWith) ||
                "s2-ajax".equals(s2Request) ||
                "s2-fetch".equals(s2Request) ||
                "s2-async".equals(s2Request);
        if (!isAjax && ajaxRequestUrlRegex != null && !ajaxRequestUrlRegex.isBlank()) {
            var servletPath = request.getServletPath();
            isAjax = servletPath != null && servletPath.matches(ajaxRequestUrlRegex);

        }
        return isAjax;
    }

    /**
     * JSON 요청인지 확인한다.
     *
     * @param request HttpServletRequest 객체
     * @return JSON 요청 여부
     */
    public static boolean isJsonRequest(HttpServletRequest request) {
        var acceptHeader = request.getHeader("Accept");
        var contentType = request.getHeader("Content-Type");
        return (acceptHeader != null && acceptHeader.contains("application/json")) ||
                (contentType != null && contentType.contains("application/json"));
    }

    /**
     * 웹 애플리케이션의 루트 디렉토리에 해당하는 실제 파일 시스템 경로를 가져온다.
     *
     * @param request HttpServletRequest 객체
     * @return 어플리케이션 루트 경로
     * @throws IllegalStateException 파일 시스템 경로가 없는 배포(실행 가능한 jar 등)일 때
     */
    public static String getApplicationRootPath(HttpServletRequest request) {
        // request.getServletContext() does not create a session | 세션을 만들지 않음
        var servletContext = request.getServletContext();
        var rootPath = servletContext.getRealPath("/");
        if (S2Util.isEmpty(rootPath)) {
            var resource = servletContext.getClassLoader().getResource("");
            if (resource == null) {
                throw new IllegalStateException("애플리케이션 루트 경로를 알 수 없습니다 (실행 가능한 jar 등 파일 시스템에 풀리지 않은 배포).");
            }
            rootPath = resource.getPath();
        }
        return rootPath;
    }

    /**
     * 대상 객체(VO, Map, Request, List 등)로부터 특정 필드명에 해당하는 모든 값을 추출한다.
     *
     * @param <T>        VO 타입을 제한하기 위한 제네릭
     * @param object     데이터를 추출할 원본 객체 (Collection, Map, HttpServletRequest 등 포함)
     * @param voClass    VO 계열 클래스 타입 (해당 타입일 경우 Getter를 통해 하위 탐색)
     * @param fieldNames 필드명(VO) 또는 Key(Map) 가변인자
     * @return 추출된 값들의 목록 (순서 보장 안 됨)
     */
    public static <T> List<Object> getValueAll(Object object, Class<T> voClass, Object... fieldNames) {
        return S2ServletUtil.getValueAll(new ArrayList<>(), Collections.newSetFromMap(new IdentityHashMap<>()), object,
                voClass, fieldNames);
    }

    /**
     * 대상 객체(VO, Map, Request, List 등)로부터 특정 필드명에 해당하는 모든 값을 추출한다.
     *
     * @param <T>        VO 타입을 제한하기 위한 제네릭
     * @param values     결과 목록
     * @param visited    이미 방문한 컨테이너·VO (순환 참조 방지)
     * @param object     데이터를 추출할 원본 객체 (Collection, Map, HttpServletRequest 등 포함)
     * @param voClass    VO 계열 클래스 타입 (해당 타입일 경우 Getter를 통해 하위 탐색)
     * @param fieldNames 필드명(VO) 또는 Key(Map) 가변인자
     * @return 추출된 값들의 목록 (순서 보장 안 됨)
     */
    private static <T> List<Object> getValueAll(List<Object> values, Set<Object> visited, Object object,
            Class<T> voClass, Object... fieldNames) {
        if (object == null || fieldNames == null || fieldNames.length == 0) {
            return values;
        }
        // Skip objects already on the path (a VO referencing its parent, a Map containing itself) | 순환 참조는 한 번만 방문
        if (!(object instanceof CharSequence || object instanceof Number || object instanceof Boolean)
                && !visited.add(object)) {
            return values;
        }

        // 필드명 비교를 위한 리스트 변환 (루프 밖에서 1회 수행)
        List<Object> fieldNameList = Arrays.asList(fieldNames);

        // 1. Array/List 순회 처리
        if (object instanceof Object[] array) {
            for (var obj : array) {
                getValueAll(values, visited, obj, voClass, fieldNames);
            }
        } else if (object instanceof Collection<?> collection) {
            for (var obj : collection) {
                getValueAll(values, visited, obj, voClass, fieldNames);
            }
        }
        // 2. Map 처리 (하이패스 MAP_GET 활용)
        else if (object instanceof Map<?, ?> objMap) {
            // Resolver를 통해 사전 정의된 MAP_GET 핸들 획득
            var mapGetHandle = S2Cache.getMethodHandle(
                    MethodHandleResolver.MAP_GET_KEY,
                    LookupType.METHOD).orElse(null);

            for (var entry : objMap.entrySet()) {
                var key = entry.getKey();
                var val = entry.getValue();

                if (fieldNameList.contains(key)) {
                    // 핸들이 있으면 invoke, 없으면 직접 getValue 호출
                    if (mapGetHandle != null) {
                        try {
                            values.add(mapGetHandle.invoke(objMap, key));
                        } catch (Throwable e) {
                            values.add(val);
                        }
                    } else {
                        values.add(val);
                    }
                } else {
                    getValueAll(values, visited, val, voClass, fieldNames);
                }
            }
        }
        // 3. HttpServletRequest 처리
        else if (object instanceof HttpServletRequest request) {
            var parameterEnum = request.getParameterNames();
            while (parameterEnum.hasMoreElements()) {
                var parameterNm = parameterEnum.nextElement();
                var val = request.getParameter(parameterNm);

                if (fieldNameList.contains(parameterNm)) {
                    values.add(val);
                } else {
                    getValueAll(values, visited, val, voClass, fieldNames);
                }
            }
        }
        // 4. VO/DTO 객체 처리 (MethodHandleResolver 통합 활용)
        else if (voClass != null && voClass.isInstance(object)) {
            Class<?> clazz = object.getClass();
            // 캐싱된 필드 목록을 가져와서 루프 순회 (S2Cache.getFields 활용)
            var optionalFields = S2Cache.getFields(clazz);

            if (optionalFields.isPresent()) {
                var fields = optionalFields.get();

                for (var field : fields) {
                    var fieldName = field.getName();

                    // LookupType.BOTH를 사용하여 Getter 메서드 혹은 필드 직접 접근 핸들을 가져옴
                    var key = new MethodKey(clazz, fieldName, fieldName, new Class<?>[0]);
                    var handle = S2Cache.getMethodHandle(key, LookupType.BOTH).orElse(null);

                    if (handle != null) {
                        try {
                            Object fieldObj = handle.invoke(object);
                            if (fieldNameList.contains(fieldName)) {
                                values.add(fieldObj);
                            } else {
                                getValueAll(values, visited, fieldObj, voClass, fieldNames);
                            }
                        } catch (Throwable e) {
                            // 추출 실패 시 다음 필드로 진행함
                            continue;
                        }
                    }
                }
            }
        }

        return values;
    }

    /**
     * 모바일에서 접속중인지 확인한다.
     *
     * @param request HttpServletRequest
     * @return 모바일 여부
     */
    public static boolean isMobile(HttpServletRequest request) {
        return isMobile(request.getHeader("User-Agent"));
    }

    /**
     * 모바일에서 접속중인지 확인한다.
     *
     * @param userAgent HttpServletRequest.getHeader("User-Agent")
     * @return 모바일 여부
     */
    public static boolean isMobile(String userAgent) {
        var isMobile = false;
        if (userAgent != null && !userAgent.isBlank()) {
            var filter = "iphone|ipod|android|windows ce|blackberry|symbian|windows phone|webos|opera mini|opera mobi|polaris|iemobile|lgtelecom|nokia|sonyericsson|lg|samsung";
            var filters = filter.split("\\|");

            for (var f : filters) {
                if (userAgent.toLowerCase().contains(f)) {
                    isMobile = true;
                    break;
                }
            }
        }
        return isMobile;
    }

    /**
     * 해당 응답 객체의 HTTP 상태 코드가 성공(2xx) 범위인지 확인한다.
     * <dl>
     * <dt>Spring 인터셉터</dt>
     * <dd>afterCompletion 메서드 내부</dd>
     * </dl>
     * <dl>
     * <dt>Servlet Filter</dt>
     * <dd>doFilter 메서드 내부에서 응답 체인(chain.doFilter(...))이 반환된 직후</dd>
     * </dl>
     * <dl>
     * <dl>
     * <dt>리버스 프록시 (OpenResty 등)</dt>
     * <dd>header_filter_by_lua_block 또는 log_by_lua_block 단계</dd>
     * </dl>
     * <dt>API 클라이언트</dt>
     * <dd>HTTP 요청을 보내고 응답 객체를 받은 직후</dd>
     * </dl>
     *
     * @param response HttpServletResponse 객체
     * @return 성공 여부
     */
    public static boolean isResponseSuccess(HttpServletResponse response) {
        if (response == null) {
            return false;
        }
        return isResponseSuccess(response.getStatus());
    }

    /**
     * 해당 HTTP 상태 코드가 성공(2xx) 범위인지 확인한다.
     * <dl>
     * <dt>Spring 인터셉터</dt>
     * <dd>afterCompletion 메서드 내부</dd>
     * </dl>
     * <dl>
     * <dt>Servlet Filter</dt>
     * <dd>doFilter 메서드 내부에서 응답 체인(chain.doFilter(...))이 반환된 직후</dd>
     * </dl>
     * <dl>
     * <dl>
     * <dt>리버스 프록시 (OpenResty 등)</dt>
     * <dd>header_filter_by_lua_block 또는 log_by_lua_block 단계</dd>
     * </dl>
     * <dt>API 클라이언트</dt>
     * <dd>HTTP 요청을 보내고 응답 객체를 받은 직후</dd>
     * </dl>
     *
     * @param statusCode HTTP 상태 코드 (예: 200, 404, 500)
     * @return 성공 여부
     */
    public static boolean isResponseSuccess(Integer statusCode) {
        return statusCode != null && (statusCode >= 200 && statusCode < 300);
    }

}
