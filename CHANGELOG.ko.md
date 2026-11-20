# 변경 이력

[English](./CHANGELOG.md) | **한국어**

`s2-kit`(이전 이름 `s2-support`)의 주요 변경 사항을 기록합니다. `s2-support` 1.x 의 기록도 이 파일에 이어서 둡니다.

## [1.3.0]

### ⚠️ 호환성

- ⚠️ **예외 메시지가 영어입니다**(로그는 s2-util 처럼 JVM 로케일을 따름). 메시지를 비교하지 말고 `S2FileExistsException` 같은
  예외 종류로 확인하십시오.
- ⚠️ **SFTP 풀 기본값이 작아졌습니다**(최대 연결 128 → 64, 유지 연결 16 → 8). Spring 버전은 SSH 연결 하나를 함께 쓰지 않고
  풀의 세션마다 자기 연결을 가집니다. 큰 서버는 값을 지정하십시오(`SFTP-GUIDE.ko.md`).
- ⚠️ **JS 결함 수정으로 결과가 달라질 수 있습니다**: `S2Util.jsonToQueryString`이 값을 인코딩하고 `0`/`false`를 보내며,
  `queryStringToJson`은 인코딩을 풀고, `S2Date`는 개월 계산을 월말로 맞추고 빠진 일을 1일로 읽으며, `validate`의
  `minValue`가 `0`·음수·빈 값을 바르게 다룹니다. 스크립트는 ES2020 브라우저가 필요합니다(README).
- ⚠️ **`S2PdfUtil.stamp`가 기본 폰트를 쓰고**, 폰트로 그릴 수 없는 텍스트와 읽지 못한 이미지는 조용히 빼지 않고 예외를
  던집니다.

### 추가

- 사용자에게 보일 수 있는 실패의 문구를 요청 언어(영어·한국어)로: 이미 있는 파일, 없는 파일, 전송 실패, 바쁜 파일 서버, 가득
  차거나 닫힌 업로드 큐, 너무 큰 업로드, 디스크 부족, 읽을 수 없거나 너무 큰 이미지, 암호·보안 제한이 있는 PDF. s2-util
  `S2Localizable`(`S2Localizable.localizedMessageOrDefault(e, locale)`)을 쓰며 예외 메시지는 영어로 둡니다. 새 예외 종류는 기존 종류를
  유지합니다: `S2UploadRejectedException`은 `RejectedExecutionException`, `S2LocalizedIOException`은 `IOException`.
- `SpringSftpFileManagerImpl`이 `sftp.download-idle-timeout-millis`(기본 60초) 동안 읽지 않은 다운로드 스트림을 닫고 세션을
  반납합니다(`S2SftpFileManagerImpl`과 같음). 예전에는 호출 측이 닫지 않은 스트림이 35분 동안 세션을 붙잡았습니다.
- JS: `S2Util.loginUrl`(401 응답 때 이동할 주소, 기본 `/login`), `maxByte`의 계산 방식 `S2Util.byteCharset`와 `byteCharset`
  속성(기본 `EUC-KR`, `UTF-8`이면 한글 3바이트), `S2Util.byteLength`/`truncateBytes`.
- `src/test/js`의 JS 시험을 `./gradlew check`가 `node --test`로 실행합니다(node 18 이상이 없으면 경고 후 건너뜀, CI 에서는
  필수). 그중 하나는 스크립트가 ES2020 을 넘지 않는지 확인합니다. README 에 지원 브라우저를 적었습니다.
- `S2UploadQueue`: 어떤 `FileManager`로든 정해진 동시 전송 수만큼 백그라운드에서 올립니다. 데이터를 임시 파일에 받아 두고
  `CompletableFuture`를 바로 돌려주며, 가득 차면 기다리지 않고 거절하고, 일시적 실패는 점점 늘어나는 무작위 간격으로 다시 시도하고,
  임시 파일용 디스크 여유 공간을 지킵니다.
- 이어 올리기: `FileManager.writeFile(Path, savePath, saveName, overwrite, resumeKey)`가 숨김 파일 `.<이름>.<키>.s2part`에 쓰고
  다 받으면 이름을 바꿉니다. 실패한 뒤 같은 키로 다시 부르면 SFTP 관리자는 나머지만 보냅니다. `discardPartial`은 부분 파일을
  지웁니다. `S2UploadQueue`가 이 방식으로 재시도하므로 300MB 중 250MB 에서 끊긴 업로드는 나머지 50MB 만 보냅니다. 다른 관리자는
  그냥 씁니다.
- `SFTP-GUIDE.ko.md`: SFTP 설정별 역할, 증상별로 조절할 값, 서버 규모별 출발점.
- `S2FileExistsException`(`S2RuntimeException` 하위): 덮어쓰지 않는 `FileManager.writeFile`에서 파일이 이미 있으면 모든 구현이
  던집니다.

### 변경

- ⚠️ 예외 메시지를 s2-util 처럼 영어로 바꿨습니다(예: `이미 존재하는 파일입니다: ...` → `File already exists: ...`). 메시지를
  문자열로 비교하던 코드는 예외 종류(`S2FileExistsException`)로 확인하십시오. 로그는 역시 s2-util 처럼 JVM 기본 로케일이
  한국어면 한국어, 아니면 영어로 남깁니다(`S2Util.isKorean()`).
- JS: `initializeS2DomEvents`의 전역 클릭 처리를 지웠습니다. 클릭 처리가 끝난 뒤 `preventDefault()`를 불러 아무 효과가
  없었습니다.
