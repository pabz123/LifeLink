package ug.lifelink.app.alert;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ug.lifelink.app.util.SessionManager;

/**
 * The background job. Android runs it about every 15 minutes (the minimum WorkManager allows)
 * when the phone has a network connection, even when the app is closed. It checks for the user
 * who chose "Keep me signed in"; with nobody remembered, it does nothing.
 */
public class RequestCheckWorker extends Worker {

    private static final String TAG = "RequestCheckWorker";

    public RequestCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SessionManager session = new SessionManager(getApplicationContext());
            if (session.hasRememberedUser()) {
                RequestChecker.run(getApplicationContext(), session.getRememberedUserId(), true);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Alert check failed", e);
        }
        return Result.success(); // the next period tries again
    }
}
