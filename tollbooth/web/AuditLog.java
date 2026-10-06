package tollbooth.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * ============================================================================
 *  FILE    : AuditLog.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  Writes a permanent security record of everything that happens :
 *  every login, every failed login, every logout, every password change,
 *  every payment and every notification.
 *
 *  The log is written to  data/audit.log  and the last 200 lines are also kept
 *  in memory so that the admin screen can show them without reading the file.
 *
 *  FILE HANDLING : the same idea as TransactionFileManager (append mode, so old
 *  lines are never lost) but with java.nio, because it is shorter for a simple
 *  append + read tail job. Both styles therefore appear in the project.
 * ============================================================================
 */
public class AuditLog {

    private static final Path LOG_FILE = Paths.get("data", "audit.log");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MEMORY_LIMIT = 200;

    /** The most recent lines, newest first. */
    private final Deque<String> recentLines = new ArrayDeque<>();

    public AuditLog() {
        try {
            Files.createDirectories(LOG_FILE.getParent());
        } catch (IOException e) {
            System.out.println("[warn] audit folder could not be created : " + e.getMessage());
        }
    }

    /** Adds one line to the audit trail. */
    public synchronized void record(String event, String actor, String clientAddress, String details) {
        String line = LocalDateTime.now().format(STAMP)
                + " | " + pad(event, 18)
                + " | " + pad(actor, 18)
                + " | " + pad(clientAddress, 15)
                + " | " + details;

        recentLines.addFirst(line);
        while (recentLines.size() > MEMORY_LIMIT) {
            recentLines.removeLast();
        }

        try {
            Files.write(LOG_FILE, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("[warn] audit line could not be written : " + e.getMessage());
        }
    }

    /** The recent entries for the admin screen. */
    public synchronized List<String> recent(int limit) {
        List<String> entries = new ArrayList<>();
        int count = 0;
        for (String line : recentLines) {
            if (count++ >= limit) {
                break;
            }
            entries.add(line);
        }
        return entries;
    }

    /** Full path of the file, shown in the admin screen. */
    public String getFilePath() {
        return LOG_FILE.toAbsolutePath().toString();
    }

    /** Fixed width text, so the log file is easy to read. */
    private static String pad(String text, int width) {
        String value = (text == null) ? "" : text;
        if (value.length() > width) {
            return value.substring(0, width);
        }
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < width) {
            builder.append(' ');
        }
        return builder.toString();
    }

    /** Small helper for exception details inside the log. */
    public static String describe(Throwable problem) {
        StringWriter writer = new StringWriter();
        problem.printStackTrace(new PrintWriter(writer));
        String text = writer.toString();
        return text.length() > 400 ? text.substring(0, 400) + "..." : text;
    }
}
