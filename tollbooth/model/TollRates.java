package tollbooth.model;

import java.util.Locale;

/**
 * ============================================================================
 *  FILE    : TollRates.java
 *  PACKAGE : tollbooth.model
 * ----------------------------------------------------------------------------
 *  OOP CONCEPTS SHOWN IN THIS CLASS
 * ----------------------------------------------------------------------------
 *  1. ENCAPSULATION : a private constructor stops other classes from creating
 *                     "new TollRates()". This class is only a holder of shared
 *                     constants and helper methods.
 *  2. ABSTRACTION   : the rest of the project never worries about how a
 *                     discount or a currency format is calculated - it simply
 *                     calls applyFastagDiscount() or formatAmount().
 *
 *  All the toll rates of the project are kept in ONE place, so if the
 *  government changes a rate (or the discount percentage) a student has to
 *  edit only this single file. That is the "easy to modify" requirement.
 * ============================================================================
 */
public final class TollRates {

    // ------------------------------------------------------------------
    // TOLL RATES  (in Indian Rupees)
    // ------------------------------------------------------------------
    public static final double BIKE_TOLL  = 30.0;    // two wheeler
    public static final double CAR_TOLL   = 50.0;    // car / jeep / van
    public static final double BUS_TOLL   = 100.0;   // bus
    public static final double TRUCK_TOLL = 150.0;   // truck / lorry

    /** FASTag (RFID) users get this percentage of discount. */
    public static final double FASTAG_DISCOUNT_PERCENT = 10.0;

    /**
     * The Indian Rupee symbol written as a Unicode escape (\u20B9). A Unicode
     * escape keeps this source file pure ASCII, so the project compiles on
     * every computer (Windows, Linux, Mac) without any "-encoding UTF-8"
     * problem.
     *
     * chooseCurrencySymbol() checks whether the console of the computer is able
     * to print the Rupee symbol. Old Windows command prompts cannot, so the
     * program automatically falls back to "Rs." and the output never shows a
     * broken character.
     */
    public static final String CURRENCY = chooseCurrencySymbol();

    /** Picks the Rupee symbol, or "Rs." when the console cannot print it. */
    private static String chooseCurrencySymbol() {
        String rupeeSymbol = "\u20B9";
        String consoleEncoding = System.getProperty("stdout.encoding",
                System.getProperty("file.encoding", "UTF-8"));
        try {
            String roundTrip = new String(rupeeSymbol.getBytes(consoleEncoding), consoleEncoding);
            return rupeeSymbol.equals(roundTrip) ? rupeeSymbol : "Rs.";
        } catch (java.io.UnsupportedEncodingException e) {
            return rupeeSymbol;      // unknown encoding : keep the Rupee symbol
        }
    }

    /**
     * PRIVATE CONSTRUCTOR : nobody can write "new TollRates()".
     * A class with only static members is called a utility class.
     */
    private TollRates() {
        // intentionally empty
    }

    /**
     * Applies a discount percentage on an amount.
     *
     * @param amount          the original amount
     * @param discountPercent discount in percent (for example 10 means 10%)
     * @return the amount after discount, rounded to 2 decimals
     */
    public static double applyDiscount(double amount, double discountPercent) {
        if (amount <= 0 || discountPercent <= 0) {
            return roundToTwoDecimals(amount);
        }
        double discountAmount = amount * discountPercent / 100.0;
        return roundToTwoDecimals(amount - discountAmount);
    }

    /** Shortcut used for the standard FASTag discount. */
    public static double applyFastagDiscount(double amount) {
        return applyDiscount(amount, FASTAG_DISCOUNT_PERCENT);
    }

    /** Rounds a money value to 2 decimal places. */
    public static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Formats money for display. Whole amounts are shown without decimals
     * (50 becomes 50 and not 50.00) which keeps the console output clean.
     */
    public static String formatAmount(double amount) {
        double rounded = roundToTwoDecimals(amount);
        if (rounded == Math.rint(rounded)) {
            return CURRENCY + String.valueOf((long) rounded);
        }
        return CURRENCY + String.format(Locale.US, "%.2f", rounded);
    }

    /** Prints the rate card of the toll plaza (used by the menu). */
    public static void displayRateCard() {
        System.out.println("---------------------------------------");
        System.out.println("            TOLL RATE CARD");
        System.out.println("---------------------------------------");
        System.out.println("Bike  (2 wheeler) : " + formatAmount(BIKE_TOLL));
        System.out.println("Car   (4 wheeler) : " + formatAmount(CAR_TOLL));
        System.out.println("Bus               : " + formatAmount(BUS_TOLL));
        System.out.println("Truck / Lorry     : " + formatAmount(TRUCK_TOLL));
        System.out.println("FASTag discount   : " + (int) FASTAG_DISCOUNT_PERCENT + "%");
        System.out.println("---------------------------------------");
    }
}
