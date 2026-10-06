package tollbooth;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import tollbooth.exception.InvalidPaymentException;
import tollbooth.exception.InvalidRFIDException;
import tollbooth.exception.InvalidVehicleException;
import tollbooth.model.Bike;
import tollbooth.model.Bus;
import tollbooth.model.Car;
import tollbooth.model.TollRates;
import tollbooth.model.TollTransaction;
import tollbooth.model.Truck;
import tollbooth.model.Vehicle;
import tollbooth.payment.CashPayment;
import tollbooth.payment.FastagPayment;
import tollbooth.payment.PaymentProcessor;
import tollbooth.payment.UPIPayment;
import tollbooth.service.TollBooth;
import tollbooth.service.TransactionFileManager;
import tollbooth.thread.VehicleProcessingThread;

/**
 * ============================================================================
 *  FILE    : TollBoothManager.java
 *  PACKAGE : tollbooth
 * ----------------------------------------------------------------------------
 *  MAIN CLASS of the project (contains main()). It shows the menu, reads the
 *  user input with Scanner and calls the correct method of the TollBooth.
 *
 *  Note the separation of duties :
 *     TollBoothManager  = USER INTERFACE  (menu, input, output)
 *     TollBooth         = BUSINESS LOGIC  (toll, payment, collections, file)
 *  This is called loose coupling and it makes the project easy to maintain.
 *
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. OBJECT CREATION : "new TollBooth(...)", "new Car(...)", "new UPIPayment".
 *  2. POLYMORPHISM    : the same PaymentProcessor reference is filled with
 *                       CashPayment, UPIPayment or FastagPayment objects, and
 *                       the same Vehicle reference with Car / Bike / Truck /
 *                       Bus objects.
 *  3. INTERFACE       : PaymentProcessor and the RFID device are used through
 *                       their interface types.
 *  4. EXCEPTION HANDLING : every menu action is wrapped in try / catch / finally
 *                       so that a wrong input never crashes the application.
 *  5. COLLECTIONS     : ArrayList of vehicle threads and the collections kept
 *                       inside the TollBooth object.
 *  6. MULTITHREADING  : option 9 starts several VehicleProcessingThread objects.
 * ============================================================================
 */
public class TollBoothManager {

    // ------------------------------------------------------------------
    // Constants of the project (easy to change in one place)
    // ------------------------------------------------------------------
    private static final String BOOTH_ID = "BOOTH-TN01";
    private static final String BOOTH_LOCATION = "Chennai Bypass Toll Plaza";
    private static final double DEFAULT_FASTAG_BALANCE = 1000.0;
    private static final String DEFAULT_UPI_ID = "student@upi";
    private static final int MENU_EXIT = 10;

    // ENCAPSULATION : private data members
    private final TollBooth tollBooth;
    private final Scanner scanner;
    private boolean running;

    /** CONSTRUCTOR : builds the booth and the scanner used by the menu. */
    public TollBoothManager() {
        this.tollBooth = new TollBooth(BOOTH_ID, BOOTH_LOCATION);
        this.scanner = new Scanner(System.in);
        this.running = true;
    }

    /** main() : the entry point of the application. */
    public static void main(String[] args) {
        new TollBoothManager().start();
    }

