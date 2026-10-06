package tollbooth.device;

/**
 * ============================================================================
 *  FILE    : Barrier.java
 *  PACKAGE : tollbooth.device
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. ENCAPSULATION : the "open" flag is private. Other classes can never set
 *                     it directly; they must call openBarrier() or
 *                     closeBarrier(). This protects the hardware state.
 *  2. ABSTRACTION   : the delay of the motor is simulated inside this class.
 *                     TollBooth simply calls openBarrier().
 *  3. MULTITHREADING + EXCEPTION HANDLING : Thread.sleep() is used to simulate
 *                     the real time taken by the barrier motor, and the
 *                     InterruptedException is handled properly.
 * ============================================================================
 */
public class Barrier {

    /** Time (in milliseconds) the motor needs to move the arm. */
    private static final long MOTOR_DELAY_MS = 400;

    private final String barrierId;

    // ENCAPSULATION : private hardware state
    private boolean open;
    private int totalOpenings;

    public Barrier(String barrierId) {
        this.barrierId = barrierId;
        this.open = false;
        this.totalOpenings = 0;
    }

    /** Opens the barrier arm (only a successful payment may call this). */
    public void openBarrier() {
        System.out.println("Barrier Opening...");
        simulateMotorDelay();
        this.open = true;
        this.totalOpenings = this.totalOpenings + 1;
        System.out.println("Barrier OPEN");
    }

    /** Closes the barrier arm after the vehicle has crossed. */
    public void closeBarrier() {
        System.out.println("Closing Barrier...");
        simulateMotorDelay();
        this.open = false;
        System.out.println("Barrier CLOSED");
    }

    /**
     * Simulates the physical movement of the arm.
     * Thread.sleep() can be interrupted, so the InterruptedException must be
     * caught. The thread is marked as interrupted again (best practice) before
     * the method ends.
     */
    private void simulateMotorDelay() {
        try {
            Thread.sleep(MOTOR_DELAY_MS);
        } catch (InterruptedException e) {
            System.out.println("Barrier motor interrupted : " + e.getMessage());
            Thread.currentThread().interrupt();     // restore the interrupt flag
        }
    }

    /** getter : is the barrier open right now ? */
    public boolean isOpen() {
        return open;
    }

    /** getter : how many times has the barrier opened ? */
    public int getTotalOpenings() {
        return totalOpenings;
    }

    public String getBarrierId() {
        return barrierId;
    }

    @Override
    public String toString() {
        return "Barrier " + barrierId + " (" + (open ? "OPEN" : "CLOSED") + ")";
    }
}
