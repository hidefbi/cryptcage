/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

/**
 * A live reading of a password while it is typed. The levels follow {@link PasswordPolicy}: levels
 * 3 and 4 are the ones the policy accepts, and levels 1 and 2 are not. The hint says what would
 * make the password better.
 */
public final class PasswordStrength {

    public enum Hint {
        NONE,
        SHORT,
        MIX,
        LONGER
    }

    public static final class Reading {
        /** 0 for an empty field, 1 to 4 otherwise. */
        public final int level;
        public final Hint hint;
        /** Characters still missing before the minimum length, used with {@link Hint#SHORT}. */
        public final int missing;

        Reading(final int level, final Hint hint, final int missing) {
            this.level = level;
            this.hint = hint;
            this.missing = missing;
        }
    }

    private PasswordStrength() {
    }

    public static Reading read(final String password) {
        final String value = password == null ? "" : password;
        final int length = value.codePointCount(0, value.length());
        if (length == 0) {
            return new Reading(0, Hint.NONE, 0);
        }
        if (length < PasswordPolicy.MIN_LENGTH) {
            return new Reading(1, Hint.SHORT, PasswordPolicy.MIN_LENGTH - length);
        }
        if (length >= PasswordPolicy.LONG_ENOUGH) {
            return new Reading(4, Hint.NONE, 0);
        }
        if (kinds(value) >= 2) {
            return new Reading(3, Hint.LONGER, 0);
        }
        return new Reading(2, Hint.MIX, 0);
    }

    /** Counts the character classes used: lower case, upper case, digits and everything else. */
    private static int kinds(final String value) {
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean other = false;
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
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
        int count = 0;
        if (lower) {
            count++;
        }
        if (upper) {
            count++;
        }
        if (digit) {
            count++;
        }
        if (other) {
            count++;
        }
        return count;
    }
}
