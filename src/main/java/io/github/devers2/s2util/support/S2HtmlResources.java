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

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Element;

import io.github.devers2.s2util.core.S2ThreadUtil;
import io.github.devers2.s2util.log.S2LogManager;
import io.github.devers2.s2util.log.S2Logger;

/**
 * Makes a remote HTML page self-contained before rendering, so the PDF shows the page as the browser does: images
 * ({@code <img>}, CSS backgrounds) become data URIs and stylesheets ({@code <link>}, {@code @import}) become inline
 * {@code <style>} blocks.
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 원격 HTML 페이지를 렌더링 전에 자체 완결 문서로 만든다. 브라우저 화면처럼 보이도록 이미지({@code <img>}, CSS 배경)는 data URI 로,
 * 스타일시트({@code <link>}, {@code @import})는 인라인 {@code <style>} 로 바꾼다.
 * <ul>
 * <li>페이지와 같은 출처(스킴·호스트·포트)는 그대로 받는다. 호출자가 이미 믿고 넘긴 주소이기 때문이다.</li>
 * <li>다른 호스트는 공개 주소일 때만 받는다. 내부망·루프백·링크 로컬 주소는 막는다 (HTML 에 적힌 주소로 내부망을 조회하는 SSRF 방지).
 * 리다이렉트도 단계마다 같은 기준으로 확인한다.</li>
 * <li>요청 헤더(로그인 쿠키 등)는 같은 출처에만 보낸다.</li>
 * <li>리소스 개수·하나당 크기·전체 크기를 제한하고, 여러 리소스를 동시에 받는다.</li>
 * <li>받지 못한 리소스는 경고 로그를 남기고 빼며, 페이지는 그대로 만든다 (브라우저의 깨진 이미지와 같음).</li>
 * </ul>
 */
final class S2HtmlResources {

    private static final S2Logger logger = S2LogManager.getLogger(S2HtmlResources.class);

    /** Most resources fetched for one page | 한 페이지에서 받을 최대 리소스 수 */
    static final int MAX_RESOURCES = 300;
    /** Largest single resource | 리소스 하나의 최대 크기 */
    static final long MAX_RESOURCE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_IMPORT_DEPTH = 3;

    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CSS_IMPORT = Pattern.compile(
            "@import\\s+(?:url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)|(['\"])(.*?)\\3)\\s*([^;]*);", Pattern.CASE_INSENSITIVE);
    /** Web fonts: the renderer reads only TTF/OTF, and the default font covers the text | 웹 폰트는 받지 않음 (기본 폰트가 대신함) */
    private static final Pattern FONT_FILE = Pattern.compile("(?i).*\\.(woff2?|ttf|otf|eot)([?#].*)?$");
    private static final List<String> LAZY_SRC_ATTRIBUTES = List.of("data-src", "data-original", "data-lazy-src",
            "data-lazy");

    /** Redirects are followed by hand so every hop is checked | 단계마다 확인하려고 리다이렉트는 직접 따라감 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(S2ThreadUtil.getCommonExecutor())
            .build();

    private record Fetched(byte[] data, String contentType) {
    }

    private final URI page;
    private final Map<String, String> headers;
    private final Duration timeout;
    private final AtomicLong budget;
    private final Predicate<String> onClasspath;
    private final AtomicInteger requested = new AtomicInteger();
    private final Map<URI, CompletableFuture<Fetched>> cache = new ConcurrentHashMap<>();

    private S2HtmlResources(URI page, Map<String, String> headers, Duration timeout, long budget,
            Predicate<String> onClasspath) {
        this.page = page;
        this.headers = headers != null ? headers : Map.of();
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(30);
        this.budget = new AtomicLong(budget);
        this.onClasspath = onClasspath != null ? onClasspath : path -> false;
    }

    /**
     * Returns the page with its images and stylesheets embedded.
     * <p>
     * <b>[한국어 설명]</b>
     * </p>
     * 이미지와 스타일시트를 넣은 페이지를 돌려준다.
     *
     * @param html        페이지 HTML
     * @param page        페이지 주소 (리다이렉트 후 최종 주소). 상대 경로와 같은 출처의 기준
     * @param headers     페이지 요청 헤더. 같은 출처 리소스에만 보냄 (null 가능)
     * @param timeout     리소스 하나의 제한 시간
     * @param budget      리소스 전체의 최대 바이트 수
     * @param onClasspath 페이지의 상대 경로가 클래스패스에 있는지 (있으면 받지 않고 기존 클래스패스 방식에 맡김)
     * @return 리소스를 넣은 HTML
     */
    static String inline(String html, URI page, Map<String, String> headers, Duration timeout, long budget,
            Predicate<String> onClasspath) {
        return new S2HtmlResources(page, headers, timeout, budget, onClasspath).inline(html);
    }

