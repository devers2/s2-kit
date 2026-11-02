/**
 * S2 Kit Library
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
package io.github.devers2.s2kit.support;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.github.devers2.s2util.core.S2StringUtil;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 2.0
 * @since 2025. 05. 27.
 */
public class S2QueryStringUtil {

    private S2QueryStringUtil() {
    }

    /**
     * 키-값 쌍을 쿼리 문자열로 변환하는 메서드.
     *
     * @param baseString 기존 URL 또는 쿼리 문자열 {@code(예: "test.do", "test.do?wrong", "wrong&x=y")}
     * @param entries    추가할 키-값 쌍 (가변인자, 예: Map.entry("a", "1"), Map.entry("b", "2"))
     * @return 결합된 URL 및 쿼리 문자열
     * @throws IllegalArgumentException 기존 쿼리 문자열의 퍼센트 인코딩이 잘못되었을 때
     * @apiNote
     *
     *          <pre>{@code
     * queryStringFromEntries("", Map.entry("a", "1"), Map.entry("b", "2")) → "a=1&b=2"
     * queryStringFromEntries("test.do", Map.entry("a", "1"), Map.entry("b", "2")) → "test.do?a=1&b=2"
     * queryStringFromEntries("test.do?wrong", Map.entry("a", "1"), Map.entry("b", "2")) → "test.do?wrong=&a=1&b=2"
     * queryStringFromEntries("wrong&x=y", Map.entry("a", "1"), Map.entry("b", "2"), Map.entry("x", "z")) → "wrong=&x=z&a=1&b=2"
     * queryStringFromEntries("list?t=1&t=2#top", Map.entry("page", "3")) → "list?t=1&t=2&page=3#top"
     * }</pre>
     *
     * @details
     *          <dl>
     *          <dd>- 쿼리 문자열 앞에 URL 이 있다면 기존 URL 은 유지하고 이후에 추가한다. {@code #fragment}는 맨 뒤에 유지한다.</dd>
     *          <dd>- 파라미터 순서를 유지하며, 기존 쿼리의 반복 키({@code t=1&t=2})도 모두 유지한다.</dd>
     *          <dd>- entries 의 키가 기존 쿼리에 있으면 그 키의 값을 모두 대체한다(위치는 유지).</dd>
     *          <dd>- 기존 쿼리의 키와 값은 디코딩 후 다시 인코딩하므로 이중 인코딩되지 않는다.</dd>
     *          <dd>- 제어 문자는 제거한다.</dd>
     *          </dl>
     */
    @SafeVarargs
    public static String queryStringFromEntries(String baseString, Entry<String, String>... entries) {
        var url = "";
        var fragment = "";
        var existingQuery = "";
        Map<String, List<String>> queryParams = new LinkedHashMap<>();

        if (baseString != null && !baseString.isBlank()) {
            var sanitized = S2StringUtil.sanitizeInput(baseString);
            var hashIndex = sanitized.indexOf('#');
            if (hashIndex > -1) {
                fragment = sanitized.substring(hashIndex);
                sanitized = sanitized.substring(0, hashIndex);
            }
            var queryIndex = sanitized.indexOf('?');
            if (queryIndex > -1) {
                url = sanitized.substring(0, queryIndex);
                existingQuery = sanitized.substring(queryIndex + 1);
            } else if (sanitized.contains("=") || sanitized.contains("&")) {
                existingQuery = sanitized;
            } else {
                url = sanitized;
            }
        }
        parseQueryString(existingQuery, queryParams);

        if (entries != null) {
            for (Entry<String, String> entry : entries) {
                if (entry != null && entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                    var values = new ArrayList<String>();
                    values.add(S2StringUtil.sanitizeInput(entry.getValue()));
                    queryParams.put(S2StringUtil.sanitizeInput(entry.getKey()), values);
                }
            }
        }

        var query = new StringBuilder();
        for (var param : queryParams.entrySet()) {
            for (var value : param.getValue()) {
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }

        return url + (url.isBlank() || query.length() == 0 ? "" : "?") + query + fragment;
    }

    /**
     * 쿼리 문자열을 파싱하여 Map 에 순서대로 저장한다. 키와 값을 모두 디코딩한다.
     */
    private static void parseQueryString(String queryString, Map<String, List<String>> queryParams) {
        for (var param : queryString.split("&")) {
            if (param.isBlank()) {
                continue;
            }
            var eqIndex = param.indexOf('=');
            var key = S2StringUtil.sanitizeInput(decode(eqIndex >= 0 ? param.substring(0, eqIndex) : param));
            var value = eqIndex >= 0 ? S2StringUtil.sanitizeInput(decode(param.substring(eqIndex + 1))) : "";
            if (!key.isBlank()) {
                queryParams.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("쿼리 문자열의 퍼센트 인코딩이 잘못되었습니다: " + value, e);
        }
    }

    /**
     * Query String에서 특정 파라미터의 첫 번째 값을 조회한다.
     *
     * @param queryString Query String 또는 URL {@code(예: "a=1&b=2", "http://example.com?a=1#top")}
     * @param key         조회할 파라미터 키 (디코딩된 키와 비교)
     * @return 파라미터 값 (키가 없거나 값이 없는 경우 빈 문자열, URL 디코딩 적용)
     * @throws IllegalArgumentException 퍼센트 인코딩이 잘못되었을 때
     */
    public static String getQueryStringParameter(String queryString, String key) {
        if (queryString == null || key == null || queryString.isBlank() || key.isBlank()) {
            return "";
        }
        var query = queryString;
        var hashIndex = query.indexOf('#');
        if (hashIndex >= 0) {
            query = query.substring(0, hashIndex);
        }
        var queryIndex = query.indexOf('?');
        if (queryIndex >= 0) {
            query = query.substring(queryIndex + 1);
        }
        for (var param : query.split("&")) {
            var eqIndex = param.indexOf('=');
            if (eqIndex >= 0 && key.equals(decode(param.substring(0, eqIndex)))) {
                return decode(param.substring(eqIndex + 1));
            }
        }
        return "";
    }

}
