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

    /** A pledge that stays on this phone. */
    public long add(long userId, String facility, String plannedDate, long nowMillis) {
        return add(userId, facility, plannedDate, nowMillis, null, null);
    }

    /**
     * @param remoteId the key of the shared copy, or null when the donor did not agree to share
     * @param code     the short code the donor shows at the hospital (only with remoteId)
     */
    public long add(long userId, String facility, String plannedDate, long nowMillis,
                    String remoteId, String code) {
        boolean share = remoteId != null;
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_USER_ID, userId);
        v.put(DbHelper.C_FACILITY, facility);
        v.put(DbHelper.C_PLANNED_DATE, plannedDate);
        v.put(DbHelper.C_STATUS, Pledge.STATUS_PLANNED);
        v.put(DbHelper.C_CREATED_AT, nowMillis);
        v.put(DbHelper.C_REMOTE_PLEDGE_ID, remoteId);
        v.put(DbHelper.C_CODE, code);
        v.put(DbHelper.C_SHARED, share ? 1 : 0);
        v.put(DbHelper.C_SYNCED, 0);
        return helper.getWritableDatabase().insertOrThrow(DbHelper.T_PLEDGES, null, v);
    }

    /** Earliest planned day first. */
    public List<Pledge> list(long userId) {
        return query(DbHelper.C_USER_ID + " = ?", new String[]{String.valueOf(userId)});
    }

    /** Shared pledges whose copy in the shared database is out of date. */
    public List<Pledge> pendingSync(long userId) {
        return query(DbHelper.C_USER_ID + " = ? AND " + DbHelper.C_SHARED + " = 1 AND "
                + DbHelper.C_SYNCED + " = 0", new String[]{String.valueOf(userId)});
    }

    /** Shared pledges that are still open, whose hospital answer is worth asking for. */
    public List<Pledge> sharedForStatusCheck(long userId) {
        return query(DbHelper.C_USER_ID + " = ? AND " + DbHelper.C_SHARED + " = 1 AND "
                + DbHelper.C_SYNCED + " = 1", new String[]{String.valueOf(userId)});
    }

    private List<Pledge> query(String where, String[] args) {
        List<Pledge> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(DbHelper.T_PLEDGES, null, where, args,
                null, null, DbHelper.C_PLANNED_DATE + " ASC, " + DbHelper.C_ID + " ASC")) {
            while (c.moveToNext()) {
                out.add(read(c));
            }
        }
        return out;
    }

    /** @return the pledge, or null when this user has no such pledge */
    public Pledge get(long userId, long pledgeId) {
        List<Pledge> found = query(DbHelper.C_USER_ID + " = ? AND " + DbHelper.C_ID + " = ?",
                new String[]{String.valueOf(userId), String.valueOf(pledgeId)});
        return found.isEmpty() ? null : found.get(0);
    }

    /** @return true when a row of this user was changed */
    public boolean setStatus(long userId, long pledgeId, String status) {
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_STATUS, status);
        return update(userId, pledgeId, v);
    }

    /** Cancels a pledge. A shared one is marked out of date so the cancellation is sent. */
    public boolean cancel(long userId, long pledgeId) {
        Pledge p = get(userId, pledgeId);
        if (p == null) {
            return false;
        }
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_STATUS, Pledge.STATUS_CANCELLED);
        if (p.isShared()) {
            v.put(DbHelper.C_SYNCED, 0);
        }
        return update(userId, pledgeId, v);
    }

    public boolean markSynced(long userId, long pledgeId) {
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_SYNCED, 1);
        return update(userId, pledgeId, v);
    }

    /** What the hospital answered ("CONFIRMED" and so on), or null for no answer yet. */
    public boolean setRemoteStatus(long userId, long pledgeId, String remoteStatus) {
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_REMOTE_STATUS, remoteStatus);
        return update(userId, pledgeId, v);
    }

    private boolean update(long userId, long pledgeId, ContentValues v) {
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
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_CREATED_AT)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_REMOTE_PLEDGE_ID)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_CODE)),
                c.getInt(c.getColumnIndexOrThrow(DbHelper.C_SHARED)) == 1,
                c.getInt(c.getColumnIndexOrThrow(DbHelper.C_SYNCED)) == 1,
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_REMOTE_STATUS)));
    }
}
