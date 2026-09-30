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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * S2QueryStringUtil 단위 테스트
 */
class S2QueryStringUtilTest {

    // =========================================================================
    // queryStringFromEntries
    // =========================================================================

    @Nested
    @DisplayName("queryStringFromEntries")
    class QueryStringFromEntries {

        @Test
        @DisplayName("기본: 엔트리만으로 쿼리 문자열을 생성한다")
        void basicEntries() {
            String result = S2QueryStringUtil.queryStringFromEntries("",
                    Map.entry("a", "1"), Map.entry("b", "2"));
            assertTrue(result.contains("a=1"));
            assertTrue(result.contains("b=2"));
        }

        @Test
        @DisplayName("URL + 엔트리 결합")
        void urlWithEntries() {
            String result = S2QueryStringUtil.queryStringFromEntries("test.do",
                    Map.entry("a", "1"), Map.entry("b", "2"));
            assertTrue(result.startsWith("test.do?"));
            assertTrue(result.contains("a=1"));
            assertTrue(result.contains("b=2"));
        }

        @Test
        @DisplayName("기존 쿼리 파라미터가 있는 URL에 엔트리를 추가한다")
        void urlWithExistingQuery() {
            String result = S2QueryStringUtil.queryStringFromEntries("test.do?x=1",
                    Map.entry("y", "2"));
            assertTrue(result.startsWith("test.do?"));
            assertTrue(result.contains("x=1"));
            assertTrue(result.contains("y=2"));
        }

        @Test
        @DisplayName("기존 쿼리 파라미터의 동일 키를 덮어쓴다")
        void overwriteExistingKey() {
            String result = S2QueryStringUtil.queryStringFromEntries("test.do?x=old",
                    Map.entry("x", "new"));
            assertTrue(result.contains("x=new"));
            assertFalse(result.contains("x=old"));
        }

        @Test
        @DisplayName("한글 값이 URL 인코딩된다")
        void koreanValueEncoded() {
            String result = S2QueryStringUtil.queryStringFromEntries("",
                    Map.entry("name", "테스트"));
            assertTrue(result.contains("name="));
            // URL-encoded 한글은 % 기호를 포함해야 한다
            assertTrue(result.contains("%"));
        }

        @Test
        @DisplayName("null baseString이면 엔트리만 생성된다")
        void nullBaseString() {
            String result = S2QueryStringUtil.queryStringFromEntries(null,
                    Map.entry("a", "1"));
            assertTrue(result.contains("a=1"));
            assertFalse(result.contains("?"));
        }

        @Test
        @DisplayName("null 엔트리는 무시된다")
        @SuppressWarnings("unchecked")
        void nullEntries() {
            String result = S2QueryStringUtil.queryStringFromEntries("test.do",
                    null, Map.entry("a", "1"));
            assertTrue(result.contains("a=1"));
        }

        @Test
        @DisplayName("빈 키를 가진 엔트리는 무시된다")
        void emptyKeyEntry() {
            String result = S2QueryStringUtil.queryStringFromEntries("",
                    Map.entry("", "1"), Map.entry("a", "2"));
            assertTrue(result.contains("a=2"));
            assertFalse(result.contains("=1&") || result.startsWith("=1"));
        }
    }

    @Nested
    @DisplayName("순서·반복 키·인코딩·프래그먼트")
    class Preservation {

        @Test
        @DisplayName("기존 파라미터 순서를 유지하고 새 키는 뒤에 붙는다")
        void keepsOrder() {
            assertEquals("/list?z=1&a=2&m=3&page=4",
                    S2QueryStringUtil.queryStringFromEntries("/list?z=1&a=2&m=3", Map.entry("page", "4")));
        }

        @Test
        @DisplayName("대체되는 키는 자리를 유지한다")
        void replacesInPlace() {
            assertEquals("/list?z=1&a=9&m=3",
                    S2QueryStringUtil.queryStringFromEntries("/list?z=1&a=2&m=3", Map.entry("a", "9")));
        }

