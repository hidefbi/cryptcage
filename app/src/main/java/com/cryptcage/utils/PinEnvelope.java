/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * The PIN layer of a saved password. The PIN key comes from the PIN and a salt. It checks the PIN
 * against a verifier, and it wraps each vault's saved password (already wrapped by the device
 * key) so that the device key alone is not enough to open it. The vault name is bound to each
 * wrap, so a saved copy cannot be moved to another vault.
 */
public final class PinEnvelope {

    private static final byte[] VERIFIER_PLAIN = "cryptcage-pin-ok".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VERIFIER_AAD = "cryptcage-pin-verifier".getBytes(StandardCharsets.UTF_8);

    private PinEnvelope() {
    }

    /** The PIN key: PBKDF2 of the PIN with the salt. The caller wipes it when done. */
    public static byte[] deriveKey(final char[] pin, final byte[] salt) throws GeneralSecurityException {
        return PinRules.hash(pin, salt);
    }

    /** A value that only the right PIN key can open. Stored once, when the PIN is set. */
    public static byte[] verifier(final byte[] key) throws GeneralSecurityException {
        return Cryptox.seal(key, VERIFIER_AAD, VERIFIER_PLAIN);
    }

    /** True when the key opens the verifier. A wrong key is not an error here, only false. */
    public static boolean verifies(final byte[] key, final byte[] verifier) {
        try {
            return Arrays.equals(Cryptox.open(key, VERIFIER_AAD, verifier), VERIFIER_PLAIN);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }

    /** Wraps a vault's inner copy with the PIN key. The vault name is authenticated. */
    public static byte[] wrapForVault(final byte[] key, final String vault, final byte[] inner)
            throws GeneralSecurityException {
        return Cryptox.seal(key, aad(vault), inner);
    }

    /** Opens the outer wrap. Throws when the PIN key or the vault name is wrong. */
    public static byte[] unwrapForVault(final byte[] key, final String vault, final byte[] outer)
            throws GeneralSecurityException {
        return Cryptox.open(key, aad(vault), outer);
    }

    private static byte[] aad(final String vault) {
        return ("cryptcage-vault:" + vault).getBytes(StandardCharsets.UTF_8);
    }
}
