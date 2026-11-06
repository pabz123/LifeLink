package ug.lifelink.app.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Random;

import ug.lifelink.app.data.Facilities;
import ug.lifelink.app.util.PledgeCode;

public class PledgeSharingTest {

    @Test
    public void sharedRecordHoldsOnlyTheAgreedFields() throws Exception {
        JSONObject o = new JSONObject(PledgeJson.build("uid-1", "K7Q-2M4", "2026-11-20", 1700000000000L));
        assertEquals(5, o.length());
        assertEquals("uid-1", o.getString("ownerUid"));
        assertEquals("K7Q-2M4", o.getString("donorCode"));
        assertEquals("2026-11-20", o.getString("plannedDate"));
        assertEquals("PLANNED", o.getString("status"));
        assertEquals(1700000000000L, o.getLong("createdAt"));
        assertFalse(o.has("name") || o.has("phone") || o.has("bloodGroup") || o.has("email"));
    }

    @Test
    public void statusAnswersAreReadOnlyWhenKnown() {
        assertEquals("CONFIRMED", PledgeJson.parseStatus("\"CONFIRMED\""));
        assertEquals("DID_NOT_ATTEND", PledgeJson.parseStatus(" \"DID_NOT_ATTEND\" "));
        assertEquals(null, PledgeJson.parseStatus("null"));
        assertEquals(null, PledgeJson.parseStatus(""));
        assertEquals(null, PledgeJson.parseStatus("\"HACKED\""));
        assertEquals(null, PledgeJson.parseStatus("{\"error\":\"Permission denied\"}"));
    }

    @Test
    public void statusIsQuotedAsJson() {
        assertEquals("\"CANCELLED\"", PledgeJson.quote("CANCELLED"));
    }

    @Test
    public void codesLookRightAndAvoidConfusingCharacters() {
        Random r = new Random(7);
        for (int i = 0; i < 500; i++) {
            String c = PledgeCode.generate(r);
            assertTrue(c, PledgeCode.isValid(c));
            assertFalse(c, c.matches(".*[01OIL].*"));
        }
        assertFalse(PledgeCode.isValid("k7q-2m4"));
        assertFalse(PledgeCode.isValid(null));
    }

    @Test
    public void facilityKeysAreSafeAndStable() {
        assertEquals("mbarara-regional-referral-hospital", Facilities.idFor("Mbarara Regional Referral Hospital"));
        assertEquals("a-b-c", Facilities.idFor("  A / B . C  "));
        for (Facilities.Facility f : Facilities.all()) {
            assertTrue(f.name, Facilities.idFor(f.name).matches("[a-z0-9]+(-[a-z0-9]+)*"));
        }
    }

    @Test
    public void everyFacilityHasItsOwnKey() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Facilities.Facility f : Facilities.all()) {
            assertTrue("duplicate key for " + f.name, seen.add(Facilities.idFor(f.name)));
        }
    }

    @Test
    public void signUpAnswerIsParsed() {
        String json = "{\"idToken\":\"T\",\"refreshToken\":\"R\",\"expiresIn\":\"3600\",\"localId\":\"U\"}";
        AuthSession s = FirebaseAuthParser.parseSignUp(json, 1000L);
        assertNotNull(s);
        assertEquals("U", s.uid);
        assertEquals("T", s.idToken);
        assertEquals("R", s.refreshToken);
        assertEquals(1000L + 3600_000L, s.expiresAtMillis);
    }

    @Test
    public void refreshAnswerIsParsed() {
        String json = "{\"id_token\":\"T2\",\"refresh_token\":\"R2\",\"expires_in\":\"3600\",\"user_id\":\"U\"}";
        AuthSession s = FirebaseAuthParser.parseRefresh(json, 0L);
        assertNotNull(s);
        assertEquals("T2", s.idToken);
        assertEquals("U", s.uid);
    }

    @Test
    public void badAnswersGiveNoSession() {
        assertEquals(null, FirebaseAuthParser.parseSignUp("{\"error\":{\"message\":\"CONFIGURATION_NOT_FOUND\"}}", 0L));
        assertEquals(null, FirebaseAuthParser.parseSignUp("not json", 0L));
        assertEquals(null, FirebaseAuthParser.parseRefresh("{\"id_token\":\"\",\"refresh_token\":\"R\",\"expires_in\":\"3600\",\"user_id\":\"U\"}", 0L));
        assertEquals(null, FirebaseAuthParser.parseSignUp("{\"idToken\":\"T\",\"refreshToken\":\"R\",\"expiresIn\":\"0\",\"localId\":\"U\"}", 0L));
    }

    @Test
    public void tokenIsReplacedAMinuteBeforeItExpires() {
        AuthSession s = new AuthSession("U", "T", "R", 1_000_000L);
        assertTrue(s.isFresh(1_000_000L - AuthSession.LEEWAY_MS - 1));
        assertFalse(s.isFresh(1_000_000L - AuthSession.LEEWAY_MS));
        assertFalse(new AuthSession("U", "", "R", 9_999_999L).isFresh(0L));
    }
}