        @Test
        @DisplayName("인코딩된 키와 값은 이중 인코딩되지 않는다")
        void noDoubleEncoding() {
            assertEquals("/list?a%5B0%5D=%ED%95%9C+%26",
                    S2QueryStringUtil.queryStringFromEntries("/list?a%5B0%5D=%ED%95%9C+%26"));
        }

        @Test
        @DisplayName("반복 키는 모두 유지된다")
        void keepsRepeatedKeys() {
            assertEquals("list?t=1&t=2&page=3",
                    S2QueryStringUtil.queryStringFromEntries("list?t=1&t=2", Map.entry("page", "3")));
        }

        @Test
        @DisplayName("#fragment 는 맨 뒤에 유지된다")
        void keepsFragment() {
            assertEquals("list?a=1&page=3#top",
                    S2QueryStringUtil.queryStringFromEntries("list?a=1#top", Map.entry("page", "3")));
            assertEquals("1", S2QueryStringUtil.getQueryStringParameter("list?a=1#top", "a"));
        }

        @Test
        @DisplayName("잘못된 퍼센트 인코딩은 예외가 발생한다")
        void malformedEncoding() {
            assertThrows(IllegalArgumentException.class, () -> S2QueryStringUtil.queryStringFromEntries("list?a=%E"));
            assertThrows(IllegalArgumentException.class, () -> S2QueryStringUtil.getQueryStringParameter("a=%zz", "a"));
        }

        @Test
        @DisplayName("인코딩된 키도 디코딩된 이름으로 조회된다")
        void decodedKeyLookup() {
            assertEquals("x", S2QueryStringUtil.getQueryStringParameter("a%5B0%5D=x", "a[0]"));
        }
    }

    // =========================================================================
    // getQueryStringParameter
    // =========================================================================

    @Nested
    @DisplayName("getQueryStringParameter")
    class GetQueryStringParameter {

        @Test
        @DisplayName("기본 쿼리 문자열에서 파라미터를 조회한다")
        void basicQuery() {
            assertEquals("1", S2QueryStringUtil.getQueryStringParameter("a=1&b=2", "a"));
            assertEquals("2", S2QueryStringUtil.getQueryStringParameter("a=1&b=2", "b"));
        }

        @Test
        @DisplayName("URL에서 쿼리 파라미터를 조회한다")
        void fromUrl() {
            assertEquals("hello",
                    S2QueryStringUtil.getQueryStringParameter("http://example.com?name=hello", "name"));
        }

        @Test
        @DisplayName("존재하지 않는 키를 조회하면 빈 문자열을 반환한다")
        void missingKey() {
            assertEquals("",
                    S2QueryStringUtil.getQueryStringParameter("a=1&b=2", "c"));
        }

        @Test
        @DisplayName("null 쿼리 문자열이면 빈 문자열을 반환한다")
        void nullQuery() {
            assertEquals("", S2QueryStringUtil.getQueryStringParameter(null, "a"));
        }

        @Test
        @DisplayName("null 키이면 빈 문자열을 반환한다")
        void nullKey() {
            assertEquals("", S2QueryStringUtil.getQueryStringParameter("a=1", null));
        }

        @Test
        @DisplayName("빈 쿼리 문자열이면 빈 문자열을 반환한다")
        void emptyQuery() {
            assertEquals("", S2QueryStringUtil.getQueryStringParameter("", "a"));
        }

        @Test
        @DisplayName("등호가 없는 쿼리 문자열이면 빈 문자열을 반환한다")
        void noEquals() {
            assertEquals("", S2QueryStringUtil.getQueryStringParameter("abcdef", "a"));
        }

        @Test
        @DisplayName("값이 빈 파라미터를 조회하면 빈 문자열을 반환한다")
        void emptyValue() {
            assertEquals("", S2QueryStringUtil.getQueryStringParameter("a=&b=2", "a"));
        }
    }
}
