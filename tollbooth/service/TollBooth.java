package tollbooth.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import tollbooth.device.Barrier;
import tollbooth.device.RFIDDevice;
import tollbooth.exception.InvalidPaymentException;
import tollbooth.exception.InvalidRFIDException;
import tollbooth.exception.InvalidVehicleException;
import tollbooth.model.TollRates;
import tollbooth.model.TollTransaction;
import tollbooth.model.Vehicle;
import tollbooth.payment.PaymentProcessor;

/**
 * ============================================================================
 *  FILE    : TollBooth.java
 *  PACKAGE : tollbooth.service
 * ----------------------------------------------------------------------------
 *  The "brain" of the project : it registers vehicles, keeps them in a
 *  HashMap, runs the complete toll workflow, saves every transaction in an
 *  ArrayList (+ text file) and controls the barrier.
 *
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. ENCAPSULATION : boothId, location, totals, the HashMap and the ArrayList
 *                     are private. Other classes use public methods only.
 *  2. COLLECTIONS   : HashMap<String, Vehicle> for the vehicle database (RFID
 *                     is the key, so a search is very fast) and
 *                     ArrayList<TollTransaction> for the history.
 *  3. POLYMORPHISM  : calculateToll(Vehicle) calls vehicle.calculateToll();
 *                     the real method that runs depends on the object (Car,
 *                     Bus, Truck, Bike) and is decided at RUNTIME.
 *  4. METHOD OVERLOADING : three calculateToll() methods with different
 *                     parameters.
 *  5. INTERFACE     : the booth works with the PaymentProcessor interface and
 *                     the RFIDReader / RFIDDevice device object only.
 *  6. EXCEPTION HANDLING : custom exceptions are declared with "throws" and
 *                     thrown to the caller (the user interface class), so the
 *                     program never crashes.
 *  7. MULTITHREADING : processVehicle() is "synchronized" - two vehicle
 *                     threads can never update totalCollection at the same
 *                     time (that would corrupt the data).
 * ============================================================================
 */
public class TollBooth {

    /** A valid Indian registration number looks like : TN01AB1234 */
    private static final String VEHICLE_NUMBER_PATTERN = "^[A-Z]{2}[0-9]{2}[A-Z]{1,3}[0-9]{4}$";

    // ------------------------------------------------------------------
    // ENCAPSULATION : private data members of the booth
    // ------------------------------------------------------------------
    private final String boothId;
    private String location;
    private int totalTransactions;
    private double totalCollection;

    /**
     * COLLECTIONS :
     * HashMap is used because the RFID tag is a KEY - searching by RFID takes
     * almost no time even when lakhs of vehicles are registered.
     */
    private final HashMap<String, Vehicle> vehicleDatabase;

    /** COLLECTION : ArrayList keeps the transactions in the order they happened. */
    private final ArrayList<TollTransaction> transactions;

    /** Devices (composition : a TollBooth HAS-A RFIDDevice and HAS-A Barrier). */
    private final RFIDDevice rfidDevice;
    private final Barrier barrier;

    /**
     * CONSTRUCTOR : builds the booth and its hardware, and prepares the text
     * file so that no old transaction id is repeated.
     *
     * @param boothId  id of the booth, for example BOOTH-TN01
     * @param location place where the booth is installed
     */
    public TollBooth(String boothId, String location) {
        this.boothId = boothId;
        this.location = location;
        this.totalTransactions = 0;
        this.totalCollection = 0.0;
        this.vehicleDatabase = new HashMap<>();
        this.transactions = new ArrayList<>();
        this.rfidDevice = new RFIDDevice();
        this.barrier = new Barrier(boothId + "-BARRIER");

        // FILE HANDLING : create transactions.txt if it is not there yet and
        // continue the transaction id counter from the saved records.
        TransactionFileManager.ensureFileExists();
        TollTransaction.resumeCounterFrom(TransactionFileManager.countSavedTransactions());
    }

    // ==================================================================
    //  1. VEHICLE REGISTRATION
    // ==================================================================

