package ug.lifelink.app.data;

import android.content.Context;

/**
 * One place that builds the app's shared data objects, so every screen uses the same record
 * repository on top of the same database.
 */
public final class AppData {

    private static RecordRepository records;

    private AppData() {
    }

    /** The record repository backed by the SQLite database. Safe to call from any thread. */
    public static synchronized RecordRepository records(Context context) {
        if (records == null) {
            records = new RecordRepository(new SqliteRecordStore(context.getApplicationContext()));
        }
        return records;
    }
}
