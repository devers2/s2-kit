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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * S2CollectionUtil 단위 테스트
 */
class S2CollectionUtilTest {

    // =========================================================================
    // listFindIndex
    // =========================================================================

    @Nested
    @DisplayName("listFindIndex")
    class ListFindIndex {

        @Test
        @DisplayName("조건에 맞는 첫 번째 인덱스를 반환한다")
        void findFirstMatch() {
            List<Map<String, Object>> list = List.of(
                    Map.of("type", "a", "value", 1),
                    Map.of("type", "b", "value", 2),
                    Map.of("type", "a", "value", 3));

            int idx = S2CollectionUtil.listFindIndex(list, Map.entry("type", "a"));
            assertEquals(0, idx);
        }

        @Test
        @DisplayName("다중 조건을 모두 만족하는 인덱스를 반환한다")
        void findWithMultipleConditions() {
            List<Map<String, Object>> list = List.of(
                    Map.of("type", "a", "value", 1),
                    Map.of("type", "a", "value", 3),
                    Map.of("type", "b", "value", 2));

            @SuppressWarnings("unchecked")
            int idx = S2CollectionUtil.listFindIndex(list,
                    Map.entry("type", "a"), Map.entry("value", 3));
            assertEquals(1, idx);
        }

        @Test
        @DisplayName("일치하는 항목이 없으면 -1을 반환한다")
        void noMatch() {
            List<Map<String, Object>> list = List.of(Map.of("type", "a"));
            int idx = S2CollectionUtil.listFindIndex(list, Map.entry("type", "z"));
            assertEquals(-1, idx);
        }

        @Test
        @DisplayName("null 리스트이면 -1을 반환한다")
        void nullList() {
            int idx = S2CollectionUtil.listFindIndex(null, Map.entry("type", "a"));
            assertEquals(-1, idx);
        }

        @Test
        @DisplayName("빈 리스트이면 -1을 반환한다")
        void emptyList() {
            int idx = S2CollectionUtil.listFindIndex(new ArrayList<>(), Map.entry("type", "a"));
            assertEquals(-1, idx);
        }

        @Test
        @DisplayName("조건이 없으면 -1을 반환한다")
        @SuppressWarnings("unchecked")
        void noConditions() {
            List<Map<String, Object>> list = List.of(Map.of("type", "a"));
            int idx = S2CollectionUtil.listFindIndex(list);
            assertEquals(-1, idx);
        }

        @Test
        @DisplayName("조건의 키가 비어 있으면 -1을 반환한다")
        void emptyKey() {
            List<Map<String, Object>> list = List.of(Map.of("type", "a"));
            int idx = S2CollectionUtil.listFindIndex(list, Map.entry("", "a"));
            assertEquals(-1, idx);
        }
    }

    // =========================================================================
    // listFilter
    // =========================================================================

    @Nested
    @DisplayName("listFilter")
    class ListFilter {

