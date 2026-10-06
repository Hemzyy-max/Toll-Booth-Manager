package tollbooth.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import tollbooth.exception.InvalidPaymentException;
import tollbooth.exception.InvalidRFIDException;
import tollbooth.model.TollRates;
import tollbooth.model.TollTransaction;
import tollbooth.model.Vehicle;
import tollbooth.payment.CashPayment;
import tollbooth.payment.FastagPayment;
import tollbooth.payment.PaymentProcessor;
import tollbooth.payment.UPIPayment;
import tollbooth.web.exception.InvalidPaymentAmountException;

/**
 * ============================================================================
 *  FILE    : TollPaymentService.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  The business logic of the web application : it runs the complete toll
 *  workflow for one vehicle and then tells the browser about it IN REAL TIME.
 *
 *  WORKFLOW OF ONE PAYMENT (the same 11 steps as the console project)
 *   1. the RFID tag is validated and searched in the HashMap of TollBooth
 *   2. the vehicle type is checked (a car or a bike cannot pay with FASTag)
 *   3. the toll is calculated by the child class of Vehicle (polymorphism)
 *   4. the amount is validated (missing, zero, negative or huge amounts are
 *      rejected with InvalidPaymentAmountException)
 *   5. a PaymentProcessor object collects the money (Cash / UPI / FASTag)
 *   6. a TollTransaction is created and saved (ArrayList + transactions.txt)
 *   7. the barrier opens and closes
 *   8. a PaymentRecord is stored in this service
 *   9. a "payment" event is pushed to every open browser  <- REAL TIME
 *  10. a notification is created in the notification centre
 *  11. the audit log receives the permanent record
 *
 *  OOP CONCEPTS : polymorphism (PaymentProcessor and Vehicle references),
 *  interfaces, encapsulation, collections, multithreading (the method is
 *  synchronized because the simulator thread and the web requests run together)
 *  and exception handling (three different exceptions are handled separately).
 * ============================================================================
 */
public class TollPaymentService {

    /** How many payments are kept in memory for the dashboard. */
    private static final int LEDGER_SIZE = 500;

    /** Above this amount a payment is refused as "not a real toll". */
    private static final double MAX_ACCEPTED_AMOUNT = 100_000.0;

    /** A FASTag balance under this value generates a warning notification. */
    private static final double LOW_BALANCE_ALERT = 150.0;

    /** Every completed thousand of the day's collection is announced. */
    private static final double MILESTONE_STEP = 1000.0;

    private final TollBoothEngine engine;
    private final EventHub eventHub;
    private final NotificationCentre notifications;
    private final AuditLog auditLog;

    private final Deque<PaymentRecord> ledger = new ConcurrentLinkedDeque<>();

    // today's counters (they are reset when the date changes)
    private LocalDate currentDay = LocalDate.now();
    private int todayCount;
    private double todayCollection;
    private int todayFailed;
    private final Map<String, Integer> modeCounters = PaymentRecord.emptyModeCounters();
    private final int[] hourlyCounts = new int[24];
    private final double[] hourlyCollection = new double[24];

    private long failedCountTotal;
    private long milestoneCounter;

    public TollPaymentService(TollBoothEngine engine, EventHub eventHub,
                              NotificationCentre notifications, AuditLog auditLog) {
        this.engine = engine;
        this.eventHub = eventHub;
        this.notifications = notifications;
        this.auditLog = auditLog;
    }

    // ========================================================================
    //  THE PAYMENT WORKFLOW
    // ========================================================================

