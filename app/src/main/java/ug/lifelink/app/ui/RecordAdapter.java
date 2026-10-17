package ug.lifelink.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ug.lifelink.app.R;
import ug.lifelink.app.model.BloodRecord;

/**
 * The adapter is the bridge between the data (a List of BloodRecord) and the ListView.
 * The ListView asks getView() for one row at a time, and passes back old rows that
 * scrolled off screen (convertView) so they can be reused instead of re-inflated.
 * The ViewHolder remembers each row's child views, so findViewById runs once per row view
 * instead of on every scroll.
 */
public class RecordAdapter extends ArrayAdapter<BloodRecord> {

    private final LayoutInflater inflater;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    public RecordAdapter(@NonNull Context context, @NonNull List<BloodRecord> records) {
        super(context, 0, records);
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View row = convertView;
        ViewHolder holder;
        if (row == null) {
            row = inflater.inflate(R.layout.item_record, parent, false);
            holder = new ViewHolder(row);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        BloodRecord record = getItem(position);
        if (record != null) {
            holder.tvGroup.setText(record.getBloodGroup());
            holder.tvTitle.setText(record.getFacility());

            String units = getContext().getResources()
                    .getQuantityString(R.plurals.record_units, record.getUnits(), record.getUnits());
            String date = dateFormat.format(new Date(record.getCreatedAt()));
            holder.tvSubtitle.setText(getContext().getString(R.string.record_subtitle, units, date));

            bindStatus(holder.tvStatus, record.getStatus());
        }
        return row;
    }

    /** The status is always shown as words; colour only reinforces it. */
    private void bindStatus(TextView view, String status) {
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
        view.setTextColor(ContextCompat.getColor(getContext(), colorRes));
    }

    private static class ViewHolder {
        final TextView tvGroup;
        final TextView tvTitle;
        final TextView tvSubtitle;
        final TextView tvStatus;

        ViewHolder(View row) {
            tvGroup = row.findViewById(R.id.tvGroup);
            tvTitle = row.findViewById(R.id.tvTitle);
            tvSubtitle = row.findViewById(R.id.tvSubtitle);
            tvStatus = row.findViewById(R.id.tvStatus);
        }
    }
}