    private String inline(String html) {
        var doc = Jsoup.parse(html, page.toString());

        // 1. Stylesheets into <style>, with @import and url() resolved against the stylesheet's own address
        // | 스타일시트를 <style> 로. @import 와 url() 은 스타일시트 자신의 주소 기준
        var links = new ArrayList<Element>();
        for (var link : doc.select("link[href]")) {
            var rel = " " + link.attr("rel").toLowerCase(Locale.ROOT) + " ";
            if (rel.contains(" stylesheet ") && !rel.contains(" alternate ")) {
                links.add(link);
            }
        }
        links.forEach(link -> prefetch(resolve(page(link), link.attr("href"))));
        for (var link : links) {
            var uri = resolve(page(link), link.attr("href"));
            var css = uri != null ? stylesheet(uri, 0) : null;
            if (css == null) {
                link.remove();
                continue;
            }
            var style = new Element("style");
            var media = link.attr("media");
            if (!media.isBlank()) {
                style.attr("media", media);
            }
            style.appendChild(new DataNode(css));
            link.replaceWith(style);
        }
        for (var style : doc.select("style")) {
            var css = resolveImports(absolutize(style.data(), page(style), true), 0);
            style.empty();
            style.appendChild(new DataNode(css));
        }

        // 2. Image addresses from <img>, style attributes and all CSS, fetched together
        // | <img>, style 속성, 모든 CSS 의 이미지 주소를 모아 한꺼번에 받음
        var images = new LinkedHashSet<URI>();
        var imgTargets = new ArrayList<Map.Entry<Element, URI>>();
        for (var img : doc.select("img")) {
            var src = imageSource(img);
            if (src == null || src.startsWith("data:") || isClasspath(src)) {
                continue;
            }
            var uri = resolve(page(img), src);
            if (uri != null) {
                images.add(uri);
                imgTargets.add(Map.entry(img, uri));
            } else {
                img.attr("src", "");
            }
        }
        for (var styled : doc.select("[style]")) {
            var base = page(styled);
            styled.attr("style", absolutize(styled.attr("style"), base, true));
            collectCssImages(styled.attr("style"), images);
        }
        for (var style : doc.select("style")) {
            collectCssImages(style.data(), images);
        }
        images.forEach(this::prefetch);

        // 3. Substitute data URIs; failures are dropped with a warning | data URI 로 바꿈. 실패한 것은 경고 후 뺌
        for (var entry : imgTargets) {
            var img = entry.getKey();
            img.attr("src", dataUri(entry.getValue()));
            img.removeAttr("srcset");
        }
        for (var styled : doc.select("[style]")) {
            styled.attr("style", embedCssImages(styled.attr("style")));
        }
        for (var style : doc.select("style")) {
            var css = embedCssImages(style.data());
            style.empty();
            style.appendChild(new DataNode(css));
        }
        return doc.outerHtml();
    }

    /** The address relative paths of this element resolve against ({@code <base href>} included) | 상대 경로 기준 주소 */
    private URI page(Element element) {
        try {
            var base = element.baseUri();
            return base == null || base.isBlank() ? page : URI.create(base);
        } catch (IllegalArgumentException e) {
            return page;
        }
    }

    /** src, or the lazy-loading attribute when src is empty or a placeholder, or the first srcset candidate | 실제 이미지 주소 */
    private static String imageSource(Element img) {
        var src = img.attr("src").trim();
        if (src.isEmpty() || src.startsWith("data:")) {
            for (var name : LAZY_SRC_ATTRIBUTES) {
                var lazy = img.attr(name).trim();
                if (!lazy.isEmpty()) {
                    return lazy;
                }
            }
        }
        if (src.isEmpty()) {
            var srcset = img.attr("srcset").trim();
            if (!srcset.isEmpty()) {
                return srcset.split(",")[0].trim().split("\\s+")[0];
            }
            return null;
        }
        return src;
    }

    private boolean isClasspath(String ref) {
        return !isAbsolute(ref) && onClasspath.test(ref);
    }

    private static boolean isAbsolute(String ref) {
        return ref.startsWith("//") || ref.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*");
    }

    /** Resolves a reference to an http(s) address, or null | http(s) 절대 주소로 (아니면 null) */
    static URI resolve(URI base, String ref) {
        if (ref == null) {
            return null;
        }
        var trimmed = ref.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("data:")) {
            return null;
        }
        try {
            var uri = base.resolve(trimmed.replace(" ", "%20"));
            var scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                return null;
            }
            // Drop the fragment on the string: the multi-argument URI constructors would encode '%' again
            // | 프래그먼트는 문자열에서 제거 (여러 인자 URI 생성자는 '%' 를 다시 인코딩함)
            var text = uri.toString();
            var hash = text.indexOf('#');
            return URI.create(hash >= 0 ? text.substring(0, hash) : text).normalize();
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ CSS