    /**
     * Starts the application : welcome banner, sample data and the menu loop.
     * The loop runs until the user selects Exit (option 10).
     */
    public void start() {
        printBanner();
        loadSampleVehicles();

        try {
            while (running) {
                displayMenu();
                int choice = readIntInRange("Enter your choice : ", 1, MENU_EXIT);
                try {
                    handleChoice(choice);
                } catch (InvalidRFIDException e) {
                    // The message of the exception is written for the operator.
                    System.out.println();
                    System.out.println("[RFID ERROR] " + e.getMessage());
                    if (!e.getRfidTag().isEmpty()) {
                        System.out.println("             Rejected tag : " + e.getRfidTag());
                    }
                    System.out.println("The transaction was cancelled. The system is still running.");
                } catch (InvalidPaymentException e) {
                    System.out.println();
                    System.out.println("[PAYMENT ERROR] " + e.getMessage());
                    System.out.println("The transaction was cancelled. The barrier stays CLOSED.");
                } catch (InvalidVehicleException e) {
                    System.out.println();
                    System.out.println("[VEHICLE ERROR] " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    // Thrown by the setters of the model classes.
                    System.out.println();
                    System.out.println("[INPUT ERROR] " + e.getMessage());
                } catch (Exception e) {
                    // Safety net : any other problem is reported, never a crash.
                    System.out.println();
                    System.out.println("[UNEXPECTED ERROR] " + e.getMessage());
                }
            }
        } catch (NoSuchElementException e) {
            // Happens only when the input stream is closed (for example when the
            // program is fed from a file). The application closes neatly.
            System.out.println();
            System.out.println("Input stream closed. Closing the toll booth system.");
        } finally {
            scanner.close();
            System.out.println();
            System.out.println("Thank you for using the IoT Based Toll Booth Manager System.");
            System.out.println();
        }
    }

    /** Prints the heading of the application. */
    private void printBanner() {
        System.out.println("========================================");
        System.out.println("   IoT BASED TOLL BOOTH MANAGER SYSTEM  ");
        System.out.println("========================================");
        System.out.println(" Booth Id : " + BOOTH_ID);
        System.out.println(" Location : " + BOOTH_LOCATION);
        System.out.println(" (Console application - Core Java only) ");
        System.out.println("========================================");
        TollRates.displayRateCard();
    }

    /**
     * Registers a few vehicles automatically so that the project can be
     * demonstrated immediately in the laboratory.
     */
    private void loadSampleVehicles() {
        System.out.println();
        System.out.println("Loading sample vehicles into the HashMap database...");
        try {
            tollBooth.registerVehicle(new Car("TN01AB1234", "Hemachandran", "RFID1001", 5));
            tollBooth.registerVehicle(new Bike("TN02CD5678", "Arun Kumar", "RFID1002", 150));
            tollBooth.registerVehicle(new Truck("TN03EF9012", "Suresh Logistics", "RFID1003", 3));
            tollBooth.registerVehicle(new Bus("TN04GH3456", "KPN Travels", "RFID1004", 45));
            tollBooth.registerVehicle(new Car("TN05IJ7890", "Divya Bharathi", "RFID1005", 4));
            System.out.println("5 sample vehicles are ready. Use RFID1001 ... RFID1005.");
        } catch (InvalidVehicleException | InvalidRFIDException e) {
            // Multi-catch : one handler for both custom exceptions.
            System.out.println("Sample data could not be loaded : " + e.getMessage());
        }
    }

    /** Prints the main menu (option 1 to 10). */
    private void displayMenu() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("   IoT BASED TOLL BOOTH MANAGER SYSTEM  ");
        System.out.println("========================================");
        System.out.println();
        System.out.println("1. Register Vehicle");
        System.out.println("2. Display Vehicles");
        System.out.println("3. Process Toll");
        System.out.println("4. Calculate Toll");
        System.out.println("5. Make Payment");
        System.out.println("6. View Transaction History");
        System.out.println("7. Search Vehicle by RFID");
        System.out.println("8. Display Toll Booth Details");
        System.out.println("9. Simulate Multiple Vehicles");
        System.out.println("10. Exit");
        System.out.println();
    }

    /**
     * Calls the method linked to the selected menu option.
     * The custom exceptions are declared here with "throws" and are caught by
     * the menu loop of start().
     */
    private void handleChoice(int choice)
            throws InvalidRFIDException, InvalidPaymentException, InvalidVehicleException {
        switch (choice) {
            case 1:
                registerVehicle();
                break;
            case 2:
                tollBooth.displayAllVehicles();
                break;
            case 3:
                processToll();
                break;
            case 4:
                calculateTollOnly();
                break;
            case 5:
                makePayment();
                break;
            case 6:
                viewTransactionHistory();
                break;
            case 7:
                searchVehicleByRFID();
                break;
            case 8:
                tollBooth.displayBoothDetails();
                break;
            case 9:
                simulateMultipleVehicles();
                break;
            case 10:
                exitApplication();
                break;
            default:
                System.out.println("Invalid choice. Please select a number from 1 to 10.");
        }
    }

