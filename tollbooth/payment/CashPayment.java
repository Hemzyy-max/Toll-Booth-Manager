package tollbooth.payment;

import tollbooth.exception.InvalidPaymentException;
import tollbooth.model.TollRates;

/**
 * ============================================================================
 *  FILE    : CashPayment.java
 *  PACKAGE : tollbooth.payment
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INTERFACE IMPLEMENTATION : "implements PaymentProcessor" and provides all
 *                     three methods of the interface.
 *  2. ENCAPSULATION : cashTendered, changeReturned and paymentDone are private.
 *  3. EXCEPTION HANDLING : if the customer does not give enough cash the method
 *                     THROWS InvalidPaymentException instead of returning a
 *                     wrong result.
 * ============================================================================
 */
public class CashPayment implements PaymentProcessor {

    // ENCAPSULATION : private data members
    private final double cashTendered;   // money given by the customer
    private double changeReturned;       // money returned to the customer
    private double lastPaidAmount;       // amount of the last successful payment
    private boolean paymentDone;

    /**
     * @param cashTendered the cash handed over by the driver of the vehicle
     */
    public CashPayment(double cashTendered) {
        this.cashTendered = cashTendered;
        this.changeReturned = 0.0;
        this.lastPaidAmount = 0.0;
        this.paymentDone = false;
    }

    /**
     * Collects cash and calculates the balance to be returned.
     *
     * @throws InvalidPaymentException when the amount is zero/negative or the
     *         handed over cash is less than the toll amount
     */
    @Override
    public boolean processPayment(double amount) throws InvalidPaymentException {
        System.out.println("Cash payment processing...");

        if (amount <= 0) {
            throw new InvalidPaymentException("Toll amount must be greater than zero.", amount);
        }
        if (cashTendered < amount) {
            throw new InvalidPaymentException(
                    "Cash given (" + TollRates.formatAmount(cashTendered)
                            + ") is less than the toll amount ("
                            + TollRates.formatAmount(amount) + "). "
                            + "Shortage : "
                            + TollRates.formatAmount(amount - cashTendered), amount);
        }

        this.changeReturned = TollRates.roundToTwoDecimals(cashTendered - amount);
        this.lastPaidAmount = amount;
        this.paymentDone = true;

        System.out.println("Cash received  : " + TollRates.formatAmount(cashTendered));
        System.out.println("Change returned: " + TollRates.formatAmount(changeReturned));
        return true;
    }

    /** Payment slip of the cash counter. */
    @Override
    public void printReceipt() {
        System.out.println("-------- CASH PAYMENT SLIP --------");
        System.out.println("Payment Mode : Cash");
        System.out.println("Amount Paid  : " + TollRates.formatAmount(lastPaidAmount));
        System.out.println("Change Given : " + TollRates.formatAmount(changeReturned));
        System.out.println("Status       : " + (paymentDone ? "SUCCESS" : "NOT PAID"));
        System.out.println("-----------------------------------");
    }

    @Override
    public String getPaymentMode() {
        return "Cash";
    }

    // ------------------------------------------------------------------
    // getters (encapsulation)
    // ------------------------------------------------------------------
    public double getCashTendered() {
        return cashTendered;
    }

    public double getChangeReturned() {
        return changeReturned;
    }

    public boolean isPaymentDone() {
        return paymentDone;
    }
}
