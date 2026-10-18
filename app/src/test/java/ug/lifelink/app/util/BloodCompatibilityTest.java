package ug.lifelink.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the donor-to-request matching rule. The expected results follow the standard
 * red blood cell compatibility chart.
 */
public class BloodCompatibilityTest {

    private static final String[] ALL = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};

    @Test
    public void oNegative_isUniversalDonor() {
        for (String recipient : ALL) {
            assertTrue("O- should give to " + recipient, BloodCompatibility.canDonateTo("O-", recipient));
        }
    }

    @Test
    public void abPositive_canOnlyGiveToAbPositive() {
        for (String recipient : ALL) {
            assertEquals(recipient.equals("AB+"), BloodCompatibility.canDonateTo("AB+", recipient));
        }
    }

    @Test
    public void oPositive_givesToAnyPositive_butNotNegative() {
        assertTrue(BloodCompatibility.canDonateTo("O+", "B+"));
        assertTrue(BloodCompatibility.canDonateTo("O+", "AB+"));
        assertFalse(BloodCompatibility.canDonateTo("O+", "A-"));
        assertFalse(BloodCompatibility.canDonateTo("O+", "O-"));
    }

    @Test
    public void aNegative_givesToAAndAb_inBothRh() {
        assertTrue(BloodCompatibility.canDonateTo("A-", "A+"));
        assertTrue(BloodCompatibility.canDonateTo("A-", "AB-"));
        assertFalse(BloodCompatibility.canDonateTo("A-", "B+"));
    }

    @Test
    public void everyGroup_canDonateToItself() {
        for (String group : ALL) {
            assertTrue(BloodCompatibility.canDonateTo(group, group));
        }
    }

    @Test
    public void unknownDonor_isNeverMatched() {
        for (String recipient : ALL) {
            assertFalse(BloodCompatibility.canDonateTo(BloodGroups.UNKNOWN, recipient));
        }
    }

    @Test
    public void unknownRecipient_isNeverMatched() {
        assertFalse(BloodCompatibility.canDonateTo("O-", BloodGroups.UNKNOWN));
        assertFalse(BloodCompatibility.canDonateTo("O-", null));
    }

    @Test
    public void badgeText_marksUnknownGroup() {
        assertEquals("O+", BloodGroups.badgeText("O+"));
        assertEquals("?", BloodGroups.badgeText(BloodGroups.UNKNOWN));
        assertEquals("-", BloodGroups.badgeText(BloodGroups.NOT_APPLICABLE));
    }
}
