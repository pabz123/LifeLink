package ug.lifelink.app.pledge;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ug.lifelink.app.data.Pledges;
import ug.lifelink.app.model.Pledge;

/**
 * Runs on the planned day. It reads the pledge again, so a cancelled, completed or deleted
 * pledge never produces a reminder even if the job was not cancelled in time.
 */
public class PledgeReminderWorker extends Worker {

    static final String KEY_USER_ID = "user_id";
    static final String KEY_PLEDGE_ID = "pledge_id";
    private static final String TAG = "PledgeReminderWorker";

    public PledgeReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static Data input(long userId, long pledgeId) {
        return new Data.Builder().putLong(KEY_USER_ID, userId).putLong(KEY_PLEDGE_ID, pledgeId).build();
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            long userId = getInputData().getLong(KEY_USER_ID, -1L);
            long pledgeId = getInputData().getLong(KEY_PLEDGE_ID, -1L);
            Pledge pledge = new Pledges(getApplicationContext()).get(userId, pledgeId);
            if (pledge != null && Pledge.STATUS_PLANNED.equals(pledge.getStatus())) {
                PledgeNotifier.notify(getApplicationContext(), pledge);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Pledge reminder failed", e);
        }
        return Result.success();
    }
}
