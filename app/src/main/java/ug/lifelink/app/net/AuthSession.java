package ug.lifelink.app.net;

/** An anonymous Firebase sign-in: who this phone is, and a token that proves it for a while. */
public final class AuthSession {

    /** A token is replaced this long before it expires. */
    public static final long LEEWAY_MS = 60_000L;

    public final String uid;
    public final String idToken;
    public final String refreshToken;
    public final long expiresAtMillis;

    public AuthSession(String uid, String idToken, String refreshToken, long expiresAtMillis) {
        this.uid = uid;
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.expiresAtMillis = expiresAtMillis;
    }

    public boolean isFresh(long nowMillis) {
        return idToken != null && !idToken.isEmpty() && nowMillis + LEEWAY_MS < expiresAtMillis;
    }
}
