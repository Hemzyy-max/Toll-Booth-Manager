package tollbooth.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * ============================================================================
 *  FILE    : PasswordHasher.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  Turns a password into a salted PBKDF2 hash and checks a password against a
 *  stored hash. This is the class that makes the login system a REAL one
 *  instead of a "compare two strings" demo.
 *
 *  WHY NOT STORE THE PASSWORD DIRECTLY ?
 *  Because if the user file is stolen, plain passwords are lost forever.
 *  PBKDF2WithHmacSHA256 is a slow, salted, one way function :
 *
 *      stored = PBKDF2(password, randomSalt, iterations)
 *
 *  A salted hash can never be reversed, and the random salt means two users
 *  with the same password get different hashes (so a rainbow table is useless).
 *  The high iteration count makes brute force attacks very expensive.
 *
 *  OOP CONCEPTS : encapsulation (private constructor + private static helpers),
 *  abstraction (the caller only calls hash() and verify()).
 * ============================================================================
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** Iterations : high enough to be slow for an attacker, fast enough for us. */
    private static final int ITERATIONS = 120_000;

    /** Length of the random salt and of the produced key, in bytes. */
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private PasswordHasher() {
    }

    /** A hash and its salt, stored together in the user file. */
    public static final class Hash {
        private final String saltBase64;
        private final String hashBase64;
        private final int iterations;

        Hash(String saltBase64, String hashBase64, int iterations) {
            this.saltBase64 = saltBase64;
            this.hashBase64 = hashBase64;
            this.iterations = iterations;
        }

        public String getSaltBase64() {
            return saltBase64;
        }

        public String getHashBase64() {
            return hashBase64;
        }

        public int getIterations() {
            return iterations;
        }
    }

    /** Creates a new random salt and hashes the password with it. */
    public static Hash hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        return new Hash(ENCODER.encodeToString(salt), ENCODER.encodeToString(key), ITERATIONS);
    }

    /**
     * Checks a typed password against the stored salt + hash.
     *
     * The comparison uses MessageDigest.isEqual(), which takes the same time for
     * every input. A normal equals() would stop at the first different character
     * and an attacker could measure the answer time to guess the hash
     * (a "timing attack").
     */
    public static boolean verify(String password, String saltBase64, String hashBase64, int iterations) {
        if (password == null || saltBase64 == null || hashBase64 == null) {
            return false;
        }
        try {
            byte[] salt = DECODER.decode(saltBase64);
            byte[] expected = DECODER.decode(hashBase64);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;                       // damaged hash in the file
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing is not available : " + e.getMessage(), e);
        }
    }

    /** A random token for sessions and ids (URL safe, no padding). */
    public static String randomToken(int bytes) {
        byte[] token = new byte[bytes];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    /** Digests used for the audit log chain (shows the file was not edited). */
    public static String shortDigest(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder text16 = new StringBuilder();
            for (int index = 0; index < 6; index++) {
                text16.append(String.format("%02x", digest[index]));
            }
            return text16.toString();
        } catch (NoSuchAlgorithmException e) {
            return "000000000000";
        }
    }
}
