package ug.lifelink.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
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

    private static final String STATE_DATE = "state_date";
    private static final String TAG_DATE_PICKER = "date_picker";
    private static final String KEY_CONFIRM_DELETE = "confirm_delete";
    private static final long NO_DATE = Long.MIN_VALUE;

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

    private final RecordRepository records = RecordRepository.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    private long userId;
    private long recordId;
    private String kind;
    private String[] bloodCodes;
    private String[] statusCodes;
    private long selectedDate = NO_DATE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        userId = getIntent().getLongExtra(EXTRA_USER_ID, -1L);
        recordId = getIntent().getLongExtra(EXTRA_RECORD_ID, -1L);
        kind = getIntent().getStringExtra(EXTRA_KIND);

        BloodRecord existing = null;
        if (recordId >= 0 && userId >= 0) {
            existing = records.get(userId, recordId);
            if (existing == null) {
                Toast.makeText(this, R.string.record_not_found, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            kind = existing.getKind(); // a stored record keeps its own kind
        }
        boolean kindOk = BloodRecord.KIND_DONATION.equals(kind) || BloodRecord.KIND_REQUEST.equals(kind);
        if (userId < 0 || !kindOk) {
            finish(); // opened without the data it needs (should not happen)
            return;
        }

        setContentView(R.layout.activity_record_edit);
        bindViews();
        bloodCodes = getResources().getStringArray(R.array.record_blood_group_codes);
        statusCodes = getResources().getStringArray(R.array.request_status_codes);

        boolean isDonation = BloodRecord.KIND_DONATION.equals(kind);
        boolean isNew = existing == null;
        tvScreenTitle.setText(isDonation
                ? (isNew ? R.string.title_add_donation : R.string.title_edit_donation)
                : (isNew ? R.string.title_add_request : R.string.title_edit_request));
        tvDateLabel.setText(isDonation ? R.string.label_date_donation : R.string.label_date_request);
        groupStatus.setVisibility(isDonation ? View.GONE : View.VISIBLE);
        btnDelete.setVisibility(isNew ? View.GONE : View.VISIBLE);

        if (savedInstanceState != null) {
            selectedDate = savedInstanceState.getLong(STATE_DATE, NO_DATE);
        } else if (existing != null) {
            prefillFrom(existing);
        } else {
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

        try {
            int units = Integer.parseInt(unitsText);
            if (recordId < 0) {
                records.add(userId, kind, bloodGroup, units, facility, status, note, selectedDate, now);
            } else if (!records.update(userId, recordId, bloodGroup, units, facility, status, note,
                    selectedDate, now)) {
                Toast.makeText(this, R.string.record_not_found, Toast.LENGTH_LONG).show();
                return;
            }
        } catch (IllegalArgumentException e) {
            // The repository re-checks the rules. Reaching this means a screen check was missed.
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
            return;
        }
        finishWith(RESULT_SAVED);
    }

    private void deleteRecord() {
        if (records.delete(userId, recordId)) {
            finishWith(RESULT_DELETED);
        } else {
            Toast.makeText(this, R.string.record_not_found, Toast.LENGTH_LONG).show();
        }
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
