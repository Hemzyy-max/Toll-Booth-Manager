package tollbooth.exception;

/**
 * ============================================================================
 *  FILE    : InvalidVehicleException.java
 *  PACKAGE : tollbooth.exception
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. EXCEPTION HANDLING : thrown while REGISTERING a vehicle when
 *                          - the registration number has a wrong format,
 *                          - the same registration number is registered twice,
 *                          - the same RFID tag is already used,
 *                          - or the number of the vehicle is empty.
 *  2. INHERITANCE        : extends Exception (checked exception).
 *  3. ENCAPSULATION      : the offending vehicle number is private + getter.
 * ============================================================================
 */
public class InvalidVehicleException extends Exception {

    /** Version number required for a serializable class. */
    private static final long serialVersionUID = 1L;

    /** The vehicle number that caused the problem. */
    private final String vehicleNumber;

    /** CONSTRUCTOR 1 : only the error message. */
    public InvalidVehicleException(String message) {
        super(message);
        this.vehicleNumber = "";
    }

    /** CONSTRUCTOR 2 : error message + the vehicle number. */
    public InvalidVehicleException(String message, String vehicleNumber) {
        super(message);
        this.vehicleNumber = (vehicleNumber == null) ? "" : vehicleNumber;
    }

    /** getter for the offending vehicle number. */
    public String getVehicleNumber() {
        return vehicleNumber;
    }
}
