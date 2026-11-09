package ug.lifelink.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import ug.lifelink.app.model.BloodRecord;

/**
 * Milestone 3: records stored in the app's SQLite database, so they survive the app being closed,
 * the phone restarting and Android ending the process.
 *
 * Authorisation lives in the SQL itself. Every statement that touches the records table has
 * "user_id = ?" in its WHERE clause, so a record id that belongs to another user matches no
 * row: find() returns null and update() and delete() change nothing. Values are always passed
 * as "?" arguments or ContentValues, never joined into the SQL text (no SQL injection).
 *
 * These methods read and write the disk, so callers must run them OFF the main thread.
 */
public final class SqliteRecordStore implements RecordStore {

    private static final String BY_ID_AND_OWNER =
            DbHelper.C_ID + " = ? AND " + DbHelper.C_USER_ID + " = ?";

    private final DbHelper helper;

    public SqliteRecordStore(Context context) {
        this.helper = DbHelper.getInstance(context);
    }

    @Override
    public List<BloodRecord> listFor(long userId) {
        List<BloodRecord> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_RECORDS, null,
                DbHelper.C_USER_ID + " = ?", new String[]{String.valueOf(userId)},
                null, null, DbHelper.C_CREATED_AT + " DESC, " + DbHelper.C_ID + " DESC")) {
            while (c.moveToNext()) {
                list.add(fromCursor(c));
            }
        }
        return list;
    }

    @Override
    public BloodRecord find(long userId, long recordId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_RECORDS, null, BY_ID_AND_OWNER,
                new String[]{String.valueOf(recordId), String.valueOf(userId)},
                null, null, null, "1")) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    @Override
    public BloodRecord insert(long userId, String kind, String bloodGroup, int units,
                              String facility, String status, String note, long createdAt) {
        ContentValues values = new ContentValues();
        values.put(DbHelper.C_USER_ID, userId);
        values.put(DbHelper.C_KIND, kind);
        putEditableFields(values, bloodGroup, units, facility, status, note, createdAt);
        long id = helper.getWritableDatabase().insertOrThrow(DbHelper.T_RECORDS, null, values);
        return new BloodRecord(id, userId, kind, bloodGroup, units, facility, status, note, createdAt);
    }

    @Override
    public boolean update(long userId, long recordId, String bloodGroup, int units,
                          String facility, String status, String note, long createdAt) {
        // user_id and kind are deliberately not in the values: ownership and kind never change.
        ContentValues values = new ContentValues();
        putEditableFields(values, bloodGroup, units, facility, status, note, createdAt);
        int changed = helper.getWritableDatabase().update(DbHelper.T_RECORDS, values,
                BY_ID_AND_OWNER, new String[]{String.valueOf(recordId), String.valueOf(userId)});
        return changed > 0;
    }

    @Override
    public boolean delete(long userId, long recordId) {
        int removed = helper.getWritableDatabase().delete(DbHelper.T_RECORDS, BY_ID_AND_OWNER,
                new String[]{String.valueOf(recordId), String.valueOf(userId)});
        return removed > 0;
    }

    @Override
    public boolean seedOnce(long userId, List<BloodRecord> sample) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Flip the flag first. Only the call that changes 0 to 1 gets to insert the samples,
            // so two screens racing each other cannot seed twice.
            ContentValues flag = new ContentValues();
            flag.put(DbHelper.C_RECORDS_SEEDED, 1);
            int flipped = db.update(DbHelper.T_USERS, flag,
                    DbHelper.C_ID + " = ? AND " + DbHelper.C_RECORDS_SEEDED + " = 0",
                    new String[]{String.valueOf(userId)});
            if (flipped == 0) {
                return false;
            }
            for (BloodRecord s : sample) {
                insert(userId, s.getKind(), s.getBloodGroup(), s.getUnits(), s.getFacility(),
                        s.getStatus(), s.getNote(), s.getCreatedAt());
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private static void putEditableFields(ContentValues values, String bloodGroup, int units,
                                          String facility, String status, String note,
                                          long createdAt) {
        values.put(DbHelper.C_BLOOD_GROUP, bloodGroup);
        values.put(DbHelper.C_UNITS, units);
        values.put(DbHelper.C_FACILITY, facility);
        values.put(DbHelper.C_STATUS, status);
        values.put(DbHelper.C_NOTE, note);
        values.put(DbHelper.C_CREATED_AT, createdAt);
    }

    private static BloodRecord fromCursor(Cursor c) {
        String note = c.getString(c.getColumnIndexOrThrow(DbHelper.C_NOTE));
        return new BloodRecord(
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_ID)),
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_USER_ID)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_KIND)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_BLOOD_GROUP)),
                c.getInt(c.getColumnIndexOrThrow(DbHelper.C_UNITS)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_FACILITY)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_STATUS)),
                note == null ? "" : note,
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_CREATED_AT)));
    }
}
