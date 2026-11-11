package eande;

import static eande.Check.contains;
import static eande.Check.equal;
import static eande.Check.isTrue;
import static eande.Check.lacks;
import static eande.Check.section;
import static eande.Check.test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
            contains(home.text(), "<title>E&amp;E Electrical &amp; Property Maintenance");
            isTrue(home.text().matches("(?s).*href=\"style\\.css\\?v=[0-9a-f]{8}\".*"), "style.css?v=<8 hex>");
        });

        test("the page has no JavaScript, only the structured data for Google", () -> {
            String page = Client.get("/").text();
            equal(1, page.split("<script", -1).length - 1);
            contains(page, "<script type=\"application/ld+json\">");
        });

        test("every section the menu links to exists", () -> {
            String page = Client.get("/").text();
            for (String id : new String[] {"services", "remodels", "why", "area", "faq", "contact"}) {
                contains(page, "href=\"#" + id + "\"");
                contains(page, "id=\"" + id + "\"");
            }
        });

        test("every page carries the security headers", () -> {
            for (String path : new String[] {"/", "/style.css", "/favicon.svg", "/nope"}) {
                Client.Reply reply = Client.get(path);
                for (Map.Entry<String, String> header : Server.SECURITY_HEADERS.entrySet()) {
                    if (path.equals("/nope") && header.getKey().equals("Content-Security-Policy")) {
                        continue; // checked separately: the 404 page has its own
                    }
                    equal(header.getValue(), reply.header(header.getKey()));
                }
            }
        });

        test("every FAQ question on the page is also in the structured data, word for word", () -> {
            String page = Client.get("/").text();
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
            Client.Reply zipped = Client.get("/", "Accept-Encoding: gzip, br");
            equal("gzip", zipped.header("Content-Encoding"));
            isTrue(zipped.body().length < plain.body().length / 2, "much smaller");
            byte[] unzipped = new GZIPInputStream(new ByteArrayInputStream(zipped.body())).readAllBytes();
            equal(plain.text(), new String(unzipped, StandardCharsets.UTF_8));
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

        test("the sitemap is served as XML and lists the home page", () -> {
            Client.Reply sitemap = Client.get("/sitemap.xml");
            equal(200, sitemap.status());
            equal("application/xml; charset=utf-8", sitemap.header("Content-Type"));
            contains(sitemap.text(), "<loc>https://eande.fly.dev/</loc>");
        });

        test("robots.txt allows everything and points to the sitemap", () -> {
            String robots = Client.get("/robots.txt").text();
            contains(robots, "Allow: /");
            contains(robots, "Sitemap: https://eande.fly.dev/sitemap.xml");
        });

        test("nothing else in the repo can be reached, even by name", () -> {
            for (String path : new String[] {"/README.md", "/index.html", "/404.html", "/Dockerfile",
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
}
