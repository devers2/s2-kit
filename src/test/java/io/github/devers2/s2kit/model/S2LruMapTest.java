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
package io.github.devers2.s2kit.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * S2LruMap 단위 테스트
 */
class S2LruMapTest {

    @Nested
    @DisplayName("LRU 축출 동작")
    class EvictionBehavior {

        @Test
        @DisplayName("최대 용량을 초과하면 가장 오래된 항목이 제거된다")
        void evictsEldest() {
            Map<String, String> map = S2LruMap.createSynchronizedLRUMap(3);
            map.put("a", "1");
            map.put("b", "2");
            map.put("c", "3");
            map.put("d", "4"); // "a"가 축출되어야 함

            assertNull(map.get("a"), "최대 용량 초과 시 가장 오래된 항목이 제거되어야 한다");
            assertEquals("4", map.get("d"));
            assertEquals(3, map.size());
        }

        @Test
        @DisplayName("접근된 항목은 LRU 순서에서 최신으로 이동한다")
        void accessRefreshesOrder() {
            Map<String, String> map = S2LruMap.createSynchronizedLRUMap(3);
            map.put("a", "1");
            map.put("b", "2");
            map.put("c", "3");

            // "a"에 접근하여 최신으로 이동
            map.get("a");

            // "d" 추가 → "b"가 축출되어야 함 (가장 오래 접근되지 않은 항목)
            map.put("d", "4");

            assertNull(map.get("b"), "접근되지 않은 가장 오래된 'b'가 제거되어야 한다");
            assertNotNull(map.get("a"), "'a'는 접근되었으므로 유지되어야 한다");
        }
    }

    @Nested
    @DisplayName("기본 Map 동작")
    class BasicMapOperations {

        @Test
        @DisplayName("기본 생성 시 최대 용량 10000으로 생성된다")
        void defaultCapacity() {
            Map<Integer, String> map = S2LruMap.createSynchronizedLRUMap();
            // 기본 용량 범위 내에서 항목 추가 테스트
            for (int i = 0; i < 100; i++) {
                map.put(i, "val" + i);
            }
            assertEquals(100, map.size());
        }

        @Test
        @DisplayName("put/get/remove 기본 동작이 정상적이다")
        void putGetRemove() {
            Map<String, Integer> map = S2LruMap.createSynchronizedLRUMap(10);
            map.put("key1", 100);
            assertEquals(100, map.get("key1"));

            map.remove("key1");
            assertNull(map.get("key1"));
            assertEquals(0, map.size());
        }

        @Test
        @DisplayName("containsKey가 정상 동작한다")
        void containsKey() {
            Map<String, String> map = S2LruMap.createSynchronizedLRUMap(5);
            map.put("exists", "yes");
            assertTrue(map.containsKey("exists"));
            assertFalse(map.containsKey("missing"));
        }

        @Test
        @DisplayName("용량이 1인 맵도 정상 동작한다")
        void capacity1() {
            Map<String, String> map = S2LruMap.createSynchronizedLRUMap(1);
            map.put("a", "1");
            assertEquals("1", map.get("a"));

            map.put("b", "2");
            assertNull(map.get("a"), "용량 1이므로 'a'가 축출되어야 한다");
            assertEquals("2", map.get("b"));
            assertEquals(1, map.size());
        }
    }

    @Nested
    @DisplayName("스레드 안전성")
    class ThreadSafety {

        @Test
        @DisplayName("동시 쓰기 시 데이터 손실 없이 동작한다")
        void concurrentWrites() throws InterruptedException {
            Map<Integer, Integer> map = S2LruMap.createSynchronizedLRUMap(1000);
            int threads = 10;
            int itemsPerThread = 100;

            Thread[] threadArray = new Thread[threads];
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                threadArray[t] = new Thread(() -> {
                    for (int i = 0; i < itemsPerThread; i++) {
                        map.put(threadId * itemsPerThread + i, i);
                    }
                });
                threadArray[t].start();
            }

            for (Thread thread : threadArray) {
                thread.join();
            }

            assertEquals(threads * itemsPerThread, map.size());
        }
    }
}
