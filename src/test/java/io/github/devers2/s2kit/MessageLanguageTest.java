package io.github.devers2.s2kit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Keeps the message rule of AGENTS.md (same as s2-util): exception messages are English, and a Korean log message only
 * appears in the {@code S2Util.isKorean()} branch next to its English one | AGENTS.md 의 메시지 규칙(s2-util 과 같음)을 지킨다: 예외
 * 메시지는 영어, 한국어 로그는 영어 로그와 짝을 이루는 {@code S2Util.isKorean()} 분기 안에만
 */
class MessageLanguageTest {

    private static final Path SOURCES = Path.of("src/main/java/io/github/devers2/s2kit");
    private static final Pattern KOREAN = Pattern.compile("[가-힣]");
    private static final Pattern STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern EXCEPTION = Pattern.compile("new\\s+[A-Za-z0-9_.]*(?:Exception|Error)\\s*\\(");
    private static final Pattern LOG = Pattern.compile("logger\\.(?:warn|error|info|debug|trace)\\s*\\(");

    @Test
    void exceptionMessagesAreEnglish() throws IOException {
        var found = new ArrayList<String>();
        forEachCall(EXCEPTION, (file, line, args, source, start) -> {
            if (hasKorean(args)) {
                found.add(file + ":" + line);
            }
        });
        assertTrue(found.isEmpty(), "Korean exception messages (write them in English): " + found);
    }

    @Test
    void koreanLogsSitInTheIsKoreanBranch() throws IOException {
        var found = new ArrayList<String>();
        forEachCall(LOG, (file, line, args, source, start) -> {
            if (hasKorean(args)) {
                // The nearest preceding if must be the S2Util.isKorean() check | 바로 앞의 if 가 S2Util.isKorean() 이어야 함
                var before = source.substring(Math.max(0, start - 300), start);
                var lastIf = before.lastIndexOf("if (");
                if (lastIf < 0 || !before.substring(lastIf).startsWith("if (S2Util.isKorean())")) {
                    found.add(file + ":" + line);
                }
            }
        });
        assertTrue(found.isEmpty(), "Korean logs outside an S2Util.isKorean() branch: " + found);
    }

    private interface CallVisitor {
        void visit(Path file, int line, String args, String source, int start);
    }

    private static boolean hasKorean(String args) {
        var strings = STRING.matcher(args);
        while (strings.find()) {
            if (KOREAN.matcher(strings.group()).find()) {
                return true;
            }
        }
        return false;
    }

    private static void forEachCall(Pattern call, CallVisitor visitor) throws IOException {
        try (var files = Files.walk(SOURCES)) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                var source = withoutComments(Files.readString(file));
                var matcher = call.matcher(source);
                while (matcher.find()) {
                    var end = closingParenthesis(source, matcher.end());
                    var line = (int) source.substring(0, matcher.start()).chars().filter(c -> c == '\n').count() + 1;
                    visitor.visit(file, line, source.substring(matcher.end(), end), source, matcher.start());
                }
            }
        }
    }

    /** Index of the parenthesis closing the call, skipping strings | 문자열을 건너뛰며 호출을 닫는 괄호 위치 */
    private static int closingParenthesis(String source, int from) {
        var depth = 1;
        var inString = false;
        var i = from;
        for (; i < source.length() && depth > 0; i++) {
            var c = source.charAt(i);
            if (c == '"' && source.charAt(i - 1) != '\\') {
                inString = !inString;
            } else if (!inString) {
                depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            }
        }
        return i - 1;
    }

    /** Blanks out comments, keeping line numbers | 줄 번호를 지키며 주석을 지움 */
    private static String withoutComments(String source) {
        var out = new StringBuilder(source.length());
        var i = 0;
        while (i < source.length()) {
            if (source.startsWith("//", i)) {
                var end = source.indexOf('\n', i);
                i = end < 0 ? source.length() : end;
            } else if (source.startsWith("/*", i)) {
                var end = source.indexOf("*/", i + 2);
                var stop = end < 0 ? source.length() : end + 2;
                source.substring(i, stop).chars().filter(c -> c == '\n').forEach(c -> out.append('\n'));
                i = stop;
            } else if (source.charAt(i) == '"') {
                var end = i + 1;
                while (end < source.length() && (source.charAt(end) != '"' || source.charAt(end - 1) == '\\')) {
                    end++;
                }
                out.append(source, i, Math.min(end + 1, source.length()));
                i = end + 1;
            } else {
                out.append(source.charAt(i++));
            }
        }
        return out.toString();
    }
}
