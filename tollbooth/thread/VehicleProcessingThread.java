package tollbooth.thread;

import tollbooth.exception.InvalidPaymentException;
import tollbooth.exception.InvalidRFIDException;
import tollbooth.model.TollRates;
import tollbooth.model.Vehicle;
import tollbooth.payment.CashPayment;
import tollbooth.payment.FastagPayment;
import tollbooth.payment.PaymentProcessor;
import tollbooth.service.TollBooth;

/**
 * ============================================================================
 *  FILE    : VehicleProcessingThread.java
 *  PACKAGE : tollbooth.thread
 * ----------------------------------------------------------------------------
 *  MULTITHREADING : each object of this class is one thread that processes ONE
 *  vehicle at the toll booth. Several threads can run "at the same time", which
 *  is exactly what happens at a real toll plaza when many vehicles arrive
 *  together.
 *
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. INHERITANCE : "extends Thread", so it inherits start(), sleep(), join()
 *                   and we only override run().
 *  2. METHOD OVERRIDING : run() is overridden - it holds the work of the thread
 *                   (like main() holds the work of a program).
 *  3. ENCAPSULATION : the booth and the vehicle are private final fields set
 *                   once by the constructor.
 *  4. EXCEPTION HANDLING : InterruptedException, InvalidRFIDException and
 *                   InvalidPaymentException are all caught inside the thread,
 *                   so one failed vehicle never stops the other threads.
 * ============================================================================
 */
public class VehicleProcessingThread extends Thread {

    /** Small pause (in milliseconds) that simulates a real vehicle movement. */
    private static final long ARRIVAL_DELAY_MS = 350;
    private static final long PASSING_DELAY_MS = 250;

    /** Starting cash balance of the simulated FASTag wallet. */
    private static final double DEMO_FASTAG_BALANCE = 1000.0;

    /** Counts the threads, only to give every lane thread a nice name. */
    private static int threadCounter = 0;

    // ENCAPSULATION : private final data members
    private final TollBooth tollBooth;
    private final Vehicle vehicle;
    private final boolean fastTagUser;

    /**
     * @param tollBooth   the shared booth used by every thread
     * @param vehicle     the vehicle handled by this thread
     * @param fastTagUser true when the driver wants to pay with FASTag
     */
    public VehicleProcessingThread(TollBooth tollBooth, Vehicle vehicle, boolean fastTagUser) {
        // Thread(String name) : a named thread makes the output easy to read.
        super("Lane-" + (++threadCounter));
        this.tollBooth = tollBooth;
        this.vehicle = vehicle;
        this.fastTagUser = fastTagUser;
    }

    /**
     * The work of the thread. This method is called by the Java Virtual Machine
     * when start() is used - NEVER call run() directly, otherwise no new thread
     * is created.
     */
    @Override
    public void run() {
        // SYNCHRONIZATION : the booth is locked for one vehicle at a time, just
        // like a single lane where two vehicles cannot be at the same point.
        synchronized (tollBooth) {
            System.out.println();
            System.out.println("--------------------------------------------------");
            System.out.println("[" + getName() + "] Vehicle " + vehicle.getVehicleNumber()
                    + " (" + vehicle.getVehicleType() + ") is being processed...");
            System.out.println("--------------------------------------------------");

            try {
                // sleep() simulates the time the vehicle needs to reach the booth.
                Thread.sleep(ARRIVAL_DELAY_MS);

                // The thread asks the booth for the toll of this vehicle.
                double baseToll = tollBooth.calculateToll(vehicle);
                double payableToll = fastTagUser
                        ? tollBooth.calculateToll(vehicle, true)
                        : baseToll;

                System.out.println("[" + getName() + "] Toll to be paid : "
                        + TollRates.formatAmount(payableToll)
                        + (fastTagUser ? " (FASTag discount applied)" : " (cash)"));

                // Choose the payment object for this vehicle.
                PaymentProcessor payment;
                if (fastTagUser) {
                    payment = new FastagPayment(vehicle.getRfidTag(), DEMO_FASTAG_BALANCE);
                } else {
                    // The driver gives a little more cash than the toll, so the
                    // customer also receives change : it looks realistic.
                    payment = new CashPayment(payableToll + 100.0);
                }

                // The complete workflow runs inside TollBooth (synchronized).
                tollBooth.processVehicle(vehicle.getRfidTag(), payment, fastTagUser);

                Thread.sleep(PASSING_DELAY_MS);   // vehicle leaves the lane
                System.out.println("[" + getName() + "] "
                        + vehicle.getVehicleNumber() + " left the toll booth.");

            } catch (InterruptedException e) {
                // Thread.sleep() was interrupted - stop this thread politely.
                System.out.println("[" + getName() + "] Thread was interrupted : " + e.getMessage());
                Thread.currentThread().interrupt();
            } catch (InvalidRFIDException e) {
                System.out.println("[" + getName() + "] RFID problem : " + e.getMessage());
            } catch (InvalidPaymentException e) {
                System.out.println("[" + getName() + "] Payment problem : " + e.getMessage());
                tollBooth.recordFailedTransaction(vehicle, 0.0, "Cash");
            } finally {
                // finally runs in every case, so the thread always signs off.
                System.out.println("[" + getName() + "] Thread finished.");
            }
        }
    }
}
