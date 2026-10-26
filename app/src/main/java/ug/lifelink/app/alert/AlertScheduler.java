package ug.lifelink.app.alert;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Starts and stops the background check. KEEP means calling schedule() twice adds nothing. */
public final class AlertScheduler {

    private static final String WORK_NAME = "lifelink_request_check";

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

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORK_NAME);
    }
}
