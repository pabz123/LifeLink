package ug.lifelink.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ug.lifelink.app.data.AuthResult;
import ug.lifelink.app.data.UserRepository;
import ug.lifelink.app.model.User;
import ug.lifelink.app.util.SessionManager;
import ug.lifelink.app.util.SimpleTextWatcher;
import ug.lifelink.app.util.Validators;

/**
 * Login screen.
 *
 * Event handling: button click, keyboard "Done" action, link clicks and text changes.
 * Lifecycle: every callback writes a line to Logcat under the tag LL_LIFECYCLE
 * (Milestone 1, Part E). Filter Logcat by that tag while you open the app, press Home,
 * return, rotate and press Back, then explain what you see in the README.
 * Note that SplashActivity starts first and starts this screen, so a fresh launch shows
 * this screen's onCreate only after the splash.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String LIFECYCLE_TAG = "LL_LIFECYCLE";
    private static final String TAG = "LoginActivity";

    private TextInputLayout layoutEmail;
    private TextInputLayout layoutPassword;
    private TextInputEditText txtEmail;
    private TextInputEditText txtPassword;
    private CheckBox chkRemember;
    private Button btnLogin;
    private TextView tvRegisterLink;
    private TextView tvForgotLink;

    private UserRepository users;
    private SessionManager session;

    // Database work and password hashing must not block the main (UI) thread.
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LIFECYCLE_TAG, "Login onCreate, "
                + (savedInstanceState == null ? "fresh start (no saved state)"
                : "re-created with saved state (rotation or process restore)"));

        setContentView(R.layout.activity_login);
        bindViews();
        users = new UserRepository(this);
        session = new SessionManager(this);
        setUpListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(LIFECYCLE_TAG, "Login onStart: screen becoming visible");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(LIFECYCLE_TAG, "Login onResume: in the foreground, ready for input");
    }

    @Override
    protected void onPause() {
        Log.i(LIFECYCLE_TAG, "Login onPause: losing focus (another screen or dialog in front)");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(LIFECYCLE_TAG, "Login onStop: no longer visible");
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(LIFECYCLE_TAG, "Login onRestart: coming back after being stopped");
    }

    @Override
    protected void onDestroy() {
        Log.i(LIFECYCLE_TAG, "Login onDestroy, isChangingConfigurations=" + isChangingConfigurations()
                + " isFinishing=" + isFinishing());
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.i(LIFECYCLE_TAG, "Login onSaveInstanceState: state saved before the activity may be destroyed");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.i(LIFECYCLE_TAG, "Login onRestoreInstanceState: views restored (typed text comes back)");
    }

    // ------------------------------------------------------------------ setup

    private void bindViews() {
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPassword = findViewById(R.id.layoutPassword);
        txtEmail = findViewById(R.id.txtEmail);
        txtPassword = findViewById(R.id.txtPassword);
        chkRemember = findViewById(R.id.chkRemember);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegisterLink = findViewById(R.id.tvRegisterLink);
        tvForgotLink = findViewById(R.id.tvForgotLink);
    }

    private void setUpListeners() {
        // Action 1: tap Log in.
        btnLogin.setOnClickListener(v -> attemptLogin());

        // Action 2: tap Done on the keyboard while in the password field.
        txtPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        // Action 3: open the Register screen.
        tvRegisterLink.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        // Action 4: open Forgot password, carrying over any email already typed.
        tvForgotLink.setOnClickListener(v -> {
            Intent intent = new Intent(this, ForgotPasswordActivity.class);
            String typed = textOf(txtEmail).trim();
            if (!typed.isEmpty()) {
                intent.putExtra(ForgotPasswordActivity.EXTRA_EMAIL, typed);
            }
            startActivity(intent);
        });

        // Action 5: an error disappears as soon as the user starts correcting the field.
        txtEmail.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                layoutEmail.setError(null);
            }
        });
        txtPassword.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                layoutPassword.setError(null);
            }
        });
    }

    // ------------------------------------------------------------------ login

    private void attemptLogin() {
        layoutEmail.setError(null);
        layoutPassword.setError(null);

        final String email = textOf(txtEmail).trim().toLowerCase(Locale.ROOT);
        final String password = textOf(txtPassword);

        // 1. Validate on the main thread: cheap checks, instant feedback.
        int emailError = Validators.emailError(email);
        int passwordError = password.isEmpty() ? R.string.error_password_required : 0;
        if (emailError != 0) {
            layoutEmail.setError(getString(emailError));
        }
        if (passwordError != 0) {
            layoutPassword.setError(getString(passwordError));
        }
        if (emailError != 0) {
            txtEmail.requestFocus();
            return;
        }
        if (passwordError != 0) {
            txtPassword.requestFocus();
            return;
        }

        // 2. Check the credentials on a worker thread, then return to the UI thread.
        setBusy(true);
        final boolean remember = chkRemember.isChecked();
        worker.execute(() -> {
            AuthResult result = null;
            try {
                result = users.authenticate(email, password);
            } catch (RuntimeException e) {
                Log.e(TAG, "Login failed unexpectedly", e);
            }
            final AuthResult outcome = result; // null means an unexpected error
            runOnUiThread(() -> onLoginResult(outcome, remember));
        });
    }

    private void onLoginResult(AuthResult result, boolean remember) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        setBusy(false);

        if (result == null) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
            return;
        }

        switch (result.getStatus()) {
            case LOCKED:
                layoutPassword.setError(getString(R.string.error_locked,
                        result.getMinutesLeft(System.currentTimeMillis())));
                return;
            case INVALID:
                // One message for both "unknown email" and "wrong password", so the screen
                // does not reveal which emails are registered.
                layoutPassword.setError(getString(R.string.login_failed));
                txtPassword.requestFocus();
                return;
            case SUCCESS:
            default:
                break;
        }

        User user = result.getUser();
        if (remember) {
            session.remember(user.getId());
        } else {
            session.clear();
        }
        Toast.makeText(this, getString(R.string.welcome_back, user.getFirstName()),
                Toast.LENGTH_SHORT).show();
        openHome(user);
    }

    private void openHome(User user) {
        Intent intent = new Intent(this, HomeActivity.class);
        // Only the id travels. Home loads the name, role and blood group from the database,
        // so the database stays the single source of truth.
        intent.putExtra(HomeActivity.EXTRA_USER_ID, user.getId());
        startActivity(intent);
        // finish() removes Login from the back stack, so Back on Home leaves the app
        // instead of returning to a login form for someone who is already signed in.
        finish();
    }

    private void setBusy(boolean busy) {
        btnLogin.setEnabled(!busy);
        btnLogin.setText(busy ? R.string.signing_in : R.string.action_login);
    }

    private static String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }
}
