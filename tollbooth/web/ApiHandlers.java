package tollbooth.web;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import tollbooth.exception.InvalidPaymentException;
import tollbooth.exception.InvalidRFIDException;
import tollbooth.model.TollRates;
import tollbooth.web.exception.InvalidPaymentAmountException;
import tollbooth.web.exception.WebAuthException;

/**
 * ============================================================================
 *  FILE    : ApiHandlers.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  Every REST end point of the web application, written as a small class that
 *  extends ApiHandler. Writing one class per end point keeps the code readable,
 *  and the shared work (checking the session, checking the role, sending JSON,
 *  catching the exceptions) is written only ONCE in the parent class.
 *
 *  OOP CONCEPTS : inheritance (all handlers extend ApiHandler), abstraction
 *  (ApiHandler.handle() calls the abstract method doHandle()), interfaces
 *  (HttpHandler from the JDK), exception handling.
 *
 *  END POINTS
 *   POST /api/login                 start a session
 *   POST /api/logout                end the session
 *   GET  /api/session               who am I
 *   POST /api/password              change my password
 *   GET  /api/events                live stream (Server Sent Events)
 *   GET  /api/payments              recent payments (+ ?search=)
 *   POST /api/payments              collect a toll payment
 *   GET  /api/stats                 dashboard numbers
 *   GET  /api/vehicles              registered vehicles and FASTag balances
 *   POST /api/vehicles/fastag       recharge / enable / disable a FASTag wallet
 *   GET  /api/rfid/simulate         ask the RFID reader for the next tag
 *   GET  /api/notifications         my notifications
 *   POST /api/notifications/read    mark them as read
 *   POST /api/notifications/clear   empty the centre (admin)
 *   GET  /api/users                 accounts (admin)
 *   POST /api/users                 create / enable / disable / reset (admin)
 *   GET  /api/audit                 security log (admin)
 * ============================================================================
 */
public final class ApiHandlers {

    private ApiHandlers() {
    }

    // ========================================================================
    //  BASE CLASS : the shared behaviour of every end point
    // ========================================================================

    /**
     * The parent of every handler. It is "abstract", so it cannot be used
     * directly : it only provides the common frame of a request.
     */
    public abstract static class ApiHandler implements HttpHandler {

        protected final WebContext context;

        protected ApiHandler(WebContext context) {
            this.context = context;
        }

        /** The child class implements this : it is the real work of the end point. */
        protected abstract void doHandle(HttpExchange exchange) throws Exception;

        /**
         * final = the child classes may NOT change this method. It runs the
         * common steps around every request.
         */
        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            long startedAt = System.currentTimeMillis();
            try {
                // Same origin check : a page on another website may not send
                // commands to this server with the browser of a logged in user.
                checkOrigin(exchange);
                doHandle(exchange);
            } catch (WebAuthException e) {
                HttpSupport.sendError(exchange, e.getHttpStatus(), e.getMessage());
            } catch (InvalidRFIDException e) {
                HttpSupport.sendError(exchange, 400, "RFID problem : " + e.getMessage());
            } catch (InvalidPaymentException e) {
                HttpSupport.sendError(exchange, 402, "Payment refused : " + e.getMessage());
            } catch (InvalidPaymentAmountException e) {
                HttpSupport.sendError(exchange, 400, e.getMessage());
            } catch (IllegalArgumentException e) {
                HttpSupport.sendError(exchange, 400, e.getMessage());
            } catch (HttpSupport.PayloadTooLargeException e) {
                HttpSupport.sendError(exchange, 413, "The request body is too large.");
            } catch (Exception e) {
                HttpSupport.logProblem("[" + exchange.getRequestURI().getPath() + "]", e);
                try {
                    HttpSupport.sendError(exchange, 500, "Server error. Please try again.");
                } catch (IOException ignored) {
                    // the browser is already gone
                }
            } finally {
                exchange.close();
                if (context.isRequestLogging()) {
                    long tookMs = System.currentTimeMillis() - startedAt;
                    System.out.println("[web] " + exchange.getRequestMethod() + " "
                            + exchange.getRequestURI().getPath() + " (" + tookMs + " ms)");
                }
            }
        }

