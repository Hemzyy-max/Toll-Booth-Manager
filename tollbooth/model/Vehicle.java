package tollbooth.model;

/**
 * ============================================================================
 *  FILE    : Vehicle.java
 *  PACKAGE : tollbooth.model
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. ABSTRACTION    : "abstract class Vehicle" tells WHAT every vehicle can do
 *                      (calculateToll) but not HOW it is done. Writing
 *                      "new Vehicle()" is a compile-time error, because an
 *                      abstract class is only a blueprint.
 *  2. ENCAPSULATION  : every data member is "private" (data hiding). The data
 *                      can be read or changed only through public getters and
 *                      setter methods.
 *  3. INHERITANCE    : Car, Bike, Truck and Bus use "extends Vehicle", so they
 *                      automatically get all the fields and methods written
 *                      here (code reusability).
 *  4. POLYMORPHISM   : calculateToll() is overridden (runtime polymorphism) in
 *                      every child class. A "Vehicle" reference can point to
 *                      any child object and the correct method is selected at
 *                      RUN TIME.
 *  5. OVERLOADING    : calculateToll() is written three times with different
 *                      parameter lists (compile-time polymorphism).
 * ============================================================================
 */
public abstract class Vehicle {

    // ------------------------------------------------------------------------
    // ENCAPSULATION : private data members. Outside classes cannot touch these
    // variables directly, so a Vehicle object can never be put into an invalid
    // state by mistake.
    // ------------------------------------------------------------------------
    private String vehicleNumber;   // example : TN01AB1234
    private String vehicleType;     // example : Car
    private String ownerName;       // example : Hemachandran
    private String rfidTag;         // example : RFID1001
    private int totalTrips;         // how many times this vehicle crossed a booth

    /**
     * CONSTRUCTOR (parameterized) : initialises the common data of every
     * vehicle. It is declared "protected" because only the child classes are
     * allowed to call it - an abstract class is never created directly.
     *
     * @param vehicleNumber registration number of the vehicle
     * @param vehicleType   Car / Bike / Truck / Bus
     * @param ownerName     name of the vehicle owner
     * @param rfidTag       RFID tag stuck on the vehicle
     */
    protected Vehicle(String vehicleNumber, String vehicleType, String ownerName, String rfidTag) {
        setVehicleNumber(vehicleNumber);   // every setter validates its input
        setVehicleType(vehicleType);
        setOwnerName(ownerName);
        setRfidTag(rfidTag);
        this.totalTrips = 0;
    }

    // ------------------------------------------------------------------------
    // ABSTRACTION : a method without a body. Every child class MUST provide its
    // own implementation of this method. This is the "contract" of Vehicle.
    // ------------------------------------------------------------------------
    public abstract double calculateToll();

    // ------------------------------------------------------------------------
    // METHOD OVERLOADING (compile-time polymorphism)
    // Same method name, different parameter list. The compiler decides which
    // version to call by looking at the arguments.
    // ------------------------------------------------------------------------

    /**
     * Overloaded version : used when the vehicle pays through FASTag.
     * A FASTag user gets the standard discount.
     *
     * @param fastTag true when the vehicle pays with FASTag
     * @return toll amount after the FASTag discount
     */
    public double calculateToll(boolean fastTag) {
        // NOTE : calculateToll() below is the ABSTRACT method. Which code runs
        // here is decided at run time by the actual object (Car, Truck, ...).
        // This is RUNTIME POLYMORPHISM inside a compile-time overload.
        double baseToll = calculateToll();
        if (fastTag) {
            return TollRates.applyFastagDiscount(baseToll);
        }
        return baseToll;
    }

    /**
     * Overloaded version : used when a special discount percentage is to be
     * applied (festival offer, monthly pass, and so on).
     *
     * @param fastTag        true when the vehicle pays with FASTag
     * @param discountPercent discount to apply, for example 10 for 10%
     * @return toll amount after the given discount
     */
    public double calculateToll(boolean fastTag, double discountPercent) {
        double baseToll = calculateToll();
        if (fastTag) {
            return TollRates.applyDiscount(baseToll, discountPercent);
        }
        return baseToll;
    }

    /**
     * Concrete (non-abstract) method of the abstract class. It prints the
     * common details of every vehicle ONCE and then asks the child class for
     * its extra information through getExtraDetails().
     * Children only override the small part that really changes.
     */
    public void displayVehicleDetails() {
        System.out.println("---------------------------------------");
        System.out.println("Vehicle Number : " + vehicleNumber);
        System.out.println("Owner Name     : " + ownerName);
        System.out.println("Vehicle Type   : " + vehicleType);
        System.out.println("RFID Tag       : " + rfidTag);
        System.out.println("Toll Rate      : " + TollRates.formatAmount(calculateToll()));
        System.out.println("Trips Completed: " + totalTrips);
        String extraDetails = getExtraDetails();
        if (!extraDetails.isEmpty()) {
            System.out.println(extraDetails);
        }
        System.out.println("---------------------------------------");
    }

    /**
     * METHOD OVERRIDING (runtime polymorphism) : the parent gives an empty
     * implementation, every child class overrides it with its own extra data.
     */
    public String getExtraDetails() {
        return "";
    }

    /** Increases the trip counter after a successful toll transaction. */
    public void recordTrip() {
        this.totalTrips = this.totalTrips + 1;
    }

    // ------------------------------------------------------------------------
    // getters and setters : the only public door to the private data members
    // ------------------------------------------------------------------------

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    /**
     * Setter with validation. If a wrong value is given the method fails fast
     * instead of storing junk inside the object.
     */
    public void setVehicleNumber(String vehicleNumber) {
        // A registration number is always stored in capital letters.
        this.vehicleNumber = requireText(vehicleNumber, "Vehicle number").toUpperCase();
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = requireText(vehicleType, "Vehicle type");
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = requireText(ownerName, "Owner name");
    }

    public String getRfidTag() {
        return rfidTag;
    }

    public void setRfidTag(String rfidTag) {
        this.rfidTag = requireText(rfidTag, "RFID tag").toUpperCase();
    }

    public int getTotalTrips() {
        return totalTrips;
    }

    /**
     * METHOD OVERRIDING : toString() belongs to the Object class and is
     * overridden here, so printing a Vehicle shows useful text instead of a
     * memory address like tollbooth.model.Car@1b6d3586
     */
    @Override
    public String toString() {
        return vehicleType + " [" + vehicleNumber + "] owned by " + ownerName
                + " (RFID: " + rfidTag + ")";
    }

    /**
     * Small private helper used by all the setters. Keeping the check in one
     * place is better than repeating it in every setter (DRY principle).
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty.");
        }
        return value.trim();
    }
}
