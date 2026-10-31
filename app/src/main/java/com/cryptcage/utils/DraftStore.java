/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Sealed drafts, one file per note. The files hold only what the vault sealed, so they are
 * unreadable without the vault's password. Each vault and each note gets its own file name.
 */
public final class DraftStore {

    /** Name for the draft of a note that has not been saved yet. */
    public static final String NEW_NOTE = "new";

    private final File dir;

    public DraftStore(final File dir) {
        this.dir = dir;
    }

    /** The file name for a note in a vault. The vault path is hashed, so the path is not visible. */
    public static String nameFor(final String vaultPath, final long noteId) {
        return hash(vaultPath) + "-" + (noteId < 0 ? NEW_NOTE : Long.toString(noteId));
    }

    /** Writes the sealed bytes. A partial write never replaces an earlier draft. */
    public void save(final String name, final byte[] sealed) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("draft folder");
        }
        final File target = new File(dir, name);
        final File temp = new File(dir, name + ".tmp");
        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(sealed);
            out.flush();
            ((FileOutputStream) out).getFD().sync();
        }
        if (!temp.renameTo(target)) {
            target.delete();
            if (!temp.renameTo(target)) {
                throw new IOException("draft rename");
            }
        }
    }

    /** The sealed bytes, or null when there is no draft. */
    public byte[] load(final String name) throws IOException {
        final File file = new File(dir, name);
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            final byte[] out = new byte[(int) file.length()];
            int read = 0;
            while (read < out.length) {
                final int n = in.read(out, read, out.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return out;
        }
    }

    public void delete(final String name) {
        new File(dir, name).delete();
        new File(dir, name + ".tmp").delete();
    }

    private static String hash(final String text) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
