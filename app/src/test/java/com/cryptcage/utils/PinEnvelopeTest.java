/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import org.junit.Test;

public class PinEnvelopeTest {

    private static byte[] salt(final int seed) {
        final byte[] salt = new byte[32];
        Arrays.fill(salt, (byte) seed);
        return salt;
    }

    @Test
    public void verifierOpensOnlyWithTheRightPin() throws GeneralSecurityException {
        final byte[] salt = salt(1);
        final byte[] key = PinEnvelope.deriveKey("1234".toCharArray(), salt);
        final byte[] verifier = PinEnvelope.verifier(key);
        assertTrue(PinEnvelope.verifies(PinEnvelope.deriveKey("1234".toCharArray(), salt), verifier));
        assertFalse(PinEnvelope.verifies(PinEnvelope.deriveKey("1235".toCharArray(), salt), verifier));
        assertFalse(PinEnvelope.verifies(PinEnvelope.deriveKey("1234".toCharArray(), salt(2)), verifier));
    }

    @Test
    public void vaultWrapRoundTrips() throws GeneralSecurityException {
        final byte[] key = PinEnvelope.deriveKey("1234".toCharArray(), salt(3));
        final byte[] inner = {1, 2, 3, 4, 5};
        final byte[] outer = PinEnvelope.wrapForVault(key, "Work", inner);
        assertArrayEquals(inner, PinEnvelope.unwrapForVault(key, "Work", outer));
    }

    @Test
    public void vaultWrapFailsForAnotherVaultOrAnotherKey() throws GeneralSecurityException {
        final byte[] key = PinEnvelope.deriveKey("1234".toCharArray(), salt(4));
        final byte[] outer = PinEnvelope.wrapForVault(key, "Work", new byte[]{9, 9});
        try {
            PinEnvelope.unwrapForVault(key, "Home", outer);
            fail("another vault must not open the copy");
        } catch (GeneralSecurityException expected) {
            // expected
        }
        try {
            PinEnvelope.unwrapForVault(PinEnvelope.deriveKey("0000".toCharArray(), salt(4)), "Work", outer);
            fail("another PIN must not open the copy");
        } catch (GeneralSecurityException expected) {
            // expected
        }
    }
}
