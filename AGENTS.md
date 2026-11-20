# s2-kit AI 에이전트 원칙

## 필수: 수정 후 전체 검증
Java·JS·Gradle 설정·문서를 수정했으면 완료 보고·커밋·push 전에 `./gradlew check` 전수 통과를 확인한다(JS 시험 `jsTest` 포함). 개별 테스트만으로 끝내지 않는다.

## 1. 버전·배포
- 버전은 `build.gradle.kts` 만 바꾼다. `./gradlew test` 때 `S2BuildUtils` 가 `README` 의 버전·의존성 블록을 동기화한다.
- `s2-util` 버전이 바뀌면 `gradle/libs.versions.toml` 의 `s2-util` 버전도 함께 바꾼다.
- 사용자가 요청하지 않으면 배포 태스크(`publish`, `publishToCentralPortal` 등)를 실행하지 않는다.
- 평소에는 `develop` 브랜치에서 commit·push 한다. `main` 은 Maven Central 배포본과 같아야 하므로, 사용자가 배포를 요청할 때만 `develop` 을 `main` 에 fast-forward 하고 버전 태그를 단다.

## 2. composite build
- `settings.gradle.kts` 의 `includeBuild("../s2-util")` 로 로컬 `s2-util` 소스가 그대로 빌드에 쓰인다.
- `s2-util` API 가 바뀌면 `s2-kit` 호출 코드도 함께 고친다. s2-kit 을 고치기 전에 `s2-util` 의 `./gradlew check` 를 먼저 통과시킨다.

## 3. Java·JS 버전·동시성
- 기준은 Java 17(`--release 17`). Java 21+ 전용 문법·API 를 직접 쓰지 않는다.
- 배포되는 JS(`src/main/resources` 아래)는 **ES2020 까지**(`es2020.test.mjs` 가 검사). JS 를 고치면 `src/test/js` 시험도 함께 고친다.
- 가상 스레드를 직접 만들지 않는다. 비동기·병렬 작업은 `s2-core` 의 `S2ThreadUtil` 을 쓴다(`getCommonExecutor()`, 동시 수 제한은 `newExecutor(n)`). 지연·주기 실행용 데몬 스케줄러 스레드(예: `S2TransferWatchdog`)만 직접 만든다.

## 4. 인코딩
- 모든 소스·설정·문서는 UTF-8 NoBOM.

## 5. 언어 (문서·주석·커밋)
| 대상 | 언어 |
|---|---|
| `README`·`GUIDE`·`MANUAL`·`CHANGELOG` 등 가이드 문서 | 영문(`*.md`)·한글(`*.ko.md`) 쌍으로 함께 갱신 |
| Javadoc·JSDoc | 영문 설명 → `<p><b>[한국어 설명]</b></p>` → 한글 설명 (기존 형식) |
| 그 밖의 주석(`//`, 메서드 내부 블록 주석, 빌드 스크립트) | 한국어 |
| 커밋 메시지 | 한국어. 접두어(`feat:`, `fix:`, `docs:`, `build:` …)·식별자는 영어 — 예: `fix: 날짜 검증에서 null 처리 누락 수정` |
| 예외 메시지 | 영어(세부 정보 포함). 사용자가 해결할 수 있는 실패에만 `S2KitMessages.key(...)` + `messages(_ko).properties` |
| 로그 | `if (S2Util.isKorean())` 로 한·영 함께, 또는 영어만 (`MessageLanguageTest` 가 검사) |
| 이슈·PR 답변 | 작성자의 언어 |

- 기존 주석·커밋 이력은 번역·정리 목적으로 바꾸지 않는다. 주석은 설명하는 코드가 바뀌었거나 틀렸을 때만 그 부분을 고치고, 고친 주석은 위 원칙의 언어로 쓴다.
