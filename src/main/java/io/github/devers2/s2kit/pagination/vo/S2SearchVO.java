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
package io.github.devers2.s2kit.pagination.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.devers2.s2util.core.S2DateUtil;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;
import io.github.devers2.s2kit.support.vo.S2DefaultVO;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2020. 07. 08.
 */
public class S2SearchVO extends S2DefaultVO {

    private static final S2Logger logger = S2LogManager.getLogger(S2SearchVO.class);

    /** Identifier or alias.identifier only, so it can go into SQL as is | SQL 에 그대로 넣을 수 있도록 식별자 또는 별칭.식별자만 허용 */
    private static final Pattern SORT_COLUMN = Pattern.compile("[A-Z_][A-Z0-9_]*(\\.[A-Z_][A-Z0-9_]*)?");

    /** 페이지당 레코드 개수(recordCountPerPage) */
    private int pageUnit = 10;

    /** 페이지 목록에 게시되는 페이지 건수 */
    private int pageSize = 10;

    /** 현재 페이지 번호 */
    private int pageNo = 1;

    /** ORDER BY (A DESC, B ASC ...) */
    private String orderBy;

    /** 페이지 처리 여부 */
    private String pagingYn;

    /** 팝업 layerIndex */
    private String layerIndex;

    /** 민감정보 표시 여부 */
    private String showSnstvInfoYn;

    /** 검색 조건 */
    private String searchCondition = "";

    /** 검색 단어 */
    private String searchKeyword = "";

    /** 검색 시작 일시 문자열 */
    private String searchStartLocalDateString = "";

    /** 검색 시작 일시 */
    private LocalDate searchStartLocalDate;

    /** 검색 시작 일시 문자열 (TIMESTAMP WITHOUT TIME ZONE, 타임존 제외) */
    private String searchStartLocalDateTimeString;

    /** 검색 시작 일시 (TIMESTAMP WITHOUT TIME ZONE, 타임존 제외) */
    private LocalDateTime searchStartLocalDateTime;

    /**
     * 검색 시작 일시 문자열 (TIMESTAMP WITH TIME ZONE, 타임존 필수)
     *
     * 2025-10-12T10:00:00+09:00 (ISO 8601 국제표준, 'T' 또는 공백으로 날짜와 시간을 구분)
     * 2025-10-12 10:00:00 GMT+9
     */
    private String searchStartOffsetDateTimeString;

    /**
     * 검색 시작 일시 (TIMESTAMP WITH TIME ZONE, 타임존 필수)
     *
     * <h3>허용 형식</h3>
     * <ul>
     * <li><strong>ISO-8601 표준 (자동 파싱)</strong>:
     * <ul>
     * <li>{@code 2025-10-12T10:00:00+09:00} ('T' 구분, 기본 형식)</li>
     * <li>{@code 2025-10-12T10:00:00Z} (UTC)</li>
     * <li>{@code 2025-10-12 10:00:00+09:00} (공백 허용, 확장 형식)</li>
     * </ul>
     * </li>
     * <li><strong>커스텀 형식 (Spring 파싱)</strong>:
     * <ul>
     * <li>{@code 2025-10-12 10:00:00 +0900}</li>
     * <li>{@code 2025-10-12 10:00:00 GMT+9}</li>
     * </ul>
     * </li>
     * </ul>
     *
     * <h3>Spring 컨트롤러 파싱</h3>
     *
     * <pre>{@code
     * // 1. ISO-8601 자동 파싱 (추천!, @DateTimeFormat 생략 가능)
     * &#64;RequestParam
     * &#64;DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
     * OffsetDateTime searchEndOffsetDateTime
     *
     * // 2. 커스텀 패턴 (필요 시)
     * &#64;RequestParam
     * &#64;DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss Z")
     * OffsetDateTime searchEndOffsetDateTime
     * }</pre>
     *
     * <p>
     * <strong>자동 파싱 조건</strong>: Spring Boot 2.6+ 기준, {@code java.time} 타입은 ISO-8601 문자열 자동 인식.
     * </p>
     *
     * <p>
     * <strong>주의</strong>: 타임존 오프셋이 없으면 {@code DateTimeParseException} 발생.
     * </p>
     */
    private OffsetDateTime searchStartOffsetDateTime;

    /** 검색 종료 일자 문자열 */
    private String searchEndLocalDateString = "";

    /** 검색 종료 일자 */
    private LocalDate searchEndLocalDate;

    /** 검색 종료 일시 문자열 (TIMESTAMP WITHOUT TIME ZONE, 타임존 제외) */
    private String searchEndLocalDateTimeString;

