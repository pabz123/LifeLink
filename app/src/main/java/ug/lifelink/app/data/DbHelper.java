package ug.lifelink.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Creates and upgrades the local SQLite database (file: lifelink.db in the app's private
 * storage).
 *
 * The schema is the last line of defence for data validation: CHECK constraints make the
 * database itself refuse an impossible role, blood group, status or unit count, even if a
 * bug in the app tried to save one. The screens and UserRepository validate first, so these
 * constraints should never fire in normal use.
 *
 * One shared instance (singleton) avoids several helpers opening the same file at once.
 */
public class DbHelper extends SQLiteOpenHelper {

    public static final String T_ALERT_LOG = "alert_log";
    public static final String C_ALERTED_AT = "alerted_at";

    public static final String DB_NAME = "lifelink.db";
    public static final int DB_VERSION = 5;

    // users
    public static final String T_USERS = "users";
    public static final String C_ID = "id";
    public static final String C_FULL_NAME = "full_name";
    public static final String C_EMAIL = "email";
    public static final String C_PHONE = "phone";
    public static final String C_BLOOD_GROUP = "blood_group";
    public static final String C_ROLE = "role";
    public static final String C_PASSWORD_HASH = "password_hash";
    public static final String C_SALT = "salt";
    public static final String C_SECURITY_QUESTION = "security_question";
    public static final String C_ANSWER_HASH = "answer_hash";
    public static final String C_ANSWER_SALT = "answer_salt";
    public static final String C_FAILED_ATTEMPTS = "failed_attempts";
    public static final String C_LOCKED_UNTIL = "locked_until";
    public static final String C_CREATED_AT = "created_at";
    public static final String C_RECORDS_SEEDED = "records_seeded";

    // Added in version 3. The same text is used by onCreate and by the upgrade step, so a new
    // install and an upgraded install end up with an identical column.
    private static final String RECORDS_SEEDED_DDL = C_RECORDS_SEEDED
            + " INTEGER NOT NULL DEFAULT 0 CHECK (" + C_RECORDS_SEEDED + " IN (0,1))";

    // records
    public static final String T_RECORDS = "records";
    public static final String C_USER_ID = "user_id";
    public static final String C_KIND = "kind";
    public static final String C_UNITS = "units";
    public static final String C_FACILITY = "facility";
    public static final String C_STATUS = "status";
    public static final String C_NOTE = "note";

    // request_cache: the last copy of the shared request list, so the app works offline.
    // It holds public hospital requests only, never anything about a user.
    public static final String T_REQUEST_CACHE = "request_cache";
    public static final String C_REMOTE_ID = "remote_id";
    public static final String C_URGENCY = "urgency";
    public static final String C_CONTACT_PHONE = "contact_phone";
    public static final String C_LAT = "lat";
    public static final String C_LNG = "lng";
    public static final String C_VERIFIED = "verified";
    public static final String C_POSTED_AT = "posted_at";
    public static final String C_EXPIRES_AT = "expires_at";
    public static final String C_FETCHED_AT = "fetched_at";

    private static DbHelper instance;