- ⚠️ SFTP 풀 기본값을 일반 파일 서버(4 vCPU / 8GB / 1Gbps, 앱 서버 2대, OpenSSH 기본 설정) 기준으로 맞췄습니다. 최대 연결
  128 → 64, 유지 연결 16 → 8(`S2SftpFileManagerImpl`), Spring 은 `sftp.pool.max-total` 128 → 64(`min-idle` 8, `max-idle` 32 는
  그대로). 다른 서버는 값을 지정하십시오(`SFTP-GUIDE.ko.md` 참고).

### 수정

- `s2.date.js`(와 이를 불러오는 `s2.util.js`)가 Safari 13.1 에서 읽히지 않던 문제. `S2Date`가 ES2022 문법인 클래스 필드를
  선언했습니다. 이제 생성자에서 만들고, ES2020 시험이 클래스 필드도 거부합니다.
- `S2Util.alert`/`confirm`을 겹쳐 띄우면 Enter·Esc 로 아래 창이 먼저 닫히던 문제. 이제 맨 위 창만 반응합니다.
- `S2Util.validate`의 `minValue`가 `0`이면 검사하지 않고, `-5`를 `5`로 읽고, 비어 있는 선택 입력을 오류로 보고, 숫자가 아닌 값을
  통과시키던 문제.
- ⚠️ `S2Util.jsonToQueryString`(`S2Util.fetch` GET 요청과 `downloadFile`이 사용)이 키·값을 인코딩하지 않던 문제. `A&B`가 다른
  매개변수로 쪼개지고 `#`에서 URL 이 잘리며 `+`가 공백이 됐습니다. 이제 인코딩하고, `0`·`false`를 보내고, 주어진 URL 의 `=`가 든
  값을 지키고, `#해시`는 맨 끝에 둡니다. `queryStringToJson`은 인코딩을 풀고, 앞의 `?`를 받으며, 첫 `=`에서만 나눕니다.
- ⚠️ `S2Date.add`/`subtract`가 개월·연을 더할 때 월말을 넘기던 문제(`2025-03-31`에서 1개월을 빼면 `2025-03-03`, `2025-01-31`에
  1개월을 더하면 `2025-03-03`). 이제 dayjs 처럼 그 달의 마지막 날로 맞추고, 개월·연 단위 `diff`도 따릅니다. 일이 없는
  형식(`YYYYMM`)을 파싱하면 오늘 날짜를 써서 29~31일에는 `202502`가 잘못된 날짜가 되던 문제(`S2Util.validate`도 거부)도
  고쳐, 빠진 일은 1일, 연도만 있으면 빠진 월은 1월로 둡니다.
- `S2Util.fetch`가 s2-kit 서버가 보낸 한글 파일명을 밑줄로 저장하던 문제(ASCII `filename=`을 `filename*=UTF-8''`보다 먼저
  읽음). 새 `S2Util.getContentDispositionFilename`이 `filename*`을 먼저 읽습니다.
- `initializeS2DomEvents`가 `[sort-nm]` 정렬 헤더가 있는 페이지에서 예외로 멈추던 문제(`querySelector('> ...')`는 잘못된 선택자).
- `S2Util.goPage`/`prepareActionForm`이 `0`·`false` 값을 빼고, 폼 속성과 이름이 같은 매개변수(`action`, `name`, `target`,
  `id`)를 잃고, `submit`이라는 매개변수가 있으면 실패하던 문제.
- `S2Util.template`이 키마다 차례로 치환해, `{{-다른키}}`가 든 값이 다른 필드의 이스케이프하지 않은 값으로 바뀌던 문제. 이제 한
  번에 치환합니다.
- `S2Util.showModal`이 앞 모달을 닫은 뒤 열린 모달과 같은 id 를 쓸 수 있던 문제, `transitionend`가 오지 않으면 토스트가 남던
  문제, `getLocalStorage`의 쿠키 대체 경로가 인코딩된 값을 돌려주던 문제, `validate`가 체크박스마다 같은 메시지를 반복하고 4자
  이상 도메인과 `name+tag` 주소를 거부하던 문제.
- `showS2Loading`이 페이지의 첫 `.progress-bar`(Bootstrap 것 등)를 바꾸고 Bootstrap 이 없으면 빈 바를 보이던 문제,
  `S2DropZone.disable()` 상태에서 떨어뜨린 파일을 브라우저가 열어 페이지를 벗어나던 문제.
- ⚠️ `S2PdfUtil.stamp`(와 `addPageNumbers`)가 기본 폰트를 쓰지 않던 문제. 요소마다 폰트를 지정하지 않으면 텍스트·쪽 번호·접두사를
  Helvetica 로 그려, `setDefaultFont`나 설치된 한글 폰트가 있어도 한글 텍스트가 (같은 줄의 영문까지) 조용히 빠졌습니다. 이제 기본 폰트를
  쓰고, 폰트로 그릴 수 없는 텍스트는 빠뜨리지 않고 `IOException`으로 실패합니다.
