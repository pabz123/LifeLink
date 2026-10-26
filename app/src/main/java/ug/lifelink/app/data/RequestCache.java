package ug.lifelink.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import ug.lifelink.app.model.UrgentRequest;

/**
 * The phone's copy of the shared request list. After every successful download the whole list
 * is replaced, so the cache is always exactly the last good answer from the server. When the
 * phone is offline, the app shows this copy together with when it was saved.
 *
 * This table holds public hospital requests only. It contains nothing about any user, so it is
 * shared by everyone who signs in on the phone, and it is not filtered by user id.
 *
 * These methods touch the disk, so callers must run them OFF the main thread.
 */
public final class RequestCache {

    private final DbHelper helper;

    public RequestCache(Context context) {
        this.helper = DbHelper.getInstance(context);
    }

    /** Replaces the whole cache with this list, all or nothing (one transaction). */
    public void replaceAll(List<UrgentRequest> requests, long fetchedAt) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(DbHelper.T_REQUEST_CACHE, null, null);
            for (UrgentRequest request : requests) {
                db.insertOrThrow(DbHelper.T_REQUEST_CACHE, null, toValues(request, fetchedAt));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Adds or replaces one request. Used by Demo mode to add a test request. */
    public void upsert(UrgentRequest request, long fetchedAt) {
        helper.getWritableDatabase().insertWithOnConflict(DbHelper.T_REQUEST_CACHE, null,
                toValues(request, fetchedAt), SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Newest first. Distances are not stored; they depend on where the donor is. */
    public List<UrgentRequest> listAll() {
        List<UrgentRequest> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(DbHelper.T_REQUEST_CACHE, null, null, null,
                null, null, DbHelper.C_POSTED_AT + " DESC")) {
            while (c.moveToNext()) {
                list.add(fromCursor(c));
            }
        }
        return list;
    }

    /** When the list was last downloaded successfully, or 0 when it never was. */
    public long lastFetchedAt() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT MAX(" + DbHelper.C_FETCHED_AT + ") FROM "
                + DbHelper.T_REQUEST_CACHE, null)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : 0L;
        }
    }

    private static ContentValues toValues(UrgentRequest r, long fetchedAt) {
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_REMOTE_ID, r.getRemoteId());
        v.put(DbHelper.C_FACILITY, r.getFacility());
        v.put(DbHelper.C_BLOOD_GROUP, r.getBloodGroup());
        v.put(DbHelper.C_UNITS, r.getUnits());
        v.put(DbHelper.C_URGENCY, r.getUrgency());
        v.put(DbHelper.C_NOTE, r.getNote());
        v.put(DbHelper.C_CONTACT_PHONE, r.getContactPhone());
        if (r.hasLocation()) {
            v.put(DbHelper.C_LAT, r.getLat());
            v.put(DbHelper.C_LNG, r.getLng());
        } else {
            v.putNull(DbHelper.C_LAT);
            v.putNull(DbHelper.C_LNG);
        }
        v.put(DbHelper.C_VERIFIED, r.isVerified() ? 1 : 0);
        v.put(DbHelper.C_POSTED_AT, r.getPostedAt());
        v.put(DbHelper.C_EXPIRES_AT, r.getExpiresAt());
        v.put(DbHelper.C_FETCHED_AT, fetchedAt);
        return v;
    }

    private static UrgentRequest fromCursor(Cursor c) {
        int lat = c.getColumnIndexOrThrow(DbHelper.C_LAT);
        int lng = c.getColumnIndexOrThrow(DbHelper.C_LNG);
        boolean hasPosition = !c.isNull(lat) && !c.isNull(lng);
        return new UrgentRequest(
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_REMOTE_ID)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_FACILITY)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_BLOOD_GROUP)),
                c.getInt(c.getColumnIndexOrThrow(DbHelper.C_UNITS)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_URGENCY)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_NOTE)),
                c.getString(c.getColumnIndexOrThrow(DbHelper.C_CONTACT_PHONE)),
                hasPosition ? c.getDouble(lat) : Double.NaN,
                hasPosition ? c.getDouble(lng) : Double.NaN,
                c.getInt(c.getColumnIndexOrThrow(DbHelper.C_VERIFIED)) == 1,
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_POSTED_AT)),
                c.getLong(c.getColumnIndexOrThrow(DbHelper.C_EXPIRES_AT)),
                Double.NaN);
    }
}
