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

import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import io.github.devers2.s2util.core.S2Cache;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;
import io.github.devers2.s2util.support.S2TypeUtil;

/**
 * 특정 애노테이션의 {@code value()} 값에 해당하는 구체 클래스 타입을 찾아주는 범용 유틸리티.
 *
 * <p>
 * <strong>주요 특징:</strong>
 * </p>
 * <ul>
 * <li>적층식 캐싱을 사용한다: 검색 범위(패키지)와 관계없이 클래스와 애노테이션을 기준으로 캐시를 공유한다.
 * 요청된 패키지 중 아직 스캔하지 않은 패키지만 선별하여 증분 스캔을 수행한다.</li>
 * <li>동적 범위 필터링을 적용한다: 캐시에는 누적된 모든 스캔 결과가 저장되지만, 조회 시점에는
 * 요청한 패키지 범위 내의 클래스만 필터링하여 반환한다.</li>
 * <li>유연한 반환 타입을 지원한다: 단건 조회({@link #resolveType})와 목록 조회({@link #resolveTypes})를 모두 제공한다.</li>
 * </ul>
 *
 * <p>
 * ClassPathScanningCandidateComponentProvider에 의존하므로, Spring 환경에서 사용한다.
 * </p>
 */
public final class S2AnnotationResolver {

    private static final S2Logger logger = S2LogManager.getLogger(S2AnnotationResolver.class);

    /**
     * resolveValue용 캐시 키: returnType을 포함하여 타입별 캐싱 안전성 보장.
     */
    private record ValueKey(Class<?> clazz, Class<? extends Annotation> annotationType, Class<?> returnType) {
        public ValueKey {
            Objects.requireNonNull(clazz);
            Objects.requireNonNull(annotationType);
            Objects.requireNonNull(returnType);
        }
    }

    /**
     * 캐시 값: 스캔된 클래스 데이터와 스캔 완료된 패키지 이력을 관리하는 컨테이너.
     * 하나의 키(BaseClass + Annotation)에 대해 여러 패키지의 스캔 결과가 이곳에 누적된다.
     */
    private static class AnnotationScopeCache {
        // 이미 스캔을 완료한 패키지 목록 (중복 스캔 방지용, Thread-Safe Set)
        private final Set<String> scannedPackages = Collections.synchronizedSet(new HashSet<>());

        // 값(String) -> WeakReference<Class<?>> 목록 매핑
        private final ConcurrentMap<String, List<WeakReference<Class<?>>>> valueMap = new ConcurrentHashMap<>();

        // GC된 WeakReference 청소용 큐
        private final ReferenceQueue<Class<?>> queue = new ReferenceQueue<>();

        /**
         * 요청된 패키지 중 아직 스캔하지 않은 패키지만 골라내어 스캔을 수행하고 맵을 업데이트한다.
         *
         * @param baseClass         스캔 대상 베이스 클래스
         * @param annotationClass   스캔 대상 애노테이션
         * @param requestedPackages 이번 요청에 포함된 패키지 목록 (이미 유효성 검증됨)
         */
        private void scanMissingPackages(@NonNull Class<?> baseClass,
                @NonNull Class<? extends Annotation> annotationClass,
                @NonNull String[] requestedPackages) {

            var packagesToScan = new HashSet<String>();
            for (var pkg : requestedPackages) {
                if (!isCovered(pkg)) {
                    packagesToScan.add(pkg);
                }
            }

            if (packagesToScan.isEmpty()) {
                return;
            }

            synchronized (this) {
                cleanup(); // GC된 참조 제거

                packagesToScan.removeIf(this::isCovered);
                if (packagesToScan.isEmpty()) {
                    return;
                }

                performScan(baseClass, annotationClass, packagesToScan);
                scannedPackages.addAll(packagesToScan);
            }
        }

