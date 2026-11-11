package eande;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The site's firewall: it looks at every request before the server does and
 * turns away the ones no real visitor would send.
 *
 * It blocks:
 *
 * - Anything other than reading pages (GET and HEAD). The site has no forms,
 *   so nobody needs to send it data.
 * - Requests for a site name that isn't this one, which is how bots that scan
 *   every IP address on the internet find servers.
 * - Absurdly long addresses and requests that carry a body.
 * - Visitors asking for too many pages too fast (rate limiting).
 * - Bots probing for things this site doesn't have, like /wp-login.php or /.env.
 *   One probe and that IP address is banned for an hour.
 *
 * Fly.io already only lets web traffic in (ports 80 and 443), so this covers the
 * part a network firewall can't: what's inside the requests.
 */
final class Firewall {
    private Firewall() {}

    /** A refusal: the status code, a short message, and any extra headers to send. */
    record Refusal(int status, String message, Map<String, String> headers) {
        Refusal(int status, String message) {
            this(status, message, Map.of());
        }
    }

    // The site names this server answers to. When you add your own domain, list it
    // in fly.toml under [env], e.g. ALLOWED_HOSTS = "example.com,www.example.com",
    // or visitors to that domain will be turned away.
    static final Set<String> ALLOWED_HOSTS = allowedHosts();

    static final Set<String> ALLOWED_METHODS = Set.of("GET", "HEAD");
    static final int MAX_URL_LENGTH = 2048;

    // Each visitor gets RATE_BURST requests straight away, then RATE_PER_SECOND more
    // every second. A person reading the page uses a handful; a flood uses thousands.
    static final double RATE_BURST = 60;
    static final double RATE_PER_SECOND = 2;

    static final long BAN_SECONDS = 60 * 60; // 1 hour

    // The most visitors the firewall keeps track of at once. A flood from thousands of
    // addresses could otherwise fill the server's memory with rate-limit records.
    // 50,000 records is a few megabytes.
    static final int MAX_TRACKED = 50_000;

    // Fly.io sets FLY_APP_NAME on its machines. Only there is Fly-Client-IP trustworthy:
    // anywhere else, a visitor could send the header themselves to dodge bans and rate
    // limits. The tests switch this on, since they stand in for Fly's front door.
    static boolean trustFlyHeader = System.getenv("FLY_APP_NAME") != null;

    // Addresses only an attack bot asks for: WordPress and PHP admin pages, leaked
    // secrets and git folders, and paths that try to climb out of the site folder.
    static final Pattern PROBE = Pattern.compile(
            "(\\.php|\\.aspx?|\\.cgi|\\.env|\\.git|\\.svn|\\.htaccess|\\.htpasswd|wp-|wordpress|phpmyadmin"
                    + "|xmlrpc|/admin|/cgi-bin|/vendor/|/actuator|\\.\\.|%2e%2e)",
            Pattern.CASE_INSENSITIVE);

    // Fly.io's health check comes from inside Fly, without the site's name.
    static final Set<String> EXEMPT_PATHS = Set.of("/healthz");

    // Visitor IP -> {tokens left, when they were last topped up}.
    private static final Map<String, double[]> buckets = new HashMap<>();
    // Visitor IP -> when their ban ends.
    private static final Map<String, Double> banned = new HashMap<>();
    private static double nextCleanup = 0;

    private static Set<String> allowedHosts() {
        Set<String> hosts = new HashSet<>(Set.of("eande.fly.dev", "localhost", "127.0.0.1"));
        String extra = System.getenv("ALLOWED_HOSTS");
        if (extra != null) {
            for (String host : extra.split(",")) {
                if (!host.isBlank()) {
                    hosts.add(host.strip().toLowerCase());
                }
            }
        }
        return Set.copyOf(hosts);
    }

