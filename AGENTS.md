# S2Kit AI 에이전트 개발 및 검증 절대 원칙 (AGENTS.md)

이 문서는 `s2-kit` 저장소의 코드를 분석, 수정, 생성하는 모든 AI 에이전트가 준수해야 할 **절대 원칙 및 검증 가이드라인**입니다.

---

## 🚨 [CRITICAL RULE] 코드 수정 후 전체 빌드 및 검증 필수 실행

Java 소스 코드, Gradle 설정, 문서를 수정한 후에는 **사용자에게 완료를 보고하거나 커밋/푸시하기 전에 반드시 아래 검증 명령어를 실행하여 전수 통과(100% PASS)를 확인**해야 합니다.

### 필수 실행 명령어
```bash
./gradlew check
```

> ⚠️ 개별 임의 테스트만 실행하고 끝내지 마십시오. 반드시 `./gradlew check`를 통해 모든 단위/통합 테스트가 100% 통과함을 검증해야 합니다.

---

## 1. 버전 관리 및 문서 동기화 원칙

1. **버전 변경**:
   - `build.gradle.kts`의 버전만 변경하십시오.
   - 빌드 플러그인(`S2BuildUtils`)이 `./gradlew test` 실행 시 모든 `README`의 버전 및 의존성 코드 블록을 자동으로 동기화합니다.
2. **s2-util 버전 동기화**:
   - `s2-util`의 버전이 변경되면 `gradle/libs.versions.toml`의 `s2-util` 버전을 반드시 함께 업데이트하십시오.
3. **배포 안전성**:
   - 사용자가 명시적으로 배포를 요청하지 않는 한 임의로 배포 태스크(`publish`, `publishToCentralPortal` 등)를 실행하지 마십시오.
   - 평상시에는 `git commit` 및 `git push`만 수행합니다.

---

## 2. composite build 주의사항

- `settings.gradle.kts`에 `includeBuild("../s2-util")`이 있으므로 로컬의 `s2-util` 소스가 빌드에 직접 반영됩니다.
- `s2-util`의 API가 변경되면 `s2-kit`의 호출 코드를 반드시 함께 수정하십시오.
- `s2-util`에 없는 메서드를 호출하는 실수를 방지하기 위해 **s2-kit 수정 전에 s2-util의 `./gradlew check`를 먼저 통과**시키십시오.

---

## 3. Java 버전 호환성 원칙 (Java 17 Baseline & Concurrency Reuse)

1. **기본 호환성 (Java 17 Baseline)**:
   - 모든 소스코드는 Java 17 바이트코드 타깃(`--release 17`) 호환을 엄격히 준수해야 합니다.
   - 소스코드 내에 Java 21+ 전용 API나 문법을 직접 사용해서는 안 됩니다.
2. **동시성 및 스레드 풀 재사용 원칙**:
   - `s2-kit` 내에서는 독자적인 가상 스레드(Virtual Thread) 생성 로직을 직접 구현하지 않습니다.
   - 비동기/병렬 작업(예: `S2PdfUtil`의 원격 리소스 병렬 프리페치) 시 반드시 `s2-core`의 `S2ThreadUtil.getCommonExecutor()`를 재사용하십시오.
   - 이를 통해 애플리케이션 실행 환경이 Java 21 이상이면 자동으로 가상 스레드를 활용하고, Java 17이면 안전하게 플랫폼 풀로 동작합니다.

---

## 4. 인코딩 절대 원칙: UTF-8 NoBOM

- 저장소의 모든 소스 파일, 설정 파일, 마크다운 문서는 순수 UTF-8 NoBOM(Byte Order Mark 없음)이어야 합니다.

