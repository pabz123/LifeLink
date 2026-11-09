package ug.lifelink.app.net;

import org.json.JSONException;
import org.json.JSONObject;

/** Reads the two Firebase sign-in answers. Plain Java apart from org.json, so it is unit tested. */
public final class FirebaseAuthParser {

    private FirebaseAuthParser() {
    }

    /** Answer of accounts:signUp: idToken, refreshToken, localId, expiresIn (seconds, as text). */
    public static AuthSession parseSignUp(String json, long nowMillis) {
        try {
            JSONObject o = new JSONObject(json);
            return build(o.getString("localId"), o.getString("idToken"),
                    o.getString("refreshToken"), o.getString("expiresIn"), nowMillis);
        } catch (JSONException | NumberFormatException e) {
            return null;
        }
    }

    /** Answer of the token refresh: id_token, refresh_token, user_id, expires_in. */
    public static AuthSession parseRefresh(String json, long nowMillis) {
        try {
            JSONObject o = new JSONObject(json);
            return build(o.getString("user_id"), o.getString("id_token"),
                    o.getString("refresh_token"), o.getString("expires_in"), nowMillis);
        } catch (JSONException | NumberFormatException e) {
            return null;
        }
    }

    private static AuthSession build(String uid, String idToken, String refreshToken,
                                     String expiresInSeconds, long nowMillis) {
        if (uid.isEmpty() || idToken.isEmpty() || refreshToken.isEmpty()) {
            return null;
        }
        long seconds = Long.parseLong(expiresInSeconds.trim());
        if (seconds <= 0) {
            return null;
        }
        return new AuthSession(uid, idToken, refreshToken, nowMillis + seconds * 1000L);
    }
}
