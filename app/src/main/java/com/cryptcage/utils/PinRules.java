/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * The rules for the PIN that unlocks a vault without typing the master password. The PIN is
 * checked against a salted hash, and a few wrong tries remove it.
 */
public final class PinRules {

    public static final int MIN_DIGITS = 4;
    public static final int MAX_DIGITS = 8;
    public static final int MAX_FAILURES = 5;

    private PinRules() {
    }

    /** True for 4 to 8 ASCII digits. */
    public static boolean isValid(final String pin) {
        if (pin == null || pin.length() < MIN_DIGITS || pin.length() > MAX_DIGITS) {
            return false;
        }
        for (int i = 0; i < pin.length(); i++) {
            final char c = pin.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** True once the failures reach the limit. The PIN is then removed, but the database is not. */
    public static boolean shouldWipe(final int failures) {
        return failures >= MAX_FAILURES;
    }

    public static int attemptsLeft(final int failures) {
        return Math.max(0, MAX_FAILURES - failures);
    }

    /** PBKDF2 of the PIN with the salt. Only the hash is stored, never the PIN. */
    public static byte[] hash(final char[] pin, final byte[] salt) throws GeneralSecurityException {
        return Cryptox.pbkdf2Sha256(pin, salt, Cryptox.DEFAULT_ITERATIONS);
    }

    /** Compares two hashes in constant time. */
    public static boolean matches(final byte[] expected, final byte[] actual) {
        return MessageDigest.isEqual(expected, actual);
    }
}
