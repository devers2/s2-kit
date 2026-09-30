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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * S2TypeUtil 단위 테스트
 */
class S2TypeUtilTest {

    static class SampleBean {
        private String name;

        public SampleBean() {
            this.name = "default";
        }

        public SampleBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    // =========================================================================
    // createInstance
    // =========================================================================

    @Nested
    @DisplayName("createInstance")
    class CreateInstance {

        @Test
        @DisplayName("기본 생성자로 인스턴스를 생성한다")
        void defaultConstructor() {
            SampleBean bean = S2TypeUtil.createInstance(SampleBean.class);
            assertNotNull(bean);
            assertEquals("default", bean.getName());
        }

        @Test
        @DisplayName("인자 있는 생성자로 인스턴스를 생성한다")
        void parameterizedConstructor() {
            SampleBean bean = S2TypeUtil.createInstance(SampleBean.class, "custom");
            assertNotNull(bean);
            assertEquals("custom", bean.getName());
        }
    }

    // =========================================================================
    // castByName & castListByName
    // =========================================================================

    @Nested
    @DisplayName("castByName & castListByName")
    class Casting {

        @Test
        @DisplayName("호환되는 타입으로 정상 캐스팅된다")
        void validCast() {
            Object str = "test string";
            String result = S2TypeUtil.castByName(str, String.class);
            assertEquals("test string", result);
        }

        @Test
        @DisplayName("상위 인터페이스로 정상 캐스팅된다")
        void castToInterface() {
            Object list = new ArrayList<String>();
            List<?> result = S2TypeUtil.castByName(list, List.class);
            assertNotNull(result);
        }

        @Test
        @DisplayName("null 객체 캐스팅 시 null을 반환한다")
        void nullCast() {
            assertNull(S2TypeUtil.castByName(null, String.class));
        }

        @Test
        @DisplayName("호환되지 않는 타입이면 TypeMismatchException이 발생한다")
        void incompatibleCast() {
            Object number = 123;
            assertThrows(S2TypeUtil.TypeMismatchException.class, () ->
                    S2TypeUtil.castByName(number, String.class));
        }

        @Test
        @DisplayName("castListByName으로 리스트 요소를 안전하게 형변환한다")
        void castList() {
            List<Object> rawList = List.of("a", "b", "c");
            List<String> typedList = S2TypeUtil.castListByName(rawList, String.class);
            assertEquals(3, typedList.size());
            assertEquals("a", typedList.get(0));
        }

        @Test
        @DisplayName("null 컬렉션을 전달하면 null을 반환한다")
        void nullCollection() {
            assertNull(S2TypeUtil.castListByName(null, String.class));
        }
    }

    // =========================================================================
    // instanceOfByName & isAssignableFromByName
    // =========================================================================

    @Nested
    @DisplayName("instanceOfByName & isAssignableFromByName")
    class TypeHierarchy {

        @Test
        @DisplayName("동일 클래스에 대해 true를 반환한다")
        void sameClass() {
            assertTrue(S2TypeUtil.instanceOfByName("hello", String.class));
            assertTrue(S2TypeUtil.isAssignableFromByName(String.class, String.class));
        }

        @Test
        @DisplayName("상속 관계에 대해 true를 반환한다")
        void subClass() {
            assertTrue(S2TypeUtil.instanceOfByName(new ArrayList<>(), List.class));
            assertTrue(S2TypeUtil.isAssignableFromByName(ArrayList.class, List.class));
            assertTrue(S2TypeUtil.isAssignableFromByName(ArrayList.class, Object.class));
        }

        @Test
        @DisplayName("인터페이스 구현 관계에 대해 true를 반환한다")
        void implementsInterface() {
            assertTrue(S2TypeUtil.isAssignableFromByName(String.class, CharSequence.class));
            assertTrue(S2TypeUtil.isAssignableFromByName(String.class, Serializable.class));
        }

