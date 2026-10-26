package ug.lifelink.app.location;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Where the donor is, as far as the app knows, kept ONLY on the phone (SharedPreferences, never
 * uploaded). It holds:
 *   the last real position the phone gave us (used later by the background alert check, which
 *   is not allowed to ask for a fresh position because the app asks for foreground location only),
 *   whether the location permission has been asked for before (to tell "never asked" from
 *   "asked and refused"), and
 *   Demo mode's pretend position, which wins over the real one while it is switched on.
 */
public final class LocationStore {

    private static final String FILE = "lifelink_location";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LNG = "last_lng";
    private static final String KEY_LAST_AT = "last_at";
    private static final String KEY_ASKED = "permission_asked";
    private static final String KEY_DEMO_ON = "demo_on";
    private static final String KEY_DEMO_LABEL = "demo_label";
    private static final String KEY_DEMO_LAT = "demo_lat";
    private static final String KEY_DEMO_LNG = "demo_lng";

    private final SharedPreferences prefs;

    public LocationStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The position to use, or null when none is known. Demo mode wins when it is on. */
    public double[] effective() {
        if (isDemoOn()) {
            return new double[]{doubleOf(KEY_DEMO_LAT), doubleOf(KEY_DEMO_LNG)};
        }
        return last();
    }

    public void saveLast(double lat, double lng, long atMillis) {
        prefs.edit().putLong(KEY_LAST_LAT, Double.doubleToRawLongBits(lat))
                .putLong(KEY_LAST_LNG, Double.doubleToRawLongBits(lng))
                .putLong(KEY_LAST_AT, atMillis).apply();
    }

    /** The last real position, or null when there never was one. */
    public double[] last() {
        if (!prefs.contains(KEY_LAST_LAT) || !prefs.contains(KEY_LAST_LNG)) {
            return null;
        }
        return new double[]{doubleOf(KEY_LAST_LAT), doubleOf(KEY_LAST_LNG)};
    }

    public boolean wasPermissionAsked() {
        return prefs.getBoolean(KEY_ASKED, false);
    }

    public void markPermissionAsked() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply();
    }

    // ------------------------------------------------------------------ demo mode

    public boolean isDemoOn() {
        return prefs.getBoolean(KEY_DEMO_ON, false)
                && prefs.contains(KEY_DEMO_LAT) && prefs.contains(KEY_DEMO_LNG);
    }

    public String demoLabel() {
        return prefs.getString(KEY_DEMO_LABEL, "");
    }

    public void setDemo(String label, double lat, double lng) {
        prefs.edit().putBoolean(KEY_DEMO_ON, true).putString(KEY_DEMO_LABEL, label)
                .putLong(KEY_DEMO_LAT, Double.doubleToRawLongBits(lat))
                .putLong(KEY_DEMO_LNG, Double.doubleToRawLongBits(lng)).apply();
    }

    public void clearDemo() {
        prefs.edit().putBoolean(KEY_DEMO_ON, false).apply();
    }

    private double doubleOf(String key) {
        return Double.longBitsToDouble(prefs.getLong(key, 0L));
    }
}
