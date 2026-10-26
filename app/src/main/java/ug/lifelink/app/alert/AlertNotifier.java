package ug.lifelink.app.alert;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import ug.lifelink.app.R;
import ug.lifelink.app.model.UrgentRequest;
import ug.lifelink.app.model.Urgency;

/**
 * Shows the "urgent request near you" notification. The text holds the hospital, the blood group
 * and the distance, and nothing else about the donor. Tapping it opens the app, which then
 * shows the request on Home (the app's screens are not exported, so the launcher intent is used).
 */
public final class AlertNotifier {

    public static final String CHANNEL_ID = "urgent_requests";

    private AlertNotifier() {
    }

    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** @return true when the notification was posted */
    public static boolean notify(Context context, UrgentRequest request) {
        if (!canNotify(context)) {
            return false;
        }
        createChannel(context);
        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (open == null) {
            return false;
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int titleRes = Urgency.CRITICAL.equals(request.getUrgency())
                ? R.string.alert_title_critical : R.string.alert_title_urgent;
        String text = context.getString(R.string.alert_text, request.getBloodGroup(),
                request.getFacility(), request.getDistanceKm());

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lifelink_mono)
                .setContentTitle(context.getString(titleRes))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(tap)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(request.getRemoteId().hashCode(), b.build());
            return true;
        } catch (SecurityException e) {
            return false; // permission revoked between the check and the call
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.alert_channel_description));
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
