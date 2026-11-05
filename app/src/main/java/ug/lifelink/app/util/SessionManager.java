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
    private static final String KEY_SESSION_USER_ID = "session_user_id";
    private static final String KEY_SESSION_LAST_ACTIVE = "session_last_active";
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

    /** Starts a session for a person who just logged in. It ends after SessionPolicy.IDLE_TIMEOUT_MS idle. */
    public void startSession(long userId) {
        prefs.edit().putLong(KEY_SESSION_USER_ID, userId)
                .putLong(KEY_SESSION_LAST_ACTIVE, System.currentTimeMillis()).apply();
    }

    /** Records that this user is using the app now. Does nothing for a different or ended session. */
    public void touch(long userId) {
        if (prefs.getLong(KEY_SESSION_USER_ID, NO_USER) == userId) {
            prefs.edit().putLong(KEY_SESSION_LAST_ACTIVE, System.currentTimeMillis()).apply();
        }
    }

    /**
     * True when this user may stay signed in: they chose "Keep me signed in", or their session
     * has not been idle for longer than the timeout.
     */
    public boolean isSessionValid(long userId, long nowMillis) {
        if (getRememberedUserId() == userId) {
            return true;
        }
        return prefs.getLong(KEY_SESSION_USER_ID, NO_USER) == userId
                && SessionPolicy.isActive(prefs.getLong(KEY_SESSION_LAST_ACTIVE, 0L), nowMillis);
    }

    /** The user the app may open straight to Home for: remembered, or an unexpired session. -1 for nobody. */
    public long resumableUserId(long nowMillis) {
        if (hasRememberedUser()) {
            return getRememberedUserId();
        }
        long sessionUser = prefs.getLong(KEY_SESSION_USER_ID, NO_USER);
        if (sessionUser != NO_USER && isSessionValid(sessionUser, nowMillis)) {
            return sessionUser;
        }
        return NO_USER;
    }

    /** Logout: ends both the remembered choice and the session. */
    public void clear() {
        prefs.edit().remove(KEY_REMEMBERED_USER_ID).remove(KEY_SESSION_USER_ID)
                .remove(KEY_SESSION_LAST_ACTIVE).apply();
    }
}