    /** 검색 종료 일시 (TIMESTAMP WITHOUT TIME ZONE, 타임존 제외) */
    private LocalDateTime searchEndLocalDateTime;

    /**
     * 검색 종료 일시 문자열 (TIMESTAMP WITH TIME ZONE, 타임존 필수)
     *
     * 2025-10-12T10:00:00+09:00 (ISO 8601 국제표준, 'T' 또는 공백으로 날짜와 시간을 구분)
     * 2025-10-12 10:00:00 GMT+9
     */
    private String searchEndOffsetDateTimeString;

    /**
     * 검색 종료 일시 (TIMESTAMP WITH TIME ZONE, 타임존 필수)
     *
     * <h3>허용 형식</h3>
     * <ul>
     * <li><strong>ISO-8601 표준 (자동 파싱)</strong>:
     * <ul>
     * <li>{@code 2025-10-12T10:00:00+09:00} ('T' 구분, 기본 형식)</li>
     * <li>{@code 2025-10-12T10:00:00Z} (UTC)</li>
     * <li>{@code 2025-10-12 10:00:00+09:00} (공백 허용, 확장 형식)</li>
     * </ul>
     * </li>
     * <li><strong>커스텀 형식 (Spring 파싱)</strong>:
     * <ul>
     * <li>{@code 2025-10-12 10:00:00 +0900}</li>
     * <li>{@code 2025-10-12 10:00:00 GMT+9}</li>
     * </ul>
     * </li>
     * </ul>
     *
     * <h3>Spring 컨트롤러 파싱</h3>
     *
     * <pre>{@code
     * // 1. ISO-8601 자동 파싱 (추천!, @DateTimeFormat 생략 가능)
     * &#64;RequestParam
     * &#64;DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
     * OffsetDateTime searchEndOffsetDateTime
     *
     * // 2. 커스텀 패턴 (필요 시)
     * &#64;RequestParam
     * &#64;DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss Z")
     * OffsetDateTime searchEndOffsetDateTime
     * }</pre>
     *
     * <p>
     * <strong>자동 파싱 조건</strong>: Spring Boot 2.6+ 기준, {@code java.time} 타입은 ISO-8601 문자열 자동 인식.
     * </p>
     *
     * <p>
     * <strong>주의</strong>: 타임존 오프셋이 없으면 {@code DateTimeParseException} 발생.
     * </p>
     */
    private OffsetDateTime searchEndOffsetDateTime;