- ⚠️ `S2PdfUtil.stamp`가 읽지 못한 이미지·QR 스탬프를 로그만 남기고 빼던 문제. 이제 워터마크처럼 `IOException`을 던집니다.
- `S2PdfUtil.Text.repeat`의 간격이 아주 작으면(약 0.00001pt 미만) 끝나지 않던 문제. 간격은 최소 1pt 로 둡니다.
- `S2FileUtil.copy`·`move`가 대상이 아직 없으면(새 경로로 복사·이동) `NoSuchFileException`으로 실패하던 문제.
- `S2TypeUtil.compare`가 enum, `java.sql.Timestamp`처럼 `Comparable`을 상위 클래스에서 물려받은 값을 비교하지 못하던 문제.
- `S2TypeUtil.createInstance`가 스레드 컨텍스트 클래스 로더에서 보이지 않는 클래스면 실패하던 문제(이제 넘겨받은 클래스를 씁니다).
- SFTP `writeFile`(`S2SftpFileManagerImpl`, `SpringSftpFileManagerImpl`)이 `saveName`에 하위 폴더가 있으면(`a/b.txt`) 실패하던
  문제(`savePath`만 만들었음). 상대 `savePath`면 폴더를 접속 계정의 홈이 아니라 `/` 아래에 만들던 문제도 함께 고쳐, 이제 대상 파일의
  폴더를 만듭니다.
- SFTP `writeFile`이 새 업로드가 중간에 실패하면 불완전한 파일을 남겨, 다시 올릴 때 "이미 존재"로 거부되던 문제. 이제 로컬 관리자처럼
  지웁니다.
- `S2SftpFileManagerImpl` 다운로드 스트림을 유휴 타이머와 호출 측이 동시에 닫으면 세션을 두 번 반납해, 다른 스레드가 쓰는 세션이 풀에
  다시 들어갈 수 있던 문제.
- `JschSessionFactory`의 연결 제한 시간·연결 유지 설정이 적용되지 않던 문제(JSch 는 그 키를 OpenSSH 설정 파일에서만 읽음). 응답 없는
  호스트에 연결하면 끝없이 기다릴 수 있었습니다. 풀을 재초기화할 때 최소 유휴 세션 수 대신 1개만 채우던 문제도 고쳤습니다.
- ⚠️ `SpringSftpConfig`가 공유 세션을 써서, 풀의 `SftpSession`이 모두 같은 SFTP 클라이언트를 감싸던 문제. 모든 전송이 채널
  하나로 몰리고, 그 연결이 끊기면 진행 중인 전송이 한꺼번에 실패했습니다. 이제 풀의 세션마다 자기 SSH 연결을 가집니다. 연결·인증
  제한 시간(`sftp.timeout-millis`, 30초)과 죽은 연결을 끊는 연결 유지 확인도 설정하며, 고정 10초였던 대기는
  `sftp.pool.max-wait-millis`로 바꿀 수 있습니다.
- ⚠️ `JschSessionFactory`가 모든 세션이 사용 중이라 대여 시간이 다 되면 풀을 통째로 새로 만들어, 부하가 몰릴 때 옛 연결을 쓰는
  중에 `sessionMaxTotal`개를 또 열던 문제. 이제 풀이 바쁘면 호출 측이 기다렸다가 실패하고, 세션이 남는데도 빌리지 못할 때만
  풀을 다시 만듭니다. `close()` 뒤의 호출은 풀을 다시 열지 않고 바로 실패하며, 기다리는 중의 인터럽트도 삼키지 않습니다.
- 두 SFTP 관리자 모두 신규 연결을 동시에 최대 4개만 엽니다(`S2SftpFileManagerImpl` 생성자 인자 `maxConcurrentConnects` 또는
  `-Ds2.sftp.maxConcurrentConnects`, Spring `sftp.pool.max-concurrent-connects`). 요청이 몰리거나 콜드스타트여도 OpenSSH `MaxStartups`에 걸리지 않고, 재시도는 한꺼번에
  몰리지 않도록 0.5~1.5초 사이에서 흩어 기다립니다.
- 여러 요청이 동시에 같은 새 폴더로 SFTP 업로드하면, 폴더를 함께 만들다 실패하던 문제.
- `S2SftpFileManagerImpl`이 업로드·다운로드마다 `Timer` 스레드를 만들지 않고, 공용 감시 스레드 하나로 점검합니다.
- 두 SFTP 관리자 모두 만든 직후 백그라운드에서 최소 유휴 세션을 미리 엽니다(예전에는 약 1분 뒤 첫 풀 정비 때 열림).
- `S2RestApiUtil.callApi` GET 이 쿼리 값의 `+`를 인코딩하지 않아 서버가 공백으로 읽던 문제(`a+b` → `a b`). 이제 값을 모두 인코딩합니다.
- `S2PaginationTag`가 `pageNo`가 마지막 페이지를 넘으면(행이 지워진 뒤 등) 페이지 번호를 하나도 그리지 않던 문제. 이제 마지막 페이지가
  있는 목록을 보여 줍니다. `S2PaginationInfo`가 전체 건수 컬럼을 `int`로 읽던 문제(`Integer.MAX_VALUE`를 넘으면 0)와
  `getRemainRecordCount`가 큰 페이지 번호에서 넘치던 문제도 고쳤습니다.
- `S2AnnotationResolver.resolveValue(clazz, type)`와 `resolveValue(clazz, type, String.class)`가 캐시를 함께 써서, String 이 아닌 값은
  먼저 부른 쪽 결과가 다른 쪽에 나오던 문제(`null` 또는 `"3"`).
