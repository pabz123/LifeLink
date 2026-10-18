package ug.lifelink.app.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The values a blood group can have in the database.
 *
 * Eight real groups, plus two special codes:
 *   UNKNOWN: the donor has never been tested. They can register and donate; a blood bank
 *            confirms the real group at the first donation.
 *   NA:      not applicable, used for hospital staff, who are not donors.
 */
public final class BloodGroups {

    public static final String UNKNOWN = "UNKNOWN";
    public static final String NOT_APPLICABLE = "NA";

    private static final Set<String> REAL = new HashSet<>(
            Arrays.asList("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"));

    private BloodGroups() {
    }

    /** True for one of the eight real groups. */
    public static boolean isRealGroup(String code) {
        return code != null && REAL.contains(code);
    }

    /** True when the group is known and can be used for matching. */
    public static boolean isKnown(String code) {
        return isRealGroup(code);
    }

    /** Short text for the round badge on a list row. */
    public static String badgeText(String code) {
        if (isRealGroup(code)) {
            return code;
        }
        return UNKNOWN.equals(code) ? "?" : "-";
    }
}
