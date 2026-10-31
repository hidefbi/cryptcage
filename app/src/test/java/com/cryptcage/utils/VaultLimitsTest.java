/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VaultLimitsTest {

    private static final char[] PASSWORD = "correct horse 42".toCharArray();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void noteBodyAboveTheLimitIsRefused() throws Exception {
        final String atLimit = repeat('a', (int) VaultSession.MAX_NOTE_BYTES);
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "a.cc3"), PASSWORD)) {
            final long id = vault.addNote("t", atLimit, "");
            try {
                vault.updateNote(id, "t", atLimit + "b", "");
                fail("body above the limit was written");
            } catch (VaultException expected) {
                assertEquals(VaultException.Reason.TOO_LARGE, expected.getReason());
            }
            assertEquals(atLimit.length(), vault.body(id).length());
        }
    }

    @Test
    public void limitCountsUtf8Bytes() throws Exception {
        // Each character takes three bytes, so this is over the limit with fewer characters.
        final String tooLarge = repeat('\u4e2d', (int) (VaultSession.MAX_NOTE_BYTES / 3) + 1);
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "b.cc3"), PASSWORD)) {
            try {
                vault.addNote("t", tooLarge, "");
                fail("multi-byte body above the limit was written");
            } catch (VaultException expected) {
                assertEquals(VaultException.Reason.TOO_LARGE, expected.getReason());
            }
        }
    }

    private static String repeat(final char c, final int count) {
        final char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }
}
