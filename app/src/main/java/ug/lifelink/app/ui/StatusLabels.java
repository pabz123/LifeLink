package ug.lifelink.app.ui;

import android.content.Context;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import ug.lifelink.app.R;
import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.model.Urgency;

/**
 * Shows a record status as words plus a colour. The words always carry the meaning; the
 * colour only reinforces it, so the app stays usable for colour-blind users.
 */
public final class StatusLabels {

    private StatusLabels() {
    }

    public static void apply(Context context, TextView view, String status) {
        int textRes;
        int colorRes;
        switch (status) {
            case BloodRecord.STATUS_URGENT:
                textRes = R.string.record_status_urgent;
                colorRes = R.color.status_error;
                break;
            case BloodRecord.STATUS_OPEN:
                textRes = R.string.record_status_open;
                colorRes = R.color.status_warning;
                break;
            case BloodRecord.STATUS_FULFILLED:
                textRes = R.string.record_status_fulfilled;
                colorRes = R.color.status_success;
                break;
            case BloodRecord.STATUS_COMPLETED:
            default:
                textRes = R.string.record_status_completed;
                colorRes = R.color.status_success;
                break;
        }
        view.setText(textRes);
        view.setTextColor(ContextCompat.getColor(context, colorRes));
    }

    /** Urgency of a live request: words first (Critical, Urgent, Routine), colour second. */
    public static void applyUrgency(Context context, TextView view, String urgency) {
        int textRes;
        int colorRes;
        if (Urgency.CRITICAL.equals(urgency)) {
            textRes = R.string.urgency_critical;
            colorRes = R.color.status_error;
        } else if (Urgency.URGENT.equals(urgency)) {
            textRes = R.string.urgency_urgent;
            colorRes = R.color.status_warning;
        } else {
            textRes = R.string.urgency_routine;
            colorRes = R.color.status_success;
        }
        view.setText(textRes);
        view.setTextColor(ContextCompat.getColor(context, colorRes));
    }
}
