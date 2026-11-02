package io.github.devers2.s2kit.spring.fixture;

@Kind("dog")
public class Dog extends Animal {
    static {
        InitTracker.dogInitialized = true;
    }
}