        // ------------------------------------------------------------------
        //  shared helpers
        // ------------------------------------------------------------------

        /** @return the logged in session, or throws 401. */
        protected SessionManager.Session requireSession(HttpExchange exchange) throws WebAuthException {
            String token = HttpSupport.bearerToken(exchange);
            SessionManager.Session session = context.getSessionManager().validate(token);
            if (session == null) {
                throw (token == null || token.isEmpty())
                        ? WebAuthException.notLoggedIn()
                        : WebAuthException.sessionExpired();
            }
            return session;
        }

        /** @return the logged in ADMIN, or throws 403. */
        protected SessionManager.Session requireAdmin(HttpExchange exchange) throws WebAuthException {
            SessionManager.Session session = requireSession(exchange);
            if (!session.isAdmin()) {
                throw WebAuthException.forbidden("use this part of the system");
            }
            return session;
        }

        /**
         * Cross site request forgery protection.
         *
         * The login system uses a Bearer token that the JavaScript keeps in the
         * browser and sends in a header. A page of another web site cannot read
         * that token and cannot add the header, so a forged request is not
         * possible in the first place. This method is therefore a second lock :
         * it only looks at requests that CHANGE something (POST / PUT / DELETE)
         * and that do NOT carry a token header.
         *
         * A GET request (the live event stream and the read only end points) is
         * never blocked, because a reverse proxy may rewrite the Host header.
         */
        protected void checkOrigin(HttpExchange exchange) throws WebAuthException {
            String method = exchange.getRequestMethod();
            if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method)
                    && !"DELETE".equalsIgnoreCase(method)) {
                return;
            }
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return;                        // a token that only our page can hold
            }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (origin == null || host == null) {
                return;                        // curl / tools : no Origin header
            }
            try {
                String originHost = java.net.URI.create(origin).getHost();
                String hostOnly = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
                if (originHost != null && !originHost.equalsIgnoreCase(hostOnly)
                        && !"localhost".equalsIgnoreCase(originHost)) {
                    throw new WebAuthException(403, "Request refused : wrong origin.");
                }
            } catch (IllegalArgumentException e) {
                throw new WebAuthException(403, "Request refused : broken origin header.");
            }
        }

        /** Reads a required text field of the JSON body. */
        protected String required(Map<String, Object> body, String field) {
            String value = Json.text(body, field);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("The field \"" + field + "\" is required.");
            }
            return value.trim();
        }

        /** The display name of the session user (or a fallback). */
        protected String displayName(SessionManager.Session session) {
            return (session == null) ? "System" : session.getDisplayName();
        }
    }

    // ========================================================================
    //  LOGIN / LOGOUT / SESSION
    // ========================================================================

    /** POST /api/login - checks the password and starts a session. */
    public static class LoginHandler extends ApiHandler {

        public LoginHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpSupport.sendError(exchange, 405, "Please use POST for the login.");
                return;
            }
            String address = HttpSupport.clientAddress(exchange);
            Map<String, Object> body = HttpSupport.readJsonObject(exchange);
            String username = required(body, "username").toLowerCase();
            String password = required(body, "password");

            // ---- brute force protection ----------------------------------
            if (context.getLoginGuard().isLocked(username, address)) {
                long seconds = context.getLoginGuard().secondsLeft(username, address);
                context.getAuditLog().record("LOGIN-BLOCKED", username, address,
                        "Too many wrong passwords, " + seconds + "s left");
                throw WebAuthException.tooManyAttempts(seconds);
            }

            User user = context.getUserStore().authenticate(username, password);
            if (user == null) {
                context.getLoginGuard().recordFailure(username, address);
                int left = context.getLoginGuard().remainingAttempts(username, address);
                context.getAuditLog().record("LOGIN-FAILED", username, address, "Wrong password or unknown user");

                // Tell the administrators that somebody is trying passwords.
                context.getNotifications().publish(Notification.Level.DANGER,
                        "Failed login attempt",
                        "Wrong password for \"" + username + "\" from " + address
                                + ". " + left + " attempt(s) left before the account is locked.",
                        "security", Notification.Audience.ADMIN, null);
                context.getEventHub().broadcast("login-failed", Json.obj()
                        .put("username", username)
                        .put("attemptsLeft", left)
                        .put("clientAddress", address));

                HttpSupport.sendError(exchange, 401,
                        "Invalid username or password. " + left + " attempt(s) left.");
                return;
            }

            // ---- success -------------------------------------------------
            context.getLoginGuard().recordSuccess(username, address);
            SessionManager.Session session = context.getSessionManager().create(user, address);
            context.getAuditLog().record("LOGIN", user.getUsername(), address, "Login successful");

            // LIVE EVENT : every open browser hears about the new session.
            context.getEventHub().broadcast("login", Json.obj()
                    .put("username", user.getUsername())
                    .put("displayName", user.getDisplayName())
                    .put("role", user.getRole().name())
                    .put("at", LocalDateTime.now().toString()));

            context.getNotifications().publish(Notification.Level.INFO,
                    "User signed in",
                    user.getDisplayName() + " (" + user.getRole() + ") signed in from " + address + ".",
                    "security", Notification.Audience.ALL, null);

            if (user.isMustChangePassword()) {
                context.getNotifications().publish(Notification.Level.WARNING,
                        "Password change required",
                        "The password of " + user.getDisplayName()
                                + " is a temporary one and must be changed.",
                        "security", Notification.Audience.USER, user.getUsername());
            }

            HttpSupport.sendJson(exchange, 200, Json.obj()
                    .put("token", session.getToken())
                    .put("user", user.toJson())
                    .put("expiresInMinutes", context.getSessionManager().getIdleTimeoutMinutes())
                    .put("onlineUsers", context.getSessionManager().onlineUsers()));
        }
    }

    /** POST /api/logout - ends the session. */
    public static class LogoutHandler extends ApiHandler {

        public LogoutHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireSession(exchange);
            context.getSessionManager().invalidate(session.getToken());
            context.getAuditLog().record("LOGOUT", session.getUsername(),
                    HttpSupport.clientAddress(exchange), "Session closed");

            context.getEventHub().broadcast("logout", Json.obj()
                    .put("username", session.getUsername())
                    .put("at", LocalDateTime.now().toString()));
            context.getNotifications().info("User signed out",
                    session.getDisplayName() + " signed out.");

            HttpSupport.sendJson(exchange, 200, Json.obj().put("ok", true));
        }
    }

    /** GET /api/session - who am I, plus the counters for the page header. */
    public static class SessionHandler extends ApiHandler {

        public SessionHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireSession(exchange);
            User user = context.getUserStore().findByUsername(session.getUsername());
            if (user == null) {
                throw WebAuthException.sessionExpired();
            }
            HttpSupport.sendJson(exchange, 200, Json.obj()
                    .put("user", user.toJson())
                    .put("onlineUsers", context.getSessionManager().onlineUsers())
                    .put("tabCount", context.getEventHub().clientCount())
                    .put("notifications", context.getNotifications().visibleCount(user))
                    .put("serverTime", LocalDateTime.now().toString())
                    .put("sessionStartedAt", session.getCreatedAt().toString()));
        }
    }

    /** POST /api/password - the user changes his own password. */
    public static class PasswordHandler extends ApiHandler {

        public PasswordHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireSession(exchange);
            Map<String, Object> body = HttpSupport.readJsonObject(exchange);
            String currentPassword = required(body, "currentPassword");
            String newPassword = required(body, "newPassword");

            if (currentPassword.equals(newPassword)) {
                throw new IllegalArgumentException("The new password must be different from the old one.");
            }
            boolean changed = context.getUserStore()
                    .changeOwnPassword(session.getUsername(), currentPassword, newPassword);
            if (!changed) {
                context.getLoginGuard().recordFailure(session.getUsername(),
                        HttpSupport.clientAddress(exchange));
                context.getAuditLog().record("PASSWORD-FAILED", session.getUsername(),
                        HttpSupport.clientAddress(exchange), "The current password was wrong");
                context.getNotifications().publish(Notification.Level.WARNING,
                        "Wrong current password",
                        session.getDisplayName() + " tried to change the password with a wrong old password.",
                        "security", Notification.Audience.ADMIN, null);
                throw new IllegalArgumentException("The current password is not correct.");
            }

            context.getAuditLog().record("PASSWORD-CHANGED", session.getUsername(),
                    HttpSupport.clientAddress(exchange), "Password changed successfully");
            context.getNotifications().publish(Notification.Level.SUCCESS,
                    "Password changed",
                    session.getDisplayName() + " changed the password. All other sessions were closed.",
                    "security", Notification.Audience.ADMIN, null);

            // Security : every other session of this user is closed. The current
            // one is kept open so the page the user is looking at does not break.
            List<SessionManager.Session> otherSessions = context.getSessionManager().activeSessions();
            String keepToken = session.getToken();
            for (SessionManager.Session other : otherSessions) {
                if (other.getUsername().equalsIgnoreCase(session.getUsername())
                        && !other.getToken().equals(keepToken)) {
                    context.getSessionManager().invalidate(other.getToken());
                }
            }

            HttpSupport.sendJson(exchange, 200, Json.obj().put("ok", true)
                    .put("message", "Password changed. Other sessions were closed."));
        }
    }

    // ========================================================================
    //  REAL TIME EVENT STREAM
    // ========================================================================

    /**
     * GET /api/events - the Server Sent Events stream.
     *
     * The handler does not end while the browser is connected : SSE is a long
     * lived answer, so the method keeps the connection open and pushes data
     * through the EventHub.
     */
    public static class EventsHandler extends ApiHandler {

        public EventsHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireSession(exchange);

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
            HttpSupport.addSecurityHeaders(exchange.getResponseHeaders());

            // 200 + length 0 means "chunked, the length is not known in advance".
            exchange.sendResponseHeaders(200, 0);

            EventHub.Client client = context.getEventHub().register(
                    session.getUsername(), session.getRole(),
                    HttpSupport.clientAddress(exchange), exchange.getResponseBody());

            context.getEventHub().replayHistory(client);
            context.getAuditLog().record("STREAM-OPEN", session.getUsername(),
                    HttpSupport.clientAddress(exchange), "Live stream opened");

            // Send the first event immediately so the page shows something.
            context.getEventHub().sendToUser(session.getUsername(), "hello", Json.obj()
                    .put("clientId", client.getClientId())
                    .put("serverTime", LocalDateTime.now().toString())
                    .put("pendingNotifications",
                            context.getNotifications().visibleCount(
                                    context.getUserStore().findByUsername(session.getUsername()))));

            // Keep the answer open while the browser is connected.
            while (!client.isDisconnected() && context.isRunning()) {
                Thread.sleep(700);
            }

            context.getEventHub().unregister(client);
            context.getAuditLog().record("STREAM-CLOSE", session.getUsername(),
                    HttpSupport.clientAddress(exchange), "Live stream closed");
        }
    }

    // ========================================================================
    //  PAYMENTS
    // ========================================================================

    /** GET /api/payments (list) and POST /api/payments (collect a toll). */
    public static class PaymentsHandler extends ApiHandler {

        public PaymentsHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireSession(exchange);
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> query = HttpSupport.query(exchange);
                int limit = (int) Math.min(200, Math.max(1, parseNumber(query.get("limit"), 25)));
                String search = query.get("search");
                HttpSupport.sendJson(exchange, 200, Json.obj()
                        .put("payments", search == null
                                ? context.getPaymentService().recentPaymentsJson(limit)
                                : context.getPaymentService().searchPayments(search, limit))
                        .put("stats", context.getPaymentService().statsJson()));
                return;
            }

            if (!"POST".equalsIgnoreCase(method)) {
                HttpSupport.sendError(exchange, 405, "Use GET to list or POST to pay.");
                return;
            }

            Map<String, Object> body = HttpSupport.readJsonObject(exchange);
            String rfidTag = required(body, "rfidTag").toUpperCase();
            String paymentMode = Json.text(body, "paymentMode");
            if (paymentMode == null) {
                paymentMode = "Cash";
            }
            Object amountValue = body.get("amount");
            boolean autoAmount = (amountValue == null);
            double amount = Json.number(body, "amount", 0);
            String source = Json.text(body, "rfidSource");
            if (source == null) {
                source = "manual-entry";
            }

            // The service runs the whole toll workflow and pushes the live events.
            PaymentRecord record = context.getPaymentService().processPayment(
                    session.getUsername(), session.getDisplayName(),
                    HttpSupport.clientAddress(exchange), rfidTag, paymentMode,
                    autoAmount, source, amount);

            HttpSupport.sendJson(exchange, 201, Json.obj()
                    .put("ok", true)
                    .put("payment", record.toJson())
                    .put("receipt", buildReceipt(record)));
        }

        private long parseNumber(String text, long fallback) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        /** The text of the printed receipt, built for the browser window. */
        private String buildReceipt(PaymentRecord record) {
            StringBuilder receipt = new StringBuilder();
            String line = "========================================\n";
            receipt.append(line);
            receipt.append("          TRANSACTION RECEIPT           \n");
            receipt.append(line);
            receipt.append("Receipt No     : ").append(record.getReceiptId()).append('\n');
            receipt.append("Transaction ID : ").append(record.getTransactionId()).append('\n');
            receipt.append("Vehicle Number : ").append(record.getVehicleNumber()).append('\n');
            receipt.append("Vehicle Type   : ").append(record.getVehicleType()).append('\n');
            receipt.append("RFID Tag       : ").append(record.getRfidTag()).append('\n');
            receipt.append("Toll Amount    : ").append(TollRates.formatAmount(record.getBaseToll())).append('\n');
            if (record.getDiscount() > 0) {
                receipt.append("FASTag Discount: -")
                        .append(TollRates.formatAmount(record.getDiscount())).append('\n');
            }
            receipt.append("Amount Paid    : ").append(TollRates.formatAmount(record.getPaidAmount())).append('\n');
            receipt.append("Payment Mode   : ").append(record.getPaymentMode()).append('\n');
            receipt.append("Operator       : ").append(record.getOperatorDisplayName()).append('\n');
            receipt.append("Date & Time    : ").append(record.getPaidAt()).append('\n');
            receipt.append("Status         : SUCCESS\n");
            receipt.append(line);
            receipt.append("Barrier        : OPENED and CLOSED\n");
            receipt.append("Vehicle Passed : YES\n");
            receipt.append(line);
            return receipt.toString();
        }
    }

    /** GET /api/stats - the dashboard numbers and charts. */
    public static class StatsHandler extends ApiHandler {

        public StatsHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireSession(exchange);

            Json.JsonObject stats = context.getPaymentService().statsJson();
            stats.put("serverTime", LocalDateTime.now().toString());
            stats.put("tabsConnected", context.getEventHub().clientCount());

            if (session.isAdmin()) {
                Json.JsonArray sessions = Json.arr();
                for (SessionManager.Session active : context.getSessionManager().activeSessions()) {
                    sessions.add(Json.obj()
                            .put("username", active.getUsername())
                            .put("displayName", active.getDisplayName())
                            .put("role", active.getRole().name())
                            .put("clientAddress", active.getClientAddress())
                            .put("startedAt", active.getCreatedAt().toString())
                            .put("idleSeconds", Duration.between(active.getLastSeen(),
                                    LocalDateTime.now()).getSeconds()));
                }
                stats.put("sessions", sessions);
                stats.put("clients", context.getEventHub().clientsToJson());
                stats.put("userCount", context.getUserStore().count());
                stats.put("simulatorOn", context.isSimulatorEnabled());
            }
            HttpSupport.sendJson(exchange, 200, stats);
        }
    }

    // ========================================================================
    //  VEHICLES AND FASTAG
    // ========================================================================

    /** GET /api/vehicles and POST /api/vehicles/fastag */
    public static class VehiclesHandler extends ApiHandler {

        public VehiclesHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireSession(exchange);
            String path = exchange.getRequestURI().getPath();

            if (path.endsWith("/fastag")) {
                handleFastag(exchange, session);
                return;
            }

            HttpSupport.sendJson(exchange, 200, Json.obj()
                    .put("vehicles", context.getEngine().vehiclesToJson())
                    .put("count", context.getEngine().getVehicleCount())
                    .put("fastagVehicleTypes", Json.arr().add("Truck").add("Bus"))
                    .put("rates", Json.obj()
                            .put("Bike", TollRates.BIKE_TOLL)
                            .put("Car", TollRates.CAR_TOLL)
                            .put("Bus", TollRates.BUS_TOLL)
                            .put("Truck", TollRates.TRUCK_TOLL)
                            .put("fastagDiscountPercent", TollRates.FASTAG_DISCOUNT_PERCENT)));
        }

        private void handleFastag(HttpExchange exchange, SessionManager.Session session)
                throws Exception {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpSupport.sendError(exchange, 405, "Please use POST for the FASTag action.");
                return;
            }
            Map<String, Object> body = HttpSupport.readJsonObject(exchange);
            String rfidTag = required(body, "rfidTag").toUpperCase();
            String action = Json.text(body, "action");
            if (action == null) {
                action = "recharge";
            }

            TollBoothEngine.VehicleSetup setup;
            switch (action.toLowerCase()) {
                case "recharge": {
                    double amount = Json.number(body, "amount", 0);
                    if (amount <= 0 || amount > 100_000) {
                        throw new IllegalArgumentException("Please enter a recharge amount between 1 and 100000.");
                    }
                    setup = context.getEngine().rechargeFastagWallet(rfidTag, amount);
                    context.getNotifications().publish(Notification.Level.INFO,
                            "FASTag recharged",
                            setup.getVehicleNumber() + " wallet recharged by "
                                    + TollRates.formatAmount(amount) + ". New balance : "
                                    + TollRates.formatAmount(setup.getFastagBalance()) + ".",
                            "fastag", Notification.Audience.ALL, null);
                    context.getAuditLog().record("FASTAG-RECHARGE", session.getUsername(),
                            HttpSupport.clientAddress(exchange),
                            setup.getVehicleNumber() + " +" + TollRates.formatAmount(amount));
                    break;
                }
                case "enable":
                case "disable": {
                    boolean enable = "enable".equalsIgnoreCase(action);
                    // Only an administrator may switch a wallet off or on.
                    if (!session.isAdmin()) {
                        throw WebAuthException.forbidden("switch a FASTag wallet on or off");
                    }
                    TollBoothEngine.VehicleSetup existing = context.getEngine().findSetup(rfidTag);
                    if (existing == null) {
                        throw new IllegalArgumentException("Unknown RFID tag : " + rfidTag);
                    }
                    if (!context.getEngine().isFastagVehicle(existing.getVehicleType())) {
                        throw new IllegalArgumentException("A " + existing.getVehicleType()
                                + " does not carry a FASTag.");
                    }
                    existing.setFastagEnabled(enable);
                    setup = existing;
                    context.getNotifications().publish(enable ? Notification.Level.INFO : Notification.Level.WARNING,
                            "FASTag " + (enable ? "enabled" : "disabled"),
                            existing.getVehicleNumber() + " FASTag wallet is now "
                                    + (enable ? "active" : "blocked") + ".",
                            "fastag", Notification.Audience.ALL, null);
                    context.getAuditLog().record("FASTAG-" + (enable ? "ENABLED" : "DISABLED"),
                            session.getUsername(), HttpSupport.clientAddress(exchange),
                            existing.getVehicleNumber());
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown FASTag action : " + action);
            }

            context.getEventHub().broadcast("vehicle-changed", Json.obj().put("vehicle", setup.toJson()));
            HttpSupport.sendJson(exchange, 200, Json.obj()
                    .put("ok", true)
                    .put("vehicle", setup.toJson()));
        }
    }

    /** GET /api/rfid/simulate - the reader picks the next tag by itself. */
    public static class RfidSimulateHandler extends ApiHandler {

        public RfidSimulateHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireSession(exchange);
            String rfidTag = context.getEngine().simulateRfidScan();
            if (rfidTag == null) {
                throw new IllegalArgumentException("No vehicle is registered at this booth.");
            }
            TollBoothEngine.VehicleSetup setup = context.getEngine().findSetup(rfidTag);

            context.getEventHub().broadcast("rfid-scan", Json.obj()
                    .put("rfidTag", rfidTag)
                    .put("scannedBy", session.getDisplayName())
                    .put("at", LocalDateTime.now().toString()));

            HttpSupport.sendJson(exchange, 200, Json.obj()
                    .put("rfidTag", rfidTag)
                    .put("vehicle", (setup == null) ? null : setup.toJson())
                    .put("suggestedPaymentMode", context.getEngine().randomPayableMode(rfidTag)));
        }
    }

    // ========================================================================
    //  NOTIFICATION CENTRE
    // ========================================================================

    /** GET /api/notifications, POST /api/notifications/read, /clear */
    public static class NotificationsHandler extends ApiHandler {

        public NotificationsHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireSession(exchange);
            User user = context.getUserStore().findByUsername(session.getUsername());
            if (user == null) {
                throw WebAuthException.sessionExpired();
            }
            String path = exchange.getRequestURI().getPath();

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                int limit = 50;
                String limitText = HttpSupport.query(exchange).get("limit");
                if (limitText != null) {
                    try {
                        limit = Math.min(200, Math.max(1, Integer.parseInt(limitText)));
                    } catch (NumberFormatException ignored) {
                        limit = 50;
                    }
                }
                Json.JsonArray array = Json.arr();
                for (Notification notification : context.getNotifications().visibleTo(user, limit)) {
                    array.add(notification.toJson());
                }
                HttpSupport.sendJson(exchange, 200, Json.obj()
                        .put("notifications", array)
                        .put("unread", context.getNotifications().visibleCount(user))
                        .put("totalCreated", context.getNotifications().getTotalCreated()));
                return;
            }

            if (path.endsWith("/clear")) {
                if (!session.isAdmin()) {
                    throw WebAuthException.forbidden("empty the whole notification centre");
                }
                int removed = context.getNotifications().clear();
                context.getAuditLog().record("NOTIFY-CLEAR", session.getUsername(),
                        HttpSupport.clientAddress(exchange), removed + " notifications removed");
                context.getEventHub().broadcast("notification-cleared",
                        Json.obj().put("by", session.getDisplayName()).put("removed", removed));
                context.getNotifications().info("Notification centre cleared",
                        session.getDisplayName() + " removed " + removed + " notification(s).");
                HttpSupport.sendJson(exchange, 200, Json.obj().put("ok", true).put("removed", removed));
                return;
            }

            // Default POST action : mark my notifications as read.
            int changed = context.getNotifications().markAllRead(user);
            context.getEventHub().sendToUser(session.getUsername(), "notifications-read",
                    Json.obj().put("count", changed));
            HttpSupport.sendJson(exchange, 200, Json.obj().put("ok", true).put("markedRead", changed));
        }
    }

    // ========================================================================
    //  USER ADMINISTRATION AND AUDIT (administrators only)
    // ========================================================================

    /** GET /api/users and POST /api/users (create / status / role / reset). */
    public static class UsersHandler extends ApiHandler {

        public UsersHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            SessionManager.Session session = requireAdmin(exchange);

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Json.JsonArray users = Json.arr();
                for (User user : context.getUserStore().allUsers()) {
                    users.add(user.toJson());
                }
                HttpSupport.sendJson(exchange, 200, Json.obj()
                        .put("users", users)
                        .put("count", context.getUserStore().count())
                        .put("minPasswordLength", 8));
                return;
            }

            Map<String, Object> body = HttpSupport.readJsonObject(exchange);
            String action = Json.text(body, "action");
            if (action == null) {
                action = "create";
            }

            switch (action.toLowerCase()) {
                case "create": {
                    String username = required(body, "username").toLowerCase();
                    String displayName = Json.text(body, "displayName");
                    String password = required(body, "password");
                    User.Role role = User.Role.fromText(Json.text(body, "role"));
                    boolean mustChange = Json.flag(body, "mustChangePassword", true);

                    User created = context.getUserStore().createUser(username, displayName,
                            password, role, mustChange);
                    context.getAuditLog().record("USER-CREATED", session.getUsername(),
                            HttpSupport.clientAddress(exchange),
                            created.getUsername() + " as " + created.getRole());
                    context.getNotifications().publish(Notification.Level.INFO,
                            "New " + role.name().toLowerCase() + " account created",
                            session.getDisplayName() + " created the account \""
                                    + created.getUsername() + "\".",
                            "security", Notification.Audience.ALL, null);
                    HttpSupport.sendJson(exchange, 201, Json.obj().put("ok", true)
                            .put("user", created.toJson()));
                    return;
                }
                case "status": {
                    String username = required(body, "username").toLowerCase();
                    boolean active = Json.flag(body, "active", true);
                    if (username.equals(session.getUsername()) && !active) {
                        throw new IllegalArgumentException("You cannot disable your own account.");
                    }
                    if (!context.getUserStore().setActive(username, active)) {
                        throw new IllegalArgumentException("Unknown user : " + username);
                    }
                    // A disabled user must not stay logged in.
                    int closed = active ? 0 : context.getSessionManager().invalidateAllForUser(username);
                    context.getAuditLog().record(active ? "USER-ENABLED" : "USER-DISABLED",
                            session.getUsername(), HttpSupport.clientAddress(exchange),
                            username + (active ? "" : " (" + closed + " session(s) closed)"));
                    context.getNotifications().publish(
                            active ? Notification.Level.INFO : Notification.Level.WARNING,
                            "Account " + (active ? "enabled" : "disabled"),
                            "\"" + username + "\" was " + (active ? "enabled" : "disabled")
                                    + " by " + session.getDisplayName() + ".",
                            "security", Notification.Audience.ALL, null);
                    HttpSupport.sendJson(exchange, 200, Json.obj().put("ok", true)
                            .put("sessionsClosed", closed));
                    return;
                }
                case "role": {
                    String username = required(body, "username").toLowerCase();
                    User.Role role = User.Role.fromText(Json.text(body, "role"));
                    if (username.equals(session.getUsername()) && role != session.getRole()) {
                        throw new IllegalArgumentException("You cannot change your own role.");
                    }
                    if (!context.getUserStore().setRole(username, role)) {
                        throw new IllegalArgumentException("Unknown user : " + username);
                    }
                    context.getSessionManager().invalidateAllForUser(username);
                    context.getAuditLog().record("USER-ROLE", session.getUsername(),
                            HttpSupport.clientAddress(exchange), username + " -> " + role);
                    context.getNotifications().publish(Notification.Level.INFO,
                            "Role changed",
                            "\"" + username + "\" is now " + role + ". The user must log in again.",
                            "security", Notification.Audience.ALL, null);
                    HttpSupport.sendJson(exchange, 200, Json.obj().put("ok", true));
                    return;
                }
                case "reset": {
                    String username = required(body, "username").toLowerCase();
                    String password = required(body, "password");
                    context.getUserStore().resetPassword(username, password, true);
                    context.getSessionManager().invalidateAllForUser(username);
                    context.getAuditLog().record("PASSWORD-RESET", session.getUsername(),
                            HttpSupport.clientAddress(exchange), "Password reset for " + username);
                    context.getNotifications().publish(Notification.Level.WARNING,
                            "Password reset by administrator",
                            "The password of \"" + username + "\" was reset. The user must change it "
                                    + "at the next login.", "security", Notification.Audience.ALL, null);
                    HttpSupport.sendJson(exchange, 200, Json.obj().put("ok", true));
                    return;
                }
                default:
                    throw new IllegalArgumentException("Unknown user action : " + action);
            }
        }
    }

    /** GET /api/audit - the security log (administrators only). */
    public static class AuditHandler extends ApiHandler {

        public AuditHandler(WebContext context) {
            super(context);
        }

        @Override
        protected void doHandle(HttpExchange exchange) throws Exception {
            requireAdmin(exchange);
            int limit = 60;
            String limitText = HttpSupport.query(exchange).get("limit");
            if (limitText != null) {
                try {
                    limit = Math.min(300, Math.max(1, Integer.parseInt(limitText)));
                } catch (NumberFormatException ignored) {
                    limit = 60;
                }
            }
            Json.JsonArray entries = Json.arr();
            for (String line : context.getAuditLog().recent(limit)) {
                entries.add(line);
            }
            HttpSupport.sendJson(exchange, 200, Json.obj()
                    .put("entries", entries)
                    .put("file", context.getAuditLog().getFilePath()));
        }
    }
}
