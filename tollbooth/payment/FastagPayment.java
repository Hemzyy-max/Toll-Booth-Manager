package tollbooth.payment;

import tollbooth.exception.InvalidPaymentException;
import tollbooth.model.TollRates;

/**
 * ============================================================================
 *  FILE    : FastagPayment.java
 *  PACKAGE : tollbooth.payment
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INTERFACE IMPLEMENTATION : the third implementation of PaymentProcessor.
 *                     This is the "IoT" payment : the RFID tag on the vehicle
 *                     is linked to a prepaid wallet and the money is deducted
 *                     automatically without stopping the vehicle.
 *  2. ENCAPSULATION : the wallet balance is private and can only go down
 *                     through processPayment() or up through rechargeWallet().
 *  3. EXCEPTION HANDLING : a low wallet balance throws
 *                     InvalidPaymentException with a helpful message.
 * ============================================================================
 */
public class FastagPayment implements PaymentProcessor {

    // ENCAPSULATION : private data members
    private final String rfidTag;       // FASTag id of the vehicle
    private double walletBalance;       // money available in the FASTag wallet
    private double lastPaidAmount;
    private boolean paymentDone;

    /**
     * @param rfidTag       the FASTag / RFID tag linked with this vehicle
     * @param walletBalance money available in the FASTag wallet
     */
    public FastagPayment(String rfidTag, double walletBalance) {
        this.rfidTag = rfidTag;
        this.walletBalance = walletBalance;
        this.lastPaidAmount = 0.0;
        this.paymentDone = false;
    }

    /**
     * Deducts the toll amount from the FASTag wallet.
     *
     * @throws InvalidPaymentException when the amount is wrong or the wallet
     *         does not have enough money
     */
    @Override
    public boolean processPayment(double amount) throws InvalidPaymentException {
        System.out.println("FASTag payment processing...");

        if (amount <= 0) {
            throw new InvalidPaymentException("Toll amount must be greater than zero.", amount);
        }
        if (walletBalance < amount) {
            throw new InvalidPaymentException(
                    "FASTag wallet balance (" + TollRates.formatAmount(walletBalance)
                            + ") is not enough for " + TollRates.formatAmount(amount)
                            + ". Please recharge the FASTag.", amount);
        }

        this.walletBalance = TollRates.roundToTwoDecimals(walletBalance - amount);
        this.lastPaidAmount = amount;
        this.paymentDone = true;

        System.out.println("FASTag tag     : " + rfidTag);
        System.out.println("Wallet debited : " + TollRates.formatAmount(amount));
        System.out.println("Balance left   : " + TollRates.formatAmount(walletBalance));
        return true;
    }

    /** Payment slip generated for the FASTag wallet. */
    @Override
    public void printReceipt() {
        System.out.println("------- FASTAG PAYMENT SLIP -------");
        System.out.println("Payment Mode   : FASTag");
        System.out.println("FASTag Tag     : " + rfidTag);
        System.out.println("Amount Paid    : " + TollRates.formatAmount(lastPaidAmount));
        System.out.println("Balance Left   : " + TollRates.formatAmount(walletBalance));
        System.out.println("Status         : " + (paymentDone ? "SUCCESS" : "NOT PAID"));
        System.out.println("-----------------------------------");
    }

    @Override
    public String getPaymentMode() {
        return "FASTag";
    }

    /** Adds money to the wallet (kept simple on purpose). */
    public void rechargeWallet(double amount) throws InvalidPaymentException {
        if (amount <= 0) {
            throw new InvalidPaymentException("Recharge amount must be greater than zero.", amount);
        }
        this.walletBalance = TollRates.roundToTwoDecimals(walletBalance + amount);
        System.out.println("FASTag recharged. New balance : " + TollRates.formatAmount(walletBalance));
    }

    // ------------------------------------------------------------------
    // getters (encapsulation)
    // ------------------------------------------------------------------
    public String getRfidTag() {
        return rfidTag;
    }

    public double getWalletBalance() {
        return walletBalance;
    }

    public boolean isPaymentDone() {
        return paymentDone;
    }
}
