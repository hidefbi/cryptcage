/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.GeneralSecurityException;

import org.junit.Test;

public class PinRulesTest {

    @Test
    public void acceptsFourToEightDigits() {
        assertTrue(PinRules.isValid("1234"));
        assertTrue(PinRules.isValid("12345678"));
        assertTrue(PinRules.isValid("0000"));
    }

    @Test
    public void rejectsLengthsOutsideTheRange() {
        assertFalse(PinRules.isValid(null));
        assertFalse(PinRules.isValid(""));
        assertFalse(PinRules.isValid("123"));
        assertFalse(PinRules.isValid("123456789"));
    }

    @Test
    public void rejectsAnythingButAsciiDigits() {
        assertFalse(PinRules.isValid("12a4"));
        assertFalse(PinRules.isValid("12 4"));
        assertFalse(PinRules.isValid("1234-"));
        assertFalse(PinRules.isValid("\u0661\u0662\u0663\u0664"));
    }

    @Test
    public void wipesOnTheFifthFailure() {
        assertFalse(PinRules.shouldWipe(PinRules.MAX_FAILURES - 1));
        assertTrue(PinRules.shouldWipe(PinRules.MAX_FAILURES));
        assertEquals(PinRules.MAX_FAILURES, PinRules.attemptsLeft(0));
        assertEquals(1, PinRules.attemptsLeft(PinRules.MAX_FAILURES - 1));
        assertEquals(0, PinRules.attemptsLeft(PinRules.MAX_FAILURES + 2));
    }

    @Test
    public void hashDependsOnPinAndSalt() throws GeneralSecurityException {
        final byte[] salt = new byte[32];
        final byte[] other = new byte[32];
        other[0] = 1;
        final byte[] first = PinRules.hash("1234".toCharArray(), salt);
        assertArrayEquals(first, PinRules.hash("1234".toCharArray(), salt));
        assertFalse(PinRules.matches(first, PinRules.hash("1235".toCharArray(), salt)));
        assertFalse(PinRules.matches(first, PinRules.hash("1234".toCharArray(), other)));
        assertTrue(PinRules.matches(first, PinRules.hash("1234".toCharArray(), salt)));
    }
}
