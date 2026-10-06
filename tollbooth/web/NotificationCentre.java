package tollbooth.web;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================================
 *  FILE    : NotificationCentre.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  The notification centre of the system.
 *
 *  Every important action creates a notification :
 *      - a toll payment succeeded or failed   (payment category)
 *      - somebody logged in or out            (security category)
 *      - a password was changed               (security category)
 *      - the barrier failed / opened          (device category)
 *      - the day's collection crossed a limit (system category)
 *
 *  Each notification is
 *      1. stored in this centre (so the bell icon shows a list),
 *      2. pushed LIVE to the browsers through the EventHub, and
 *      3. written to the audit log file for later proof.
 *
 *  OOP CONCEPTS : collections (ConcurrentLinkedDeque - a thread safe queue used
 *  as a bounded history), encapsulation, and polymorphism through the
 *  Notification.Level / Audience enums.
 * ============================================================================
 */
public class NotificationCentre {

    /** How many notifications are kept in memory. */
    private static final int MAX_NOTIFICATIONS = 300;

    private final Deque<Notification> notifications = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalCreated = new AtomicLong();
    private final EventHub eventHub;
    private final AuditLog auditLog;

    public NotificationCentre(EventHub eventHub, AuditLog auditLog) {
        this.eventHub = eventHub;
        this.auditLog = auditLog;
    }

    /**
     * Creates a notification, pushes it to the browsers and logs it.
     *
     * @return the notification that was created
     */
    public Notification publish(Notification.Level level, String title, String message,
                                String category, Notification.Audience audience, String targetUsername) {
        Notification notification = new Notification(level, title, message, category,
                audience, targetUsername);

        notifications.addFirst(notification);
        while (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.removeLast();
        }
        totalCreated.incrementAndGet();

        // REAL TIME PUSH : every open browser receives this immediately.
        eventHub.broadcast("notification", notification.toJson());

        // Permanent proof in the audit file.
        String actor = (audience == Notification.Audience.USER) ? targetUsername : audience.name();
        auditLog.record("NOTIFY", actor == null ? "-" : actor, "-",
                "[" + level + "] " + title + " : " + message);

        return notification;
    }

    /** Shortcut methods, so the calling code stays readable. */
    public Notification info(String title, String message) {
        return publish(Notification.Level.INFO, title, message, "system",
                Notification.Audience.ALL, null);
    }

    public Notification success(String title, String message) {
        return publish(Notification.Level.SUCCESS, title, message, "system",
                Notification.Audience.ALL, null);
    }

    public Notification warning(String title, String message, String category) {
        return publish(Notification.Level.WARNING, title, message, category,
                Notification.Audience.ALL, null);
    }

    public Notification danger(String title, String message, String category) {
        return publish(Notification.Level.DANGER, title, message, category,
                Notification.Audience.ADMIN, null);
    }

    /** The notifications a particular user is allowed to see. */
    public List<Notification> visibleTo(User user, int limit) {
        List<Notification> visible = new ArrayList<>();
        for (Notification notification : notifications) {
            if (notification.isVisibleTo(user)) {
                visible.add(notification);
            }
            if (visible.size() >= limit) {
                break;
            }
        }
        return visible;
    }

    public int visibleCount(User user) {
        int count = 0;
        for (Notification notification : notifications) {
            if (notification.isVisibleTo(user)) {
                count++;
            }
        }
        return count;
    }

    /** Marks the whole centre as read for one user (returns how many changed). */
    public int markAllRead(User user) {
        int changed = 0;
        for (Notification notification : notifications) {
            if (notification.isVisibleTo(user) && !notification.isRead()) {
                notification.markRead();
                changed++;
            }
        }
        return changed;
    }

    /** Deletes every notification (admin action). */
    public int clear() {
        int size = notifications.size();
        notifications.clear();
        return size;
    }

    public long getTotalCreated() {
        return totalCreated.get();
    }

    public int size() {
        return notifications.size();
    }
}
