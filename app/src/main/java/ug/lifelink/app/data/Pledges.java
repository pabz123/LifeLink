package ug.lifelink.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import ug.lifelink.app.model.Pledge;

/**
 * Storage for pledges. Every statement filters by user_id, so one account on the phone can
 * never read or change another's pledges. Callers run it OFF the main thread.
 */
public final class Pledges {

    private final DbHelper helper;

    public Pledges(Context context) {
        this.helper = DbHelper.getInstance(context);
    }

    public long add(long userId, String facility, String plannedDate, long nowMillis) {
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_USER_ID, userId);
        v.put(DbHelper.C_FACILITY, facility);
        v.put(DbHelper.C_PLANNED_DATE, plannedDate);
        v.put(DbHelper.C_STATUS, Pledge.STATUS_PLANNED);
        v.put(DbHelper.C_CREATED_AT, nowMillis);
        return helper.getWritableDatabase().insertOrThrow(DbHelper.T_PLEDGES, null, v);
    }

    /** Earliest planned day first. */
    public List<Pledge> list(long userId) {
        List<Pledge> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(DbHelper.T_PLEDGES, null,
                DbHelper.C_USER_ID + " = ?", new String[]{String.valueOf(userId)},
                null, null, DbHelper.C_PLANNED_DATE + " ASC, " + DbHelper.C_ID + " ASC")) {
            while (c.moveToNext()) {
                out.add(read(c));
            }
        }
        return out;
    }

    /** @return the pledge, or null when this user has no such pledge */
    public Pledge get(long userId, long pledgeId) {
        try (Cursor c = helper.getReadableDatabase().query(DbHelper.T_PLEDGES, null,
                DbHelper.C_USER_ID + " = ? AND " + DbHelper.C_ID + " = ?",
                new String[]{String.valueOf(userId), String.valueOf(pledgeId)},
                null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** @return true when a row of this user was changed */
    public boolean setStatus(long userId, long pledgeId, String status) {
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_STATUS, status);
        return helper.getWritableDatabase().update(DbHelper.T_PLEDGES, v,
                DbHelper.C_USER_ID + " = ? AND " + DbHelper.C_ID + " = ?",
                new String[]{String.valueOf(userId), String.valueOf(pledgeId)}) == 1;
    }

    public boolean delete(long userId, long pledgeId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(DbHelper.T_PLEDGES, DbHelper.C_USER_ID + " = ? AND " + DbHelper.C_ID + " = ?",
                new String[]{String.valueOf(userId), String.valueOf(pledgeId)}) == 1;
    }

    private static Pledge read(Cursor c) {
        return new Pledge(
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_ID)),
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_USER_ID)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_FACILITY)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_PLANNED_DATE)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_CREATED_AT)));
    }
}
