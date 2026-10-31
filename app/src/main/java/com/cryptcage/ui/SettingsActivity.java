/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.cryptcage.R;
import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.StringMethods;
import com.cryptcage.utils.VaultException;
import com.cryptcage.utils.VaultSession;

public class SettingsActivity extends SecureActivity
        implements ChoosePasswordDialogFragment.Callback, InsertPasswordDialogFragment.Callback,
        PinDialogFragment.Callback {

    /** True when the password being asked for sets up biometric unlock, false for the PIN. */
    private boolean setupForBiometric;

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

    /** Asks for the password of the open vault before the PIN or biometric unlock is set up. */
    void startSetup(final boolean biometric) {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        setupForBiometric = biometric;
        InsertPasswordDialogFragment.newInstance(vaultName(session.file))
                .show(getSupportFragmentManager(), "setup_password");
    }

    /** The password of the open vault, checked before the PIN or biometric unlock is set up with it. */
    @Override
    public void onPasswordEntered(final String fileName, final char[] password) {
        final App.Session session = App.session();
        if (session == null) {
            Arrays.fill(password, '\0');
            return;
        }
        final File file = session.file;
        final boolean biometric = setupForBiometric;
        Tasks.io(() -> {
            try {
                VaultSession.open(file, password).close();
                PinStore.holdPassword(password);
                final String name = vaultName(file);
                if (biometric) {
                    final Cipher cipher = BiometricVault.encryptCipher();
                    Tasks.ui(() -> promptBiometricSetup(name, cipher));
                } else {
                    Tasks.ui(() -> PinDialogFragment.newInstance(PinDialogFragment.MODE_SETUP, name)
                            .show(getSupportFragmentManager(), "pin_setup"));
                }
            } catch (VaultException e) {
                PinStore.dropPassword();
                final int message = VaultMessages.messageFor(e.getReason());
                Tasks.ui(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
            } catch (GeneralSecurityException e) {
                PinStore.dropPassword();
                Tasks.ui(() -> Toast.makeText(this, R.string.bio_unavailable, Toast.LENGTH_LONG).show());
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void promptBiometricSetup(final String name, final Cipher cipher) {
        BiometricVault.authenticate(this, cipher, R.string.bio_prompt_setup, new BiometricVault.Listener() {
            @Override
            public void onAuthenticated(final Cipher authorized) {
                saveBiometric(name, authorized);
            }

            @Override
            public void onFailed() {
                PinStore.dropPassword();
            }
        });
    }

    private void saveBiometric(final String name, final Cipher authorized) {
        Tasks.io(() -> {
            final char[] password = PinStore.takePassword();
            boolean saved = false;
            if (password != null) {
                try {
                    BiometricVault.saveCredential(this, name, authorized, password);
                    saved = true;
                } catch (GeneralSecurityException e) {
                    saved = false;
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
            final int message = saved ? R.string.bio_saved : R.string.bio_unavailable;
            Tasks.ui(() -> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                refreshSettingsRows();
            });
        });
    }

    @Override
    public void onPinFinished(final int mode, final String vault, final char[] password) {
        if (mode == PinDialogFragment.MODE_SETUP) {
            Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_LONG).show();
            refreshSettingsRows();
        }
    }

    @Override
    public void onPinDismissed(final int mode, final String vault, final boolean usePassword) {
        // Nothing to do: the PIN is not set up, and the password stays as it was.
    }

    private void refreshSettingsRows() {
        final Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.settings_container);
        if (fragment instanceof SettingsFragment) {
            ((SettingsFragment) fragment).refreshSecurityRows();
        }
    }

    /** The name the vault is listed under, which is also the name the PIN and biometric stores use. */
    private static String vaultName(final File file) {
        final String name = file.getName();
        return name.endsWith(StringMethods.EXTENSION)
                ? name.substring(0, name.length() - StringMethods.EXTENSION.length())
                : name;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        /** Shows whether the PIN and biometric unlock are on. */
        void refreshSecurityRows() {
            final Context context = requireContext();
            final Preference pin = findPreference("pref_pin");
            if (pin != null) {
                pin.setSummary(PinStore.isEnabled(context) ? R.string.pref_pin_on : R.string.pref_pin_off);
            }
            final Preference biometric = findPreference("pref_biometric");
            if (biometric != null) {
                final int reason = BiometricVault.unavailableReason(context);
                if (reason != 0) {
                    biometric.setSummary(reason);
                } else {
                    biometric.setSummary(BiometricVault.isEnabled(context)
                            ? R.string.pref_biometric_on : R.string.pref_biometric_off);
                }
            }
        }

        private void onPinClicked() {
            final Context context = requireContext();
            if (PinStore.isEnabled(context)) {
                confirmDisable(R.string.pin_disable_title, R.string.pin_disable_message, () -> {
                    PinStore.disable(context);
                    refreshSecurityRows();
                });
            } else {
                ((SettingsActivity) requireActivity()).startSetup(false);
            }
        }

        private void onBiometricClicked() {
            final Context context = requireContext();
            final int reason = BiometricVault.unavailableReason(context);
            if (reason != 0) {
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
                return;
            }
            if (BiometricVault.isEnabled(context)) {
                confirmDisable(R.string.bio_disable_title, R.string.bio_disable_message, () -> {
                    BiometricVault.disable(context);
                    refreshSecurityRows();
                });
            } else {
                ((SettingsActivity) requireActivity()).startSetup(true);
            }
        }

        private void confirmDisable(final int title, final int message, final Runnable action) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> action.run())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

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
            final Preference pin = findPreference("pref_pin");
            if (pin != null) {
                pin.setVisible(unlocked && PinStore.supported());
                pin.setOnPreferenceClickListener(preference -> {
                    onPinClicked();
                    return true;
                });
            }
            final Preference biometric = findPreference("pref_biometric");
            if (biometric != null) {
                biometric.setVisible(unlocked);
                biometric.setOnPreferenceClickListener(preference -> {
                    onBiometricClicked();
                    return true;
                });
            }
            refreshSecurityRows();
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
