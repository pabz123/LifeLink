package ug.lifelink.app.pledge;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

/**
 * Background job for the shared copy of pledges. It runs only with a network connection (the
 * scheduler sets that condition), so a pledge saved offline is sent when the phone is online
 * again. Two modes: send what is out of date, or withdraw the shared copy of a deleted pledge.
 */
public class PledgeSyncWorker extends Worker {

    static final String KEY_USER_ID = "user_id";
    static final String KEY_FACILITY = "facility";
    static final String KEY_REMOTE_ID = "remote_id";
    private static final String TAG = "PledgeSyncWorker";

    public PledgeSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static Data uploadInput(long userId) {
        return new Data.Builder().putLong(KEY_USER_ID, userId).build();
    }

    static Data withdrawInput(String facility, String remoteId) {
        return new Data.Builder().putString(KEY_FACILITY, facility).putString(KEY_REMOTE_ID, remoteId).build();
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            String remoteId = getInputData().getString(KEY_REMOTE_ID);
            if (remoteId != null) {
                boolean gone = PledgeSync.withdraw(getApplicationContext(),
                        getInputData().getString(KEY_FACILITY), remoteId);
                return gone ? Result.success() : Result.retry();
            }
            PledgeSync.upload(getApplicationContext(), getInputData().getLong(KEY_USER_ID, -1L));
            return Result.success();
        } catch (IOException e) {
            Log.w(TAG, "Pledge sync will retry: " + e.getMessage());
            return Result.retry(); // WorkManager waits, with growing delays, and tries again
        } catch (RuntimeException e) {
            Log.e(TAG, "Pledge sync failed", e);
            return Result.failure();
        }
    }
}
