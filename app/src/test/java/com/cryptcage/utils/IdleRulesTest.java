/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IdleRulesTest {

    private static final long MINUTE = 60_000L;

    @Test
    public void locksOnceTheIdleTimeIsReached() {
        assertFalse(IdleRules.isExpired(MINUTE - 1, 5 * MINUTE));
        assertTrue(IdleRules.isExpired(5 * MINUTE, 5 * MINUTE));
        assertTrue(IdleRules.isExpired(6 * MINUTE, 5 * MINUTE));
    }

    @Test
    public void zeroTimeoutNeverLocksForIdleness() {
        assertFalse(IdleRules.isExpired(Long.MAX_VALUE, 0));
        assertEquals(0, IdleRules.remaining(MINUTE, 0));
    }

    @Test
    public void remainingCountsDownAndStopsAtZero() {
        assertEquals(4 * MINUTE, IdleRules.remaining(MINUTE, 5 * MINUTE));
        assertEquals(0, IdleRules.remaining(7 * MINUTE, 5 * MINUTE));
    }
}
