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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.devers2.s2util.core.S2Cache;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * S2 프레임워크의 타입 관련 유틸리티 클래스.
 * <p>
 * 이 클래스는 다음과 같은 주요 기능을 제공합니다:
 * <ul>
 * <li><b>타입 캐스팅:</b> 클래스 로더 불일치 상황에서도 이름 기반으로 안전하게 객체를 캐스팅합니다.
 * ({@link #castByName(Object, Class)} 참조)</li>
 * <li><b>타입 검증:</b> 객체가 특정 타입과 호환되는지 클래스 이름으로 확인합니다.
 * ({@link #instanceOfByName(Object, Class)} 참조)</li>
 * <li><b>인스턴스 생성:</b> 리플렉션을 이용하여 클래스 타입 정보로 제네릭 인스턴스를 생성합니다.
 * ({@link #createInstance(Class)} 참조)</li>
 * </ul>
 * <p>
 * 특히 DevTools와 같이 동적으로 클래스가 로드되는 환경에서 발생할 수 있는 {@link ClassCastException}을
 * 방지하는 데 유용합니다. 타입 계층 구조 분석 결과는 {@link S2Cache}를 통해 캐싱되어 성능을 최적화합니다.
 *
 * @author devers2
 * @version 1.1
 * @since 2025. 12. 30.
 */
public class S2TypeUtil {

    private static final S2Logger logger = S2LogManager.getLogger(S2TypeUtil.class);

    /**
     * 타입 계층 구조 확인 결과를 저장하기 위한 캐시 이름
     */
    private static final String TYPE_HIERARCHY_CACHE_NAME = "s2.cache.type_hierarchy";
    private static final String INTERFACE_INFO_CACHE_NAME = "s2.cache.interface_info";
    private static final VarHandle INTERFACE_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Class[].class);

    /**
     * 캐시 키
     *
     * @param candidateName 검사 대상 클래스의 정규화된 이름
     * @param baseName      기준 클래스의 정규화된 이름
     */
    private record TypeKey(String candidateName, String baseName) {
        /**
         * 콤팩트 생성자를 통해 이름 정규화 적용.
         */
        private TypeKey {
            candidateName = normalizeTypeName(candidateName);
            baseName = normalizeTypeName(baseName);
        }
    }

    /**
     * 인터페이스 정보를 담는 내부 레코드
     */
    private record InterfaceInfo(List<String> names, Class<?>[] types) {
    }

    /**
     * 타입 정보를 담는 내부 레코드
     */
    private record TypePair(Type cand, Type bs) {
    }

    private S2TypeUtil() {
        // Prevent instantiation
    }

    /**
     * 지정된 매개변수로 제네릭 인스턴스를 생성한다.
     * <p>
     * DevTools 환경에서의 클래스 로더 불일치 문제를 해결하기 위해 현재 컨텍스트의 ClassLoader를 사용한다.
     * 이 메서드는 주어진 인자들의 런타임 클래스 타입과 정확히 일치하는 생성자를 찾아 객체를 생성합니다.
     * <br>
     * <b>주의:</b> 이 메서드는 상속 관계나 인터페이스를 인자로 받는 생성자를 찾지 못할 수 있으며,
     * 프리미티브 타입(int, double 등)과 null 인자를 처리하는 데 제약이 있습니다.
     *
     * @param resolvedClass 생성할 인스턴스의 클래스 타입
     * @param args          생성자에 전달할 인자 목록
     * @param <T>           생성할 인스턴스의 타입
     * @return 생성된 인스턴스
     * @throws RuntimeException 인자 타입과 일치하는 생성자를 찾지 못하거나 인스턴스 생성에 실패할 경우 발생
     */
    public static <T> T createInstance(Class<? extends T> resolvedClass, Object... args) {
        try {
            // DevTools 환경에서 ClassLoader 불일치 문제를 해결하기 위해 현재 컨텍스트의 ClassLoader로 클래스를 다시 로드한다.
            var contextLoader = Thread.currentThread().getContextClassLoader();
            @SuppressWarnings("unchecked")
            Class<? extends T> typeClass = contextLoader == null ? resolvedClass
                    : (Class<? extends T>) Class.forName(resolvedClass.getName(), true, contextLoader);

            var arguments = args == null ? new Object[0] : args;
            var constructor = findConstructor(typeClass, arguments);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "리플렉션을 통한 인스턴스 생성에 실패했습니다: %s (args: %s)",
                            resolvedClass.getName(), Arrays.toString(args != null ? args : new Object[] { "null" })),
                    e);
        }
    }

    /**
     * Finds a constructor that accepts the arguments (subtypes, boxing and null for reference types), not only one whose
     * parameter types equal the argument classes | 인자 클래스와 정확히 같은 타입뿐 아니라 하위 타입·박싱·null 을 받는 생성자를 찾음
     */
    @SuppressWarnings("unchecked")
    private static <T> Constructor<? extends T> findConstructor(Class<? extends T> type, Object[] args)
            throws NoSuchMethodException {
        Constructor<?> match = null;
        for (var candidate : type.getDeclaredConstructors()) {
            var parameters = candidate.getParameterTypes();
            if (parameters.length != args.length) {
                continue;
            }
            var accepts = true;
            for (int i = 0; i < parameters.length && accepts; i++) {
                var parameter = parameters[i].isPrimitive() ? MethodType.methodType(parameters[i]).wrap().returnType()
                        : parameters[i];
                accepts = args[i] == null ? !parameters[i].isPrimitive() : parameter.isInstance(args[i]);
            }
            if (accepts) {
                if (match != null) {
                    throw new NoSuchMethodException("인자에 맞는 생성자가 여럿입니다: " + type.getName() + " " + Arrays.toString(args));
                }
                match = candidate;
            }
        }
        if (match == null) {
            throw new NoSuchMethodException("인자에 맞는 생성자가 없습니다: " + type.getName() + " " + Arrays.toString(args));
        }
        return (Constructor<? extends T>) match;
    }

    /**
     * 클래스 타입 정보를 기반으로 제네릭 인스턴스를 생성한다.
     * DevTools 환경에서의 ClassLoader 불일치 문제를 해결하기 위해 현재 컨텍스트의 ClassLoader를 사용한다.
     * 이 메서드는 기본 생성자를 사용하여 객체를 생성한다.
     *
     * @param resolvedClass 생성할 인스턴스의 클래스 타입
     * @param <T>           생성할 인스턴스의 타입
     * @return 생성된 인스턴스
     * @throws RuntimeException 인스턴스 생성에 실패할 경우 발생
     */
    public static <T> T createInstance(Class<? extends T> resolvedClass) {
        return createInstance(resolvedClass, (Object[]) null);
    }

    /**
     * 객체를 대상 타입으로 캐스팅한다. 실패 원인이 클래스 로더 차이인지 알려 준다.
     * <p>
     * 이름은 같지만 다른 클래스 로더(예: Spring DevTools 재시작 전후)로 로드된 클래스는 JVM 에서 서로 다른 타입이므로 캐스팅할 수 없다. 예전
     * 구현은 이 경우에도 그대로 돌려줘 호출한 쪽에서 원인을 알기 어려운 {@link ClassCastException}이 났다. 이제 이 자리에서
     * 클래스 로더 차이를 설명하는 {@link TypeMismatchException}을 던진다. 이름만으로 호환 여부를 알고 싶다면
     * {@link #instanceOfByName(Object, Class)}를 쓴다.
     * </p>
     *
     * @param obj       변환할 객체
     * @param typeClass 대상 클래스 타입
     * @param <T>       대상 제네릭 타입
     * @return 캐스팅된 객체 (obj가 null이면 null 반환함)
     * @throws TypeMismatchException 타입이 호환되지 않거나, 이름은 같지만 클래스 로더가 달라 캐스팅할 수 없을 때
     */
    public static <T> T castByName(Object obj, Class<T> typeClass) {
        if (obj == null) {
            return null;
        }
        var target = typeClass.isPrimitive() ? MethodType.methodType(typeClass).wrap().returnType() : typeClass;
        if (target.isInstance(obj)) {
            @SuppressWarnings("unchecked")
            T cast = (T) target.cast(obj);
            return cast;
        }
        if (isAssignableFromByName(obj.getClass(), typeClass)) {
            throw new TypeMismatchException(String.format(
                    "타입 불일치: %s 는 이름상 %s 와 호환되지만 클래스 로더가 달라 캐스팅할 수 없습니다 (객체: %s, 대상: %s). "
                            + "DevTools 재시작 전에 만든 객체를 재사용하고 있지 않은지 확인하십시오.",
                    obj.getClass().getName(), typeClass.getName(), obj.getClass().getClassLoader(),
                    typeClass.getClassLoader()));
        }
        throw new TypeMismatchException(
                String.format("타입 불일치: %s를 %s로 캐스팅할 수 없습니다.", obj.getClass().getName(), typeClass.getName()));
    }

    /**
     * 컬렉션 내의 요소들을 이름 기반으로 검증하여 안전하게 형변환된 리스트로 반환한다.
     *
     * @param collection 변환할 원본 컬렉션
     * @param typeClass  대상 클래스 타입
     * @param <T>        대상 제네릭 타입
     * @return 형변환된 객체들이 담긴 리스트 (원본이 null이면 null 반환함)
     */
    public static <T> List<T> castListByName(Collection<?> collection, Class<T> typeClass) {
        if (collection == null) {
            return null;
        }

        List<T> result = new ArrayList<>(collection.size());
        for (Object obj : collection) {
            result.add(castByName(obj, typeClass));
        }
        return result;
    }

    /**
     * 객체가 대상 클래스 타입과 이름 기반으로 호환되는지 확인한다. (instanceof 대용)
     *
     * @param obj       검사할 객체
     * @param typeClass 목표 클래스 타입
     * @return 호환 가능하면 true 반환함
     */
    public static boolean instanceOfByName(Object obj, Class<?> typeClass) {
        if (obj == null || typeClass == null) {
            return false;
        }
        return isAssignableFromByName(obj.getClass(), typeClass);
    }

    /**
     * 클래스 이름 기반으로 상속 또는 구현 관계인지 확인한다. (isAssignableFrom 대용)
     *
     * <p>
     * 후보 클래스가 기준 클래스와 이름이 같거나 상속/구현했는지 확인.
     * 결과를 S2Cache의 논리적 영역에 캐싱하며, 만료 설정으로 신선도 유지.
     * </p>
     *
     * @param candidate 후보 클래스 (실제 객체 클래스).
     * @param base      기준 클래스 (목표 타입).
     * @return 할당 가능하거나 이름 일치 시 true.
     */
    public static boolean isAssignableFromByName(Class<?> candidate, Class<?> base) {
        if (candidate == null || base == null)
            return false;

        // 배열일 경우 차원 및 컴포넌트 타입 비교 수행함
        if (candidate.isArray() != base.isArray())
            return false;
        if (candidate.isArray()) {
            int candDims = getArrayDimensions(candidate);
            int baseDims = getArrayDimensions(base);
            if (candDims != baseDims)
                return false;
            // 배열 내부 요소 타입으로 재귀 확인함
            return isAssignableFromByName(candidate.getComponentType(), base.getComponentType());
        }

        TypeKey key = new TypeKey(getNormalizedName(candidate), getNormalizedName(base));

        return S2Cache.resolve(
                TYPE_HIERARCHY_CACHE_NAME,
                key,
                TypeKey.class,
                Boolean.class,
                2000,
                7200_000L,
                k -> Optional.of(calculateHierarchy(candidate, k.baseName()))).orElse(false);
    }

    /**
     * 제네릭 타입을 반복(Queue) 방식으로 안전하게 비교한다
     *
     * @param candidate 후보 타입 (예: field.getGenericType()).
     * @param base      기준 타입 (비교 대상).
     * @return 제네릭 호환 시 true.
     */
    public static boolean isAssignableFromByGenericName(Type candidate, Type base) {
        Deque<TypePair> queue = new ArrayDeque<>();
        queue.add(new TypePair(candidate, base));

        while (!queue.isEmpty()) {
            TypePair pair = queue.poll();
            Type cand = pair.cand;
            Type bs = pair.bs;

            // WildcardType 처리 (상/하한 비교)
            if (cand instanceof WildcardType candWild && bs instanceof WildcardType baseWild) {
                if (!Arrays.equals(candWild.getUpperBounds(), baseWild.getUpperBounds()))
                    return false;
                if (!Arrays.equals(candWild.getLowerBounds(), baseWild.getLowerBounds()))
                    return false;
                continue;
            }

            // TypeVariable 처리 (e.g., T extends Number)
            if (cand instanceof TypeVariable<?> candVar && bs instanceof TypeVariable<?> baseVar) {
                if (!candVar.getName().equals(baseVar.getName()))
                    return false;
                Type[] candBounds = candVar.getBounds();
                Type[] baseBounds = baseVar.getBounds();
                if (candBounds.length != baseBounds.length)
                    return false;
                for (int i = 0; i < candBounds.length; i++) {
                    queue.add(new TypePair(candBounds[i], baseBounds[i]));
                }
                continue;
            }

            // GenericArrayType 처리 (e.g., T[])
            if (cand instanceof GenericArrayType candArr && bs instanceof GenericArrayType baseArr) {
                queue.add(new TypePair(candArr.getGenericComponentType(), baseArr.getGenericComponentType()));
                continue;
            }

            // ParameterizedType 처리
            if (cand instanceof ParameterizedType candParam && bs instanceof ParameterizedType baseParam) {
                if (!isAssignableFromByName((Class<?>) candParam.getRawType(), (Class<?>) baseParam.getRawType())) {
                    return false;
                }
                Type[] candArgs = candParam.getActualTypeArguments();
                Type[] baseArgs = baseParam.getActualTypeArguments();
                if (candArgs.length != baseArgs.length)
                    return false;
                for (int i = 0; i < candArgs.length; i++) {
                    queue.add(new TypePair(candArgs[i], baseArgs[i]));
                }
            } else if (cand instanceof Class<?> candClass && bs instanceof Class<?> baseClass) {
                if (!isAssignableFromByName(candClass, baseClass))
                    return false;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 배열의 차원 수를 계산한다.
     *
     * @param clazz 대상 클래스
     */
    private static int getArrayDimensions(Class<?> clazz) {
        int dims = 0;
        while (clazz.isArray()) {
            dims++;
            clazz = clazz.getComponentType();
        }
        return dims;
    }

    /**
     * 리플렉션을 사용하여 실제 계층 구조를 탐색한다.
     *
     * @param candidate  검사할 클래스
     * @param targetName 찾고자 하는 대상 클래스의 이름
     * @return 관계 확인 시 true 반환함
     */
    private static boolean calculateHierarchy(Class<?> candidate, String targetName) {
        try {
            Class<?> current = candidate;
            while (current != null) {
                if (getNormalizedName(current).equals(targetName)) {
                    return true;
                }
                if (hasInterfaceByNameIterative(current, targetName)) {
                    return true;
                }
                current = current.getSuperclass();
            }
            return false;
        } catch (IllegalAccessError | SecurityException e) {
            logger.warn("계층 구조 탐색 중 접근 오류 발생: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 반복문을 사용하여 인터페이스 계층을 안전하게 탐색한다.
     *
     * @param clazz      검사 클래스 또는 인터페이스.
     * @param targetName 목표 인터페이스 이름.
     * @return 일치 시 true.
     */
    private static boolean hasInterfaceByNameIterative(Class<?> clazz, String targetName) {
        Deque<Class<?>> stack = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        stack.push(clazz);

        while (!stack.isEmpty()) {
            Class<?> current = stack.pop();
            if (visited.contains(current))
                continue;
            visited.add(current);

            // 통합된 캐시에서 정보를 한 번에 가져옴 (리플렉션 0회 목표)
            InterfaceInfo info = getInterfaceInfoCached(current);

            List<String> names = info.names();
            Class<?>[] types = info.types();

            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equals(targetName)) {
                    return true;
                }
                // VarHandle을 사용하여 배열 요소에 초고속 접근함
                Class<?> iface = (Class<?>) INTERFACE_ARRAY_HANDLE.get(types, i);
                stack.push(iface);
            }
        }
        return false;
    }

    /**
     * 인터페이스 정보(이름+타입)를 한 번에 캐싱하여 리플렉션을 최소화한다.
     *
     * @param clazz 대상 클래스
     */
    private static InterfaceInfo getInterfaceInfoCached(Class<?> clazz) {
        return S2Cache.resolve(
                INTERFACE_INFO_CACHE_NAME,
                clazz,
                Class.class,
                InterfaceInfo.class,
                5000,
                7200_000L,
                k -> {
                    Class<?>[] ifaces = k.getInterfaces();
                    List<String> names = Arrays.stream(ifaces).map(S2TypeUtil::getNormalizedName).toList();
                    return Optional.of(new InterfaceInfo(names, ifaces));
                }).orElseGet(() -> new InterfaceInfo(List.of(), new Class<?>[0]));
    }

    /**
     * 배열 타입 이름([L...)을 정규화된 이름(String[])으로 변환한다.
     */
    private static String normalizeTypeName(String name) {
        if (name == null || !name.startsWith("[")) {
            return name;
        }

        int dims = 0;
        while (name.charAt(dims) == '[') {
            dims++;
        }

        String component = name.substring(dims);
        String typeName;

        if (component.startsWith("L") && component.endsWith(";")) {
            // 객체 배열: [Ljava/lang/String; -> java.lang.String
            typeName = component.substring(1, component.length() - 1).replace('/', '.');
        } else {
            // 기본 타입 배열: [I -> int, [[B -> byte
            typeName = switch (component.charAt(0)) {
                case 'Z' -> "boolean";
                case 'B' -> "byte";
                case 'C' -> "char";
                case 'D' -> "double";
                case 'F' -> "float";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'S' -> "short";
                case 'V' -> "void"; // 실제로는 발생하기 어렵지만 완전성을 위해 추가
                default -> component;
            };
        }

        return typeName + "[]".repeat(dims);
    }

    /**
     * 클래스 이름을 정규화 (프리미티브/래퍼 변환 포함)
     *
     * @param clazz 대상 클래스
     * @return 정규화된 이름
     */
    private static String getNormalizedName(Class<?> clazz) {
        Class<?> primitive = S2Cache.WRAPPER_TO_PRIMITIVE_MAP.get(clazz);
        if (primitive != null) {
            return normalizeTypeName(primitive.getName());
        }
        // PRIMITIVE_TO_WRAPPER_MAP이 S2Cache에 있으면 반대로도 체크 (코드에 따라 추가)
        return normalizeTypeName(clazz.getName());
    }

    /**
     * 클래스 로더와 관계없이 이름 기반 타입 검증을 통해 두 객체의 값의 크기를 비교한다.
     * <p>
     * S2TypeUtil의 isAssignableFromByName을 활용하여 논리적으로 호환되는 타입일 때만
     * Comparable 인터페이스를 통해 실제 값의 차이를 반환한다. null 은 가장 작은 값으로 본다.
     * </p>
     * <p>
     * 비교할 수 없는 값(호환되지 않는 타입, Comparable 이 아닌 타입)은 예외를 던진다. 예전처럼 0(같음)을 돌려주면 정렬 결과가 조용히 틀어진다.
     * </p>
     *
     * @param v1 첫 번째 객체 (대상값)
     * @param v2 두 번째 객체 (기준값)
     * @return v1이 작으면 음수, 같으면 0, v1이 크면 양수
     * @throws IllegalArgumentException 두 값을 비교할 수 없을 때
     */
    @SuppressWarnings({ "unchecked", "rawtypes" }) // 제네릭 소거로 인한 경고 억제 (이름 기반 비교로 안전함)
    public static int compare(Object v1, Object v2) {
        if (v1 == null && v2 == null)
            return 0;
        if (v1 == null)
            return -1;
        if (v2 == null)
            return 1;

        Class<?> c1 = v1.getClass();
        Class<?> c2 = v2.getClass();

        // 1. 이름 기반 타입 호환성 체크 (클래스 로더 무관)
        if (isAssignableFromByName(c1, c2) || isAssignableFromByName(c2, c1)) {
            // 2. v1 클래스가 Comparable 인터페이스를 이름 기반으로 구현했는지 확인
            if (hasInterfaceByNameIterative(c1, "java.lang.Comparable")) {
                try {
                    Comparable comp = (Comparable) v1;
                    return comp.compareTo(v2);
                } catch (ClassCastException e) {
                    throw incomparable(v1, v2, e);
                }
            }
            // 3. v1이 Comparable 아니면 v2로 대칭 시도 (대칭성 강화)
            else if (hasInterfaceByNameIterative(c2, "java.lang.Comparable")) {
                try {
                    Comparable comp = (Comparable) v2;
                    return -Integer.signum(comp.compareTo(v1)); // 부호 반전으로 v1 기준 반환 (MIN_VALUE 반전 방지)
                } catch (ClassCastException e) {
                    throw incomparable(v1, v2, e);
                }
            }
        }

        throw incomparable(v1, v2, null);
    }

    private static IllegalArgumentException incomparable(Object v1, Object v2, Throwable cause) {
        return new IllegalArgumentException(String.format("비교할 수 없는 값입니다: %s (%s), %s (%s)", v1,
                v1.getClass().getName(), v2, v2.getClass().getName()), cause);
    }

    /**
     * 타입 불일치 시 발생하는 커스텀 예외.
     */
    public static class TypeMismatchException extends RuntimeException {
        private static final long serialVersionUID = 8225219523214522617L;

        public TypeMismatchException(String message) {
            super(message);
        }
    }

}
