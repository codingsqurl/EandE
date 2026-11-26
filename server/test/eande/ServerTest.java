package eande;

import static eande.Check.contains;
import static eande.Check.equal;
import static eande.Check.isTrue;
import static eande.Check.lacks;
import static eande.Check.section;
import static eande.Check.test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** The server: which addresses give which pages, and the headers that come with them. */
final class ServerTest {
    private ServerTest() {}

    static void run() {
        section("Server");

        test("the home page loads, with the stylesheet's fingerprint filled in", () -> {
            Client.Reply home = Client.get("/");
            equal(200, home.status());
            equal("text/html; charset=utf-8", home.header("Content-Type"));
            contains(home.text(), "<title>E&amp;E Property Maintenance &amp; Home Improvement, LLC");
            isTrue(home.text().matches("(?s).*href=\"style\\.css\\?v=[0-9a-f]{8}\".*"), "style.css?v=<8 hex>");
        });

        test("the page has no JavaScript, only the structured data for Google", () -> {
            String page = Client.get("/").text();
            equal(1, page.split("<script", -1).length - 1);
            contains(page, "<script type=\"application/ld+json\">");
        });

        test("every section the menu links to exists", () -> {
            String page = Client.get("/").text();
            for (String id : new String[] {"why", "contact"}) {
                contains(page, "href=\"./#" + id + "\"");
                contains(page, "id=\"" + id + "\"");
            }
            // The menu lists each service by name; the back links still lead to the question bar.
            String menu = between(page, "<nav class=\"site-nav\"", "</nav>");
            for (String service : new String[] {"electrical", "maintenance", "trees", "concrete", "remodels"}) {
                contains(menu, "<a class=\"nav-service\" href=\"" + service + "\">");
            }
            contains(page, "id=\"services\"");
            contains(page, "<a href=\"areas\">Service area</a>");
            contains(page, "<a href=\"faq\">FAQ</a>");
        });

        test("the questions and answers have a page of their own", () -> {
            String faq = Client.get("/faq").text();
            contains(faq, "<h1>Common questions.</h1>");
            contains(faq, "\"@type\": \"FAQPage\"");
            lacks(Client.get("/").text(), "<details class=\"faq\">");
        });

        test("the areas page links each town to its own page, which shows the town on a Google map", () -> {
            String areas = Client.get("/areas").text();
            contains(areas, "<iframe class=\"map-frame\" src=\"https://www.google.com/maps");
            lacks(areas, "maps/search");
            for (String town : Server.TOWNS) {
                contains(areas, "<a class=\"town\" href=\"" + town + "\">");
            }
            String page = Client.get("/woodland-park").text();
            contains(page, "<h1>Woodland Park.</h1>");
            contains(page, "<iframe class=\"map-frame\" src=\"https://www.google.com/maps?q=Woodland+Park%2C+CO&amp;output=embed\"");
            contains(page, "<a class=\"back-link\" href=\"areas\">");
        });

        test("only the areas and town pages may load frames from Google", () -> {
            contains(Server.CSP_MAPS, "frame-src https://www.google.com");
            for (Map.Entry<String, String> page : Server.PAGES.entrySet()) {
                String expected = Server.showsMap(page.getValue()) ? Server.CSP_MAPS : Server.CSP;
                equal(expected, Client.get(page.getKey()).header("Content-Security-Policy"));
            }
            isTrue(Server.showsMap("areas.html") && Server.showsMap("aspen.html"), "map pages");
            isTrue(!Server.showsMap("index.html") && !Server.showsMap("faq.html"), "other pages");
        });

        test("the visitor counter is off without a token, and adds only Cloudflare's script with one", () -> {
            equal("", Server.analyticsToken(null));
            equal("", Server.analyticsToken(""));
            Check.fails(() -> Server.analyticsToken("abc\"><script>"));
            Check.fails(() -> Server.analyticsToken("short"));

            String page = "<html>\n  <body>\n    <p>Hi</p>\n  </body>\n</html>\n";
            equal(page, Server.withAnalytics(page, ""));
            equal(Server.CSP, Server.withAnalyticsCsp(Server.CSP, ""));

            String token = "0123456789abcdef0123456789abcdef";
            String counted = Server.withAnalytics(page, token);
            contains(counted, "<script defer src=\"https://static.cloudflareinsights.com/beacon.min.js\" "
                    + "data-cf-beacon='{\"token\": \"" + token + "\"}'></script>\n  </body>");
            equal(1, counted.split("<script", -1).length - 1);

            String csp = Server.withAnalyticsCsp(Server.CSP_MAPS, token);
            contains(csp, "script-src https://static.cloudflareinsights.com/beacon.min.js;");
            contains(csp, "connect-src 'self' https://cloudflareinsights.com");
            contains(csp, "frame-src https://www.google.com");
            lacks(csp, "script-src 'none'");
            lacks(csp, "unsafe-inline");
        });

        test("no page asks for an email address that doesn't exist yet", () -> {
            for (String path : Server.PAGES.keySet()) {
                lacks(Client.get(path).text(), "example.com");
            }
        });

        test("the question bar asks what's needed and links to a page for each service", () -> {
            String home = Client.get("/").text();
            contains(home, "What do you need help with?");
            for (String service : new String[] {"electrical", "maintenance", "trees", "concrete", "remodels"}) {
                contains(home, "<a class=\"picker-option\" href=\"" + service + "\">");
                equal(200, Client.get("/" + service).status());
            }
        });

        test("every service page loads, with its own title and address for Google", () -> {
            for (String path : Server.PAGES.keySet()) {
                Client.Reply reply = Client.get(path);
                equal(200, reply.status());
                equal("no-cache", reply.header("Cache-Control"));
                contains(reply.text(), "<link rel=\"canonical\" href=\"https://eande.fly.dev" + path + "\">");
                isTrue(reply.text().matches("(?s).*href=\"style\\.css\\?v=[0-9a-f]{8}\".*"), path + " style.css?v=<8 hex>");
            }
        });

        test("a service page marks itself on its question bar and has no JavaScript", () -> {
            String page = Client.get("/trees").text();
            contains(page, "<a class=\"picker-option\" href=\"trees\" aria-current=\"page\">");
            equal(1, page.split("aria-current", -1).length - 1);
            lacks(page, "<script");
        });

        test("every page shares the home page's header, contact section, footer and call bar", () -> {
            String home = Client.get("/").text();
            for (String path : Server.PAGES.keySet()) {
                String page = Client.get(path).text();
                for (String[] part : new String[][] {
                        {"<a class=\"skip-link\"", "</header>"},
                        {"<section id=\"contact\"", "</section>"},
                        {"<footer", "</html>"}}) {
                    equal(between(home, part[0], part[1]), between(page, part[0], part[1]));
                }
            }
        });

        test("the logo is the hero's background on every page, and nowhere else", () -> {
            String css = Client.get("/style.css").text();
            contains(between(css, ".hero::before {", "}"), "background: url(\"logo.webp\")");
            equal(1, css.split(java.util.regex.Pattern.quote("url(\"logo.webp\")"), -1).length - 1);
            for (String path : Server.PAGES.keySet()) {
                String page = Client.get(path).text();
                contains(page, "<section class=\"hero");
                contains(page, "<link rel=\"preload\" href=\"logo.webp\" as=\"image\" type=\"image/webp\">");
                lacks(page, "<img class=\"hero-logo\"");
            }
        });

        test("no page uses bullet-point separators", () -> {
            for (String path : Server.PAGES.keySet()) {
                lacks(Client.get(path).text(), "·");
            }
        });

        test("every page carries the security headers", () -> {
            for (String path : new String[] {"/", "/areas", "/style.css", "/favicon.svg", "/nope"}) {
                Client.Reply reply = Client.get(path);
                for (Map.Entry<String, String> header : Server.SECURITY_HEADERS.entrySet()) {
                    if ((path.equals("/nope") || path.equals("/areas")) && header.getKey().equals("Content-Security-Policy")) {
                        continue; // checked separately: the 404 and areas pages have their own
                    }
                    equal(header.getValue(), reply.header(header.getKey()));
                }
            }
        });

        test("every FAQ question on the page is also in the structured data, word for word", () -> {
            String page = Client.get("/faq").text();
            java.util.regex.Matcher question = java.util.regex.Pattern.compile("<summary>(.*?)</summary>").matcher(page);
            int found = 0;
            while (question.find()) {
                contains(page, "\"name\": \"" + question.group(1).replace("&amp;", "&") + "\"");
                found++;
            }
            isTrue(found >= 5, "at least 5 questions, got " + found);
        });

        test("the share image may be shown by other sites, the rest of the site may not", () -> {
            equal("cross-origin", Client.get("/og-image.png").header("Cross-Origin-Resource-Policy"));
            equal("same-origin", Client.get("/").header("Cross-Origin-Resource-Policy"));
            equal("same-origin", Client.get("/style.css").header("Cross-Origin-Resource-Policy"));
        });

        test("slow or stalled connections are cut off", () -> {
            Client.port();
            equal("10", System.getProperty("sun.net.httpserver.maxReqTime"));
            equal("30", System.getProperty("sun.net.httpserver.maxRspTime"));
            equal("30", System.getProperty("sun.net.httpserver.idleInterval"));
        });

        test("responses don't say what server software runs the site", () -> {
            equal(null, Client.get("/").header("Server"));
        });

        test("the home page is checked for changes on every visit", () -> {
            equal("no-cache", Client.get("/").header("Cache-Control"));
        });

        test("a repeat visit with an unchanged page gets 304 and no body", () -> {
            String etag = Client.get("/").header("ETag");
            Client.Reply again = Client.get("/", "If-None-Match: " + etag);
            equal(304, again.status());
            equal(0, again.body().length);
        });

        test("a different ETag gets the full page", () -> {
            equal(200, Client.get("/", "If-None-Match: \"stale\"").status());
        });

        test("HEAD gets the headers and length but no body", () -> {
            Client.Reply head = Client.send("HEAD", "/", Client.freshIp());
            equal(200, head.status());
            isTrue(Integer.parseInt(head.header("Content-Length")) > 1000, "a real length");
            equal(0, head.body().length);
        });

        test("text is squashed with gzip when the browser accepts it", () -> {
            Client.Reply plain = Client.get("/");
            Client.Reply zipped = Client.get("/", "Accept-Encoding: gzip");
            equal("gzip", zipped.header("Content-Encoding"));
            isTrue(zipped.body().length < plain.body().length / 2, "much smaller");
            byte[] unzipped = new GZIPInputStream(new ByteArrayInputStream(zipped.body())).readAllBytes();
            equal(plain.text(), new String(unzipped, StandardCharsets.UTF_8));
        });

        test("text squashed ahead of time with Brotli is sent to browsers that accept it", () -> {
            // Java can't decode Brotli either, so a stand-in file checks the server picks it up.
            Path file = Server.brotliFile(Client.get("/").body());
            boolean madeHere = !Files.exists(file);
            if (madeHere) {
                Files.createDirectories(file.getParent());
                Files.write(file, Server.plain("stand-in"));
            }
            try {
                Client.Reply reply = Client.get("/", "Accept-Encoding: gzip, deflate, br");
                equal("br", reply.header("Content-Encoding"));
                equal("Accept-Encoding", reply.header("Vary"));
                isTrue(Arrays.equals(Files.readAllBytes(file), reply.body()), "the squashed file's bytes");
                equal("gzip", Client.get("/", "Accept-Encoding: gzip, br;q=0").header("Content-Encoding"));
            } finally {
                if (madeHere) {
                    Files.delete(file);
                }
            }
        });

        test("text with no Brotli copy, like a file edited after the build, falls back to gzip", () -> {
            Path file = Server.brotliFile(Client.get("/").body());
            boolean had = Files.exists(file);
            byte[] saved = had ? Files.readAllBytes(file) : null;
            Files.deleteIfExists(file);
            try {
                equal("gzip", Client.get("/", "Accept-Encoding: gzip, br").header("Content-Encoding"));
            } finally {
                if (had) {
                    Files.write(file, saved);
                }
            }
        });

        test("Accept-Encoding is read word by word, and q=0 means no", () -> {
            isTrue(Server.accepts("gzip, deflate, br", "br"), "br listed");
            isTrue(Server.accepts("BR;q=0.5", "br"), "any case, any weight above 0");
            isTrue(!Server.accepts("gzip, br;q=0", "br"), "q=0");
            isTrue(!Server.accepts("brotli", "br"), "only the whole word");
            isTrue(!Server.accepts(null, "gzip"), "no header");
        });

        test("the fingerprinted stylesheet is kept for a year, a plain one for 10 minutes", () -> {
            equal("public, max-age=31536000, immutable",
                    Client.get("/style.css?v=" + Server.styleVersion()).header("Cache-Control"));
            equal("public, max-age=600", Client.get("/style.css").header("Cache-Control"));
            equal("public, max-age=600", Client.get("/style.css?v=old").header("Cache-Control"));
            equal("text/css; charset=utf-8", Client.get("/style.css").header("Content-Type"));
        });

        test("the public files load with the right types", () -> {
            equal("image/svg+xml", Client.get("/favicon.svg").header("Content-Type"));
            equal("image/webp", Client.get("/logo.webp").header("Content-Type"));
            equal("text/plain; charset=utf-8", Client.get("/robots.txt").header("Content-Type"));
            equal("public, max-age=86400", Client.get("/robots.txt").header("Cache-Control"));
            equal("image/png", Client.get("/og-image.png").header("Content-Type"));
            equal("image/png", Client.get("/apple-touch-icon.png").header("Content-Type"));
        });

        test("share previews and Google get the image, title and business details they need", () -> {
            String page = Client.get("/").text();
            contains(page, "<meta property=\"og:image\" content=\"https://eande.fly.dev/og-image.png\">");
            contains(page, "<meta name=\"twitter:card\" content=\"summary_large_image\">");
            contains(page, "<link rel=\"apple-touch-icon\" href=\"apple-touch-icon.png\">");
            contains(page, "\"openingHoursSpecification\"");
            contains(page, "\"hasOfferCatalog\"");
        });

        test("the sitemap is served as XML and lists every page", () -> {
            Client.Reply sitemap = Client.get("/sitemap.xml");
            equal(200, sitemap.status());
            equal("application/xml; charset=utf-8", sitemap.header("Content-Type"));
            for (String path : Server.PAGES.keySet()) {
                contains(sitemap.text(), "<loc>https://eande.fly.dev" + path + "</loc>");
            }
        });

        test("robots.txt allows everything and points to the sitemap", () -> {
            String robots = Client.get("/robots.txt").text();
            contains(robots, "Allow: /");
            contains(robots, "Sitemap: https://eande.fly.dev/sitemap.xml");
        });

        test("nothing else in the repo can be reached, even by name", () -> {
            for (String path : new String[] {"/README.md", "/index.html", "/electrical.html", "/404.html", "/Dockerfile",
                    "/fly.toml", "/run.sh", "/CHECKLIST.md", "/server/src/eande/Server.java", "/a/b/c"}) {
                equal(404, Client.get(path).status());
            }
        });

        test("missing pages get the 404 page, with inline styles allowed for it alone", () -> {
            Client.Reply missing = Client.get("/nope");
            equal(404, missing.status());
            contains(missing.text(), "<title>Page not found");
            lacks(missing.text(), "<script");
            equal(Server.CSP_404, missing.header("Content-Security-Policy"));
        });

        test("the health check answers ok, whatever site name Fly asks for", () -> {
            Client.Reply health = Client.send("GET", "/healthz", null, "Host: 10.0.0.5:8080");
            equal(200, health.status());
            equal("ok", health.text());
            equal("no-store", health.header("Cache-Control"));
        });

        test("the health check still only allows reading", () -> {
            Client.Reply post = Client.send("POST", "/healthz", null, "Content-Length: 0");
            equal(405, post.status());
            equal("GET, HEAD", post.header("Allow"));
        });
    }

    /** The text from the first start up to and including the end that follows it. */
    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        isTrue(from >= 0, "found <" + start + ">");
        return text.substring(from, text.indexOf(end, from) + end.length());
    }
}
