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
    public void singleClassIsRejectedUnlessLong() {
        assertEquals(PasswordPolicy.Result.TOO_SIMPLE, PasswordPolicy.check("abcdefghijkl"));
        assertEquals(PasswordPolicy.Result.OK, PasswordPolicy.check("abcdefghijklmnop"));
    }

    @Test
    public void mixedPasswordIsAccepted() {
        assertEquals(PasswordPolicy.Result.OK, PasswordPolicy.check("correct horse 42"));
    }

    @Test
    public void chineseLengthCountsCharacters() {
        assertEquals(PasswordPolicy.Result.TOO_SHORT, PasswordPolicy.check("我的笔记密码"));
        assertEquals(PasswordPolicy.Result.OK, PasswordPolicy.check("我的笔记密码就是这一串字符很长好"));
    }
}