- `S2FileUtil.getExtension(name, true)`, `S2ServletUtil.isMobile`이 기본 로케일로 소문자를 만들던 문제(터키어에서 `GIF`가 `gıf`).
- `S2ServletUtil.getValueAll`이 같은 객체가 두 번 나오면(예: 리스트에 같은 맵 두 개) 두 번째를 건너뛰던 문제. 이제 실제 순환만 건너뜁니다.
- `S2FileUtil.detectExtension`이 항목 경로 중간에 `word/`, `xl/`, `ppt/`, `version.xml`이 있는 zip(예: `keyword/a.txt`)을
  오피스·HWPX 로 판별하던 문제. 이제 루트 항목만 봅니다.

### 의존성

- `s2-core` 2.1.0 → 2.2.0 (필수: 사용자용 문구가 2.2.0 에 새로 생긴 `S2Localizable`을 쓰고, `S2UploadQueue`가 2.2.0 에서 고친
  `S2ThreadUtil.newExecutor`의 가상 스레드 동시 실행 제한에 기댐). 이 버전보다 s2-util 2.2.0 을 먼저 배포해야 합니다.
- Apache PDFBox 3.0.4 → 3.0.8 (보안·버그 수정 패치), AspectJ Weaver 1.9.25.1 → 1.9.26, lz4-java 1.12.0 → 1.12.1,
  Caffeine 3.2.3 → 3.3.1, jackson-databind 3.0.3 → 3.2.4. `S2PdfUtil`을 쓰는 앱은 PDFBox 3.0.8 을 권장합니다 (3.0.x 는 그대로 동작).

## [1.2.0]

### ⚠️ 호환성

- ⚠️ **`S2PdfUtil`이 PDF 후처리 도구로 `s2-kit`에 돌아왔습니다.** 텍스트·쪽 번호·이미지·QR 스탬프와 워터마크(`stamp`,
  `StampOptions`, `PageSelector`), 쪽 번호(`addPageNumbers`), PDF·이미지 병합(`mergePdfs`, `mergePdfFiles`, `mergePdfPaths`,
  `mergeImagesToPdf`, `mergeImageFiles`, `mergeImagePaths`), 문서별 책갈피(`addBookmark`), 쪽 수 조회와 쪽 이미지 변환을
  제공합니다. Apache PDFBox 만 쓰고 외부 프로그램을 실행하지 않으므로 어떤 도구로 만든 PDF 에도 쓸 수 있습니다.
- ⚠️ **Apache PDFBox 는 선택 의존성입니다.** `S2PdfUtil`을 쓰려면 `org.apache.pdfbox:pdfbox:3.0.4`를 추가합니다. 없으면 처음
  사용할 때 안내 예외가 납니다.
- ⚠️ **문서 변환 기능은 이 프로젝트에서 빠졌습니다.** HTML·텍스트·SVG·마크다운·오피스·한글 문서·URL 을 PDF 로 변환해 병합하는
  기능(`PdfSource`, `MergeOptions`, `merge`, `prepare`, 변환 결과 캐시), `S2OfficeConverter`, `S2MarkdownUtil`, 웹 PDF 뷰어,
  변환기 설치 스크립트를 제거했습니다. `s2-kit-pdf` 모듈은 중단합니다. 마지막 배포 버전(1.1.0)은 Maven Central 에 남지만 더
  갱신하지 않습니다.

### 추가

- 여러 줄 스탬프 텍스트 정렬: `Text`/`PageNumber`의 `.multiLineTextAlign(TextAlign.LEFT | CENTER | RIGHT)`로 각 줄을 가장 긴
  줄 기준으로 맞춥니다(회전한 텍스트 포함).

## [1.1.0]

### 📑 모듈 분리

- PDF·문서 변환(`S2PdfUtil`, `S2OfficeConverter`, `S2MarkdownUtil`), 웹 PDF 뷰어, 변환기 설치 스크립트를 루트 `s2-kit` JAR 에서
  별도 모듈 `s2-kit-pdf`로 옮겼습니다. 루트 JAR 는 더 이상 PDFBox, OpenHTMLtoPDF, CommonMark 에 의존하지 않습니다. (이후 중단,
  1.2.0 참고)

## [1.0.0] - 2026-10-02

`s2-support` 1.1.5 의 후속으로, 이름을 `s2-kit`으로 바꾼 첫 버전입니다. 1.1.5 대비 변경이며 하위 호환을 유지하지 않으므로
올리기 전에 ⚠️ 항목을 확인하십시오. 옮기는 방법은 README 의 "s2-support 에서 옮기기"를 참고하십시오.

### ⚠️ 호환성

- ⚠️ **이름이 `s2-kit`으로 바뀌었습니다.**
  - 좌표: `io.github.devers2.internal:s2-support` → `io.github.devers2.internal:s2-kit` (그룹은 그대로).
  - 패키지: `io.github.devers2.s2util.{support,file,pagination,spring,model}` → `io.github.devers2.s2kit.{...}`.
    `s2-core` 패키지(`io.github.devers2.s2util.core`, `.json`, `.log` 등)는 그대로입니다.
  - 프런트 자원 경로: `/s2-util/js/`, `/s2-util/css/` → `/s2-kit/js/`, `/s2-kit/css/`. 파일 이름(`s2.util.js` 등)과 JS API
    (`S2Util` 등), JSP 태그 URI 는 그대로입니다. `s2.validator.js`는 `s2-validator`에 있으므로 `/s2-util/js/`에 그대로 있습니다.
  - 저장소: `https://github.com/devers2/s2-kit` (옛 주소는 새 주소로 이동합니다).
- **`s2-core` 2.0.0 이 필요합니다.** `s2-support`의 `S2JsonUtil`은 삭제되었습니다. `s2-core`의
  `io.github.devers2.s2util.json.S2JsonUtil`을 사용하십시오.
