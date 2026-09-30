package io.github.devers2.s2util.spring.fixture;

/** Records static initialization of fixtures without initializing them | 픽스처를 초기화하지 않고 정적 초기화 여부를 기록 */
public final class InitTracker {
    public static volatile boolean dogInitialized;

    private InitTracker() {
    }
}
