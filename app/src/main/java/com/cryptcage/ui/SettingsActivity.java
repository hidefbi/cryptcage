/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.cryptcage.R;

public class SettingsActivity extends SecureActivity {

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

    public static class SettingsFragment extends PreferenceFragmentCompat {
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
