package ug.lifelink.app.util;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import ug.lifelink.app.R;

/**
 * In-app language choice (English or Luganda). AppCompat stores the choice and recreates the
 * screens; strings missing in Luganda fall back to English automatically.
 */
public final class AppLanguage {

    public static final String ENGLISH = "en";
    public static final String LUGANDA = "lg";

    private AppLanguage() {
    }

    /** @return the tag currently chosen in the app, ENGLISH when none was chosen */
    public static String current() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty() && locales.get(0) != null
                && LUGANDA.equals(locales.get(0).getLanguage())) {
            return LUGANDA;
        }
        return ENGLISH;
    }

    public static void apply(String tag) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }

    /** The two names are shown in their own language so each can be recognised. */
    public static void showPicker(Activity activity) {
        String[] names = {activity.getString(R.string.language_english),
                activity.getString(R.string.language_luganda)};
        int checked = LUGANDA.equals(current()) ? 1 : 0;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_language)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String tag = which == 1 ? LUGANDA : ENGLISH;
                    if (!tag.equals(current())) {
                        apply(tag);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
