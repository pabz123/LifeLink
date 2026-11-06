package ug.lifelink.app.pledge;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import ug.lifelink.app.model.Pledge;
import ug.lifelink.app.util.PledgePolicy;

/**
 * One WorkManager job per pledge, started for 08:00 on the planned day. WorkManager keeps the
 * job across restarts and reboots. Timing is approximate (Android may delay it a little), which
 * is fine for a morning reminder. Exact alarms would need an extra permission.
 */
public final class PledgeScheduler {

    private PledgeScheduler() {
    }

    private static String workName(long pledgeId) {
        return "lifelink_pledge_" + pledgeId;
    }

    /** @return false when no reminder was set because 08:00 that day has already passed */
    public static boolean schedule(Context context, Pledge pledge) {
        long delay = PledgePolicy.reminderDelayMillis(
                LocalDate.parse(pledge.getPlannedDate()), LocalDateTime.now());
        if (delay < 0) {
            return false;
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PledgeReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(PledgeReminderWorker.input(pledge.getUserId(), pledge.getId()))
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(workName(pledge.getId()), ExistingWorkPolicy.REPLACE, request);
        return true;
    }

    private static Constraints needsNetwork() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /**
     * Send the shared pledges that are out of date, as soon as the phone is online (at once when
     * it already is). APPEND_OR_REPLACE: a pledge added while a run is in progress is not missed.
     */
    public static void syncNow(Context context, long userId) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PledgeSyncWorker.class)
                .setConstraints(needsNetwork())
                .setInputData(PledgeSyncWorker.uploadInput(userId))
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                "lifelink_pledge_sync_" + userId, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    /** Remove the shared copy of a deleted pledge, when online. */
    public static void withdrawLater(Context context, Pledge pledge) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PledgeSyncWorker.class)
                .setConstraints(needsNetwork())
                .setInputData(PledgeSyncWorker.withdrawInput(pledge.getFacility(), pledge.getRemoteId()))
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                "lifelink_pledge_withdraw_" + pledge.getRemoteId(), ExistingWorkPolicy.REPLACE, request);
    }

    public static void cancel(Context context, long pledgeId) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(workName(pledgeId));
    }
}
