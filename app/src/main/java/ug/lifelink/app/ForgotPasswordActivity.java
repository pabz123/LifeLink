package ug.lifelink.app;

import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
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
import ug.lifelink.app.util.SimpleTextWatcher;
import ug.lifelink.app.util.Validators;

/**
 * Password reset by security question, in two steps on one screen.
 *
 * Step 1: enter the email. Step 2: answer the question and choose a new password.
 *
 * Design notes for the README and the oral defence:
 *  - LifeLink has no server yet, so it cannot email a reset link. A security question is the
 *    standard offline alternative. A production version would send a one-time code by SMS or
 *    email through the backend (the proposal names Africa's Talking for SMS).
 *  - For an unknown email the screen still shows a question (a fixed one chosen from the
 *    email text) and fails only at the end with the same message as a wrong answer. That way
 *    this page cannot be used to discover which emails are registered.
 *  - Wrong answers count towards the same five-attempt lockout as wrong passwords.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "ug.lifelink.app.extra.EMAIL";

    private static final String TAG = "ForgotPasswordActivity";
    private static final String STATE_STEP_TWO = "step_two";
    private static final String STATE_EMAIL = "reset_email";
    private static final String STATE_QUESTION = "question_index";

    private TextView tvForgotSubheading;
    private View groupEmailStep;
    private View groupResetStep;
    private TextView tvQuestion;
    private TextInputLayout layoutEmail;
    private TextInputLayout layoutAnswer;
    private TextInputLayout layoutPassword;
    private TextInputLayout layoutConfirm;
    private TextInputEditText txtEmail;
    private TextInputEditText txtAnswer;
    private TextInputEditText txtPassword;
    private TextInputEditText txtConfirm;
    private Button btnContinue;
    private Button btnReset;
    private TextView tvBackLink;

    private UserRepository users;
    private String[] questions;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private boolean stepTwo;
    private String emailForReset = "";
    private int questionIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        bindViews();
        users = new UserRepository(this);
        questions = getResources().getStringArray(R.array.security_questions);
        setUpListeners();

        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_STEP_TWO)) {
            // The phone was rotated in step 2: rebuild that step.
            emailForReset = savedInstanceState.getString(STATE_EMAIL, "");
            questionIndex = savedInstanceState.getInt(STATE_QUESTION, 1);
            showStepTwo();
        } else if (savedInstanceState == null) {
            String prefill = getIntent().getStringExtra(EXTRA_EMAIL);
            if (prefill != null) {
                txtEmail.setText(prefill);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_STEP_TWO, stepTwo);
        outState.putString(STATE_EMAIL, emailForReset);
        outState.putInt(STATE_QUESTION, questionIndex);
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private void bindViews() {
        tvForgotSubheading = findViewById(R.id.tvForgotSubheading);
        groupEmailStep = findViewById(R.id.groupEmailStep);
        groupResetStep = findViewById(R.id.groupResetStep);
        tvQuestion = findViewById(R.id.tvQuestion);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutAnswer = findViewById(R.id.layoutAnswer);
        layoutPassword = findViewById(R.id.layoutPassword);
        layoutConfirm = findViewById(R.id.layoutConfirm);
        txtEmail = findViewById(R.id.txtEmail);
        txtAnswer = findViewById(R.id.txtAnswer);
        txtPassword = findViewById(R.id.txtPassword);
        txtConfirm = findViewById(R.id.txtConfirm);
        btnContinue = findViewById(R.id.btnContinue);
        btnReset = findViewById(R.id.btnReset);
        tvBackLink = findViewById(R.id.tvBackLink);
    }

    private void setUpListeners() {
        btnContinue.setOnClickListener(v -> continueToQuestion());
        txtEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                continueToQuestion();
                return true;
            }
            return false;
        });

        btnReset.setOnClickListener(v -> attemptReset());
        txtConfirm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptReset();
                return true;
            }
            return false;
        });

        tvBackLink.setOnClickListener(v -> finish());

        clearErrorOnEdit(txtEmail, layoutEmail);
        clearErrorOnEdit(txtAnswer, layoutAnswer);
        clearErrorOnEdit(txtPassword, layoutPassword);
        clearErrorOnEdit(txtConfirm, layoutConfirm);
    }

    // ------------------------------------------------------------------ step 1

    private void continueToQuestion() {
        layoutEmail.setError(null);
        final String email = textOf(txtEmail).trim().toLowerCase(Locale.ROOT);

        int emailError = Validators.emailError(email);
        if (emailError != 0) {
            layoutEmail.setError(getString(emailError));
            txtEmail.requestFocus();
            return;
        }

        btnContinue.setEnabled(false);
        worker.execute(() -> {
            int index = -1;
            boolean failed = false;
            try {
                index = users.getSecurityQuestion(email);
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not load the security question", e);
                failed = true;
            }
            final int found = index;
            final boolean error = failed;
            runOnUiThread(() -> onQuestionLoaded(email, found, error));
        });
    }

    private void onQuestionLoaded(String email, int foundIndex, boolean error) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        btnContinue.setEnabled(true);
        if (error) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
            return;
        }

        emailForReset = email;
        // Unknown email: show a stable fake question so the page looks the same.
        questionIndex = foundIndex >= 1 ? foundIndex
                : 1 + Math.floorMod(email.hashCode(), Validators.SECURITY_QUESTION_COUNT);
        showStepTwo();
        txtAnswer.requestFocus();
    }

    private void showStepTwo() {
        stepTwo = true;
        groupEmailStep.setVisibility(View.GONE);
        groupResetStep.setVisibility(View.VISIBLE);
        tvForgotSubheading.setText(R.string.forgot_subheading_question);
        int safeIndex = Math.max(1, Math.min(questionIndex, questions.length - 1));
        tvQuestion.setText(questions[safeIndex]);
    }

    // ------------------------------------------------------------------ step 2

    private void attemptReset() {
        layoutAnswer.setError(null);
        layoutPassword.setError(null);
        layoutConfirm.setError(null);

        final String answer = textOf(txtAnswer);
        final String newPassword = textOf(txtPassword);
        final String confirm = textOf(txtConfirm);

        View firstInvalid = null;

        int answerError = Validators.securityAnswerError(answer);
        if (answerError != 0) {
            layoutAnswer.setError(getString(answerError));
            firstInvalid = txtAnswer;
        }
        int passwordError = Validators.passwordError(newPassword);
        if (passwordError != 0) {
            layoutPassword.setError(getString(passwordError));
            if (firstInvalid == null) {
                firstInvalid = txtPassword;
            }
        }
        int confirmError = Validators.confirmError(newPassword, confirm);
        if (confirmError != 0) {
            layoutConfirm.setError(getString(confirmError));
            if (firstInvalid == null) {
                firstInvalid = txtConfirm;
            }
        }
        if (firstInvalid != null) {
            firstInvalid.requestFocus();
            return;
        }

        setBusy(true);
        final String email = emailForReset;
        worker.execute(() -> {
            AuthResult result = null;
            try {
                result = users.resetPassword(email, answer, newPassword);
            } catch (RuntimeException e) {
                Log.e(TAG, "Reset failed unexpectedly", e);
            }
            final AuthResult outcome = result;
            runOnUiThread(() -> onResetResult(outcome));
        });
    }

    private void onResetResult(AuthResult result) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        setBusy(false);

        if (result == null) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
            return;
        }
        switch (result.getStatus()) {
            case SUCCESS:
                Toast.makeText(this, R.string.reset_done, Toast.LENGTH_LONG).show();
                finish(); // back to Login
                return;
            case LOCKED:
                layoutAnswer.setError(getString(R.string.error_locked,
                        result.getMinutesLeft(System.currentTimeMillis())));
                return;
            case INVALID:
            default:
                // Same message whether the email is unknown or the answer is wrong.
                layoutAnswer.setError(getString(R.string.reset_failed));
                txtAnswer.requestFocus();
        }
    }

    private void setBusy(boolean busy) {
        btnReset.setEnabled(!busy);
        btnReset.setText(busy ? R.string.resetting : R.string.action_reset);
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
