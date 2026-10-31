/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClipboardExpiryTest {

    private static final long NOW = 1_000_000L;

    @Test
    public void clearsWhenTheTimeHasPassed() {
        assertTrue(ClipboardExpiry.isDue("a", "a", NOW, NOW));
        assertTrue(ClipboardExpiry.isDue("a", "a", NOW - 1, NOW));
    }

    @Test
    public void keepsTheCopyUntilTheTimeHasPassed() {
        assertFalse(ClipboardExpiry.isDue("a", "a", NOW + 1, NOW));
    }

    @Test
    public void anOlderTimerNeverClearsANewerCopy() {
        assertFalse(ClipboardExpiry.isDue("new", "old", NOW - 1, NOW));
    }

    @Test
    public void nothingIsStoredMeansNothingIsCleared() {
        assertFalse(ClipboardExpiry.isDue(null, "a", NOW - 1, NOW));
        assertFalse(ClipboardExpiry.isDue("a", null, NOW - 1, NOW));
        assertFalse(ClipboardExpiry.isDue("a", "a", 0L, NOW));
    }
}
