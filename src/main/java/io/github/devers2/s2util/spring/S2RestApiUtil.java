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
package io.github.devers2.s2util.spring;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * s2's utilities
 * REST API 호출 및 관련 유틸리티 기능을 제공하는 클래스.
 * HTTP 요청을 처리하고, {@link InputStreamResource}를 생성하는 메서드를 포함한다.
 *
 * @author devers2
 * @version 1.0
 * @since 2025. 01. 09.
 */
@SuppressWarnings("null")
public class S2RestApiUtil {

    private static final S2Logger logger = S2LogManager.getLogger(S2RestApiUtil.class);

    private static final int DEFAULT_TIMEOUT = 30000;
    private static final RestTemplate defaultRestTemplate;

    static {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(DEFAULT_TIMEOUT);
        factory.setReadTimeout(DEFAULT_TIMEOUT);
        defaultRestTemplate = new RestTemplate(factory);
        defaultRestTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    /**
     * 지정된 URL로 REST API를 호출하여 응답을 문자열로 반환한다.
     * 기본 타임아웃(30,000ms)을 사용하며, HTTP 메서드와 매개변수를 받아 요청을 처리한다.
     *
     * @param url    호출할 REST API의 URL. (필수)
     * @param method 사용할 HTTP 메서드. {@link HttpMethod#POST}, {@link HttpMethod#GET}만 지원한다. (필수)
     * @param params 요청에 포함할 매개변수로, 키-값 쌍의 배열. (선택)
     * @return API 호출 결과로 반환된 문자열 응답. 응답 본문이 없으면 null을 반환.
     * @throws IllegalArgumentException POST/GET 이외의 HTTP 메서드를 전달한 경우
     *
     *         <pre>{@code
     * String result = S2RestApiUtil.callApi("request.api", HttpMethod.POST,
     *     Map.entry("param1", value1),
     *     Map.entry("param2", value2),
     *     Map.entry("files", S2RestApiUtil.createInputStreamResource(fileInputStream, "file1.text", 1213)),
     *     Map.entry("files", S2RestApiUtil.createInputStreamResource(fileInputStream, "file2.pdf", 3121))
     * );
     * }</pre>
     */
    @SafeVarargs
    public static String callApi(String url, HttpMethod method, Map.Entry<String, Object>... params) {
        return S2RestApiUtil.callApi(url, method, null, params);
    }

    /**
     * 지정된 URL로 REST API를 호출하여 응답을 문자열로 반환한다.
     * 사용자 지정 타임아웃을 설정할 수 있으며, HTTP 메서드와 매개변수를 받아 요청을 처리한다.
     *
     * @param url     호출할 REST API의 URL. (필수)
     * @param method  사용할 HTTP 메서드. {@link HttpMethod#POST}, {@link HttpMethod#GET}만 지원한다. (필수)
     * @param timeout 연결 및 읽기 타임아웃(밀리초 단위). null인 경우 기본값 30,000ms 사용. (선택)
     * @param params  요청에 포함할 매개변수로, 키-값 쌍의 배열. (선택)
     * @return API 호출 결과로 반환된 문자열 응답. 응답 본문이 없으면 null을 반환.
     * @throws IllegalArgumentException POST/GET 이외의 HTTP 메서드를 전달한 경우
     * @see #callApi(String, HttpMethod, Integer, HttpHeaders, Map.Entry...)
     * @apiNote
     *
     *          <pre>{@code
     * String result = S2RestApiUtil.callApi("request.api", HttpMethod.POST, 10000,
     *     Map.entry("param1", value1),
     *     Map.entry("param2", value2),
     *     Map.entry("files", S2RestApiUtil.createInputStreamResource(fileInputStream, "file1.text", 1213)),
     *     Map.entry("files", S2RestApiUtil.createInputStreamResource(fileInputStream, "file2.pdf", 3121))
     * );
     * }</pre>
     */
    @SafeVarargs
    public static String callApi(String url, HttpMethod method, Integer timeout, Map.Entry<String, Object>... params) {
        return callApi(url, method, timeout, null, params);
    }

    /**
     * 요청 헤더를 지정하여 REST API 를 호출하고 응답 본문을 그대로 문자열로 반환한다.
     * <ul>
     * <li>POST: 파라미터에 파일({@link Resource}, {@code byte[]})이 있으면 {@code multipart/form-data}, 없으면
     * {@code application/x-www-form-urlencoded}로 보낸다.</li>
     * <li>GET: 파라미터를 쿼리 문자열로 붙인다.</li>
     * <li>응답 본문은 가공하지 않는다(JSON 의 유니코드 이스케이프도 풀지 않으므로 JSON 이 깨지지 않음).</li>
     * </ul>
     *
     * @param url     호출할 REST API의 URL. (필수)
     * @param method  사용할 HTTP 메서드. {@link HttpMethod#POST}, {@link HttpMethod#GET}만 지원한다. (필수)
     * @param timeout 연결 및 읽기 타임아웃(밀리초 단위). null인 경우 기본값 30,000ms 사용. (선택)
     * @param headers 추가할 요청 헤더 (예: Authorization). null 허용 (선택)
     * @param params  요청에 포함할 매개변수로, 키-값 쌍의 배열. (선택)
     * @return 응답 본문. 본문이 없으면 null
     * @throws IllegalArgumentException POST/GET 이외의 HTTP 메서드를 전달한 경우
     * @throws org.springframework.web.client.RestClientException 요청 실패 또는 4xx/5xx 응답
     *
     *         <pre>{@code
     * var headers = new HttpHeaders();
     * headers.setBearerAuth(token);
     * String json = S2RestApiUtil.callApi("https://api.example.com/users", HttpMethod.GET, null, headers,
     *         Map.entry("page", 1));
     * }</pre>
     */
    @SafeVarargs
    public static String callApi(String url, HttpMethod method, Integer timeout, HttpHeaders headers,
            Map.Entry<String, Object>... params) {
        int vTimeout = timeout != null && timeout > 0 ? timeout : DEFAULT_TIMEOUT;

        RestTemplate restTemplate;
        if (vTimeout == DEFAULT_TIMEOUT) {
            restTemplate = defaultRestTemplate;
        } else {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(vTimeout);
            requestFactory.setReadTimeout(vTimeout);
            restTemplate = new RestTemplate(requestFactory);
            restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        }

        var requestHeaders = new HttpHeaders();
        requestHeaders.setAcceptCharset(Collections.singletonList(StandardCharsets.UTF_8));
        if (headers != null) {
            requestHeaders.putAll(headers);
        }

        String result = null;
        ResponseEntity<String> responseEntity = null;

        if (method == HttpMethod.POST) {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            var multipart = false;
            if (params != null) {
                for (Map.Entry<String, Object> param : params) {
                    if (param != null && param.getKey() != null && param.getValue() != null) {
                        multipart |= param.getValue() instanceof Resource || param.getValue() instanceof byte[];
                        body.add(param.getKey(), param.getValue());
                    }
                }
            }
            if (!multipart) {
                // A form body needs string values | 폼 본문은 문자열 값이어야 함
                body.replaceAll((key, values) -> new ArrayList<>(values.stream().map(String::valueOf).toList()));
            }
            if (requestHeaders.getContentType() == null) {
                requestHeaders.setContentType(multipart ? MediaType.MULTIPART_FORM_DATA : MediaType.APPLICATION_FORM_URLENCODED);
            }
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, requestHeaders);
            responseEntity = restTemplate.postForEntity(url, requestEntity, String.class);
        } else if (method == HttpMethod.GET) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
            if (params != null) {
                for (Map.Entry<String, Object> param : params) {
                    if (param != null && param.getKey() != null && param.getValue() != null) {
                        builder.queryParam(param.getKey(), param.getValue());
                    }
                }
            }

            // URI 객체를 직접 사용하여 이중 인코딩 방지
            java.net.URI uri = builder.build().encode().toUri();
            HttpEntity<Void> requestEntity = new HttpEntity<>(requestHeaders);
            responseEntity = restTemplate.exchange(uri, HttpMethod.GET, requestEntity, String.class);
        } else {
            // POST/GET 외 메서드는 지원하지 않는다. 조용히 null을 반환하면 "서버 응답이 없어서 null"인지
            // "애초에 지원 안 하는 메서드라 아무것도 안 보냈는지" 구분이 안 돼 디버깅이 어려워지므로 즉시 실패시킨다.
            throw new IllegalArgumentException(
                    "Unsupported HTTP method: " + method + " (only POST and GET are supported)");
        }

