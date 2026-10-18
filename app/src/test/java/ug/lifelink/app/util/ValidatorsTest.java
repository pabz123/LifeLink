package ug.lifelink.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ug.lifelink.app.R;
import ug.lifelink.app.model.User;

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

    // ---- blood group: donors must choose, "unknown" is allowed, hospital staff are exempt

    @Test
    public void bloodGroup_donorWithRealGroup_isValid() {
        assertEquals(0, Validators.bloodGroupError(User.ROLE_DONOR, "O+"));
    }

    @Test
    public void bloodGroup_donorWhoDoesNotKnow_isValid() {
        assertEquals(0, Validators.bloodGroupError(User.ROLE_DONOR, BloodGroups.UNKNOWN));
    }

    @Test
    public void bloodGroup_donorWithNothingChosen_isRejected() {
        assertEquals(R.string.error_blood_group_required, Validators.bloodGroupError(User.ROLE_DONOR, ""));
    }

    @Test
    public void bloodGroup_donorWithMadeUpGroup_isRejected() {
        assertEquals(R.string.error_blood_group_required, Validators.bloodGroupError(User.ROLE_DONOR, "Z+"));
    }

    @Test
    public void bloodGroup_hospitalStaff_needNone() {
        assertEquals(0, Validators.bloodGroupError(User.ROLE_HOSPITAL, ""));
    }

    // ---- security question and answer

    @Test
    public void securityQuestion_promptPosition_isRejected() {
        assertEquals(R.string.error_security_question_required, Validators.securityQuestionError(0));
    }

    @Test
    public void securityQuestion_realQuestion_isValid() {
        assertEquals(0, Validators.securityQuestionError(Validators.SECURITY_QUESTION_COUNT));
    }

    @Test
    public void securityQuestion_beyondList_isRejected() {
        assertEquals(R.string.error_security_question_required,
                Validators.securityQuestionError(Validators.SECURITY_QUESTION_COUNT + 1));
    }

    @Test
    public void securityAnswer_tooShort_isRejected() {
        assertEquals(R.string.error_security_answer_short, Validators.securityAnswerError(" ab "));
    }

    @Test
    public void securityAnswer_normalisation_ignoresCaseAndSpaces() {
        assertEquals("mbarara town", Validators.normalizeAnswer("  Mbarara   TOWN "));
    }

    // ---- record form (Milestone 2)

    @Test
    public void facility_normalName_isValid() {
        assertEquals(0, Validators.facilityError("Mbarara Regional Referral Hospital"));
        assertEquals(0, Validators.facilityError("St. Mary's Hospital (Lacor)"));
    }

    @Test
    public void facility_empty_isRequired() {
        assertEquals(R.string.error_facility_required, Validators.facilityError("   "));
    }

    @Test
    public void facility_oneCharacter_isInvalid() {
        assertEquals(R.string.error_facility_invalid, Validators.facilityError("K"));
    }

    @Test
    public void facility_withSymbols_isInvalid() {
        assertEquals(R.string.error_facility_invalid, Validators.facilityError("Hospital <script>"));
    }

    @Test
    public void units_validRange_isAccepted() {
        assertEquals(0, Validators.unitsError("1"));
        assertEquals(0, Validators.unitsError(" 20 "));
    }

    @Test
    public void units_empty_isRequired() {
        assertEquals(R.string.error_units_required, Validators.unitsError(""));
    }

    @Test
    public void units_outOfRange_isRejected() {
        assertEquals(R.string.error_units_range, Validators.unitsError("0"));
        assertEquals(R.string.error_units_range, Validators.unitsError("21"));
        assertEquals(R.string.error_units_range, Validators.unitsError("-3"));
        assertEquals(R.string.error_units_range, Validators.unitsError("2.5"));
        assertEquals(R.string.error_units_range, Validators.unitsError("abc"));
    }

    @Test
    public void recordBloodGroup_unknownIsNotAcceptedForARecord() {
        assertEquals(0, Validators.recordBloodGroupError("AB-"));
        assertEquals(R.string.error_record_blood_group_required, Validators.recordBloodGroupError(BloodGroups.UNKNOWN));
        assertEquals(R.string.error_record_blood_group_required, Validators.recordBloodGroupError(""));
    }

    @Test
    public void recordStatus_matchesTheKindOfRecord() {
        assertTrue(Validators.isValidRecordStatus("DONATION", "COMPLETED"));
        assertFalse(Validators.isValidRecordStatus("DONATION", "OPEN"));
        assertTrue(Validators.isValidRecordStatus("REQUEST", "URGENT"));
        assertFalse(Validators.isValidRecordStatus("REQUEST", "COMPLETED"));
        assertFalse(Validators.isValidRecordStatus("GIFT", "OPEN"));
    }

    @Test
    public void note_over120Characters_isRejected() {
        StringBuilder longNote = new StringBuilder();
        for (int i = 0; i < 121; i++) {
            longNote.append('x');
        }
        assertEquals(R.string.error_note_long, Validators.noteError(longNote.toString()));
        assertEquals(0, Validators.noteError(""));
    }

    @Test
    public void date_futureIsRejected_pastIsAccepted() {
        long day = 24L * 60 * 60 * 1000;
        long now = 1_000 * day;
        assertEquals(0, Validators.dateError(now - 5 * day, now));
        assertEquals(R.string.error_date_future, Validators.dateError(now + 5 * day, now));
    }

    // ---- eligibility

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
