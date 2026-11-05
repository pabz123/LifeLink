package ug.lifelink.app.pledge;

import android.content.Context;

import androidx.work.ExistingWorkPolicy;
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

    public static void cancel(Context context, long pledgeId) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(workName(pledgeId));
    }
}
