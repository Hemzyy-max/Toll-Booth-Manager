package tollbooth.payment;

import tollbooth.exception.InvalidPaymentException;

/**
 * ============================================================================
 *  FILE    : PaymentProcessor.java
 *  PACKAGE : tollbooth.payment
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INTERFACE   : a contract. CashPayment, UPIPayment and FastagPayment all
 *                   "implements PaymentProcessor", therefore they all must
 *                   provide these three methods.
 *  2. ABSTRACTION : TollBooth calls processPayment() without knowing whether
 *                   the money comes as cash, UPI or FASTag.
 *  3. POLYMORPHISM: "PaymentProcessor payment = new CashPayment(100);" and
 *                   "payment = new UPIPayment("user@upi");" - the same
 *                   reference calls different code at run time.
 *  4. EXCEPTION   : processPayment() uses the "throws" keyword to declare that
 *                   a payment can fail.
 * ============================================================================
 */
public interface PaymentProcessor {

    /**
     * Collects the money.
     *
     * @param amount the toll amount to be paid
     * @return true when the payment is successful
     * @throws InvalidPaymentException when the payment fails
     *         (less cash, wrong UPI id, low FASTag balance, ...)
     */
    boolean processPayment(double amount) throws InvalidPaymentException;

    /** Prints a small payment slip for the customer. */
    void printReceipt();

    /** @return "Cash", "UPI" or "FASTag" - used in the transaction record. */
    String getPaymentMode();
}
