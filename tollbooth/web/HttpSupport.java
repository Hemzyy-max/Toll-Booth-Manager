package tollbooth.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

/**
 * ============================================================================
 *  FILE    : HttpSupport.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  Small helper methods shared by every HTTP handler : reading the request body,
 *  sending a JSON / text / file answer, reading the query string and the bearer
 *  token, and adding the security headers.
 *
 *  Keeping these helpers in ONE class is the "do not repeat yourself" rule :
 *  every handler stays short and readable.
 * ============================================================================
 */
public final class HttpSupport {

    private static final Logger LOG = Logger.getLogger(HttpSupport.class.getName());

    /** Biggest request body the server accepts (1 MB) - a simple DoS guard. */
    public static final int MAX_BODY_BYTES = 1024 * 1024;

    private HttpSupport() {
    }

    // ========================================================================
    //  REQUEST HELPERS
    // ========================================================================

    /** Reads the whole request body as UTF-8 text with a size limit. */
    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new PayloadTooLargeException();
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Reads the body and parses it as a JSON object. */
    public static Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body.trim().isEmpty()) {
            return new HashMap<>();
        }
        return Json.parseObject(body);
    }

    /** Simple exception used to answer with HTTP 413. */
    public static class PayloadTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;

        public PayloadTooLargeException() {
            super("Request body is too large");
        }
    }

    /** Decodes the query string of the request (null safe). */
    public static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> parameters = new HashMap<>();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return parameters;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = (equals < 0) ? pair : pair.substring(0, equals);
            String value = (equals < 0) ? "" : pair.substring(equals + 1);
            parameters.put(urlDecode(name), urlDecode(value));
        }
        return parameters;
    }

    /** Turns + and %xx back into normal characters. */
    public static String urlDecode(String text) {
        try {
            return java.net.URLDecoder.decode(text, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * Reads the session token of the request.
     * It comes either from the "Authorization: Bearer xxx" header (used by
     * fetch() calls) or from the "?token=xxx" query parameter, because the
     * browser EventSource API cannot send custom headers for server sent events.
     */
    public static String bearerToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return query(exchange).get("token");
    }

    /** The real client address (the preview proxy sends X-Forwarded-For). */
    public static String clientAddress(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0) ? forwarded.trim() : forwarded.substring(0, comma).trim();
        }
        InetSocketAddress address = exchange.getRemoteAddress();
        return (address == null) ? "unknown" : address.getAddress().getHostAddress();
    }

    // ========================================================================
    //  RESPONSE HELPERS
    // ========================================================================

    /**
     * Sends a JSON answer.
     *
     * SECURITY NOTE : no "X-Frame-Options" and no "frame-ancestors" are sent on
     * purpose, so that the application can also be embedded in the preview
     * window of the hosting platform. Everything else is locked down with the
     * Content-Security-Policy below.
     */
    public static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        addSecurityHeaders(headers);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /** Sends a JSON error message : { "error" : "...." } */
    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Json.obj().put("error", message));
    }

    /** Sends a plain text answer. */
    public static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/plain; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        addSecurityHeaders(headers);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /** Sends a file (html, css, js, json) from the classpath. */
    public static void sendResource(HttpExchange exchange, int status, byte[] bytes,
                                    String contentType, boolean cache) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", cache ? "public, max-age=300" : "no-store");
        addSecurityHeaders(headers);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /** The security headers used by every answer of this server. */
    public static void addSecurityHeaders(Headers headers) {
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-XSS-Protection", "0");
        headers.set("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");
        headers.set("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
                        + "connect-src 'self'; base-uri 'none'; form-action 'self'; object-src 'none'");
    }

    /** Logs a problem without ever throwing out of a handler. */
    public static void logProblem(String where, Exception e) {
        LOG.log(Level.WARNING, where + " : " + e.getMessage(), e);
    }
}
