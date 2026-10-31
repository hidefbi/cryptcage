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

public class KdbxTypeTest {

    private static byte[] header(final int... bytes) {
        final byte[] out = new byte[64];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = (byte) bytes[i];
        }
        return out;
    }

    private static final int[] KDBX_SIGNATURE = {0x03, 0xD9, 0xA2, 0x9A, 0x67, 0xFB, 0x4B, 0xB5};

    @Test
    public void kdbxIsAcceptedAsDatabaseAndNotEditable() {
        final FileTypes.Check check = FileTypes.check("passwords.kdbx", header(KDBX_SIGNATURE));
        assertTrue(check.matches);
        assertEquals(FileTypes.Category.DATABASE, check.kind.category);
        assertFalse(check.editable());
        assertFalse(check.secret());
        assertFalse(check.text());
    }

    @Test
    public void renamedFileIsRejected() {
        assertFalse(FileTypes.check("passwords.kdbx", header(0x50, 0x4B, 0x03, 0x04)).matches);
        assertFalse(FileTypes.check("passwords.kdbx", header(0x03, 0xD9, 0xA2, 0x9A)).matches);
    }

    @Test
    public void cleanNameKeepsExtension() {
        final String cleaned = FileTypes.cleanFileName("My Db.KDBX");
        assertTrue(cleaned != null && cleaned.endsWith(".kdbx"));
    }
}
