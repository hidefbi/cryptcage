package com.cryptcage.utils;

import java.util.Arrays;

/**
 * The derived key of an open vault, together with the salt and cost it was made with.
 * Only the key material lives here; the password itself is never kept.
 */
public final class VaultKey {

    final byte[] salt;
    final int iterations;
    private byte[] key;

    VaultKey(final byte[] salt, final int iterations, final byte[] key) {
        this.salt = salt;
        this.iterations = iterations;
        this.key = key;
    }

    byte[] material() {
        if (key == null) {
            throw new IllegalStateException("key already wiped");
        }
        return key;
    }

    /** Overwrites the key bytes. Call it only after no save is still using them. */
    public void wipe() {
        if (key != null) {
            Arrays.fill(key, (byte) 0);
            key = null;
        }
    }
}
