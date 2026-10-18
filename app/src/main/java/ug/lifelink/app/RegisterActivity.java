package ug.lifelink.app;

import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ug.lifelink.app.data.UserRepository;
import ug.lifelink.app.model.User;
import ug.lifelink.app.util.BloodGroups;
import ug.lifelink.app.util.SimpleTextWatcher;
import ug.lifelink.app.util.Validators;

/**
 * Register screen. Validates every field, then saves the new user to SQLite with a salted
 * password hash and a hashed security answer.
 *
 * Role matters: donors choose a blood group (or "I do not know"), while hospital and blood
 * bank staff have no blood group to give, so that block is hidden for them.
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private TextInputLayout layoutName;
    private TextInputLayout layoutEmail;
    private TextInputLayout layoutPhone;
    private TextInputLayout layoutPassword;
    private TextInputLayout layoutConfirm;
    private TextInputLayout layoutSecurityAnswer;
    private TextInputEditText txtName;
    private TextInputEditText txtEmail;
    private TextInputEditText txtPhone;
    private TextInputEditText txtPassword;
    private TextInputEditText txtConfirm;
    private TextInputEditText txtSecurityAnswer;
    private View groupBloodGroup;
    private Spinner spinBloodGroup;
    private TextView tvBloodGroupError;
    private Spinner spinSecurityQuestion;
    private TextView tvSecurityQuestionError;
    private RadioGroup rgRole;
    private CheckBox chkConsent;
    private TextView tvConsentError;
    private Button btnRegister;
    private TextView tvLoginLink;

    private UserRepository users;
    private String[] bloodGroupCodes;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        bindViews();
        users = new UserRepository(this);
        bloodGroupCodes = getResources().getStringArray(R.array.blood_group_codes);
        setUpListeners();
        updateBloodGroupVisibility();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private void bindViews() {
        layoutName = findViewById(R.id.layoutName);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPhone = findViewById(R.id.layoutPhone);
        layoutPassword = findViewById(R.id.layoutPassword);
        layoutConfirm = findViewById(R.id.layoutConfirm);
        layoutSecurityAnswer = findViewById(R.id.layoutSecurityAnswer);
        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtPhone = findViewById(R.id.txtPhone);
        txtPassword = findViewById(R.id.txtPassword);
        txtConfirm = findViewById(R.id.txtConfirm);
        txtSecurityAnswer = findViewById(R.id.txtSecurityAnswer);
        groupBloodGroup = findViewById(R.id.groupBloodGroup);
        spinBloodGroup = findViewById(R.id.spinBloodGroup);
        tvBloodGroupError = findViewById(R.id.tvBloodGroupError);
        spinSecurityQuestion = findViewById(R.id.spinSecurityQuestion);
        tvSecurityQuestionError = findViewById(R.id.tvSecurityQuestionError);
        rgRole = findViewById(R.id.rgRole);
        chkConsent = findViewById(R.id.chkConsent);
        tvConsentError = findViewById(R.id.tvConsentError);
        btnRegister = findViewById(R.id.btnRegister);
        tvLoginLink = findViewById(R.id.tvLoginLink);
    }

    private void setUpListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());

        txtSecurityAnswer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister();
                return true;
            }
            return false;
        });

        // Back to Login. finish() returns to the Login screen that is still underneath.
        tvLoginLink.setOnClickListener(v -> finish());

        clearErrorOnEdit(txtName, layoutName);
        clearErrorOnEdit(txtEmail, layoutEmail);
        clearErrorOnEdit(txtPhone, layoutPhone);
        clearErrorOnEdit(txtPassword, layoutPassword);
        clearErrorOnEdit(txtConfirm, layoutConfirm);
        clearErrorOnEdit(txtSecurityAnswer, layoutSecurityAnswer);

        // Hospital staff have no blood group, so hide that block for them.
        rgRole.setOnCheckedChangeListener((group, checkedId) -> updateBloodGroupVisibility());

        spinBloodGroup.setOnItemSelectedListener(new ClearOnSelect(tvBloodGroupError));
        spinSecurityQuestion.setOnItemSelectedListener(new ClearOnSelect(tvSecurityQuestionError));

        chkConsent.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                tvConsentError.setVisibility(View.GONE);
            }
        });
    }

    private boolean isHospitalSelected() {
        return rgRole.getCheckedRadioButtonId() == R.id.rbHospital;
    }

    private void updateBloodGroupVisibility() {
        groupBloodGroup.setVisibility(isHospitalSelected() ? View.GONE : View.VISIBLE);
        tvBloodGroupError.setVisibility(View.GONE);
    }

    private void attemptRegister() {
        final String name = textOf(txtName).trim();
        final String email = textOf(txtEmail).trim().toLowerCase(Locale.ROOT);
        final String rawPhone = textOf(txtPhone);
        final String password = textOf(txtPassword);
        final String confirm = textOf(txtConfirm);
        final String answer = textOf(txtSecurityAnswer);
        final int questionIndex = spinSecurityQuestion.getSelectedItemPosition();
        final String role = isHospitalSelected() ? User.ROLE_HOSPITAL : User.ROLE_DONOR;

        int bloodPosition = spinBloodGroup.getSelectedItemPosition();
        final String bloodGroup = isHospitalSelected() ? BloodGroups.NOT_APPLICABLE
                : (bloodPosition >= 0 && bloodPosition < bloodGroupCodes.length
                ? bloodGroupCodes[bloodPosition] : "");

        // Validate every field and show every problem at once, then focus the first one.
        View firstInvalid = null;

        int nameError = Validators.nameError(name);
        if (nameError != 0) {
            layoutName.setError(getString(nameError));
            firstInvalid = txtName;
        }

        int emailError = Validators.emailError(email);
        if (emailError != 0) {
            layoutEmail.setError(getString(emailError));
            if (firstInvalid == null) {
                firstInvalid = txtEmail;
            }
        }

        int phoneError = Validators.phoneError(rawPhone);
        if (phoneError != 0) {
            layoutPhone.setError(getString(phoneError));
            if (firstInvalid == null) {
                firstInvalid = txtPhone;
            }
        }

        if (Validators.bloodGroupError(role, bloodGroup) != 0) {
            tvBloodGroupError.setVisibility(View.VISIBLE);
            if (firstInvalid == null) {
                firstInvalid = spinBloodGroup;
            }
        }

        int passwordError = Validators.passwordError(password);
        if (passwordError != 0) {
            layoutPassword.setError(getString(passwordError));
            if (firstInvalid == null) {
                firstInvalid = txtPassword;
            }
        }

        int confirmError = Validators.confirmError(password, confirm);
        if (confirmError != 0) {
            layoutConfirm.setError(getString(confirmError));
            if (firstInvalid == null) {
                firstInvalid = txtConfirm;
            }
        }

        if (Validators.securityQuestionError(questionIndex) != 0) {
            tvSecurityQuestionError.setVisibility(View.VISIBLE);
            if (firstInvalid == null) {
                firstInvalid = spinSecurityQuestion;
            }
        }

        int answerError = Validators.securityAnswerError(answer);
        if (answerError != 0) {
            layoutSecurityAnswer.setError(getString(answerError));
            if (firstInvalid == null) {
                firstInvalid = txtSecurityAnswer;
            }
        }

        if (!chkConsent.isChecked()) {
            tvConsentError.setVisibility(View.VISIBLE);
            if (firstInvalid == null) {
                firstInvalid = chkConsent;
            }
        }

        if (firstInvalid != null) {
            firstInvalid.requestFocus();
            return;
        }

        setBusy(true);
        worker.execute(() -> {
            boolean taken = false;
            boolean failed = false;
            try {
                if (users.emailExists(email)) {
                    taken = true;
                } else {
                    failed = users.create(name, email, rawPhone, bloodGroup, role, password,
                            questionIndex, answer) < 0;
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Registration failed unexpectedly", e);
                failed = true;
            }
            final boolean emailTaken = taken;
            final boolean error = failed;
            runOnUiThread(() -> onRegisterResult(emailTaken, error));
        });
    }

    private void onRegisterResult(boolean emailTaken, boolean error) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        setBusy(false);

        if (emailTaken) {
            layoutEmail.setError(getString(R.string.error_email_taken));
            txtEmail.requestFocus();
            return;
        }
        if (error) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.account_created, Toast.LENGTH_LONG).show();
        finish(); // back to Login
    }

    private void setBusy(boolean busy) {
        btnRegister.setEnabled(!busy);
        btnRegister.setText(busy ? R.string.creating_account : R.string.action_register);
    }

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

    /** Hides an error label as soon as a real (non-prompt) spinner item is chosen. */
    private static class ClearOnSelect implements AdapterView.OnItemSelectedListener {
        private final TextView errorView;

        ClearOnSelect(TextView errorView) {
            this.errorView = errorView;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (position > 0) {
                errorView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}