    static String clientIp(HttpExchange exchange) {
        // Fly.io's front door puts the visitor's real address in Fly-Client-IP and
        // replaces any copy the visitor sends, so it can't be faked. Running locally,
        // there's no front door, so use the direct connection's address.
        String fly = exchange.getRequestHeaders().getFirst("Fly-Client-IP");
        if (trustFlyHeader && fly != null && !fly.isEmpty()) {
            return fly;
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    /**
     * Who a visitor counts as for bans and rate limits. An IPv4 address is one visitor.
     * With IPv6, one home or phone is handed a whole block of 18 quintillion addresses
     * (a "/64"), so a bot could use a new one for every request. The block counts as one visitor.
     */
    static String visitor(String ip) {
        if (ip.indexOf(':') < 0) {
            return ip;
        }
        try {
            // An address written out in numbers is only parsed here, never looked up online.
            InetAddress address = InetAddress.getByName(ip);
            if (address instanceof Inet6Address) {
                byte[] block = new byte[8];
                System.arraycopy(address.getAddress(), 0, block, 0, 8);
                return HexFormat.of().formatHex(block) + "::/64";
            }
            return address.getHostAddress();
        } catch (Exception notAnAddress) {
            return ip;
        }
    }

    private static double now() {
        return System.nanoTime() / 1e9;
    }

    private static boolean rateLimited(String ip, double now) {
        double[] bucket = buckets.computeIfAbsent(ip, k -> new double[] {RATE_BURST, now});
        bucket[0] = Math.min(RATE_BURST, bucket[0] + (now - bucket[1]) * RATE_PER_SECOND);
        bucket[1] = now;
        if (bucket[0] < 1) {
            return true;
        }
        bucket[0] -= 1;
        return false;
    }

    /** Forgets visitors who've gone quiet and bans that have run out, so memory can't fill up. */
    private static void cleanup(double now) {
        if (now < nextCleanup) {
            return;
        }
        nextCleanup = now + 60;
        double full = RATE_BURST / RATE_PER_SECOND; // after this long, a visitor's bucket is full again
        buckets.values().removeIf(bucket -> now - bucket[1] > full);
        banned.values().removeIf(until -> until <= now);
        // Still too many even after tidying up means a flood from many addresses.
        // Forgetting everyone's rate-limit count is better than running out of memory.
        // Bans are kept; new ones just stop being added until old ones run out.
        if (buckets.size() >= MAX_TRACKED) {
            buckets.clear();
        }
    }

    /** Lifts a ban. Only the tests use it, to clean up after themselves. */
    static synchronized void unbanForTests(String ip) {
        banned.remove(visitor(ip));
    }

    /** Returns a refusal turning the request away, or null to let it through. */
    static synchronized Refusal check(HttpExchange exchange) {
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        if (EXEMPT_PATHS.contains(path)) {
            return null;
        }

        double now = now();
        if (buckets.size() >= MAX_TRACKED) {
            nextCleanup = 0; // tidy up now rather than waiting for the minute to pass
        }
        cleanup(now);
        String ip = visitor(clientIp(exchange));

        if (banned.getOrDefault(ip, 0.0) > now) {
            return new Refusal(403, "Forbidden");
        }

        // Check both the address as sent and as decoded, so %-tricks can't hide a probe.
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        String asSent = uri.getRawPath() + "?" + query;
        if (PROBE.matcher(asSent).find() || PROBE.matcher(path).find()) {
            if (banned.size() < MAX_TRACKED) {
                banned.put(ip, now + BAN_SECONDS);
            }
            return new Refusal(403, "Forbidden");
        }

        if (!ALLOWED_METHODS.contains(exchange.getRequestMethod())) {
            return new Refusal(405, "Method Not Allowed", Map.of("Allow", "GET, HEAD"));
        }

        Headers headers = exchange.getRequestHeaders();
        String host = headers.getFirst("Host") == null ? "" : headers.getFirst("Host");
        int colon = host.lastIndexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        if (!ALLOWED_HOSTS.contains(host.toLowerCase())) {
            return new Refusal(421, "Misdirected Request");
        }

        if (("http://" + headers.getFirst("Host") + asSent).length() > MAX_URL_LENGTH) {
            return new Refusal(414, "URI Too Long");
        }

        String length = headers.getFirst("Content-Length");
        if ((length != null && !length.equals("0")) || headers.containsKey("Transfer-Encoding")) {
            return new Refusal(413, "Content Too Large");
        }

        if (rateLimited(ip, now)) {
            return new Refusal(429, "Too Many Requests", Map.of("Retry-After", "30"));
        }

        return null;
    }
}
