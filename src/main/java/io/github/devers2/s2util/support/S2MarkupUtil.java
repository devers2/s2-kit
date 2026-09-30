/**
 * S2 Support Library
 *
 * Copyright 2020 - 2026 devers2 (이승수, Daejeon, Korea)
 * Contact: eseungsu.dev@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For more information, please see the LICENSE file in the root directory.
 */
package io.github.devers2.s2util.support;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * s2's utilities
 *
 * @author devers2
 * @version 1.0
 * @since 2020. 07. 08.
 */
public class S2MarkupUtil {

    // private static final S2Logger logger = S2LogManager.getLogger(S2MarkupUtil.class);

    private S2MarkupUtil() {
    }

    /**
     * 마크업 문자열에서 특정 태그가 최상위 태그인지 확인한다.
     *
     * @param markupString 마크업 문자열
     * @param tagName      태그
     * @return 최상위 태그인 경우 true
     * @apiNote html 태그(&lt;html&gt;)가 최상위 태그인지 확인
     *
     *          <pre>{@code
     * String htmlString = S2FileUtil.readFile("c:/test.html");
     * boolean isTopLevelTag = S2MarkupUtil.isTopLevelTag(htmlString, "html");
     * }</pre>
     */
    public static boolean isTopLevelTag(String markupString, String tagName) {
        if (markupString == null || markupString.isBlank() || tagName == null || tagName.isBlank()) {
            return false;
        }

        // 태그를 찾는 정규 표현식 (속성을 포함). 태그명은 정규식 메타문자가 섞여 들어와도
        // 리터럴로만 매칭되도록 Pattern.quote 로 이스케이프한다.
        var quotedTagName = Pattern.quote(tagName);
        var tagRegex = "<" + quotedTagName + "\\b[^>]*>|</" + quotedTagName + ">";
        var tagPattern = Pattern.compile(tagRegex, Pattern.CASE_INSENSITIVE);
        var tagMatcher = tagPattern.matcher(markupString);

        int depth = 0;
        int startIndex = -1;
        int endIndex = -1;

        while (tagMatcher.find()) {
            var tag = tagMatcher.group();
            if (tag.charAt(1) == '/') { // 종료 태그 확인
                depth--;
                if (depth == 0) {
                    endIndex = tagMatcher.end();
                    break;
                }
            } else {
                if (depth == 0) {
                    startIndex = tagMatcher.start();
                }
                depth++;
            }
        }

        // 최상위 태그인 경우: 시작 인덱스가 0이고, 끝 인덱스가 문자열의 길이와 같아야 함
        return startIndex == 0 && endIndex == markupString.length() && depth == 0;
    }

    /**
     * 마크업 문자열에서 특정 태그가 최상위 태그라면 해당 태그를 삭제한다.
     *
     * @param markupString  마크업 문자열
     * @param removeTagName 삭제할 태그
     * @return 삭제된 HTML 문자열
     *
     * @apiNote 최상위 html 태그 제거(&lt;html&gt;)
     *
     *          <pre>{@code
     * String htmlString = S2FileUtil.readFile("c:/test.html");
     * htmlString = S2MarkupUtil.removeTopLevelTag(htmlString, "html");
     * }</pre>
     */
    public static String removeTopLevelTag(String markupString, String removeTagName) {
        if (markupString == null || markupString.isBlank() || removeTagName == null || removeTagName.isBlank()) {
            return markupString;
        }

        // 태그를 찾는 정규 표현식 (속성을 포함, 대소문자 구분 없음)
        var quotedTagName = Pattern.quote(removeTagName);
        var tagRegex = "<" + quotedTagName + "\\b[^>]*>|</" + quotedTagName + ">";
        var tagPattern = Pattern.compile(tagRegex, Pattern.CASE_INSENSITIVE);
        var tagMatcher = tagPattern.matcher(markupString);

        // 태그의 시작과 끝 위치를 저장할 변수
        int startIndex = -1;
        int endIndex = -1;
        int depth = 0;
        String startTag = null;
        String endTag = null;

        while (tagMatcher.find()) {
            var tag = tagMatcher.group();
            if (tag.toLowerCase().startsWith("</")) {
                depth--;
                if (depth == 0) {
                    endIndex = tagMatcher.end();
                    endTag = tag;
                    break;
                }
            } else {
                if (depth == 0) {
                    startIndex = tagMatcher.start();
                    startTag = tag;
                }
                depth++;
            }
        }

        // 최상위 태그를 찾았고, 그 태그가 전체 문자열을 감싸고 있는 경우에만 제거
        if (startIndex == 0 && endIndex == markupString.length() && startTag != null && endTag != null) {
            return markupString.substring(startTag.length(), markupString.length() - endTag.length());
        }

        // 그 외의 경우 원래 문자열 반환
        return markupString;
    }

