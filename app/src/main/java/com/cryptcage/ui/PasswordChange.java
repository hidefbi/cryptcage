/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.io.File;
import java.util.Arrays;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.cryptcage.R;
import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.StringMethods;
import com.cryptcage.utils.VaultException;
import com.cryptcage.utils.VaultSession;

/**
 * Changes the master password of the open database. Shared by the note list and the settings.
 * The current password is checked first, so the new one is only asked for by someone who knows
 * the old one.
 */
final class PasswordChange {

    private PasswordChange() {
    }

    /** The name the vault is listed under, which is also the name the PIN and biometric stores use. */
    static String vaultName(final File file) {
        final String name = file.getName();
        return name.endsWith(StringMethods.EXTENSION)
                ? name.substring(0, name.length() - StringMethods.EXTENSION.length())
                : name;
    }

    /**
     * Opens the vault with the current password on a worker thread. onMatch runs on the UI thread
     * when the password is right. The array is cleared in every case.
     */
    static void verifyCurrent(final Activity activity, final File file, final char[] current,
                              final Runnable onMatch) {
        final Context app = activity.getApplicationContext();
        // The check takes about a second, so the wait is shown right away instead of a blank gap.
        final AlertDialog waiting = waitingDialog(activity);
        Tasks.io(() -> {
            try {
                VaultSession.open(file, current).close();
                Tasks.ui(() -> {
                    dismiss(waiting);
                    onMatch.run();
                });
            } catch (VaultException e) {
                final int message = VaultMessages.messageFor(e.getReason());
                Tasks.ui(() -> {
                    dismiss(waiting);
                    Toast.makeText(app, message, Toast.LENGTH_SHORT).show();
                });
            } finally {
                Arrays.fill(current, '\0');
            }
        });
    }

    private static AlertDialog waitingDialog(final Activity activity) {
        final int pad = (int) (24 * activity.getResources().getDisplayMetrics().density);
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, pad, pad, pad);
        row.addView(new ProgressBar(activity));
        final TextView text = new TextView(activity);
        text.setText(R.string.dialog_checkingPassword);
        text.setPadding(pad, 0, 0, 0);
        row.addView(text);
        return new AlertDialog.Builder(activity)
                .setView(row)
                .setCancelable(false)
                .show();
    }

    private static void dismiss(final AlertDialog dialog) {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    /**
     * The password array is cleared once the change has finished or could not start. After a
     * change, the PIN and biometric copies of this database are removed, because they still hold
     * the old password.
     */
    static void apply(final Context context, final char[] password) {
        final App.Session current = App.session();
        if (current == null) {
            Arrays.fill(password, '\0');
            return;
        }
        final Context app = context.getApplicationContext();
        Tasks.io(() -> {
            try {
                current.vault.changePassword(password);
                final String name = vaultName(current.file);
                PinStore.removeCredential(app, name);
                BiometricVault.removeCredential(app, name);
                Tasks.ui(() -> Toast.makeText(app, R.string.toast_success_changePsw, Toast.LENGTH_SHORT).show());
            } catch (VaultException e) {
                final int message = VaultMessages.messageFor(e.getReason());
                Tasks.ui(() -> Toast.makeText(app, message, Toast.LENGTH_SHORT).show());
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }
}
