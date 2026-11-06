package ug.lifelink.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;

import ug.lifelink.app.data.Facilities;
import ug.lifelink.app.data.Pledges;
import ug.lifelink.app.location.LocationStore;
import ug.lifelink.app.model.Pledge;
import ug.lifelink.app.pledge.CalendarLink;
import ug.lifelink.app.pledge.PledgeScheduler;
import ug.lifelink.app.pledge.PledgeSync;
import ug.lifelink.app.util.PledgeCode;
import ug.lifelink.app.util.PledgePolicy;

/**
 * Plan a donation and manage the plans. A pledge stays on this phone. It sets a reminder for
 * 08:00 on the day, and can be added to the phone's calendar. Only the donor can mark it done,
 * so it is never presented as a confirmed donation.
 *
 * Intent extra EXTRA_USER_ID (the signed-in user). This Activity is not exported, and every
 * storage call carries the user id.
 */
public class PledgeActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "ug.lifelink.app.extra.USER_ID";

    private static final String STATE_DATE = "state_pledge_date";
    private static final String TAG_DATE_PICKER = "pledge_date_picker";

    private TextInputLayout layoutFacility;
    private MaterialAutoCompleteTextView txtFacility;
    private TextView tvDate;
    private TextView tvError;
    private TextView tvEmpty;
    private ListView listPledges;
    private CheckBox chkShare;

    private final List<String> facilityNames = new ArrayList<>();
    private final List<Pledge> items = new ArrayList<>();
    private ArrayAdapter<Pledge> adapter;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final DateTimeFormatter shown = DateTimeFormatter.ofPattern("d MMM yyyy");

    private Pledges pledges;
    private long userId;
    private LocalDate selected; // null until the donor chooses

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = getIntent().getLongExtra(EXTRA_USER_ID, -1L);
        if (userId < 0) {
            finish();
            return;
        }
        setContentView(R.layout.activity_pledges);
        pledges = new Pledges(this);

        layoutFacility = findViewById(R.id.layoutPledgeFacility);
        txtFacility = findViewById(R.id.txtPledgeFacility);
        tvDate = findViewById(R.id.tvPledgeDate);
        tvError = findViewById(R.id.tvPledgeError);
        tvEmpty = findViewById(R.id.tvPledgeEmpty);
        listPledges = findViewById(R.id.listPledges);
        chkShare = findViewById(R.id.chkShare);

        ((ImageButton) findViewById(R.id.btnBack)).setOnClickListener(v -> finish());
        ((Button) findViewById(R.id.btnPledgeDate)).setOnClickListener(v -> showDatePicker());
        ((Button) findViewById(R.id.btnPledgeSave)).setOnClickListener(v -> save());

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_DATE)) {
            selected = LocalDate.parse(savedInstanceState.getString(STATE_DATE));
        }
        setUpFacilityList();
        setUpList();
        renderDate();
        reload();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selected != null) {
            outState.putString(STATE_DATE, selected.toString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }

    // ------------------------------------------------------------ form

    private void setUpFacilityList() {
        double[] where = new LocationStore(this).effective();
        facilityNames.addAll(Facilities.namesNear(where != null,
                where == null ? 0 : where[0], where == null ? 0 : where[1]));
        ArrayAdapter<String> facilityAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, facilityNames) {
            @NonNull
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults r = new FilterResults();
                        r.values = new ArrayList<>(facilityNames);
                        r.count = facilityNames.size();
                        return r;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
        txtFacility.setAdapter(facilityAdapter);
    }

    private void showDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();
        long start = selected != null
                ? selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                : MaterialDatePicker.todayInUtcMilliseconds();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.action_choose_date)
                .setSelection(start)
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) {
                return;
            }
            // The picker returns midnight UTC of the chosen day.
            selected = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate();
            tvError.setVisibility(View.GONE);
            renderDate();
        });
        picker.show(getSupportFragmentManager(), TAG_DATE_PICKER);
    }

    private void renderDate() {
        tvDate.setText(selected == null ? getString(R.string.error_date_required) : shown.format(selected));
    }

    private void save() {
        layoutFacility.setError(null);
        String facility = txtFacility.getText() == null ? "" : txtFacility.getText().toString().trim();
        if (!facilityNames.contains(facility)) {
            layoutFacility.setError(getString(R.string.error_facility_pick));
            return;
        }
        int problem = PledgePolicy.checkDate(selected, LocalDate.now());
        if (problem != PledgePolicy.OK) {
            tvError.setText(problem == PledgePolicy.ERR_NO_DATE ? R.string.error_date_required
                    : problem == PledgePolicy.ERR_PAST ? R.string.error_pledge_past
                    : R.string.error_pledge_far);
            tvError.setVisibility(View.VISIBLE);
            return;
        }
        final String date = selected.toString();
        final boolean share = chkShare.isChecked();
        worker.execute(() -> {
            try {
                String remoteId = share ? UUID.randomUUID().toString() : null;
                String code = share ? PledgeCode.generate(new Random()) : null;
                long id = pledges.add(userId, facility, date, System.currentTimeMillis(), remoteId, code);
                Pledge saved = pledges.get(userId, id);
                boolean reminder = saved != null && PledgeScheduler.schedule(this, saved);
                if (share) {
                    PledgeScheduler.syncNow(this, userId);
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, reminder ? R.string.pledge_saved_reminder
                            : R.string.pledge_saved_no_reminder, Toast.LENGTH_LONG).show();
                    txtFacility.setText("", false);
                    chkShare.setChecked(false);
                    selected = null;
                    renderDate();
                    reload();
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show());
            }
        });
    }

    // ------------------------------------------------------------ list

    private void setUpList() {
        adapter = new ArrayAdapter<Pledge>(this, android.R.layout.simple_list_item_2, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = convertView != null ? convertView : getLayoutInflater()
                        .inflate(android.R.layout.simple_list_item_2, parent, false);
                Pledge p = getItem(position);
                LocalDate day = LocalDate.parse(p.getPlannedDate());
                String status = PledgePolicy.effectiveStatus(p.getStatus(), day, LocalDate.now());
                ((TextView) row.findViewById(android.R.id.text1)).setText(p.getFacility());
                String label = Pledge.REMOTE_CONFIRMED.equals(p.getRemoteStatus())
                        ? getString(R.string.pledge_status_confirmed) : statusLabel(status);
                String line = getString(R.string.pledge_row, shown.format(day), label);
                if (p.isShared()) {
                    line += "\n" + shareLine(p);
                }
                ((TextView) row.findViewById(android.R.id.text2)).setText(line);
                return row;
            }
        };
        listPledges.setAdapter(adapter);
        listPledges.setOnItemClickListener((parent, view, position, id) -> showActions(items.get(position)));
    }

    /** What the donor needs to know about the shared copy, including the code to show at the hospital. */
    private String shareLine(Pledge p) {
        String state;
        if (Pledge.REMOTE_CONFIRMED.equals(p.getRemoteStatus())) {
            state = getString(R.string.pledge_remote_confirmed);
        } else if (Pledge.REMOTE_DID_NOT_ATTEND.equals(p.getRemoteStatus())) {
            state = getString(R.string.pledge_remote_missed);
        } else if (!p.isSynced()) {
            state = getString(R.string.pledge_share_waiting);
        } else {
            state = getString(R.string.pledge_share_sent);
        }
        return getString(R.string.pledge_share_line, p.getCode(), state);
    }

    private String statusLabel(String status) {
        switch (status) {
            case Pledge.STATUS_COMPLETED: return getString(R.string.pledge_status_completed);
            case Pledge.STATUS_MISSED: return getString(R.string.pledge_status_missed);
            case Pledge.STATUS_CANCELLED: return getString(R.string.pledge_status_cancelled);
            default: return getString(R.string.pledge_status_planned);
        }
    }

    /** Shows the saved pledges at once, then asks the hospital's answers and shows them if they changed. */
    private void reload() {
        worker.execute(() -> {
            showSaved();
            try {
                if (PledgeSync.refreshAnswers(this, userId) > 0) {
                    showSaved();
                }
            } catch (java.io.IOException | RuntimeException ignored) {
                // offline: the last known answers stay on screen
            }
        });
    }

    private void showSaved() {
        List<Pledge> loaded = pledges.list(userId);
        runOnUiThread(() -> {
            items.clear();
            items.addAll(loaded);
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void showActions(Pledge p) {
        boolean planned = Pledge.STATUS_PLANNED.equals(p.getStatus());
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (planned) {
            labels.add(getString(R.string.pledge_action_calendar));
            actions.add(() -> {
                if (!CalendarLink.open(this, p)) {
                    Toast.makeText(this, R.string.error_no_calendar, Toast.LENGTH_LONG).show();
                }
            });
            labels.add(getString(R.string.pledge_action_donated));
            actions.add(() -> change(p, Pledge.STATUS_COMPLETED));
            labels.add(getString(R.string.pledge_action_cancel));
            actions.add(() -> cancelPledge(p));
        }
        labels.add(getString(R.string.action_delete));
        actions.add(() -> remove(p));
        new AlertDialog.Builder(this)
                .setTitle(p.getFacility())
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    /** Completed or cancelled pledges no longer remind. */
    private void change(Pledge p, String status) {
        worker.execute(() -> {
            pledges.setStatus(userId, p.getId(), status);
            PledgeScheduler.cancel(this, p.getId());
            runOnUiThread(this::reload);
        });
    }

    private void cancelPledge(Pledge p) {
        worker.execute(() -> {
            pledges.cancel(userId, p.getId()); // a shared pledge is marked to send the cancellation
            PledgeScheduler.cancel(this, p.getId());
            if (p.isShared()) {
                PledgeScheduler.syncNow(this, userId);
            }
            runOnUiThread(this::reload);
        });
    }

    /** Deleting a shared pledge also removes the shared copy, so the donor can withdraw it. */
    private void remove(Pledge p) {
        worker.execute(() -> {
            pledges.delete(userId, p.getId());
            PledgeScheduler.cancel(this, p.getId());
            if (p.isShared() && p.getRemoteId() != null) {
                PledgeScheduler.withdrawLater(this, p);
            }
            runOnUiThread(this::reload);
        });
    }
}
