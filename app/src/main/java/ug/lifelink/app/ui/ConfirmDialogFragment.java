package ug.lifelink.app.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ug.lifelink.app.R;

/**
 * Reusable dialog for the whole app: a confirmation (Cancel plus a confirm button) or an
 * information message (OK only).
 *
 * Why a DialogFragment and not a plain AlertDialog: when the phone is rotated, the Activity is
 * destroyed and recreated. A plain dialog would be lost (and Android logs a "window leaked"
 * error). The FragmentManager recreates a DialogFragment by itself, so the dialog survives.
 *
 * The result goes back through the Fragment Result API: the Activity listens for the request
 * key with setFragmentResultListener(), and that listener is registered again in onCreate()
 * after every rotation, so the answer is never lost.
 */
public class ConfirmDialogFragment extends DialogFragment {

    public static final String TAG = "confirm_dialog";

    private static final String ARG_KEY = "key";
    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";
    private static final String ARG_POSITIVE = "positive";

    /** Cancel and a confirm button. The Activity is told only when the user confirms. */
    public static ConfirmDialogFragment newConfirmation(@NonNull String requestKey,
                                                        @StringRes int title,
                                                        @StringRes int message,
                                                        @StringRes int positiveLabel) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, requestKey);
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MESSAGE, message);
        args.putInt(ARG_POSITIVE, positiveLabel);
        ConfirmDialogFragment fragment = new ConfirmDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /** A message with one OK button and no result. */
    public static ConfirmDialogFragment newInfo(@StringRes int title, @StringRes int message) {
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MESSAGE, message);
        ConfirmDialogFragment fragment = new ConfirmDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        final String key = args.getString(ARG_KEY);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(args.getInt(ARG_TITLE))
                .setMessage(args.getInt(ARG_MESSAGE));

        if (key == null) {
            builder.setPositiveButton(R.string.action_ok, null);
        } else {
            builder.setPositiveButton(args.getInt(ARG_POSITIVE), (dialog, which) ->
                    getParentFragmentManager().setFragmentResult(key, new Bundle()));
            builder.setNegativeButton(R.string.action_cancel, null);
        }
        return builder.create();
    }
}