    // ==================================================================
    //  OPTION 1 : REGISTER VEHICLE
    // ==================================================================
    private void registerVehicle() throws InvalidVehicleException, InvalidRFIDException {
        printTitle("VEHICLE REGISTRATION");
        System.out.println("Select the vehicle type :");
        System.out.println("1. Car   (" + TollRates.formatAmount(TollRates.CAR_TOLL) + ")");
        System.out.println("2. Bike  (" + TollRates.formatAmount(TollRates.BIKE_TOLL) + ")");
        System.out.println("3. Truck (" + TollRates.formatAmount(TollRates.TRUCK_TOLL) + ")");
        System.out.println("4. Bus   (" + TollRates.formatAmount(TollRates.BUS_TOLL) + ")");

        int type = readIntInRange("Enter the type (1-4) : ", 1, 4);
        String vehicleNumber = readLine("Enter the vehicle number (example TN06KL2345) : ");
        String ownerName = readLine("Enter the owner name : ");

        System.out.println("Press ENTER to get an automatic RFID tag, or type your own tag (example RFID1006).");
        String rfidTag = readLine("Enter the RFID tag : ");
        if (rfidTag.isEmpty()) {
            rfidTag = generateFreeTag();
            System.out.println("Automatic RFID tag generated : " + rfidTag);
        }

        // OBJECT CREATION : the correct child object is built here. The
        // reference type is Vehicle (parent) but the object is a Car / Bike / ...
        Vehicle vehicle = createVehicle(type, vehicleNumber, ownerName, rfidTag);
        tollBooth.registerVehicle(vehicle);   // may throw the custom exceptions
    }

    /**
     * FACTORY method : creates the correct child object for the selected type.
     * A Vehicle reference is returned, which is again runtime polymorphism.
     */
    private Vehicle createVehicle(int type, String vehicleNumber, String ownerName, String rfidTag) {
        switch (type) {
            case 1: {
                String seats = readLine("Number of seats (ENTER for the default value 5) : ");
                if (seats.isEmpty()) {
                    return new Car(vehicleNumber, ownerName, rfidTag);        // overloaded constructor
                }
                return new Car(vehicleNumber, ownerName, rfidTag, parsePositiveInt(seats, 5));
            }
            case 2: {
                String cc = readLine("Engine capacity in cc (ENTER for the default value 150) : ");
                if (cc.isEmpty()) {
                    return new Bike(vehicleNumber, ownerName, rfidTag);       // overloaded constructor
                }
                return new Bike(vehicleNumber, ownerName, rfidTag, parsePositiveInt(cc, 150));
            }
            case 3: {
                String axles = readLine("Number of axles (ENTER for the default value 2) : ");
                if (axles.isEmpty()) {
                    return new Truck(vehicleNumber, ownerName, rfidTag);      // overloaded constructor
                }
                return new Truck(vehicleNumber, ownerName, rfidTag, parsePositiveInt(axles, 2));
            }
            default: {
                String capacity = readLine("Passenger capacity (ENTER for the default value 45) : ");
                if (capacity.isEmpty()) {
                    return new Bus(vehicleNumber, ownerName, rfidTag);        // overloaded constructor
                }
                return new Bus(vehicleNumber, ownerName, rfidTag, parsePositiveInt(capacity, 45));
            }
        }
    }

    /** Asks the RFID device for a tag that is not used yet. */
    private String generateFreeTag() {
        String tag;
        do {
            tag = tollBooth.getRfidDevice().generateNewTag();
        } while (vehicleAlreadyRegistered(tag));
        return tag;
    }

    /** Small helper : is this tag already inside the HashMap of the booth ? */
    private boolean vehicleAlreadyRegistered(String rfidTag) {
        try {
            tollBooth.searchVehicleByRFID(rfidTag);
            return true;        // no exception -> the tag exists
        } catch (InvalidRFIDException e) {
            return false;       // the tag is free
        }
    }

