package tollbooth.device;

import java.util.regex.Pattern;

import tollbooth.exception.InvalidRFIDException;

/**
 * ============================================================================
 *  FILE    : RFIDDevice.java
 *  PACKAGE : tollbooth.device
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INTERFACE IMPLEMENTATION : "implements RFIDReader" - the class promises
 *                     to provide readRFID() and validateRFID().
 *  2. ABSTRACTION   : the hardware behaviour is hidden inside this class. The
 *                     service layer only calls readRFID() / validateRFID().
 *  3. ENCAPSULATION : lastScannedTag, totalScans and the tag counter are
 *                     private. The outside world can only read them.
 *  4. EXCEPTION HANDLING : simulateScan() throws InvalidRFIDException with the
 *                     "throws" keyword when the tag is not in the RFID####
 *                     format.
 *
 *  "IoT" part : this class is the software stand-in for the real RFID reader
 *  hardware at the toll plaza. It stores the tag that the antenna detected and
 *  then the program reads it, exactly like a serial/USB reader would deliver a
 *  tag id to the computer.
 * ============================================================================
 */
public class RFIDDevice implements RFIDReader {

    /** A valid tag looks like RFID1001 : the word RFID + exactly 4 digits. */
    private static final Pattern RFID_PATTERN = Pattern.compile("^RFID\\d{4}$");

    /** Static counter used by generateNewTag() so no two tags are the same. */
    private static int tagSerialCounter = 1000;

    // ------------------------------------------------------------------
    // ENCAPSULATION : private state of the device
    // ------------------------------------------------------------------
    private String lastScannedTag;
    private int totalScans;

    /** Constructor : the device starts with an empty scanner memory. */
    public RFIDDevice() {
        this.lastScannedTag = "";
        this.totalScans = 0;
    }

    /**
     * FACADE method for the simulation : the antenna "detects" the tag. It also
     * validates the tag, so an unreadable / damaged tag is reported immediately.
     *
     * @param rfidTag the tag that the antenna picked up
     * @throws InvalidRFIDException when the tag format is wrong
     */
    public void simulateScan(String rfidTag) throws InvalidRFIDException {
        if (!validateRFID(rfidTag)) {
            throw new InvalidRFIDException(
                    "RFID tag format is not valid (expected format : RFID1001)", rfidTag);
        }
        this.lastScannedTag = rfidTag.trim().toUpperCase();
        this.totalScans = this.totalScans + 1;
        System.out.println("RFID SCANNER : tag detected -> " + this.lastScannedTag);
    }

    /**
     * INTERFACE METHOD : returns the tag that the antenna is holding. In this
     * simulation it is the tag stored by the last simulateScan() call.
     */
    @Override
    public String readRFID() {
        return lastScannedTag;
    }

    /**
     * INTERFACE METHOD : checks the format of a tag.
     * null, an empty text or anything other than RFID + 4 digits is rejected.
     */
    @Override
    public boolean validateRFID(String rfidTag) {
        if (rfidTag == null) {
            return false;
        }
        return RFID_PATTERN.matcher(rfidTag.trim().toUpperCase()).matches();
    }

    /**
     * Creates the next free RFID tag for a newly registered vehicle :
     * RFID1001, RFID1002, RFID1003 ...
     *
     * @return a brand new tag
     */
    public String generateNewTag() {
        tagSerialCounter = tagSerialCounter + 1;
        return "RFID" + tagSerialCounter;
    }

    /**
     * Keeps the automatic counter in step when a tag is typed manually by the
     * user (for example RFID1010). Without this method generateNewTag() could
     * create a tag that already exists.
     *
     * @param rfidTag a tag that is already in use
     */
    public void syncTagSerial(String rfidTag) {
        if (!validateRFID(rfidTag)) {
            return;
        }
        int serial = Integer.parseInt(rfidTag.trim().toUpperCase().substring(4));
        if (serial > tagSerialCounter) {
            tagSerialCounter = serial;
        }
    }

    // ------------------------------------------------------------------
    // getters (encapsulation : read only access)
    // ------------------------------------------------------------------
    public int getTotalScans() {
        return totalScans;
    }

    @Override
    public String toString() {
        return "RFID reader (scans so far : " + totalScans + ")";
    }
}
