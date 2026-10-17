package ug.lifelink.app.util;

import java.util.regex.Pattern;

import ug.lifelink.app.R;

/**
 * Input validation rules in one place. Every method returns 0 when the input is valid,
 * otherwise the string resource id of the message to show. Returning ids (not text)
 * keeps all wording in strings.xml and keeps this class free of Android UI code,
 * so the rules can be unit tested on a laptop.
 */
public final class Validators {

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 64;

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

    /** Stores every number in one international format: 0772123456 becomes +256772123456. */
    public static String normalizePhone(String rawPhone) {
        String phone = cleanPhone(rawPhone);
        return phone.startsWith("0") ? "+256" + phone.substring(1) : phone;
    }

    private static String cleanPhone(String rawPhone) {
        return rawPhone == null ? "" : rawPhone.replace(" ", "").replace("-", "");
    }
}