    // ==================================================================
    //  OPTION 3 : PROCESS TOLL  (the complete 11 step workflow)
    // ==================================================================
    private void processToll() throws InvalidRFIDException, InvalidPaymentException {
        printTitle("TOLL BOOTH VEHICLE ENTRY");
        String rfidTag = readLine("Enter RFID : ").toUpperCase();

        // The vehicle is looked up first, because the amount to be paid must be
        // known before the driver chooses the payment mode.
        Vehicle vehicle = tollBooth.searchVehicleByRFID(rfidTag);
        System.out.println("Vehicle found : " + vehicle.getVehicleNumber()
                + " (" + vehicle.getVehicleType() + ")");

        System.out.println();
        System.out.println("Select Payment Mode:");
        System.out.println("1. Cash");
        System.out.println("2. UPI");
        System.out.println("3. FASTag");
        int mode = readIntInRange("Enter choice : ", 1, 3);

        boolean fastTagUser = (mode == 3);
        // RUNTIME POLYMORPHISM : calculateToll() gives the amount according to
        // the real object type (Car / Bike / Truck / Bus).
        double payableToll = fastTagUser
                ? tollBooth.calculateToll(vehicle, true)     // overload with FASTag discount
                : tollBooth.calculateToll(vehicle);

        // INTERFACE + POLYMORPHISM : one reference, three different objects.
        PaymentProcessor payment = createPaymentProcessor(mode, rfidTag, payableToll);

        try {
            // STEPS 1 to 11 of the toll process (inside TollBooth).
            tollBooth.processVehicle(rfidTag, payment, fastTagUser);
        } catch (InvalidPaymentException e) {
            // The payment failed, so the attempt is stored with status FAILED
            // and the error is passed to the menu loop for a neat message.
            tollBooth.recordFailedTransaction(vehicle, payableToll, payment.getPaymentMode());
            throw e;
        }

        System.out.println();
        System.out.println("Toll process finished for " + vehicle.getVehicleNumber() + ".");
    }

    /**
     * Creates the payment object chosen by the driver.
     * This method shows POLYMORPHISM very clearly : the return type is the
     * interface PaymentProcessor, but the object is a CashPayment, a UPIPayment
     * or a FastagPayment.
     */
    private PaymentProcessor createPaymentProcessor(int mode, String rfidTag, double amount) {
        switch (mode) {
            case 1: {
                System.out.println();
                double tendered = readDouble("Enter the cash given (ENTER for the exact amount "
                        + TollRates.formatAmount(amount) + ") : ", amount);
                return new CashPayment(tendered);
            }
            case 2: {
                System.out.println();
                String upiId = readLine("Enter the UPI id (ENTER for " + DEFAULT_UPI_ID + ") : ");
                if (upiId.isEmpty()) {
                    upiId = DEFAULT_UPI_ID;
                }
                return new UPIPayment(upiId);
            }
            default: {
                System.out.println();
                double balance = readDouble("Enter the FASTag wallet balance (ENTER for "
                        + TollRates.formatAmount(DEFAULT_FASTAG_BALANCE) + ") : ",
                        DEFAULT_FASTAG_BALANCE);
                return new FastagPayment(rfidTag, balance);
            }
        }
    }

