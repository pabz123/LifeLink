package ug.lifelink.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ug.lifelink.app.data.AppData;
import ug.lifelink.app.data.RecordRepository;
import ug.lifelink.app.model.BloodRecord;
import ug.lifelink.app.ui.ConfirmDialogFragment;
import ug.lifelink.app.util.SimpleTextWatcher;
import ug.lifelink.app.util.Validators;

/**
 * Add, edit or delete one record: a donation for a donor, a blood request for hospital staff.
 *
 * Intent extras (all set by our own screens; this Activity is not exported):
 *   EXTRA_USER_ID          the signed-in user, required
 *   EXTRA_RECORD_ID        the record to edit; absent or -1 means "add a new one"
 *   EXTRA_KIND             DONATION or REQUEST, used when adding
 *   EXTRA_DEFAULT_GROUP    blood group to preselect when adding (the donor's own)
 *   EXTRA_PREFILL_FACILITY facility name to preselect when adding
 *
 * Authorisation: every repository call carries the user id, so this screen can only ever read,
 * change or delete the signed-in user's own records.
 *
 * Rotation: text fields and spinners restore themselves because they have IDs. The chosen date
 * is a plain field, so it is saved in onSaveInstanceState(). The date picker and the delete
 * dialog are fragments, which Android recreates by itself.
 */
