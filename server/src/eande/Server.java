package eande;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * The E&E website's server.
 *
 * It serves the site's files and adds the security and caching rules to every
 * response. It's built only from what comes with Java, with no framework or
 * libraries. It started as a copy of the portfolio's server.
 *
 * Run it locally from the repo folder with:
 *
 *     ./run.sh
 *
 * then open http://127.0.0.1:8000
 */
public final class Server {
    private Server() {}

    // The folder holding index.html, style.css and the rest. It's the folder the
    // server is started from, unless SITE_DIR says otherwise.
    static final Path SITE = Path.of(envOr("SITE_DIR", System.getProperty("user.dir")));

    // Only these files are ever served. Everything else in the repo (README, notes,
    // the server's own code) stays private, even if someone guesses its name.
    // The number is how many seconds a browser may keep its copy.
    static final Map<String, Integer> PUBLIC_FILES = Map.of(
            "favicon.svg", 60 * 60 * 24,    // 1 day
            "apple-touch-icon.png", 60 * 60 * 24,
            "og-image.png", 60 * 60 * 24,
            "robots.txt", 60 * 60 * 24,     // 1 day
            "sitemap.xml", 60 * 60 * 24);   // 1 day

    static final Map<String, String> CONTENT_TYPES = Map.of(
            "svg", "image/svg+xml",
            "png", "image/png",
            "jpg", "image/jpeg",
            "webp", "image/webp",
            "txt", "text/plain; charset=utf-8",
            "xml", "application/xml; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "html", "text/html; charset=utf-8");

    // The browser's safety rules: only load files from this site, never run scripts,
    // and never let another site show this one inside a frame.
    static final String CSP = "default-src 'self'; img-src 'self'; style-src 'self'; script-src 'none'; "
            + "object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
    // The 404 page keeps its styles inside itself, so it also needs inline styles allowed.
    static final String CSP_404 = CSP.replace("style-src 'self'", "style-src 'self' 'unsafe-inline'");

    static final Map<String, String> SECURITY_HEADERS = orderedMap(
            "Content-Security-Policy", CSP,
            "X-Content-Type-Options", "nosniff",
            "Referrer-Policy", "strict-origin-when-cross-origin",
            // The page needs none of these, so no add-on or injected code can use them either.
            "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=(), "
                    + "serial=(), bluetooth=(), browsing-topics=()",
            "Cross-Origin-Opener-Policy", "same-origin",
            // Other sites can't pull this site's files into their pages (except the share
            // image, below). It also stops a class of attacks that read memory across sites.
            "Cross-Origin-Resource-Policy", "same-origin",
            // Older browsers that don't understand frame-ancestors in the CSP.
            "X-Frame-Options", "DENY",
            "Strict-Transport-Security", "max-age=31536000");

    // Files other sites are meant to show, like the preview a chat app draws for a shared link.
    static final Set<String> SHAREABLE = Set.of("og-image.png", "apple-touch-icon.png");

    // Text responses at least this big are squashed before sending, so pages download faster.
    static final int GZIP_MIN_SIZE = 500;

    // ---------- The stylesheet ----------
    // Its address includes a fingerprint of its contents (style.css?v=1a2b3c4d), so
    // browsers can keep it for a year. When the file changes, so does the address.
    static final Path STYLE_CSS = SITE.resolve("style.css");
    static String styleVersion() {
        return sha256(read(STYLE_CSS)).substring(0, 8);
    }

    // With -Deande.quiet=true the server doesn't log each request, so test output stays readable.
    static final boolean QUIET = Boolean.getBoolean("eande.quiet");

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(envOr("PORT", "8080"));
        String host = envOr("HOST", "0.0.0.0");

        HttpServer server = start(host, port);
        // When the host stops the server, let requests in progress finish (up to a second).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        System.out.println("serving " + SITE + " on http://" + host + ":" + port);
    }

