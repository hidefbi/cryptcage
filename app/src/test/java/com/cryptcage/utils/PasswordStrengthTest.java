/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordStrengthTest {

    @Test
    public void emptyFieldHasNoLevel() {
        assertEquals(0, PasswordStrength.read("").level);
        assertEquals(0, PasswordStrength.read(null).level);
    }

    @Test
    public void shortPasswordsReportHowManyCharactersAreMissing() {
        final PasswordStrength.Reading one = PasswordStrength.read("a");
        assertEquals(1, one.level);
        assertEquals(PasswordStrength.Hint.SHORT, one.hint);
        assertEquals(PasswordPolicy.MIN_LENGTH - 1, one.missing);
        assertEquals(1, PasswordStrength.read("abcdefghijk").missing);
    }

    @Test
    public void longEnoughWithOneCharacterKindIsFair() {
        final PasswordStrength.Reading reading = PasswordStrength.read("abcdefghijkl");
        assertEquals(2, reading.level);
        assertEquals(PasswordStrength.Hint.MIX, reading.hint);
    }

    @Test
    public void twoKindsAtTwelveCharactersIsGood() {
        final PasswordStrength.Reading reading = PasswordStrength.read("abcdefghijk1");
        assertEquals(3, reading.level);
        assertEquals(PasswordStrength.Hint.LONGER, reading.hint);
    }

    @Test
    public void sixteenCharactersIsStrongWhateverTheKinds() {
        final PasswordStrength.Reading reading = PasswordStrength.read("abcdefghijklmnop");
        assertEquals(4, reading.level);
        assertEquals(PasswordStrength.Hint.NONE, reading.hint);
    }

    @Test
    public void levelsThreeAndFourAgreeWithThePolicy() {
        final String[] samples = {
            "", "a", "abcdefghijk", "abcdefghijkl", "abcdefghijk1", "ABCDEFGHIJKL",
            "abcdefghijklmnop", "1234567890123456", "passw0rd-long-enough", "\u4E2D\u6587\u5BC6\u7801\u6D4B\u8BD5\u4E00\u4E8C\u4E09\u56DB\u4E94\u516D\u4E03\u516B"
        };
        for (final String sample : samples) {
            final boolean accepted = PasswordPolicy.check(sample) == PasswordPolicy.Result.OK;
            final boolean strongEnough = PasswordStrength.read(sample).level >= 3;
            assertEquals(sample, accepted, strongEnough);
        }
    }

    @Test
    public void countsCodePointsNotUnits() {
        final String twelveEmoji = "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00"
                + "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00";
        assertTrue(PasswordStrength.read(twelveEmoji).missing == 0);
    }
}