        if (responseEntity != null && responseEntity.getBody() != null) {
            // Returned as is: decoding unicode escapes here would break JSON strings containing quotes | 그대로 반환 (유니코드 이스케이프를 풀면 JSON 이 깨짐)
            result = responseEntity.getBody();
            logger.debug("Call URL: {}, Method: {}, Status: {}", url, method, responseEntity.getStatusCode());
        }

        return result;
    }

    /**
     * 주어진 {@link InputStream}, 파일명, 콘텐츠 길이를 이용하여 {@link InputStreamResource}를 생성.
     * 생성된 {@code InputStreamResource}는 {@code getFilename()}과 {@code contentLength()} 메서드를 오버라이드하여 파일명과 콘텐츠 길이를 명시적으로 반환.
     *
     * @param inputStream   전송할 데이터의 {@link InputStream}. (필수)
     * @param filename      다운로드될 확장자를 포함한 파일 이름. (필수)
     * @param contentLength 전송할 데이터의 총 길이 (바이트 단위). (필수, 0 이상)
     *                      ※ contentLength 가 없는 경우 InputStream 을 두번읽으면서 오류나 날수 있어 반드시 넣어야 한다.
     * @return 파일명과 콘텐츠 길이가 설정된 새로운 {@link InputStreamResource} 객체.
     * @throws IllegalArgumentException {@code inputStream} 또는 {@code filename}이 null이거나 {@code contentLength}가 음수인 경우.
     * @details
     *          <dl>
     *          <dd>InputStreamResource 를 사용할 때 Content-Length를 미리 계산해 설정하면 Spring 이 스트림을 미리 읽지 않는다.</dd>
     *          <dd>※ 즉 Content-Length 명시하지 않으면 InputStreamResource 를 2번 읽으면서 java.lang.IllegalStateException 예외가 발생한다.</dd>
     *          </dl>
     */
    public static InputStreamResource createInputStreamResource(InputStream inputStream, String filename,
            long contentLength) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream must not be null");
        }
        if (filename == null) {
            throw new IllegalArgumentException("Filename must not be null");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("Content length must not be negative");
        }
        return new InputStreamResource(inputStream) {

            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long contentLength() {
                return contentLength;
            }
        };
    }

}