        @Test
        @DisplayName("조건에 맞는 모든 항목을 반환한다")
        void filterMatches() {
            List<Map<String, Object>> list = List.of(
                    Map.of("type", "a", "value", 1),
                    Map.of("type", "b", "value", 2),
                    Map.of("type", "a", "value", 3));

            List<Map<String, Object>> result = S2CollectionUtil.listFilter(list, Map.entry("type", "a"));
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("일치 항목이 없으면 빈 리스트를 반환한다")
        void noMatch() {
            List<Map<String, Object>> list = List.of(Map.of("type", "a"));
            List<Map<String, Object>> result = S2CollectionUtil.listFilter(list, Map.entry("type", "z"));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 리스트이면 빈 리스트를 반환한다")
        void nullList() {
            List<Map<String, Object>> result = S2CollectionUtil.listFilter(null, Map.entry("type", "a"));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("빈 조건이면 빈 리스트를 반환한다")
        @SuppressWarnings("unchecked")
        void emptyConditions() {
            List<Map<String, Object>> list = List.of(Map.of("type", "a"));
            List<Map<String, Object>> result = S2CollectionUtil.listFilter(list);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // listSort
    // =========================================================================

    @Nested
    @DisplayName("listSort")
    class ListSort {

        @Test
        @DisplayName("ASC 정렬이 올바르게 동작한다")
        void sortAsc() {
            List<Map<String, Object>> list = new ArrayList<>(List.of(
                    Map.of("name", "c"),
                    Map.of("name", "a"),
                    Map.of("name", "b")));

            List<Map<String, Object>> result = S2CollectionUtil.listSort(list, "name", "ASC");
            assertEquals("a", result.get(0).get("name"));
            assertEquals("b", result.get(1).get("name"));
            assertEquals("c", result.get(2).get("name"));
        }

        @Test
        @DisplayName("DESC 정렬이 올바르게 동작한다")
        void sortDesc() {
            List<Map<String, Object>> list = new ArrayList<>(List.of(
                    Map.of("name", "a"),
                    Map.of("name", "c"),
                    Map.of("name", "b")));

            List<Map<String, Object>> result = S2CollectionUtil.listSort(list, "name", "DESC");
            assertEquals("c", result.get(0).get("name"));
            assertEquals("b", result.get(1).get("name"));
            assertEquals("a", result.get(2).get("name"));
        }

        @Test
        @DisplayName("숫자 필드 정렬이 올바르게 동작한다")
        void sortNumbers() {
            List<Map<String, Object>> list = new ArrayList<>(List.of(
                    Map.of("value", 30),
                    Map.of("value", 10),
                    Map.of("value", 20)));

            List<Map<String, Object>> result = S2CollectionUtil.listSort(list, "value", "ASC");
            assertEquals(10, result.get(0).get("value"));
            assertEquals(20, result.get(1).get("value"));
            assertEquals(30, result.get(2).get("value"));
        }

        @Test
        @DisplayName("null 값이 포함된 필드 정렬 시 오류 없이 동작한다")
        void sortWithNulls() {
            Map<String, Object> m1 = new HashMap<>();
            m1.put("name", null);
            Map<String, Object> m2 = Map.of("name", "a");
            Map<String, Object> m3 = Map.of("name", "b");

            List<Map<String, Object>> list = new ArrayList<>(List.of(m1, m2, m3));
            List<Map<String, Object>> result = S2CollectionUtil.listSort(list, "name", "ASC");

            assertNotNull(result);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("잘못된 orderBy 값이면 예외가 발생한다 (정렬하지 않은 목록을 조용히 돌려주지 않음)")
        void invalidOrderBy() {
            List<Map<String, Object>> list = List.of(Map.of("name", "a"));
            assertThrows(IllegalArgumentException.class, () -> S2CollectionUtil.listSort(list, "name", "INVALID"));
            assertEquals(list, S2CollectionUtil.listSort(list, "name", null), "null orderBy means ASC");
        }

        @Test
        @DisplayName("NaN·무한대도 정렬한다")
        void nonFiniteNumbers() {
            List<Map<String, Object>> list = List.of(Map.of("v", Double.NaN), Map.of("v", 1.5),
                    Map.of("v", Double.NEGATIVE_INFINITY));
            var result = S2CollectionUtil.listSort(list, "v", "ASC");
            assertEquals(Double.NEGATIVE_INFINITY, result.get(0).get("v"));
            assertEquals(1.5, result.get(1).get("v"));
        }

        @Test
        @DisplayName("빈 리스트도 항상 새 리스트를 반환한다")
        void emptyList() {
            List<Map<String, Object>> list = new ArrayList<>();
            List<Map<String, Object>> result = S2CollectionUtil.listSort(list, "name", "ASC");
            assertNotSame(list, result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("원본 리스트를 변경하지 않는다 (불변성)")
        void doesNotMutateOriginal() {
            List<Map<String, Object>> list = List.of(
                    Map.of("name", "c"),
                    Map.of("name", "a"));

            List<Map<String, Object>> result = S2CollectionUtil.listSort(list, "name", "ASC");
            // 원본은 불변 리스트이므로 결과는 새 리스트여야 한다
            assertNotSame(list, result);
            assertEquals("a", result.get(0).get("name"));
        }
    }
}