public class RecordEditActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "ug.lifelink.app.extra.USER_ID";
    public static final String EXTRA_RECORD_ID = "ug.lifelink.app.extra.RECORD_ID";
    public static final String EXTRA_KIND = "ug.lifelink.app.extra.KIND";
    public static final String EXTRA_DEFAULT_GROUP = "ug.lifelink.app.extra.DEFAULT_GROUP";
    public static final String EXTRA_PREFILL_FACILITY = "ug.lifelink.app.extra.PREFILL_FACILITY";

    /** Returned to the caller so it can give the right feedback. */
    public static final String EXTRA_RESULT = "ug.lifelink.app.extra.RESULT";
    public static final String RESULT_SAVED = "SAVED";
    public static final String RESULT_DELETED = "DELETED";

    private static final String TAG = "RecordEditActivity";
    private static final String STATE_DATE = "state_date";
    private static final String TAG_DATE_PICKER = "date_picker";
    private static final String KEY_CONFIRM_DELETE = "confirm_delete";
    private static final long NO_DATE = Long.MIN_VALUE;
    private static final int OUTCOME_OK = 0;
    private static final int OUTCOME_NOT_FOUND = 1;
    private static final int OUTCOME_ERROR = 2;

    private TextView tvScreenTitle;
    private TextInputLayout layoutFacility;
    private TextInputEditText txtFacility;
    private Spinner spinRecordBlood;
    private TextView tvBloodError;
    private TextInputLayout layoutUnits;
    private TextInputEditText txtUnits;
    private View groupStatus;
    private Spinner spinStatus;
    private TextView tvDateLabel;
    private TextView tvDateValue;
    private TextView tvDateError;
    private TextInputLayout layoutNote;
    private TextInputEditText txtNote;
    private Button btnSave;
    private Button btnDelete;

    private RecordRepository records;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    private long userId;
    private long recordId;
    private String kind;
    private String[] bloodCodes;
    private String[] statusCodes;
    private long selectedDate = NO_DATE;
    /** False while an existing record is still being read from the database. */
    private boolean ready;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        userId = getIntent().getLongExtra(EXTRA_USER_ID, -1L);
        recordId = getIntent().getLongExtra(EXTRA_RECORD_ID, -1L);
        kind = getIntent().getStringExtra(EXTRA_KIND);

        boolean kindOk = BloodRecord.KIND_DONATION.equals(kind) || BloodRecord.KIND_REQUEST.equals(kind);
        if (userId < 0 || !kindOk) {
            finish(); // opened without the data it needs (should not happen)
            return;
        }

        records = AppData.records(this);
        setContentView(R.layout.activity_record_edit);
        bindViews();
        bloodCodes = getResources().getStringArray(R.array.record_blood_group_codes);
        statusCodes = getResources().getStringArray(R.array.request_status_codes);

        boolean isDonation = BloodRecord.KIND_DONATION.equals(kind);
        final boolean isNew = recordId < 0;
        tvScreenTitle.setText(isDonation
                ? (isNew ? R.string.title_add_donation : R.string.title_edit_donation)
                : (isNew ? R.string.title_add_request : R.string.title_edit_request));
        tvDateLabel.setText(isDonation ? R.string.label_date_donation : R.string.label_date_request);
        groupStatus.setVisibility(isDonation ? View.GONE : View.VISIBLE);
        btnDelete.setVisibility(isNew ? View.GONE : View.VISIBLE);

        if (savedInstanceState != null) {
            selectedDate = savedInstanceState.getLong(STATE_DATE, NO_DATE);
        } else if (isNew) {
            prefillNew();
        }
        renderDate();

        setUpListeners();

        // After a rotation the date picker fragment is recreated without our listener.
        @SuppressWarnings("unchecked")
        MaterialDatePicker<Long> picker =
                (MaterialDatePicker<Long>) getSupportFragmentManager().findFragmentByTag(TAG_DATE_PICKER);
        if (picker != null) {
            picker.addOnPositiveButtonClickListener(this::onDatePicked);
        }

        getSupportFragmentManager().setFragmentResultListener(KEY_CONFIRM_DELETE, this,
                (requestKey, result) -> deleteRecord());

        if (isNew) {
            ready = true;
        } else {
            // Editing: read the stored record off the main thread. The form is shown at once and
            // filled in a moment later. After a rotation the fields restore themselves, so the
            // record is only used to confirm that it still exists.
            loadExisting(savedInstanceState == null);
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private void loadExisting(final boolean prefill) {
        worker.execute(() -> {
            BloodRecord found = null;
            try {
                found = records.get(userId, recordId);
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not read the record", e);
            }
            final BloodRecord existing = found;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (existing == null) {
                    Toast.makeText(this, R.string.record_not_found, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                kind = existing.getKind(); // a stored record keeps its own kind
                if (prefill) {
                    prefillFrom(existing);
                    renderDate();
                }
                ready = true;
            });
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_DATE, selectedDate);
    }

    // ------------------------------------------------------------------ setup

    private void bindViews() {
        tvScreenTitle = findViewById(R.id.tvScreenTitle);
        layoutFacility = findViewById(R.id.layoutFacility);
        txtFacility = findViewById(R.id.txtFacility);
        spinRecordBlood = findViewById(R.id.spinRecordBlood);
        tvBloodError = findViewById(R.id.tvBloodError);
        layoutUnits = findViewById(R.id.layoutUnits);
        txtUnits = findViewById(R.id.txtUnits);
        groupStatus = findViewById(R.id.groupStatus);
        spinStatus = findViewById(R.id.spinStatus);
        tvDateLabel = findViewById(R.id.tvDateLabel);
        tvDateValue = findViewById(R.id.tvDateValue);
        tvDateError = findViewById(R.id.tvDateError);
        layoutNote = findViewById(R.id.layoutNote);
        txtNote = findViewById(R.id.txtNote);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
    }

    private void prefillNew() {
        String facility = getIntent().getStringExtra(EXTRA_PREFILL_FACILITY);
        if (facility != null) {
            txtFacility.setText(facility);
        }
        selectBloodGroup(getIntent().getStringExtra(EXTRA_DEFAULT_GROUP));
        txtUnits.setText("1");
        selectedDate = System.currentTimeMillis();
    }

    private void prefillFrom(BloodRecord record) {
        txtFacility.setText(record.getFacility());
        selectBloodGroup(record.getBloodGroup());
        txtUnits.setText(String.valueOf(record.getUnits()));
        txtNote.setText(record.getNote());
        for (int i = 0; i < statusCodes.length; i++) {
            if (statusCodes[i].equals(record.getStatus())) {
                spinStatus.setSelection(i);
            }
        }
        selectedDate = record.getCreatedAt();
    }

    private void selectBloodGroup(String code) {
        if (code == null) {
            return;
        }
        for (int i = 1; i < bloodCodes.length; i++) { // position 0 is the prompt
            if (bloodCodes[i].equals(code)) {
                spinRecordBlood.setSelection(i);
                return;
            }
        }
    }

    private void setUpListeners() {
        ((ImageButton) findViewById(R.id.btnBack)).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> attemptSave());
        btnDelete.setOnClickListener(v -> ConfirmDialogFragment.newConfirmation(
                KEY_CONFIRM_DELETE, R.string.delete_confirm_title, R.string.delete_confirm_message,
                R.string.action_delete).show(getSupportFragmentManager(), ConfirmDialogFragment.TAG));
        findViewById(R.id.btnChooseDate).setOnClickListener(v -> showDatePicker());

        clearErrorOnEdit(txtFacility, layoutFacility);
        clearErrorOnEdit(txtUnits, layoutUnits);
        clearErrorOnEdit(txtNote, layoutNote);

        spinRecordBlood.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    tvBloodError.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // ------------------------------------------------------------------ date

    private void showDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.action_choose_date)
                .setSelection(selectedDate != NO_DATE ? selectedDate : MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(this::onDatePicked);
        picker.show(getSupportFragmentManager(), TAG_DATE_PICKER);
    }

    private void onDatePicked(Long selection) {
        if (selection == null) {
            return;
        }
        // The picker returns midnight UTC of the chosen day; never store a moment after "now".
        selectedDate = Math.min(selection, System.currentTimeMillis());
        tvDateError.setVisibility(View.GONE);
        renderDate();
    }

    private void renderDate() {
        tvDateValue.setText(selectedDate == NO_DATE
                ? getString(R.string.error_date_required)
                : dateFormat.format(new Date(selectedDate)));
    }

    // ------------------------------------------------------------------ save and delete

    private void attemptSave() {
        if (!ready) {
            return; // the stored record is still loading
        }
        final String facility = textOf(txtFacility).trim();
        final String unitsText = textOf(txtUnits).trim();
        final String note = textOf(txtNote).trim();
        final int bloodPosition = spinRecordBlood.getSelectedItemPosition();
        final String bloodGroup = bloodPosition >= 0 && bloodPosition < bloodCodes.length
                ? bloodCodes[bloodPosition] : "";
        final boolean isDonation = BloodRecord.KIND_DONATION.equals(kind);
        final int statusPosition = spinStatus.getSelectedItemPosition();
        final String status = isDonation ? BloodRecord.STATUS_COMPLETED
                : (statusPosition >= 0 && statusPosition < statusCodes.length
                ? statusCodes[statusPosition] : "");
        final long now = System.currentTimeMillis();

        // Check every field and show every problem at once, then focus the first one.
        View firstInvalid = null;

        int facilityError = Validators.facilityError(facility);
        if (facilityError != 0) {
            layoutFacility.setError(getString(facilityError));
            firstInvalid = txtFacility;
        }

        if (Validators.recordBloodGroupError(bloodGroup) != 0) {
            tvBloodError.setVisibility(View.VISIBLE);
            if (firstInvalid == null) {
                firstInvalid = spinRecordBlood;
            }
        }

        int unitsError = Validators.unitsError(unitsText);
        if (unitsError != 0) {
            layoutUnits.setError(getString(unitsError));
            if (firstInvalid == null) {
                firstInvalid = txtUnits;
            }
        }

        int dateError = selectedDate == NO_DATE ? R.string.error_date_required
                : Validators.dateError(selectedDate, now);
        if (dateError != 0) {
            tvDateError.setText(dateError);
            tvDateError.setVisibility(View.VISIBLE);
            if (firstInvalid == null) {
                firstInvalid = findViewById(R.id.btnChooseDate);
            }
        }

        int noteError = Validators.noteError(note);
        if (noteError != 0) {
            layoutNote.setError(getString(noteError));
            if (firstInvalid == null) {
                firstInvalid = txtNote;
            }
        }

        if (firstInvalid != null) {
            firstInvalid.requestFocus();
            return;
        }

        final int units = Integer.parseInt(unitsText);
        final long date = selectedDate;
        setBusy(true);
        worker.execute(() -> {
            int outcome = OUTCOME_OK;
            try {
                if (recordId < 0) {
                    records.add(userId, kind, bloodGroup, units, facility, status, note, date, now);
                } else if (!records.update(userId, recordId, bloodGroup, units, facility, status,
                        note, date, now)) {
                    outcome = OUTCOME_NOT_FOUND;
                }
            } catch (IllegalArgumentException e) {
                // The repository re-checks the rules. Reaching this means a screen check was missed.
                Log.w(TAG, "Repository rejected a value the screen accepted", e);
                outcome = OUTCOME_ERROR;
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not save the record", e);
                outcome = OUTCOME_ERROR;
            }
            final int result = outcome;
            runOnUiThread(() -> onWriteFinished(result, RESULT_SAVED));
        });
    }

    private void deleteRecord() {
        if (!ready) {
            return;
        }
        setBusy(true);
        worker.execute(() -> {
            int outcome = OUTCOME_OK;
            try {
                if (!records.delete(userId, recordId)) {
                    outcome = OUTCOME_NOT_FOUND;
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not delete the record", e);
                outcome = OUTCOME_ERROR;
            }
            final int result = outcome;
            runOnUiThread(() -> onWriteFinished(result, RESULT_DELETED));
        });
    }

    /** Back on the main thread after a save or delete. */
    private void onWriteFinished(int outcome, String resultIfOk) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        setBusy(false);
        if (outcome == OUTCOME_OK) {
            finishWith(resultIfOk);
        } else if (outcome == OUTCOME_NOT_FOUND) {
            Toast.makeText(this, R.string.record_not_found, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
        }
    }

    /** While the database is being written, the buttons are off so a double tap cannot save twice. */
    private void setBusy(boolean busy) {
        btnSave.setEnabled(!busy);
        btnDelete.setEnabled(!busy);
    }

    private void finishWith(String result) {
        Intent data = new Intent().putExtra(EXTRA_RESULT, result);
        setResult(RESULT_OK, data);
        finish();
    }

    // ------------------------------------------------------------------ helpers

    private void clearErrorOnEdit(TextInputEditText field, final TextInputLayout layout) {
        field.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                layout.setError(null);
            }
        });
    }

    private static String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }
}
