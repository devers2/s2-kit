package io.github.devers2.s2kit.spring;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.devers2.s2kit.spring.fixture.Animal;
import io.github.devers2.s2kit.spring.fixture.Cat;
import io.github.devers2.s2kit.spring.fixture.InitTracker;
import io.github.devers2.s2kit.spring.fixture.Kind;
import io.github.devers2.s2kit.spring.fixture.dup.OtherCat;

/**
 * Annotation value lookup: single match, ambiguity, and loading without initialization.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 애노테이션 값 조회의 단건 일치, 여러 건 일치 오류, 초기화 없는 클래스 로드를 확인합니다.
 */
class S2AnnotationResolverTest {

    private static final String FIXTURE = "io.github.devers2.s2kit.spring.fixture";

    @Test
    void resolvesWithoutRunningStaticInitializers() {
        var type = S2AnnotationResolver.resolveType(Animal.class, Kind.class, "dog");
        assertEquals(FIXTURE + ".Dog", type.getName());
        assertFalse(InitTracker.dogInitialized, "scanning must not run static initializers");
        assertNull(S2AnnotationResolver.resolveType(Animal.class, Kind.class, "bird"));
    }

    @Test
    void severalMatchesAreAnErrorNotAnArbitraryPick() {
        var e = assertThrows(IllegalStateException.class,
                () -> S2AnnotationResolver.resolveType(Animal.class, Kind.class, "cat", FIXTURE));
        assertTrue(e.getMessage().contains(OtherCat.class.getName()) && e.getMessage().contains(Cat.class.getName()),
                e.getMessage());
        assertEquals(2, S2AnnotationResolver.resolveTypes(Animal.class, Kind.class, "cat", FIXTURE).size());
        // A narrower scan scope gives one match | 스캔 범위를 좁히면 하나
        assertEquals(OtherCat.class, S2AnnotationResolver.resolveType(Animal.class, Kind.class, "cat", FIXTURE + ".dup"));
    }
}