    /**
     * Processes one toll payment.
     *
     * @param actorUsername   who is collecting the money ("SYSTEM-AUTO" for the simulator)
     * @param actorDisplayName the name shown in the receipt
     * @param clientAddress   the address of the computer of the operator
     * @param rfidTag         the tag read by the RFID reader or typed by the operator
     * @param paymentMode     "Cash", "UPI" or "FASTag"
     * @param autoAmount      true when the system pays : the exact toll is taken
     * @param rfidSource      where the tag came from : "reader-simulation" or "manual-entry"
     * @param cashTendered    for CASH payments : the money handed over by the driver.
     *                        It is ignored for UPI and FASTag, because those modes
     *                        always take exactly the toll amount. The server never
     *                        trusts the browser for the toll itself : the amount is
     *                        always calculated again by TollBooth.calculateToll().
     * @throws InvalidRFIDException          the tag is unknown or in the wrong format
     * @throws InvalidPaymentException       the payment could not be completed
     * @throws InvalidPaymentAmountException the amount is missing or not acceptable
     */
    public synchronized PaymentRecord processPayment(String actorUsername, String actorDisplayName,
                                                     String clientAddress, String rfidTag,
                                                     String paymentMode, boolean autoAmount,
                                                     String rfidSource, double cashTendered)
            throws InvalidRFIDException, InvalidPaymentException, InvalidPaymentAmountException {

        rolloverDayIfNeeded();

        // STEP 1 : find the vehicle of this tag (HashMap lookup inside TollBooth)
        Vehicle vehicle = engine.getTollBooth().searchVehicleByRFID(rfidTag);
        String tag = vehicle.getRfidTag().toUpperCase();
        TollBoothEngine.VehicleSetup setup = engine.findSetup(tag);

        // STEP 2 : the rule of the project - only trucks and buses carry a FASTag
        String mode = normaliseMode(paymentMode);
        if ("FASTag".equals(mode) && !engine.isFastagVehicle(vehicle.getVehicleType())) {
            throw new InvalidPaymentException(
                    "A " + vehicle.getVehicleType() + " (" + vehicle.getVehicleNumber()
                            + ") does not carry a FASTag. Please pay by Cash or UPI.", 0.0);
        }
        if ("FASTag".equals(mode) && (setup == null || setup.getFastagBalance() <= 0)) {
            throw new InvalidPaymentException(
                    "The FASTag wallet of " + vehicle.getVehicleNumber()
                            + " is empty. Please recharge it or pay by Cash / UPI.", 0.0);
        }

        // STEP 3 : the toll is asked from the vehicle object itself
        double baseToll = engine.getTollBooth().calculateToll(vehicle);
        double payableToll = "FASTag".equals(mode)
                ? engine.getTollBooth().calculateToll(vehicle, true)     // 10% FASTag discount
                : baseToll;

        // STEP 4 : the amount collected is ALWAYS the calculated toll. For cash,
        // the handed over amount is validated as well, so a short payment is
        // refused with a clear message and the barrier stays closed.
        double amountToCollect = payableToll;
        if (!autoAmount && "Cash".equals(mode)) {
            cashTendered = validateAmount(cashTendered);
        } else if (!autoAmount) {
            // UPI / FASTag : a wrong figure from the browser is simply ignored.
            cashTendered = payableToll;
        }

        // STEP 5 : choose the payment object (interface + polymorphism)
        PaymentProcessor payment = createPaymentProcessor(mode, tag, setup, amountToCollect, cashTendered);

        TollTransaction transaction;
        try {
            // STEP 5b : collect the money
            engine.getTollBooth().collectPayment(payment, amountToCollect);

            // STEP 6 : the transaction object (automatic TXN id) is stored in the
            // ArrayList of the booth and appended to transactions.txt
            transaction = engine.getTollBooth().recordTransaction(
                    vehicle, amountToCollect, mode, TollTransaction.STATUS_SUCCESS);
            engine.recordVehicleTrip(tag);
        } catch (InvalidPaymentException e) {
            // The payment failed : nothing is collected, the barrier stays closed
            // and the attempt is stored with the status FAILED.
            handleFailedPayment(vehicle, amountToCollect, mode, actorUsername, clientAddress, e);
            throw e;
        }

        // STEP 7 : open and close the barrier
        engine.getTollBooth().openBarrier();
        boolean vehiclePassed = true;
        engine.getTollBooth().closeBarrier();

        // STEP 8 : keep the payment for the dashboard
        PaymentRecord record = new PaymentRecord(
                transaction.getTransactionId(), tag, vehicle.getVehicleNumber(),
                vehicle.getVehicleType(), baseToll, amountToCollect, mode,
                actorUsername, actorDisplayName, rfidSource, clientAddress);
        ledger.addFirst(record);
        while (ledger.size() > LEDGER_SIZE) {
            ledger.removeLast();
        }

        // counters
        todayCount++;
        todayCollection = TollRates.roundToTwoDecimals(todayCollection + amountToCollect);
        modeCounters.merge(mode, 1, Integer::sum);
        int hour = record.getPaidAt().getHour();
        hourlyCounts[hour]++;
        hourlyCollection[hour] = TollRates.roundToTwoDecimals(hourlyCollection[hour] + amountToCollect);

        // the FASTag wallet must be charged (the money left the tag)
        if ("FASTag".equals(mode) && setup != null) {
            engine.chargeFastagWallet(tag, amountToCollect);
        }

        // STEP 9 : REAL TIME - push the event to every open browser
        eventHub.broadcast("payment", record.toJson().put("vehiclePassed", vehiclePassed));

        // STEP 10 : the notification centre
        notifications.publish(Notification.Level.SUCCESS,
                "Toll collected - " + record.getPaymentMode(),
                record.getVehicleNumber() + " (" + record.getVehicleType() + ") paid "
                        + TollRates.formatAmount(record.getPaidAmount())
                        + " at " + record.getFormattedTime()
                        + " by " + actorDisplayName,
                "payment", Notification.Audience.ALL, null);

        checkLowBalance(setup);
        checkCollectionMilestone();

        // STEP 11 : permanent proof
        auditLog.record("PAYMENT", actorUsername, clientAddress,
                record.toAuditLine() + " | tag " + tag + " | source " + rfidSource);

        return record;
    }

