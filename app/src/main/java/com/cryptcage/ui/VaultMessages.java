/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import com.cryptcage.R;
import com.cryptcage.utils.VaultException;

/** The message shown to the user for a vault error. */
final class VaultMessages {

    private VaultMessages() {
    }

    static int messageFor(final VaultException.Reason reason) {
        switch (reason) {
            case BAD_PASSWORD_OR_DAMAGED:
                return R.string.toast_wrongPassword;
            case NOT_A_VAULT:
                return R.string.toast_notVault;
            case TOO_LARGE:
                return R.string.toast_tooLarge;
            case LIMIT_REACHED:
                return R.string.toast_vaultFull;
            case IO:
                return R.string.toast_IOError;
            case ROLLED_BACK:
                return R.string.toast_rolledBack;
            default:
                return R.string.toast_fatalError;
        }
    }
}
