/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.cryptcage.R;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.PinRules;

/**
 * Asks for the PIN in one of three cases: setting it up (with the vault's password already
 * held), unlocking a vault, or saving a vault's password under the PIN the first time that vault
 * is opened.
 */
public class PinDialogFragment extends DialogFragment {

    public static final int MODE_SETUP = 0;
    public static final int MODE_UNLOCK = 1;
    public static final int MODE_REGISTER = 2;

    public interface Callback {
        /** UNLOCK passes the master password. SETUP and REGISTER pass null after the copy is saved. */
        void onPinFinished(int mode, String vault, char[] password);

        /** The dialog closed without finishing. usePassword is true when the user chose the master password. */
        void onPinDismissed(int mode, String vault, boolean usePassword);
    }

    private enum Result { DONE, WRONG, WIPED, FAILED }

    private static final String ARG_MODE = "mode";
    private static final String ARG_VAULT = "vault";

    private EditText field;
    private EditText confirm;
    private boolean resolved;
    private boolean usePassword;

    public static PinDialogFragment newInstance(final int mode, final String vault) {
        final PinDialogFragment fragment = new PinDialogFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_MODE, mode);
        args.putString(ARG_VAULT, vault);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(requireContext()).inflate(R.layout.pin_entry, null);
        field = view.findViewById(R.id.field_pin);
        confirm = view.findViewById(R.id.conf_field_pin);
        final boolean setup = mode() == MODE_SETUP;
        confirm.setVisibility(setup ? View.VISIBLE : View.GONE);
        final AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setView(view)
                .setTitle(setup ? R.string.pin_title_setup : R.string.pin_title_unlock)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (mode() == MODE_UNLOCK) {
            builder.setNeutralButton(R.string.pin_use_password, null);
        }
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit());
            if (mode() == MODE_UNLOCK) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    usePassword = true;
                    dismiss();
                });
            }
        }
    }

    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        super.onDismiss(dialog);
        PinStore.dropPassword();
        if (!resolved && getActivity() instanceof Callback) {
            ((Callback) getActivity()).onPinDismissed(mode(), vault(), usePassword);
        }
    }

    private int mode() {
        return getArguments() == null ? MODE_UNLOCK : getArguments().getInt(ARG_MODE);
    }

    private String vault() {
        return getArguments() == null ? null : getArguments().getString(ARG_VAULT);
    }

    private void submit() {
        final String text = field.getText().toString();
        if (!PinRules.isValid(text)) {
            field.setError(getString(R.string.pin_invalid));
            return;
        }
        if (mode() == MODE_SETUP && !text.equals(confirm.getText().toString())) {
            confirm.setError(getString(R.string.pin_mismatch));
            return;
        }
        final char[] pin = text.toCharArray();
        field.setText("");
        confirm.setText("");
        final Context app = requireContext().getApplicationContext();
        final int mode = mode();
        final String vault = vault();
        setBusy(true);
        Tasks.io(() -> {
            try {
                final Outcome outcome = run(app, mode, vault, pin);
                Tasks.ui(() -> apply(outcome));
            } finally {
                Arrays.fill(pin, '\0');
            }
        });
    }

    /** Runs on the background thread: the PIN check is slow on purpose. */
    private static Outcome run(final Context app, final int mode, final String vault, final char[] pin) {
        try {
            if (mode == MODE_SETUP) {
                final char[] password = PinStore.takePassword();
                if (password == null) {
                    return Outcome.of(Result.FAILED, null, 0);
                }
                try {
                    PinStore.enable(app, pin);
                    PinStore.saveCredential(app, vault, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
                return Outcome.of(Result.DONE, null, 0);
            }
            switch (PinStore.check(app, pin)) {
                case WRONG:
                    return Outcome.of(Result.WRONG, null, PinStore.attemptsLeft(app));
                case WIPED:
                    return Outcome.of(Result.WIPED, null, 0);
                case NOT_SET:
                    return Outcome.of(Result.FAILED, null, 0);
                default:
                    break;
            }
            if (mode == MODE_REGISTER) {
                final char[] password = PinStore.takePassword();
                if (password == null) {
                    return Outcome.of(Result.FAILED, null, 0);
                }
                try {
                    PinStore.saveCredential(app, vault, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
                return Outcome.of(Result.DONE, null, 0);
            }
            final char[] password = PinStore.loadCredential(app, vault);
            return password == null ? Outcome.of(Result.FAILED, null, 0) : Outcome.of(Result.DONE, password, 0);
        } catch (GeneralSecurityException e) {
            return Outcome.of(Result.FAILED, null, 0);
        }
    }

    /** Disables the buttons while the PIN is checked, so a second tap does not start another check. */
    private void setBusy(final boolean busy) {
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
            if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!busy);
            }
        }
    }

    private void apply(final Outcome outcome) {
        setBusy(false);
        switch (outcome.result) {
            case DONE:
                resolved = true;
                dismiss();
                if (getActivity() instanceof Callback) {
                    ((Callback) getActivity()).onPinFinished(mode(), vault(), outcome.password);
                }
                break;
            case WRONG:
                field.setError(getString(R.string.pin_wrong, outcome.attemptsLeft));
                break;
            case WIPED:
                toast(R.string.pin_wiped);
                usePassword = true;
                dismiss();
                break;
            default:
                toast(R.string.pin_unavailable);
                usePassword = true;
                dismiss();
                break;
        }
    }

    private void toast(final int message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private static final class Outcome {
        final Result result;
        final char[] password;
        final int attemptsLeft;

        private Outcome(final Result result, final char[] password, final int attemptsLeft) {
            this.result = result;
            this.password = password;
            this.attemptsLeft = attemptsLeft;
        }

        static Outcome of(final Result result, final char[] password, final int attemptsLeft) {
            return new Outcome(result, password, attemptsLeft);
        }
    }
}