    // ========================================================================
    //  VALIDATION HELPERS
    // ========================================================================

    /** Checks the amount sent from the browser. */
    private double validateAmount(double expectedAmount) throws InvalidPaymentAmountException {
        if (Double.isNaN(expectedAmount) || Double.isInfinite(expectedAmount)) {
            throw new InvalidPaymentAmountException("The amount is not a valid number.", expectedAmount);
        }
        if (expectedAmount <= 0) {
            throw new InvalidPaymentAmountException(
                    "The amount must be greater than zero (received " + expectedAmount + ").",
                    expectedAmount);
        }
        if (expectedAmount > MAX_ACCEPTED_AMOUNT) {
            throw new InvalidPaymentAmountException(
                    "The amount " + TollRates.formatAmount(expectedAmount)
                            + " is too large for a toll payment.", expectedAmount);
        }
        return TollRates.roundToTwoDecimals(expectedAmount);
    }

    /** Cash / UPI / FASTag must be written in the same way everywhere. */
    private String normaliseMode(String paymentMode) {
        if (paymentMode == null) {
            return "Cash";
        }
        String text = paymentMode.trim();
        if (text.equalsIgnoreCase("FASTag") || text.equalsIgnoreCase("FASTAG")) {
            return "FASTag";
        }
        if (text.equalsIgnoreCase("UPI")) {
            return "UPI";
        }
        return "Cash";
    }

    /**
     * FACTORY METHOD : builds the correct PaymentProcessor object.
     * The return type is the INTERFACE, so the rest of the code does not care
     * which implementation it received - this is the loose coupling that the
     * OOPJ project demonstrates.
     *
     * @param tollAmount   the amount that must reach the booth (the calculated toll)
     * @param cashTendered for cash payments, the money given by the driver
     */
    private PaymentProcessor createPaymentProcessor(String mode, String rfidTag,
                                                    TollBoothEngine.VehicleSetup setup,
                                                    double tollAmount, double cashTendered) {
        if ("FASTag".equals(mode)) {
            double balance = (setup == null) ? 0.0 : setup.getFastagBalance();
            return new FastagPayment(rfidTag, balance);      // wallet is charged below
        }
        if ("UPI".equals(mode)) {
            return new UPIPayment("tollbooth@upi");
        }
        // Cash : CashPayment itself checks "money given < toll" and returns the change.
        return new CashPayment(cashTendered > 0 ? cashTendered : tollAmount);
    }

    /** Everything that must happen when a payment fails. */
    private void handleFailedPayment(Vehicle vehicle, double amount, String mode,
                                     String actorUsername, String clientAddress,
                                     InvalidPaymentException problem) {
        engine.getTollBooth().recordFailedTransaction(vehicle, amount, mode);
        todayFailed++;
        failedCountTotal++;

        eventHub.broadcast("payment-failed", Json.obj()
                .put("vehicleNumber", vehicle.getVehicleNumber())
                .put("vehicleType", vehicle.getVehicleType())
                .put("rfidTag", vehicle.getRfidTag())
                .put("paymentMode", mode)
                .put("amount", amount)
                .put("reason", problem.getMessage())
                .put("operator", actorUsername)
                .put("barrier", "CLOSED"));

        notifications.publish(Notification.Level.WARNING,
                "Payment failed - barrier stayed closed",
                vehicle.getVehicleNumber() + " (" + vehicle.getVehicleType() + ") : "
                        + problem.getMessage(),
                "payment", Notification.Audience.ALL, null);

        auditLog.record("PAYMENT-FAILED", actorUsername, clientAddress,
                vehicle.getVehicleNumber() + " " + mode + " "
                        + TollRates.formatAmount(amount) + " : " + problem.getMessage());
    }

    private void checkLowBalance(TollBoothEngine.VehicleSetup setup) {
        if (setup == null || !setup.isFastagEnabled()) {
            return;
        }
        if (setup.getFastagBalance() < LOW_BALANCE_ALERT) {
            notifications.publish(Notification.Level.DANGER,
                    "FASTag wallet almost empty",
                    setup.getVehicleNumber() + " has only "
                            + TollRates.formatAmount(setup.getFastagBalance())
                            + " left in the FASTag wallet. Recharge it soon.",
                    "fastag", Notification.Audience.ADMIN, null);
        }
    }

    private void checkCollectionMilestone() {
        long milestone = (long) (todayCollection / MILESTONE_STEP);
        if (milestone > milestoneCounter && milestone > 0) {
            milestoneCounter = milestone;
            notifications.publish(Notification.Level.INFO,
                    "Collection milestone reached",
                    "The booth has collected " + TollRates.formatAmount(milestone * MILESTONE_STEP)
                            + " since the last milestone.",
                    "system", Notification.Audience.ALL, null);
        }
    }

