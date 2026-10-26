package ug.lifelink.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UrgencyTest {

    @Test
    public void parseFallsBackToRoutine() {
        assertEquals(Urgency.ROUTINE, Urgency.parse(null));
        assertEquals(Urgency.ROUTINE, Urgency.parse("whatever"));
        assertEquals(Urgency.CRITICAL, Urgency.parse("CRITICAL"));
    }

    @Test
    public void validity() {
        assertTrue(Urgency.isValid(Urgency.URGENT));
        assertFalse(Urgency.isValid("urgent "));
    }

    @Test
    public void rankOrdersCriticalFirst() {
        assertTrue(Urgency.rank(Urgency.CRITICAL) < Urgency.rank(Urgency.URGENT));
        assertTrue(Urgency.rank(Urgency.URGENT) < Urgency.rank(Urgency.ROUTINE));
    }

    @Test
    public void radiiByUrgencyAndGroup() {
        assertEquals(15.0, Urgency.radiusKm(Urgency.CRITICAL, "A+"), 1e-9);
        assertEquals(22.5, Urgency.radiusKm(Urgency.CRITICAL, "A-"), 1e-9);
        assertEquals(30.0, Urgency.radiusKm(Urgency.URGENT, "O+"), 1e-9);
        assertEquals(50.0, Urgency.radiusKm(Urgency.ROUTINE, "B+"), 1e-9);
    }
}
