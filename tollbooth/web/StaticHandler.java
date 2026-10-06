package tollbooth.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * ============================================================================
 *  FILE    : StaticHandler.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  Serves the web page of the application (HTML, CSS, JavaScript, favicon) from
 *  the classpath, so the whole site travels inside the compiled project and no
 *  external web server is needed.
 *
 *  Everything that is NOT a known file is answered with the login page, which
 *  makes the application a single page application : /dashboard, /anything ...
 *  all show index.html and the routing is done in the browser by the JavaScript.
 *
 *  SECURITY : only files of the "static" folder can be served, and the file name
 *  is checked, so a request like  /static/../../users.json  can never leave the
 *  folder (path traversal protection).
 * ============================================================================
 */
public class StaticHandler implements HttpHandler {

    /** File name -> content type. */
    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();

    static {
        CONTENT_TYPES.put("html", "text/html; charset=utf-8");
        CONTENT_TYPES.put("css", "text/css; charset=utf-8");
        CONTENT_TYPES.put("js", "application/javascript; charset=utf-8");
        CONTENT_TYPES.put("json", "application/json; charset=utf-8");
        CONTENT_TYPES.put("svg", "image/svg+xml");
        CONTENT_TYPES.put("png", "image/png");
        CONTENT_TYPES.put("ico", "image/x-icon");
        CONTENT_TYPES.put("txt", "text/plain; charset=utf-8");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // The browser asks for /favicon.ico on its own : answer with the icon
        // stored in the project, so no unnecessary 404 appears in the browser
        // developer console.
        if (path.equals("/favicon.ico")) {
            byte[] icon = readResource("favicon.svg");
            if (icon != null) {
                HttpSupport.sendResource(exchange, 200, icon, "image/svg+xml", true);
                exchange.close();
                return;
            }
        }

        if (path.startsWith("/api/")) {
            HttpSupport.sendError(exchange, 404, "Unknown API end point : " + path);
            return;
        }

        String resourceName = resolve(path);
        byte[] content = readResource(resourceName);

        if (content == null && !resourceName.equals("index.html")) {
            // Unknown page : give the single page application its start page.
            resourceName = "index.html";
            content = readResource(resourceName);
        }
        if (content == null) {
            HttpSupport.sendText(exchange, 500,
                    "index.html is missing from the build. Please compile the project again.");
            return;
        }

        String extension = resourceName.substring(resourceName.lastIndexOf('.') + 1);
        String contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
        boolean cache = !resourceName.equals("index.html");
        HttpSupport.sendResource(exchange, 200, content, contentType, cache);
        exchange.close();
    }

    /** Maps the URL to a file of the classpath, with the traversal check. */
    private String resolve(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "index.html";
        }
        String clean = path;
        if (clean.startsWith("/static/")) {
            clean = clean.substring("/static/".length());
        } else if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        clean = HttpSupport.urlDecode(clean);

        // SECURITY : no "..", no backslash, no absolute path.
        if (clean.contains("..") || clean.contains("\\") || clean.startsWith("/")
                || clean.indexOf('\0') >= 0) {
            return "index.html";
        }
        if (clean.isEmpty() || !clean.contains(".")) {
            return "index.html";          // a page name of the single page app
        }
        return clean;
    }

    /**
     * Reads a file from
     *   1. the classpath (inside the compiled "out" folder or the jar), which is
     *      the normal case, or
     *   2. the project folders "resources/static" and "web", used when the class
     *      files were compiled without copying the resources.
     * The first place that has the file wins.
     */
    private byte[] readResource(String name) {
        byte[] fromClasspath = readFromClasspath(name);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        return readFromDisk(name);
    }

    private byte[] readFromClasspath(String name) {
        String fullPath = "/static/" + name;
        try (InputStream input = StaticHandler.class.getResourceAsStream(fullPath)) {
            return (input == null) ? null : input.readAllBytes();
        } catch (IOException e) {
            HttpSupport.logProblem("classpath resource " + fullPath, e);
            return null;
        }
    }

    private byte[] readFromDisk(String name) {
        java.nio.file.Path[] places = {
                java.nio.file.Paths.get("resources", "static", name),
                java.nio.file.Paths.get("web", name),
                java.nio.file.Paths.get("static", name)
        };
        for (java.nio.file.Path place : places) {
            try {
                if (java.nio.file.Files.isRegularFile(place)) {
                    return java.nio.file.Files.readAllBytes(place);
                }
            } catch (IOException e) {
                HttpSupport.logProblem("file " + place, e);
            }
        }
        return null;
    }

    /** Small helper used by the console messages. */
    public static String describeEncoding() {
        return StandardCharsets.UTF_8.name();
    }
}
