package ug.lifelink.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeoTest {

    @Test
    public void samePointIsZero() {
        assertEquals(0.0, Geo.distanceKm(-0.6, 30.65, -0.6, 30.65), 1e-9);
    }

    @Test
    public void mbararaToKampalaIsAboutTwoHundredAndSixtyKm() {
        // Mbarara (-0.6072, 30.6545) to Kampala (0.3476, 32.5825): roughly 235 km in a straight line.
        double km = Geo.distanceKm(-0.6072, 30.6545, 0.3476, 32.5825);
        assertTrue("was " + km, km > 225 && km < 245);
    }

    @Test
    public void distanceIsSymmetric() {
        double a = Geo.distanceKm(0.35, 32.58, 1.08, 34.17);
        double b = Geo.distanceKm(1.08, 34.17, 0.35, 32.58);
        assertEquals(a, b, 1e-9);
    }

    @Test
    public void coordinateRanges() {
        assertTrue(Geo.isValidLatitude(90));
        assertTrue(Geo.isValidLongitude(-180));
        assertFalse(Geo.isValidLatitude(90.1));
        assertFalse(Geo.isValidLongitude(180.1));
        assertFalse(Geo.isValidLatitude(Double.NaN));
    }
}
