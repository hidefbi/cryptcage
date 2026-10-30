/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

/** A vault container could not be read or written. */
final class StoreException extends Exception {

    private static final long serialVersionUID = 1L;

    enum Reason {
        NOT_A_STORE,
        AUTH_FAILED,
        CORRUPT,
        IO,
        EXISTS,
        BAD_ARGUMENT,
        LIMIT_REACHED,
        INTERNAL
    }

    private final Reason reason;

    StoreException(final Reason reason, final String detail) {
        super(detail == null ? reason.name() : reason.name() + ": " + detail);
        this.reason = reason;
    }

    Reason reason() {
        return reason;
    }
}