- ⚠️ **SFTP(`S2SftpFileManagerImpl`, `JschSessionFactory`)가 기본으로 서버 호스트 키를 검증합니다.** `~/.ssh/known_hosts` 또는
  `knownHostsPath`로 지정한 파일에 키가 없는 서버는 연결이 거부됩니다. 키를 등록(`ssh-keyscan -p 22 host >> known_hosts`)하거나,
  신뢰할 수 있는 내부망에서만 `allowUnknownHosts = true`를 지정하십시오. 이전에는 `StrictHostKeyChecking=no`가 고정되어 중간자 공격에
  노출되었습니다.
- ⚠️ **`s2.util.js`가 기본으로 이스케이프합니다.**
  - `S2Util.template`: `{{=key}}`는 HTML 이스케이프한 값을 넣습니다. 신뢰할 수 있는 HTML 은 `{{-key}}`를 쓰십시오.
  - `S2Util.alert`, `confirm`, `showToast`: 문자열 메시지는 텍스트로 표시되며 `\n`은 줄바꿈이 됩니다. HTML 이 필요하면 DOM 노드를
    넘기십시오. 토스트와 모달의 제목도 텍스트입니다.
  - `S2Util.pagination`은 `jsFunction`이 함수 이름이 아니면 예외를 던집니다.
- ⚠️ **실패를 숨기지 않습니다.** 실패 시 `null`, `""`, `-1` 또는 입력값을 돌려주던 다음 메서드가 이제 원인을 담은 예외를 던집니다.
  - `S2EncryptionUtil.decrypt`는 틀린 비밀번호에 암호문을 그대로 돌려줬으나 이제 `GeneralSecurityException`을 던집니다(틀린
    비밀번호·변조 데이터는 `AEADBadTagException`). `encrypt`/`decrypt`는 `GeneralSecurityException`을 선언합니다.
  - `S2StreamUtil.streamToByteArray`(50MB 초과, 입출력 오류), `convertStreamToString`.
  - `S2HashUtil.generateSHA256/512/512To256`, `generateXXHash64`: `null`은 `NullPointerException`, 빈 문자열·공백도 해시합니다(이전엔
    `""` 반환). 없는 파일은 예외.
  - `S2ImageUtil.convertImage`, `imageResize`, `convertImageExtension`, `encodeImageToBase64`.
  - `S2QueryStringUtil`: 잘못된 퍼센트 인코딩은 `IllegalArgumentException`.
  - `S2FileUtil.deleteTemporaryFilesOlderThan`은 접두사가 필수입니다(시스템 임시 디렉토리는 다른 프로그램과 공유).
  - `S2FileUtil` 파일 작업: `streamToFile`, `streamToTempFile`(`-1`/`null` 반환이었음, 실패 시 쓰다 만 파일도 삭제), `processStreamWithTempFile`
    (처리기를 `null`로 호출했음), `makeDirectory`(새로 만들면 `true`, 이미 있으면 `false`), `delete`(삭제하면 `true`, 없으면 `false`,
    지울 수 없는 항목이 있으면 나머지를 지운 뒤 예외), `getSize`, `fileToReader`/`fileToInputStream`(빈 경로). `finally`·Cleaner 의 정리는
    예외 대신 로그를 남기는 새 `deleteQuietly`를 씁니다.
  - `S2PdfUtil`: 한글 폰트가 없는데 한글(CJK)을 쓰면 `#` 대신 예외(한글 폰트를 설치하거나 `setDefaultFont` 호출), 없는 폰트 리소스는 예외,
    `addPageNumbers`는 `null` 대신 예외.
  - 파일 관리자 없이 부르는 `FileManager.downloadRemoteFile`도 `writeFile` 규칙을 따라 덮어쓰지 않습니다.
- **암호화·비밀번호 해시 형식 변경**(`s2v2:` 접두사). `S2EncryptionUtil`은 AES-256-GCM 이라 틀린 비밀번호나 변조된 데이터는 반드시
  실패합니다. `S2HashUtil.hash`는 PBKDF2 310,000 회를 쓰며 반복 횟수를 함께 저장합니다. 1.x 로 만든 값(접두사 없음)도 계속 복호화·검증되며,
  `S2EncryptionUtil.isLegacyFormat`, `S2HashUtil.needsRehash`로 다시 저장할 대상을 알 수 있습니다.
- **`S2AutoConfiguration`을 삭제**했습니다(`META-INF/spring.factories`, `AutoConfiguration.imports` 등록 포함). 로거를 만드는 것 외에 하는
  일이 없었으며 설정할 것은 없습니다.
- ⚠️ **`S2ServletUtil.getClientIp` / `getRealServerName`이 요청 헤더를 믿지 않습니다.** 클라이언트가 위조할 수 없는
  `request.getRemoteAddr()` / `request.getServerName()`을 돌려줍니다. 리버스 프록시 뒤라면 컨테이너가 전달 헤더를 반영하게 하거나(Spring
  Boot `server.forward-headers-strategy=native`, Tomcat `RemoteIpValve`), 신뢰하는 프록시 주소를 받는 새 오버로드
  (`getClientIp(request, Set.of("10.0.0.5"))`)를 쓰십시오. `X-Forwarded-For`를 오른쪽부터 읽어 신뢰하는 프록시만 건너뜁니다.
- ⚠️ **`S2ServletUtil.getPrevServletPath`는 다른 출처·컨텍스트의 Referer 에 `""`를 돌려줍니다**(그대로 돌려줘 리다이렉트에 쓰면 오픈
  리다이렉트였음).
