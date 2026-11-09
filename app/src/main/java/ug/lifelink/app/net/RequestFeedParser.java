package ug.lifelink.app.net;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import ug.lifelink.app.model.Urgency;
import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.util.Geo;
import ug.lifelink.app.util.Validators;

/**
 * Turns the JSON text from the request feed into UrgentRequest objects.
 *
 * Data from the internet is never trusted. Each entry is checked against the same rules the
 * app uses for its own forms (facility name, real blood group, 1 to 20 units, note length), and
 * an entry that breaks a rule is skipped and counted, never shown. A bad entry cannot crash the
 * app or put odd text on screen.
 *
 * The feed is a JSON object keyed by request id:
 *   { "req_001": { "facility": "...", "bloodGroup": "O-", "units": 3, "urgency": "CRITICAL",
 *                  "note": "...", "lat": -0.6, "lng": 30.6, "contactPhone": "+256...",
 *                  "verified": true, "postedAt": 1792915200000, "expiresAt": 1798102800000 } }
 * Firebase answers the text "null" when the list is empty, which is accepted as "no requests".
 */
public final class RequestFeedParser {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9]{9,15}");

    /** The valid requests, and how many entries were skipped because they broke a rule. */
    public static final class Result {
        public final List<UrgentRequest> requests;
        public final int skipped;

        Result(List<UrgentRequest> requests, int skipped) {
            this.requests = requests;
            this.skipped = skipped;
        }
    }

    private RequestFeedParser() {
    }

    /** @throws JSONException when the text is not JSON at all */
    public static Result parse(String json) throws JSONException {
        List<UrgentRequest> list = new ArrayList<>();
        int skipped = 0;
        String text = json == null ? "" : json.trim();
        if (text.isEmpty() || text.equals("null")) {
            return new Result(list, 0);
        }

        if (text.startsWith("[")) {
            // Firebase turns keys like 0, 1, 2 into a JSON array. Our keys are text, but accept it.
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry == null) {
                    continue; // an empty slot, not a bad entry
                }
                UrgentRequest request = fromJson(String.valueOf(i), entry);
                if (request == null) {
                    skipped++;
                } else {
                    list.add(request);
                }
            }
            return new Result(list, skipped);
        }

        JSONObject root = new JSONObject(text);
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject entry = root.optJSONObject(key);
            UrgentRequest request = entry == null ? null : fromJson(key, entry);
            if (request == null) {
                skipped++;
            } else {
                list.add(request);
            }
        }
        return new Result(list, skipped);
    }

    /** Returns null when the entry breaks a rule. */
    static UrgentRequest fromJson(String key, JSONObject o) {
        if (key == null || !KEY.matcher(key).matches()) {
            return null;
        }
        String facility = text(o, "facility");
        if (Validators.facilityError(facility) != 0) {
            return null;
        }
        String bloodGroup = text(o, "bloodGroup");
        if (Validators.recordBloodGroupError(bloodGroup) != 0) {
            return null;
        }
        int units = o.optInt("units", -1);
        if (units < Validators.UNITS_MIN || units > Validators.UNITS_MAX) {
            return null;
        }

        String note = text(o, "note");
        if (note.length() > Validators.NOTE_MAX) {
            note = note.substring(0, Validators.NOTE_MAX);
        }
        String phone = text(o, "contactPhone");
        if (!PHONE.matcher(phone).matches()) {
            phone = ""; // keep the request, drop a number that is not a phone number
        }

        double lat = number(o, "lat");
        double lng = number(o, "lng");
        if (!Geo.isValidLatitude(lat) || !Geo.isValidLongitude(lng)) {
            lat = Double.NaN;
            lng = Double.NaN;
        }

        return new UrgentRequest(key, facility.trim(), bloodGroup, units,
                Urgency.parse(text(o, "urgency")), note, phone, lat, lng,
                o.optBoolean("verified", false),
                Math.max(0L, o.optLong("postedAt", 0L)),
                Math.max(0L, o.optLong("expiresAt", 0L)),
                Double.NaN);
    }

    /** A missing or null value gives an empty string (org.json would give the text "null"). */
    private static String text(JSONObject o, String name) {
        return o.isNull(name) ? "" : o.optString(name, "").trim();
    }

    private static double number(JSONObject o, String name) {
        return o.isNull(name) ? Double.NaN : o.optDouble(name, Double.NaN);
    }
}
