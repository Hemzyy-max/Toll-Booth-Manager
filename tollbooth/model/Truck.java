package tollbooth.model;

/**
 * ============================================================================
 *  FILE    : Truck.java
 *  PACKAGE : tollbooth.model
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INHERITANCE       : Truck IS-A Vehicle.
 *  2. METHOD OVERRIDING : calculateToll() returns the highest toll of the
 *                         project, because a truck damages the road the most.
 *  3. CONSTRUCTOR OVERLOADING : a truck can be created with or without the
 *                         number of axles.
 * ============================================================================
 */
public class Truck extends Vehicle {

    /** Default number of axles of a small truck. */
    private static final int DEFAULT_AXLES = 2;

    /** Extra data that only a truck has. */
    private int numberOfAxles;

    /**
     * CONSTRUCTOR 1 (full parameter list).
     *
     * @param vehicleNumber registration number of the truck
     * @param ownerName     owner of the truck
     * @param rfidTag       RFID tag of the truck
     * @param numberOfAxles number of axles of the truck
     */
    public Truck(String vehicleNumber, String ownerName, String rfidTag, int numberOfAxles) {
        super(vehicleNumber, "Truck", ownerName, rfidTag);   // INHERITANCE
        setNumberOfAxles(numberOfAxles);
    }

    /** CONSTRUCTOR 2 : overloaded convenience constructor. */
    public Truck(String vehicleNumber, String ownerName, String rfidTag) {
        this(vehicleNumber, ownerName, rfidTag, DEFAULT_AXLES);
    }

    /** METHOD OVERRIDING : a truck pays 150 rupees. */
    @Override
    public double calculateToll() {
        return TollRates.TRUCK_TOLL;
    }

    /** METHOD OVERRIDING : truck specific display line. */
    @Override
    public String getExtraDetails() {
        return "Number of Axles : " + numberOfAxles;
    }

    // ------------------------------------------------------------------
    // getter and setter (encapsulation)
    // ------------------------------------------------------------------
    public int getNumberOfAxles() {
        return numberOfAxles;
    }

    public void setNumberOfAxles(int numberOfAxles) {
        if (numberOfAxles <= 0) {
            throw new IllegalArgumentException("Number of axles must be greater than zero.");
        }
        this.numberOfAxles = numberOfAxles;
    }
}
