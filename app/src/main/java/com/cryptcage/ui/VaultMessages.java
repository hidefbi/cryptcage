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
            default:
                return R.string.toast_fatalError;
        }
    }
}
