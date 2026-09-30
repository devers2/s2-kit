# 변경 이력

[English](./CHANGELOG.md) | **한국어**

`s2-support`의 주요 변경 사항을 기록합니다.

## [2.0.0] - 미배포

1.1.5 대비 변경입니다. 하위 호환을 유지하지 않는 **메이저 버전**이므로 올리기 전에 ⚠️ 항목을 확인하십시오.

### ⚠️ 호환성

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

### 보안

- `S2FileUtil.unzipFiles`는 대상 디렉토리 밖을 가리키는 항목(Zip Slip)을 거부하고 항목 수와 해제 크기를 제한합니다(기본 1만 개, 1 GiB,
  제한을 받는 오버로드 추가).
- SFTP `writeFile`/`readFile`/`deleteFile`은 `savePath`를 벗어나는 `saveName`(`../`, 절대 경로)을 거부합니다.
- `S2SearchVO.getOrderByList()`는 식별자(`COLUMN`, `ALIAS.COLUMN`)만 남기며, `getOrderByList(allowedColumns)`는 허용 목록도
  검사합니다. 결과를 MyBatis `${column}`에 그대로 써도 안전합니다.
- `S2PaginationTag`는 `onclick` 안의 `jsParam`을 이스케이프하고 함수 이름이 아닌 `jsFunction`을 거부합니다.
- `S2PdfUtil`: HTML 렌더러가 `data:` URI 만 읽으므로 HTML 에 적힌 URL(`http:`, `file:`, `//host`)을 가져오지 않습니다(SSRF).
  `PdfSource.ofUrl`은 http/https 만 받습니다.
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
- `S2PdfUtil`이 임시 파일마다 `deleteOnExit`를 호출하지 않습니다(JVM 종료 때까지 모든 경로를 메모리에 보관했음).
