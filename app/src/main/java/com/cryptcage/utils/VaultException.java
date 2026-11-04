/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

/** Something went wrong while opening or writing a vault file. */
public class VaultException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** Wrong password, or the file was changed on disk. The two cannot be told apart. */
        BAD_PASSWORD_OR_DAMAGED,
        /** The file is not a vault at all. */
        NOT_A_VAULT,
        /** The file is larger than we are willing to load. */
        TOO_LARGE,
        /** Disk or file system error. */
        IO,
        /** Something unexpected inside the crypto layer. */
        INTERNAL,
        /** The vault has reached its content limit, so the change was not made. */
        LIMIT_REACHED,
        /** The change is not allowed, for example because the entry is read-only. */
        REJECTED,
        /** The file is older than the last version this device saved or opened, so it is not opened. */
        ROLLED_BACK
    }

    private final Reason reason;

    public VaultException(final Reason reason, final Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
