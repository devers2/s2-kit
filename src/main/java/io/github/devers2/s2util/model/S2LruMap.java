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
package io.github.devers2.s2util.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * s2's utilities
 * 최대 크기를 가진 LRU(Least Recently Used) 캐시 기능을 제공하는 Map
 *
 * <p>
 * <b>기본 동작:</b><br>
 * 이 Map은 <b>LRU(Least Recently Used) 알고리즘</b>에 따라 동작하며, 최대 용량을 초과하면
 * <b>가장 오랫동안 접근되지 않은 항목(eldest entry)</b>을 자동으로 제거한다.
 * </p>
 *
 * <p>
 * <b>⚠️ 성능 및 동시성 주의사항 (Performance Warning):</b><br>
 * 이 클래스는 스레드 안전성을 보장하기 위해 {@link Collections#synchronizedMap}을 사용하여 래핑된다.<br>
 * 이는 <strong>읽기 작업({@code get})을 포함한 모든 접근 시 Map 전체에 락(Monitor Lock)을 건다.</strong>
 * </p>
 *
 * <p>
 * 따라서 수많은 스레드가 동시에 빈번하게 읽기를 수행하는 <b>고성능/고병렬 환경(High-Concurrency)</b>에서는
 * 심각한 스레드 경합(Lock Contention)과 대기(Blocking)가 발생하여 전체 시스템의 처리량(Throughput)이 저하될 수 있다.<br>
 * <i>(참고: {@code S2Util}의 MethodHandle 캐시처럼 초고속 읽기가 핵심인 곳에서는 이 클래스 대신 {@code ConcurrentHashMap} 기반의 전략을 사용해야 한다.)</i>
 * </p>
 *
 * <p>
 * <b>권장 사용처:</b><br>
 * - 데이터의 <b>정합성과 정확한 LRU 순서 유지</b>가 절대적 속도보다 중요한 경우<br>
 * - 동시 접근 빈도가 높지 않은 일반적인 비즈니스 데이터 캐싱<br>
 * - 캐시 크기가 작고, 쓰기(put) 빈도가 상대적으로 높은 경우
 * </p>
 *
 * <p>
 * <b>⚠️ 순회(iteration) 시 주의사항:</b><br>
 * {@code get}/{@code put} 등 개별 메서드 호출은 자동으로 락이 걸리지만, {@code entrySet()},
 * {@code keySet()}, {@code values()} 로 얻은 뷰를 for-each 나 {@code Iterator} 로 순회하는 동안은
 * 그 순회 전체가 하나의 원자적 동작이 아니다. 다른 스레드가 동시에 {@code put}/{@code remove} 를
 * 호출하면 {@code ConcurrentModificationException} 이 발생할 수 있으므로, 순회할 때는 반드시 그
 * 맵 객체 자체를 대상으로 수동 동기화해야 한다:
 *
 * <pre>{@code
 * Map<K, V> cache = S2LruMap.createSynchronizedLRUMap();
 * synchronized (cache) {
 *     for (Map.Entry<K, V> entry : cache.entrySet()) {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * 단순히 각 항목에 대해 처리만 하면 되는 경우라면, 수동 동기화가 필요 없는 {@link Map#forEach}
 * 사용을 권장한다({@code Collections.synchronizedMap} 은 {@code forEach} 자체를 내부적으로
 * 동기화해서 제공한다).
 * </p>
 *
 * @param <K> 키 타입
 * @param <V> 값 타입
 *
 * @author devers2
 * @version 1.0
 * @since 2020. 07. 08.
 */
public class S2LruMap<K, V> extends LinkedHashMap<K, V> {

    private static final long serialVersionUID = -2094892419572502396L;

    private final int maxCapacity;

    /**
     * S2LRUMap 생성자
     *
     * @param initialCapacity 초기 용량
     * @param loadFactor      로드 팩터
     * @param accessOrder     접근 순서 (true: LRU, false: 삽입 순서)
     * @param maxCapacity     최대 용량
     */
    private S2LruMap(int initialCapacity, float loadFactor, boolean accessOrder, int maxCapacity) {
        super(initialCapacity, loadFactor, accessOrder);
        this.maxCapacity = maxCapacity;
    }

    /**
     * 캐시 크기가 maxCapacity를 초과하면 가장 오래된 항목(가장 오랫동안 접근되지 않은 항목)을 제거한다.
     * 이는 LRU 알고리즘에 따라 동작하며, 새로운 항목이 추가될 때마다 검사된다.
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxCapacity;
    }

    /**
     * LRU 기능을 가진 쓰레드 안전한 Map 인스턴스를 생성하는 헬퍼 메서드.
     * (최대 용량 10000으로 자동 설정)
     *
     * @param <K> 키 타입
     * @param <V> 값 타입
     * @return 쓰레드 안전한 LRU Map
     */
    public static <K, V> Map<K, V> createSynchronizedLRUMap() {
        return createSynchronizedLRUMap(10000);
    }

    /**
     * LRU 기능을 가진 쓰레드 안전한 Map 인스턴스를 생성하는 헬퍼 메서드.
     *
     * @param maxCapacity 최대 용량
     * @param <K>         키 타입
     * @param <V>         값 타입
     * @return 쓰레드 안전한 LRU Map
     * @throws IllegalArgumentException maxCapacity 가 1 보다 작을 때
     */
    public static <K, V> Map<K, V> createSynchronizedLRUMap(int maxCapacity) {
        if (maxCapacity < 1) {
            throw new IllegalArgumentException("maxCapacity 는 1 이상이어야 합니다: " + maxCapacity);
        }
        // Start small; the map grows as entries are added | 작게 시작하고 항목이 늘면 커짐
        return Collections.synchronizedMap(
                new S2LruMap<>(Math.min(maxCapacity, 16), 0.75f, true, maxCapacity));
    }

}
