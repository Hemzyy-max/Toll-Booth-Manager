package tollbooth.web.exception;

/**
 * ============================================================================
 *  FILE    : InvalidPaymentAmountException.java
 *  PACKAGE : tollbooth.web.exception
 * ----------------------------------------------------------------------------
 *  Thrown when the amount sent from the web page is not acceptable :
 *  an empty amount, text instead of a number, zero, a negative value or a
 *  value that is far too big.
 *
 *  It carries the amount that was rejected (like the custom exceptions of the
 *  academic project do), so the message can show the exact figure to the user.
 *
 *  OOP CONCEPTS : exception handling, inheritance, encapsulation.
 * ============================================================================
 */
public class InvalidPaymentAmountException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The amount that was rejected. */
    private final double rejectedAmount;

    public InvalidPaymentAmountException(String message, double rejectedAmount) {
        super(message);
        this.rejectedAmount = rejectedAmount;
    }

    public double getRejectedAmount() {
        return rejectedAmount;
    }
}
