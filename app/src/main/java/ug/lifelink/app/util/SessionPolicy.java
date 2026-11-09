package ug.lifelink.app.util;

/**
 * The rule for how long a signed-in session may sit idle (app in the background, phone locked)
 * before the person must log in again. "Keep me signed in" is a separate, longer-lived choice and
 * ignores this timeout.
 *
 * Pure Java (no Android classes), so it is unit tested on a laptop.
 */
public final class SessionPolicy {

    /** Idle time after which a normal session ends. */
    public static final long IDLE_TIMEOUT_MS = 15L * 60 * 1000;

    private SessionPolicy() {
    }

    /**
     * @param lastActiveMillis when the person last used the app (0 or less means never)
     * @return true while the session is still valid. A clock that moved backwards ends the
     *         session, because the idle time cannot be trusted.
     */
    public static boolean isActive(long lastActiveMillis, long nowMillis) {
        if (lastActiveMillis <= 0L) {
            return false;
        }
        long idle = nowMillis - lastActiveMillis;
        return idle >= 0L && idle <= IDLE_TIMEOUT_MS;
    }
}
