package com.cryptcage.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a vault into the plain bytes that get encrypted, and back.
 *
 * Version 3 layout: version (int), next id (long), entry count (int), then per entry
 * id (long), starred (boolean), read-only (boolean), title, note, tags, kind (byte),
 * file name, and file data. Strings are length-prefixed UTF-8, with -1 for null.
 * Version 2 has no read-only flag; version 1 has neither the flag, kind, file name nor data.
 */
final class DatabaseCodec {

    static final int PAYLOAD_VERSION = 3;
    private static final int VERSION_WITH_FILES = 2;
    private static final int VERSION_TEXT_ONLY = 1;

    private static final int MAX_NOTES = 500_000;
    private static final int MAX_FIELD_BYTES = 16 * 1024 * 1024;

    private DatabaseCodec() {
    }

    static byte[] encode(final DatabaseForNotes db) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(buffer);
        out.writeInt(PAYLOAD_VERSION);
        out.writeLong(db.getPosition());
        final Map<Long, Notes> sorted = new TreeMap<>(db.rawNotes());
        out.writeInt(sorted.size());
        for (Map.Entry<Long, Notes> entry : sorted.entrySet()) {
            final Notes item = entry.getValue();
            out.writeLong(entry.getKey());
            out.writeBoolean(item.isStarred());
            out.writeBoolean(item.isReadOnly());
            writeString(out, item.getTitle());
            writeString(out, item.getNote());
            writeString(out, item.getTags());
            out.writeByte(item.getKind());
            writeString(out, item.getFileName());
            writeBytes(out, item.getData());
        }
        out.flush();
        return buffer.toByteArray();
    }

    static DatabaseForNotes decode(final byte[] data) throws IOException {
        final DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        final int version = in.readInt();
        if (version != PAYLOAD_VERSION && version != VERSION_WITH_FILES && version != VERSION_TEXT_ONLY) {
            throw new IOException("unknown payload version " + version);
        }
        final DatabaseForNotes db = new DatabaseForNotes();
        db.setPosition(in.readLong());
        final int count = in.readInt();
        if (count < 0 || count > MAX_NOTES) {
            throw new IOException("bad entry count " + count);
        }
        for (int i = 0; i < count; i++) {
            final long id = in.readLong();
            final boolean starred = in.readBoolean();
            final boolean readOnly = version == PAYLOAD_VERSION && in.readBoolean();
            final String title = readString(in);
            final String note = readString(in);
            final String tags = readString(in);
            int kind = Notes.KIND_NOTE;
            String fileName = null;
            byte[] fileData = null;
            if (version != VERSION_TEXT_ONLY) {
                kind = in.readByte();
                if (kind != Notes.KIND_NOTE && kind != Notes.KIND_FILE) {
                    throw new IOException("bad entry kind " + kind);
                }
                fileName = readString(in);
                fileData = readBytes(in);
            }
            if (db.rawNotes().containsKey(id)) {
                throw new IOException("duplicate entry id " + id);
            }
            final Notes item = new Notes(title, note);
            item.setTags(tags);
            item.setStarred(starred);
            item.setReadOnly(readOnly);
            item.setKind(kind);
            item.setFileName(fileName);
            item.setData(fileData);
            db.rawNotes().put(id, item);
        }
        db.rebuildIndex();
        db.fixPosition();
        return db;
    }

    private static void writeString(final DataOutputStream out, final String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        writeBytes(out, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(final DataOutputStream out, final byte[] value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(value.length);
        out.write(value);
    }

    private static String readString(final DataInputStream in) throws IOException {
        final byte[] bytes = readBytes(in);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(final DataInputStream in) throws IOException {
        final int length = in.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > MAX_FIELD_BYTES) {
            throw new IOException("bad field length " + length);
        }
        final byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }
}
