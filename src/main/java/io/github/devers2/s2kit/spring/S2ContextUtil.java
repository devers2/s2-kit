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
package io.github.devers2.s2kit.spring;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import io.github.devers2.s2util.core.S2Util;

/**
 * Spring 프레임워크 컨텍스트 환경에서 다양한 편의 기능을 제공하는 유틸리티 클래스.
 *
 * @author devers2
 * @since 2025. 12. 20
 */
public final class S2ContextUtil {

    private S2ContextUtil() {
        // Prevent instantiation
    }

    /**
     * AOP JoinPoint에서 파라미터명 또는 객체 내부 필드명과 일치하는 값을 추출한다.
     * <p>
     * <b>검색 우선순위:</b>
     * <ol>
     * <li>파라미터 이름과 일치하는 아규먼트 값</li>
     * <li>아규먼트가 객체(VO, Map)일 경우, 해당 객체 내부의 필드 값 ({@link S2Util#getValue(Object, Object)} 활용)</li>
     * </ol>
     * </p>
     *
     * @param joinPoint        AspectJ JoinPoint 객체
     * @param schParameterName 찾고자 하는 파라미터명 또는 VO 내 필드명
     * @return 추출된 값 (없을 경우 null)
     */
    public static Object getJoinPointParameter(final JoinPoint joinPoint, final String schParameterName) {
        return getJoinPointParameter(joinPoint, null, schParameterName);
    }

    /**
     * AOP JoinPoint에서 특정 파라미터 값 또는 파라미터 객체 내부의 필드 값을 추출한다.
     * <p>
     * <b>검색 우선순위:</b>
     * <ol>
     * <li>파라미터 이름과 일치하는 아규먼트 값</li>
     * <li>아규먼트가 객체(VO, Map)일 경우, 해당 객체 내부의 필드 값 ({@link S2Util#getValue(Object, Object)} 활용)</li>
     * </ol>
     * </p>
     *
     * @param <T>              VO 클래스 타입
     * @param joinPoint        AOP JoinPoint 객체
     * @param voClass          검사할 VO 클래스 타입 (null이 아닐 경우, 해당 타입의 객체만 내부 필드를 검색함)
     * @param schParameterName 찾고자 하는 파라미터 또는 필드명
     * @return 찾은 값 (없으면 null)
     */
    public static <T> Object getJoinPointParameter(final JoinPoint joinPoint, Class<T> voClass,
            final String schParameterName) {
        if (joinPoint == null || schParameterName == null) {
            return null;
        }

        final var signature = (MethodSignature) joinPoint.getSignature();
        final var parameterNames = signature.getParameterNames();
        final var arguments = joinPoint.getArgs();

        if (parameterNames == null || arguments == null) {
            return null;
        }

        // 우선순위를 지키기 위해 2-pass 로 검색한다: 파라미터별로 "이름 일치 -> 딥서치"를 번갈아
        // 처리하면 뒤쪽 파라미터의 이름 일치보다 앞쪽 파라미터의 딥서치가 먼저 걸려버릴 수 있다.
        // (예: method(Foo foo, String userId) 에서 "userId" 검색 시, foo 내부의 userId 필드가
        // 실제 userId 파라미터보다 먼저 반환되면 안 된다.)

        // 1. 파라미터 이름 자체가 찾으려는 이름과 같은 아규먼트를 전체 파라미터에서 먼저 찾는다 (최우선)
        for (var i = 0; i < parameterNames.length; i++) {
            if (schParameterName.equals(parameterNames[i]) && S2Util.isNotEmpty(arguments[i])) {
                return arguments[i];
            }
        }

        // 2. 이름 일치가 없으면, 객체 내부 필드 검색 (Deep Search)
        // argument가 null이 아니고, voClass 제약이 없거나 해당 타입인 경우에만 검색
        for (var i = 0; i < parameterNames.length; i++) {
            var argument = arguments[i];
            if (argument != null && (voClass == null || voClass.isInstance(argument))) {
                // 캐싱된 MethodHandle을 사용하는 getValue로 핀포인트 조회
                var deepValue = S2Util.getValue(argument, schParameterName);
                if (S2Util.isNotEmpty(deepValue)) {
                    return deepValue;
                }
            }
        }

        return null;
    }

}