    /** Starts the server. Port 0 picks any free port, which the tests use. */
    static HttpServer start(String host, int port) throws IOException {
        limitSlowConnections();
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", Server::handle);
        // A lightweight thread for each request, so one slow visitor can't hold up the rest.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    /**
     * Time limits, so a visitor can't tie up the server by sending a request very slowly
     * or never reading the answer (a "slowloris" attack). Java reads these settings once,
     * when the first server starts, and they apply to every connection.
     */
    static void limitSlowConnections() {
        setIfMissing("sun.net.httpserver.maxReqTime", "10");      // seconds to send a request
        setIfMissing("sun.net.httpserver.maxRspTime", "30");      // seconds to take the answer
        setIfMissing("sun.net.httpserver.idleInterval", "30");    // seconds a kept-open connection may sit idle
        setIfMissing("sun.net.httpserver.maxIdleConnections", "200");
        setIfMissing("sun.net.httpserver.maxReqHeaders", "100");  // a browser sends about 20
    }

    private static void setIfMissing(String name, String value) {
        if (System.getProperty(name) == null) {
            System.setProperty(name, value);
        }
    }

    static void handle(HttpExchange exchange) {
        int status = 500;
        try {
            Firewall.Refusal refusal = Firewall.check(exchange);
            if (refusal != null) {
                status = refusal.status();
                send(exchange, status, plain(refusal.message()), CONTENT_TYPES.get("txt"),
                        withNoStore(refusal.headers()));
            } else {
                status = route(exchange);
            }
        } catch (Exception error) {
            System.err.println("error handling " + exchange.getRequestURI() + ": " + error);
            try {
                send(exchange, 500, plain("Internal Server Error"), CONTENT_TYPES.get("txt"), Map.of());
            } catch (IOException ignored) {
                // The visitor has already gone.
            }
        } finally {
            exchange.close();
            if (!QUIET) {
                System.out.println(Firewall.clientIp(exchange) + " \"" + exchange.getRequestMethod() + " "
                        + exchange.getRequestURI().getRawPath() + "\" " + status);
            }
        }
    }

    /** Sends the right page for the address, and returns the status code it sent. */
    static int route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/healthz")) {
            // Fly.io calls this to check the server is up. It skips the firewall, so check the method here.
            if (!method.equals("GET") && !method.equals("HEAD")) {
                send(exchange, 405, plain("Method Not Allowed"), CONTENT_TYPES.get("txt"), Map.of("Allow", "GET, HEAD"));
                return 405;
            }
            send(exchange, 200, plain("ok"), CONTENT_TYPES.get("txt"), Map.of("Cache-Control", "no-store"));
            return 200;
        }
        if (path.equals("/")) {
            return home(exchange);
        }
        if (path.equals("/style.css")) {
            String cache = styleVersion().equals(queryValue(exchange, "v"))
                    ? "public, max-age=31536000, immutable"
                    : "public, max-age=600";
            send(exchange, 200, read(STYLE_CSS), CONTENT_TYPES.get("css"), Map.of("Cache-Control", cache));
            return 200;
        }

        String name = path.substring(1);
        if (PUBLIC_FILES.containsKey(name)) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Cache-Control", "public, max-age=" + PUBLIC_FILES.get(name));
            if (SHAREABLE.contains(name)) {
                headers.put("Cross-Origin-Resource-Policy", "cross-origin");
            }
            send(exchange, 200, read(SITE.resolve(name)), contentType(name), headers);
            return 200;
        }

        send(exchange, 404, read(SITE.resolve("404.html")), CONTENT_TYPES.get("html"),
                Map.of("Content-Security-Policy", CSP_404, "Cache-Control", "no-cache"));
        return 404;
    }

    // ---------- The home page ----------

    /** index.html with the stylesheet's fingerprint filled in. It's small, so it's read fresh each visit. */
    static String homePage() {
        String page = new String(read(SITE.resolve("index.html")), StandardCharsets.UTF_8);
        return page.replace("href=\"style.css\"", "href=\"style.css?v=" + styleVersion() + "\"");
    }

    static int home(HttpExchange exchange) throws IOException {
        byte[] page = homePage().getBytes(StandardCharsets.UTF_8);
        String etag = "\"" + sha256(page).substring(0, 16) + "\"";
        // "no-cache" means the browser keeps a copy but checks with us before using it.
        // If nothing changed, we answer "304 Not Modified" and it reuses its copy.
        Map<String, String> headers = Map.of("Cache-Control", "no-cache", "ETag", etag);
        if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            send(exchange, 304, null, null, headers);
            return 304;
        }
        send(exchange, 200, page, CONTENT_TYPES.get("html"), headers);
        return 200;
    }

    // ---------- Sending responses ----------

    /** Sends a response with the security headers, squashing text when the browser allows it. */
    static void send(HttpExchange exchange, int status, byte[] body, String contentType, Map<String, String> extra)
            throws IOException {
        Headers headers = exchange.getResponseHeaders();
        extra.forEach(headers::set);
        // The page's own headers win; the security headers fill in the rest.
        SECURITY_HEADERS.forEach((name, value) -> {
            if (!headers.containsKey(name)) {
                headers.set(name, value);
            }
        });
        if (contentType != null) {
            headers.set("Content-Type", contentType);
        }

        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }

        boolean text = contentType != null && (contentType.startsWith("text/") || contentType.startsWith("image/svg")
                || contentType.startsWith("application/xml"));
        if (text) {
            headers.set("Vary", "Accept-Encoding");
            String accepts = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            if (body.length >= GZIP_MIN_SIZE && accepts != null && accepts.contains("gzip")) {
                body = gzip(body);
                headers.set("Content-Encoding", "gzip");
            }
        }

        if (exchange.getRequestMethod().equals("HEAD")) {
            // A HEAD request gets the headers it would have got, but no body.
            headers.set("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    // ---------- Small helpers ----------

    static byte[] plain(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static Map<String, String> withNoStore(Map<String, String> headers) {
        Map<String, String> all = new LinkedHashMap<>(headers);
        all.put("Cache-Control", "no-store");
        return all;
    }

    static String contentType(String name) {
        return CONTENT_TYPES.getOrDefault(name.substring(name.lastIndexOf('.') + 1), "application/octet-stream");
    }

    static String queryValue(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return "";
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(key)) {
                return pair.substring(equals + 1);
            }
        }
        return "";
    }

    static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException error) {
            throw new IllegalStateException("couldn't read " + path, error);
        }
    }

    static byte[] gzip(byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.length / 3);
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(body);
        }
        return out.toByteArray();
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }

    /** A map that keeps the order its entries were given in. */
    static Map<String, String> orderedMap(String... namesAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            map.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return map;
    }
}
