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

    public static final String DB_NAME = "lifelink.db";
    public static final int DB_VERSION = 2;

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

    // records
    public static final String T_RECORDS = "records";
    public static final String C_USER_ID = "user_id";
    public static final String C_KIND = "kind";
    public static final String C_UNITS = "units";
    public static final String C_FACILITY = "facility";
    public static final String C_STATUS = "status";
    public static final String C_NOTE = "note";

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
                + C_CREATED_AT + " INTEGER NOT NULL)");

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Versions 1 and 2 only ever held test accounts made during development, so the
        // tables are simply rebuilt. Once real data exists (after Milestone 3), a schema
        // change must instead use ALTER TABLE steps so nobody loses their records.
        db.execSQL("DROP TABLE IF EXISTS " + T_RECORDS);
        db.execSQL("DROP TABLE IF EXISTS " + T_USERS);
        onCreate(db);
    }
}
