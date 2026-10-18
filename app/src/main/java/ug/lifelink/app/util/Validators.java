package ug.lifelink.app.util;

import java.util.Locale;
import java.util.regex.Pattern;

import ug.lifelink.app.R;
import ug.lifelink.app.model.User;

/**
 * Input validation rules in one place. Every method returns 0 when the input is valid,
 * otherwise the string resource id of the message to show. Returning ids (not text)
 * keeps all wording in strings.xml and keeps this class free of Android UI code,
 * so the rules can be unit tested on a laptop.
 *
 * The same rules run twice: on the screen (instant feedback) and again inside
 * UserRepository (so bad data cannot reach the database even if a screen is bypassed).
 */
public final class Validators {

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 64;
    public static final int ANSWER_MIN = 3;
    public static final int ANSWER_MAX = 60;

    /** Number of real questions in R.array.security_questions (the prompt is not counted). */
    public static final int SECURITY_QUESTION_COUNT = 4;

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern NAME =
            Pattern.compile("^\\p{L}[\\p{L} .'-]{1,59}$");
    private static final Pattern UG_MOBILE =
            Pattern.compile("^(?:\\+256|0)7\\d{8}$");

    private Validators() {
    }

    public static int nameError(String name) {
        if (name == null || name.trim().isEmpty()) {
            return R.string.error_name_required;
        }
        if (!NAME.matcher(name.trim()).matches()) {
            return R.string.error_name_invalid;
        }
        return 0;
    }

    public static int emailError(String email) {
        if (email == null || email.trim().isEmpty()) {
            return R.string.error_email_required;
        }
        if (!EMAIL.matcher(email.trim()).matches()) {
            return R.string.error_email_invalid;
        }
        return 0;
    }

    public static int phoneError(String rawPhone) {
        String phone = cleanPhone(rawPhone);
        if (phone.isEmpty()) {
            return R.string.error_phone_required;
        }
        if (!UG_MOBILE.matcher(phone).matches()) {
            return R.string.error_phone_invalid;
        }
        return 0;
    }

    /**
     * Donors must pick one of the eight groups or "unknown". Hospital staff have no blood
     * group to give, so nothing is required from them.
     */
    public static int bloodGroupError(String role, String code) {
        if (User.ROLE_HOSPITAL.equals(role)) {
            return 0;
        }
        boolean ok = BloodGroups.isRealGroup(code) || BloodGroups.UNKNOWN.equals(code);
        return ok ? 0 : R.string.error_blood_group_required;
    }

    public static int passwordError(String password) {
        if (password == null || password.isEmpty()) {
            return R.string.error_password_required;
        }
        if (password.length() < PASSWORD_MIN) {
            return R.string.error_password_short;
        }
        if (password.length() > PASSWORD_MAX) {
            return R.string.error_password_long;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit ? 0 : R.string.error_password_weak;
    }

    public static int confirmError(String password, String confirm) {
        if (confirm == null || confirm.isEmpty()) {
            return R.string.error_confirm_required;
        }
        return confirm.equals(password) ? 0 : R.string.error_confirm_mismatch;
    }

    /** The spinner position is the question number: 1 to SECURITY_QUESTION_COUNT. */
    public static int securityQuestionError(int questionIndex) {
        boolean ok = questionIndex >= 1 && questionIndex <= SECURITY_QUESTION_COUNT;
        return ok ? 0 : R.string.error_security_question_required;
    }

    public static int securityAnswerError(String answer) {
        String normalized = normalizeAnswer(answer);
        if (normalized.isEmpty()) {
            return R.string.error_security_answer_required;
        }
        if (normalized.length() < ANSWER_MIN) {
            return R.string.error_security_answer_short;
        }
        if (normalized.length() > ANSWER_MAX) {
            return R.string.error_security_answer_long;
        }
        return 0;
    }

    /**
     * Makes answers forgiving: "  Mbarara  Town " and "mbarara town" are the same answer.
     * The normalised text is what gets hashed, both when saving and when checking.
     */
    public static String normalizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ records (Milestone 2)

    public static final int UNITS_MIN = 1;
    public static final int UNITS_MAX = 20;
    public static final int NOTE_MAX = 120;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static final Pattern FACILITY =
            Pattern.compile("^[\\p{L}0-9][\\p{L}0-9 .,'&()/-]{1,79}$");

    public static int facilityError(String facility) {
        if (facility == null || facility.trim().isEmpty()) {
            return R.string.error_facility_required;
        }
        return FACILITY.matcher(facility.trim()).matches() ? 0 : R.string.error_facility_invalid;
    }

    /** Units arrive from a text field, so this checks the text: a whole number from 1 to 20. */
    public static int unitsError(String rawUnits) {
        String text = rawUnits == null ? "" : rawUnits.trim();
        if (text.isEmpty()) {
            return R.string.error_units_required;
        }
        if (!text.matches("\\d{1,2}")) {
            return R.string.error_units_range;
        }
        int value = Integer.parseInt(text);
        return value >= UNITS_MIN && value <= UNITS_MAX ? 0 : R.string.error_units_range;
    }

    /** A record needs a real blood group. "Unknown" is not accepted for a donation or a request. */
    public static int recordBloodGroupError(String code) {
        return BloodGroups.isRealGroup(code) ? 0 : R.string.error_record_blood_group_required;
    }

    /** A donation is always COMPLETED. A request is OPEN, URGENT or FULFILLED. */
    public static boolean isValidRecordStatus(String kind, String status) {
        if (kind == null || status == null) {
            return false;
        }
        if ("DONATION".equals(kind)) {
            return "COMPLETED".equals(status);
        }
        if ("REQUEST".equals(kind)) {
            return "OPEN".equals(status) || "URGENT".equals(status) || "FULFILLED".equals(status);
        }
        return false;
    }

    public static int noteError(String note) {
        if (note != null && note.trim().length() > NOTE_MAX) {
            return R.string.error_note_long;
        }
        return 0;
    }

    /** A record cannot be dated in the future (one day of slack covers time zones). */
    public static int dateError(long dateMillis, long nowMillis) {
        return dateMillis > nowMillis + DAY_MS ? R.string.error_date_future : 0;
    }

    /** Stores every number in one international format: 0772123456 becomes +256772123456. */
    public static String normalizePhone(String rawPhone) {
        String phone = cleanPhone(rawPhone);
        return phone.startsWith("0") ? "+256" + phone.substring(1) : phone;
    }

    private static String cleanPhone(String rawPhone) {
        return rawPhone == null ? "" : rawPhone.replace(" ", "").replace("-", "");
    }
}
