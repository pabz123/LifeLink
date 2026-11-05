package ug.lifelink.app.pledge;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import ug.lifelink.app.R;
import ug.lifelink.app.alert.AlertNotifier;
import ug.lifelink.app.model.Pledge;

/** Shows "your planned donation is today". The text holds the facility only. */
public final class PledgeNotifier {

    public static final String CHANNEL_ID = "pledge_reminders";

    private PledgeNotifier() {
    }

    /** @return true when the notification was posted */
    public static boolean notify(Context context, Pledge pledge) {
        if (!AlertNotifier.canNotify(context)) {
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
        String text = context.getString(R.string.pledge_reminder_text, pledge.getFacility());
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lifelink_mono)
                .setContentTitle(context.getString(R.string.pledge_reminder_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tap)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(("pledge-" + pledge.getId()).hashCode(), b.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.pledge_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(context.getString(R.string.pledge_channel_description));
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
