package ug.lifelink.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Creates and upgrades the local SQLite database (file: lifelink.db in the app's private
 * storage). Both tables are created now so the schema is stable from M1. The records table
 * is first used by the UI in M3.
 *
 * One shared instance (singleton) avoids several helpers opening the same file at once.
 */
public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "lifelink.db";
    public static final int DB_VERSION = 1;

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
                + C_EMAIL + " TEXT NOT NULL UNIQUE, "
                + C_PHONE + " TEXT NOT NULL, "
                + C_BLOOD_GROUP + " TEXT NOT NULL, "
                + C_ROLE + " TEXT NOT NULL, "
                + C_PASSWORD_HASH + " TEXT NOT NULL, "
                + C_SALT + " TEXT NOT NULL, "
                + C_CREATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + T_RECORDS + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_USER_ID + " INTEGER NOT NULL REFERENCES " + T_USERS + "(" + C_ID + ") ON DELETE CASCADE, "
                + C_KIND + " TEXT NOT NULL, "
                + C_BLOOD_GROUP + " TEXT NOT NULL, "
                + C_UNITS + " INTEGER NOT NULL, "
                + C_FACILITY + " TEXT NOT NULL, "
                + C_STATUS + " TEXT NOT NULL, "
                + C_NOTE + " TEXT, "
                + C_CREATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_records_user ON " + T_RECORDS + "(" + C_USER_ID + ", " + C_CREATED_AT + " DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first release, so there is nothing to migrate yet.
        // A later schema change must add real ALTER TABLE steps here instead of dropping data.
    }
}
