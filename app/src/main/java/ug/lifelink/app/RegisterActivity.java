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
import ug.lifelink.app.util.SimpleTextWatcher;
import ug.lifelink.app.util.Validators;

/**
 * Register screen. Validates every field, then saves the new user to SQLite with a
 * salted password hash. Choice controls: Spinner (blood group), RadioGroup (role),
 * CheckBox (consent).
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private TextInputLayout layoutName;
    private TextInputLayout layoutEmail;
    private TextInputLayout layoutPhone;
    private TextInputLayout layoutPassword;
    private TextInputLayout layoutConfirm;
    private TextInputEditText txtName;
    private TextInputEditText txtEmail;
    private TextInputEditText txtPhone;
    private TextInputEditText txtPassword;
    private TextInputEditText txtConfirm;
    private Spinner spinBloodGroup;
    private TextView tvBloodGroupError;
    private RadioGroup rgRole;
    private CheckBox chkConsent;
    private TextView tvConsentError;
    private Button btnRegister;
    private TextView tvLoginLink;

    private UserRepository users;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        bindViews();
        users = new UserRepository(this);
        setUpListeners();
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
        txtName = findViewById(R.id.txtName);
        txtEmail = findViewById(R.id.txtEmail);
        txtPhone = findViewById(R.id.txtPhone);
        txtPassword = findViewById(R.id.txtPassword);
        txtConfirm = findViewById(R.id.txtConfirm);
        spinBloodGroup = findViewById(R.id.spinBloodGroup);
        tvBloodGroupError = findViewById(R.id.tvBloodGroupError);
        rgRole = findViewById(R.id.rgRole);
        chkConsent = findViewById(R.id.chkConsent);
        tvConsentError = findViewById(R.id.tvConsentError);
        btnRegister = findViewById(R.id.btnRegister);
        tvLoginLink = findViewById(R.id.tvLoginLink);
    }

    private void setUpListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());

        txtConfirm.setOnEditorActionListener((v, actionId, event) -> {
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

        spinBloodGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    tvBloodGroupError.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        chkConsent.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                tvConsentError.setVisibility(View.GONE);
            }
        });
    }

    private void attemptRegister() {
        final String name = textOf(txtName).trim();
        final String email = textOf(txtEmail).trim().toLowerCase(Locale.ROOT);
        final String rawPhone = textOf(txtPhone);
        final String password = textOf(txtPassword);
        final String confirm = textOf(txtConfirm);
        final int bloodPosition = spinBloodGroup.getSelectedItemPosition();

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

        if (bloodPosition <= 0) { // position 0 is the "Select blood group" prompt
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

        final String bloodGroup = (String) spinBloodGroup.getSelectedItem();
        final String role = rgRole.getCheckedRadioButtonId() == R.id.rbHospital
                ? User.ROLE_HOSPITAL : User.ROLE_DONOR;
        final String phone = Validators.normalizePhone(rawPhone);

        setBusy(true);
        worker.execute(() -> {
            boolean taken = false;
            boolean failed = false;
            try {
                if (users.emailExists(email)) {
                    taken = true;
                } else {
                    failed = users.create(name, email, phone, bloodGroup, role, password) < 0;
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
}