    /** 검색 사용 여부 */
    private String searchUseYn = "";

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.max(1, pageSize);
    }

    public int getPageUnit() {
        return pageUnit;
    }

    public void setPageUnit(int pageUnit) {
        this.pageUnit = Math.max(1, pageUnit);
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = Math.max(1, pageNo);
    }

    /** 현재 레코드 목록의 첫번째 인덱스 */
    public int getFirstIndex() {
        // Saturate instead of overflowing to a negative offset | 음수 오프셋으로 넘치지 않도록 최댓값에서 멈춤
        return (int) Math.min(Integer.MAX_VALUE - (long) this.getPageUnit(), (long) (this.getPageNo() - 1) * this.getPageUnit());
    }

    /** 현재 레코드 목록의 마지막 인덱스 */
    public int getLastIndex() {
        return this.getFirstIndex() + (this.getPageUnit() - 1);
    }

    /** 현재 레코드 목록의 첫번째 순번 */
    public int getFirstNo() {
        return this.getFirstIndex() + 1;
    }

    /** 현재 레코드 목록의 마지막 순번 */
    public int getLastNo() {
        return this.getLastIndex() + 1;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    /**
     * orderBy ("A_COLUMN DESC, B_COLUMN ASC ...") → [{column: "A_COLUMN", sort: "DESC"}, {column: "B_COLUMN", sort: "ASC"} ...]
     * <p>
     * 컬럼명은 식별자({@code COLUMN} 또는 {@code ALIAS.COLUMN}, 영문·숫자·밑줄)만 허용하고 그 외 항목은 버린다. MyBatis {@code ${column}}처럼
     * SQL 에 그대로 들어가므로, 가능하면 허용 컬럼을 지정하는 {@link #getOrderByList(Collection)}를 사용한다.
     * </p>
     *
     * @return 정렬 목록 (orderBy 가 비어 있으면 null)
     */
    public List<Map<String, String>> getOrderByList() {
        return getOrderByList(null);
    }

    /**
     * orderBy 를 정렬 목록으로 변환하되 {@code allowedColumns}에 있는 컬럼만 남긴다. (대소문자 무시)
     *
     * @param allowedColumns 허용 컬럼 (null: 식별자 형식만 검사)
     * @return 정렬 목록 (orderBy 가 비어 있으면 null)
     */
    public List<Map<String, String>> getOrderByList(Collection<String> allowedColumns) {
        if (orderBy == null || orderBy.isBlank()) {
            return null;
        }
        Set<String> allowed = null;
        if (allowedColumns != null) {
            allowed = new HashSet<>();
            for (var column : allowedColumns) {
                allowed.add(column.toUpperCase(Locale.ROOT));
            }
        }

        List<Map<String, String>> orderByList = new ArrayList<>();
        for (var orderByInfo : orderBy.trim().split(",")) {
            var orderByInfoArr = orderByInfo.trim().split("\\s+");
            var column = orderByInfoArr[0].trim().toUpperCase(Locale.ROOT);
            if (column.isEmpty()) {
                continue;
            }
            if (!SORT_COLUMN.matcher(column).matches() || (allowed != null && !allowed.contains(column))) {
                logger.warn("허용되지 않은 정렬 컬럼을 무시합니다: {}", orderByInfo.trim());
                continue;
            }
            var sort = orderByInfoArr.length > 1 && "DESC".equalsIgnoreCase(orderByInfoArr[1].trim()) ? "DESC" : "ASC";
            Map<String, String> orderByMap = new HashMap<>();
            orderByMap.put("column", column);
            orderByMap.put("sort", sort);
            orderByList.add(orderByMap);
        }
        return orderByList;
    }

    public String getPagingYn() {
        return pagingYn;
    }

    public void setPagingYn(String pagingYn) {
        this.pagingYn = pagingYn;
    }

    public String getLayerIndex() {
        return layerIndex;
    }

    public void setLayerIndex(String layerIndex) {
        this.layerIndex = layerIndex;
    }

    public String getShowSnstvInfoYn() {
        return showSnstvInfoYn;
    }

    public void setShowSnstvInfoYn(String showSnstvInfoYn) {
        this.showSnstvInfoYn = showSnstvInfoYn;
    }

    public String getSearchCondition() {
        return searchCondition;
    }

    public void setSearchCondition(String searchCondition) {
        this.searchCondition = searchCondition;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public void setSearchKeyword(String searchKeyword) {
        this.searchKeyword = searchKeyword;
    }

    public String getSearchStartLocalDateString() {
        return searchStartLocalDateString;
    }

    public void setSearchStartLocalDateString(String searchStartLocalDateString) {
        this.searchStartLocalDateString = searchStartLocalDateString;
    }

    public LocalDate getSearchStartLocalDate() {
        return searchStartLocalDate;
    }

    public void setSearchStartLocalDate(LocalDate searchStartLocalDate) {
        this.searchStartLocalDate = searchStartLocalDate;
    }

    /**
     * 날짜 문자열과 패턴을 받아 시작 일자(LocalDate) 객체로 변환한다.
     *
     * @param searchStartLocalDateString 시작 일자 문자열
     * @param pattern                    파싱에 사용할 형식 패턴 (DateTimeFormatter pattern, yyyy 년 MM 월 dd 일 E 요일)
     */
    public void setSearchStartLocalDate(String searchStartLocalDateString, String pattern) {
        this.searchStartLocalDate = S2DateUtil.parseToLocalDate(searchStartLocalDateString, pattern, false);
    }

    public String getSearchStartLocalDateTimeString() {
        return searchStartLocalDateTimeString;
    }

    public void setSearchStartLocalDateTimeString(String searchStartLocalDateTimeString) {
        this.searchStartLocalDateTimeString = searchStartLocalDateTimeString;
    }

    public LocalDateTime getSearchStartLocalDateTime() {
        return searchStartLocalDateTime;
    }

    public void setSearchStartLocalDateTime(LocalDateTime searchStartLocalDateTime) {
        this.searchStartLocalDateTime = searchStartLocalDateTime;
    }

    /**
     * 날짜/시간 문자열과 패턴을 받아 시작 일시(LocalDateTime) 객체로 변환한다.
     *
     * @param searchStartLocalDateTimeString 시작 일시 문자열 (반드시 시/분 정보 필요)
     * @param pattern                        파싱에 사용할 형식 패턴 (DateTimeFormatter pattern, yyyy 년 MM 월 dd 일 HH 시 mm 분 ss 초 E 요일)
     */
    public void setSearchStartLocalDateTime(String searchStartLocalDateTimeString, String pattern) {
        this.searchStartLocalDateTime = S2DateUtil.parseToLocalDateTime(searchStartLocalDateTimeString, pattern, false);
        ;
    }

    public String getSearchStartOffsetDateTimeString() {
        return searchStartOffsetDateTimeString;
    }

    public void setSearchStartOffsetDateTimeString(String searchStartOffsetDateTimeString) {
        this.searchStartOffsetDateTimeString = searchStartOffsetDateTimeString;
    }

    public OffsetDateTime getSearchStartOffsetDateTime() {
        return searchStartOffsetDateTime;
    }

    /**
     * 날짜 단위 검색을 위해 시작 시점을 해당 날짜의 자정(00:00:00)으로 정규화하여 반환한다.
     *
     * <pre>
     * WHERE 대상시점 &gt;= #{searchStartOffsetDateTimeAsStartOfDay} AND #{searchEndOffsetDateTimeAsEndOfDayExclusive} &gt; 대상시점
     * </pre>
     *
     * @return 검색 시작 자정 시점의 OffsetDateTime 객체
     */
    public OffsetDateTime getSearchStartOffsetDateTimeAsStartOfDay() {
        if (Objects.isNull(searchStartOffsetDateTime)) {
            return null;
        }
        // 시각만 00:00:00으로 변경
        return searchStartOffsetDateTime.with(LocalTime.MIN);
    }

    public void setSearchStartOffsetDateTime(OffsetDateTime searchStartOffsetDateTime) {
        this.searchStartOffsetDateTime = searchStartOffsetDateTime;
    }

    /**
     * 시작 일시(타임존 포함) 문자열과 패턴을 받아 시작 일시(OffsetDateTime) 객체로 변환한다.
     *
     * @param searchStartOffsetDateTimeString 시작 일시 문자열
     * @param pattern                         파싱에 사용할 형식 패턴 (DateTimeFormatter pattern, 반드시 오프셋 패턴(O, X, Z 등) 포함)
     *                                        yyyy-MM-dd'T'HH:mm:ssXXX → 2025-10-12T10:00:00+09:00 ('T': ISO 8601 국제표준, 'T' 또는 공백으로 날짜와 시간을 구분)
     *                                        yyyy-MM-dd HH:mm:ss O → 2025-10-12 10:00:00 GMT+9
     *                                        O: GMT+9
     *                                        OO, OOO, OOOO: GMT+9
     *                                        X: +09, Z {@code {+09: KST(한국표준시), Z: UTC(+00)}}
     *                                        XX: +0900, Z
     *                                        XXX: +09:00, Z
     *                                        XXXX: +090000, Z
     *                                        XXXXX: +09:00:00, Z
     *                                        Z, ZZ, ZZZ: +0900
     *                                        ZZZZ: GMT+09:00
     *                                        ZZZZZ: +09:00, Z
     */
    public void setSearchStartOffsetDateTime(String searchStartOffsetDateTimeString, String pattern) {
        this.searchStartOffsetDateTime = S2DateUtil.parseToOffsetDateTime(searchStartOffsetDateTimeString, pattern,
                false);
    }

    public String getSearchEndLocalDateString() {
        return searchEndLocalDateString;
    }

    public void setSearchEndLocalDateString(String searchEndLocalDateString) {
        this.searchEndLocalDateString = searchEndLocalDateString;
    }

    public LocalDate getSearchEndLocalDate() {
        return searchEndLocalDate;
    }

    /**
     * 검색 종료일의 경계 일자(다음 날짜의 자정, 00:00:00)를 반환한다.
     * 데이터베이스 쿼리에서 기간 검색 시, 검색 종료일({@code searchEndLocalDate})의 다음 날짜를 상한선으로 사용하여 종료일 당일은 포함하되 그 다음 날은 제외한다.
     *
     * <pre>
     * WHERE 대상날짜 &gt;= #{searchStartLocalDate} AND #{searchEndLocalDateExclusive} &gt; 대상날짜
     * </pre>
     *
     * @return 검색 종료일 다음 날 자정 시점의 LocalDate 객체
     */
    public LocalDate getSearchEndLocalDateExclusive() {
        return searchEndLocalDate != null ? searchEndLocalDate.plusDays(1) : null;
    }

    public void setSearchEndLocalDate(LocalDate searchEndLocalDate) {
        this.searchEndLocalDate = searchEndLocalDate;
    }

    /**
     * 날짜 문자열과 패턴을 받아 종료 일자(LocalDate) 객체로 변환한다.
     *
     * @param searchEndLocalDateString 종료 일자 문자열
     * @param pattern                  파싱에 사용할 형식 패턴 (DateTimeFormatter pattern, yyyy 년 MM 월 dd 일 E 요일)
     */
    public void setSearchEndLocalDate(String searchEndLocalDateString, String pattern) {
        this.searchEndLocalDate = S2DateUtil.parseToLocalDate(searchEndLocalDateString, pattern, false);
    }

    public String getSearchEndLocalDateTimeString() {
        return searchEndLocalDateTimeString;
    }

    public void setSearchEndLocalDateTimeString(String searchEndLocalDateTimeString) {
        this.searchEndLocalDateTimeString = searchEndLocalDateTimeString;
    }

    public LocalDateTime getSearchEndLocalDateTime() {
        return searchEndLocalDateTime;
    }

    public void setSearchEndLocalDateTime(LocalDateTime searchEndLocalDateTime) {
        this.searchEndLocalDateTime = searchEndLocalDateTime;
    }

    /**
     * 종료 일시 문자열과 패턴을 받아 종료 일시(LocalDateTime) 객체로 변환한다.
     *
     * @param searchEndLocalDateTimeString 종료 일시 문자열 (반드시 시/분 정보 필요)
     * @param pattern                      파싱에 사용할 형식 패턴 (DateTimeFormatter pattern, yyyy 년 MM 월 dd 일 HH 시 mm 분 ss 초 E 요일)
     */
    public void setSearchEndLocalDateTime(String searchEndLocalDateTimeString, String pattern) {
        this.searchEndLocalDateTime = S2DateUtil.parseToLocalDateTime(searchEndLocalDateTimeString, pattern, false);
    }

    public String getSearchEndOffsetDateTimeString() {
        return searchEndOffsetDateTimeString;
    }

    public void setSearchEndOffsetDateTimeString(String searchEndOffsetDateTimeString) {
        this.searchEndOffsetDateTimeString = searchEndOffsetDateTimeString;
    }

    public OffsetDateTime getSearchEndOffsetDateTime() {
        return searchEndOffsetDateTime;
    }

    /**
     * 날짜 단위 검색을 위해 종료 시점의 경계 값(다음 날 자정 00:00:00)을 반환한다.
     * 이 값은 검색 종료 일자를 포함하되 그 다음 날의 시작 시점부터는 제외하는 배타적 상한({@code Exclusive Upper Bound})으로 사용된다.
     *
     * <pre>
     * WHERE 대상시점 &gt;= #{searchStartOffsetDateTimeAsStartOfDay} AND #{searchEndOffsetDateTimeAsEndOfDayExclusive} &gt; 대상시점
     * </pre>
     *
     * @return 검색 종료 시점 다음 날 자정 시점의 OffsetDateTime 객체
     */
    public OffsetDateTime getSearchEndOffsetDateTimeAsEndOfDayExclusive() {
        if (Objects.isNull(searchEndOffsetDateTime)) {
            return null;
        }

        return searchEndOffsetDateTime
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay()
                .atOffset(searchEndOffsetDateTime.getOffset());
    }

    public void setSearchEndOffsetDateTime(OffsetDateTime searchEndOffsetDateTime) {
        this.searchEndOffsetDateTime = searchEndOffsetDateTime;
    }

    /**
     * 종료 일시(타임존 포함) 문자열과 패턴을 받아 종료 일시(OffsetDateTime) 객체로 변환한다.
     *
     * @param searchEndOffsetDateTimeString 종료 일시 문자열
     * @param pattern                       파싱에 사용할 형식 패턴 (DateTimeFormatter pattern, 반드시 오프셋 패턴(O, X, Z 등) 포함)
     *                                      yyyy-MM-dd'T'HH:mm:ssXXX → 2025-10-12T10:00:00+09:00 ('T': ISO 8601 국제표준, 'T' 또는 공백으로 날짜와 시간을 구분)
     *                                      yyyy-MM-dd HH:mm:ss O → 2025-10-12 10:00:00 GMT+9
     *                                      O: GMT+9
     *                                      OO, OOO, OOOO: GMT+9
     *                                      X: +09, Z {@code {+09: KST(한국표준시), Z: UTC(+00)}}
     *                                      XX: +0900, Z
     *                                      XXX: +09:00, Z
     *                                      XXXX: +090000, Z
     *                                      XXXXX: +09:00:00, Z
     *                                      Z, ZZ, ZZZ: +0900
     *                                      ZZZZ: GMT+09:00
     *                                      ZZZZZ: +09:00, Z
     */
    public void setSearchEndOffsetDateTime(String searchEndOffsetDateTimeString, String pattern) {
        this.searchEndOffsetDateTime = S2DateUtil.parseToOffsetDateTime(searchEndOffsetDateTimeString, pattern, false);
    }

    public String getSearchUseYn() {
        return searchUseYn;
    }

    public void setSearchUseYn(String searchUseYn) {
        this.searchUseYn = searchUseYn;
    }

}
