/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.Arrays;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.cryptcage.R;
import com.cryptcage.app.App;

public class SettingsActivity extends SecureActivity implements ChoosePasswordDialogFragment.Callback {

    @Override
    protected boolean needsVault() {
        return false;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPasswordChosen(final String purpose, final char[] password) {
        if (!ChoosePasswordDialogFragment.PURPOSE_CHANGE.equals(purpose)) {
            Arrays.fill(password, '\0');
            return;
        }
        PasswordChange.apply(this, password);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private void showClipboardTimeout(final Preference preference) {
            final Context context = requireContext();
            final View view = LayoutInflater.from(context).inflate(R.layout.pref_duration, null);
            final NumberPicker hours = view.findViewById(R.id.picker_hours);
            final NumberPicker minutes = view.findViewById(R.id.picker_minutes);
            final NumberPicker seconds = view.findViewById(R.id.picker_seconds);
            ((TextView) view.findViewById(R.id.text_hint)).setText(R.string.pref_clipboardTimeout_summary);
            hours.setMinValue(0);
            hours.setMaxValue(ClipboardTimer.MAX_SECONDS / 3600);
            minutes.setMinValue(0);
            minutes.setMaxValue(59);
            seconds.setMinValue(0);
            seconds.setMaxValue(59);
            final int current = ClipboardTimer.seconds(context);
            hours.setValue(current / 3600);
            minutes.setValue(current % 3600 / 60);
            seconds.setValue(current % 60);
            new AlertDialog.Builder(context)
                    .setTitle(R.string.pref_clipboardTimeout)
                    .setView(view)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        final int total = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
                        ClipboardTimer.save(context, total);
                        preference.setSummary(ClipboardTimer.format(ClipboardTimer.seconds(context)));
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            // The new value is stored after these listeners return, so the change is applied
            // on the next pass of the event loop.
            final Preference palette = findPreference("pref_palette");
            if (palette != null) {
                palette.setOnPreferenceChangeListener((preference, value) -> {
                    requireView().post(() -> requireActivity().recreate());
                    return true;
                });
            }
            final boolean unlocked = App.isUnlocked();
            final Preference clipboard = findPreference("pref_clipboardTimeout");
            if (clipboard != null) {
                clipboard.setVisible(unlocked);
                clipboard.setSummary(ClipboardTimer.format(ClipboardTimer.seconds(requireContext())));
                clipboard.setOnPreferenceClickListener(preference -> {
                    showClipboardTimeout(preference);
                    return true;
                });
            }
            final Preference changePassword = findPreference("pref_changePassword");
            if (changePassword != null) {
                changePassword.setVisible(unlocked);
                changePassword.setOnPreferenceClickListener(preference -> {
                    ChoosePasswordDialogFragment.newInstance(ChoosePasswordDialogFragment.PURPOSE_CHANGE)
                            .show(getParentFragmentManager(), "change_password");
                    return true;
                });
            }
            final Preference about = findPreference("pref_about");
            if (about != null) {
                about.setOnPreferenceClickListener(preference -> {
                    AboutToast.show(requireContext());
                    return true;
                });
            }
            final Preference screenshot = findPreference("pref_screenshot");
            if (screenshot != null) {
                screenshot.setOnPreferenceChangeListener((preference, value) -> {
                    requireView().post(() -> SecureActivity.applyScreenshotPolicy(requireActivity()));
                    return true;
                });
            }
        }
    }
}
