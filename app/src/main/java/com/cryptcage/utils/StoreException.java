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