- ⚠️ **임의의 결과 대신 오류:** `S2AnnotationResolver.resolveType`은 여러 클래스가 일치하면 `IllegalStateException`(첫 번째를 골랐음),
  `S2TypeUtil.compare`는 비교할 수 없는 값에 `IllegalArgumentException`(0, 즉 "같음"을 돌려줬음), `S2TypeUtil.castByName`은 클래스 로더
  차이를 설명하는 `TypeMismatchException`(호출한 쪽에서 나중에 `ClassCastException`이 났음), `S2CollectionUtil.listSort`는 ASC/DESC 가
  아닌 `orderBy`에 예외이며 항상 새 목록을 돌려줍니다. `S2LruMap.createSynchronizedLRUMap`은 1 미만 용량을 거부합니다.
- **`S2RestApiUtil.callApi`는 응답 본문을 그대로 돌려줍니다**(유니코드 이스케이프를 풀어 JSON 이 깨졌음). POST 는 파일(`Resource`,
  `byte[]`)이 없으면 `application/x-www-form-urlencoded`로 보내며, 요청 헤더를 받는 오버로드를 추가했습니다.
- `s2.util.js`의 `S2Util.validate(form)`은 폐기 예정입니다. s2-validator 의 `S2Validator`를 쓰십시오.
- 컴파일 기준 Spring 버전: Spring Framework 6.2.19, Spring Integration 6.5.10.
- ⚠️ **`FileManager.writeFile`이 기존 파일을 덮어쓰지 않습니다.** 로컬·Spring SFTP 관리자는 조용히 덮어쓰고 JSch SFTP 관리자는 거부해
  동작이 달랐습니다. 이제 셋 다 `S2RuntimeException`을 던지고 기존 파일은 그대로 둡니다. 덮어쓰려면 새
  `writeFile(data, savePath, saveName, overwrite)`에 `overwrite = true`를 넘기십시오. 직접 만든 `FileManager` 구현은 이 4 인자 메서드를
  구현합니다(3 인자 메서드는 default). 로컬 관리자는 파일을 원자적으로 만들어(`CREATE_NEW`) 같은 이름으로 동시에 써도 하나만 성공하며,
  실패하면 쓰다 만 파일을 지웁니다. 실패 시 `-1` 대신 예외를 던집니다.
- **jsch** 를 유지보수되는 포크 `com.github.mwiede:jsch`로 바꿨습니다(패키지 `com.jcraft.jsch` 동일, rsa-sha2 와 최신 OpenSSH 지원).

### 추가

- 여러 건을 처리하기 위한 `S2EncryptionUtil` 키 방식: `generateKey()`, `keyToBase64`/`keyFromBase64`, `encrypt(text, SecretKey)`/
  `decrypt(text, SecretKey)`(AES-256-GCM, 접두사 `s2k1:<키 이름>:`, 1건 1ms 미만, 1,000건 왕복 약 0.1초). 키 이름은 인증 데이터에 포함되며,
  `KeyRing`으로 서비스를 멈추지 않고 키를 교체합니다. 주 키로 암호화하고, 암호문에 적힌 이름의 키로 복호화하며, `needsReencrypt`/`reencrypt`로
  옛 값을 주 키로 옮깁니다. 단일 키 API 는 키 이름 `default`를 기록하므로 교체 후에도 그대로 읽힙니다. 비밀번호 방식은 사람이 입력한 비밀번호로 가끔
  쓰도록 일부러 느리게(PBKDF2, 1건 약 70ms) 유지합니다. 두 형식은 섞어 쓸 수 없으며, 다른 API 로 복호화하면 맞는 API 를 알려 주는 예외가
  납니다.
- 문서 변환과 여러 소스 병합: `S2PdfUtil.merge`/`prepare`(HTML·텍스트·SVG·마크다운·이미지·LibreOffice 를 통한 오피스·한글
  문서·URL, 책갈피·쪽 번호·변환 결과 캐시·동시 변환), `S2OfficeConverter`, `S2MarkdownUtil`. 1.1.0 에서 `s2-kit-pdf`로 옮겼고
  지금은 이 프로젝트에 없습니다(1.2.0 참고).
- `S2ImageUtil.resizeToFit(bytes, maxWidth, maxHeight)`(비율 유지, 서브샘플링, EXIF 방향 적용, 바꿀 것이 없으면 같은 배열),
  `scaleToFit`, `exifOrientation`, `applyOrientation`, `JPEG_QUALITY`.
- `S2FileUtil` 파일 형식 판별: `detectExtension(Path | byte[])`(내용으로 판별, 확장자 없는 업로드 파일 등), `getExtensionByHint(파일명·확장자·MIME)`,
  `getExtensionByMimeType(String)`, `getMimeTypeByExtension(확장자·파일명)`. MIME 대응표에 png, docx, pptx, md, txt, 한글(hwp·hwpx) 변형 등을
  보완했고, 기존 `getExtensionByMimeType(Path)`·`getContentType(Path)`는 OS 가 판별하지 못하면(확장자 없는 파일) 내용으로 판별합니다.
