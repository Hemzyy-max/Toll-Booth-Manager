package tollbooth.model;

/**
 * ============================================================================
 *  FILE    : Bike.java
 *  PACKAGE : tollbooth.model
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INHERITANCE       : Bike IS-A Vehicle.
 *  2. METHOD OVERRIDING : calculateToll() returns the two wheeler rate.
 *                         This class ALSO overrides the OVERLOADED method
 *                         calculateToll(boolean fastTag), because a bike does
 *                         not carry a FASTag, so no discount is ever given.
 *                         It proves that overriding works for every version of
 *                         an overloaded method.
 *  3. CONSTRUCTOR OVERLOADING : two constructors, one with the engine capacity
 *                         and one without it.
 * ============================================================================
 */
public class Bike extends Vehicle {

    /** Default engine capacity in cubic centimetres (cc). */
    private static final int DEFAULT_ENGINE_CC = 150;

    /** Extra data that only a bike has. */
    private int engineCapacityCc;

    /**
     * CONSTRUCTOR 1 (full parameter list).
     *
     * @param vehicleNumber   registration number of the bike
     * @param ownerName       owner of the bike
     * @param rfidTag         RFID tag of the bike
     * @param engineCapacityCc engine capacity in cc
     */
    public Bike(String vehicleNumber, String ownerName, String rfidTag, int engineCapacityCc) {
        super(vehicleNumber, "Bike", ownerName, rfidTag);   // INHERITANCE
        setEngineCapacityCc(engineCapacityCc);
    }

    /** CONSTRUCTOR 2 : overloaded convenience constructor with the default cc. */
    public Bike(String vehicleNumber, String ownerName, String rfidTag) {
        this(vehicleNumber, ownerName, rfidTag, DEFAULT_ENGINE_CC);
    }

    /** METHOD OVERRIDING : a bike pays 30 rupees. */
    @Override
    public double calculateToll() {
        return TollRates.BIKE_TOLL;
    }

    /**
     * METHOD OVERRIDING of an OVERLOADED method.
     * A two wheeler never gets the FASTag discount in this project, so the
     * boolean parameter is simply ignored and the normal bike toll is returned.
     */
    @Override
    public double calculateToll(boolean fastTag) {
        return calculateToll();
    }

    /** METHOD OVERRIDING : bike specific display line. */
    @Override
    public String getExtraDetails() {
        return "Engine Capacity : " + engineCapacityCc + " cc (no FASTag for two wheelers)";
    }

    // ------------------------------------------------------------------
    // getter and setter (encapsulation)
    // ------------------------------------------------------------------
    public int getEngineCapacityCc() {
        return engineCapacityCc;
    }

    public void setEngineCapacityCc(int engineCapacityCc) {
        if (engineCapacityCc <= 0) {
            throw new IllegalArgumentException("Engine capacity must be greater than zero.");
        }
        this.engineCapacityCc = engineCapacityCc;
    }
}
