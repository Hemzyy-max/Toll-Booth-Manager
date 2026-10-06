package tollbooth.payment;

import tollbooth.exception.InvalidPaymentException;
import tollbooth.model.TollRates;

/**
 * ============================================================================
 *  FILE    : UPIPayment.java
 *  PACKAGE : tollbooth.payment
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INTERFACE IMPLEMENTATION : a second class implements PaymentProcessor.
 *                     The TollBooth code does not change at all - this is the
 *                     advantage of programming against an interface.
 *  2. ENCAPSULATION : the UPI id and the generated reference number are
 *                     private.
 *  3. EXCEPTION HANDLING : a wrong UPI id (without @) throws
 *                     InvalidPaymentException.
 *  4. STRING HANDLING : the reference number is built with string
 *                     concatenation and Math.random() to look realistic.
 * ============================================================================
 */
public class UPIPayment implements PaymentProcessor {

    // ENCAPSULATION : private data members
    private final String upiId;             // example : student@upi
    private String transactionReference;    // example : UPIREF481920
    private double lastPaidAmount;
    private boolean paymentDone;

    /**
     * @param upiId the UPI id of the customer (must contain the @ character)
     */
    public UPIPayment(String upiId) {
        this.upiId = (upiId == null) ? "" : upiId.trim();
        this.transactionReference = "";
        this.lastPaidAmount = 0.0;
        this.paymentDone = false;
    }

    /**
     * Simulates a UPI transfer.
     *
     * @throws InvalidPaymentException for a wrong amount or an invalid UPI id
     */
    @Override
    public boolean processPayment(double amount) throws InvalidPaymentException {
        System.out.println("UPI payment processing...");

        if (amount <= 0) {
            throw new InvalidPaymentException("Toll amount must be greater than zero.", amount);
        }
        if (upiId.isEmpty() || !upiId.contains("@") || upiId.startsWith("@") || upiId.endsWith("@")) {
            throw new InvalidPaymentException(
                    "Invalid UPI id \"" + upiId + "\" (correct format : name@bank)", amount);
        }

        this.transactionReference = "UPIREF" + (100000 + (int) (Math.random() * 899999));
        this.lastPaidAmount = amount;
        this.paymentDone = true;

        System.out.println("UPI id         : " + upiId);
        System.out.println("Reference No   : " + transactionReference);
        System.out.println("Amount debited : " + TollRates.formatAmount(amount));
        return true;
    }

    /** Payment slip of the UPI app. */
    @Override
    public void printReceipt() {
        System.out.println("--------- UPI PAYMENT SLIP --------");
        System.out.println("Payment Mode : UPI");
        System.out.println("UPI Id       : " + upiId);
        System.out.println("Reference No : " + transactionReference);
        System.out.println("Amount Paid  : " + TollRates.formatAmount(lastPaidAmount));
        System.out.println("Status       : " + (paymentDone ? "SUCCESS" : "NOT PAID"));
        System.out.println("-----------------------------------");
    }

    @Override
    public String getPaymentMode() {
        return "UPI";
    }

    // ------------------------------------------------------------------
    // getters (encapsulation)
    // ------------------------------------------------------------------
    public String getUpiId() {
        return upiId;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public boolean isPaymentDone() {
        return paymentDone;
    }
}
