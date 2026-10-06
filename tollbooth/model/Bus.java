package tollbooth.model;

/**
 * ============================================================================
 *  FILE    : Bus.java
 *  PACKAGE : tollbooth.model
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INHERITANCE       : Bus IS-A Vehicle.
 *  2. METHOD OVERRIDING : calculateToll() returns the bus rate of 100 rupees.
 *  3. CONSTRUCTOR OVERLOADING : a bus can be created with or without the
 *                         passenger capacity.
 *
 *  Notice how short every child class is : all the common work is already
 *  done by the abstract parent class Vehicle (code reusability).
 * ============================================================================
 */
public class Bus extends Vehicle {

    /** Default passenger capacity of a bus. */
    private static final int DEFAULT_CAPACITY = 45;

    /** Extra data that only a bus has. */
    private int passengerCapacity;

    /**
     * CONSTRUCTOR 1 (full parameter list).
     *
     * @param vehicleNumber     registration number of the bus
     * @param ownerName         owner of the bus (usually a travel company)
     * @param rfidTag           RFID tag of the bus
     * @param passengerCapacity number of passengers the bus can carry
     */
    public Bus(String vehicleNumber, String ownerName, String rfidTag, int passengerCapacity) {
        super(vehicleNumber, "Bus", ownerName, rfidTag);   // INHERITANCE
        setPassengerCapacity(passengerCapacity);
    }

    /** CONSTRUCTOR 2 : overloaded convenience constructor. */
    public Bus(String vehicleNumber, String ownerName, String rfidTag) {
        this(vehicleNumber, ownerName, rfidTag, DEFAULT_CAPACITY);
    }

    /** METHOD OVERRIDING : a bus pays 100 rupees. */
    @Override
    public double calculateToll() {
        return TollRates.BUS_TOLL;
    }

    /** METHOD OVERRIDING : bus specific display line. */
    @Override
    public String getExtraDetails() {
        return "Passenger Capacity : " + passengerCapacity + " seats";
    }

    // ------------------------------------------------------------------
    // getter and setter (encapsulation)
    // ------------------------------------------------------------------
    public int getPassengerCapacity() {
        return passengerCapacity;
    }

    public void setPassengerCapacity(int passengerCapacity) {
        if (passengerCapacity <= 0) {
            throw new IllegalArgumentException("Passenger capacity must be greater than zero.");
        }
        this.passengerCapacity = passengerCapacity;
    }
}