- 워터마크: `Watermark.of(이미지)...`(당시 `MergeOptions.watermark`, 지금은 `StampOptions.watermark`)로 모든 쪽에 이미지를 찍습니다. 크기는 가로·세로 둘 다(`size`), 가로만
  (`width`, 세로는 비율대로), 세로만(`height`, 가로는 비율대로) 정할 수 있고, 위치는 가운데와 8방향(`Position`), 간격은 `offset(x, y)`
  (가장자리 기준이면 안쪽으로, 가운데 기준인 축은 x 오른쪽·y 아래로), 불투명도는 `opacity`. 단위는 pt. 이미지는 한 번만 넣어 모든 쪽이
  공유하고, PNG 투명도를 유지하며, 쪽 번호는 워터마크 위에 그립니다. 회전된 쪽(`/Rotate` 90·180·270)은 화면에 보이는 쪽 기준으로
  놓고 바로 세웁니다.
- `S2PdfUtil.setDefaultFont(Path)` / `setDefaultFont(Class, String)` / `resetDefaultFont()`. 폰트를 지정하지 않으면 설치된 한글 TrueType 폰트를
  씁니다(`SYSTEM_FONT_CANDIDATES`: 맑은 고딕, 나눔고딕, Noto Sans KR 등).

### 보안

- `S2FileUtil.unzipFiles`는 대상 디렉토리 밖을 가리키는 항목(Zip Slip)을 거부하고 항목 수와 해제 크기를 제한합니다(기본 1만 개, 1 GiB,
  제한을 받는 오버로드 추가).
- SFTP `writeFile`/`readFile`/`deleteFile`은 `savePath`를 벗어나는 `saveName`(`../`, 절대 경로)을 거부합니다.
- `S2SearchVO.getOrderByList()`는 식별자(`COLUMN`, `ALIAS.COLUMN`)만 남기며, `getOrderByList(allowedColumns)`는 허용 목록도
  검사합니다. 결과를 MyBatis `${column}`에 그대로 써도 안전합니다.
- `S2PaginationTag`는 `onclick` 안의 `jsParam`을 이스케이프하고 함수 이름이 아닌 `jsFunction`을 거부합니다.
- `FileManager.downloadRemoteFile`은 http/https 만 받고(`file:`/`jar:` 거부), 연결·읽기 제한 시간을 두며, 2xx 가 아니면 실패하고,
  로컬 저장 경로를 검사하며, 요청을 한 번만 보냅니다(파일 정보는 같은 응답에서 읽음).

### 수정

- `s2fn.tld`, `s2tag.tld`가 존재하지 않는 클래스를 가리켜 JSP 함수와 페이지네이션 태그를 쓸 수 없었습니다. 이제 `s2-core`와
  `S2PaginationTag`를 가리킵니다. 대상 메서드가 없는 `formatter`는 삭제했고 `paginationRecordNo`는 `S2PaginationInfo.getRecordNo`에
  연결했습니다.
- `Content-Disposition` 해석(`S2ServletUtil.getFilenameFromHeader`, `S2RemoteFile`): `filename*`를 `filename`보다 우선하고(RFC 6266),
  `+`를 공백으로 바꾸지 않으며, 따옴표와 디렉토리 부분을 제거합니다. 해석기는 `S2FileUtil.parseContentDispositionFilename`으로
  공개했습니다.
- `S2FileUtil.zipDirectory`는 디렉토리 스트림을 닫고 항목 이름에 항상 `/`를 씁니다. `unzipFiles`는 없는 상위 디렉토리를 만듭니다.
- `S2SearchVO`: 페이지 번호·단위·크기는 최소 1이며 `getFirstIndex`가 음수로 넘치지 않습니다.
- `S2StreamUtil.streamToByteArray(Reader)`가 32K 버퍼 경계에 걸친 BMP 밖 문자(이모지)를 깨뜨리던 문제를 고쳤습니다.
- `S2QueryStringUtil.queryStringFromEntries`가 파라미터 순서, 반복 키, `#fragment`를 유지하며 기존 쿼리의 키를 이중 인코딩하지
  않습니다(`a%5B0%5D` → `a%255B0%255D` 이던 문제).
- `S2ImageUtil`: 비율 무시 모드에서 제한을 넘는 쪽만 줄입니다(최대 너비 200 에 400×100 이미지가 그대로 남던 문제). `TYPE_CUSTOM`
  이미지도 처리하고, PNG 는 투명도를 유지하며 JPG 는 흰 배경으로 합성합니다. 고품질(bicubic) 보간을 쓰고, 1억 픽셀을 넘는 이미지는 디코딩
  전에 거부합니다. 원본 파일은 호출마다 JVM 종료 훅을 추가하던 방식 대신 결과를 쓴 직후 삭제합니다.
- 사진 방향: 휴대폰으로 세로로 찍은 사진(EXIF 방향)이 PDF 와 `S2ImageUtil` 크기 변경 결과에서 옆으로 누워 나왔습니다. 이제 바로 세우며,
  크기를 바꾸지 않는 JPEG 는 다시 압축하지 않고 PDF 에서 돌려 그립니다. `S2ImageUtil`이 JPEG 를 쓸 때 ImageIO 기본 품질(0.75) 대신 0.9 를 씁니다.
- 나눔고딕처럼 제어 문자(U+0000~U+001F)와 공백을 같은 글리프에 연결한 폰트로 만든 PDF 에서, 복사·검색한 공백이 빈 문자(U+0000)가 되던 문제를
  고쳤습니다. 폰트를 불러올 때 제어 문자 연결을 지웁니다(화면 모양은 같음).