    /**
     * 마크업 문자열에서 특정 태그와 그 내용을 제거한다. (중첩된 태그도 제거, 닫히지 않은 태그는 문자열 끝까지 제거)
     * <p>
     * 정규식 기반의 단순 처리이므로 XSS 방어용 HTML 정화기로 쓰면 안 된다(이벤트 속성, {@code javascript:} URL 등은 그대로 남음). 신뢰할 수 없는
     * HTML 은 jsoup {@code Safelist} 같은 정화기를 사용한다.
     * </p>
     *
     * @param markupString  마크업 문자열
     * @param removeTagName 삭제할 태그
     * @return 제거된 HTML 문자열
     *
     * @apiNote script 태그 제거(&lt;script&gt;)
     *
     *          <pre>{@code
     * String htmlString = S2FileUtil.readFile("c:/test.html");
     * htmlString = S2MarkupUtil.removeTagContent(htmlString, "script");
     * }</pre>
     *
     * @see S2FileUtil#readFile(String)
     */
    public static String removeTagContent(String markupString, String removeTagName) {
        if (markupString == null || markupString.isBlank() || removeTagName == null || removeTagName.isBlank()) {
            return markupString;
        }

        // 여는/닫는 태그만 매칭하고 깊이를 직접 추적하여 중첩 태그를 제거한다.
        // (재귀적인 중첩 수량자 정규식은 catastrophic backtracking(ReDoS) 위험이 있어 사용하지 않는다)
        var quotedTagName = Pattern.quote(removeTagName);
        var tagRegex = "(?i)<" + quotedTagName + "\\b[^>]*>|</" + quotedTagName + "\\s*>";
        var tagPattern = Pattern.compile(tagRegex);
        var tagMatcher = tagPattern.matcher(markupString);

        var sb = new StringBuilder();
        var lastAppendEnd = 0;
        var depth = 0;

        while (tagMatcher.find()) {
            if (!tagMatcher.group().startsWith("</")) {
                if (depth == 0) {
                    sb.append(markupString, lastAppendEnd, tagMatcher.start());
                }
                depth++;
            } else if (depth > 0) {
                depth--;
                if (depth == 0) {
                    lastAppendEnd = tagMatcher.end();
                }
            }
        }
        // An unclosed tag removes everything after it, so "<script>alert(1)" does not survive | 닫히지 않은 태그는 끝까지 제거
        if (depth == 0) {
            sb.append(markupString, lastAppendEnd, markupString.length());
        }

        return sb.toString();
    }

    /**
     * 불완전한 태그를 제거한다.(닫힌 태그가 없는 열린 태그 또는 열린 태그가 없는 닫힌 태그 삭제)
     *
     * @param markupString  마크업 문자열
     * @param removeTagName 삭제할 태그
     * @return 제거된 HTML 문자열
     *
     * @apiNote test.html 파일에서 불완전한 div 태그(&lt;div&gt;) 제거하여 new.html 파일에 작성
     *
     *          <pre>{@code
     * S2FileUtil.writeFile("c:/new.html", S2MarkupUtil.removeIncompleteTag(S2FileUtil.readFile("c:/test.html"), "div"));
     * }</pre>
     *
     * @see S2FileUtil#writeFile(String, String)
     * @see S2FileUtil#readFile(String)
     */
    public static String removeIncompleteTag(String markupString, String removeTagName) {
        if (markupString == null || markupString.isBlank() || removeTagName == null || removeTagName.isBlank()) {
            return markupString;
        }

        // 태그명은 정규식 메타문자가 섞여 들어와도 리터럴로만 매칭되도록 이스케이프한다.
        var quotedTagName = Pattern.quote(removeTagName);

        // 완전한 태그 쌍을 찾는 정규식 (줄바꿈 포함)
        var completeTagRegex = "(?s)<" + quotedTagName + "\\b[^>]*>.*?</" + quotedTagName + ">";
        var completePattern = Pattern.compile(completeTagRegex);
        var completeMatcher = completePattern.matcher(markupString);

        // 완전한 태그의 위치를 저장
        var completeTagPositions = new ArrayList<int[]>();
        while (completeMatcher.find()) {
            completeTagPositions.add(new int[] { completeMatcher.start(), completeMatcher.end() });
        }

        // 모든 태그를 찾는 정규식
        var allTagRegex = "<" + quotedTagName + "\\b[^>]*>|</" + quotedTagName + ">";
        var allTagPattern = Pattern.compile(allTagRegex);
        var allTagMatcher = allTagPattern.matcher(markupString);

        var result = new StringBuilder(markupString);
        int offset = 0;

        while (allTagMatcher.find()) {
            int start = allTagMatcher.start() - offset;
            int end = allTagMatcher.end() - offset;
            boolean isComplete = false;

            // 현재 태그가 완전한 태그의 일부인지 확인
            for (int[] position : completeTagPositions) {
                if (allTagMatcher.start() >= position[0] && allTagMatcher.end() <= position[1]) {
                    isComplete = true;
                    break;
                }
            }

            // 불완전한 태그만 제거
            if (!isComplete) {
                result.delete(start, end);
                offset += (end - start);
            }
        }

        return result.toString();
    }

}
