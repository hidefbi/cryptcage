package com.notecrypt.utils;

/**
 * Rules for passwords chosen when a vault is created or its password is changed.
 * Passwords accepted by older versions still open normally; only new ones are checked.
 */
public final class PasswordPolicy {

    /** Minimum length, counted in characters (code points). */
    public static final int MIN_LENGTH = 12;
    /** Passwords this long are accepted even if they use only one kind of character. */
    public static final int LONG_ENOUGH = 16;

    public enum Result {
        OK,
        TOO_SHORT,
        TOO_SIMPLE
    }

    private PasswordPolicy() {
    }

    public static Result check(final String password) {
        if (password == null) {
            return Result.TOO_SHORT;
        }
        final int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            return Result.TOO_SHORT;
        }
        if (length >= LONG_ENOUGH) {
            return Result.OK;
        }
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean other = false;
        for (int i = 0; i < password.length(); i++) {
            final char c = password.charAt(i);
            if (c >= 'a' && c <= 'z') {
                lower = true;
            } else if (c >= 'A' && c <= 'Z') {
                upper = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else {
                other = true;
            }
        }
        int kinds = 0;
        if (lower) {
            kinds++;
        }
        if (upper) {
            kinds++;
        }
        if (digit) {
            kinds++;
        }
        if (other) {
            kinds++;
        }
        return kinds >= 2 ? Result.OK : Result.TOO_SIMPLE;
    }
}
