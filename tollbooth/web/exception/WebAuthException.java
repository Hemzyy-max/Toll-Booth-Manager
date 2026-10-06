package tollbooth.web.exception;

/**
 * ============================================================================
 *  FILE    : WebAuthException.java
 *  PACKAGE : tollbooth.web.exception
 * ----------------------------------------------------------------------------
 *  Thrown when a web request tries to do something that is not allowed :
 *   - no session token at all            -> HTTP 401 (login required)
 *   - a valid token but the wrong role   -> HTTP 403 (not allowed)
 *   - too many wrong passwords           -> HTTP 429 (slow down)
 *
 *  OOP CONCEPTS : exception handling (a custom CHECKED exception), inheritance
 *  from Exception, and encapsulation of the HTTP status code, so a handler can
 *  answer with the right code without any "if" chains.
 * ============================================================================
 */
public class WebAuthException extends Exception {

    /** Version number required for a serializable class. */
    private static final long serialVersionUID = 1L;

    /** HTTP status : 401 = not logged in, 403 = forbidden, 429 = too many tries. */
    private final int httpStatus;

    public WebAuthException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /** 401 - the user must log in. */
    public static WebAuthException notLoggedIn() {
        return new WebAuthException(401, "Please log in to continue.");
    }

    /** 401 - the session expired or was revoked. */
    public static WebAuthException sessionExpired() {
        return new WebAuthException(401, "Your session has expired. Please log in again.");
    }

    /** 403 - the user is logged in but has the wrong role. */
    public static WebAuthException forbidden(String what) {
        return new WebAuthException(403, "Only an administrator may " + what + ".");
    }

    /** 429 - the login form is temporarily blocked. */
    public static WebAuthException tooManyAttempts(long secondsLeft) {
        return new WebAuthException(429,
                "Too many wrong passwords. Please wait " + secondsLeft + " second(s) and try again.");
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
