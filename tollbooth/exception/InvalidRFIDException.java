package tollbooth.exception;

/**
 * ============================================================================
 *  FILE    : InvalidRFIDException.java
 *  PACKAGE : tollbooth.exception
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. EXCEPTION HANDLING : a USER DEFINED (custom) checked exception.
 *  2. INHERITANCE        : it extends Exception, so it becomes a real
 *                          exception that can be thrown and caught.
 *  3. CONSTRUCTOR OVERLOADING : one constructor takes only a message, the
 *                          other also takes the wrong RFID value.
 *  4. ENCAPSULATION      : the wrong RFID value is stored in a private field
 *                          and read through a getter.
 *
 *  WHY A CHECKED EXCEPTION (extends Exception) ?
 *  The compiler forces the programmer to write try/catch or throws for it,
 *  therefore an invalid RFID can never be silently ignored.
 * ============================================================================
 */
public class InvalidRFIDException extends Exception {

    /** Version number required for a serializable class. */
    private static final long serialVersionUID = 1L;

    /** The RFID value that caused the problem (may be empty). */
    private final String rfidTag;

    /** CONSTRUCTOR 1 : only the error message. */
    public InvalidRFIDException(String message) {
        super(message);                 // super() calls the Exception constructor
        this.rfidTag = "";
    }

    /** CONSTRUCTOR 2 : error message + the RFID value that failed. */
    public InvalidRFIDException(String message, String rfidTag) {
        super(message);
        this.rfidTag = (rfidTag == null) ? "" : rfidTag;
    }

    /** getter for the wrong RFID value. */
    public String getRfidTag() {
        return rfidTag;
    }
}
