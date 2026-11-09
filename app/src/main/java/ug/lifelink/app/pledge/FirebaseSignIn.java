package ug.lifelink.app.pledge;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;

import ug.lifelink.app.net.AuthSession;
import ug.lifelink.app.net.FirebaseAuthParser;
import ug.lifelink.app.net.FirebaseConfig;
import ug.lifelink.app.net.RestHttp;

/**
 * An anonymous Firebase sign-in for this phone: no name, email or phone number. It gives the
 * phone a private id (uid) that the database rules use to let the phone write only its own
 * pledges. The refresh token is kept in the app's private preferences. Clearing the app's data
 * creates a new id, and the old pledges can then no longer be changed from this phone.
 *
 * Network calls: run OFF the main thread.
 */
public final class FirebaseSignIn {

    private static final String PREFS = "lifelink_firebase_auth";
    private static final String K_UID = "uid";
    private static final String K_TOKEN = "id_token";
    private static final String K_REFRESH = "refresh_token";
    private static final String K_EXPIRES = "expires_at";

    private FirebaseSignIn() {
    }

    /** @return a signed-in session with a fresh token; throws IOException when there is no network or the sign-in is refused */
    public static synchronized AuthSession ensure(Context context) throws IOException {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        AuthSession saved = new AuthSession(prefs.getString(K_UID, null), prefs.getString(K_TOKEN, null),
                prefs.getString(K_REFRESH, null), prefs.getLong(K_EXPIRES, 0L));
        if (saved.uid != null && saved.isFresh(now)) {
            return saved;
        }
        AuthSession session = null;
        if (saved.uid != null && saved.refreshToken != null) {
            session = refresh(saved.refreshToken, now);
        }
        if (session == null) {
            session = signUp(now);
        }
        prefs.edit().putString(K_UID, session.uid).putString(K_TOKEN, session.idToken)
                .putString(K_REFRESH, session.refreshToken).putLong(K_EXPIRES, session.expiresAtMillis)
                .apply();
        return session;
    }

    private static AuthSession signUp(long now) throws IOException {
        RestHttp.Response r = RestHttp.send("POST",
                "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + FirebaseConfig.WEB_API_KEY,
                "{\"returnSecureToken\":true}",
                "application/json");
        AuthSession s = r.ok() ? FirebaseAuthParser.parseSignUp(r.body, now) : null;
        if (s == null) {
            throw new IOException("sign-in refused (" + r.code + ")");
        }
        return s;
    }

    /** @return the new session, or null when the refresh token is no longer accepted */
    private static AuthSession refresh(String refreshToken, long now) throws IOException {
        RestHttp.Response r = RestHttp.send("POST",
                "https://securetoken.googleapis.com/v1/token?key=" + FirebaseConfig.WEB_API_KEY,
                "grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(refreshToken, "UTF-8"),
                "application/x-www-form-urlencoded");
        return r.ok() ? FirebaseAuthParser.parseRefresh(r.body, now) : null;
    }
}
