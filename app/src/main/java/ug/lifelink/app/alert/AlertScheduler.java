package ug.lifelink.app.alert;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Starts and stops the background check. KEEP means calling schedule() twice adds nothing. */
public final class AlertScheduler {

    private static final String WORK_NAME = "lifelink_request_check";
    private static final String ONLINE_WORK_NAME = "lifelink_check_when_online";

    private AlertScheduler() {
    }

    public static void schedule(Context context) {
        Constraints needsNetwork = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                RequestCheckWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(needsNetwork)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /**
     * One check as soon as the phone has a connection again (Android waits for the network, even
     * with the app closed). KEEP means many offline attempts queue only one job.
     */
    public static void runWhenOnline(Context context) {
        Constraints needsNetwork = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RequestCheckWorker.class)
                .setConstraints(needsNetwork)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(ONLINE_WORK_NAME, ExistingWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORK_NAME);
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(ONLINE_WORK_NAME);
    }
}
