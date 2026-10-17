package ug.lifelink.app.util;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * TextWatcher has three methods but screens usually need only one.
 * This adapter class gives the other two an empty body.
 */
public abstract class SimpleTextWatcher implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public abstract void afterTextChanged(Editable s);
}
