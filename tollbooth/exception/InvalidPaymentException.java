package tollbooth.exception;

/**
 * ============================================================================
 *  FILE    : InvalidPaymentException.java
 *  PACKAGE : tollbooth.exception
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. EXCEPTION HANDLING : custom checked exception for every payment problem
 *                          (cash is less than the toll, wrong UPI id,
 *                          FASTag wallet balance is not enough, and so on).
 *  2. INHERITANCE        : extends Exception.
 *  3. CONSTRUCTOR OVERLOADING : message only / message + amount.
 *  4. ENCAPSULATION      : the failed amount is kept in a private field.
 * ============================================================================
 */
public class InvalidPaymentException extends Exception {

    /** Version number required for a serializable class. */
    private static final long serialVersionUID = 1L;

    /** The amount for which the payment failed. */
    private final double attemptedAmount;

    /** CONSTRUCTOR 1 : only the error message. */
    public InvalidPaymentException(String message) {
        super(message);
        this.attemptedAmount = 0.0;
    }

    /** CONSTRUCTOR 2 : error message + the amount that failed. */
    public InvalidPaymentException(String message, double attemptedAmount) {
        super(message);
        this.attemptedAmount = attemptedAmount;
    }

    /** getter for the amount that failed. */
    public double getAttemptedAmount() {
        return attemptedAmount;
    }
}
