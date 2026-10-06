package tollbooth.model;

/**
 * ============================================================================
 *  FILE    : Car.java
 *  PACKAGE : tollbooth.model
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INHERITANCE        : "extends Vehicle" - a Car IS-A Vehicle, so it gets
 *                          vehicleNumber, ownerName, rfidTag and all the
 *                          methods of the parent class for free.
 *  2. CONSTRUCTOR        : super(...) passes the common data to the parent
 *                          constructor and this(...) calls another constructor
 *                          of the SAME class (constructor overloading).
 *  3. METHOD OVERRIDING  : calculateToll() of the parent is replaced by the
 *                          car rate (runtime polymorphism).
 *  4. ABSTRACTION        : the abstract method of the parent is implemented
 *                          here, so Car is a concrete (usable) class.
 * ============================================================================
 */
public class Car extends Vehicle {

    /** Default value used by the second constructor. */
    private static final int DEFAULT_SEATS = 5;

    /** Extra data that only a car has (encapsulation). */
    private int numberOfSeats;

    /**
     * CONSTRUCTOR 1 (full parameter list).
     *
     * @param vehicleNumber registration number of the car
     * @param ownerName     owner of the car
     * @param rfidTag       RFID tag of the car
     * @param numberOfSeats seating capacity of the car
     */
    public Car(String vehicleNumber, String ownerName, String rfidTag, int numberOfSeats) {
        // INHERITANCE : super() calls the Vehicle constructor and stores the
        // common data. "Car" is the vehicle type of this class.
        super(vehicleNumber, "Car", ownerName, rfidTag);
        setNumberOfSeats(numberOfSeats);
    }

    /**
     * CONSTRUCTOR 2 (convenience constructor).
     * CONSTRUCTOR OVERLOADING : same class, different parameter list.
     * this(...) reuses constructor 1 with the default seating capacity.
     */
    public Car(String vehicleNumber, String ownerName, String rfidTag) {
        this(vehicleNumber, ownerName, rfidTag, DEFAULT_SEATS);
    }

    /**
     * METHOD OVERRIDING : this single line is the heart of runtime
     * polymorphism. When a Vehicle reference points to a Car object, this
     * method runs and the car toll is charged.
     */
    @Override
    public double calculateToll() {
        return TollRates.CAR_TOLL;      // a car pays 50 rupees
    }

    /** METHOD OVERRIDING : adds the car specific information to the display. */
    @Override
    public String getExtraDetails() {
        return "Number of Seats: " + numberOfSeats;
    }

    // ------------------------------------------------------------------
    // getter and setter (encapsulation)
    // ------------------------------------------------------------------
    public int getNumberOfSeats() {
        return numberOfSeats;
    }

    public void setNumberOfSeats(int numberOfSeats) {
        if (numberOfSeats <= 0) {
            throw new IllegalArgumentException("Number of seats must be greater than zero.");
        }
        this.numberOfSeats = numberOfSeats;
    }
}
