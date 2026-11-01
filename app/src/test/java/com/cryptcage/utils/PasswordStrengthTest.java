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
    public void commonPasswordIsWeakWithCommonHint() {
        final PasswordStrength.Reading reading = PasswordStrength.read("password1234");
        assertEquals(1, reading.level);
        assertEquals(PasswordStrength.Hint.COMMON, reading.hint);
    }

    @Test
    public void predictablePatternIsFairWithPredictableHint() {
        final PasswordStrength.Reading reading = PasswordStrength.read("zyxwvutsrqpo");
        assertEquals(2, reading.level);
        assertEquals(PasswordStrength.Hint.PREDICTABLE, reading.hint);
    }

    @Test
    public void acceptedBelowSixteenCharactersIsGoodWithLongerHint() {
        final PasswordStrength.Reading reading = PasswordStrength.read("lowercaseonly");
        assertEquals(3, reading.level);
        assertEquals(PasswordStrength.Hint.LONGER, reading.hint);
    }

    @Test
    public void sixteenCharactersIsStrong() {
        final PasswordStrength.Reading reading = PasswordStrength.read("mySecretHorse2026");
        assertEquals(4, reading.level);
        assertEquals(PasswordStrength.Hint.NONE, reading.hint);
    }

    @Test
    public void levelsThreeAndFourAgreeWithThePolicy() {
        final String[] samples = {
            "", "a", "abcdefghijk", "abcdefghijkl", "password1234", "lowercaseonly",
            "mySecretHorse2026", "1234567890123456", "passw0rd-long-enough",
            "中文密码测试一二三四五六七八"
        };
        for (final String sample : samples) {
            final boolean accepted = PasswordPolicy.check(sample) == PasswordPolicy.Result.OK;
            final boolean strongEnough = PasswordStrength.read(sample).level >= 3;
            assertEquals(sample, accepted, strongEnough);
        }
    }

    @Test
    public void countsCodePointsNotUnits() {
        final String twelveEmoji = "😀😀😀😀😀😀"
                + "😀😀😀😀😀😀";
        assertTrue(PasswordStrength.read(twelveEmoji).missing == 0);
    }
}
