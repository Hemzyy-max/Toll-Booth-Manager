package tollbooth.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ============================================================================
 *  FILE    : TollTransaction.java
 *  PACKAGE : tollbooth.model
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. ENCAPSULATION   : all transaction data is private, exposed through
 *                       getters. The transaction id, the amount and the date
 *                       are final, so a saved transaction can never be edited
 *                       by mistake.
 *  2. CONSTRUCTOR OVERLOADING : three constructors
 *                       - payment details only (normal case),
 *                       - payment details + status,
 *                       - full constructor used while loading the file.
 *  3. METHOD OVERLOADING / OVERRIDING : generateReceipt() returns a printable
 *                       receipt and toString() overrides Object.toString().
 *  4. STATIC MEMBER   : transactionCounter is shared by every object and
 *                       generates automatic ids : TXN1001, TXN1002, ...
 * ============================================================================
 */
public class TollTransaction {

    /** Status values used in the project. */
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED  = "FAILED";

    /** Used by the file reader when it splits a saved line. */
    public static final String FIELD_SEPARATOR = ";";

    /**
     * STATIC counter : one copy for the whole class (not for each object).
     * It is the reason every transaction gets a unique id (TXN1001, TXN1002...).
     */
    private static int transactionCounter = 1000;

    /** Display format used on the screen, for example 06-10-2026 15:42:10 */
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    /** Format used inside the text file, easy to read back by the program. */
    private static final DateTimeFormatter FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ------------------------------------------------------------------
    // ENCAPSULATION : private data members
    // ------------------------------------------------------------------
    private final String transactionId;
    private final String vehicleNumber;
    private final String vehicleType;
    private final double tollAmount;
    private final String paymentMode;
    private final LocalDateTime dateTime;
    private String transactionStatus;

    /**
     * CONSTRUCTOR 1 : the normal way of creating a transaction. The status is
     * assumed to be SUCCESS because a transaction object is created only after
     * a successful payment.
     */
    public TollTransaction(String vehicleNumber, String vehicleType,
                           double tollAmount, String paymentMode) {
        this(vehicleNumber, vehicleType, tollAmount, paymentMode, STATUS_SUCCESS);
    }

    /** CONSTRUCTOR 2 : same as above but the status can also be FAILED. */
    public TollTransaction(String vehicleNumber, String vehicleType,
                           double tollAmount, String paymentMode, String transactionStatus) {
        this(nextTransactionId(), vehicleNumber, vehicleType, tollAmount,
                paymentMode, LocalDateTime.now(), transactionStatus);
    }

    /**
     * CONSTRUCTOR 3 : full constructor. It is used by the FILE READER, where
     * the id and the date must be taken from the file and not created again.
     */
    public TollTransaction(String transactionId, String vehicleNumber, String vehicleType,
                           double tollAmount, String paymentMode,
                           LocalDateTime dateTime, String transactionStatus) {
        this.transactionId = transactionId;
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
        this.tollAmount = tollAmount;
        this.paymentMode = paymentMode;
        this.dateTime = dateTime;
        this.transactionStatus = transactionStatus;
    }

    /**
     * PRIVATE static helper : builds the next id TXN1001, TXN1002, ...
     * The counter is static, so two transactions can never share an id.
     */
    private static String nextTransactionId() {
        transactionCounter = transactionCounter + 1;
        return "TXN" + transactionCounter;
    }

    /**
     * Called once at start up by TransactionFileManager. If the file already
     * contains 7 transactions, the counter continues from 1007 so that a new
     * run of the program does not repeat old transaction ids.
     *
     * @param existingTransactionCount number of lines already saved in the file
     */
    public static void resumeCounterFrom(int existingTransactionCount) {
        if (existingTransactionCount > 0) {
            transactionCounter = 1000 + existingTransactionCount;
        }
    }

