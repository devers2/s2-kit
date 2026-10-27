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

### 2. 프론트엔드 웹 PDF 뷰어 연동 (HTML / Thymeleaf / Vanilla JS)

JAR 내부에 정적 자원이 패키징되어 있으므로, npm 설치나 별도 빌드 없이 스크립트 태그 1줄로 즉시 사용 가능합니다.

```html
<!-- 1. 스타일시트 로드 -->
<link rel="stylesheet" th:href="@{/s2-kit/pdf-viewer/pdf-viewer.css}">

<!-- 2. 뷰어가 렌더링될 대상 컨테이너 -->
<div id="pdf-viewer-container" style="width: 100%; height: 800px;"></div>

<!-- 3. ES 모듈 임포트 및 초기화 -->
<script type="module" th:inline="javascript">
    import { S2PdfViewer } from /*[[@{/s2-kit/pdf-viewer/pdf-viewer.js}]]*/ '/s2-kit/pdf-viewer/pdf-viewer.js';

    // 뷰어 인스턴스 생성 및 마운트
    const viewer = new S2PdfViewer('pdf-viewer-container', {
        src: '/api/v1/files/download?id=123',
        fileName: '보고서.pdf',
        config: {
            zoom: 'page-width', // 배율 ('auto', 'page-fit', 'page-width', 또는 1.2 등 숫자)
            lazy: true          // 뷰포트 인접 페이지만 렌더링 (대용량 PDF 최적화)
        },
        // (선택) 시점확인 / 전자서명 배지 표기
        timestamp: {
            timestamped: true,
            issuedAt: '2026-10-27T15:30:00Z',
            authority: '한국정보인증(KICA)',
            verified: true
        }
    });

    // 이벤트 구독
    viewer.on('load', ({ numPages }) => {
        console.log(`문서 로드 완료: 총 ${numPages}페이지`);
    });

    viewer.on('error', ({ error, phase }) => {
        console.error(`[${phase}] 에러 발생:`, error);
    });

    // 페이지 언마운트 또는 모달 닫기 시 자원 해제
    // viewer.destroy();
</script>
```

---

### 3. 백엔드 PDF 병합 및 스탬프 (`S2PdfUtil`)

```java
import io.github.devers2.s2kit.support.S2PdfUtil;
import io.github.devers2.s2kit.support.S2PdfUtil.PdfSource;
import io.github.devers2.s2kit.support.S2PdfUtil.MergeOptions;
import io.github.devers2.s2kit.support.S2PdfUtil.StampOptions;

// 1. 다중 포맷 PDF 병합 (HTML, PDF, 이미지, 웹 URL)
var options = MergeOptions.create()
        .bookmarks(true)
        .pageNumbers(true)
        .title("통합 보고서");

try (InputStream pdf = S2PdfUtil.merge(List.of(
        PdfSource.ofHtml("<h1>표지</h1>").title("표지"),
        PdfSource.ofPdf(Path.of("본문.pdf")),
        PdfSource.ofUrl("https://example.com/chart.png").maxBytes(10_000_000)
), options)) {
    pdf.transferTo(response.getOutputStream());
}

// 2. 동적 워터마크 및 페이지 번호 스탬프
var stampOptions = StampOptions.builder()
        .pageNumber(S2PdfUtil.PageNumber.of("%d / %d")
                .position(S2PdfUtil.Position.BOTTOM_CENTER))
        .addText(S2PdfUtil.Text.of(pageNo -> "연구노트 - 페이지 " + pageNo)
                .position(S2PdfUtil.Position.TOP_CENTER))
        .build();

InputStream stamped = S2PdfUtil.stamp(pdfStream, stampOptions);
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
