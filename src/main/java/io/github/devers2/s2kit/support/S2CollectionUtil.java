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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import io.github.devers2.s2util.core.S2Util;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2025. 05. 27.
 */
public class S2CollectionUtil {

    /**
     * Map 또는 VO 객체의 List 에서 조건에 맞는 Index 를 반환한다. (1개 이상의 객체가 조건에 맞는다면 첫번째 객체의 Index 를 반환)
     *
     * @param <K>        조건 키의 타입
     * @param <V>        조건 값의 타입
     * @param <T>        리스트 요소의 타입
     * @param list       (Map 또는 VO 객체의 List)
     * @param conditions 조건(가변인자)
     * @return Index
     * @apiNote
     *
     *          <pre>{@code
     * int findIdx = S2Util.listFindIndex(list, Map.entry("typeA", "a"), Map.entry("typeB", 3));
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    public static <K, V, T> int listFindIndex(List<T> list, Entry<K, V>... conditions) {
        if (S2Util.isEmpty(list) || S2Util.isEmpty(conditions)) {
            return -1;
        }

        // 값(value)은 빈 문자열("") 같은 정상적인 검색 대상일 수 있으므로 검증하지 않는다.
        // key 가 없는 조건만 무효로 처리한다.
        if (Arrays.stream(conditions).anyMatch(c -> S2Util.isEmpty(c.getKey()))) {
            return -1;
        }

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            boolean allConditionsMet = true;

            for (Entry<K, V> condition : conditions) {
                if (!Objects.equals(condition.getValue(), S2Util.getValue(item, condition.getKey()))) {
                    allConditionsMet = false;
                    break;
                }
            }

            if (allConditionsMet) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Map 또는 VO 객체의 List 에서 조건에 맞는 객체 목록을 반환한다.
     *
     * @param <K>        조건 키의 타입
     * @param <V>        조건 값의 타입
     * @param <T>        리스트 요소의 타입
     * @param list       (Map 또는 VO 객체의 List)
     * @param conditions 조건(가변인자)
     * @return 객체 목록
     * @apiNote
     *
     *          <pre>{@code
     * List<Map < String, Object>> filterList = (List<Map<String, Object>>);
     * S2Util.listFilter(testList, Map.entry("typeA", "a"), Map.entry("typeB", 3));
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    public static <K, V, T> List<T> listFilter(List<T> list, Entry<K, V>... conditions) {
        if (S2Util.isEmpty(list) || S2Util.isEmpty(conditions)) {
            return new ArrayList<>();
        }

        List<T> resultList = new ArrayList<>();
        for (T item : list) {
            boolean allConditionsMet = true;
            for (Entry<K, V> condition : conditions) {
                // 값(value)은 빈 문자열("") 같은 정상적인 검색 대상일 수 있으므로 검증하지 않는다.
                boolean currentConditionMet = S2Util.isNotEmpty(condition.getKey())
                        && Objects.equals(S2Util.getValue(item, condition.getKey()), condition.getValue());

                if (!currentConditionMet) {
                    allConditionsMet = false;
                    break;
                }
            }

            if (allConditionsMet) {
                resultList.add(item);
            }
        }
        return resultList;
    }

    /**
     * Map 또는 VO 객체의 목록을 정렬하여 반환한다.
     *
     * @param <V>       정렬기준 필드의 타입
     * @param <T>       리스트 요소의 타입
     * @param list      (Map 또는 VO 객체의 List)
     * @param fieldName 정렬기준 필드명(VO) 또는 Key(Map)
     * @param orderBy   정렬순서("DESC", "ASC", 대소문자 무시. null/공백이면 ASC)
     * @return 정렬된 새 목록 (원본은 바꾸지 않음. null 값은 ASC 에서 맨 앞, DESC 에서 맨 뒤)
     * @throws IllegalArgumentException orderBy 가 ASC/DESC 가 아닐 때
     * @apiNote
     *
     *          <pre>{@code
     * S2Util.listSort(testList, "key", "ASC");
     * }</pre>
     */
    public static <V, T> List<T> listSort(List<T> list, V fieldName, String orderBy) {
        var descending = orderBy != null && "DESC".equalsIgnoreCase(orderBy.trim());
        if (orderBy != null && !orderBy.isBlank() && !descending && !"ASC".equalsIgnoreCase(orderBy.trim())) {
            throw new IllegalArgumentException("orderBy 는 ASC 또는 DESC 여야 합니다: " + orderBy);
        }
        if (list == null) {
            return null;
        }
        if (S2Util.isEmpty(fieldName)) {
            return new ArrayList<>(list);
        }

        Comparator<T> comparator = (a, b) -> {
            Object va = S2Util.getValue(a, fieldName);
            Object vb = S2Util.getValue(b, fieldName);

            if (va == null && vb == null) {
                return 0;
            }
            if (va == null) {
                return -1;
            }
            if (vb == null) {
                return 1;
            }

            // 숫자끼리 비교 (정밀도 위해 BigDecimal 사용)
            if (va instanceof Number && vb instanceof Number) {
                if (!isFinite((Number) va) || !isFinite((Number) vb)) {
                    // NaN and infinity have no BigDecimal form | NaN·무한대는 BigDecimal 로 나타낼 수 없음
                    return Double.compare(((Number) va).doubleValue(), ((Number) vb).doubleValue());
                }
                BigDecimal na = new BigDecimal(String.valueOf(va));
                BigDecimal nb = new BigDecimal(String.valueOf(vb));
                return na.compareTo(nb);
            }

            // 날짜 비교 (java.util.Date 기준)
            if (va instanceof Date && vb instanceof Date) {
                return ((Date) va).compareTo((Date) vb);
            }

            // Comparable 처리 (제네릭 경고 회피 및 타입 불일치 시 문자열 폴백)
            if (va instanceof Comparable && vb instanceof Comparable) {
                try {
                    @SuppressWarnings("unchecked")
                    Comparable<Object> ca = (Comparable<Object>) va;
                    return ca.compareTo(vb);
                } catch (ClassCastException e) {
                    return String.valueOf(va).compareTo(String.valueOf(vb));
                }
            }

            // 그 외 문자열 기반 비교
            return String.valueOf(va).compareTo(String.valueOf(vb));
        };

        // 호출자가 넘긴 리스트가 불변 리스트(List.of() 등)일 수 있으므로 원본을 직접 정렬하지
        // 않고 복사본을 정렬해 반환한다.
        var result = new ArrayList<>(list);
        var effectiveComparator = descending ? comparator.reversed() : comparator;

        try {
            result.sort(effectiveComparator);
        } catch (IllegalArgumentException e) {
            // 필드 값의 타입이 요소마다 뒤섞여 있어 위 comparator 가 total ordering 계약을
            // 위반한 경우(TimSort 의 "Comparison method violates its general contract!").
            // 항상 유효한 총순서를 보장하는 문자열 비교로 안전하게 다시 정렬한다.
            Comparator<T> stringComparator = Comparator
                    .comparing(item -> String.valueOf(S2Util.getValue(item, fieldName)));
            result.sort(descending ? stringComparator.reversed() : stringComparator);
        }
        return result;
    }

    private static boolean isFinite(Number number) {
        return !(number instanceof Double || number instanceof Float) || Double.isFinite(number.doubleValue());
    }

}
