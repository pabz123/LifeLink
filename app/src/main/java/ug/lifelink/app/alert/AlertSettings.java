package ug.lifelink.app.alert;

import android.content.Context;
import android.content.SharedPreferences;

/** Remembers that the notification permission was already asked for, so it is asked once. */
public final class AlertSettings {

    private static final String FILE = "lifelink_alerts";
    private static final String KEY_ASKED = "notification_permission_asked";

    private final SharedPreferences prefs;

    public AlertSettings(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean wasNotificationPermissionAsked() {
        return prefs.getBoolean(KEY_ASKED, false);
    }

    public void markNotificationPermissionAsked() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply();
    }
}
