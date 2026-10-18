package ug.lifelink.app.util;

/**
 * Red blood cell compatibility between a donor and a patient, used to match donors to
 * urgent requests.
 *
 * Two independent rules must both pass:
 *   ABO: O can give to anyone; A to A and AB; B to B and AB; AB only to AB.
 *   Rh:  a Rh-negative donor can give to anyone; a Rh-positive donor only to Rh-positive.
 *
 * A donor whose group is unknown is never matched (the method returns false) until a blood
 * bank confirms it. Pure Java, so it is unit tested on a laptop.
 */
public final class BloodCompatibility {

    private BloodCompatibility() {
    }

    public static boolean canDonateTo(String donorGroup, String recipientGroup) {
        if (!BloodGroups.isRealGroup(donorGroup) || !BloodGroups.isRealGroup(recipientGroup)) {
            return false;
        }
        String donorAbo = abo(donorGroup);
        String recipientAbo = abo(recipientGroup);
        boolean aboOk = donorAbo.equals("O")
                || donorAbo.equals(recipientAbo)
                || recipientAbo.equals("AB");

        boolean donorPositive = donorGroup.endsWith("+");
        boolean recipientPositive = recipientGroup.endsWith("+");
        boolean rhOk = !donorPositive || recipientPositive;

        return aboOk && rhOk;
    }

    private static String abo(String group) {
        return group.substring(0, group.length() - 1); // "AB+" becomes "AB"
    }
}
