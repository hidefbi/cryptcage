/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Whole-vault backups. A backup is a byte-for-byte copy of the vault file. It is encrypted with
 * the same master password as the vault, so it can be imported again with the usual import and
 * opened with that password. Nothing is decrypted or re-encrypted on the way.
 */
public final class VaultBackup {

    /** Suffix added to the vault name when a backup is suggested, before the extension. */
    public static final String SUFFIX = "-backup";

    private static final int BUFFER = 64 * 1024;

    /** Length and SHA-256 digest of some bytes. */
    public static final class Digest {
        public final long length;
        public final String sha256;

        Digest(final long length, final String sha256) {
            this.length = length;
            this.sha256 = sha256;
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof Digest)) {
                return false;
            }
            final Digest that = (Digest) other;
            return length == that.length && sha256.equals(that.sha256);
        }

        @Override
        public int hashCode() {
            return sha256.hashCode();
        }
    }

    private VaultBackup() {
    }

    /** The file name offered when saving a backup, for example "work-backup.ncf". */
    public static String suggestedName(final String vaultFileName) {
        return StringMethods.getNameDB(vaultFileName) + SUFFIX + StringMethods.EXTENSION;
    }

    /** Copies the input to the output and returns what was written. The output is not closed. */
    public static Digest copy(final InputStream in, final OutputStream out) throws IOException {
        final MessageDigest digest = newDigest();
        final byte[] buffer = new byte[BUFFER];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            digest.update(buffer, 0, read);
            total += read;
        }
        out.flush();
        return new Digest(total, hex(digest.digest()));
    }

    /** Reads the input to the end and returns its length and digest. */
    public static Digest digest(final InputStream in) throws IOException {
        final MessageDigest digest = newDigest();
        final byte[] buffer = new byte[BUFFER];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
            total += read;
        }
        return new Digest(total, hex(digest.digest()));
    }

    private static MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available", e);
        }
    }

    private static String hex(final byte[] bytes) {
        final StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }
}