    public static synchronized DbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // SQLite ignores FOREIGN KEY rules unless this is switched on for each connection.
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_USERS + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_FULL_NAME + " TEXT NOT NULL, "
                + C_EMAIL + " TEXT NOT NULL UNIQUE CHECK (" + C_EMAIL + " = lower(" + C_EMAIL + ")), "
                + C_PHONE + " TEXT NOT NULL, "
                + C_BLOOD_GROUP + " TEXT NOT NULL CHECK (" + C_BLOOD_GROUP
                + " IN ('A+','A-','B+','B-','AB+','AB-','O+','O-','UNKNOWN','NA')), "
                + C_ROLE + " TEXT NOT NULL CHECK (" + C_ROLE + " IN ('DONOR','HOSPITAL')), "
                + C_PASSWORD_HASH + " TEXT NOT NULL, "
                + C_SALT + " TEXT NOT NULL, "
                + C_SECURITY_QUESTION + " INTEGER NOT NULL CHECK (" + C_SECURITY_QUESTION + " BETWEEN 1 AND 4), "
                + C_ANSWER_HASH + " TEXT NOT NULL, "
                + C_ANSWER_SALT + " TEXT NOT NULL, "
                + C_FAILED_ATTEMPTS + " INTEGER NOT NULL DEFAULT 0, "
                + C_LOCKED_UNTIL + " INTEGER NOT NULL DEFAULT 0, "
                + C_CREATED_AT + " INTEGER NOT NULL, "
                + RECORDS_SEEDED_DDL + ")");

        db.execSQL("CREATE TABLE " + T_RECORDS + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_USER_ID + " INTEGER NOT NULL REFERENCES " + T_USERS + "(" + C_ID + ") ON DELETE CASCADE, "
                + C_KIND + " TEXT NOT NULL CHECK (" + C_KIND + " IN ('DONATION','REQUEST')), "
                + C_BLOOD_GROUP + " TEXT NOT NULL CHECK (" + C_BLOOD_GROUP
                + " IN ('A+','A-','B+','B-','AB+','AB-','O+','O-','UNKNOWN')), "
                + C_UNITS + " INTEGER NOT NULL CHECK (" + C_UNITS + " BETWEEN 1 AND 20), "
                + C_FACILITY + " TEXT NOT NULL, "
                + C_STATUS + " TEXT NOT NULL CHECK (" + C_STATUS
                + " IN ('COMPLETED','OPEN','URGENT','FULFILLED')), "
                + C_NOTE + " TEXT, "
                + C_CREATED_AT + " INTEGER NOT NULL)");

        // Every record query filters by user_id, so index it (newest first).
        db.execSQL("CREATE INDEX idx_records_user ON " + T_RECORDS
                + "(" + C_USER_ID + ", " + C_CREATED_AT + " DESC)");

        createRequestCache(db);
        createAlertLog(db);
    }

    /** Used by onCreate and by the version 4 upgrade step, so both give the same table. */
    private void createRequestCache(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_REQUEST_CACHE + " ("
                + C_REMOTE_ID + " TEXT PRIMARY KEY, "
                + C_FACILITY + " TEXT NOT NULL, "
                + C_BLOOD_GROUP + " TEXT NOT NULL CHECK (" + C_BLOOD_GROUP
                + " IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')), "
                + C_UNITS + " INTEGER NOT NULL CHECK (" + C_UNITS + " BETWEEN 1 AND 20), "
                + C_URGENCY + " TEXT NOT NULL CHECK (" + C_URGENCY
                + " IN ('CRITICAL','URGENT','ROUTINE')), "
                + C_NOTE + " TEXT NOT NULL DEFAULT '', "
                + C_CONTACT_PHONE + " TEXT NOT NULL DEFAULT '', "
                + C_LAT + " REAL, "
                + C_LNG + " REAL, "
                + C_VERIFIED + " INTEGER NOT NULL DEFAULT 0 CHECK (" + C_VERIFIED + " IN (0,1)), "
                + C_POSTED_AT + " INTEGER NOT NULL DEFAULT 0, "
                + C_EXPIRES_AT + " INTEGER NOT NULL DEFAULT 0, "
                + C_FETCHED_AT + " INTEGER NOT NULL)");
    }

    /** One row per (user, request) already notified, so the same request never alerts twice. */
    private void createAlertLog(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_ALERT_LOG + " ("
                + C_USER_ID + " INTEGER NOT NULL REFERENCES " + T_USERS + "(" + C_ID + ") ON DELETE CASCADE, "
                + C_REMOTE_ID + " TEXT NOT NULL, "
                + C_ALERTED_AT + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + C_USER_ID + ", " + C_REMOTE_ID + "))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Version 1 only ever held development test accounts, so it is rebuilt from scratch.
            // onCreate builds the newest schema, so no further steps are needed.
            db.execSQL("DROP TABLE IF EXISTS " + T_RECORDS);
            db.execSQL("DROP TABLE IF EXISTS " + T_USERS);
            onCreate(db);
            return;
        }
        // From version 2 on, real accounts may exist, so every change keeps their data.
        if (oldVersion < 3) {
            // Version 3 (Milestone 3): remember that a user has been given the sample records,
            // now that records are stored and no longer start empty after every launch.
            db.execSQL("ALTER TABLE " + T_USERS + " ADD COLUMN " + RECORDS_SEEDED_DDL);
        }
        if (oldVersion < 4) {
            // Version 4 (Milestone 4): the offline copy of the shared request list.
            createRequestCache(db);
        }
        if (oldVersion < 5) {
            // Version 5 (Milestone 4): the list of requests already notified.
            createAlertLog(db);
        }
    }
}