    /** Resets the daily counters when the date changes. */
    private void rolloverDayIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            currentDay = today;
            todayCount = 0;
            todayCollection = 0;
            todayFailed = 0;
            milestoneCounter = 0;
            modeCounters.clear();
            modeCounters.putAll(PaymentRecord.emptyModeCounters());
            for (int index = 0; index < hourlyCounts.length; index++) {
                hourlyCounts[index] = 0;
                hourlyCollection[index] = 0;
            }
            notifications.info("New business day",
                    "The counters were reset for " + today + ". Good morning!");
        }
    }

    // ========================================================================
    //  READ ONLY VIEWS FOR THE DASHBOARD
    // ========================================================================

    public List<PaymentRecord> recentPayments(int limit) {
        List<PaymentRecord> list = new ArrayList<>();
        int count = 0;
        for (PaymentRecord record : ledger) {
            if (count++ >= limit) {
                break;
            }
            list.add(record);
        }
        return list;
    }

    public Json.JsonArray recentPaymentsJson(int limit) {
        Json.JsonArray array = Json.arr();
        for (PaymentRecord record : recentPayments(limit)) {
            array.add(record.toJson());
        }
        return array;
    }

    /** Search by receipt id, transaction id or vehicle number. */
    public Json.JsonArray searchPayments(String term, int limit) {
        Json.JsonArray array = Json.arr();
        if (term == null || term.trim().isEmpty()) {
            return recentPaymentsJson(limit);
        }
        String needle = term.trim().toLowerCase();
        int count = 0;
        for (PaymentRecord record : ledger) {
            if (record.getVehicleNumber().toLowerCase().contains(needle)
                    || record.getReceiptId().toLowerCase().contains(needle)
                    || record.getTransactionId().toLowerCase().contains(needle)
                    || record.getRfidTag().toLowerCase().contains(needle)
                    || record.getPaymentMode().toLowerCase().contains(needle)) {
                array.add(record.toJson());
                if (++count >= limit) {
                    break;
                }
            }
        }
        return array;
    }

    /** Everything the dashboard needs, in one JSON answer. */
    public Json.JsonObject statsJson() {
        rolloverDayIfNeeded();

        Json.JsonArray hourly = Json.arr();
        for (int hour = 0; hour < 24; hour++) {
            hourly.add(Json.obj()
                    .put("hour", hour)
                    .put("label", String.format("%02d:00", hour))
                    .put("count", hourlyCounts[hour])
                    .put("amount", hourlyCollection[hour]));
        }

        Json.JsonArray modes = Json.arr();
        for (Map.Entry<String, Integer> entry : modeCounters.entrySet()) {
            modes.add(Json.obj().put("mode", entry.getKey()).put("count", entry.getValue()));
        }

        // The vehicle types that crossed today, counted from the ledger.
        Map<String, Integer> typeCounters = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (PaymentRecord record : ledger) {
            if (record.getPaidAt().toLocalDate().equals(today)) {
                typeCounters.merge(record.getVehicleType(), 1, Integer::sum);
            }
        }
        Json.JsonArray types = Json.arr();
        for (Map.Entry<String, Integer> entry : typeCounters.entrySet()) {
            types.add(Json.obj().put("type", entry.getKey()).put("count", entry.getValue()));
        }

        PaymentRecord last = ledger.peekFirst();
        double average = (todayCount == 0) ? 0.0
                : TollRates.roundToTwoDecimals(todayCollection / todayCount);

        return Json.obj()
                .put("today", today.toString())
                .put("todayCount", todayCount)
                .put("todayCollection", todayCollection)
                .put("todayCollectionText", TollRates.formatAmount(todayCollection))
                .put("averageTicket", average)
                .put("averageTicketText", TollRates.formatAmount(average))
                .put("todayFailed", todayFailed)
                .put("failedTotal", failedCountTotal)
                .put("hourly", hourly)
                .put("modes", modes)
                .put("vehicleTypes", types)
                .put("lastPayment", (last == null) ? null : last.toJson())
                .put("engineTransactions", engine.getTollBooth().getTotalTransactions())
                .put("engineCollectionText",
                        TollRates.formatAmount(engine.getTollBooth().getTotalCollection()))
                .put("barrierOpen", engine.getTollBooth().getBarrier().isOpen())
                .put("registeredVehicles", engine.getVehicleCount())
                .put("rfidScans", engine.getTollBooth().getRfidDevice().getTotalScans())
                .put("simulatedPayments", engine.getSimulatedPayments())
                .put("ledgerSize", ledger.size());
    }
}
