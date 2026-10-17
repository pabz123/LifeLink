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

import ug.lifelink.app.data.UserRepository;
import ug.lifelink.app.model.User;
import ug.lifelink.app.util.SessionManager;
import ug.lifelink.app.util.SimpleTextWatcher;
import ug.lifelink.app.util.Validators;

/**
 * Login screen and the app's launcher activity.
 *
 * Event handling: button click, keyboard "Done" action, link click and text changes.
 * Lifecycle: every callback writes a line to Logcat under the tag LL_LIFECYCLE
 * (Milestone 1, Part E). Filter Logcat by that tag while you launch, press Home,
 * return, rotate and press Back, then explain what you see in the README.
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

    private UserRepository users;
    private SessionManager session;

    // Database work and password hashing must not block the main (UI) thread.
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LIFECYCLE_TAG, "Login onCreate, "
                + (savedInstanceState == null ? "fresh launch (no saved state)"
                : "re-created with saved state (rotation or process restore)"));

        setContentView(R.layout.activity_login);
        bindViews();
        users = new UserRepository(this);
        session = new SessionManager(this);

        setUpListeners();
        skipLoginIfRemembered();
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

        // Action 4: an error disappears as soon as the user starts correcting the field.
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

    /** "Keep me signed in" from an earlier login: go straight to Home. */
    private void skipLoginIfRemembered() {
        if (!session.hasRememberedUser()) {
            return;
        }
        final long rememberedId = session.getRememberedUserId();
        worker.execute(() -> {
            User user = null;
            try {
                user = users.findById(rememberedId);
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not load the remembered user", e);
            }
            final User result = user;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (result == null) {
                    session.clear(); // the account no longer exists
                } else {
                    openHome(result);
                }
            });
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
            User user = null;
            boolean failed = false;
            try {
                user = users.authenticate(email, password);
            } catch (RuntimeException e) {
                Log.e(TAG, "Login failed unexpectedly", e);
                failed = true;
            }
            final User result = user;
            final boolean error = failed;
            runOnUiThread(() -> onLoginResult(result, error, remember));
        });
    }

    private void onLoginResult(User user, boolean error, boolean remember) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        setBusy(false);

        if (error) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
            return;
        }
        if (user == null) {
            // One message for both "unknown email" and "wrong password", so the screen
            // does not reveal which emails are registered.
            layoutPassword.setError(getString(R.string.login_failed));
            txtPassword.requestFocus();
            return;
        }

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
        intent.putExtra(HomeActivity.EXTRA_USER_ID, user.getId());
        intent.putExtra(HomeActivity.EXTRA_USER_NAME, user.getFullName());
        intent.putExtra(HomeActivity.EXTRA_USER_ROLE, user.getRole());
        intent.putExtra(HomeActivity.EXTRA_BLOOD_GROUP, user.getBloodGroup());
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