- 라이선스: `licenses/NOTICE`의 UUID Creator 가 jsoup 의 라이선스 파일(`LICENSE-MIT`, 저작권자 Jonathan Hedley)을 가리키고 있었습니다.
  UUID Creator 원문(`LICENSE-UUID-CREATOR-MIT`, Fabio Lima)을 추가해 바로잡았습니다. commonmark-java(`LICENSE-COMMONMARK-BSD`)와
  autolink-java(`LICENSE-AUTOLINK-MIT`) 원문을 추가했습니다. 경량 빌드에서 `S2PdfUtil`을 빼면 jsoup 을 쓰는 보조 클래스(`S2HtmlResources` 등)가
  남아 컴파일이 실패하던 문제도 고쳤습니다(기능 묶음에 함께 등록).
- `S2FileUtil.deleteFilesOlderThan`은 접두사가 있으면 디렉토리를 지우지 않으며, 시작 디렉토리는 지우지 않습니다.
- `licenses/NOTICE`가 OpenHTMLtoPDF 를 "LGPL 2.1 / MPL 2.0"으로 적고 있었습니다. 실제로는 LGPL 2.1 이상이며 MPL 2.0 을 쓰는 의존성이 없어
  `LICENSE-MPL-2.0`을 삭제했습니다. NOTICE 에 Spring Web, Spring Integration SFTP, JSR-305 를 추가하고, 컴파일 전용·선택 의존성을 표시하며,
  JSch 포크에 포함된 라이선스(JZlib, jBCrypt)를 명시했습니다.
- `README-LGPL-2.1-PDF.md`가 OpenHTMLToPDF 를 포함한다며 소스 제공을 약속하고 있었으나 s2-support 는 포함하지 않습니다(`compileOnly`).
  애플리케이션이 직접 추가한다는 점과, 애플리케이션이 재배포할 때 적용되는 의무를 적도록 고쳤습니다. `s2.dropzone.js`의 "All right
  reserved" 문구와 `s2.util.css`의 존재하지 않는 모달 헤더 로고(`SEEK_logo.png`) 참조를 삭제했습니다.
- `S2SftpFileManagerImpl`이 `AutoCloseable`을 구현합니다. `close()`로 SSH 세션 풀을 닫습니다(연결과 풀 스레드를 정리할 방법이 없었음).
  SFTP 실패 예외가 원인과 메시지(예: "이미 존재하는 파일", 거부된 호스트 키)를 담습니다.
- `SpringSftpConfig`가 `sftp.private-key-path`를 필수로 요구하고 `sftp.password`를 무시하던 문제를 고쳤습니다. 개인키가 있으면 개인키,
  없으면 비밀번호를 쓰며, 둘 다 없으면 시작 시 실패합니다.
- 두 SFTP 관리자를 내장 SSH 서버(Apache MINA SSHD)로 시험합니다: 전송, 모르는·바뀐 호스트 키 거부, `allowUnknownHosts`, 경로 이탈 차단.
- README 예제가 존재하지 않는 API(`S2ContextUtil.getBean`, `S2PaginationInfo` 세터)를 쓰고 있었습니다. 실제 API 로 바꾸고 시험에서
  컴파일합니다.
- `S2AnnotationResolver`는 스캔한 클래스를 정적 초기화 없이 베이스 클래스의 클래스 로더로 로드합니다. 캐시가 DevTools 재시작 후에도
  클래스(와 클래스 로더)를 붙잡지 않으며, 상위 패키지 다음에 하위 패키지를 스캔해도 같은 클래스가 두 번 나오지 않습니다.
- `S2ServletUtil.getApplicationRootPath`가 HTTP 세션을 만들지 않으며, `getValueAll`은 순환 참조에서 멈춥니다.
- `S2FileUtil.joinPaths`가 호출자의 배열을 바꾸지 않으며, `getApplicationRootPath(Class)`는 jar 안의 클래스에서도
  `NullPointerException` 없이 jar 가 있는 디렉토리를 돌려줍니다.
- `S2TypeUtil.createInstance`가 하위 타입·박싱 값을 받는 생성자도 찾습니다.
- `S2CollectionUtil.listSort`가 `NaN`·무한대를 실패 없이 정렬합니다.
- `S2MarkupUtil.removeTagContent`가 닫히지 않은 태그를 끝까지 제거합니다(`"<script>alert(1)"`이 남았음). HTML 정화기는 아닙니다.
- `s2.util.js`의 `S2Util.options`가 option 값·라벨에 HTML 이스케이프된 텍스트를 넣던 문제(`A&B` → `A&amp;B`)를 고쳤습니다.
- `S2PdfUtil` 이미지: WebP 는 문서에만 있고 지원되지 않았습니다. `com.twelvemonkeys.imageio:imageio-webp`가 있으면 동작하며 없으면 그렇게 알려 줍니다.
  이미지는 한 번만 디코딩하고(JPEG 는 원본 그대로), 1억 픽셀을 넘으면 디코딩 전에 거부합니다. HTML 은 바이트 배열 대신 파일로 바로 렌더링하고,
  HTML·텍스트·SVG 스트림, 스트림 이미지, URL 다운로드에 크기 한도를 둡니다(URL 은 `Content-Length`를 먼저 확인).
- `S2PdfUtil.addPageNumbers`가 오류나 너무 큰 쪽 수에 `null`을 돌려주던 것을 예외로 바꿨습니다. 쪽 번호는 폭에 맞춰 가운데 정렬합니다.
- `S2PdfUtil`이 임시 파일마다 `deleteOnExit`를 호출하지 않습니다(JVM 종료 때까지 모든 경로를 메모리에 보관했음).
