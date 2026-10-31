/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.Locale;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

import androidx.preference.DialogPreference;

import com.cryptcage.R;

/** A time picked as hours, minutes and seconds. The value is stored in seconds. */
public class DurationPreference extends DialogPreference {

    private NumberPicker hours;
    private NumberPicker minutes;
    private NumberPicker seconds;

    public DurationPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.pref_duration);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    static String format(final int total) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60);
    }

    @Override
    protected Object onGetDefaultValue(final TypedArray a, final int index) {
        return a.getInteger(index, ClipboardTimer.DEFAULT_SECONDS);
    }

    @Override
    protected void onSetInitialValue(final Object defaultValue) {
        final int fallback = defaultValue instanceof Integer
                ? (Integer) defaultValue : ClipboardTimer.DEFAULT_SECONDS;
        setSummary(format(getPersistedInt(fallback)));
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);
        hours = view.findViewById(R.id.picker_hours);
        minutes = view.findViewById(R.id.picker_minutes);
        seconds = view.findViewById(R.id.picker_seconds);
        hours.setMinValue(0);
        hours.setMaxValue(ClipboardTimer.MAX_SECONDS / 3600);
        minutes.setMinValue(0);
        minutes.setMaxValue(59);
        seconds.setMinValue(0);
        seconds.setMaxValue(59);
        final int value = getPersistedInt(ClipboardTimer.DEFAULT_SECONDS);
        hours.setValue(value / 3600);
        minutes.setValue(value % 3600 / 60);
        seconds.setValue(value % 60);
    }

    @Override
    protected void onDialogClosed(final boolean positiveResult) {
        if (!positiveResult || hours == null) {
            return;
        }
        final int total = Math.min(hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue(),
                ClipboardTimer.MAX_SECONDS);
        persistInt(total);
        setSummary(format(total));
    }
}
