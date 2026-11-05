package ug.lifelink.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which requests a donor has already been notified about. Every statement filters by user_id,
 * so one account on the phone never affects another's alerts. Callers run it OFF the main thread.
 */
public final class AlertLog {

    private final DbHelper helper;

    public AlertLog(Context context) {
        this.helper = DbHelper.getInstance(context);
    }

    public Set<String> alertedIds(long userId) {
        Set<String> ids = new HashSet<>();
        try (Cursor c = helper.getReadableDatabase().query(DbHelper.T_ALERT_LOG,
                new String[]{DbHelper.C_REMOTE_ID}, DbHelper.C_USER_ID + " = ?",
                new String[]{String.valueOf(userId)}, null, null, null)) {
            while (c.moveToNext()) {
                ids.add(c.getString(0));
            }
        }
        return ids;
    }

    public void markAlerted(long userId, List<String> remoteIds, long atMillis) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (String id : remoteIds) {
                ContentValues v = new ContentValues();
                v.put(DbHelper.C_USER_ID, userId);
                v.put(DbHelper.C_REMOTE_ID, id);
                v.put(DbHelper.C_ALERTED_AT, atMillis);
                db.insertWithOnConflict(DbHelper.T_ALERT_LOG, null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Forgets this user's alert history (Demo mode, so the same test can be shown again). */
    public void clear(long userId) {
        helper.getWritableDatabase().delete(DbHelper.T_ALERT_LOG,
                DbHelper.C_USER_ID + " = ?", new String[]{String.valueOf(userId)});
    }
}
