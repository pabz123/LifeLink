package ug.lifelink.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Locale;

import ug.lifelink.app.model.User;
import ug.lifelink.app.util.BloodGroups;
import ug.lifelink.app.util.PasswordHasher;
import ug.lifelink.app.util.Validators;

/**
 * All user-related database access lives here, so screens never write SQL.
 *
 * Controls in this class:
 *  - Every query passes input through "?" placeholders or ContentValues (no SQL injection).
 *  - Passwords and security answers are salted and hashed, never stored as text.
 *  - create() and resetPassword() re-validate their input (defence in depth).
 *  - Five wrong passwords or answers lock the account for five minutes (brute-force limit).
 *
 * These methods touch the disk and hash secrets, so callers must run them OFF the main thread.
 */
public class UserRepository {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCKOUT_MS = 5L * 60 * 1000;

    private final DbHelper helper;

    public UserRepository(Context context) {
        this.helper = DbHelper.getInstance(context);
    }

    public boolean emailExists(String email) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT 1 FROM " + DbHelper.T_USERS + " WHERE " + DbHelper.C_EMAIL + " = ? LIMIT 1",
                new String[]{normalizeEmail(email)})) {
            return c.moveToFirst();
        }
    }

    /**
     * Creates a user. Throws IllegalArgumentException if any value fails validation.
     *
     * @return the new row id, or -1 if the insert failed (for example a duplicate email)
     */
    public long create(String fullName, String email, String phone, String bloodGroup, String role,
                       String password, int securityQuestion, String securityAnswer) {
        boolean hospital = User.ROLE_HOSPITAL.equals(role);
        if (!hospital && !User.ROLE_DONOR.equals(role)) {
            throw new IllegalArgumentException("Invalid role");
        }
        String group = hospital ? BloodGroups.NOT_APPLICABLE : bloodGroup;
        String cleanEmail = normalizeEmail(email);

        requireValid(Validators.nameError(fullName), "name");
        requireValid(Validators.emailError(cleanEmail), "email");
        requireValid(Validators.phoneError(phone), "phone");
        requireValid(Validators.bloodGroupError(role, group), "blood group");
        requireValid(Validators.passwordError(password), "password");
        requireValid(Validators.securityQuestionError(securityQuestion), "security question");
        requireValid(Validators.securityAnswerError(securityAnswer), "security answer");

        String salt = PasswordHasher.newSalt();
        String answerSalt = PasswordHasher.newSalt();

        ContentValues values = new ContentValues();
        values.put(DbHelper.C_FULL_NAME, fullName.trim());
        values.put(DbHelper.C_EMAIL, cleanEmail);
        values.put(DbHelper.C_PHONE, Validators.normalizePhone(phone));
        values.put(DbHelper.C_BLOOD_GROUP, group);
        values.put(DbHelper.C_ROLE, role);
        values.put(DbHelper.C_PASSWORD_HASH, PasswordHasher.hash(password, salt));
        values.put(DbHelper.C_SALT, salt);
        values.put(DbHelper.C_SECURITY_QUESTION, securityQuestion);
        values.put(DbHelper.C_ANSWER_HASH,
                PasswordHasher.hash(Validators.normalizeAnswer(securityAnswer), answerSalt));
        values.put(DbHelper.C_ANSWER_SALT, answerSalt);
        values.put(DbHelper.C_CREATED_AT, System.currentTimeMillis());

        return helper.getWritableDatabase().insert(DbHelper.T_USERS, null, values);
    }

    /** Checks an email and password pair, with lockout after repeated failures. */
    public AuthResult authenticate(String email, String password) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();

        long id;
        String salt;
        String storedHash;
        int attempts;
        long lockedUntil;
        User user;
        try (Cursor c = db.query(DbHelper.T_USERS, null,
                DbHelper.C_EMAIL + " = ?", new String[]{normalizeEmail(email)},
                null, null, null, "1")) {
            if (!c.moveToFirst()) {
                return AuthResult.invalid();
            }
            id = c.getLong(c.getColumnIndexOrThrow(DbHelper.C_ID));
            salt = c.getString(c.getColumnIndexOrThrow(DbHelper.C_SALT));
            storedHash = c.getString(c.getColumnIndexOrThrow(DbHelper.C_PASSWORD_HASH));
            attempts = c.getInt(c.getColumnIndexOrThrow(DbHelper.C_FAILED_ATTEMPTS));
            lockedUntil = c.getLong(c.getColumnIndexOrThrow(DbHelper.C_LOCKED_UNTIL));
            user = fromCursor(c);
        }

        if (lockedUntil > now) {
            return AuthResult.locked(lockedUntil); // do not even check the password
        }
        if (PasswordHasher.verify(password, salt, storedHash)) {
            clearFailures(db, id);
            return AuthResult.success(user);
        }
        return recordFailure(db, id, attempts, now);
    }

    /**
     * Returns the security question number (1 to 4) saved for this email, or -1 when the
     * email is not registered. The caller must not show a different screen for -1, or the
     * reset page would reveal which emails exist.
     */
    public int getSecurityQuestion(String email) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_USERS, new String[]{DbHelper.C_SECURITY_QUESTION},
                DbHelper.C_EMAIL + " = ?", new String[]{normalizeEmail(email)},
                null, null, null, "1")) {
            return c.moveToFirst() ? c.getInt(0) : -1;
        }
    }

    /**
     * Sets a new password if the security answer is right. Shares the lockout counter with
     * login, so guessing answers is as limited as guessing passwords.
     */
    public AuthResult resetPassword(String email, String answer, String newPassword) {
        requireValid(Validators.passwordError(newPassword), "password");

        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();

        long id;
        String answerSalt;
        String answerHash;
        int attempts;
        long lockedUntil;
        User user;
        try (Cursor c = db.query(DbHelper.T_USERS, null,
                DbHelper.C_EMAIL + " = ?", new String[]{normalizeEmail(email)},
                null, null, null, "1")) {
            if (!c.moveToFirst()) {
                return AuthResult.invalid();
            }
            id = c.getLong(c.getColumnIndexOrThrow(DbHelper.C_ID));
            answerSalt = c.getString(c.getColumnIndexOrThrow(DbHelper.C_ANSWER_SALT));
            answerHash = c.getString(c.getColumnIndexOrThrow(DbHelper.C_ANSWER_HASH));
            attempts = c.getInt(c.getColumnIndexOrThrow(DbHelper.C_FAILED_ATTEMPTS));
            lockedUntil = c.getLong(c.getColumnIndexOrThrow(DbHelper.C_LOCKED_UNTIL));
            user = fromCursor(c);
        }

        if (lockedUntil > now) {
            return AuthResult.locked(lockedUntil);
        }
        if (!PasswordHasher.verify(Validators.normalizeAnswer(answer), answerSalt, answerHash)) {
            return recordFailure(db, id, attempts, now);
        }

        String salt = PasswordHasher.newSalt();
        ContentValues values = new ContentValues();
        values.put(DbHelper.C_PASSWORD_HASH, PasswordHasher.hash(newPassword, salt));
        values.put(DbHelper.C_SALT, salt);
        values.put(DbHelper.C_FAILED_ATTEMPTS, 0);
        values.put(DbHelper.C_LOCKED_UNTIL, 0L);
        db.update(DbHelper.T_USERS, values, DbHelper.C_ID + " = ?", new String[]{String.valueOf(id)});
        return AuthResult.success(user);
    }

    public User findById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_USERS, null,
                DbHelper.C_ID + " = ?", new String[]{String.valueOf(id)},
                null, null, null, "1")) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private AuthResult recordFailure(SQLiteDatabase db, long id, int previousAttempts, long now) {
        int attempts = previousAttempts + 1;
        ContentValues values = new ContentValues();
        AuthResult result;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            long until = now + LOCKOUT_MS;
            values.put(DbHelper.C_FAILED_ATTEMPTS, 0);
            values.put(DbHelper.C_LOCKED_UNTIL, until);
            result = AuthResult.locked(until);
        } else {
            values.put(DbHelper.C_FAILED_ATTEMPTS, attempts);
            result = AuthResult.invalid();
        }
        db.update(DbHelper.T_USERS, values, DbHelper.C_ID + " = ?", new String[]{String.valueOf(id)});
        return result;
    }

    private void clearFailures(SQLiteDatabase db, long id) {
        ContentValues values = new ContentValues();
        values.put(DbHelper.C_FAILED_ATTEMPTS, 0);
        values.put(DbHelper.C_LOCKED_UNTIL, 0L);
        db.update(DbHelper.T_USERS, values, DbHelper.C_ID + " = ?", new String[]{String.valueOf(id)});
    }

    private static void requireValid(int errorResId, String field) {
        if (errorResId != 0) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static User fromCursor(Cursor c) {
        return new User(
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_ID)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_FULL_NAME)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_EMAIL)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_PHONE)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_BLOOD_GROUP)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_ROLE)));
    }
}
