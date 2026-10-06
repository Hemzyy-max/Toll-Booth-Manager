package tollbooth.web;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 *  FILE    : SessionManager.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  Keeps the list of users who are logged in right now.
 *
 *  HOW THE LOGIN WORKS
 *   1. The user sends the username and the password to /api/login.
 *   2. UserStore checks the PBKDF2 hash. If it is correct, SessionManager
 *      creates a random session token (32 random bytes, URL safe base64).
 *   3. The browser keeps that token and sends it in the "Authorization: Bearer"
 *      header with every request, and as "?token=" for the live event stream.
 *   4. The token lives in the server memory only, so it can be revoked at any
 *      moment (logout, password change, admin action).
 *
 *  SECURITY POINTS SHOWN HERE
 *   - Random tokens (SecureRandom) : guessing is impossible.
 *   - SLIDING expiry : 30 minutes without any request and the session dies.
 *   - ABSOLUTE expiry : 8 hours, even for a busy session.
 *   - Thread safety : many requests touch this object at the same time, so a
 *     ConcurrentHashMap is used.
 * ============================================================================
 */
public class SessionManager {

    /** A session that nobody uses for this long is thrown away. */
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    /** Maximum life of a session, however active it is. */
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(8);

    /** One logged in user. */
    public static final class Session {
        private final String token;
        private final String username;
        private final User.Role role;
        private final String displayName;
        private final LocalDateTime createdAt;
        private final LocalDateTime expiresAbsolutely;
        private volatile LocalDateTime lastSeen;
        private final String clientAddress;

        Session(String token, User user, String clientAddress) {
            this.token = token;
            this.username = user.getUsername();
            this.displayName = user.getDisplayName();
            this.role = user.getRole();
            this.createdAt = LocalDateTime.now();
            this.expiresAbsolutely = createdAt.plus(ABSOLUTE_TIMEOUT);
            this.lastSeen = createdAt;
            this.clientAddress = clientAddress;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public User.Role getRole() {
            return role;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getLastSeen() {
            return lastSeen;
        }

        public String getClientAddress() {
            return clientAddress;
        }

        public boolean isAdmin() {
            return role == User.Role.ADMIN;
        }

        long idleSeconds() {
            return Duration.between(lastSeen, LocalDateTime.now()).getSeconds();
        }

        boolean isExpired() {
            LocalDateTime now = LocalDateTime.now();
            return now.isAfter(expiresAbsolutely)
                    || Duration.between(lastSeen, now).compareTo(IDLE_TIMEOUT) > 0;
        }
    }

    private final Map<String, Session> sessionsByToken = new ConcurrentHashMap<>();

    /** Creates a new session after a successful password check. */
    public Session create(User user, String clientAddress) {
        String token = PasswordHasher.randomToken(32);
        Session session = new Session(token, user, clientAddress);
        sessionsByToken.put(token, session);
        return session;
    }

    /**
     * Returns the session of a token, or null when the token is unknown or
     * expired. A valid session is "touched" (sliding expiry).
     */
    public Session validate(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Session session = sessionsByToken.get(token);
        if (session == null) {
            return null;
        }
        if (session.isExpired()) {
            sessionsByToken.remove(token);
            return null;
        }
        session.lastSeen = LocalDateTime.now();
        return session;
    }

    /** Ends one session (logout). */
    public boolean invalidate(String token) {
        return token != null && sessionsByToken.remove(token) != null;
    }

    /** Ends every session of a user (password change, account disabled). */
    public int invalidateAllForUser(String username) {
        int removed = 0;
        for (Map.Entry<String, Session> entry : sessionsByToken.entrySet()) {
            if (entry.getValue().getUsername().equalsIgnoreCase(username)) {
                sessionsByToken.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    /** Everyone who is online right now, sorted by name. */
    public List<String> onlineUsers() {
        List<String> users = new ArrayList<>();
        for (Session session : sessionsByToken.values()) {
            users.add(session.getDisplayName());
        }
        users.sort(Comparator.naturalOrder());
        return users;
    }

    /** Details of the live sessions, used by the admin screen. */
    public List<Session> activeSessions() {
        List<Session> list = new ArrayList<>(sessionsByToken.values());
        list.sort(Comparator.comparing(Session::getCreatedAt));
        return list;
    }

    public int size() {
        return sessionsByToken.size();
    }

    /** Removes expired sessions (called by the background cleaner). */
    public int cleanup() {
        int removed = 0;
        for (Map.Entry<String, Session> entry : sessionsByToken.entrySet()) {
            if (entry.getValue().isExpired()) {
                sessionsByToken.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    public long getIdleTimeoutMinutes() {
        return IDLE_TIMEOUT.toMinutes();
    }
}