    /**
     * Adds a vehicle to the HashMap database.
     *
     * @throws InvalidVehicleException when the registration number is wrong or
     *         already registered
     * @throws InvalidRFIDException    when the RFID tag is wrong or already used
     */
    public void registerVehicle(Vehicle vehicle) throws InvalidVehicleException, InvalidRFIDException {
        if (vehicle == null) {
            throw new InvalidVehicleException("Vehicle object is null, nothing to register.");
        }

        String vehicleNumber = vehicle.getVehicleNumber().toUpperCase();
        String rfidTag = vehicle.getRfidTag().toUpperCase();

        // Business rule 1 : the registration number must look like TN01AB1234
        if (!Pattern.matches(VEHICLE_NUMBER_PATTERN, vehicleNumber)) {
            throw new InvalidVehicleException(
                    "Registration number \"" + vehicleNumber
                            + "\" is not in the valid format (example : TN01AB1234).",
                    vehicleNumber);
        }
        // Business rule 2 : the RFID tag must look like RFID1001
        if (!rfidDevice.validateRFID(rfidTag)) {
            throw new InvalidRFIDException(
                    "RFID tag \"" + rfidTag
                            + "\" is not in the valid format (example : RFID1001).",
                    rfidTag);
        }
        // Business rule 3 : one tag can belong to only one vehicle
        if (vehicleDatabase.containsKey(rfidTag)) {
            throw new InvalidVehicleException(
                    "RFID tag " + rfidTag + " is already used by vehicle "
                            + vehicleDatabase.get(rfidTag).getVehicleNumber() + ".",
                    vehicleNumber);
        }
        // Business rule 4 : the same vehicle must not be registered twice
        if (isVehicleNumberRegistered(vehicleNumber)) {
            throw new InvalidVehicleException(
                    "Vehicle " + vehicleNumber + " is already registered at this booth.",
                    vehicleNumber);
        }

        rfidDevice.syncTagSerial(rfidTag);   // keep the auto tag generator in step
        vehicleDatabase.put(rfidTag, vehicle);

        System.out.println("Vehicle registered successfully.");
        System.out.println("RFID Tag       : " + rfidTag);
        System.out.println("Vehicle Number : " + vehicleNumber);
        System.out.println("Vehicle Type   : " + vehicle.getVehicleType());
    }

    /** Helper : is this registration number already in the HashMap ? */
    private boolean isVehicleNumberRegistered(String vehicleNumber) {
        for (Vehicle vehicle : vehicleDatabase.values()) {
            if (vehicle.getVehicleNumber().equalsIgnoreCase(vehicleNumber)) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================
    //  2. SEARCH AND DISPLAY
    // ==================================================================

    /**
     * Searches the vehicle database using the RFID tag (HashMap.get).
     *
     * @throws InvalidRFIDException when the format is wrong or the vehicle is
     *         not registered
     */
    public Vehicle searchVehicleByRFID(String rfidTag) throws InvalidRFIDException {
        if (!rfidDevice.validateRFID(rfidTag)) {
            throw new InvalidRFIDException(
                    "RFID tag format is wrong : \"" + rfidTag + "\"", rfidTag);
        }
        String key = rfidTag.trim().toUpperCase();
        Vehicle vehicle = vehicleDatabase.get(key);
        if (vehicle == null) {
            throw new InvalidRFIDException(
                    "No vehicle is registered with the RFID tag " + key
                            + ". Please register the vehicle first.", key);
        }
        return vehicle;
    }

    /** Prints every registered vehicle of the HashMap. */
    public void displayAllVehicles() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("        REGISTERED VEHICLES            ");
        System.out.println("========================================");

        if (vehicleDatabase.isEmpty()) {
            System.out.println("No vehicle is registered yet.");
            return;
        }

        int serialNumber = 1;
        // Iterating a HashMap with entrySet() gives us both the key (RFID)
        // and the value (Vehicle object) in one loop.
        for (Map.Entry<String, Vehicle> entry : vehicleDatabase.entrySet()) {
            Vehicle vehicle = entry.getValue();
            System.out.println(serialNumber + ". " + vehicle.getVehicleNumber()
                    + " | " + vehicle.getVehicleType()
                    + " | Owner : " + vehicle.getOwnerName()
                    + " | RFID : " + entry.getKey()
                    + " | Trips : " + vehicle.getTotalTrips());
            serialNumber = serialNumber + 1;
        }
        System.out.println("----------------------------------------");
        System.out.println("Total registered vehicles : " + vehicleDatabase.size());
    }

    /** Prints the ArrayList of transactions (in memory history). */
    public void displayTransactionHistory() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("        TRANSACTION HISTORY            ");
        System.out.println("========================================");

        if (transactions.isEmpty()) {
            System.out.println("No transaction has happened in this session.");
            return;
        }

        for (int index = 0; index < transactions.size(); index++) {
            System.out.print((index + 1) + ". ");
            transactions.get(index).displayTransaction();   // ArrayList.get()
        }
        System.out.println("----------------------------------------");
        System.out.println("Transactions this session : " + transactions.size());
        System.out.println("Collection this session   : " + TollRates.formatAmount(totalCollection));
    }

