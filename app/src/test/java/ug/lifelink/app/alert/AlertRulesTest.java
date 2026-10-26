package ug.lifelink.app.alert;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.model.Urgency;

public class AlertRulesTest {

    private static UrgentRequest req(String id, String urgency, boolean verified, double distance) {
        return new UrgentRequest(id, "Test Hospital", "O+", 2, urgency, "", "",
                0.3, 32.5, verified, 1L, 0L, distance);
    }

    private static List<UrgentRequest> one(UrgentRequest r) {
        List<UrgentRequest> l = new ArrayList<>();
        l.add(r);
        return l;
    }

    @Test
    public void verifiedCriticalWithDistanceAlerts() {
        assertEquals(1, AlertRules.select(one(req("a", Urgency.CRITICAL, true, 4.0)), new HashSet<String>()).size());
    }

    @Test
    public void urgentAlertsToo() {
        assertEquals(1, AlertRules.select(one(req("a", Urgency.URGENT, true, 4.0)), new HashSet<String>()).size());
    }

    @Test
    public void routineStaysSilent() {
        assertEquals(0, AlertRules.select(one(req("a", Urgency.ROUTINE, true, 4.0)), new HashSet<String>()).size());
    }

    @Test
    public void unverifiedHospitalStaysSilent() {
        assertEquals(0, AlertRules.select(one(req("a", Urgency.CRITICAL, false, 4.0)), new HashSet<String>()).size());
    }

    @Test
    public void unknownDistanceStaysSilent() {
        assertEquals(0, AlertRules.select(one(req("a", Urgency.CRITICAL, true, Double.NaN)), new HashSet<String>()).size());
    }

    @Test
    public void alreadyNotifiedIsNotRepeated() {
        Set<String> seen = new HashSet<>();
        seen.add("a");
        assertEquals(0, AlertRules.select(one(req("a", Urgency.CRITICAL, true, 4.0)), seen).size());
        assertEquals(1, AlertRules.select(one(req("b", Urgency.CRITICAL, true, 4.0)), seen).size());
    }
}
