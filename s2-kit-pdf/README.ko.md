# s2-kit-pdf — PDF 및 오피스 문서 통합 솔루션 모듈 (s2-kit)

🌐 [English](README.md) | **한국어**

[![Maven Central](https://img.shields.io/maven-central/v/io.github.devers2/s2-kit-pdf?color=brightgreen&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.devers2/s2-kit-pdf)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

> 📦 **[`s2-kit`](../README.ko.md)**의 PDF 및 문서 뷰어 전용 확장 모듈입니다.
> 서버 인프라(Podman 변환기 설치 스크립트)부터 백엔드(PDF 병합/스탬프/변환 엔진), 프론트엔드(웹 PDF 뷰어 컴포넌트)까지 완벽한 올인원 기능을 제공합니다.

---

## 📖 모듈 구성 (Module Architecture)

`s2-kit-pdf` 모듈은 엔드투엔드 문서 처리에 필요한 3가지 영역을 모두 포함합니다:

```
s2-kit-pdf/
├── scripts/                        # 1. 팟맨(Podman) 온/오프라인 변환기 설치 스크립트
│   ├── setup-s2-office-converter.sh
│   └── README.md
├── src/main/java/                  # 2. 백엔드 PDF & 오피스 변환 엔진
│   └── io/github/devers2/s2kit/support/
│       ├── S2PdfUtil.java          # PDF 병합, 워터마크/스탬프, 렌더링
│       ├── S2OfficeConverter.java  # LibreOffice + H2Orestart 오피스/한글 변환
│       └── S2MarkdownUtil.java     # 마크다운 → HTML/PDF 변환
└── src/main/resources/             # 3. 프론트엔드 웹 PDF 뷰어 자원
    └── META-INF/resources/s2-kit/pdf-viewer/
        ├── pdf-viewer.js           # 독립 실행형 웹 뷰어 (React 내장, JSDoc 완비)
        ├── pdf-viewer.css          # 뷰어 스타일시트
        └── pdfjs/                  # Mozilla pdf.js worker, 폰트, wasm 디코더
```

---

## 🚀 빠른 시작 (Quick Start)

### 1. 의존성 추가 (Gradle / Maven)

`s2-kit-pdf`를 선언하면 기반 라이브러리인 `s2-kit`과 `s2-core`가 자동으로 함께 제공됩니다.

**Gradle (Kotlin DSL)**
```kotlin
implementation("io.github.devers2:s2-kit-pdf:1.0.0")
```

**Gradle (Groovy)**
```groovy
implementation 'io.github.devers2:s2-kit-pdf:1.0.0'
```

---

### 2. 프론트엔드 웹 PDF 뷰어 연동 (HTML / Thymeleaf / JSP / Vanilla JS)

JAR 내부에 정적 웹 자원(`/META-INF/resources/s2-kit/pdf-viewer/`)이 패키징되어 있으므로, npm 설치나 별도 빌드 없이 스크립트 태그 1줄로 즉시 사용 가능합니다. 또한 `pdf-viewer.js` 번들 최상단에 풍부한 **JSDoc 및 TypeScript 선언**이 내장되어 있어 VS Code, IntelliJ 등 IDE에서 완벽한 자동 완성을 지원합니다.

#### 기본 연동 예시 (Thymeleaf / JSP / HTML)

```html
<!-- 1. 스타일시트 로드 -->
<link rel="stylesheet" th:href="@{/s2-kit/pdf-viewer/pdf-viewer.css}" href="/s2-kit/pdf-viewer/pdf-viewer.css">

<!-- 2. 뷰어가 렌더링될 대상 컨테이너 -->
<div id="pdf-viewer-container" style="width: 100%; height: 800px;"></div>

<!-- 3. ES 모듈 임포트 및 초기화 -->
<script type="module">
    import { S2PdfViewer } from '/s2-kit/pdf-viewer/pdf-viewer.js';

    // 뷰어 인스턴스 생성 및 마운트
    const viewer = new S2PdfViewer('pdf-viewer-container', {
        src: '/api/v1/files/download?id=123', // 다운로드 URL 또는 Uint8Array / ArrayBuffer
        fileName: '연구노트.pdf',
        config: {
            zoom: 'page-width', // 배율 ('auto' | 'page-fit' | 'page-width' | 1.2 등 숫자)
            lazy: true          // 뷰포트 인접 페이지만 지연 렌더링 (대용량 PDF 최적화)
        },
        // (선택) 시점확인(TSA) / 전자서명 배지 표기
        timestamp: {
            timestamped: true,
            issuedAt: '2026-10-27T15:30:00Z',
            authority: '한국정보인증(KICA)',
            verified: true
        }
    });

    // 이벤트 구독
    viewer.on('load', ({ numPages, src }) => {
        console.log(`문서 로드 완료: 총 ${numPages}페이지`);
    });

    viewer.on('error', ({ error, phase, src }) => {
        console.error(`[${phase}] 에러 발생:`, error);
    });

    // 비동기 준비 대기
    await viewer.ready;

    // 페이지 이동 또는 모달 닫기 시 메모리/Worker 완전 해제 (필수)
    // viewer.destroy();
</script>
```

#### 옵션 레퍼런스 (`S2PdfViewerOptions`)

| 옵션 | 타입 | 기본값 | 설명 |
| :--- | :--- | :--- | :--- |
| `src` **(필수)** | `string \| ArrayBuffer \| Uint8Array` | - | PDF 파일 URL 경로 또는 바이너리 바이트 배열 |
| `fileName` | `string` | `undefined` | 툴바 및 다운로드 시 표시할 원본 파일명 |
| `config.zoom` | `'auto' \| 'page-fit' \| 'page-width' \| number` | `'page-width'` | 초기 확대 배율 (숫자 지정 시 1.0 = 100%, 1.5 = 150%) |
| `config.lazy` | `boolean` | `true` | 화면 뷰포트 인접 페이지만 동적으로 렌더링 (대용량 문서 메모리 절약) |
| `config.scrollMode` | `'vertical' \| 'horizontal' \| 'wrapped'` | `'vertical'` | 문서 스크롤 방향 |
| `timestamp` | `object` | `undefined` | 시점확인(TSA) / 전자서명 진본확인 배지 정보 |
| `timestamp.timestamped` | `boolean` | - | TSA 시점확인 적용 여부 |
| `timestamp.issuedAt` | `string` | - | 서명/시점확인 발급 일시 (ISO-8601 포맷) |
| `timestamp.authority` | `string` | - | 발급 공인인증기관 (예: 한국정보인증, Amano eTiming) |
| `timestamp.verified` | `boolean` | - | 전자서명 유효성 검증 성공 여부 |

#### 주요 메서드 및 수명주기 API

- **`await viewer.ready`**: `Promise<{ numPages: number }>` — 문서 파싱 및 뷰어 준비 완료 시 resolve됩니다.
- **`viewer.on(event, handler)` / `viewer.off(event, handler)`**:
  - `'load'`: `({ numPages, src }) => void` — 문서 로드 완료 시 발생
  - `'error'`: `({ error, phase, src }) => void` — 로드(`'load'`) 또는 렌더링(`'render'`) 실패 시 발생
- **`viewer.destroy()`**: React 루트 언마운트, DOM 리스너 해제, 백그라운드 Mozilla pdf.js worker 스레드를 안전하게 파괴하여 SPA 환경에서의 메모리 누수를 원천 차단합니다.
- **`getViewerInstance(elementId)`**: 컨테이너 엘리먼트 ID로 이미 생성되어 있는 `S2PdfViewer` 인스턴스를 조회합니다.

---

### 3. 백엔드 PDF 병합 및 변환 엔진 (`S2PdfUtil`, `S2OfficeConverter`)

```java
import io.github.devers2.s2kit.support.S2PdfUtil;
import io.github.devers2.s2kit.support.S2PdfUtil.PdfSource;
import io.github.devers2.s2kit.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2kit.support.S2PdfUtil.StampOptions;
import io.github.devers2.s2kit.support.S2OfficeConverter;

// 1. 한글 폰트 설정 (미지정 시 OS 내 나눔고딕, 본고딕 등 시스템 폰트 자동 탐색)
S2PdfUtil.setDefaultFont(Path.of("/usr/share/fonts/truetype/nanum/NanumGothic.ttf"));

// 2. 다중 포맷 PDF 병합 (HTML, PDF, 이미지, 오피스, 한글, 웹 URL)
var options = MergeOptions.create()
        .bookmarks(true)          // 문서별 북마크/목차 자동 생성
        .pageNumbers(true)        // "1 / N" 하단 중앙 페이지 번호 각인
        .title("통합 보고서")
        .imageDpi(200)            // 이미지 해상도 자동 리사이즈 (용량 최적화)
        .cache(true);             // 중간 변환 결과 디스크 캐싱 재사용

try (InputStream pdf = S2PdfUtil.merge(List.of(
        PdfSource.ofHtml("<h1>표지</h1>").title("표지"),
        PdfSource.ofPdf(Path.of("본문.pdf")),
        PdfSource.ofDocument(Path.of("계획서.docx")), // LibreOffice 자동 연동
        PdfSource.ofDocument(Path.of("보고서.hwp")),  // H2Orestart 한글 확장 자동 연동
        PdfSource.ofUrl("https://example.com/chart.png").maxBytes(10_000_000)
), options)) {
    pdf.transferTo(response.getOutputStream());
}

// 3. 동적 워터마크 및 페이지 번호 스탬프
var stampOptions = StampOptions.builder()
        .pageNumber(S2PdfUtil.PageNumber.of("%d / %d")
                .position(S2PdfUtil.Position.BOTTOM_CENTER))
        .addText(S2PdfUtil.Text.of(pageNo -> "연구노트 - 페이지 " + pageNo)
                .position(S2PdfUtil.Position.TOP_CENTER))
        .watermark(S2PdfUtil.Watermark.of(Path.of("logo.png"))
                .width(200).opacity(0.2f))
        .build();

try (InputStream stamped = S2PdfUtil.stamp(pdfStream, stampOptions)) {
    stamped.transferTo(response.getOutputStream());
}

// 4. 웹 에디터용 HTML/이미지 분리 추출
if (S2OfficeConverter.isAvailable()) {
    var imported = S2OfficeConverter.toHtml(Path.of("보고서.hwp"),
            S2OfficeConverter.HtmlOptions.create().maxImageWidth(1600));
    String html = imported.html(imageName -> "/api/v1/files/view?name=" + imageName);
}
```

---

### 4. 서버 변환 엔진 설치 (Podman 온/오프라인 스크립트)

`s2-kit-pdf`의 `scripts/` 디렉터리에 포함된 팟맨 설치기를 사용하면, 서버에 복잡한 수동 설정 없이 LibreOffice(H2Orestart 한글 확장 포함)와 헤드리스 Chromium을 컨테이너 기반으로 즉시 구축할 수 있습니다.

- **온라인 환경 원클릭 설치**:
  ```bash
  cd scripts/
  ./setup-s2-office-converter.sh
  ```
- **폐쇄망용 단일 오프라인 설치 파일 생성 (`--make-offline`)**:
  ```bash
  # 인터넷 연결 환경에서 실행하여 단일 실행 파일 생성
  ./setup-s2-office-converter.sh --make-offline --target el9

  # 폐쇄망 서버로 전달 후 실행
  ./setup-s2-office-converter-offline.sh
  ```

자세한 옵션과 지원 환경은 [scripts/README.md](scripts/README.md)를 참고하세요.

---

## ⚖️ 라이선스 (License)

- `s2-kit-pdf`는 기본적으로 **[Apache License 2.0](LICENSE)**에 따라 배포됩니다.
- HTML-to-PDF 변환은 서버에 설치된 **Chromium 헤드리스(`s2-chrome`)를 기본 렌더러로 사용하여 100% LGPL-Free(Apache 2.0 / BSD)로 동작**합니다.
- 순수 Java 인메모리 폴백 엔진인 OpenHTMLtoPDF(LGPL 2.1)를 사용할 경우의 라이선스 고지 및 소스 교체 안내는 [README-LGPL-2.1-PDF.md](README-LGPL-2.1-PDF.md)를 참고하십시오.
