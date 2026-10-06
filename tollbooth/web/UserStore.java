package tollbooth.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ============================================================================
 *  FILE    : UserStore.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  The "database" of the login system : it keeps every account in memory and
 *  saves them to  data/users.json  after every change, so the accounts survive
 *  a restart of the server.
 *
 *  OOP CONCEPTS
 *   - COLLECTIONS : LinkedHashMap keeps the accounts in a fixed order and gives
 *     a very fast search by username.
 *   - ENCAPSULATION : the map is private, the passwords are stored only as
 *     PBKDF2 hashes and the class never returns its real map.
 *   - EXCEPTION HANDLING : a bad user file is reported and ignored instead of
 *     stopping the server.
 * ============================================================================
 */
public class UserStore {

    private static final Path USER_FILE = Paths.get("data", "users.json");

    /** Rules for a username and a password. */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,31}$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    /** Default accounts, created only when the user file does not exist yet. */
    private static final String DEFAULT_ADMIN_USER = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";
    private static final String DEFAULT_OPERATOR_USER = "operator";
    private static final String DEFAULT_OPERATOR_PASSWORD = "Operator@123";

    private final Map<String, User> usersByUsername = new LinkedHashMap<>();

    public UserStore() {
        load();
        createDefaultAccountsIfEmpty();
    }

    // ========================================================================
    //  START UP
    // ========================================================================

    private void createDefaultAccountsIfEmpty() {
        if (!usersByUsername.isEmpty()) {
            return;
        }
        String adminPassword = System.getenv().getOrDefault("TOLLBOOTH_ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);
        String operatorPassword = System.getenv().getOrDefault("TOLLBOOTH_OPERATOR_PASSWORD", DEFAULT_OPERATOR_PASSWORD);

        addUser(new User(DEFAULT_ADMIN_USER, "Toll Booth Administrator", User.Role.ADMIN,
                null, null, 0, true, LocalDateTime.now(), null, 0, false), adminPassword);
        addUser(new User(DEFAULT_OPERATOR_USER, "Booth Operator", User.Role.OPERATOR,
                null, null, 0, true, LocalDateTime.now(), null, 0, false), operatorPassword);

        System.out.println("-------------------------------------------------------------");
        System.out.println(" First start : two accounts were created");
        System.out.println("   ADMIN    : " + DEFAULT_ADMIN_USER + " / " + adminPassword);
        System.out.println("   OPERATOR : " + DEFAULT_OPERATOR_USER + " / " + operatorPassword);
        System.out.println(" (set TOLLBOOTH_ADMIN_PASSWORD / TOLLBOOTH_OPERATOR_PASSWORD to change)");
        System.out.println("-------------------------------------------------------------");
    }

    private void addUser(User user, String plainPassword) {
        user.setPassword(PasswordHasher.hash(plainPassword));
        usersByUsername.put(user.getUsername(), user);
        save();
    }

    // ========================================================================
    //  LOGIN
    // ========================================================================

    /**
     * Checks the credentials.
     *
     * @return the User object when everything is correct, otherwise null.
     *         "null for every kind of failure" is on purpose : the caller shows
     *         the same message for a wrong user and a wrong password, so an
     *         attacker cannot discover which usernames exist.
     */
    public User authenticate(String username, String password) {
        User user = findByUsername(username);
        if (user == null || !user.isActive()) {
            // Still do a hash calculation so the answer time is the same.
            PasswordHasher.verify(password == null ? "" : password,
                    "AAAAAAAAAAAAAAAAAAAAAA==", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", 120_000);
            return null;
        }
        if (!user.checkPassword(password)) {
            return null;
        }
        user.recordLogin();
        save();
        return user;
    }

    // ========================================================================
    //  QUERIES
    // ========================================================================

    public User findByUsername(String username) {
        if (username == null) {
            return null;
        }
        return usersByUsername.get(username.trim().toLowerCase());
    }

    public boolean exists(String username) {
        return findByUsername(username) != null;
    }

    /** A copy of the accounts, newest first (the internal map stays private). */
    public Collection<User> allUsers() {
        List<User> list = new ArrayList<>(usersByUsername.values());
        list.sort((first, second) -> second.getCreatedAt().compareTo(first.getCreatedAt()));
        return list;
    }

    public int count() {
        return usersByUsername.size();
    }

    // ========================================================================
    //  CHANGES (admin actions and self service)
    // ========================================================================

    /**
     * Creates a new account.
     *
     * @throws IllegalArgumentException when the username or the password breaks
     *         the rules of the system
     */
    public User createUser(String username, String displayName, String password, User.Role role,
                           boolean mustChangePassword) {
        String cleanUsername = (username == null) ? "" : username.trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(cleanUsername).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 3 to 32 characters : small letters, digits, dot, dash or underscore.");
        }
        if (usersByUsername.containsKey(cleanUsername)) {
            throw new IllegalArgumentException("The username " + cleanUsername + " already exists.");
        }
        validatePasswordStrength(password);

        String cleanDisplayName = (displayName == null || displayName.trim().isEmpty())
                ? cleanUsername : displayName.trim();
        User user = new User(cleanUsername, cleanDisplayName, role, null, null, 0, true,
                LocalDateTime.now(), null, 0, mustChangePassword);
        addUser(user, password);
        return user;
    }

    /** Enables or disables an account (a disabled user cannot log in). */
    public boolean setActive(String username, boolean active) {
        User user = findByUsername(username);
        if (user == null) {
            return false;
        }
        user.setActive(active);
        save();
        return true;
    }

    public boolean setRole(String username, User.Role role) {
        User user = findByUsername(username);
        if (user == null) {
            return false;
        }
        user.setRole(role);
        save();
        return true;
    }

    /** Change by the user himself (the old password is checked first). */
    public boolean changeOwnPassword(String username, String currentPassword, String newPassword) {
        User user = findByUsername(username);
        if (user == null || !user.checkPassword(currentPassword)) {
            return false;
        }
        validatePasswordStrength(newPassword);
        user.setPassword(PasswordHasher.hash(newPassword));
        user.setMustChangePassword(false);
        save();
        return true;
    }

    /** Reset by an administrator (a forgotten password). */
    public void resetPassword(String username, String newPassword, boolean mustChange) {
        User user = findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("Unknown user : " + username);
        }
        validatePasswordStrength(newPassword);
        user.setPassword(PasswordHasher.hash(newPassword));
        user.setMustChangePassword(mustChange);
        save();
    }

    /** @throws IllegalArgumentException when the password is too weak. */
    public void validatePasswordStrength(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must have at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException(
                    "Password must contain at least one letter and one digit.");
        }
    }

    // ========================================================================
    //  FILE HANDLING  (data/users.json)
    // ========================================================================

    /** Saves every account to the JSON file. */
    public synchronized void save() {
        Json.JsonArray array = Json.arr();
        for (User user : usersByUsername.values()) {
            array.add(Json.obj()
                    .put("username", user.getUsername())
                    .put("displayName", user.getDisplayName())
                    .put("role", user.getRole().name())
                    .put("active", user.isActive())
                    .put("createdAt", user.getCreatedAt().toString())
                    .put("lastLoginAt", (user.getLastLoginAt() == null) ? null : user.getLastLoginAt().toString())
                    .put("loginCount", user.getLoginCount())
                    .put("mustChangePassword", user.isMustChangePassword())
                    .put("passwordSalt", user.getPasswordSalt())
                    .put("passwordHash", user.getPasswordHash())
                    .put("passwordIterations", user.getPasswordIterations()));
        }
        try {
            Files.createDirectories(USER_FILE.getParent());
            Files.write(USER_FILE, array.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.out.println("[warn] users could not be saved : " + e.getMessage());
        }
    }

    /** Reads the accounts back from the JSON file. */
    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(USER_FILE)) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(USER_FILE), StandardCharsets.UTF_8);
            Object parsed = Json.parse(text);
            if (!(parsed instanceof List)) {
                System.out.println("[warn] users file has an unexpected format, starting empty");
                return;
            }
            for (Object item : (List<Object>) parsed) {
                Map<String, Object> map = (Map<String, Object>) item;
                User user = new User(
                        Json.text(map, "username"),
                        Json.text(map, "displayName"),
                        User.Role.fromText(Json.text(map, "role")),
                        Json.text(map, "passwordSalt"),
                        Json.text(map, "passwordHash"),
                        (int) Json.number(map, "passwordIterations", 120_000),
                        Json.flag(map, "active", true),
                        parseDate(Json.text(map, "createdAt")),
                        parseDate(Json.text(map, "lastLoginAt")),
                        (int) Json.number(map, "loginCount", 0),
                        Json.flag(map, "mustChangePassword", false));
                usersByUsername.put(user.getUsername(), user);
            }
            System.out.println("Loaded " + usersByUsername.size() + " user account(s) from " + USER_FILE);
        } catch (IOException | RuntimeException e) {
            System.out.println("[warn] users file could not be read : " + e.getMessage());
        }
    }

    private LocalDateTime parseDate(String text) {
        if (text == null || "null".equals(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
