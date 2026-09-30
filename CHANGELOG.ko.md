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
- `S2PdfUtil`이 임시 파일마다 `deleteOnExit`를 호출하지 않습니다(JVM 종료 때까지 모든 경로를 메모리에 보관했음).