        @Test
        @DisplayName("무관한 클래스에 대해 false를 반환한다")
        void unrelatedClass() {
            assertFalse(S2TypeUtil.instanceOfByName("hello", Integer.class));
            assertFalse(S2TypeUtil.isAssignableFromByName(String.class, Integer.class));
        }

        @Test
        @DisplayName("null 인자에 대해 false를 반환한다")
        void nullArguments() {
            assertFalse(S2TypeUtil.instanceOfByName(null, String.class));
            assertFalse(S2TypeUtil.instanceOfByName("hello", null));
            assertFalse(S2TypeUtil.isAssignableFromByName(null, String.class));
            assertFalse(S2TypeUtil.isAssignableFromByName(String.class, null));
        }

        @Test
        @DisplayName("배열 타입에 대해서도 차원과 컴포넌트를 정확히 비교한다")
        void arrayTypes() {
            assertTrue(S2TypeUtil.isAssignableFromByName(String[].class, Object[].class));
            assertFalse(S2TypeUtil.isAssignableFromByName(String[].class, String[][].class));
        }
    }

    // =========================================================================
    // compare
    // =========================================================================

    @Nested
    @DisplayName("compare")
    class CompareValues {

        @Test
        @DisplayName("문자열 비교가 정상 동작한다")
        void stringCompare() {
            assertTrue(S2TypeUtil.compare("a", "b") < 0);
            assertEquals(0, S2TypeUtil.compare("a", "a"));
            assertTrue(S2TypeUtil.compare("b", "a") > 0);
        }

        @Test
        @DisplayName("숫자 비교가 정상 동작한다")
        void numberCompare() {
            assertTrue(S2TypeUtil.compare(10, 20) < 0);
            assertEquals(0, S2TypeUtil.compare(10, 10));
            assertTrue(S2TypeUtil.compare(30, 20) > 0);
        }

        @Test
        @DisplayName("null 처리: 둘 다 null이면 0, 하나만 null이면 적절한 부호를 반환한다")
        void nullHandling() {
            assertEquals(0, S2TypeUtil.compare(null, null));
            assertEquals(-1, S2TypeUtil.compare(null, "a"));
            assertEquals(1, S2TypeUtil.compare("a", null));
        }

        @Test
        @DisplayName("비교 불가한 타입이면 0(같음) 대신 예외가 발생한다")
        void incompatibleThrows() {
            assertThrows(IllegalArgumentException.class, () -> S2TypeUtil.compare("hello", 123));
            assertThrows(IllegalArgumentException.class, () -> S2TypeUtil.compare(new Object(), new Object()));
        }

        @Test
        @DisplayName("castByName 은 이름이 같아도 클래스 로더가 다르면 원인을 알려 주는 예외를 던진다")
        void castAcrossClassLoaders() throws Exception {
            var location = SampleBean.class.getProtectionDomain().getCodeSource().getLocation();
            try (var isolated = new java.net.URLClassLoader(new java.net.URL[] { location }, null)) {
                var constructor = isolated.loadClass(SampleBean.class.getName()).getDeclaredConstructor();
                constructor.setAccessible(true);
                var foreign = constructor.newInstance();
                assertTrue(S2TypeUtil.instanceOfByName(foreign, SampleBean.class));
                var e = assertThrows(S2TypeUtil.TypeMismatchException.class,
                        () -> S2TypeUtil.castByName(foreign, SampleBean.class));
                assertTrue(e.getMessage().contains("클래스 로더"), e.getMessage());
            }
            assertEquals(Integer.valueOf(5), S2TypeUtil.castByName(5, int.class));
        }

        @Test
        @DisplayName("createInstance 는 하위 타입·박싱 인자로도 생성자를 찾는다")
        void createInstanceWithAssignableArgs() {
            record Box(Number value, int count) {
            }
            var box = S2TypeUtil.createInstance(Box.class, 3L, 2);
            assertEquals(3L, box.value());
            assertEquals(2, box.count());
            assertThrows(RuntimeException.class, () -> S2TypeUtil.createInstance(Box.class, "x", 2));
        }
    }
}
