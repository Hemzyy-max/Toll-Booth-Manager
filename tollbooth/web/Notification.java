package tollbooth.web;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================================
 *  FILE    : Notification.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  One message that is shown in the notification centre of the web page.
 *
 *  OOP CONCEPTS
 *   - ENCAPSULATION : the fields are private and final (a notification never
 *     changes after it is created), read through getters.
 *   - ENUM : "Level" is a fixed list, so the front end can colour the message
 *     (success = green, warning = orange, danger = red, info = blue).
 *   - STATIC COUNTER : every notification gets its own id (N1001, N1002 ...).
 * ============================================================================
 */
public class Notification {

    /** How important the message is. */
    public enum Level {
        INFO,
        SUCCESS,
        WARNING,
        DANGER
    }

    /** Whom the message is meant for. */
    public enum Audience {
        /** Every logged in user. */
        ALL,
        /** Only the administrators. */
        ADMIN,
        /** Only the user with this username. */
        USER
    }

    private static final AtomicLong ID_SOURCE = new AtomicLong(1000);

    private final String id;
    private final Level level;
    private final String title;
    private final String message;
    private final String category;
    private final Audience audience;
    private final String targetUsername;
    private final LocalDateTime createdAt;
    private boolean read;

    public Notification(Level level, String title, String message, String category,
                        Audience audience, String targetUsername) {
        this.id = "N" + ID_SOURCE.incrementAndGet();
        this.level = level;
        this.title = title;
        this.message = message;
        this.category = category;
        this.audience = audience;
        this.targetUsername = (audience == Audience.USER) ? targetUsername : null;
        this.createdAt = LocalDateTime.now();
        this.read = false;
    }

    // ------------------------------------------------------------------
    //  getters
    // ------------------------------------------------------------------
    public String getId() {
        return id;
    }

    public Level getLevel() {
        return level;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getCategory() {
        return category;
    }

    public Audience getAudience() {
        return audience;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }

    /**
     * Can the given user see this notification ?
     * This is a small example of an access rule written as a method instead of
     * spreading "if" statements all over the server.
     */
    public boolean isVisibleTo(User user) {
        if (user == null) {
            return false;
        }
        switch (audience) {
            case ALL:
                return true;
            case ADMIN:
                return user.isAdmin();
            case USER:
                return user.getUsername().equalsIgnoreCase(targetUsername);
            default:
                return false;
        }
    }

    /** The JSON form sent to the browser. */
    public Json.JsonObject toJson() {
        return Json.obj()
                .put("id", id)
                .put("level", level.name().toLowerCase())
                .put("title", title)
                .put("message", message)
                .put("category", category)
                .put("audience", audience.name())
                .put("targetUsername", targetUsername)
                .put("createdAt", createdAt.toString())
                .put("read", read);
    }

    @Override
    public String toString() {
        return "[" + level + "] " + title + " - " + message;
    }
}
