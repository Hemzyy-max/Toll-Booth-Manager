package tollbooth.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * ============================================================================
 *  FILE    : TollBoothServer.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  The web server of the project. It uses the HTTP server that is built into the
 *  JDK (com.sun.net.httpserver), so the application stays "no external library"
 *  and still speaks real HTTP in the browser.
 *
 *  WHAT IT DOES
 *   1. creates the WebContext (all the services),
 *   2. binds the port on 0.0.0.0 so the sandbox / host can reach it,
 *   3. registers every end point of the REST API and the static file handler,
 *   4. starts a small background task that cleans expired sessions every minute,
 *   5. registers a shutdown hook, so Ctrl+C stops the server neatly and tells
 *      the browsers through the live event stream.
 *
 *  OOP CONCEPTS : composition, multithreading (the server answers every request
 *  in its own thread from a thread pool), and the try-with-resources style
 *  start/stop lifecycle.
 * ============================================================================
 */
public class TollBoothServer {

    /** Default port. The hosting platform can change it with the PORT variable. */
    private static final int DEFAULT_PORT = 3000;

    private final int port;
    private final boolean requestLogging;
    private final boolean simulatorEnabled;

    private HttpServer httpServer;
    private ScheduledExecutorService housekeeping;
    private WebContext context;

    public TollBoothServer(int port, boolean requestLogging, boolean simulatorEnabled) {
        this.port = port;
        this.requestLogging = requestLogging;
        this.simulatorEnabled = simulatorEnabled;
    }

    /** Starts everything. */
    public void start() throws IOException {
        context = new WebContext(requestLogging, simulatorEnabled);

        httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // A thread pool : every request (and every live stream) gets its own
        // thread, so one slow request never blocks the others.
        httpServer.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "http-worker");
            thread.setDaemon(true);
            return thread;
        }));

        registerEndpoints();

        // Housekeeping thread : expired sessions, dead streams, old login limits.
        housekeeping = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "housekeeping");
            thread.setDaemon(true);
            return thread;
        });
        housekeeping.scheduleWithFixedDelay(context::housekeeping, 60, 60, TimeUnit.SECONDS);

        httpServer.start();
        context.getAuditLog().record("SERVER-START", "system", "-", "Listening on port " + port);
        context.getEngine().startSimulator(simulatorEnabled);

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "shutdown-hook"));
        printStartupMessage();
    }

    /** Registers all the routes. */
    private void registerEndpoints() {
        // --- health check end points ---------------------------------------
        // A hosting platform (or a load balancer) calls one of these to decide
        // whether the application is alive. They must ALWAYS answer 200, so the
        // page is never replaced by a "404 not found" screen.
        HttpHandler healthCheck = exchange -> HttpSupport.sendJson(exchange, 200, Json.obj()
                .put("status", "UP")
                .put("application", "IoT Based Toll Booth Manager")
                .put("uptimeSeconds", context.getUptimeSeconds())
                .put("vehicles", context.getEngine().getVehicleCount())
                .put("liveStreams", context.getEventHub().clientCount())
                .put("serverTime", java.time.LocalDateTime.now().toString()));
        httpServer.createContext("/health", healthCheck);
        httpServer.createContext("/healthz", healthCheck);
        httpServer.createContext("/api/health", healthCheck);
        httpServer.createContext("/api/ping", healthCheck);

        // --- REST API -----------------------------------------------------
        httpServer.createContext("/api/login", new ApiHandlers.LoginHandler(context));
        httpServer.createContext("/api/logout", new ApiHandlers.LogoutHandler(context));
        httpServer.createContext("/api/session", new ApiHandlers.SessionHandler(context));
        httpServer.createContext("/api/password", new ApiHandlers.PasswordHandler(context));
        httpServer.createContext("/api/payments", new ApiHandlers.PaymentsHandler(context));
        httpServer.createContext("/api/stats", new ApiHandlers.StatsHandler(context));
        httpServer.createContext("/api/vehicles/fastag", new ApiHandlers.VehiclesHandler(context));
        httpServer.createContext("/api/vehicles", new ApiHandlers.VehiclesHandler(context));
        httpServer.createContext("/api/rfid/simulate", new ApiHandlers.RfidSimulateHandler(context));
        httpServer.createContext("/api/notifications/read", new ApiHandlers.NotificationsHandler(context));
        httpServer.createContext("/api/notifications/clear", new ApiHandlers.NotificationsHandler(context));
        httpServer.createContext("/api/notifications", new ApiHandlers.NotificationsHandler(context));
        httpServer.createContext("/api/users", new ApiHandlers.UsersHandler(context));
        httpServer.createContext("/api/audit", new ApiHandlers.AuditHandler(context));

        // --- the live stream (Server Sent Events) --------------------------
        // The answer of this handler stays open as long as the browser is
        // connected. The server uses a cached thread pool, so a long lived
        // stream can never block the other requests.
        httpServer.createContext("/api/events", new ApiHandlers.EventsHandler(context));

        // --- the web page and its files ------------------------------------
        httpServer.createContext("/", new StaticHandler());
    }

    private void printStartupMessage() {
        System.out.println();
        System.out.println("=======================================================");
        System.out.println("  IOT BASED TOLL BOOTH MANAGER - REAL TIME WEB SERVER  ");
        System.out.println("=======================================================");
        System.out.println("  Listening on      : http://0.0.0.0:" + port);
        System.out.println("  Vehicles loaded   : " + context.getEngine().getVehicleCount());
        System.out.println("  Web users         : " + context.getUserStore().count());
        System.out.println("  Request logging   : " + (requestLogging ? "ON" : "OFF"));
        System.out.println("  Auto simulation   : " + (simulatorEnabled ? "ON" : "OFF"));
        System.out.println("  Audit file        : " + context.getAuditLog().getFilePath());
        System.out.println("-------------------------------------------------------");
        System.out.println("  Log in with the ADMIN or OPERATOR account printed above.");
        System.out.println("  Press Ctrl+C to stop the server.");
        System.out.println("=======================================================");
        System.out.println();
    }

    /** Stops the server (also called by the shutdown hook). */
    public void stop() {
        if (httpServer != null) {
            System.out.println();
            System.out.println("Stopping the toll booth server...");
            if (context != null) {
                context.shutdown();
            }
            if (housekeeping != null) {
                housekeeping.shutdownNow();
            }
            httpServer.stop(0);
            System.out.println("Server stopped. Goodbye.");
        }
    }

    public int getPort() {
        return port;
    }

    public WebContext getContext() {
        return context;
    }

    /** Reads the port from the PORT environment variable or from -Dport. */
    public static int resolvePort(String[] args) {
        String fromEnvironment = System.getenv("PORT");
        if (fromEnvironment != null) {
            try {
                return Integer.parseInt(fromEnvironment.trim());
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        for (int index = 0; index < args.length - 1; index++) {
            if ("--port".equals(args[index])) {
                try {
                    return Integer.parseInt(args[index + 1].trim());
                } catch (NumberFormatException ignored) {
                    // fall through to the default
                }
            }
        }
        return DEFAULT_PORT;
    }
}
