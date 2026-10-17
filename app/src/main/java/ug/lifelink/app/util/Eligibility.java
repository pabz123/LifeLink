package ug.lifelink.app.util;

/**
 * Donor eligibility rule used by the Home status card: a donor may give blood again
 * 90 days after the last donation (the interval used in the project proposal).
 * Pure Java, so it can be unit tested without a phone.
 */
public final class Eligibility {

    public static final int INTERVAL_DAYS = 90;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private Eligibility() {
    }

    public static int daysSince(long thenMillis, long nowMillis) {
        return (int) Math.max(0L, (nowMillis - thenMillis) / DAY_MS);
    }

    /** Days left before the donor may donate again; 0 means eligible now. */
    public static int daysUntilEligible(long lastDonationMillis, long nowMillis) {
        return Math.max(0, INTERVAL_DAYS - daysSince(lastDonationMillis, nowMillis));
    }
}