    /** A fetched stylesheet with imports inlined, or null when it could not be fetched | 가져온 스타일시트 (실패 시 null) */
    private String stylesheet(URI uri, int depth) {
        var fetched = await(uri);
        if (fetched == null) {
            return null;
        }
        var type = fetched.contentType().toLowerCase(Locale.ROOT);
        if (!type.isEmpty() && !type.contains("text/css") && !type.contains("text/plain")) {
            logger.warn("PDF 에 넣지 못한 스타일시트: {} (CSS 가 아닌 응답: {})", uri, fetched.contentType());
            return null;
        }
        var css = new String(fetched.data(), charset(fetched.contentType()));
        if (!css.isEmpty() && css.charAt(0) == '﻿') {
            css = css.substring(1);
        }
        return resolveImports(absolutize(css, uri, false), depth);
    }

    /** Replaces {@code @import} rules with the imported CSS | @import 를 가져온 CSS 로 바꿈 */
    private String resolveImports(String css, int depth) {
        var matcher = CSS_IMPORT.matcher(css);
        var targets = new ArrayList<URI>();
        while (matcher.find()) {
            targets.add(importTarget(matcher));
        }
        if (targets.isEmpty()) {
            return css;
        }
        targets.forEach(this::prefetch);
        matcher.reset();
        var out = new StringBuilder();
        var index = 0;
        while (matcher.find()) {
            var uri = targets.get(index++);
            String imported = null;
            if (uri != null && depth < MAX_IMPORT_DEPTH) {
                imported = stylesheet(uri, depth + 1);
            } else if (uri != null) {
                logger.warn("PDF 에 넣지 못한 스타일시트: {} (@import 가 {}단계를 넘음)", uri, MAX_IMPORT_DEPTH);
            }
            var media = matcher.group(5).trim();
            var replacement = imported == null ? ""
                    : media.isEmpty() ? imported : "@media " + media + " {\n" + imported + "\n}";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Imports were already made absolute by {@link #absolutize} | @import 주소는 absolutize 에서 이미 절대 주소 */
    private static URI importTarget(Matcher matcher) {
        var ref = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
        try {
            return ref == null ? null : resolve(URI.create("http://invalid/"), ref);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Makes url() and @import addresses absolute. Page-level relative paths found on the classpath are left for the
     * classpath loader | url()·@import 주소를 절대 주소로. 페이지의 상대 경로가 클래스패스에 있으면 그대로 둠
     */
    private String absolutize(String css, URI base, boolean pageLevel) {
        var importMatcher = CSS_IMPORT.matcher(css);
        var withImports = new StringBuilder();
        while (importMatcher.find()) {
            var ref = importMatcher.group(2) != null ? importMatcher.group(2) : importMatcher.group(4);
            var uri = resolve(base, ref);
            var media = importMatcher.group(5);
            var replacement = uri == null ? "" : "@import url(\"" + uri + "\") " + media + ";";
            importMatcher.appendReplacement(withImports, Matcher.quoteReplacement(replacement));
        }
        importMatcher.appendTail(withImports);

        var matcher = CSS_URL.matcher(withImports);
        var out = new StringBuilder();
        while (matcher.find()) {
            var ref = matcher.group(2).trim();
            String replacement;
            if (ref.startsWith("data:") || ref.startsWith("#") || ref.isEmpty() || (pageLevel && isClasspath(ref))) {
                replacement = matcher.group(0);
            } else {
                var uri = resolve(base, ref);
                replacement = uri == null ? "url('')" : "url('" + uri + "')";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static void collectCssImages(String css, Set<URI> images) {
        var matcher = CSS_URL.matcher(css);
        while (matcher.find()) {
            var ref = matcher.group(2).trim();
            if (ref.startsWith("http://") || ref.startsWith("https://")) {
                if (!FONT_FILE.matcher(ref).matches()) {
                    images.add(URI.create(ref));
                }
            }
        }
    }

    private String embedCssImages(String css) {
        var matcher = CSS_URL.matcher(css);
        var out = new StringBuilder();
        while (matcher.find()) {
            var ref = matcher.group(2).trim();
            String replacement = matcher.group(0);
            if ((ref.startsWith("http://") || ref.startsWith("https://")) && !FONT_FILE.matcher(ref).matches()) {
                replacement = "url('" + dataUri(URI.create(ref)) + "')";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Charset charset(String contentType) {
        var matcher = Pattern.compile("(?i)charset=\"?([\\w.:-]+)").matcher(contentType);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (Exception ignored) {
                // Unknown charset: fall back to UTF-8 | 알 수 없는 문자셋은 UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    // --------------------------------------------------------------- images

    /** A data URI for a fetched image, or "" (dropped) | 받은 이미지의 data URI (실패 시 빈 값) */
    private String dataUri(URI uri) {
        var fetched = await(uri);
        if (fetched == null) {
            return "";
        }
        var mime = imageMime(fetched);
        if (mime == null) {
            logger.warn("PDF 에 넣지 못한 이미지: {} (이미지가 아닌 응답: {})", uri, fetched.contentType());
            return "";
        }
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(fetched.data());
    }

    /** The image type from the header, or from the leading bytes | 헤더 또는 앞부분 바이트로 본 이미지 형식 */
    private static String imageMime(Fetched fetched) {
        var type = fetched.contentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (type.startsWith("image/")) {
            return type;
        }
        var d = fetched.data();
        if (d.length >= 4 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 4 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') {
            return "image/gif";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E'
                && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        if (d.length >= 2 && d[0] == 'B' && d[1] == 'M') {
            return "image/bmp";
        }
        var head = new String(d, 0, Math.min(d.length, 512), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (head.contains("<svg")) {
            return "image/svg+xml";
        }
        return null;
    }

    // ------------------------------------------------------------- fetching

    private void prefetch(URI uri) {
        if (uri != null) {
            fetch(uri);
        }
    }

    /** The fetched resource, or null after logging why | 받은 리소스 (실패 시 사유를 남기고 null) */
    private Fetched await(URI uri) {
        try {
            return fetch(uri).join();
        } catch (CompletionException e) {
            var cause = e.getCause() != null ? e.getCause() : e;
            logger.warn("PDF 에 넣지 못한 리소스: {} ({})", uri, cause.getMessage());
            return null;
        }
    }

    private CompletableFuture<Fetched> fetch(URI uri) {
        return cache.computeIfAbsent(uri, key -> {
            if (requested.incrementAndGet() > MAX_RESOURCES) {
                return CompletableFuture.failedFuture(new IOException("리소스가 " + MAX_RESOURCES + "개를 넘습니다"));
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return download(key);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, S2ThreadUtil.getCommonExecutor());
        });
    }

    private Fetched download(URI uri) throws IOException, InterruptedException {
        var current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkAllowed(current);
            var request = HttpRequest.newBuilder(current).timeout(timeout).GET();
            if (sameOrigin(current, page)) {
                headers.forEach(request::header);
            }
            var response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                var status = response.statusCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    var location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new IOException("HTTP " + status + " without Location"));
                    var next = resolve(current, location);
                    if (next == null) {
                        throw new IOException("리다이렉트 주소가 올바르지 않습니다: " + location);
                    }
                    current = next;
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new IOException("HTTP " + status);
                }
                var limit = Math.min(MAX_RESOURCE_BYTES, budget.get());
                if (limit <= 0) {
                    throw new IOException("리소스 전체 크기 한도를 넘었습니다");
                }
                var declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declared > limit) {
                    throw new IOException("크기 " + declared + " bytes 가 한도 " + limit + " bytes 를 넘습니다");
                }
                byte[] data;
                try {
                    data = S2StreamUtil.streamToByteArray(body, false, limit);
                } catch (RuntimeException e) {
                    throw new IOException("크기가 한도 " + limit + " bytes 를 넘습니다", e);
                }
                budget.addAndGet(-data.length);
                return new Fetched(data, response.headers().firstValue("Content-Type").orElse(""));
            }
        }
        throw new IOException("리다이렉트가 " + MAX_REDIRECTS + "번을 넘습니다");
    }

    /** Same origin as the page, or a host whose every address is public | 페이지와 같은 출처이거나 모든 주소가 공개 주소 */
    private void checkAllowed(URI uri) throws IOException {
        if (sameOrigin(uri, page)) {
            return;
        }
        for (var address : InetAddress.getAllByName(uri.getHost())) {
            if (!isPublic(address)) {
                throw new IOException("내부망 주소는 가져오지 않습니다: " + uri.getHost() + " (" + address.getHostAddress() + ")");
            }
        }
    }

    static boolean sameOrigin(URI a, URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost())
                && port(a) == port(b);
    }

    private static int port(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** False for loopback, private, link-local, CGNAT, unique-local and similar addresses | 내부망 계열이면 false */
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        var b = address.getAddress();
        if (address instanceof Inet4Address) {
            var first = b[0] & 0xFF;
            var second = b[1] & 0xFF;
            return first != 0 // this network | 0.0.0.0/8
                    && !(first == 100 && second >= 64 && second <= 127) // CGNAT 100.64.0.0/10
                    && !(first == 198 && (second == 18 || second == 19)) // benchmarking 198.18.0.0/15
                    && first < 240; // reserved, broadcast | 예약, 브로드캐스트
        }
        if (address instanceof Inet6Address) {
            return (b[0] & 0xFE) != 0xFC; // unique local fc00::/7
        }
        return true;
    }
}