        /** A package scanned itself or through a parent package | 직접 또는 상위 패키지로 이미 스캔된 패키지 */
        private boolean isCovered(String pkg) {
            synchronized (scannedPackages) {
                for (var scanned : scannedPackages) {
                    if (pkg.equals(scanned) || pkg.startsWith(scanned + ".")) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * 실제 클래스패스 스캔을 수행하여 valueMap에 데이터를 적재한다.
         */
        private void performScan(@NonNull Class<?> baseClass,
                @NonNull Class<? extends Annotation> annotationClass,
                @NonNull Set<String> packagesToScan) {

            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(baseClass));

            try {
                var valueMethod = annotationClass.getMethod("value");

                for (var basePackage : packagesToScan) {
                    if (basePackage == null || basePackage.isBlank()) {
                        continue;
                    }
                    for (var bd : scanner.findCandidateComponents(basePackage)) {
                        // Load without running static initializers, through the application's class loader
                        // | 정적 초기화 없이, 애플리케이션 클래스 로더로 로드
                        var clazz = ClassUtils.forName(bd.getBeanClassName(), classLoaderFor(baseClass));

                        if (clazz.isAnnotationPresent(annotationClass)) {
                            var annotation = clazz.getAnnotation(annotationClass);
                            var value = valueMethod.invoke(annotation);
                            var valueString = String.valueOf(value);

                            var refs = valueMap.computeIfAbsent(valueString,
                                    k -> Collections.synchronizedList(new ArrayList<>()));
                            synchronized (refs) {
                                // A parent package scanned later finds the same class again | 나중에 스캔한 상위 패키지가 같은 클래스를 다시 찾음
                                if (refs.stream().noneMatch(ref -> ref.get() == clazz)) {
                                    refs.add(new WeakReference<>(clazz, queue));
                                }
                            }
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        annotationClass.getSimpleName() + " annotation must have a 'value()' method.", e);
            } catch (Exception e) {
                throw new RuntimeException("Failed to scan and build map for base class: " + baseClass.getSimpleName()
                        + " and annotation: " + annotationClass.getSimpleName(), e);
            }
        }

        /**
         * GC된 WeakReference를 청소한다.
         */
        private void cleanup() {
            Reference<? extends Class<?>> ref;
            while ((ref = queue.poll()) != null) {
                for (List<WeakReference<Class<?>>> list : valueMap.values()) {
                    list.remove(ref);
                }
            }
        }

        /**
         * 애노테이션 값에 해당하는 클래스 목록을 조회하되, 요청된 패키지 범위 내에 있는 것만 필터링한다.
         *
         * @param annotationValue 찾으려는 애노테이션 값
         * @param scopePackages   현재 요청의 유효 패키지 범위
         * @return 필터링된 클래스 목록 (없으면 빈 리스트)
         */
        private List<Class<?>> findClasses(@NonNull String annotationValue, @NonNull String[] scopePackages) {
            var refs = valueMap.get(annotationValue);
            if (refs == null) {
                return Collections.emptyList();
            }

            var result = new ArrayList<Class<?>>();
            // refs 는 Collections.synchronizedList 로 감싸져 있는데, 이 래퍼는 개별 메서드 호출만
            // 스레드 세이프하고 순회(iteration)는 호출자가 그 리스트 객체에 대해 직접 동기화해야
            // 한다는 게 명세된 계약이다. 동기화 없이 순회하면 동시에 performScan() 이 같은
            // 버킷(refs)에 항목을 추가할 때 ConcurrentModificationException 이 발생할 수 있다.
            synchronized (refs) {
                for (var ref : refs) {
                    var clazz = ref.get();
                    if (clazz == null) {
                        continue;
                    }
                    var classPackage = clazz.getPackage();
                    if (classPackage != null && isPackageIncluded(classPackage.getName(), scopePackages)) {
                        result.add(clazz);
                    }
                }
            }
            return result;
        }

        private boolean isPackageIncluded(String targetPackage, String[] scopePackages) {
            for (String scope : scopePackages) {
                if (targetPackage.equals(scope) || targetPackage.startsWith(scope + ".")) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Scan results stored on the base class itself (ClassValue), keyed weakly by annotation class, so classes and
     * their class loader can be unloaded (e.g. Spring DevTools restarts) | 결과를 베이스 클래스 자체(ClassValue)에 두고 애노테이션
     * 클래스는 약한 키로 보관하여, 클래스와 클래스 로더가 언로드될 수 있게 함 (DevTools 재시작 등)
     */
    private static final ClassValue<Map<Class<? extends Annotation>, AnnotationScopeCache>> FACTORY_CACHE = new ClassValue<>() {
        @Override
        protected Map<Class<? extends Annotation>, AnnotationScopeCache> computeValue(Class<?> type) {
            return Collections.synchronizedMap(new WeakHashMap<>());
        }
    };

    private static ClassLoader classLoaderFor(Class<?> baseClass) {
        var loader = baseClass.getClassLoader();
        // Object and JDK classes have the bootstrap loader: use the application's | 부트스트랩 로더면 애플리케이션 로더 사용
        return loader != null ? loader : ClassUtils.getDefaultClassLoader();
    }

    private S2AnnotationResolver() {
        // private 생성자: 인스턴스화 방지
    }

    // 내부 헬퍼: 패키지 유효성 검사 및 필터링 (null/blank/중복 제거)
    private static String[] validatePackages(String... packages) {
        Set<String> uniquePackages = new HashSet<>();
        for (String pkg : packages) {
            if (pkg != null && !pkg.isBlank()) {
                uniquePackages.add(pkg);
            }
        }
        return uniquePackages.toArray(new String[0]);
    }

    // 내부 헬퍼: fallback 패키지 결정 (baseClass 패키지 null 체크 추가)
    @NonNull
    private static String[] getFallbackPackages(@NonNull Class<?> baseClass) {
        Package pkg = baseClass.getPackage();
        if (pkg == null) {
            throw new IllegalArgumentException("Base class '" + baseClass.getSimpleName()
                    + "' has no package defined. Specify scan packages explicitly.");
        }
        return new String[] { pkg.getName() };
    }

    // 내부 공통 로직: 캐시 조회, 증분 스캔, 범위 필터링을 수행한다.
    private static <T, A extends Annotation> List<Class<?>> resolveInternal(
            @NonNull Class<T> baseClass,
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue,
            @NonNull String[] packagesToScan) {

        AnnotationScopeCache scopeCache = FACTORY_CACHE.get(baseClass).computeIfAbsent(annotationClass,
                k -> new AnnotationScopeCache());

        scopeCache.scanMissingPackages(baseClass, annotationClass, packagesToScan);

        return scopeCache.findClasses(annotationValue, packagesToScan);
    }

    /**
     * 베이스 클래스, 애노테이션 클래스, 그리고 애노테이션의 값을 통해 해당 구체 클래스 타입을 조회한다.
     *
     * <p>
     * 스캔 범위가 명시되지 않았다면, {@code baseClass}가 속한 패키지만 스캔한다.
     * </p>
     *
     * @param <T>             베이스 엔티티 타입
     * @param <A>             애노테이션 타입 (예: DiscriminatorValue.class)
     * @param baseClass       조회하려는 상속 계층의 베이스 클래스
     * @param annotationClass 값 조회를 위한 애노테이션 클래스
     * @param annotationValue 애노테이션의 값과 일치하는 문자열
     * @return 조회된 구체 클래스 타입. 없으면 {@code null}
     * @throws IllegalStateException 해당하는 클래스가 여럿일 때
     *
     *         <pre>{@code
     * // 예시: baseClass의 패키지만 스캔
     * Class<? extends BaseEntity> resolvedClass = S2AnnotationResolver.resolveType(
     *         BaseEntity.class, DiscriminatorValue.class, "TYPE_B");
     *
     * if (resolvedClass != null) {
     *     Class<SubEntity> specificClass = resolvedClass.asSubclass(SubEntity.class);
     *     SubEntity instance = S2Util.createInstance(specificClass);
     *         }
     * }</pre>
     */
    @Nullable
    public static <T, A extends Annotation> Class<? extends T> resolveType(
            @NonNull Class<T> baseClass,
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue) {
        return resolveType(baseClass, annotationClass, annotationValue, false);
    }

    /**
     * 베이스 클래스, 애노테이션 클래스, 그리고 애노테이션의 값을 통해 해당 구체 클래스 타입을 조회한다.
     *
     * <p>
     * {@code ignoreClassLoader}가 {@code true}인 경우, 클래스 로더가 달라도 클래스 이름 기반으로 타입을 검증합니다.
     * </p>
     *
     * @param <T>               베이스 엔티티 타입
     * @param <A>               애노테이션 타입
     * @param baseClass         조회하려는 상속 계층의 베이스 클래스
     * @param annotationClass   값 조회를 위한 애노테이션 클래스
     * @param annotationValue   애노테이션의 값과 일치하는 문자열
     * @param ignoreClassLoader 클래스 로더가 달라도 이름 기반으로 검증할지 여부
     * @return 조회된 구체 클래스 타입. 없으면 {@code null}
     * @throws IllegalStateException 해당하는 클래스가 여럿일 때
     *
     *         <pre>{@code
     * // 예시: baseClass의 패키지만 스캔
     * Class<? extends BaseEntity> resolvedClass = S2AnnotationResolver.resolveType(
     *         BaseEntity.class, DiscriminatorValue.class, "TYPE_B", true);
     * }</pre>
     */
    @Nullable
    public static <T, A extends Annotation> Class<? extends T> resolveType(
            @NonNull Class<T> baseClass,
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue,
            boolean ignoreClassLoader) {

        String[] packagesToScan = getFallbackPackages(baseClass);

        List<Class<?>> candidates = resolveInternal(baseClass, annotationClass, annotationValue, packagesToScan);

        return single(candidates, baseClass, annotationClass, annotationValue, ignoreClassLoader);
    }

    /**
     * 베이스 클래스, 애노테이션 클래스, 그리고 애노테이션의 값을 통해 해당 구체 클래스 타입을 조회한다. (스캔 패키지 명시 버전)
     * <p>
     * 스캔 범위가 명시되지 않았다면, {@code baseClass}가 속한 패키지만 스캔한다.
     * </p>
     *
     * @param <T>             베이스 엔티티 타입
     * @param <A>             애노테이션 타입 (예: DiscriminatorValue.class)
     * @param baseClass       조회하려는 상속 계층의 베이스 클래스
     * @param annotationClass 값 조회를 위한 애노테이션 클래스
     * @param annotationValue 애노테이션의 값과 일치하는 문자열
     * @param scanPackages    스캔 대상 패키지 가변 인자 (예: "com.mycompany.entity", "com.mycompany.domain")
     * @return 조회된 구체 클래스 타입. 없으면 {@code null}
     * @throws IllegalStateException 해당하는 클래스가 여럿일 때
     *
     *         <pre>{@code
     * // 예시: 지정된 패키지 스캔
     * Class<? extends BaseEntity> resolvedClass = S2AnnotationResolver.resolveType(
     *     BaseEntity.class, DiscriminatorValue.class, "TYPE_B", "com.my.pkg");
     *
     * if (resolvedClass != null) {
     *     Class<SubEntity> specificClass = resolvedClass.asSubclass(SubEntity.class);
     *     SubEntity instance = S2Util.createInstance(specificClass);
     *         }
     * }</pre>
     */
    @Nullable
    public static <T, A extends Annotation> Class<? extends T> resolveType(
            @NonNull Class<T> baseClass,
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue,
            String... scanPackages) {
        return resolveType(baseClass, annotationClass, annotationValue, false, scanPackages);
    }

    /**
     * 베이스 클래스, 애노테이션 클래스, 그리고 애노테이션의 값을 통해 해당 구체 클래스 타입을 조회한다. (스캔 패키지 및 클래스 로더 옵션 명시 버전)
     *
     * <p>
     * {@code ignoreClassLoader}가 {@code true}인 경우, 클래스 로더가 달라도 클래스 이름 기반으로 타입을 검증합니다.
     * </p>
     *
     * @param <T>               베이스 엔티티 타입
     * @param <A>               애노테이션 타입
     * @param baseClass         조회하려는 상속 계층의 베이스 클래스
     * @param annotationClass   값 조회를 위한 애노테이션 클래스
     * @param annotationValue   애노테이션의 값과 일치하는 문자열
     * @param ignoreClassLoader 클래스 로더가 달라도 이름 기반으로 검증할지 여부
     * @param scanPackages      스캔 대상 패키지 가변 인자 (예: "com.mycompany.entity", "com.mycompany.domain")
     * @return 조회된 구체 클래스 타입. 없으면 {@code null}
     * @throws IllegalStateException 해당하는 클래스가 여럿일 때
     *
     *         <pre>{@code
     * // 예시: 지정된 패키지 스캔
     * Class<? extends BaseEntity> resolvedClass = S2AnnotationResolver.resolveType(
     *     BaseEntity.class, DiscriminatorValue.class, "TYPE_B", true, "com.my.pkg");
     * }</pre>
     */
    @Nullable
    public static <T, A extends Annotation> Class<? extends T> resolveType(
            @NonNull Class<T> baseClass,
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue,
            boolean ignoreClassLoader,
            String... scanPackages) {

        String[] packagesToScan = validatePackages(scanPackages);
        if (packagesToScan.length == 0) {
            packagesToScan = getFallbackPackages(baseClass);
        }

        List<Class<?>> candidates = resolveInternal(baseClass, annotationClass, annotationValue, packagesToScan);

        return single(candidates, baseClass, annotationClass, annotationValue, ignoreClassLoader);
    }

    /**
     * 애노테이션 값으로 구체 클래스 타입을 조회한다. (베이스 클래스 없음, 스캔 패키지 필수)
     * <p>
     * 상속 관계에 상관없이 명시된 패키지 내 모든 클래스를 탐색한다.
     * </p>
     *
     * @param <A>             애노테이션 타입 (예: Component.class)
     * @param annotationClass 값 조회를 위한 애노테이션 클래스
     * @param annotationValue 애노테이션의 값과 일치하는 문자열
     * @param scanPackages    스캔 대상 패키지 가변 인자 (필수)
     * @return 조회된 구체 클래스 타입. 없으면 {@code null}
     * @throws IllegalStateException 해당하는 클래스가 여럿일 때
     * @throws IllegalArgumentException 스캔 패키지가 설정되지 않은 경우
     *
     *                                  <pre>{@code
     * // 예시: 지정된 패키지 스캔
     * Class<?> resolvedClass = S2AnnotationResolver.resolveType(
     *     ModuleConfig.class, "PAYMENT_GATEWAY", "com.my.config.module");
     * }</pre>
     */
    @Nullable
    public static <A extends Annotation> Class<?> resolveType(
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue,
            String... scanPackages) {

        String[] packagesToScan = validatePackages(scanPackages);
        if (packagesToScan.length == 0) {
            throw new IllegalArgumentException(
                    "Scan packages must be explicitly set when resolving without a base class.");
        }

        return resolveType(Object.class, annotationClass, annotationValue, false, packagesToScan);
    }

    /**
     * 베이스 클래스, 애노테이션 클래스, 그리고 애노테이션의 값을 통해 해당 구체 클래스 타입 목록을 조회한다.
     *
     * <p>
     * 스캔 범위가 명시되지 않았다면, {@code baseClass}가 속한 패키지만 스캔한다.
     * </p>
     *
     * @param <T>             베이스 엔티티 타입
     * @param <A>             애노테이션 타입 (예: DiscriminatorValue.class)
     * @param baseClass       조회하려는 상속 계층의 베이스 클래스
     * @param annotationClass 값 조회를 위한 애노테이션 클래스
     * @param annotationValue 애노테이션의 값과 일치하는 문자열
     * @return 조회된 구체 클래스 타입 목록 (불변)
     *
     *         <pre>{@code
     * // 예시: baseClass의 패키지만 스캔
     * List<Class<? extends BaseEntity>> resolvedClasses = S2AnnotationResolver.resolveTypes(
     *     BaseEntity.class, DiscriminatorValue.class, "TYPE_B");
     * }</pre>
     */
    public static <T, A extends Annotation> List<Class<? extends T>> resolveTypes(
            @NonNull Class<T> baseClass,
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue) {
        return resolveTypes(baseClass, annotationClass, annotationValue, false);
    }

    /**
     * 베이스 클래스, 애노테이션 클래스, 그리고 애노테이션의 값을 통해 해당 구체 클래스 타입 목록을 조회한다.
     *
     * <p>
     * {@code ignoreClassLoader}가 {@code true}인 경우, 클래스 로더가 달라도 클래스 이름 기반으로 타입을 검증합니다.
     * </p>
     *
     * @param <T>               베이스 엔티티 타입
     * @param <A>               애노테이션 타입
     * @param baseClass         조회하려는 상속 계층의 베이스 클래스
     * @param annotationClass   값 조회를 위한 애노테이션 클래스
     * @param annotationValue   애노테이션의 값과 일치하는 문자열
     * @param ignoreClassLoader 클래스 로더가 달라도 이름 기반으로 검증할지 여부
     * @return 조회된 구체 클래스 타입 목록 (불변)
     *
     *         <pre>{@code
     * // 예시: baseClass의 패키지만 스캔
     * List<Class<? extends BaseEntity>> resolvedClasses = S2AnnotationResolver.resolveTypes(
     *     BaseEntity.class, DiscriminatorValue.class, "TYPE_B", true);
     * }</pre>
     */
    public static <T, A extends Annotation> List<Class<? extends T>> resolveTypes(
            @NonNull Class<T> baseClass,
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue,
            boolean ignoreClassLoader) {

        String[] packagesToScan = getFallbackPackages(baseClass);

        List<Class<?>> candidates = resolveInternal(baseClass, annotationClass, annotationValue, packagesToScan);

        List<Class<? extends T>> results = new ArrayList<>();
        for (Class<?> clazz : candidates) {
            Class<? extends T> casted = castToSubclass(clazz, baseClass, ignoreClassLoader);
            if (casted != null) {
                results.add(casted);
            }
        }

        return Collections.unmodifiableList(results);
    }

    /**
     * 베이스 클래스, 애노테이션 클래스, 그리고 애노테이션의 값을 통해 해당 구체 클래스 타입 목록을 조회한다. (스캔 패키지 명시 버전)
     * <p>
     * 스캔 범위가 명시되지 않았다면, {@code baseClass}가 속한 패키지만 스캔한다.
     * </p>
     *
     * @param <T>             베이스 엔티티 타입
     * @param <A>             애노테이션 타입 (예: DiscriminatorValue.class)
     * @param baseClass       조회하려는 상속 계층의 베이스 클래스
     * @param annotationClass 값 조회를 위한 애노테이션 클래스
     * @param annotationValue 애노테이션의 값과 일치하는 문자열
     * @param scanPackages    스캔 대상 패키지 가변 인자 (예: "com.mycompany.entity", "com.mycompany.domain")
     * @return 조회된 구체 클래스 타입 목록 (불변)
     *
     *         <pre>{@code
     * // 예시: 지정된 패키지 스캔
     * List<Class<? extends BaseEntity>> resolvedClasses = S2AnnotationResolver.resolveTypes(
     *     BaseEntity.class, DiscriminatorValue.class, "TYPE_B", "com.my.pkg");
     * }</pre>
     */
    public static <T, A extends Annotation> List<Class<? extends T>> resolveTypes(
            @NonNull Class<T> baseClass,
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue,
            String... scanPackages) {
        return resolveTypes(baseClass, annotationClass, annotationValue, false, scanPackages);
    }

    /**
     * 베이스 클래스, 애노테이션 클래스, 그리고 애노테이션의 값을 통해 해당 구체 클래스 타입 목록을 조회한다. (스캔 패키지 및 클래스 로더 옵션 명시 버전)
     *
     * <p>
     * {@code ignoreClassLoader}가 {@code true}인 경우, 클래스 로더가 달라도 클래스 이름 기반으로 타입을 검증합니다.
     * </p>
     *
     * @param <T>               베이스 엔티티 타입
     * @param <A>               애노테이션 타입
     * @param baseClass         조회하려는 상속 계층의 베이스 클래스
     * @param annotationClass   값 조회를 위한 애노테이션 클래스
     * @param annotationValue   애노테이션의 값과 일치하는 문자열
     * @param ignoreClassLoader 클래스 로더가 달라도 이름 기반으로 검증할지 여부
     * @param scanPackages      스캔 대상 패키지 가변 인자 (예: "com.mycompany.entity", "com.mycompany.domain")
     * @return 조회된 구체 클래스 타입 목록 (불변)
     *
     *         <pre>{@code
     * // 예시: 지정된 패키지 스캔
     * List<Class<? extends BaseEntity>> resolvedClasses = S2AnnotationResolver.resolveTypes(
     *     BaseEntity.class, DiscriminatorValue.class, "TYPE_B", true, "com.my.pkg");
     * }</pre>
     */
    public static <T, A extends Annotation> List<Class<? extends T>> resolveTypes(
            @NonNull Class<T> baseClass,
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue,
            boolean ignoreClassLoader,
            String... scanPackages) {

        String[] packagesToScan = validatePackages(scanPackages);
        if (packagesToScan.length == 0) {
            packagesToScan = getFallbackPackages(baseClass);
        }

        List<Class<?>> candidates = resolveInternal(baseClass, annotationClass, annotationValue, packagesToScan);

        List<Class<? extends T>> results = new ArrayList<>();
        for (Class<?> clazz : candidates) {
            Class<? extends T> casted = castToSubclass(clazz, baseClass, ignoreClassLoader);
            if (casted != null) {
                results.add(casted);
            }
        }

        return Collections.unmodifiableList(results);
    }

    /**
     * 애노테이션 값으로 구체 클래스 타입 목록을 조회한다. (베이스 클래스 없음, 스캔 패키지 필수)
     *
     * <p>
     * 상속 관계에 상관없이 명시된 패키지 내 모든 클래스를 탐색한다.
     * </p>
     *
     * @param <A>             애노테이션 타입 (예: Component.class)
     * @param annotationClass 값 조회를 위한 애노테이션 클래스
     * @param annotationValue 애노테이션의 값과 일치하는 문자열
     * @param scanPackages    스캔 대상 패키지 가변 인자 (필수)
     * @return 조회된 구체 클래스 타입 목록 (불변)
     * @throws IllegalArgumentException 스캔 패키지가 설정되지 않은 경우
     *
     *                                  <pre>{@code
     * // 예시: 지정된 패키지 스캔
     * List<Class<?>> resolvedClasses = S2AnnotationResolver.resolveTypes(
     *     ModuleConfig.class, "PAYMENT_GATEWAY", "com.my.config.module");
     * }</pre>
     */
    public static <A extends Annotation> List<Class<?>> resolveTypes(
            @NonNull Class<A> annotationClass,
            @NonNull String annotationValue,
            String... scanPackages) {

        String[] packagesToScan = validatePackages(scanPackages);
        if (packagesToScan.length == 0) {
            throw new IllegalArgumentException(
                    "Scan packages must be explicitly set when resolving without a base class. This is to prevent full classpath scanning and ensure performance.");
        }

        return resolveTypes(Object.class, annotationClass, annotationValue, packagesToScan);
    }

    /**
     * One match or null; several matches are an error instead of an arbitrary pick | 하나 또는 null. 여럿이면 임의로 고르지 않고 오류
     */
    private static <T> Class<? extends T> single(List<Class<?>> candidates, Class<T> baseClass,
            Class<? extends Annotation> annotationClass, String annotationValue, boolean ignoreClassLoader) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            var names = new ArrayList<String>();
            for (var candidate : candidates) {
                names.add(candidate.getName());
            }
            throw new IllegalStateException("@" + annotationClass.getSimpleName() + "(\"" + annotationValue
                    + "\") 에 해당하는 클래스가 여럿입니다. resolveTypes 를 쓰거나 스캔 패키지를 좁히십시오: " + names);
        }
        return castToSubclass(candidates.get(0), baseClass, ignoreClassLoader);
    }

    /**
     * 후보 클래스를 베이스 클래스로 형변환을 시도한다.
     *
     * <p>
     * 기본적으로{@link Class#isAssignableFrom(Class)}을 통해 검증하며,
     * {@code ignoreClassLoader}가 {@code true}인 경우 클래스 로더가 달라도 이름 기반으로 검증을 수행합니다.
     * </p>
     *
     * @param <T>               베이스 클래스 타입
     * @param candidate         검색된 후보 클래스
     * @param baseClass         대상 베이스 클래스
     * @param ignoreClassLoader 클래스 로더 차이 무시 여부
     * @return 형변환된 클래스 객체. 호환되지 않으면 {@code null}.
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<? extends T> castToSubclass(Class<?> candidate, Class<T> baseClass,
            boolean ignoreClassLoader) {
        try {
            return (Class<? extends T>) candidate.asSubclass(baseClass);
        } catch (ClassCastException e) {
            if (ignoreClassLoader && S2TypeUtil.isAssignableFromByName(candidate, baseClass)) {
                logger.debug(
                        "Class names are identical but ClassLoaders are different. candidate Ldr: {}, baseClass Ldr: {}",
                        candidate.getClassLoader(), baseClass.getClassLoader());
                return (Class<? extends T>) candidate;
            }

            logger.warn(
                    "Incompatible types. candidate '{}' is not a subclass of '{}'.",
                    candidate.getName(), baseClass.getName());
        }
        return null;
    }

    /**
     * 클래스 또는 그 상위 클래스/인터페이스에서 지정된 애노테이션을 찾아 반환합니다.
     * <p>
     * Spring의 {@link AnnotationUtils#findAnnotation(Class, Class)}를 사용하여
     * 상속 계층 구조 전체를 검색하여 가장 가까운 애노테이션을 찾습니다.
     * </p>
     *
     * @param <T>            찾고자 하는 애노테이션의 타입
     * @param clazz          검색을 시작할 클래스 (null이 아니어야 함)
     * @param annotationType 찾고자 하는 애노테이션의 {@code Class} 객체 (null이 아니어야 함)
     * @return 발견된 애노테이션 객체. 클래스 계층 구조에서 애노테이션을 찾지 못한 경우 {@code null}.
     */
    @Nullable
    public static <T extends Annotation> T resolveAnnotation(
            @NonNull Class<?> clazz,
            @NonNull Class<T> annotationType) {
        // AnnotationUtils.findAnnotation은 상속 관계까지 뒤져서 실제 붙어있는 '애노테이션 객체'를 찾아준다.
        return AnnotationUtils.findAnnotation(clazz, annotationType);
    }

    /**
     * 클래스에 지정된 애노테이션을 찾아 그 {@code value()} 값을 문자열로 반환한다.
     * <p>
     * <b>캐싱 적용:</b> 동일한 클래스-애노테이션 조합에 대한 반복 조회 시
     * {@link AnnotationUtils#getValue(Annotation)} 리플렉션 비용을 절감한다.
     * </p>
     * <p>
     * 상속 계층을 따라 애노테이션을 검색한다.
     * </p>
     *
     * @param <T>            애노테이션 타입
     * @param clazz          검색할 클래스
     * @param annotationType 찾고자 하는 애노테이션의 {@code Class} 객체
     * @return 애노테이션의 {@code value()} 값. 애노테이션이나 값이 없으면 {@code null}을 반환.
     */
    @Nullable
    public static <T extends Annotation> String resolveValue(
            @NonNull Class<?> clazz,
            @NonNull Class<T> annotationType) {

        ValueKey key = new ValueKey(clazz, annotationType, String.class);

        return S2Cache.resolve(
                "s2.annotation.values",
                key,
                ValueKey.class,
                String.class,
                1000, // maxSize: 1000 (조정 가능)
                0, // expiryMs: 0 (영구 캐싱, 클래스 불변 가정)
                k -> {
                    @SuppressWarnings("unchecked")
                    Class<T> annType = (Class<T>) k.annotationType; // 안전 캐스팅
                    @SuppressWarnings("null")
                    T annotation = resolveAnnotation(k.clazz, annType);
                    if (annotation == null) {
                        return Optional.empty();
                    }
                    Object value = AnnotationUtils.getValue(annotation);
                    return Optional.ofNullable(value == null ? null : String.valueOf(value));
                }).orElse(null);
    }

    /**
     * 클래스에 지정된 애노테이션을 찾아 그 {@code value()} 값을 원하는 자료형으로 반환한다.
     * <p>
     * <b>캐싱 적용:</b> 동일한 클래스-애노테이션-반환타입 조합에 대한 반복 조회 시
     * {@link AnnotationUtils#getValue(Annotation)} 리플렉션 비용을 절감한다.
     * </p>
     * <p>
     * <b>타입 안전성:</b> 캐시된 원본 값을 {@code returnType}으로 형변환한다.
     * 형변환 실패 시 {@code null}을 반환하고 경고 로그를 기록한다.
     * </p>
     *
     * @param <T>            애노테이션 타입
     * @param <R>            반환받고자 하는 자료형 타입
     * @param clazz          검색할 클래스
     * @param annotationType 찾고자 하는 애노테이션의 {@code Class} 객체
     * @param returnType     반환받을 결과값의 {@code Class} 객체
     * @return 지정된 자료형으로 변환된 애노테이션의 {@code value()} 값. 변환 실패 시 {@code null}
     * @throws ClassCastException 형변환이 불가능한 경우 (로그 기록 후 null 반환)
     */
    @Nullable
    public static <T extends Annotation, R> R resolveValue(
            @NonNull Class<?> clazz,
            @NonNull Class<T> annotationType,
            @NonNull Class<R> returnType) {

        ValueKey key = new ValueKey(clazz, annotationType, returnType);

        return S2Cache.resolve(
                "s2.annotation.values",
                key,
                ValueKey.class,
                returnType,
                1000,
                0,
                k -> {
                    @SuppressWarnings("unchecked")
                    Class<T> annType = (Class<T>) k.annotationType;
                    @SuppressWarnings("null")
                    T annotation = resolveAnnotation(k.clazz, annType);
                    if (annotation == null) {
                        return Optional.empty();
                    }
                    Object value = AnnotationUtils.getValue(annotation);
                    if (value == null) {
                        return Optional.empty();
                    }
                    try {
                        return Optional.of(returnType.cast(value));
                    } catch (ClassCastException e) {
                        logger.warn("Value cast failed for type {} in annotation {} on class {}",
                                returnType.getSimpleName(), annType.getSimpleName(), k.clazz.getName(), e);
                        return Optional.empty();
                    }
                }).orElse(null);
    }

}
