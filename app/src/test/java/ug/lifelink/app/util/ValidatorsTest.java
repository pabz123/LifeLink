package ug.lifelink.app.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ug.lifelink.app.R;

/**
 * Local unit tests: they run on your laptop with no phone or emulator
 * (right-click this file in Android Studio and choose Run).
 * They cover the validation rules written in the README and double as test cases for Milestone 4.
 */
public class ValidatorsTest {

    @Test
    public void email_valid_returnsZero() {
        assertEquals(0, Validators.emailError("pabz@must.ac.ug"));
    }

    @Test
    public void email_missingAtSign_isInvalid() {
        assertEquals(R.string.error_email_invalid, Validators.emailError("pabz.must.ac.ug"));
    }

    @Test
    public void email_empty_isRequired() {
        assertEquals(R.string.error_email_required, Validators.emailError("  "));
    }

    @Test
    public void phone_localFormat_isValid() {
        assertEquals(0, Validators.phoneError("0772 123 456"));
    }

    @Test
    public void phone_internationalFormat_isValid() {
        assertEquals(0, Validators.phoneError("+256772123456"));
    }

    @Test
    public void phone_tooShort_isInvalid() {
        assertEquals(R.string.error_phone_invalid, Validators.phoneError("077212"));
    }

    @Test
    public void phone_normalize_convertsLeadingZero() {
        assertEquals("+256772123456", Validators.normalizePhone("0772 123 456"));
    }

    @Test
    public void password_tooShort_isRejected() {
        assertEquals(R.string.error_password_short, Validators.passwordError("abc12"));
    }

    @Test
    public void password_lettersOnly_isWeak() {
        assertEquals(R.string.error_password_weak, Validators.passwordError("abcdefghij"));
    }

    @Test
    public void password_lettersAndDigits_isAccepted() {
        assertEquals(0, Validators.passwordError("lifelink2026"));
    }

    @Test
    public void confirm_mismatch_isRejected() {
        assertEquals(R.string.error_confirm_mismatch, Validators.confirmError("lifelink2026", "lifelink2025"));
    }

    @Test
    public void name_withDigits_isInvalid() {
        assertEquals(R.string.error_name_invalid, Validators.nameError("Pabz 123"));
    }

    @Test
    public void eligibility_after92Days_isEligible() {
        long day = 24L * 60 * 60 * 1000;
        long now = 1_000 * day;
        assertEquals(0, Eligibility.daysUntilEligible(now - 92 * day, now));
    }

    @Test
    public void eligibility_after30Days_waits60Days() {
        long day = 24L * 60 * 60 * 1000;
        long now = 1_000 * day;
        assertEquals(60, Eligibility.daysUntilEligible(now - 30 * day, now));
    }
}
