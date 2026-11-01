/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.util.Locale;

/**
 * Rules for passwords chosen when a vault is created, its password is changed, or an export is
 * protected. Passwords accepted by older versions still open normally; only new ones are checked.
 *
 * <p>A password is refused when it is shorter than {@link #MIN_LENGTH}, when it is a common
 * password or a common word with simple substitutions (for example {@code P@ssw0rd2026}), or when
 * it follows an obvious pattern: a repeated character, a run such as {@code 1234} or
 * {@code dcba}, a keyboard row such as {@code qwer}, or a block repeated over and over.
 */
public final class PasswordPolicy {

    /** Minimum length, counted in characters (code points). */
    public static final int MIN_LENGTH = 12;

    /** This many equal characters in a row is refused. */
    private static final int REPEAT_LIMIT = 4;

    /** A counting or keyboard run of this many characters is refused. */
    private static final int RUN_LIMIT = 4;

    /** A common word is also refused when it appears inside a longer password of this size. */
    private static final int CONTAINS_MIN = 7;

    private static final String[] KEYBOARD_ROWS = {
        "1234567890",
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm",
        "qwertzuiop",
        "azertyuiop",
    };

    private static final String[] COMMON_WORDS = {
        "password", "qwerty", "qwertyuiop", "letmein", "welcome", "monkey", "dragon",
        "sunshine", "iloveyou", "princess", "football", "baseball", "superman", "starwars",
        "trustno", "shadow", "master", "batman", "michael", "jessica", "computer", "internet",
        "freedom", "whatever", "login", "admin", "administrator", "secret", "ninja", "hello",
        "hottie", "loveme", "lovely", "mustang", "access", "flower", "summer", "winter",
        "soccer", "basketball", "hockey", "pepper", "ginger", "cookie", "chocolate", "cheese",
        "banana", "orange", "purple", "yankees", "jordan", "harley", "ranger", "thomas",
        "robert", "jennifer", "charlie", "andrew", "daniel", "joshua", "hunter", "buster",
        "tigger", "killer", "matrix", "asdfgh", "asdfghjkl", "zxcvbnm", "qazwsx", "passwd",
        "changeme", "default", "guest", "toor", "abcdef", "abcdefg", "forever", "nothing",
        "iloveu", "trustme", "happy", "sexy", "baby", "angel", "tiger", "maggie",
        "woaini", "wodemima", "mima", "jiayou", "aini",
    };

    public enum Result {
        OK,
        TOO_SHORT,
        COMMON,
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
        if (isCommon(password)) {
            return Result.COMMON;
        }
        if (isPredictable(password)) {
            return Result.TOO_SIMPLE;
        }
        return Result.OK;
    }

    /**
     * True when the letters of the password, after undoing common substitutions and dropping
     * digits and symbols, are a common word. "P@ssw0rd2026" and "iloveyou!!" both match.
     */
    static boolean isCommon(final String password) {
        final String core = core(password);
        if (core.isEmpty()) {
            return false;
        }
        for (String word : COMMON_WORDS) {
            if (core.equals(word)) {
                return true;
            }
            if (word.length() >= CONTAINS_MIN && core.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** True when the password follows one of the obvious patterns. */
    static boolean isPredictable(final String password) {
        final String lower = password.toLowerCase(Locale.ROOT);
        return hasRepeat(lower, REPEAT_LIMIT)
                || hasSequence(lower, RUN_LIMIT)
                || hasKeyboardRun(lower, RUN_LIMIT)
                || isBlockRepeat(lower);
    }

    /** Lower-case letters only, with the usual look-alike characters turned back into letters. */
    private static String core(final String password) {
        final StringBuilder out = new StringBuilder(password.length());
        final String lower = password.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            final char c = lower.charAt(i);
            final char letter;
            switch (c) {
                case '0':
                    letter = 'o';
                    break;
                case '1':
                case '!':
                    letter = 'i';
                    break;
                case '3':
                    letter = 'e';
                    break;
                case '4':
                case '@':
                    letter = 'a';
                    break;
                case '5':
                case '$':
                    letter = 's';
                    break;
                case '7':
                    letter = 't';
                    break;
                default:
                    letter = c;
                    break;
            }
            if (letter >= 'a' && letter <= 'z') {
                out.append(letter);
            }
        }
        return out.toString();
    }

    private static boolean hasRepeat(final String s, final int count) {
        int run = 0;
        for (int i = 0; i < s.length(); i++) {
            run = i > 0 && s.charAt(i) == s.charAt(i - 1) ? run + 1 : 1;
            if (run >= count) {
                return true;
            }
        }
        return false;
    }

    /** A run of letters or digits, each one step up or down from the one before. */
    private static boolean hasSequence(final String s, final int count) {
        int run = 1;
        int direction = 0;
        for (int i = 1; i < s.length(); i++) {
            final char previous = s.charAt(i - 1);
            final char current = s.charAt(i);
            final int step = current - previous;
            final boolean stepped = (step == 1 || step == -1)
                    && isAlphanumeric(previous) && isAlphanumeric(current);
            if (stepped && (direction == 0 || step == direction)) {
                direction = step;
                run++;
            } else if (stepped) {
                direction = step;
                run = 2;
            } else {
                direction = 0;
                run = 1;
            }
            if (run >= count) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAlphanumeric(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static boolean hasKeyboardRun(final String s, final int count) {
        for (String row : KEYBOARD_ROWS) {
            final String reversed = new StringBuilder(row).reverse().toString();
            for (int i = 0; i + count <= s.length(); i++) {
                final String window = s.substring(i, i + count);
                if (row.contains(window) || reversed.contains(window)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True when the whole password is one shorter piece repeated at least twice, such as abcabc. */
    private static boolean isBlockRepeat(final String s) {
        final int length = s.length();
        for (int size = 1; size <= length / 2; size++) {
            if (length % size != 0) {
                continue;
            }
            final String block = s.substring(0, size);
            boolean same = true;
            for (int i = size; i < length && same; i += size) {
                same = s.startsWith(block, i);
            }
            if (same) {
                return true;
            }
        }
        return false;
    }
}
