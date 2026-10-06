package tollbooth.web;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 *  FILE    : LoginGuard.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  Protects the login form against a brute force attack (trying thousands of
 *  passwords automatically).
 *
 *  RULE USED
 *   - 5 wrong passwords for the same "username + IP address" -> the account is
 *     locked for 60 seconds for that address.
 *   - After a successful login the counter is cleared.
 *   - Older entries are cleaned automatically so the map cannot grow forever.
 *
 *  OOP CONCEPTS : encapsulation (private map and private inner class),
 *  Collections (ConcurrentHashMap is a thread safe HashMap, which is needed
 *  because the HTTP server answers many requests at the same time).
 * ============================================================================
 */
public class LoginGuard {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_TIME = Duration.ofSeconds(60);
    private static final Duration MEMORY_TIME = Duration.ofMinutes(30);

    /** One counter per "username@ip". */
    private static final class Attempts {
        private int failures;
        private LocalDateTime lockedUntil;
        private LocalDateTime lastTry;
    }

    private final Map<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();

    /** @return true when this user+address combination is temporarily locked. */
    public boolean isLocked(String username, String clientAddress) {
        Attempts attempts = attemptsByKey.get(key(username, clientAddress));
        if (attempts == null || attempts.lockedUntil == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(attempts.lockedUntil)) {
            attemptsByKey.remove(key(username, clientAddress));    // lock has expired
            return false;
        }
        return true;
    }

    /** @return seconds left before a new attempt is allowed. */
    public long secondsLeft(String username, String clientAddress) {
        Attempts attempts = attemptsByKey.get(key(username, clientAddress));
        if (attempts == null || attempts.lockedUntil == null) {
            return 0;
        }
        long seconds = Duration.between(LocalDateTime.now(), attempts.lockedUntil).getSeconds();
        return Math.max(1, seconds);
    }

    /** Counts one more failure and locks the key when the limit is reached. */
    public void recordFailure(String username, String clientAddress) {
        String key = key(username, clientAddress);
        Attempts attempts = attemptsByKey.computeIfAbsent(key, ignored -> new Attempts());
        attempts.failures = attempts.failures + 1;
        attempts.lastTry = LocalDateTime.now();
        if (attempts.failures >= MAX_FAILURES) {
            attempts.lockedUntil = LocalDateTime.now().plus(LOCK_TIME);
        }
    }

    /** @return how many wrong passwords are still allowed before the lock. */
    public int remainingAttempts(String username, String clientAddress) {
        Attempts attempts = attemptsByKey.get(key(username, clientAddress));
        if (attempts == null) {
            return MAX_FAILURES;
        }
        return Math.max(0, MAX_FAILURES - attempts.failures);
    }

    /** Called after a successful login. */
    public void recordSuccess(String username, String clientAddress) {
        attemptsByKey.remove(key(username, clientAddress));
    }

    /** Removes forgotten counters so the map cannot grow without limit. */
    public void cleanup() {
        LocalDateTime limit = LocalDateTime.now().minus(MEMORY_TIME);
        attemptsByKey.entrySet().removeIf(entry -> {
            Attempts attempts = entry.getValue();
            return attempts.lastTry != null && attempts.lastTry.isBefore(limit);
        });
    }

    private String key(String username, String clientAddress) {
        String user = (username == null) ? "?" : username.toLowerCase();
        String address = (clientAddress == null) ? "?" : clientAddress;
        return user + "@" + address;
    }

    public int getMaxFailures() {
        return MAX_FAILURES;
    }
}
