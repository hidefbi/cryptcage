/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Unsaved text of one note being edited. It is encoded, then sealed by the vault before it is stored. */
public final class Draft {

    private static final int VERSION = 1;

    public final long noteId;
    public final String title;
    public final String tags;
    public final String body;

    public Draft(final long noteId, final String title, final String tags, final String body) {
        this.noteId = noteId;
        this.title = title;
        this.tags = tags;
        this.body = body;
    }

    public byte[] encode() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeLong(noteId);
            writeText(out, title);
            writeText(out, tags);
            writeText(out, body);
        }
        return bytes.toByteArray();
    }

    /** Reads a draft made by {@link #encode}. Throws IOException for anything it does not recognise. */
    public static Draft decode(final byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readUnsignedByte() != VERSION) {
                throw new IOException("draft version");
            }
            final long id = in.readLong();
            final String title = readText(in);
            final String tags = readText(in);
            final String body = readText(in);
            return new Draft(id, title, tags, body);
        }
    }

    private static void writeText(final DataOutputStream out, final String text) throws IOException {
        final byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    private static String readText(final DataInputStream in) throws IOException {
        final int length = in.readInt();
        if (length < 0 || length > in.available()) {
            throw new IOException("draft length");
        }
        final byte[] utf8 = new byte[length];
        in.readFully(utf8);
        return new String(utf8, StandardCharsets.UTF_8);
    }
}
