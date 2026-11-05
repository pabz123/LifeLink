package ug.lifelink.app.pledge;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;

import java.time.LocalDate;
import java.time.ZoneOffset;

import ug.lifelink.app.R;
import ug.lifelink.app.model.Pledge;

/**
 * "Add to calendar": opens the phone's calendar app with the event filled in. The donor taps
 * Save there, so LifeLink needs no calendar permission and cannot read the calendar.
 */
public final class CalendarLink {

    private CalendarLink() {
    }

    public static Intent insertIntent(Context context, Pledge pledge) {
        // An all-day event is stored as midnight UTC of the day.
        long start = LocalDate.parse(pledge.getPlannedDate())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        return new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 24L * 60 * 60 * 1000)
                .putExtra(CalendarContract.Events.ALL_DAY, true)
                .putExtra(CalendarContract.Events.TITLE, context.getString(R.string.pledge_calendar_title))
                .putExtra(CalendarContract.Events.EVENT_LOCATION, pledge.getFacility())
                .putExtra(CalendarContract.Events.DESCRIPTION, context.getString(R.string.pledge_calendar_description));
    }

    /** @return false when no calendar app is installed */
    public static boolean open(Context context, Pledge pledge) {
        try {
            context.startActivity(insertIntent(context, pledge));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