    /** Prints the complete status of the toll booth. */
    public void displayBoothDetails() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("          TOLL BOOTH DETAILS           ");
        System.out.println("========================================");
        System.out.println("Booth Id            : " + boothId);
        System.out.println("Location            : " + location);
        System.out.println("Registered vehicles : " + vehicleDatabase.size());
        System.out.println("Transactions        : " + totalTransactions);
        System.out.println("Total collection    : " + TollRates.formatAmount(totalCollection));
        System.out.println("Barrier openings    : " + barrier.getTotalOpenings());
        System.out.println("RFID scans          : " + rfidDevice.getTotalScans());
        System.out.println("Barrier status      : " + (barrier.isOpen() ? "OPEN" : "CLOSED"));
        System.out.println("Transaction file    : " + TransactionFileManager.getFilePath());
        System.out.println("========================================");
    }

    // ==================================================================
    //  3. TOLL CALCULATION  (polymorphism + overloading)
    // ==================================================================

    /**
     * POLYMORPHISM : the parameter is a Vehicle reference, but the object
     * inside may be a Car, Bike, Bus or Truck. Which calculateToll() runs is
     * decided at RUNTIME by the Java Virtual Machine.
     */
    public double calculateToll(Vehicle vehicle) {
        return vehicle.calculateToll();
    }

    /** OVERLOADED version : toll for a FASTag customer (10% discount). */
    public double calculateToll(Vehicle vehicle, boolean fastTag) {
        return vehicle.calculateToll(fastTag);
    }

    /** OVERLOADED version : toll with any discount percentage. */
    public double calculateToll(Vehicle vehicle, boolean fastTag, double discountPercent) {
        return vehicle.calculateToll(fastTag, discountPercent);
    }

    // ==================================================================
    //  4. THE COMPLETE TOLL WORKFLOW  (11 steps of the project)
    // ==================================================================

    /**
     * Runs the whole toll process for one vehicle.
     * It is "synchronized" because several VehicleProcessingThread objects may
     * call it at the same time : only one vehicle may update the totals and the
     * barrier at a time (thread safety).
     *
     * @param rfidTag    the tag read at the entry
     * @param payment    the payment object (Cash / UPI / FASTag)
     * @param fastTagUser true when the driver pays with FASTag (discount)
     * @return the saved TollTransaction object
     * @throws InvalidRFIDException    for a wrong or unknown RFID tag
     * @throws InvalidPaymentException when the payment fails
     */
    public synchronized TollTransaction processVehicle(String rfidTag,
                                                       PaymentProcessor payment,
                                                       boolean fastTagUser)
            throws InvalidRFIDException, InvalidPaymentException {

        System.out.println();

        // STEP 1 : the RFID antenna reads the tag (IoT device simulation)
        rfidDevice.simulateScan(rfidTag);

        // STEP 2 : the tag that was read is validated
        String detectedTag = rfidDevice.readRFID();
        if (!rfidDevice.validateRFID(detectedTag)) {
            throw new InvalidRFIDException("The tag that was read is not valid.", detectedTag);
        }

        // STEP 3 : the vehicle is fetched from the HashMap database
        Vehicle vehicle = searchVehicleByRFID(detectedTag);

        // STEP 4 : show the detected vehicle on the operator screen
        System.out.println();
        System.out.println("RFID validated successfully.");
        System.out.println("---------------------------------------");
        System.out.println("RFID DETECTED");
        System.out.println("RFID           : " + detectedTag);
        System.out.println("Vehicle        : " + vehicle.getVehicleNumber());
        System.out.println("Type           : " + vehicle.getVehicleType());
        System.out.println("Owner          : " + vehicle.getOwnerName());
        System.out.println("---------------------------------------");

        // STEP 5 : toll calculation (runtime polymorphism + overloading)
        double baseToll = calculateToll(vehicle);
        double payableToll = fastTagUser ? calculateToll(vehicle, true) : baseToll;
        System.out.println();
        System.out.println("Toll Amount    : " + TollRates.formatAmount(baseToll));
        if (fastTagUser && payableToll < baseToll) {
            System.out.println("FASTag discount (" + (int) TollRates.FASTAG_DISCOUNT_PERCENT
                    + "%) : -" + TollRates.formatAmount(
                            TollRates.roundToTwoDecimals(baseToll - payableToll)));
        }
        System.out.println("Amount Payable : " + TollRates.formatAmount(payableToll));

        // STEP 6 + 7 : collect the money through the chosen payment mode
        collectPayment(payment, payableToll);

        // STEP 8 + 9 : create the transaction object (the id and the date time
        // are automatic), store it in the ArrayList and append it to the file.
        TollTransaction transaction = recordTransaction(vehicle, payableToll,
                payment.getPaymentMode(), TollTransaction.STATUS_SUCCESS);

        System.out.println();
        System.out.println(transaction.generateReceipt());

        // STEP 10 : open the barrier after a successful payment
        openBarrier();
        System.out.println("Vehicle Passed Successfully.");

        // STEP 11 : close the barrier
        closeBarrier();

        return transaction;
    }

    /**
     * Calls the payment object. Because the parameter type is the
     * PaymentProcessor INTERFACE, the same line of code can pay by cash, by UPI
     * or by FASTag.
     */
    public void collectPayment(PaymentProcessor payment, double amount)
            throws InvalidPaymentException {
        if (payment == null) {
            throw new InvalidPaymentException("No payment mode was selected.", amount);
        }
        boolean paid = payment.processPayment(amount);
        if (!paid) {
            throw new InvalidPaymentException("The payment was not successful.", amount);
        }
        System.out.println();
        System.out.println("Payment Successful!");
    }

    /**
     * Creates a transaction object, stores it in the ArrayList, updates the
     * totals when the status is SUCCESS and appends the record to
     * transactions.txt.
     *
     * This method is "synchronized" for the same reason as processVehicle() :
     * several threads may reach it at the same moment.
     */
    public synchronized TollTransaction recordTransaction(Vehicle vehicle, double amount,
                                                          String paymentMode, String status) {
        TollTransaction transaction = new TollTransaction(
                vehicle.getVehicleNumber(),
                vehicle.getVehicleType(),
                amount,
                paymentMode,
                status);

        transactions.add(transaction);                 // COLLECTION : ArrayList

        if (TollTransaction.STATUS_SUCCESS.equalsIgnoreCase(status)) {
            vehicle.recordTrip();
            totalTransactions = totalTransactions + 1;
            totalCollection = TollRates.roundToTwoDecimals(totalCollection + amount);
        }

        TransactionFileManager.saveTransaction(transaction);   // FILE HANDLING
        return transaction;
    }

    /**
     * Saves a FAILED attempt in the history. It does not change the collection
     * because no money was received.
     */
    public synchronized TollTransaction recordFailedTransaction(Vehicle vehicle, double amount,
                                                               String paymentMode) {
        TollTransaction failedTransaction =
                recordTransaction(vehicle, amount, paymentMode, TollTransaction.STATUS_FAILED);
        System.out.println("Failed attempt recorded with status FAILED (money not collected).");
        return failedTransaction;
    }

    // ==================================================================
    //  5. BARRIER CONTROL
    // ==================================================================

    /** Opens the barrier arm (the device class prints "Barrier OPEN"). */
    public void openBarrier() {
        barrier.openBarrier();
    }

    /** Closes the barrier arm. */
    public void closeBarrier() {
        barrier.closeBarrier();
    }

    // ==================================================================
    //  6. GETTERS  (encapsulation : read only access)
    // ==================================================================

    public String getBoothId() {
        return boothId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Location must not be empty.");
        }
        this.location = location.trim();
    }

    public int getTotalTransactions() {
        return totalTransactions;
    }

    public double getTotalCollection() {
        return totalCollection;
    }

    public int getVehicleCount() {
        return vehicleDatabase.size();
    }

    public RFIDDevice getRfidDevice() {
        return rfidDevice;
    }

    public Barrier getBarrier() {
        return barrier;
    }

    /**
     * Returns a COPY of the registered vehicles.
     * A copy is returned so that no other class can change the internal
     * HashMap of the booth (protection of data - encapsulation).
     */
    public Collection<Vehicle> getRegisteredVehicles() {
        return new ArrayList<>(vehicleDatabase.values());
    }
}
