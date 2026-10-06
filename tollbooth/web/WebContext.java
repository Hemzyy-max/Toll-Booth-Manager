package tollbooth.web;

/**
 * ============================================================================
 *  FILE    : WebContext.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  Holds every service object of the web application in ONE place and gives it
 *  to the HTTP handlers.
 *
 *  This is the "context" or "application" object : instead of letting every
 *  handler build its own UserStore / EventHub (which would lose all the data),
 *  the server creates them once at start up and passes this object around.
 *
 *  OOP CONCEPTS : composition (the context HAS-A UserStore, HAS-A EventHub,
 *  HAS-A TollPaymentService ...), encapsulation (the fields are private and can
 *  only be read), and a single place to change the wiring of the application.
 * ============================================================================
 */
public class WebContext {

    private final UserStore userStore;
    private final SessionManager sessionManager;
    private final LoginGuard loginGuard;
    private final AuditLog auditLog;
    private final EventHub eventHub;
    private final NotificationCentre notifications;
    private final TollBoothEngine engine;
    private final TollPaymentService paymentService;

    private final boolean requestLogging;
    private final boolean simulatorEnabled;
    private volatile boolean running = true;
    private final long startedAt = System.currentTimeMillis();

    public WebContext(boolean requestLogging, boolean simulatorEnabled) {
        this.requestLogging = requestLogging;
        this.simulatorEnabled = simulatorEnabled;

        this.auditLog = new AuditLog();
        this.eventHub = new EventHub();
        this.notifications = new NotificationCentre(eventHub, auditLog);
        this.sessionManager = new SessionManager();
        this.loginGuard = new LoginGuard();
        this.userStore = new UserStore();

        this.engine = new TollBoothEngine();
        this.paymentService = new TollPaymentService(engine, eventHub, notifications, auditLog);

        // The automatic vehicle simulator uses the payment service, so the two
        // objects are wired together here ("late binding").
        this.engine.attachPaymentService(paymentService);
    }

    // ------------------------------------------------------------------
    //  one getter per service (read only access)
    // ------------------------------------------------------------------
    public UserStore getUserStore() {
        return userStore;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public LoginGuard getLoginGuard() {
        return loginGuard;
    }

    public AuditLog getAuditLog() {
        return auditLog;
    }

    public EventHub getEventHub() {
        return eventHub;
    }

    public NotificationCentre getNotifications() {
        return notifications;
    }

    public TollBoothEngine getEngine() {
        return engine;
    }

    public TollPaymentService getPaymentService() {
        return paymentService;
    }

    public boolean isRequestLogging() {
        return requestLogging;
    }

    public boolean isSimulatorEnabled() {
        return simulatorEnabled;
    }

    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startedAt) / 1000;
    }

    // ------------------------------------------------------------------
    //  life cycle
    // ------------------------------------------------------------------

    /** Called every minute by the server : housekeeping of the memory. */
    public void housekeeping() {
        int sessions = sessionManager.cleanup();
        loginGuard.cleanup();
        int clients = eventHub.cleanupDeadClients();
        if (requestLogging && (sessions > 0 || clients > 0)) {
            System.out.println("[housekeeping] removed " + sessions + " expired session(s), "
                    + clients + " dead stream(s)");
        }
    }

    /** The server is stopping : every browser is told through the live stream. */
    public void shutdown() {
        running = false;
        engine.stopSimulator();
        eventHub.closeAll("The server is stopping.");
        auditLog.record("SERVER-STOP", "system", "-", "Server stopped");
    }

    public boolean isRunning() {
        return running;
    }
}
