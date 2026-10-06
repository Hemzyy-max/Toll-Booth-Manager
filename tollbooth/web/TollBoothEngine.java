package tollbooth.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import tollbooth.exception.InvalidRFIDException;
import tollbooth.exception.InvalidVehicleException;
import tollbooth.model.Bike;
import tollbooth.model.Bus;
import tollbooth.model.Car;
import tollbooth.model.TollRates;
import tollbooth.model.Truck;
import tollbooth.model.Vehicle;
import tollbooth.service.TollBooth;

/**
 * ============================================================================
 *  FILE    : TollBoothEngine.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  The bridge between the OOPJ console project and the web application.
 *
 *  It does NOT re-write the toll logic : it reuses the classes of the academic
 *  project exactly as they are
 *
 *      TollBooth  -> registration, HashMap of vehicles, calculateToll(),
 *                    recordTransaction(), transactions.txt, barrier
 *      Vehicle / Car / Bike / Truck / Bus  -> the toll rates (polymorphism)
 *      TollRates  -> the money formatting
 *
 *  and adds the two things a real time web application needs :
 *      1. an RFID tag simulator, so the reader can also deliver a tag by itself
 *         (in the console project the operator always typed the tag), and
 *      2. a background task that keeps the dashboard alive (a vehicle arrives
 *         every few seconds) - KEEP_AUTO_SIMULATION controls it.
 *
 *  OOP CONCEPTS : composition (the engine HAS-A TollBooth), collections,
 *  multithreading (ScheduledExecutorService), exception handling.
 * ============================================================================
 */
public class TollBoothEngine {

    /** Vehicle types of the academic project that carry a FASTag. */
    private static final String[] FASTAG_VEHICLE_TYPES = {"Truck", "Bus"};

    /** How often the automatic vehicle simulator runs. */
    private static final int SIMULATION_SECONDS = 7;

    /** One registered vehicle, with the web only information (FASTag wallet). */
    public static class VehicleSetup {
        private final String rfidTag;
        private final String vehicleNumber;
        private final String vehicleType;
        private final String ownerName;
        private final double baseToll;
        private double fastagBalance;
        private boolean fastagEnabled;
        private int trips;

        VehicleSetup(Vehicle vehicle, double fastagBalance, boolean fastagEnabled) {
            this.rfidTag = vehicle.getRfidTag();
            this.vehicleNumber = vehicle.getVehicleNumber();
            this.vehicleType = vehicle.getVehicleType();
            this.ownerName = vehicle.getOwnerName();
            this.baseToll = vehicle.calculateToll();       // RUNTIME POLYMORPHISM
            this.fastagBalance = fastagBalance;
            this.fastagEnabled = fastagEnabled;
            this.trips = vehicle.getTotalTrips();
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

        public double getFastagBalance() {
            return fastagBalance;
        }

        public boolean isFastagEnabled() {
            return fastagEnabled;
        }

        public void setFastagBalance(double fastagBalance) {
            this.fastagBalance = fastagBalance;
        }

        public void setFastagEnabled(boolean fastagEnabled) {
            this.fastagEnabled = fastagEnabled;
        }

        public void addTrip() {
            this.trips++;
        }

        public Json.JsonObject toJson() {
            return Json.obj()
                    .put("rfidTag", rfidTag)
                    .put("vehicleNumber", vehicleNumber)
                    .put("vehicleType", vehicleType)
                    .put("ownerName", ownerName)
                    .put("baseToll", baseToll)
                    .put("baseTollText", TollRates.formatAmount(baseToll))
                    .put("fastagBalance", fastagBalance)
                    .put("fastagBalanceText", TollRates.formatAmount(fastagBalance))
                    .put("fastagEnabled", fastagEnabled)
                    .put("trips", trips);
        }
    }

    private final TollBooth tollBooth;
    private final Map<String, VehicleSetup> setupsByTag = new LinkedHashMap<>();
    private final Random random = new Random();
    private final ScheduledExecutorService simulator;

    private TollPaymentService paymentService;
    private long simulatedPayments;

