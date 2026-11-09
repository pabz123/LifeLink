package ug.lifelink.app.net;

import org.json.JSONException;
import org.json.JSONObject;

/** The text sent to and read from the shared database for one pledge. */
public final class PledgeJson {

    private PledgeJson() {
    }

    /**
     * The whole shared record. It holds a random code, the planned day and a status, and who
     * owns it. It holds no name, phone number or blood group.
     */
    public static String build(String ownerUid, String code, String plannedDate, long createdAt) {
        try {
            return new JSONObject()
                    .put("ownerUid", ownerUid)
                    .put("donorCode", code)
                    .put("plannedDate", plannedDate)
                    .put("status", "PLANNED")
                    .put("createdAt", createdAt)
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e); // cannot happen with plain values
        }
    }

    /** A JSON string value, for example "CANCELLED" with its quotes. */
    public static String quote(String value) {
        return JSONObject.quote(value);
    }

    /**
     * Reads the answer for the status child, for example {@code "CONFIRMED"}.
     * @return the status, or null when the answer is empty, null or not a known status
     */
    public static String parseStatus(String body) {
        if (body == null) {
            return null;
        }
        String t = body.trim();
        if (t.length() < 3 || t.charAt(0) != '"' || t.charAt(t.length() - 1) != '"') {
            return null;
        }
        String v = t.substring(1, t.length() - 1);
        switch (v) {
            case "PLANNED":
            case "CANCELLED":
            case "CONFIRMED":
            case "DID_NOT_ATTEND":
                return v;
            default:
                return null;
        }
    }
}
