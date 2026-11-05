package ug.lifelink.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import ug.lifelink.app.util.Validators;

public class FacilitiesTest {

    @Test
    public void nearestFirstFromMbarara() {
        List<String> names = Facilities.namesNear(true, -0.6072, 30.6545);
        assertTrue(names.get(0).startsWith("Mbarara"));
        assertTrue(names.get(1).startsWith("Mbarara"));
    }

    @Test
    public void nearestFirstFromMbale() {
        assertTrue(Facilities.namesNear(true, 1.08, 34.17).get(0).startsWith("Mbale"));
    }

    @Test
    public void withoutLocationTheListIsAlphabetical() {
        List<String> names = Facilities.namesNear(false, 0, 0);
        for (int i = 1; i < names.size(); i++) {
            assertTrue(names.get(i - 1).compareTo(names.get(i)) <= 0);
        }
    }

    @Test
    public void everyFacilityAppearsOnceAndIsKnown() {
        List<String> names = Facilities.namesNear(true, 0.3, 32.5);
        assertEquals(Facilities.all().size(), names.size());
        assertEquals(names.size(), new HashSet<>(names).size());
        assertTrue(Facilities.isKnown(names.get(0)));
        assertFalse(Facilities.isKnown("Imaginary Hospital"));
    }

    @Test
    public void everyNameIsAcceptedByTheFacilityRule() {
        for (Facilities.Facility f : Facilities.all()) {
            assertTrue(f.name, Validators.facilityError(f.name) == 0);
        }
    }
}
