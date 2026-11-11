package eande;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts one copy of the real server for the tests, and sends it raw HTTP.
 *
 * Raw HTTP over a socket, rather than Java's HTTP client, because the tests need
 * to send things a browser never would: a fake Host, a GET with a body, a POST.
 */
final class Client {
    private Client() {}

    /** What came back: the status, the headers (names in lower case) and the body. */
    record Reply(int status, Map<String, String> headers, byte[] body) {
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private static HttpServer server;
    private static int nextIp = 1;

    static synchronized int port() throws Exception {
        if (server == null) {
            // The tests play the part of Fly's front door, so they set Fly-Client-IP themselves.
            Firewall.trustFlyHeader = true;
            server = Server.start("127.0.0.1", 0);
        }
        return server.getAddress().getPort();
    }

    /** A visitor address no other test has used, so bans and rate limits don't leak between tests. */
    static synchronized String freshIp() {
        int n = nextIp++;
        return "198.51." + (n / 250) + "." + (n % 250 + 1);
    }

    /** A plain GET from a new visitor, as a browser would send it. */
    static Reply get(String path, String... headers) throws Exception {
        return send("GET", path, freshIp(), headers);
    }

    /**
     * Sends a request. headers are "Name: value" lines. A Host header is added
     * unless one is given, and a Fly-Client-IP header unless ip is null.
     */
    static Reply send(String method, String path, String ip, String... headers) throws Exception {
        StringBuilder request = new StringBuilder(method + " " + path + " HTTP/1.1\r\n");
        boolean hasHost = false;
        String body = "";
        for (String header : headers) {
            if (header.startsWith("BODY:")) {
                body = header.substring(5);
                continue;
            }
            hasHost |= header.toLowerCase().startsWith("host:");
            request.append(header).append("\r\n");
        }
        if (!hasHost) {
            request.append("Host: localhost\r\n");
        }
        if (ip != null) {
            request.append("Fly-Client-IP: ").append(ip).append("\r\n");
        }
        request.append("Connection: close\r\n\r\n").append(body);

        try (Socket socket = new Socket("127.0.0.1", port())) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            return parse(readAll(socket.getInputStream(), method.equals("HEAD")));
        }
    }

    private static byte[] readAll(InputStream in, boolean head) throws Exception {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = in.read(buffer)) != -1) {
                all.write(buffer, 0, read);
                // A HEAD reply has no body, so stop once the headers are in.
                if (head && all.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                    break;
                }
            }
        } catch (java.net.SocketTimeoutException timedOut) {
            // Fine: whatever arrived is the reply.
        }
        return all.toByteArray();
    }

    private static Reply parse(byte[] raw) {
        String text = new String(raw, StandardCharsets.ISO_8859_1);
        int end = text.indexOf("\r\n\r\n");
        if (end < 0) {
            throw new AssertionError("no complete reply: <" + text + ">");
        }
        String[] lines = text.substring(0, end).split("\r\n");
        int status = Integer.parseInt(lines[0].split(" ")[1]);
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            headers.put(lines[i].substring(0, colon).trim().toLowerCase(), lines[i].substring(colon + 1).trim());
        }
        byte[] body = java.util.Arrays.copyOfRange(raw, end + 4, raw.length);
        return new Reply(status, headers, body);
    }
}
