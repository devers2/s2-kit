package io.github.devers2.s2util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.github.devers2.s2util.file.FileManager;
import io.github.devers2.s2util.file.impl.S2SftpFileManagerImpl;
import io.github.devers2.s2util.json.S2JsonUtil;
import io.github.devers2.s2util.pagination.S2PaginationInfo;
import io.github.devers2.s2util.pagination.vo.S2SearchVO;
import io.github.devers2.s2util.support.S2EncryptionUtil;
import io.github.devers2.s2util.support.S2HashUtil;

/**
 * The Java examples in README.md / README.ko.md, so they keep compiling against the real API.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * README.md / README.ko.md 의 Java 예제를 그대로 옮겨, 예제가 실제 API 와 어긋나면 컴파일이 실패하도록 합니다. (AOP 예제는 AspectJ 가 테스트
 * 클래스패스에 없어 제외)
 */
class ReadmeExamplesTest {

    record Board(String title) {
    }

    @Test
    void pagination() {
        var searchVO = new S2SearchVO();
        searchVO.setPageNo(2);
        searchVO.setOrderBy("reg_dt desc, title");

        List<Board> list = new ArrayList<>(List.of(new Board("a")));
        long total = 150;
        S2PaginationInfo<Board> page = new S2PaginationInfo<>(searchVO, list, total);
        assertEquals(15, page.getTotalPageCount());
        assertEquals(10, searchVO.getFirstIndex());

        List<Map<String, String>> orderBy = searchVO.getOrderByList(List.of("REG_DT", "TITLE"));
        assertEquals(2, orderBy.size());
    }

    @Test
    void encryptionAndHashing() throws Exception {
        var appEncryptionKey = S2EncryptionUtil.keyToBase64(S2EncryptionUtil.generateKey()); // stands in for APP_ENCRYPTION_KEY
        SecretKey key = S2EncryptionUtil.keyFromBase64(appEncryptionKey);
        String encrypted = S2EncryptionUtil.encrypt("010-1234-5678", key);
        String plain = S2EncryptionUtil.decrypt(encrypted, key);
        assertEquals("010-1234-5678", plain);

        var password = "typed by a person";
        String sealed = S2EncryptionUtil.encrypt("secret text", password);
        assertEquals("secret text", S2EncryptionUtil.decrypt(sealed, password));

        var input = password;
        String stored = S2HashUtil.hash(password);
        if (S2HashUtil.verify(input, stored) && S2HashUtil.needsRehash(stored)) {
            stored = S2HashUtil.hash(input);
        }
        assertTrue(S2HashUtil.verify(input, stored));
    }

    /** Compiled, not run: it would connect to a server | 컴파일만 확인 (실행하면 서버에 접속함) */
    @SuppressWarnings("unused")
    private static void sftp(InputStream inputStream) {
        FileManager sftp = new S2SftpFileManagerImpl("sftp.example.com", 22, "app", "/keys/id_ed25519", null, null,
                null, null, null, "/etc/ssh/known_hosts_sftp", false);
        sftp.writeFile(inputStream, "/upload/2026", "report.pdf");
    }

    @Test
    void json() {
        record MyDto(String name) {
        }
        var myObject = new MyDto("s2");
        String json = S2JsonUtil.toJson(myObject);
        MyDto dto = S2JsonUtil.fromJson(json, MyDto.class);
        assertEquals(myObject, dto);
    }

    @Test
    void readmesContainTheseExamples() throws Exception {
        for (var readme : List.of("README.md", "README.ko.md")) {
            var text = Files.readString(Path.of(readme));
            assertTrue(text.contains("new S2PaginationInfo<>(searchVO, list, total)"), readme);
            assertTrue(text.contains("searchVO.getOrderByList(List.of(\"REG_DT\", \"TITLE\"))"), readme);
            assertTrue(text.contains("S2HashUtil.needsRehash(stored)"), readme);
            assertTrue(text.contains("S2EncryptionUtil.keyFromBase64(System.getenv(\"APP_ENCRYPTION_KEY\"))"), readme);
            assertTrue(text.contains("\"/etc/ssh/known_hosts_sftp\", false)"), readme);
            assertTrue(text.contains("S2JsonUtil.fromJson(json, MyDto.class)"), readme);
            assertFalse(text.contains("getBean("), readme);
            assertFalse(text.contains("setCurrentPageNo"), readme);
        }
    }
}
