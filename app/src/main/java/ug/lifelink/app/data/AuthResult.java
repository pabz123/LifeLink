package ug.lifelink.app.data;

import ug.lifelink.app.model.User;

/**
 * The outcome of a login or a password-reset attempt.
 *
 * SUCCESS: the credentials were right (the user is attached).
 * INVALID: wrong email, password or answer. The screen shows one generic message, so it never
 *          reveals which part was wrong.
 * LOCKED:  too many failed attempts; try again after lockedUntil.
 */
public final class AuthResult {

    public enum Status { SUCCESS, INVALID, LOCKED }

    private final Status status;
    private final User user;
    private final long lockedUntil;

    private AuthResult(Status status, User user, long lockedUntil) {
        this.status = status;
        this.user = user;
        this.lockedUntil = lockedUntil;
    }

    static AuthResult success(User user) {
        return new AuthResult(Status.SUCCESS, user, 0L);
    }

    static AuthResult invalid() {
        return new AuthResult(Status.INVALID, null, 0L);
    }

    static AuthResult locked(long lockedUntil) {
        return new AuthResult(Status.LOCKED, null, lockedUntil);
    }

    public Status getStatus() {
        return status;
    }

    public User getUser() {
        return user;
    }

    public long getLockedUntil() {
        return lockedUntil;
    }

    /** Whole minutes left on a lockout, rounded up, never less than 1. */
    public int getMinutesLeft(long nowMillis) {
        long left = lockedUntil - nowMillis;
        return (int) Math.max(1L, (left + 59_999L) / 60_000L);
    }
}
