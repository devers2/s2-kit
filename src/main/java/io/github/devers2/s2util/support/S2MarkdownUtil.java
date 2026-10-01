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

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Markdown to HTML (CommonMark with GitHub-style tables, strikethrough, autolinks, task lists and heading anchors),
 * safe to show on a web page by default.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 마크다운을 HTML 로 바꾼다 (CommonMark + GitHub 식 표, 취소선, 자동 링크, 체크박스 목록, 제목 앵커). 기본값으로 웹 화면에 바로 넣어도 안전하다.
 * <ul>
 * <li>마크다운 안의 HTML 태그는 글자 그대로 보인다 (XSS 방지). 믿을 수 있는 문서만 {@link #toHtml(String, boolean)}로 허용한다.</li>
 * <li>링크 주소는 http, https, mailto, 상대 경로, {@code #} 만 허용하고 {@code javascript:}, {@code data:} 등은 지운다. 이미지는 여기에
 * {@code data:image/...} 를 더 허용한다.</li>
 * <li>{@code S2PdfUtil.PdfSource.ofMarkdown(...)}이 같은 변환을 써서 화면과 PDF 의 모양이 일치한다.</li>
 * </ul>
 * <p>
 * 의존성(앱에서 추가, 선택): {@code org.commonmark:commonmark} 와 확장 {@code commonmark-ext-gfm-tables},
 * {@code commonmark-ext-gfm-strikethrough}, {@code commonmark-ext-autolink}, {@code commonmark-ext-task-list-items},
 * {@code commonmark-ext-heading-anchor} (BSD 2-Clause; autolink 확장은 MIT 인 {@code org.nibor.autolink:autolink} 를 함께 씀).
 * 없으면 예외로 필요한 의존성을 알려 준다.
 * </p>
 *
 * <pre>{@code
 * String html = S2MarkdownUtil.toHtml("# 실험 결과\n| 항목 | 값 |\n|---|---|\n| A | 1 |");
 * }</pre>
 */
public final class S2MarkdownUtil {

    static final String REQUIRED = "마크다운을 쓰려면 org.commonmark:commonmark 와 확장(commonmark-ext-gfm-tables, "
            + "commonmark-ext-gfm-strikethrough, commonmark-ext-autolink, commonmark-ext-task-list-items, "
            + "commonmark-ext-heading-anchor) 의존성이 필요합니다.";

    private static final List<String> REQUIRED_CLASSES = List.of(
            "org.commonmark.parser.Parser",
            "org.commonmark.ext.gfm.tables.TablesExtension",
            "org.commonmark.ext.gfm.strikethrough.StrikethroughExtension",
            "org.commonmark.ext.autolink.AutolinkExtension",
            "org.commonmark.ext.task.list.items.TaskListItemsExtension",
            "org.commonmark.ext.heading.anchor.HeadingAnchorExtension");

    private S2MarkdownUtil() {
    }

    /**
     * Whether the CommonMark libraries are on the classpath | CommonMark 라이브러리가 있는지
     *
     * @return 마크다운을 변환할 수 있으면 true
     */
    public static boolean isAvailable() {
        var loader = S2MarkdownUtil.class.getClassLoader();
        for (var name : REQUIRED_CLASSES) {
            try {
                Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Markdown to HTML; HTML tags in the Markdown are shown as text | 마크다운을 HTML 로. 마크다운 안의 HTML 태그는 글자로 보임
     *
     * @param markdown 마크다운
     * @return HTML 조각 ({@code <html>}, {@code <body>} 없이)
     * @throws IllegalStateException CommonMark 의존성이 없을 때
     */
    public static String toHtml(String markdown) {
        return toHtml(markdown, false);
    }

    /**
     * Markdown to HTML.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 마크다운을 HTML 로 바꾼다.
     *
     * @param markdown  마크다운
     * @param allowHtml 마크다운 안의 HTML 태그를 그대로 둘지. 사용자가 쓴 문서에는 false (XSS)
     * @return HTML 조각 ({@code <html>}, {@code <body>} 없이)
     * @throws IllegalStateException CommonMark 의존성이 없을 때
     */
    public static String toHtml(String markdown, boolean allowHtml) {
        Objects.requireNonNull(markdown, "markdown");
        if (!isAvailable()) {
            throw new IllegalStateException(REQUIRED);
        }
        return Converter.render(markdown, allowHtml);
    }

    /**
     * Holds the CommonMark types, so S2MarkdownUtil itself loads without them and can say what is missing | CommonMark
     * 타입을 여기에만 두어, 라이브러리가 없어도 S2MarkdownUtil 은 로드되고 무엇이 없는지 알려 줄 수 있음
     */
    private static final class Converter {
        private static final List<org.commonmark.Extension> EXTENSIONS = List.of(
                org.commonmark.ext.gfm.tables.TablesExtension.create(),
                org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                org.commonmark.ext.autolink.AutolinkExtension.create(),
                org.commonmark.ext.task.list.items.TaskListItemsExtension.create(),
                org.commonmark.ext.heading.anchor.HeadingAnchorExtension.create());

        // Parser and renderer are thread-safe | 파서와 렌더러는 스레드에 안전함
        private static final org.commonmark.parser.Parser PARSER = org.commonmark.parser.Parser.builder()
                .extensions(EXTENSIONS).build();
        private static final org.commonmark.renderer.html.HtmlRenderer SAFE = renderer(true);
        private static final org.commonmark.renderer.html.HtmlRenderer TRUSTED = renderer(false);

        private static org.commonmark.renderer.html.HtmlRenderer renderer(boolean escapeHtml) {
            return org.commonmark.renderer.html.HtmlRenderer.builder()
                    .extensions(EXTENSIONS)
                    .escapeHtml(escapeHtml)
                    .sanitizeUrls(true)
                    .urlSanitizer(new SafeUrls())
                    .build();
        }

        static String render(String markdown, boolean allowHtml) {
            return (allowHtml ? TRUSTED : SAFE).render(PARSER.parse(markdown));
        }
    }

    /** Only harmless link and image addresses | 해가 없는 링크·이미지 주소만 */
    private static final class SafeUrls implements org.commonmark.renderer.html.UrlSanitizer {
        @Override
        public String sanitizeLinkUrl(String url) {
            return isSafeLink(url) ? url : "";
        }

        @Override
        public String sanitizeImageUrl(String url) {
            return isSafeLink(url) || url.trim().toLowerCase(Locale.ROOT).startsWith("data:image/") ? url : "";
        }
    }

    /** http, https, mailto, relative paths and in-page anchors | http, https, mailto, 상대 경로, 쪽 안 앵커 */
    static boolean isSafeLink(String url) {
        if (url == null) {
            return false;
        }
        var value = url.trim();
        var colon = value.indexOf(':');
        var slash = value.indexOf('/');
        var query = value.indexOf('?');
        var hash = value.indexOf('#');
        // No scheme: a colon only after a /, ? or # (relative path) | 스킴 없음: 콜론이 /, ?, # 뒤에만 있으면 상대 경로
        if (colon < 0 || (slash >= 0 && slash < colon) || (query >= 0 && query < colon) || (hash >= 0 && hash < colon)) {
            return true;
        }
        var scheme = value.substring(0, colon).toLowerCase(Locale.ROOT);
        return scheme.equals("http") || scheme.equals("https") || scheme.equals("mailto");
    }
}