    // ==================================================================
    //  OPTION 4 : CALCULATE TOLL (shows method overloading on the screen)
    // ==================================================================
    private void calculateTollOnly() throws InvalidRFIDException {
        printTitle("TOLL CALCULATION");
        String rfidTag = readLine("Enter RFID : ").toUpperCase();
        Vehicle vehicle = tollBooth.searchVehicleByRFID(rfidTag);

        vehicle.displayVehicleDetails();
        System.out.println("Toll calculation for a " + vehicle.getVehicleType()
                + " using the OVERLOADED methods :");
        System.out.println();
        System.out.println("1. calculateToll()                  -> "
                + TollRates.formatAmount(tollBooth.calculateToll(vehicle)));
        System.out.println("2. calculateToll(true)   [FASTag]   -> "
                + TollRates.formatAmount(tollBooth.calculateToll(vehicle, true)));
        System.out.println("3. calculateToll(false)  [no tag]   -> "
                + TollRates.formatAmount(tollBooth.calculateToll(vehicle, false)));

        double customDiscount = readDouble("Enter a discount % for calculateToll(true, discount) "
                + "(ENTER for 5) : ", 5.0);
        System.out.println("4. calculateToll(true, " + customDiscount + ")         -> "
                + TollRates.formatAmount(tollBooth.calculateToll(vehicle, true, customDiscount)));
        System.out.println();
        System.out.println("NOTE : the same method name calculateToll() is used with different");
        System.out.println("       parameter lists. The compiler picks the version to call :");
        System.out.println("       this is COMPILE TIME POLYMORPHISM (method overloading).");
        if ("Bike".equalsIgnoreCase(vehicle.getVehicleType())) {
            System.out.println();
            System.out.println("NOTE 2 : Bike has overridden calculateToll(boolean), so a two");
            System.out.println("         wheeler never receives the FASTag discount.");
        }
    }

    // ==================================================================
    //  OPTION 5 : MAKE PAYMENT ONLY (barrier is not used)
    // ==================================================================
    private void makePayment() throws InvalidRFIDException, InvalidPaymentException {
        printTitle("TOLL PAYMENT (payment only)");
        String rfidTag = readLine("Enter RFID : ").toUpperCase();
        Vehicle vehicle = tollBooth.searchVehicleByRFID(rfidTag);

        double baseToll = tollBooth.calculateToll(vehicle);
        System.out.println("Vehicle : " + vehicle.getVehicleNumber()
                + " | Normal toll : " + TollRates.formatAmount(baseToll));

        System.out.println();
        System.out.println("Select Payment Mode:");
        System.out.println("1. Cash");
        System.out.println("2. UPI");
        System.out.println("3. FASTag");
        int mode = readIntInRange("Enter choice : ", 1, 3);

        boolean fastTagUser = (mode == 3);
        double payableToll = fastTagUser
                ? tollBooth.calculateToll(vehicle, true)
                : baseToll;
        PaymentProcessor payment = createPaymentProcessor(mode, rfidTag, payableToll);

        try {
            tollBooth.collectPayment(payment, payableToll);      // interface method call
        } catch (InvalidPaymentException e) {
            tollBooth.recordFailedTransaction(vehicle, payableToll, payment.getPaymentMode());
            throw e;
        }

        payment.printReceipt();

        // The successful payment is stored even though the vehicle did not cross
        // the barrier (for example an advance payment at the counter).
        TollTransaction transaction = tollBooth.recordTransaction(
                vehicle, payableToll, payment.getPaymentMode(), TollTransaction.STATUS_SUCCESS);
        System.out.println();
        System.out.println("Payment recorded as " + transaction.getTransactionId()
                + " (barrier not used in this option).");
    }

