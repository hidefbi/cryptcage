/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PasswordPolicyTest {

    @Test
    public void shortPasswordsAreRejected() {
        assertEquals(PasswordPolicy.Result.TOO_SHORT, PasswordPolicy.check("a"));
        assertEquals(PasswordPolicy.Result.TOO_SHORT, PasswordPolicy.check("Abc123!xyz"));
        assertEquals(PasswordPolicy.Result.TOO_SHORT, PasswordPolicy.check(null));
    }

    @Test
    public void commonPasswordsAreRejectedEvenWithDigitsAndSymbols() {
        assertEquals(PasswordPolicy.Result.COMMON, PasswordPolicy.check("password1234"));
        assertEquals(PasswordPolicy.Result.COMMON, PasswordPolicy.check("iloveyou2026"));
        assertEquals(PasswordPolicy.Result.COMMON, PasswordPolicy.check("P@ssw0rd2026!"));
    }

    @Test
    public void obviousPatternsAreRejected() {
        assertEquals(PasswordPolicy.Result.TOO_SIMPLE, PasswordPolicy.check("aaaaaaaaaaaa"));
        assertEquals(PasswordPolicy.Result.TOO_SIMPLE, PasswordPolicy.check("zyxwvutsrqpo"));
        assertEquals(PasswordPolicy.Result.TOO_SIMPLE, PasswordPolicy.check("1234abcd5678"));
        assertEquals(PasswordPolicy.Result.TOO_SIMPLE, PasswordPolicy.check("zqwerxcvbnmq"));
        assertEquals(PasswordPolicy.Result.TOO_SIMPLE, PasswordPolicy.check("123123123123"));
    }

    @Test
    public void singleCharacterKindIsAcceptedWhenNotPredictable() {
        assertEquals(PasswordPolicy.Result.OK, PasswordPolicy.check("lowercaseonly"));
    }

    @Test
    public void passphrasesAreAccepted() {
        assertEquals(PasswordPolicy.Result.OK, PasswordPolicy.check("correct horse 42"));
        assertEquals(PasswordPolicy.Result.OK, PasswordPolicy.check("mySecretHorse2026"));
    }

    @Test
    public void chineseLengthCountsCharacters() {
        assertEquals(PasswordPolicy.Result.TOO_SHORT, PasswordPolicy.check("我的笔记密码"));
        assertEquals(PasswordPolicy.Result.OK, PasswordPolicy.check("我的笔记密码就是这一串字符很长好"));
    }
}