    public TollBoothEngine() {
        this.tollBooth = new TollBooth("BOOTH-TN01", "Chennai Bypass Toll Plaza");
        this.simulator = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auto-vehicle-simulator");
            thread.setDaemon(true);          // never blocks the stop of the server
            return thread;
        });
        registerSiteVehicles();
    }

    // ========================================================================
    //  VEHICLES
    // ========================================================================

    /**
     * Registers the vehicles of the plaza. The objects are the very same child
     * classes as in the console project (Car, Bike, Truck, Bus), and every one
     * of them knows its own toll.
     */
    private void registerSiteVehicles() {
        // The FASTag wallet balance belongs to the FASTag tag, so it is kept in
        // the setup object of the web layer (the academic Vehicle class must not
        // know anything about money balances).
        addVehicle(new Car("TN01AB1234", "Hemachandran", "RFID1001", 5), 1000.0);
        addVehicle(new Bike("TN02CD5678", "Arun Kumar", "RFID1002", 150), 0.0);
        addVehicle(new Truck("TN03EF9012", "Suresh Logistics", "RFID1003", 3), 1500.0);
        addVehicle(new Bus("TN04GH3456", "KPN Travels", "RFID1004", 45), 1200.0);
        addVehicle(new Car("TN05IJ7890", "Divya Bharathi", "RFID1005", 4), 800.0);
        addVehicle(new Car("TN06PQ3456", "Karthik Raja", "RFID1006", 4), 600.0);
        addVehicle(new Truck("TN07RS7891", "Meena Transport", "RFID1007", 4), 2000.0);
        addVehicle(new Bus("TN08TU2345", "Parveen Travels", "RFID1008", 50), 900.0);
    }

    private void addVehicle(Vehicle vehicle, double fastagBalance) {
        try {
            tollBooth.registerVehicle(vehicle);          // throws the custom exceptions
        } catch (InvalidVehicleException | InvalidRFIDException e) {
            System.out.println("[warn] vehicle not registered : " + e.getMessage());
            return;
        }
        boolean fastagEnabled = fastagBalance > 0 && isFastagVehicle(vehicle.getVehicleType());
        setupsByTag.put(vehicle.getRfidTag(), new VehicleSetup(vehicle, fastagBalance, fastagEnabled));
    }

    /** A vehicle type that may use a FASTag : truck or bus. */
    public boolean isFastagVehicle(String vehicleType) {
        for (String allowed : FASTAG_VEHICLE_TYPES) {
            if (allowed.equalsIgnoreCase(vehicleType)) {
                return true;
            }
        }
        return false;
    }

    /** All the vehicles with their web information (admin screen). */
    public List<VehicleSetup> getVehicleSetups() {
        return new ArrayList<>(setupsByTag.values());
    }

    public VehicleSetup findSetup(String rfidTag) {
        return (rfidTag == null) ? null : setupsByTag.get(rfidTag.trim().toUpperCase());
    }

    /**
     * Charges the FASTag wallet of a vehicle.
     *
     * @throws IllegalArgumentException when the vehicle has no FASTag
     */
    public VehicleSetup chargeFastagWallet(String rfidTag, double amount) {
        VehicleSetup setup = findSetup(rfidTag);
        if (setup == null) {
            throw new IllegalArgumentException("Unknown RFID tag : " + rfidTag);
        }
        if (!isFastagVehicle(setup.getVehicleType())) {
            throw new IllegalArgumentException(
                    "A " + setup.getVehicleType() + " does not carry a FASTag. Only trucks and buses do.");
        }
        setup.setFastagBalance(TollRates.roundToTwoDecimals(setup.getFastagBalance() - amount));
        setup.setFastagEnabled(setup.getFastagBalance() > 0);
        return setup;
    }

    /** Recharges the FASTag wallet of a vehicle. */
    public VehicleSetup rechargeFastagWallet(String rfidTag, double amount) {
        VehicleSetup setup = findSetup(rfidTag);
        if (setup == null) {
            throw new IllegalArgumentException("Unknown RFID tag : " + rfidTag);
        }
        if (!isFastagVehicle(setup.getVehicleType())) {
            throw new IllegalArgumentException(
                    "A " + setup.getVehicleType() + " does not carry a FASTag. Only trucks and buses do.");
        }
        setup.setFastagBalance(TollRates.roundToTwoDecimals(setup.getFastagBalance() + amount));
        setup.setFastagEnabled(true);
        return setup;
    }

    public void recordVehicleTrip(String rfidTag) {
        VehicleSetup setup = findSetup(rfidTag);
        if (setup != null) {
            setup.addTrip();
        }
    }

    /** Vehicles whose FASTag wallet is almost empty (used for an alert). */
    public List<VehicleSetup> lowBalanceVehicles(double threshold) {
        List<VehicleSetup> low = new ArrayList<>();
        for (VehicleSetup setup : setupsByTag.values()) {
            if (setup.isFastagEnabled() && setup.getFastagBalance() < threshold) {
                low.add(setup);
            }
        }
        return low;
    }

    /**
     * Simulates the RFID reader : it picks a tag that is currently standing in
     * front of the antenna. In a real installation this value would come from a
     * serial port or a socket of the reader hardware.
     */
    public String simulateRfidScan() {
        List<VehicleSetup> setups = getVehicleSetups();
        if (setups.isEmpty()) {
            return null;
        }
        return setups.get(random.nextInt(setups.size())).getRfidTag();
    }

    /** A payment mode that the chosen vehicle is allowed to use. */
    public String randomPayableMode(String rfidTag) {
        VehicleSetup setup = findSetup(rfidTag);
        if (setup == null) {
            return "Cash";
        }
        boolean fastagPossible = setup.isFastagEnabled() && setup.getFastagBalance() >= setup.getBaseToll();
        if (fastagPossible) {
            return random.nextBoolean() ? "FASTag" : (random.nextBoolean() ? "Cash" : "UPI");
        }
        return random.nextBoolean() ? "Cash" : "UPI";
    }

    // ========================================================================
    //  BACKGROUND SIMULATION  (keeps the real time dashboard alive)
    // ========================================================================

    /** Connects the payment service that the simulator must use. */
    public void attachPaymentService(TollPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Starts the automatic vehicle simulator. */
    public void startSimulator(boolean enabled) {
        if (!enabled) {
            System.out.println("Automatic vehicle simulation is OFF.");
            return;
        }
        simulator.scheduleWithFixedDelay(this::runOneSimulatedVehicle,
                SIMULATION_SECONDS, SIMULATION_SECONDS, TimeUnit.SECONDS);
        System.out.println("Automatic vehicle simulation is ON (one vehicle every "
                + SIMULATION_SECONDS + " seconds).");
    }

    private void runOneSimulatedVehicle() {
        if (paymentService == null) {
            return;
        }
        try {
            String rfidTag = simulateRfidScan();
            if (rfidTag == null) {
                return;
            }
            String mode = randomPayableMode(rfidTag);
            paymentService.processPayment(
                    "SYSTEM-AUTO",              // no logged in operator : system payment
                    "Automatic lane reader",
                    "auto-simulator",
                    rfidTag,
                    mode,
                    true,                       // auto : the exact toll is taken
                    "reader-simulation",        // the IoT reader itself
                    0.0);
            simulatedPayments++;
        } catch (Exception e) {
            // The simulator must never die : the next run simply tries again.
            System.out.println("[simulator] " + e.getClass().getSimpleName() + " : " + e.getMessage());
        }
    }

    public long getSimulatedPayments() {
        return simulatedPayments;
    }

    public void stopSimulator() {
        simulator.shutdownNow();
    }

    // ========================================================================
    //  ACCESS TO THE ACADEMIC PROJECT OBJECTS
    // ========================================================================

    public TollBooth getTollBooth() {
        return tollBooth;
    }

    public Collection<Vehicle> getVehicles() {
        return tollBooth.getRegisteredVehicles();
    }

    public int getVehicleCount() {
        return tollBooth.getVehicleCount();
    }

    /** Vehicle numbers, used by the vehicle selector of the payment form. */
    public Json.JsonArray vehiclesToJson() {
        Json.JsonArray array = Json.arr();
        for (VehicleSetup setup : setupsByTag.values()) {
            array.add(setup.toJson());
        }
        return array;
    }
}
