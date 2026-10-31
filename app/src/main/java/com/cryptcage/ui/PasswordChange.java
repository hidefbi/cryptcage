/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.Arrays;

import android.content.Context;
import android.widget.Toast;

import com.cryptcage.R;
import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.VaultException;

/** Changes the master password of the open database. Shared by the note list and the settings. */
final class PasswordChange {

    private PasswordChange() {
    }

    /** The password array is cleared once the change has finished or could not start. */
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