    /** Prints one compact line about this transaction. */
    public void displayTransaction() {
        System.out.println(transactionId + " | " + getFormattedDateTime() + " | "
                + vehicleNumber + " | " + vehicleType + " | "
                + TollRates.formatAmount(tollAmount) + " | "
                + paymentMode + " | " + transactionStatus);
    }

    /**
     * Builds and returns a printed receipt (a String, not a println).
     * Returning a String makes the method reusable : it can be printed on the
     * console today and written to a file or a printer tomorrow.
     */
    public String generateReceipt() {
        String newLine = System.lineSeparator();
        StringBuilder receipt = new StringBuilder();
        receipt.append("========================================").append(newLine);
        receipt.append("          TRANSACTION RECEIPT           ").append(newLine);
        receipt.append("========================================").append(newLine);
        receipt.append("Transaction ID : ").append(transactionId).append(newLine);
        receipt.append("Vehicle Number : ").append(vehicleNumber).append(newLine);
        receipt.append("Vehicle Type   : ").append(vehicleType).append(newLine);
        receipt.append("Toll Amount    : ").append(TollRates.formatAmount(tollAmount)).append(newLine);
        receipt.append("Payment Mode   : ").append(paymentMode).append(newLine);
        receipt.append("Date & Time    : ").append(getFormattedDateTime()).append(newLine);
        receipt.append("Status         : ").append(transactionStatus).append(newLine);
        receipt.append("========================================");
        return receipt.toString();
    }

    /** Converts this transaction into ONE line for transactions.txt */
    public String toFileLine() {
        return transactionId + FIELD_SEPARATOR
                + vehicleNumber + FIELD_SEPARATOR
                + vehicleType + FIELD_SEPARATOR
                + String.format(java.util.Locale.US, "%.2f", tollAmount) + FIELD_SEPARATOR
                + paymentMode + FIELD_SEPARATOR
                + dateTime.format(FILE_FORMAT) + FIELD_SEPARATOR
                + transactionStatus;
    }

    /**
     * FACTORY method : creates a TollTransaction object from one saved line.
     * It returns null when the line is damaged, so the caller can skip it
     * instead of crashing the whole program.
     */
    public static TollTransaction fromFileLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        String[] parts = line.split(FIELD_SEPARATOR);
        if (parts.length != 7) {
            return null;                         // damaged line - skip it
        }
        try {
            return new TollTransaction(
                    parts[0],                                   // transaction id
                    parts[1],                                   // vehicle number
                    parts[2],                                   // vehicle type
                    Double.parseDouble(parts[3]),               // toll amount
                    parts[4],                                   // payment mode
                    LocalDateTime.parse(parts[5], FILE_FORMAT), // date and time
                    parts[6]);                                  // status
        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            // Multi catch : harmless for the user, the bad line is simply skipped.
            return null;
        }
    }

    /** Formatted date and time for the screen. */
    public String getFormattedDateTime() {
        return dateTime.format(DISPLAY_FORMAT);
    }

    // ------------------------------------------------------------------
    // getters (the data is read-only from outside)
    // ------------------------------------------------------------------
    public String getTransactionId() {
        return transactionId;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public double getTollAmount() {
        return tollAmount;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getTransactionStatus() {
        return transactionStatus;
    }

    /** A transaction can only be marked FAILED / SUCCESS, never edited. */
    public void setTransactionStatus(String transactionStatus) {
        if (STATUS_SUCCESS.equalsIgnoreCase(transactionStatus)
                || STATUS_FAILED.equalsIgnoreCase(transactionStatus)) {
            this.transactionStatus = transactionStatus.toUpperCase();
        } else {
            throw new IllegalArgumentException("Status must be SUCCESS or FAILED.");
        }
    }

    /** METHOD OVERRIDING of Object.toString(). */
    @Override
    public String toString() {
        return transactionId + " (" + vehicleNumber + ", "
                + TollRates.formatAmount(tollAmount) + ", " + paymentMode + ")";
    }
}
