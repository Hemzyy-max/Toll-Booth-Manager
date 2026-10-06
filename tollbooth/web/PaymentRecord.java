package tollbooth.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import tollbooth.model.TollRates;

/**
 * ============================================================================
 *  FILE    : PaymentRecord.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  One payment of the web application, shown in the "Recent payments" list and
 *  used to build the collection chart.
 *
 *  RELATION WITH THE OOPJ PROJECT
 *  The payment is validated and stored by the classes of the academic project
 *  (Vehicle, calculateToll(), PaymentProcessor implementations and
 *  TollBooth/TransactionFileManager for transactions.txt). This class keeps the
 *  extra information the web page needs (who paid, from which IP address, the
 *  id of the live event) and converts everything to JSON.
 *
 *  OOP CONCEPTS : encapsulation, a static counter for the receipt id, and a
 *  toJson() method that hides the internal fields from the front end.
 * ============================================================================
 */
public class PaymentRecord {

    private static final AtomicLong SEQUENCE = new AtomicLong(1000);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String receiptId;
    private final String transactionId;
    private final String rfidTag;
    private final String vehicleNumber;
    private final String vehicleType;
    private final double baseToll;
    private final double paidAmount;
    private final String paymentMode;
    private final String operatorUsername;
    private final String operatorDisplayName;
    private final String rfidSource;
    private final String clientAddress;
    private final LocalDateTime paidAt;

    public PaymentRecord(String transactionId, String rfidTag, String vehicleNumber, String vehicleType,
                         double baseToll, double paidAmount, String paymentMode,
                         String operatorUsername, String operatorDisplayName,
                         String rfidSource, String clientAddress) {
        this.receiptId = "RCPT" + SEQUENCE.incrementAndGet();
        this.transactionId = transactionId;
        this.rfidTag = rfidTag;
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
        this.baseToll = baseToll;
        this.paidAmount = paidAmount;
        this.paymentMode = paymentMode;
        this.operatorUsername = operatorUsername;
        this.operatorDisplayName = operatorDisplayName;
        this.rfidSource = rfidSource;
        this.clientAddress = clientAddress;
        this.paidAt = LocalDateTime.now();
    }

    // ------------------------------------------------------------------
    //  getters
    // ------------------------------------------------------------------
    public String getReceiptId() {
        return receiptId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getRfidTag() {
        return rfidTag;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public double getBaseToll() {
        return baseToll;
    }

    public double getPaidAmount() {
        return paidAmount;
    }

    public double getDiscount() {
        return TollRates.roundToTwoDecimals(baseToll - paidAmount);
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public String getOperatorUsername() {
        return operatorUsername;
    }

    public String getOperatorDisplayName() {
        return operatorDisplayName;
    }

    public String getRfidSource() {
        return rfidSource;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public String getFormattedTime() {
        return paidAt.format(TIME_FORMAT);
    }

    /** The JSON form used by the payment table and by the live event. */
    public Json.JsonObject toJson() {
        return Json.obj()
                .put("receiptId", receiptId)
                .put("transactionId", transactionId)
                .put("rfidTag", rfidTag)
                .put("vehicleNumber", vehicleNumber)
                .put("vehicleType", vehicleType)
                .put("baseToll", baseToll)
                .put("paidAmount", paidAmount)
                .put("discount", getDiscount())
                .put("paidAmountText", TollRates.formatAmount(paidAmount))
                .put("baseTollText", TollRates.formatAmount(baseToll))
                .put("paymentMode", paymentMode)
                .put("operator", operatorDisplayName)
                .put("operatorUsername", operatorUsername)
                .put("rfidSource", rfidSource)
                .put("paidAt", paidAt.toString())
                .put("paidTime", getFormattedTime());
    }

    /** A short one line summary, used in the audit log. */
    public String toAuditLine() {
        return receiptId + " " + vehicleNumber + " (" + vehicleType + ") "
                + TollRates.formatAmount(paidAmount) + " by " + paymentMode
                + (getDiscount() > 0 ? " [discount " + TollRates.formatAmount(getDiscount()) + "]" : "");
    }

    /** Payment modes counted for the doughnut chart of the dashboard. */
    public static Map<String, Integer> emptyModeCounters() {
        Map<String, Integer> counters = new LinkedHashMap<>();
        counters.put("Cash", 0);
        counters.put("UPI", 0);
        counters.put("FASTag", 0);
        return counters;
    }
}
