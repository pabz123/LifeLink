package ug.lifelink.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers which user chose "Keep me signed in", using SharedPreferences
 * (small key-value settings stored in the app's private storage).
 *
 * Only the user's id is stored, never the password. Trade-off to record in the README:
 * anyone who can read the app's private storage (for example on a rooted phone) could
 * reuse the id, so "remember me" is a convenience, not a strong credential.
 */
public class SessionManager {

    private static final String PREFS_NAME = "lifelink_session";
    private static final String KEY_REMEMBERED_USER_ID = "remembered_user_id";
    private static final long NO_USER = -1L;

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void remember(long userId) {
        prefs.edit().putLong(KEY_REMEMBERED_USER_ID, userId).apply();
    }

    /** @return the remembered user's id, or -1 when nobody asked to be remembered */
    public long getRememberedUserId() {
        return prefs.getLong(KEY_REMEMBERED_USER_ID, NO_USER);
    }

    public boolean hasRememberedUser() {
        return getRememberedUserId() != NO_USER;
    }

    public void clear() {
        prefs.edit().remove(KEY_REMEMBERED_USER_ID).apply();
    }
}
