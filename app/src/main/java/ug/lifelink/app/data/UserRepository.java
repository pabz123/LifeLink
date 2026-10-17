package ug.lifelink.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import ug.lifelink.app.model.User;
import ug.lifelink.app.util.PasswordHasher;

/**
 * All user-related database access lives here, so screens never write SQL.
 * Every query passes user input through "?" placeholders (never string concatenation),
 * which prevents SQL injection.
 *
 * These methods touch the disk and hash passwords, so callers must run them OFF the main thread.
 */
public class UserRepository {

    private final DbHelper helper;

    public UserRepository(Context context) {
        this.helper = DbHelper.getInstance(context);
    }

    public boolean emailExists(String email) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT 1 FROM " + DbHelper.T_USERS + " WHERE " + DbHelper.C_EMAIL + " = ? LIMIT 1",
                new String[]{email})) {
            return c.moveToFirst();
        }
    }

    /**
     * Creates a user with a salted, hashed password.
     *
     * @return the new row id, or -1 if the insert failed (for example a duplicate email)
     */
    public long create(String fullName, String email, String phone, String bloodGroup,
                       String role, String password) {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash(password, salt);

        ContentValues values = new ContentValues();
        values.put(DbHelper.C_FULL_NAME, fullName);
        values.put(DbHelper.C_EMAIL, email);
        values.put(DbHelper.C_PHONE, phone);
        values.put(DbHelper.C_BLOOD_GROUP, bloodGroup);
        values.put(DbHelper.C_ROLE, role);
        values.put(DbHelper.C_PASSWORD_HASH, hash);
        values.put(DbHelper.C_SALT, salt);
        values.put(DbHelper.C_CREATED_AT, System.currentTimeMillis());

        return helper.getWritableDatabase().insert(DbHelper.T_USERS, null, values);
    }

    /**
     * Checks an email and password pair.
     *
     * @return the matching user, or null when the email is unknown or the password is wrong
     */
    public User authenticate(String email, String password) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_USERS, null,
                DbHelper.C_EMAIL + " = ?", new String[]{email},
                null, null, null, "1")) {
            if (!c.moveToFirst()) {
                return null;
            }
            String salt = c.getString(c.getColumnIndexOrThrow(DbHelper.C_SALT));
            String storedHash = c.getString(c.getColumnIndexOrThrow(DbHelper.C_PASSWORD_HASH));
            if (!PasswordHasher.verify(password, salt, storedHash)) {
                return null;
            }
            return fromCursor(c);
        }
    }

    public User findById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_USERS, null,
                DbHelper.C_ID + " = ?", new String[]{String.valueOf(id)},
                null, null, null, "1")) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
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
