package ug.lifelink.app.model;

/**
 * How urgent a blood request is, and how far from the hospital a donor is still asked to help.
 * A critical need is life-threatening and only donors who can arrive quickly are useful, so its
 * radius is the smallest. Routine needs can wait, so the app looks further for donors.
 *
 * Rh-negative groups are much rarer, so the radius is widened for them (a rare group has to
 * come from further away).
 *
 * Pure Java (no Android classes), so it is unit tested on a laptop.
 */
public final class Urgency {

    public static final String CRITICAL = "CRITICAL";
    public static final String URGENT = "URGENT";
    public static final String ROUTINE = "ROUTINE";

    public static final double RADIUS_CRITICAL_KM = 15.0;
    public static final double RADIUS_URGENT_KM = 30.0;
    public static final double RADIUS_ROUTINE_KM = 50.0;
    public static final double NEGATIVE_GROUP_FACTOR = 1.5;

    private Urgency() {
    }

    public static boolean isValid(String urgency) {
        return CRITICAL.equals(urgency) || URGENT.equals(urgency) || ROUTINE.equals(urgency);
    }

    /** Unknown or missing text becomes ROUTINE, the least alarming level. */
    public static String parse(String text) {
        if (text == null) {
            return ROUTINE;
        }
        String clean = text.trim().toUpperCase(java.util.Locale.ROOT);
        return isValid(clean) ? clean : ROUTINE;
    }

    /** 0 is the most urgent, so sorting by rank puts critical requests first. */
    public static int rank(String urgency) {
        if (CRITICAL.equals(urgency)) {
            return 0;
        }
        return URGENT.equals(urgency) ? 1 : 2;
    }

    /** How far from the hospital a matching donor may be, in kilometres. */
    public static double radiusKm(String urgency, String requestBloodGroup) {
        double base;
        if (CRITICAL.equals(urgency)) {
            base = RADIUS_CRITICAL_KM;
        } else if (URGENT.equals(urgency)) {
            base = RADIUS_URGENT_KM;
        } else {
            base = RADIUS_ROUTINE_KM;
        }
        boolean rhNegative = requestBloodGroup != null && requestBloodGroup.endsWith("-");
        return rhNegative ? base * NEGATIVE_GROUP_FACTOR : base;
    }
}
