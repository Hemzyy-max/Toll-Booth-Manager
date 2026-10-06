package tollbooth.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ============================================================================
 *  FILE    : User.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  One login account of the toll booth system.
 *
 *  OOP CONCEPTS
 *   - ENCAPSULATION : every field is private, the password is never stored as
 *     text (only the PBKDF2 hash + salt), and the object can be changed only
 *     through its methods.
 *   - ENUM : "Role" is an enum, which is the Java way of writing a fixed list
 *     of constants. Using an enum instead of the text "ADMIN" prevents spelling
 *     mistakes : the compiler checks it.
 * ============================================================================
 */
public class User {

    /** The two kinds of accounts of the system. */
    public enum Role {
        /** Full control : users, audit log, everything an operator can do. */
        ADMIN,
        /** Works at the booth : processes toll payments. */
        OPERATOR;

        public static Role fromText(String text) {
            if (text == null) {
                return OPERATOR;
            }
            try {
                return Role.valueOf(text.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return OPERATOR;
            }
        }
    }

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    // ENCAPSULATION : private data members
    private final String username;
    private String displayName;
    private String passwordSalt;
    private String passwordHash;
    private int passwordIterations;
    private Role role;
    private boolean active;
    private final LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private int loginCount;
    private boolean mustChangePassword;

    public User(String username, String displayName, Role role,
                String passwordSalt, String passwordHash, int passwordIterations,
                boolean active, LocalDateTime createdAt, LocalDateTime lastLoginAt,
                int loginCount, boolean mustChangePassword) {
        this.username = username.toLowerCase();
        this.displayName = displayName;
        this.role = role;
        this.passwordSalt = passwordSalt;
        this.passwordHash = passwordHash;
        this.passwordIterations = passwordIterations;
        this.active = active;
        this.createdAt = (createdAt == null) ? LocalDateTime.now() : createdAt;
        this.lastLoginAt = lastLoginAt;
        this.loginCount = loginCount;
        this.mustChangePassword = mustChangePassword;
    }

    // ------------------------------------------------------------------
    //  getters (read only access to the private data)
    // ------------------------------------------------------------------
    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public int getLoginCount() {
        return loginCount;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public int getPasswordIterations() {
        return passwordIterations;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public String getFormattedLastLogin() {
        return (lastLoginAt == null) ? "never" : lastLoginAt.format(DISPLAY_FORMAT);
    }

    // ------------------------------------------------------------------
    //  behaviour
    // ------------------------------------------------------------------

    /** Stores a new password (already hashed by PasswordHasher). */
    public void setPassword(PasswordHasher.Hash hash) {
        this.passwordSalt = hash.getSaltBase64();
        this.passwordHash = hash.getHashBase64();
        this.passwordIterations = hash.getIterations();
    }

    /** TRUE means the user must choose a new password at the next login. */
    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Called after a successful login. */
    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
        this.loginCount = this.loginCount + 1;
    }

    /** Checks a typed password against the stored hash. */
    public boolean checkPassword(String typedPassword) {
        return PasswordHasher.verify(typedPassword, passwordSalt, passwordHash, passwordIterations);
    }

    /** The safe view of a user for the web front end (never the hash). */
    public Json.JsonObject toJson() {
        return Json.obj()
                .put("username", username)
                .put("displayName", displayName)
                .put("role", role.name())
                .put("active", active)
                .put("createdAt", createdAt.toString())
                .put("lastLoginAt", (lastLoginAt == null) ? null : lastLoginAt.toString())
                .put("loginCount", loginCount)
                .put("mustChangePassword", mustChangePassword);
    }

    @Override
    public String toString() {
        return displayName + " (" + username + ", " + role + ")";
    }
}
