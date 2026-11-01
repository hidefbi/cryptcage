/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

/**
 * A live reading of a password while it is typed. The levels follow {@link PasswordPolicy}: level
 * 3 and 4 are the ones the policy accepts, and levels 1 and 2 are not. The hint says what would
 * make the password better.
 */
public final class PasswordStrength {

    public enum Hint {
        NONE,
        SHORT,
        COMMON,
        PREDICTABLE,
        LONGER
    }

    /** Length from which a password is reported as strong. */
    private static final int STRONG_LENGTH = 16;

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
        switch (PasswordPolicy.check(value)) {
            case TOO_SHORT:
                return new Reading(1, Hint.SHORT, PasswordPolicy.MIN_LENGTH - length);
            case COMMON:
                return new Reading(1, Hint.COMMON, 0);
            case TOO_SIMPLE:
                return new Reading(2, Hint.PREDICTABLE, 0);
            default:
                break;
        }
        if (length >= STRONG_LENGTH) {
            return new Reading(4, Hint.NONE, 0);
        }
        return new Reading(3, Hint.LONGER, 0);
    }
}
