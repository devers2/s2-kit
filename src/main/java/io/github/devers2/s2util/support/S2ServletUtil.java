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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * 실제 서버 이름을 가져온다
     *
     * @param request HttpServletRequest
     * @return 실제 서버 이름
     */
    public static String getRealServerName(HttpServletRequest request) {
        var serverName = request.getHeader("X-Forwarded-Host");
        if (serverName == null || serverName.isBlank()) {
            serverName = request.getHeader("Host");
        }

        if (serverName != null && !serverName.isBlank()) {
            // X-Forwarded-Host / Host 는 클라이언트나 프록시가 조작할 수 있으므로 신뢰성을 점검하는 로직 필요할 수 있음
            serverName = serverName.split(":")[0]; // 포트 제거
        } else {
            // request.getServerName 은 서블릿 컨테이너가 결정한 서버 이름으로 클라이언트 조작 불가능
            serverName = request.getServerName();
        }

        return serverName;
    }

    /**
     * 실제 요청 IP를 가져온다
     *
     * @param request HttpServletRequest
     * @return 클라이언트 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        var clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp != null && !clientIp.isBlank() && clientIp.contains(",")) {
            // X-Forwarded-For 헤더에 여러 IP가 있을 경우 첫 번째 IP가 클라이언트의 실제 IP
            clientIp = clientIp.split(",")[0].trim();
        }
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getHeader("Proxy-Client-IP");
        }
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getHeader("WL-Proxy-Client-IP");
        }
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp;
    }

    /**
     * REFERER 를 활용하여 이전 페이지의 URI 를 가져온다. (링크를 통해 들어온 경우에만 확인 가능)
     *
     * @param request HttpServletRequest 객체
     * @return URI
     */
    public static String getPrevServletPath(HttpServletRequest request) {
        var prevServletPath = "";
        try {
            var referer = request.getHeader("REFERER");
            if (referer != null && !referer.isBlank()) {
                prevServletPath = referer
                        .replace(request.getRequestURL().toString().replace(request.getServletPath(), ""), "");
            }
        } catch (Exception e) {
            logger.error("getPrevServletPath failed", e);
        }
        return prevServletPath;
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
     */
    public static String getApplicationRootPath(HttpServletRequest request) {
        var rootPath = request.getSession().getServletContext().getRealPath("/");
        if (S2Util.isEmpty(rootPath)) {
            rootPath = Objects.requireNonNull(request.getSession().getServletContext().getClassLoader().getResource(""))
                    .getPath();
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
        return S2ServletUtil.getValueAll(new ArrayList<>(), object, voClass, fieldNames);
    }

    /**
     * 대상 객체(VO, Map, Request, List 등)로부터 특정 필드명에 해당하는 모든 값을 추출한다.
     *
     * @param <T>        VO 타입을 제한하기 위한 제네릭
     * @param values     결과 목록
     * @param object     데이터를 추출할 원본 객체 (Collection, Map, HttpServletRequest 등 포함)
     * @param voClass    VO 계열 클래스 타입 (해당 타입일 경우 Getter를 통해 하위 탐색)
     * @param fieldNames 필드명(VO) 또는 Key(Map) 가변인자
     * @return 추출된 값들의 목록 (순서 보장 안 됨)
     */
    private static <T> List<Object> getValueAll(List<Object> values, Object object, Class<T> voClass,
            Object... fieldNames) {
        if (object == null || fieldNames == null || fieldNames.length == 0) {
            return values;
        }

        // 필드명 비교를 위한 리스트 변환 (루프 밖에서 1회 수행)
        List<Object> fieldNameList = Arrays.asList(fieldNames);

        // 1. Array/List 순회 처리
        if (object instanceof Object[] array) {
            for (var obj : array) {
                getValueAll(values, obj, voClass, fieldNames);
            }
        } else if (object instanceof List<?> list) {
            for (var obj : list) {
                getValueAll(values, obj, voClass, fieldNames);
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
                    getValueAll(values, val, voClass, fieldNames);
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
                    getValueAll(values, val, voClass, fieldNames);
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
                                getValueAll(values, fieldObj, voClass, fieldNames);
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
