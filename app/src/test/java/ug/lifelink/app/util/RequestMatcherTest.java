package ug.lifelink.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.model.Urgency;

public class RequestMatcherTest {

    private static final long NOW = 1_800_000_000_000L;
    // Donor in Mbarara town.
    private static final double LAT = -0.6072;
    private static final double LNG = 30.6545;

    private static UrgentRequest req(String id, String group, String urgency,
                                     double lat, double lng, long expiresAt) {
        return new UrgentRequest(id, "Test Hospital " + id, group, 2, urgency, "", "",
                lat, lng, true, NOW - 1000, expiresAt, Double.NaN);
    }

    private static List<UrgentRequest> list(UrgentRequest... r) {
        List<UrgentRequest> l = new ArrayList<>();
        for (UrgentRequest x : r) {
            l.add(x);
        }
        return l;
    }

    @Test
    public void incompatibleGroupIsDropped() {
        List<UrgentRequest> out = RequestMatcher.match("A+", true, LAT, LNG, NOW,
                list(req("a", "B+", Urgency.CRITICAL, -0.61, 30.66, 0)));
        assertEquals(0, out.size());
    }

    @Test
    public void expiredRequestIsDropped() {
        List<UrgentRequest> out = RequestMatcher.match("O+", true, LAT, LNG, NOW,
                list(req("a", "O+", Urgency.CRITICAL, -0.61, 30.66, NOW - 1)));
        assertEquals(0, out.size());
    }

    @Test
    public void criticalOutsideFifteenKmIsDropped() {
        // About 55 km north of the donor.
        List<UrgentRequest> out = RequestMatcher.match("O+", true, LAT, LNG, NOW,
                list(req("a", "O+", Urgency.CRITICAL, -0.1, 30.66, 0)));
        assertEquals(0, out.size());
    }

    @Test
    public void routineWithinFiftyKmIsKeptWithDistance() {
        // About 33 km away.
        List<UrgentRequest> out = RequestMatcher.match("O+", true, LAT, LNG, NOW,
                list(req("a", "O+", Urgency.ROUTINE, -0.3, 30.66, 0)));
        assertEquals(1, out.size());
        assertTrue(out.get(0).hasDistance());
        assertTrue(out.get(0).getDistanceKm() > 30 && out.get(0).getDistanceKm() < 40);
    }

    @Test
    public void withoutLocationEverythingCompatibleIsShownWithoutDistance() {
        List<UrgentRequest> out = RequestMatcher.match("O+", false, 0, 0, NOW,
                list(req("a", "O+", Urgency.CRITICAL, 3.0, 40.0, 0)));
        assertEquals(1, out.size());
        assertTrue(!out.get(0).hasDistance());
    }

    @Test
    public void requestWithoutCoordinatesIsKeptEvenWithLocation() {
        List<UrgentRequest> out = RequestMatcher.match("O+", true, LAT, LNG, NOW,
                list(req("a", "O+", Urgency.URGENT, Double.NaN, Double.NaN, 0)));
        assertEquals(1, out.size());
        assertTrue(!out.get(0).hasDistance());
    }

    @Test
    public void mostUrgentFirstThenNearest() {
        List<UrgentRequest> out = RequestMatcher.match("O-", true, LAT, LNG, NOW, list(
                req("far-routine", "O-", Urgency.ROUTINE, -0.5, 30.7, 0),
                req("near-routine", "O-", Urgency.ROUTINE, -0.6, 30.66, 0),
                req("critical", "O-", Urgency.CRITICAL, -0.62, 30.65, 0)));
        assertEquals("critical", out.get(0).getRemoteId());
        assertEquals("near-routine", out.get(1).getRemoteId());
        assertEquals("far-routine", out.get(2).getRemoteId());
    }

    @Test
    public void rhNegativeRequestsGetALargerRadius() {
        // Roughly 20 km away: outside 15 km for Critical, inside 22.5 km for a negative group.
        UrgentRequest positive = req("p", "O+", Urgency.CRITICAL, -0.43, 30.72, 0);
        UrgentRequest negative = req("n", "O-", Urgency.CRITICAL, -0.43, 30.72, 0);
        assertEquals(0, RequestMatcher.match("O-", true, LAT, LNG, NOW, list(positive)).size());
        assertEquals(1, RequestMatcher.match("O-", true, LAT, LNG, NOW, list(negative)).size());
    }
}
