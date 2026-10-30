package com.cryptcage.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Notes and attached files on top of {@link BlockStore}.
 *
 * The index (titles, tags, flags and the list of blocks per entry) is small and is kept in
 * memory. Note bodies and file contents are split into blocks and read only when asked for.
 * Changes become durable only when {@link #commit()} is called.
 */
final class NoteRepository implements AutoCloseable {

    /** Total size of titles, tags, names, bodies and file contents. */
    static final long CONTENT_LIMIT = 1L << 30;

    /** Unused bytes below this amount are not worth a rewrite. */
    static final long COMPACT_MIN_WASTE = 64L << 20;

    private static final int INDEX_VERSION = 1;
    private static final int MAX_STRING_BYTES = 1 << 20;
    private static final int MAX_ENTRIES = 1_000_000;
    private static final int FLAG_STARRED = 1;
    private static final int FLAG_READ_ONLY = 2;

    static final int KIND_NOTE = 0;
    static final int KIND_FILE = 1;

    /** One note or file. The block list is what holds the bulk data. */
    static final class Entry {
        final long id;
        final int kind;
        String title;
        String tags;
        String fileName;
        boolean starred;
        boolean readOnly;
        long size;
        long[] blocks;

        Entry(final long id, final int kind, final String title, final String tags, final String fileName) {
            this.id = id;
            this.kind = kind;
            this.title = title;
            this.tags = tags;
            this.fileName = fileName;
            this.blocks = new long[0];
        }

        boolean isFile() {
            return kind == KIND_FILE;
        }
    }

    private BlockStore store;
    private final TreeMap<Long, Entry> entries = new TreeMap<>();
    private long nextEntry = 1;
    private long nextBlock = 1;
    private long limit = CONTENT_LIMIT;

    private NoteRepository(final BlockStore store) {
        this.store = store;
    }

    static NoteRepository create(final File file, final char[] password, final int iterations)
            throws StoreException {
        return new NoteRepository(BlockStore.create(file, password, iterations));
    }

    static NoteRepository open(final File file, final char[] password) throws StoreException {
        final BlockStore store = BlockStore.open(file, password);
        final NoteRepository repo = new NoteRepository(store);
        try {
            repo.decodeIndex(store.payload());
        } catch (StoreException e) {
            store.close();
            throw e;
        }
        return repo;
    }

    long addNote(final String title, final String body, final String tags) throws StoreException {
        return put(null, KIND_NOTE, title, tags, null, bytes(body));
    }

    void updateNote(final long id, final String title, final String body, final String tags)
            throws StoreException {
        put(requireWritable(id, KIND_NOTE), KIND_NOTE, title, tags, null, bytes(body));
    }

    long addFile(final String name, final byte[] data) throws StoreException {
        checkFile(name, data);
        return put(null, KIND_FILE, name, "", name, data);
    }

    void replaceFile(final long id, final String name, final byte[] data) throws StoreException {
        checkFile(name, data);
        put(requireWritable(id, KIND_FILE), KIND_FILE, name, "", name, data);
    }

    void setStarred(final long id, final boolean starred) throws StoreException {
        find(id).starred = starred;
    }

    void setReadOnly(final long id, final boolean readOnly) throws StoreException {
        find(id).readOnly = readOnly;
    }

    void delete(final long id) throws StoreException {
        final Entry entry = find(id);
        entries.remove(id);
        releaseBlocks(entry.blocks);
    }

    String readBody(final long id) throws StoreException {
        final Entry entry = find(id);
        if (entry.isFile()) {
            throw new StoreException(StoreException.Reason.BAD_ARGUMENT, "entry " + id + " is a file");
        }
        return new String(readBlocks(entry.blocks), StandardCharsets.UTF_8);
    }

    byte[] readFile(final long id) throws StoreException {
        final Entry entry = find(id);
        if (!entry.isFile()) {
            throw new StoreException(StoreException.Reason.BAD_ARGUMENT, "entry " + id + " is a note");
        }
        return readBlocks(entry.blocks);
    }

    /** Index data for one entry, or null if there is no such entry. Bodies are not loaded. */
    Entry entry(final long id) {
        return entries.get(id);
    }

    /** All index entries in id order. Bodies are not loaded. */
    List<Entry> list() {
        return new ArrayList<>(entries.values());
    }

    /** Bytes counted against {@link #CONTENT_LIMIT}. */
    long usedBytes() {
        long total = 0;
        for (Entry entry : entries.values()) {
            total += costOf(entry);
        }
        return total;
    }

    /** Number of blocks currently stored, including any not yet committed. */
    int blockCount() {
        return store.blockIds().size();
    }

    /** Saves every change made since the last commit. */
    void commit() throws StoreException {
        store.commit(encodeIndex());
    }

    /** Bytes the file takes up on disk. */
    long fileBytes() throws StoreException {
        return store.fileBytes();
    }

    /** True when unused records take up more than half the file and more than 64 MB. */
    boolean needsCompaction() throws StoreException {
        final long waste = store.fileBytes() - store.liveBytes();
        return waste > COMPACT_MIN_WASTE && waste > store.liveBytes();
    }

    /** Commits pending changes, then rewrites the file without the unused records. */
    void compact() throws StoreException {
        commit();
        store = store.compact();
    }

    /** Commits pending changes, then makes the file open with a new password. */
    void changePassword(final char[] password) throws StoreException {
        commit();
        store = store.rekey(password);
    }

    @Override
    public void close() {
        store.close();
    }

    /** Test hook: lowers the content limit so that the limit can be reached in a unit test. */
    void setLimitForTests(final long bytes) {
        this.limit = bytes;
    }

    private Entry find(final long id) throws StoreException {
        final Entry entry = entries.get(id);
        if (entry == null) {
            throw new StoreException(StoreException.Reason.BAD_ARGUMENT, "no entry with id " + id);
        }
        return entry;
    }

    private Entry requireWritable(final long id, final int kind) throws StoreException {
        final Entry entry = find(id);
        if (entry.kind != kind) {
            throw new StoreException(StoreException.Reason.BAD_ARGUMENT, "entry " + id + " has another kind");
        }
        if (entry.readOnly) {
            throw new StoreException(StoreException.Reason.BAD_ARGUMENT, "entry " + id + " is read-only");
        }
        return entry;
    }

    private static void checkFile(final String name, final byte[] data) throws StoreException {
        if (name == null || data == null || data.length > FileKinds.MAX_FILE_BYTES) {
            throw new StoreException(StoreException.Reason.BAD_ARGUMENT, "file name or size not acceptable");
        }
    }

    /**
     * Writes a new entry, or replaces the content of an existing one. Blocks for the new content
     * are written first, so a failure leaves the old content in place.
     */
    private long put(final Entry existing, final int kind, final String title, final String tags,
                     final String fileName, final byte[] data) throws StoreException {
        final String cleanTitle = title == null ? "" : title;
        final String cleanTags = DatabaseForNotes.cleanTags(tags);
        final String cleanName = fileName == null ? "" : fileName;
        final long newCost = textBytes(cleanTitle) + textBytes(cleanTags) + textBytes(cleanName) + data.length;
        final long oldCost = existing == null ? 0 : costOf(existing);
        final long exceptId = existing == null ? -1 : existing.id;
        if (usedBytesExcept(exceptId) + newCost > limit) {
            throw new StoreException(StoreException.Reason.LIMIT_REACHED, "vault content limit reached");
        }
        final long[] blocks = writeBlocks(data);
        final Entry target;
        if (existing == null) {
            target = new Entry(nextEntry, kind, cleanTitle, cleanTags, cleanName);
            nextEntry++;
            entries.put(target.id, target);
        } else {
            target = existing;
            target.title = cleanTitle;
            target.tags = cleanTags;
            target.fileName = cleanName;
        }
        final long[] old = target.blocks;
        target.blocks = blocks;
        target.size = data.length;
        releaseBlocks(old);
        return target.id;
    }

    private long usedBytesExcept(final long id) {
        long total = 0;
        for (Entry entry : entries.values()) {
            if (entry.id != id) {
                total += costOf(entry);
            }
        }
        return total;
    }

    private static long costOf(final Entry entry) {
        return entry.size + textBytes(entry.title) + textBytes(entry.tags) + textBytes(entry.fileName);
    }

    private long[] writeBlocks(final byte[] data) throws StoreException {
        final int count = (data.length + BlockStore.BLOCK_BYTES - 1) / BlockStore.BLOCK_BYTES;
        final long[] ids = new long[count];
        int written = 0;
        try {
            for (int i = 0; i < count; i++) {
                final int from = i * BlockStore.BLOCK_BYTES;
                final int to = Math.min(data.length, from + BlockStore.BLOCK_BYTES);
                ids[i] = nextBlock++;
                store.putBlock(ids[i], Arrays.copyOfRange(data, from, to));
                written = i + 1;
            }
        } catch (StoreException e) {
            for (int i = 0; i < written; i++) {
                store.removeBlock(ids[i]);
            }
            throw e;
        }
        return ids;
    }

    private byte[] readBlocks(final long[] ids) throws StoreException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (long id : ids) {
            final byte[] chunk = store.getBlock(id);
            if (chunk == null) {
                throw new StoreException(StoreException.Reason.CORRUPT, "missing block " + id);
            }
            out.write(chunk, 0, chunk.length);
        }
        return out.toByteArray();
    }

    private void releaseBlocks(final long[] ids) {
        for (long id : ids) {
            store.removeBlock(id);
        }
    }

    private static byte[] bytes(final String text) {
        return (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
    }

    private static long textBytes(final String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }

    private byte[] encodeIndex() throws StoreException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(INDEX_VERSION);
            out.writeLong(nextEntry);
            out.writeLong(nextBlock);
            out.writeInt(entries.size());
            for (Entry entry : entries.values()) {
                out.writeLong(entry.id);
                out.writeByte(entry.kind);
                out.writeByte((entry.starred ? FLAG_STARRED : 0) | (entry.readOnly ? FLAG_READ_ONLY : 0));
                writeString(out, entry.title);
                writeString(out, entry.tags);
                writeString(out, entry.fileName);
                out.writeLong(entry.size);
                out.writeInt(entry.blocks.length);
                for (long block : entry.blocks) {
                    out.writeLong(block);
                }
            }
        } catch (IOException e) {
            throw new StoreException(StoreException.Reason.INTERNAL, e.getMessage());
        }
        final byte[] encoded = bytes.toByteArray();
        if (encoded.length > BlockStore.MAX_PAYLOAD_BYTES) {
            throw new StoreException(StoreException.Reason.LIMIT_REACHED, "too many entries");
        }
        return encoded;
    }

    private void decodeIndex(final byte[] raw) throws StoreException {
        if (raw.length == 0) {
            return;
        }
        final Set<Long> referenced = new HashSet<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            if (in.readUnsignedByte() != INDEX_VERSION) {
                throw corrupt("index version");
            }
            nextEntry = in.readLong();
            nextBlock = in.readLong();
            final int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw corrupt("entry count");
            }
            for (int i = 0; i < count; i++) {
                final long id = in.readLong();
                final int kind = in.readUnsignedByte();
                final int flags = in.readUnsignedByte();
                if ((kind != KIND_NOTE && kind != KIND_FILE) || id < 1 || id >= nextEntry) {
                    throw corrupt("entry header");
                }
                final Entry entry = new Entry(id, kind, readString(in), readString(in), readString(in));
                entry.starred = (flags & FLAG_STARRED) != 0;
                entry.readOnly = (flags & FLAG_READ_ONLY) != 0;
                entry.size = in.readLong();
                final int blocks = in.readInt();
                if (blocks < 0 || blocks > BlockStore.MAX_BLOCKS) {
                    throw corrupt("block count");
                }
                entry.blocks = new long[blocks];
                for (int b = 0; b < blocks; b++) {
                    final long block = in.readLong();
                    if (block < 1 || block >= nextBlock || !referenced.add(block)) {
                        throw corrupt("block reference");
                    }
                    entry.blocks[b] = block;
                }
                if (entries.put(id, entry) != null) {
                    throw corrupt("duplicate entry");
                }
            }
            if (in.available() != 0) {
                throw corrupt("trailing data");
            }
        } catch (IOException e) {
            throw corrupt("truncated index");
        }
        if (!store.blockIds().containsAll(referenced)) {
            throw corrupt("index refers to missing blocks");
        }
    }

    private static void writeString(final DataOutputStream out, final String value) throws IOException {
        final byte[] bytes = bytes(value);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(final DataInputStream in) throws IOException, StoreException {
        final int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw corrupt("string length");
        }
        final byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static StoreException corrupt(final String detail) {
        return new StoreException(StoreException.Reason.CORRUPT, detail);
    }
}
