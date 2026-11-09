package ug.lifelink.app.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.model.Urgency;

public class RequestFeedParserTest {

    private static final String GOOD = "{\"facility\":\"Mbale Regional Referral Hospital\","
            + "\"bloodGroup\":\"B+\",\"units\":4,\"urgency\":\"CRITICAL\",\"note\":\"Maternal emergency\","
            + "\"contactPhone\":\"+256700000001\",\"lat\":1.0775,\"lng\":34.1753,"
            + "\"verified\":true,\"postedAt\":1792915200000,\"expiresAt\":1798102800000}";

    @Test
    public void emptyAndNullTextMeanNoRequests() throws JSONException {
        assertEquals(0, RequestFeedParser.parse("null").requests.size());
        assertEquals(0, RequestFeedParser.parse("").requests.size());
        assertEquals(0, RequestFeedParser.parse(null).requests.size());
    }

    @Test
    public void validObjectIsParsed() throws JSONException {
        RequestFeedParser.Result r = RequestFeedParser.parse("{\"r1\":" + GOOD + "}");
        assertEquals(1, r.requests.size());
        assertEquals(0, r.skipped);
        UrgentRequest q = r.requests.get(0);
        assertEquals("r1", q.getRemoteId());
        assertEquals("B+", q.getBloodGroup());
        assertEquals(4, q.getUnits());
        assertEquals(Urgency.CRITICAL, q.getUrgency());
        assertTrue(q.isVerified());
        assertTrue(q.hasLocation());
        assertFalse(q.hasDistance());
    }

    @Test
    public void badEntriesAreSkippedAndCounted() throws JSONException {
        String bad = "{\"facility\":\"X\",\"bloodGroup\":\"Z9\",\"units\":2}";
        String badUnits = GOOD.replace("\"units\":4", "\"units\":99");
        RequestFeedParser.Result r = RequestFeedParser.parse(
                "{\"ok\":" + GOOD + ",\"b1\":" + bad + ",\"b2\":" + badUnits + ",\"b3\":5}");
        assertEquals(1, r.requests.size());
        assertEquals(3, r.skipped);
    }

    @Test
    public void unknownUrgencyBecomesRoutine() throws JSONException {
        String text = GOOD.replace("CRITICAL", "PANIC");
        RequestFeedParser.Result r = RequestFeedParser.parse("{\"a\":" + text + "}");
        assertEquals(Urgency.ROUTINE, r.requests.get(0).getUrgency());
    }

    @Test
    public void outOfRangeCoordinatesAreDroppedButRequestKept() throws JSONException {
        String text = GOOD.replace("\"lat\":1.0775", "\"lat\":123.0");
        RequestFeedParser.Result r = RequestFeedParser.parse("{\"a\":" + text + "}");
        assertEquals(1, r.requests.size());
        assertFalse(r.requests.get(0).hasLocation());
    }

    @Test
    public void badPhoneIsBlankedAndLongNoteTruncated() throws JSONException {
        StringBuilder longNote = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longNote.append('x');
        }
        String text = GOOD.replace("+256700000001", "call me")
                .replace("Maternal emergency", longNote.toString());
        UrgentRequest q = RequestFeedParser.parse("{\"a\":" + text + "}").requests.get(0);
        assertEquals("", q.getContactPhone());
        assertTrue(q.getNote().length() <= 120);
    }

    @Test
    public void keyWithSlashIsRejected() throws JSONException {
        RequestFeedParser.Result r = RequestFeedParser.parse("{\"a/b\":" + GOOD + "}");
        assertEquals(0, r.requests.size());
        assertEquals(1, r.skipped);
    }

    @Test
    public void arrayFormIsAccepted() throws JSONException {
        RequestFeedParser.Result r = RequestFeedParser.parse("[null," + GOOD + "]");
        assertEquals(1, r.requests.size());
        assertEquals("1", r.requests.get(0).getRemoteId());
    }

    @Test(expected = JSONException.class)
    public void notJsonThrows() throws JSONException {
        RequestFeedParser.parse("<html>captive portal</html>");
    }
}
