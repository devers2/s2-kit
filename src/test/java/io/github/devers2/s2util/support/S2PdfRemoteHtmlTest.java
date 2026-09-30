package io.github.devers2.s2util.support;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.devers2.s2util.support.S2PdfUtil.PdfSource;

/**
 * Remote HTML pages come out as the browser shows them: same-origin images and stylesheets are embedded, internal
 * addresses are never fetched, and missing resources do not fail the PDF.
 *
 * <p>
 * <b>[한국어 설명]</b>
 * </p>
 * 원격 HTML 페이지가 브라우저 화면처럼 나오는지 확인합니다: 같은 출처의 이미지·스타일시트를 넣고, 내부망 주소는 받지 않으며, 받지 못한 리소스가 있어도 PDF 를
 * 만듭니다.
 */
class S2PdfRemoteHtmlTest {

    private HttpServer origin;
    private HttpServer other;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final List<String> originRequests = new CopyOnWriteArrayList<>();
    private final List<String> otherRequests = new CopyOnWriteArrayList<>();
    private final Map<String, String> cookies = new ConcurrentHashMap<>();

    private String base;
    private String otherBase;

    @BeforeEach
    void start() throws IOException {
        // These tests check the built-in renderer, even where s2-chrome is installed | s2-chrome 이 있어도 내장 렌더러를 시험
        S2PdfUtil.setBrowserRenderingEnabled(false);
        origin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin.createContext("/", this::serveOrigin);
        origin.start();
        other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/", exchange -> {
            otherRequests.add(exchange.getRequestURI().getPath());
            send(exchange, 200, "image/png", image("png", Color.RED));
        });
        other.start();
        base = "http://127.0.0.1:" + origin.getAddress().getPort();
        otherBase = "http://127.0.0.1:" + other.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        S2PdfUtil.setBrowserRenderingEnabled(true);
        origin.stop(0);
        other.stop(0);
    }

    private void serveOrigin(HttpExchange exchange) throws IOException {
        var path = exchange.getRequestURI().getPath();
        originRequests.add(path);
        var cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            cookies.put(path, cookie);
        }
        if (path.equals("/img/redirect.png")) {
            exchange.getResponseHeaders().add("Location", otherBase + "/via-redirect.png");
            send(exchange, 302, "text/plain", new byte[0]);
            return;
        }
        var body = files.get(path);
        if (body == null) {
            send(exchange, 404, "text/plain", "missing".getBytes());
            return;
        }
        var type = path.endsWith(".css") ? "text/css; charset=UTF-8"
                : path.endsWith(".html") ? "text/html; charset=UTF-8"
                        : path.endsWith(".jpg") ? "image/jpeg"
                                : path.endsWith(".svg") ? "image/svg+xml" : "image/png";
        send(exchange, 200, type, body);
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    /** Distinct colors, so each image can be found in the rendered page | 렌더링 결과에서 찾을 수 있도록 이미지마다 다른 색 */
    private static final Map<String, Color> PALETTE = new LinkedHashMap<>();
    static {
        PALETTE.put("red", Color.RED);
        PALETTE.put("green", Color.GREEN);
        PALETTE.put("blue", Color.BLUE);
        PALETTE.put("magenta", Color.MAGENTA);
        PALETTE.put("cyan", Color.CYAN);
        PALETTE.put("yellow", Color.YELLOW);
    }

    private static byte[] image(String format, Color color) throws IOException {
        var img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 40, 40);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static byte[] image(String format, String color) throws IOException {
        return image(format, PALETTE.get(color));
    }

    private static PDDocument load(InputStream merged) throws IOException {
        try (merged) {
            return Loader.loadPDF(merged.readAllBytes());
        }
    }

    /** Palette colors visible on the rendered pages | 렌더링된 페이지에 보이는 팔레트 색 */
    private static List<String> colors(PDDocument doc) throws IOException {
        var found = new java.util.TreeSet<String>();
        var renderer = new PDFRenderer(doc);
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            var page = renderer.renderImageWithDPI(i, 36);
            for (int y = 0; y < page.getHeight(); y++) {
                for (int x = 0; x < page.getWidth(); x++) {
                    var c = new Color(page.getRGB(x, y));
                    PALETTE.forEach((name, p) -> {
                        if (Math.abs(c.getRed() - p.getRed()) < 40 && Math.abs(c.getGreen() - p.getGreen()) < 40
                                && Math.abs(c.getBlue() - p.getBlue()) < 40) {
                            found.add(name);
                        }
                    });
                }
            }
        }
        return List.copyOf(found);
    }

    /** Pixels of one palette color on the first page at 72 dpi (1pt = 1px) | 첫 쪽의 한 색 픽셀 수 */
    private static int pixels(PDDocument doc, String color) throws IOException {
        var p = PALETTE.get(color);
        var page = new PDFRenderer(doc).renderImageWithDPI(0, 72);
        var count = 0;
        for (int y = 0; y < page.getHeight(); y++) {
            for (int x = 0; x < page.getWidth(); x++) {
                var c = new Color(page.getRGB(x, y));
                if (Math.abs(c.getRed() - p.getRed()) < 40 && Math.abs(c.getGreen() - p.getGreen()) < 40
                        && Math.abs(c.getBlue() - p.getBlue()) < 40) {
                    count++;
                }
            }
        }
        return count;
    }

    private void page(String html) {
        files.put("/page.html", html.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void imagesAndStylesheetsOfThePageAreEmbedded() throws IOException {
        files.put("/img/a.png", image("png", "red"));
        files.put("/img/b.jpg", image("jpg", "green"));
        files.put("/img/lazy.png", image("png", "blue"));
        files.put("/img/bg.png", image("png", "magenta"));
        files.put("/img/c.png", image("png", "cyan"));
        files.put("/img/d.png", image("png", "yellow"));
        files.put("/css/site.css", """
                @import "more.css";
                .hide { display: none; }
                .box { height: 40px; background-image: url(../img/d.png); }
                """.getBytes(StandardCharsets.UTF_8));
        files.put("/css/more.css", ".hide2 { display: none; }".getBytes(StandardCharsets.UTF_8));
        page("""
                <!DOCTYPE html>
                <html><head>
                <link rel="stylesheet" href="css/site.css">
                <style>.hide3 { display: none; } .inline-bg { height: 40px; background-image: url('img/bg.png'); }</style>
                </head><body>
                <h1>Report Title</h1>
                <p class="hide">HIDDEN-BY-LINK</p><p class="hide2">HIDDEN-BY-IMPORT</p><p class="hide3">HIDDEN-BY-HEAD</p>
                <img src="img/a.png"><img src="/img/b.jpg?v=3">
                <img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" data-src="img/lazy.png">
                <div class="inline-bg"></div><div class="box"></div>
                <div style="height: 40px; background: url(img/c.png)"></div>
                </body></html>
                """);

        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            assertEquals(List.of("blue", "cyan", "green", "magenta", "red", "yellow"), colors(doc));
            var text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Report Title"), text);
            assertFalse(text.contains("HIDDEN"), "link, @import and head CSS all apply: " + text);
        }
    }

    @Test
    void internalAddressesOfOtherHostsAreNeverFetched() throws IOException {
        page("<html><body><p>ok</p>"
                + "<img src=\"" + otherBase + "/direct.png\">"
                + "<img src=\"/img/redirect.png\">"
                + "<div style=\"height:40px;background-image:url('" + otherBase + "/bg.png')\"></div>"
                + "<link rel=\"stylesheet\" href=\"" + otherBase + "/x.css\"></body></html>");
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("ok"));
            assertEquals(List.of(), colors(doc));
        }
        assertEquals(List.of(), otherRequests, "another origin on a loopback address, directly or through a redirect");
        assertTrue(originRequests.contains("/img/redirect.png"), "same origin is fetched");
    }

    @Test
    void headersGoOnlyToTheSameOriginAndMissingResourcesAreDropped() throws IOException {
        files.put("/img/a.png", image("png", "green"));
        page("<html><body><p>still rendered</p><img src=\"img/a.png\"><img src=\"img/missing.png\">"
                + "<img src=\"img/not-image.png\"></body></html>");
        files.put("/img/not-image.png", "<html>login</html>".getBytes(StandardCharsets.UTF_8));
        var source = PdfSource.ofUrl(base + "/page.html", Map.of("Cookie", "SESSION=abc"), null);
        try (var doc = load(S2PdfUtil.merge(source))) {
            assertTrue(new PDFTextStripper().getText(doc).contains("still rendered"));
            assertEquals(List.of("green"), colors(doc));
        }
        assertEquals("SESSION=abc", cookies.get("/img/a.png"));
        assertTrue(originRequests.contains("/img/missing.png"));
    }

    @Test
    void stringHtmlStillNeverFetches() throws IOException {
        files.put("/img/a.png", image("png", "blue"));
        var html = "<html><body><img src=\"" + base + "/img/a.png\"><p>x</p></body></html>";
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofHtml(html)))) {
            assertEquals(List.of(), colors(doc));
        }
        assertEquals(List.of(), originRequests);
    }

    @Test
    void siblingImagesInStringHtmlAreAllRendered() throws IOException {
        // Rendering used to drop everything after the first unclosed <img> | 예전에는 닫히지 않은 첫 <img> 뒤가 모두 사라졌음
        var html = new StringBuilder("<p>");
        for (var color : List.of("red", "green", "blue")) {
            html.append("<img src=\"data:image/png;base64,")
                    .append(java.util.Base64.getEncoder().encodeToString(image("png", color))).append("\">");
        }
        html.append("</p><p>AFTER IMAGES</p>");
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofHtml(html.toString())))) {
            assertEquals(List.of("blue", "green", "red"), colors(doc));
            assertTrue(new PDFTextStripper().getText(doc).contains("AFTER IMAGES"));
        }
    }

    @Test
    void screenStylesApplyAndPrintStylesDoNot() throws IOException {
        files.put("/print.css", ".c { display: none; }".getBytes(StandardCharsets.UTF_8));
        page("""
                <html><head>
                <style>@media print { .a { display: none; } a[href]:after { content: " (" attr(href) ")"; } }
                @media screen { .b { display: none; } } @media not print { .d { display: none; } }</style>
                <link rel="stylesheet" media="print" href="/print.css">
                </head><body><p class="a">SHOWN-A</p><p class="b">HIDDEN-B</p><p class="c">SHOWN-C</p>
                <p class="d">HIDDEN-D</p><a href="/somewhere">LINK</a></body></html>
                """);
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            var text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("SHOWN-A") && text.contains("SHOWN-C") && text.contains("LINK"), text);
            assertFalse(text.contains("HIDDEN") || text.contains("somewhere"), text);
        }
    }

    @Test
    void svgIsDrawnWithoutFetchingWhatItPointsAt() throws IOException {
        assertTrue(S2PdfUtil.isSvgSupported(), "openhtmltopdf-svg-support is a test dependency");
        files.put("/logo.svg", ("<svg xmlns='http://www.w3.org/2000/svg' width='60' height='60'>"
                + "<rect width='60' height='60' fill='#ff0000'/>"
                + "<image href='" + otherBase + "/from-svg.png' width='10' height='10'/>"
                + "<image href='inner.png' x='20' y='20' width='40' height='40'/></svg>").getBytes(StandardCharsets.UTF_8));
        files.put("/inner.png", image("png", "yellow"));
        page("<html><body><img src=\"logo.svg\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"60\" height=\"60\">"
                + "<rect width=\"60\" height=\"60\" fill=\"#00ff00\"/></svg></body></html>");
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            assertEquals(List.of("green", "red", "yellow"), colors(doc), "a refused reference does not blank the SVG");
        }
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofSvg(
                "<svg xmlns='http://www.w3.org/2000/svg' width='80' height='80'><circle cx='40' cy='40' r='30' fill='#0000ff'/></svg>")))) {
            assertEquals(List.of("blue"), colors(doc));
        }
        assertEquals(List.of(), otherRequests, "addresses inside an SVG are not fetched");
    }

    @Test
    void pageCssSizesSvgImages() throws IOException {
        files.put("/big.svg", "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><rect width='100' height='100' fill='#ff0000'/></svg>"
                .getBytes(StandardCharsets.UTF_8));
        page("<html><head><style>.logo img { width: 40px; height: 40px; }</style></head><body>"
                + "<div class=\"logo\"><img src=\"big.svg\"></div></body></html>");
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            var red = pixels(doc, "red"); // 40px = 30pt → about 900 | 40px = 30pt → 약 900
            assertTrue(red > 700 && red < 1100, "sized by the page CSS: " + red);
        }
    }

    @Test
    void prefixedAttributesAndElementsDoNotBreakRendering() throws IOException {
        var html = "<html><body><div id=\"app\" v-on:click=\"go\" x-on:click=\"go\" :class=\"c\" @click=\"go\">VUE TEXT</div>"
                + "<p class=\"MsoNormal\">WORD TEXT<o:p></o:p></p><p><o:p>INSIDE PREFIXED</o:p></p>"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\"><defs><rect id=\"r\" width=\"40\" "
                + "height=\"40\" fill=\"#ff00ff\"/></defs><use xlink:href=\"#r\"/></svg></body></html>";
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofHtml(html)))) {
            var text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("VUE TEXT") && text.contains("WORD TEXT") && text.contains("INSIDE PREFIXED"), text);
            assertEquals(List.of("magenta"), colors(doc));
        }
    }

    @Test
    void pageFontsFallBackToTheDefaultFontForKorean() throws IOException {
        var hasFont = S2PdfUtil.SYSTEM_FONT_CANDIDATES.stream().anyMatch(p -> Files.isReadable(Path.of(p)));
        Assumptions.assumeTrue(hasFont, "no Korean font installed");
        page("<html><head><style>body { font: 14px/1.5 'Malgun Gothic', sans-serif; } h1 { font-family: Pretendard; }"
                + "</style></head><body><h1>보고서 제목</h1><p style=\"font-family: '맑은 고딕'\">본문 내용</p></body></html>");
        try (var doc = load(S2PdfUtil.merge(PdfSource.ofUrl(base + "/page.html")))) {
            var text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("보고서 제목") && text.contains("본문 내용"), text);
        }
    }

    @Test
    void fontFallbackRules() {
        assertEquals("font-family: Pretendard, ConvertPDF;", S2PdfUtil.addFontFallback("font-family: Pretendard;"));
        assertEquals("font: 12px/1.5 'A', sans-serif, ConvertPDF !important}",
                S2PdfUtil.addFontFallback("font: 12px/1.5 'A', sans-serif !important}"));
        assertEquals("font: inherit;", S2PdfUtil.addFontFallback("font: inherit;"));
        assertEquals("font-family: inherit", S2PdfUtil.addFontFallback("font-family: inherit"));
        assertEquals("font-size: 12px; font-weight: bold", S2PdfUtil.addFontFallback("font-size: 12px; font-weight: bold"));
        assertEquals("a.font:hover{color:red}", S2PdfUtil.addFontFallback("a.font:hover{color:red}"));
        assertEquals("font-family: monospace, ConvertPDF", S2PdfUtil.addFontFallback("font-family: monospace, ConvertPDF"));
    }

    @Test
    void mediaQueriesAreDecidedForAnA4WideScreen() {
        assertEquals("print", S2HtmlResources.swapMediaTypes("screen"));
        assertEquals("speech", S2HtmlResources.swapMediaTypes("print"));
        assertEquals("print", S2HtmlResources.swapMediaTypes("not print"));
        assertEquals("print", S2HtmlResources.swapMediaTypes("screen and (min-width: 768px)"));
        assertEquals("speech", S2HtmlResources.swapMediaTypes("screen and (min-width: 1200px)"));
        assertEquals("speech", S2HtmlResources.swapMediaTypes("(max-width: 575.98px)"));
        assertEquals("print", S2HtmlResources.swapMediaTypes("only screen and (max-width: 60em)"));
        assertEquals("speech", S2HtmlResources.swapMediaTypes("(prefers-color-scheme: dark)"));
        assertEquals("speech, print", S2HtmlResources.swapMediaTypes("print, (min-width: 40rem)"));
        assertEquals("@media print{.a{}} @media speech{.b{}}",
                S2HtmlResources.screenMedia("@media screen and (min-width: 700px){.a{}} @media (min-width: 1400px){.b{}}"));
    }

    @Test
    void addressRules() throws IOException {
        for (var internal : List.of("127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.0.1", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "::1", "fd00::1", "fe80::1")) {
            assertFalse(S2HtmlResources.isPublic(InetAddress.getByName(internal)), internal);
        }
        for (var external : List.of("8.8.8.8", "1.1.1.1", "2606:4700:4700::1111")) {
            assertTrue(S2HtmlResources.isPublic(InetAddress.getByName(external)), external);
        }
        var page = URI.create("https://intra.example.com/app/view");
        assertTrue(S2HtmlResources.sameOrigin(page, URI.create("https://intra.example.com:443/img/a.png")));
        assertFalse(S2HtmlResources.sameOrigin(page, URI.create("http://intra.example.com/img/a.png")));
        assertFalse(S2HtmlResources.sameOrigin(page, URI.create("https://cdn.example.com/a.png")));
        assertEquals(URI.create("https://intra.example.com/img/a%20b.png"), S2HtmlResources.resolve(page, "../img/a b.png#x"));
        assertEquals(URI.create("https://intra.example.com/a%20b.png"), S2HtmlResources.resolve(page, "/a%20b.png"));
        assertNull(S2HtmlResources.resolve(page, "file:///etc/passwd"));
        assertNull(S2HtmlResources.resolve(page, "#frag"));
    }
}
