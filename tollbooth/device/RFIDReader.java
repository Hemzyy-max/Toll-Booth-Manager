package tollbooth.device;

/**
 * ============================================================================
 *  FILE    : RFIDReader.java
 *  PACKAGE : tollbooth.device
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INTERFACE    : an interface is a 100% abstract type. Every method inside
 *                    it is public and abstract by default, so we do not write
 *                    "abstract" here.
 *  2. ABSTRACTION  : the rest of the project only knows "RFIDReader". It does
 *                    not care whether the tag is read by a real USB reader or
 *                    by the simulated class RFIDDevice.
 *  3. LOOSE COUPLING : TollBooth depends on this interface, so the hardware
 *                    class can be replaced without touching TollBooth.
 * ============================================================================
 */
public interface RFIDReader {

    /**
     * Simulates reading the tag that is currently in front of the antenna.
     *
     * @return the RFID tag that was scanned (for example RFID1001)
     */
    String readRFID();

    /**
     * Checks whether the given tag is a valid RFID tag of this system.
     *
     * @param rfidTag the tag to check
     * @return true when the tag is valid, otherwise false
     */
    boolean validateRFID(String rfidTag);
}