    // ==================================================================
    //  OPTION 6 : TRANSACTION HISTORY
    // ==================================================================
    private void viewTransactionHistory() {
        // TollBooth prints the heading and the ArrayList content itself.
        tollBooth.displayTransactionHistory();

        System.out.println();
        String answer = readLine("Open the saved file history (transactions.txt) too? (y/n) : ");
        if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
            TransactionFileManager.displaySavedTransactions();   // FILE READING
        }
    }

    // ==================================================================
    //  OPTION 7 : SEARCH VEHICLE BY RFID
    // ==================================================================
    private void searchVehicleByRFID() throws InvalidRFIDException {
        printTitle("SEARCH VEHICLE BY RFID");
        String rfidTag = readLine("Enter the RFID tag : ").toUpperCase();

        Vehicle vehicle = tollBooth.searchVehicleByRFID(rfidTag);
        System.out.println("Vehicle found in the HashMap database.");
        vehicle.displayVehicleDetails();
        System.out.println("POLYMORPHISM : the same display method shows the extra details of a "
                + vehicle.getVehicleType() + " only.");
    }

    // ==================================================================
    //  OPTION 9 : MULTITHREADING SIMULATION
    // ==================================================================
    private void simulateMultipleVehicles() {
        printTitle("MULTIPLE VEHICLES AT THE SAME TIME");

        // COLLECTION : the registered vehicles are copied into an ArrayList.
        List<Vehicle> vehicles = new ArrayList<>(tollBooth.getRegisteredVehicles());
        if (vehicles.isEmpty()) {
            System.out.println("No vehicle is registered yet. Please register a vehicle first.");
            return;
        }

        int maximumThreads = Math.min(4, vehicles.size());
        int threadCount = readIntInRange(
                "How many vehicles arrive together (1-" + maximumThreads + ") : ",
                1, maximumThreads);

        // COLLECTION : ArrayList of threads.
        List<VehicleProcessingThread> threads = new ArrayList<>();
        for (int index = 0; index < threadCount; index++) {
            Vehicle vehicle = vehicles.get(index);
            boolean fastTagUser = (index % 2 == 0);   // alternate Cash and FASTag
            threads.add(new VehicleProcessingThread(tollBooth, vehicle, fastTagUser));
        }

        System.out.println();
        System.out.println("Starting " + threads.size() + " vehicle processing threads...");
        System.out.println("(Each thread handles one vehicle. TollBooth.processVehicle() is");
        System.out.println(" synchronized, so the shared totals stay correct.)");

        // start() creates a new thread and the JVM calls run() inside it.
        for (VehicleProcessingThread thread : threads) {
            thread.start();
        }

        // join() makes the main thread wait for every vehicle thread to finish.
        for (VehicleProcessingThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.out.println("Main thread was interrupted : " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        // COLLECTION iteration : every thread reports its result.
        System.out.println();
        System.out.println("All vehicle threads have finished.");
        for (VehicleProcessingThread thread : threads) {
            System.out.println("  Thread " + thread.getName() + " -> state : " + thread.getState());
        }
        tollBooth.displayBoothDetails();
    }

    // ==================================================================
    //  OPTION 10 : EXIT
    // ==================================================================
    private void exitApplication() {
        System.out.println();
        System.out.println("Closing the toll booth...");
        tollBooth.displayBoothDetails();
        this.running = false;      // stops the while loop of start()
    }

    // ==================================================================
    //  SMALL INPUT / OUTPUT HELPER METHODS
    // ==================================================================

    /** Prints a centred title box, for example the vehicle entry heading. */
    private void printTitle(String title) {
        String line = "========================================";
        int spaces = Math.max(0, (line.length() - title.length()) / 2);
        StringBuilder padding = new StringBuilder();
        for (int index = 0; index < spaces; index++) {
            padding.append(' ');
        }
        System.out.println();
        System.out.println(line);
        System.out.println(padding.toString() + title);
        System.out.println(line);
        System.out.println();
    }

    /** Reads one full line of text from the keyboard. */
    private String readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            throw new NoSuchElementException("No more input available.");
        }
        return scanner.nextLine().trim();
    }

    /**
     * Reads a number between minimum and maximum.
     * EXCEPTION HANDLING : if the user types letters (NumberFormatException) or a
     * number outside the range, the question is asked again instead of crashing.
     */
    private int readIntInRange(String prompt, int minimum, int maximum) {
        while (true) {
            String text = readLine(prompt);
            try {
                int value = Integer.parseInt(text);
                if (value < minimum || value > maximum) {
                    System.out.println("Please enter a number between " + minimum
                            + " and " + maximum + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("\"" + text + "\" is not a valid number. Try again.");
            }
        }
    }

    /** Reads a decimal number; an empty input returns the default value. */
    private double readDouble(String prompt, double defaultValue) {
        String text = readLine(prompt);
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            System.out.println("\"" + text + "\" is not a valid amount. Default value used.");
            return defaultValue;
        }
    }

    /** Converts text into a positive number, otherwise the default is used. */
    private int parsePositiveInt(String text, int defaultValue) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value <= 0) {
                System.out.println("The value must be positive. Default value used.");
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("\"" + text + "\" is not a number. Default value used.");
            return defaultValue;
        }
    }
}
