package com.cryptcage.utils;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.cryptcage.utils.StoreException.Reason;

/**
 * Vault container, format version 3.
 *
 * The file starts with a prologue: magic, version, file id, KDF settings, salt and the
 * data key wrapped by the password key. Two header slots follow, and records are appended
 * after them. A record is either a block or an index, sealed with AES-256-GCM and a random
 * nonce of its own. The additional data ties each record to the file id, its kind, its block
 * id and the generation it was written in, so records cannot be moved or copied.
 *
 * A commit appends the index, syncs, and then writes the header slot of the new generation.
 * The two slots alternate, so a crash leaves either the old state or the new one.
 *
 * Known limit: whoever can write the file can put back an earlier committed state. Detecting
 * that needs a counter kept outside the file.
 */
final class BlockStore implements AutoCloseable {

    /** Largest plaintext of one block. */
    static final int BLOCK_BYTES = 4 * 1024 * 1024;
    static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;
    static final int MAX_BLOCKS = 1_000_000;

    private static final byte[] MAGIC = {'C', 'C', '3', 0};
    private static final int VERSION = 3;

    private static final int PROLOGUE_BYTES = 128;
    private static final int OFF_VERSION = 4;
    private static final int OFF_FILE_ID = 5;
    private static final int OFF_ITERATIONS = 21;
    private static final int OFF_SALT = 25;
    private static final int OFF_WRAP = 57;
    private static final int WRAP_BYTES = Cryptox.NONCE_BYTES + Cryptox.KEY_BYTES + Cryptox.TAG_BITS / 8;
    private static final int FILE_ID_BYTES = 16;

    private static final int SLOT_A = 128;
    private static final int SLOT_B = 192;
    private static final int SLOT_USED = 52;
    private static final int SLOT_MAC_AT = 20;
    private static final int SLOT_MAC_BYTES = 32;
    private static final int DATA_START = 256;

    private static final int KIND_BLOCK = 0;
    private static final int KIND_INDEX = 1;
    private static final int ENTRY_BYTES = 8 + 8 + 4 + 8;
    private static final int RECORD_OVERHEAD = Cryptox.NONCE_BYTES + Cryptox.TAG_BITS / 8;
    private static final int MAX_RECORD = BLOCK_BYTES + RECORD_OVERHEAD;
    private static final int MAX_INDEX_RECORD =
            4 + MAX_BLOCKS * ENTRY_BYTES + 4 + MAX_PAYLOAD_BYTES + RECORD_OVERHEAD;

    private final RandomAccessFile raf;
    private final byte[] fileId;
    private final byte[] blockKey;
    private final byte[] macKey;
    private final Map<Long, Ref> catalog = new TreeMap<>();
    private byte[] payload = new byte[0];
    private long generation;
    private boolean closed;
    private List<byte[]> nonceLog;

    private static final class Ref {
        final long offset;
        final int length;
        final long generation;

        Ref(final long offset, final int length, final long generation) {
            this.offset = offset;
            this.length = length;
            this.generation = generation;
        }
    }

    private static final class Slot {
        final long generation;
        final long offset;
        final int length;

        Slot(final long generation, final long offset, final int length) {
            this.generation = generation;
            this.offset = offset;
            this.length = length;
        }
    }

    private BlockStore(final RandomAccessFile raf, final byte[] fileId, final byte[] dataKey)
            throws GeneralSecurityException {
        this.raf = raf;
        this.fileId = fileId;
        this.blockKey = deriveKey(dataKey, "cc3 block key");
        this.macKey = deriveKey(dataKey, "cc3 slot mac");
    }

    /** Makes a new, empty container at a path that does not exist yet. */
    static BlockStore create(final File file, final char[] password, final int iterations) throws StoreException {
        if (iterations < Cryptox.MIN_ITERATIONS || iterations > Cryptox.MAX_ITERATIONS) {
            throw new StoreException(Reason.BAD_ARGUMENT, "iteration count");
        }
        if (file.exists()) {
            throw new StoreException(Reason.EXISTS, null);
        }
        final byte[] fileId = Cryptox.randomBytes(FILE_ID_BYTES);
        final byte[] salt = Cryptox.randomBytes(Cryptox.SALT_BYTES);
        final byte[] dataKey = Cryptox.randomBytes(Cryptox.KEY_BYTES);
        final byte[] prologue = new byte[PROLOGUE_BYTES];
        byte[] kek = null;
        RandomAccessFile raf = null;
        try {
            System.arraycopy(MAGIC, 0, prologue, 0, MAGIC.length);
            prologue[OFF_VERSION] = (byte) VERSION;
            System.arraycopy(fileId, 0, prologue, OFF_FILE_ID, FILE_ID_BYTES);
            ByteBuffer.wrap(prologue).putInt(OFF_ITERATIONS, iterations);
            System.arraycopy(salt, 0, prologue, OFF_SALT, salt.length);

            kek = Cryptox.pbkdf2Sha256(password, salt, iterations);
            final byte[] wrapped = Cryptox.seal(kek, authenticatedPrologue(prologue), dataKey);
            System.arraycopy(wrapped, 0, prologue, OFF_WRAP, WRAP_BYTES);

            raf = new RandomAccessFile(file, "rw");
            raf.write(prologue);
            raf.write(new byte[DATA_START - PROLOGUE_BYTES]);
            raf.getFD().sync();

            final BlockStore store = new BlockStore(raf, fileId, dataKey);
            store.commit(new byte[0]);
            return store;
        } catch (GeneralSecurityException e) {
            discard(file, raf);
            throw new StoreException(Reason.INTERNAL, "key setup failed");
        } catch (IOException e) {
            discard(file, raf);
            throw new StoreException(Reason.IO, e.getMessage());
        } catch (StoreException e) {
            discard(file, raf);
            throw e;
        } finally {
            if (kek != null) {
                Arrays.fill(kek, (byte) 0);
            }
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    /** Opens an existing container and loads its index. */
    static BlockStore open(final File file, final char[] password) throws StoreException {
        if (!file.isFile()) {
            throw new StoreException(Reason.IO, "missing file");
        }
        RandomAccessFile raf = null;
        byte[] kek = null;
        byte[] dataKey = null;
        boolean opened = false;
        try {
            raf = new RandomAccessFile(file, "rw");
            if (raf.length() < DATA_START) {
                throw new StoreException(Reason.NOT_A_STORE, "too short");
            }
            final byte[] prologue = new byte[PROLOGUE_BYTES];
            raf.seek(0);
            raf.readFully(prologue);
            for (int i = 0; i < MAGIC.length; i++) {
                if (prologue[i] != MAGIC[i]) {
                    throw new StoreException(Reason.NOT_A_STORE, "magic");
                }
            }
            if (prologue[OFF_VERSION] != VERSION) {
                throw new StoreException(Reason.NOT_A_STORE, "version");
            }
            final int iterations = ByteBuffer.wrap(prologue).getInt(OFF_ITERATIONS);
            if (iterations < Cryptox.MIN_ITERATIONS || iterations > Cryptox.MAX_ITERATIONS) {
                throw new StoreException(Reason.CORRUPT, "iteration count");
            }
            final byte[] salt = Arrays.copyOfRange(prologue, OFF_SALT, OFF_SALT + Cryptox.SALT_BYTES);
            kek = Cryptox.pbkdf2Sha256(password, salt, iterations);
            final byte[] wrapped = Arrays.copyOfRange(prologue, OFF_WRAP, OFF_WRAP + WRAP_BYTES);
            dataKey = Cryptox.open(kek, authenticatedPrologue(prologue), wrapped);
            if (dataKey.length != Cryptox.KEY_BYTES) {
                throw new StoreException(Reason.CORRUPT, "data key length");
            }
            final byte[] fileId = Arrays.copyOfRange(prologue, OFF_FILE_ID, OFF_FILE_ID + FILE_ID_BYTES);
            final BlockStore store = new BlockStore(raf, fileId, dataKey);
            store.load();
            opened = true;
            return store;
        } catch (GeneralSecurityException e) {
            throw new StoreException(Reason.AUTH_FAILED, "wrong password or damaged file");
        } catch (IOException e) {
            throw new StoreException(Reason.IO, e.getMessage());
        } finally {
            if (kek != null) {
                Arrays.fill(kek, (byte) 0);
            }
            if (dataKey != null) {
                Arrays.fill(dataKey, (byte) 0);
            }
            if (!opened && raf != null) {
                closeQuietly(raf);
            }
        }
    }

    /** Stores a block in memory and on disk. It is not part of the saved state until commit. */
    void putBlock(final long id, final byte[] plain) throws StoreException {
        ensureOpen();
        if (plain == null || plain.length > BLOCK_BYTES) {
            throw new StoreException(Reason.BAD_ARGUMENT, "block size");
        }
        final long gen = generation + 1;
        try {
            final byte[] record = seal(KIND_BLOCK, id, gen, plain);
            final long offset = raf.length();
            raf.seek(offset);
            raf.write(record);
            catalog.put(id, new Ref(offset, record.length, gen));
        } catch (GeneralSecurityException e) {
            throw new StoreException(Reason.INTERNAL, "block seal");
        } catch (IOException e) {
            throw new StoreException(Reason.IO, e.getMessage());
        }
    }

    /** Returns the block, or null if there is no block with this id. */
    byte[] getBlock(final long id) throws StoreException {
        ensureOpen();
        final Ref ref = catalog.get(id);
        if (ref == null) {
            return null;
        }
        try {
            final byte[] record = readBytes(ref.offset, ref.length);
            return Cryptox.open(blockKey, recordAad(KIND_BLOCK, id, ref.generation), record);
        } catch (GeneralSecurityException e) {
            throw new StoreException(Reason.CORRUPT, "block " + id + " does not authenticate");
        } catch (IOException e) {
            throw new StoreException(Reason.IO, e.getMessage());
        }
    }

    void removeBlock(final long id) {
        ensureOpen();
        catalog.remove(id);
    }

    Set<Long> blockIds() {
        ensureOpen();
        return new TreeSet<>(catalog.keySet());
    }

    /** The payload saved with the last commit. */
    byte[] payload() {
        ensureOpen();
        return payload.clone();
    }

    /** Saves the current blocks and the payload as the new state. */
    void commit(final byte[] userPayload) throws StoreException {
        ensureOpen();
        if (userPayload == null || userPayload.length > MAX_PAYLOAD_BYTES) {
            throw new StoreException(Reason.BAD_ARGUMENT, "payload size");
        }
        if (catalog.size() > MAX_BLOCKS) {
            throw new StoreException(Reason.BAD_ARGUMENT, "too many blocks");
        }
        final long gen = generation + 1;
        try {
            final byte[] record = seal(KIND_INDEX, 0, gen, encodeIndex(userPayload));
            final long offset = raf.length();
            raf.seek(offset);
            raf.write(record);
            raf.getFD().sync();
            writeSlot(gen, offset, record.length);
            raf.getFD().sync();
            generation = gen;
            payload = userPayload.clone();
        } catch (GeneralSecurityException e) {
            throw new StoreException(Reason.INTERNAL, "index seal");
        } catch (IOException e) {
            throw new StoreException(Reason.IO, e.getMessage());
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Arrays.fill(blockKey, (byte) 0);
        Arrays.fill(macKey, (byte) 0);
        closeQuietly(raf);
    }

    /** Test hook: from now on, every nonce written is recorded. */
    void startNonceLog() {
        nonceLog = new ArrayList<>();
    }

    /** Test hook: nonces recorded since {@link #startNonceLog()}. */
    List<byte[]> nonces() {
        return nonceLog == null ? new ArrayList<byte[]>() : nonceLog;
    }

    /** Test hook: where a block's record starts in the file. */
    long offsetOf(final long id) {
        return catalog.get(id).offset;
    }

    /** Test hook: how long a block's record is in the file. */
    int lengthOf(final long id) {
        return catalog.get(id).length;
    }

    private void load() throws StoreException, IOException, GeneralSecurityException {
        final long fileLength = raf.length();
        final Slot first = readSlot(SLOT_A, fileLength);
        final Slot second = readSlot(SLOT_B, fileLength);
        Slot best = first;
        if (second != null && (best == null || second.generation > best.generation)) {
            best = second;
        }
        if (best == null) {
            throw new StoreException(Reason.CORRUPT, "no valid header slot");
        }
        final byte[] record = readBytes(best.offset, best.length);
        final byte[] plain;
        try {
            plain = Cryptox.open(blockKey, recordAad(KIND_INDEX, 0, best.generation), record);
        } catch (GeneralSecurityException e) {
            throw new StoreException(Reason.CORRUPT, "index does not authenticate");
        }
        parseIndex(plain, best.generation, fileLength);
        generation = best.generation;
    }

    private Slot readSlot(final int at, final long fileLength)
            throws IOException, GeneralSecurityException, StoreException {
        if (fileLength < at + SLOT_USED) {
            return null;
        }
        final byte[] raw = readBytes(at, SLOT_USED);
        final byte[] expected = mac(raw, 0, SLOT_MAC_AT);
        final byte[] stored = Arrays.copyOfRange(raw, SLOT_MAC_AT, SLOT_USED);
        if (!MessageDigest.isEqual(expected, stored)) {
            return null;
        }
        final ByteBuffer b = ByteBuffer.wrap(raw);
        final long gen = b.getLong(0);
        final long offset = b.getLong(8);
        final int length = b.getInt(16);
        if (gen < 1 || offset < DATA_START || length < RECORD_OVERHEAD
                || length > MAX_INDEX_RECORD || offset + length > fileLength) {
            return null;
        }
        return new Slot(gen, offset, length);
    }

    private void writeSlot(final long gen, final long offset, final int length)
            throws IOException, GeneralSecurityException {
        final byte[] body = new byte[SLOT_USED];
        ByteBuffer.wrap(body).putLong(0, gen).putLong(8, offset).putInt(16, length);
        final byte[] tag = mac(body, 0, SLOT_MAC_AT);
        System.arraycopy(tag, 0, body, SLOT_MAC_AT, SLOT_MAC_BYTES);
        raf.seek(gen % 2 == 1 ? SLOT_B : SLOT_A);
        raf.write(body);
    }

    private void parseIndex(final byte[] plain, final long slotGen, final long fileLength) throws StoreException {
        final ByteBuffer b = ByteBuffer.wrap(plain);
        if (b.remaining() < 4) {
            throw new StoreException(Reason.CORRUPT, "index truncated");
        }
        final int count = b.getInt();
        if (count < 0 || count > MAX_BLOCKS || (long) count * ENTRY_BYTES > b.remaining()) {
            throw new StoreException(Reason.CORRUPT, "index count");
        }
        final Map<Long, Ref> found = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            final long id = b.getLong();
            final long offset = b.getLong();
            final int length = b.getInt();
            final long gen = b.getLong();
            if (length < RECORD_OVERHEAD || length > MAX_RECORD || offset < DATA_START
                    || offset + length > fileLength || gen < 1 || gen > slotGen) {
                throw new StoreException(Reason.CORRUPT, "index entry " + id);
            }
            if (found.put(id, new Ref(offset, length, gen)) != null) {
                throw new StoreException(Reason.CORRUPT, "duplicate block " + id);
            }
        }
        if (b.remaining() < 4) {
            throw new StoreException(Reason.CORRUPT, "index payload");
        }
        final int payloadLength = b.getInt();
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES || payloadLength != b.remaining()) {
            throw new StoreException(Reason.CORRUPT, "index payload length");
        }
        final byte[] body = new byte[payloadLength];
        b.get(body);
        catalog.clear();
        catalog.putAll(found);
        payload = body;
    }

    private byte[] encodeIndex(final byte[] userPayload) {
        final ByteBuffer b = ByteBuffer.allocate(4 + catalog.size() * ENTRY_BYTES + 4 + userPayload.length);
        b.putInt(catalog.size());
        for (Map.Entry<Long, Ref> entry : catalog.entrySet()) {
            final Ref ref = entry.getValue();
            b.putLong(entry.getKey());
            b.putLong(ref.offset);
            b.putInt(ref.length);
            b.putLong(ref.generation);
        }
        b.putInt(userPayload.length);
        b.put(userPayload);
        return b.array();
    }

    private byte[] seal(final int kind, final long id, final long gen, final byte[] plain)
            throws GeneralSecurityException {
        final byte[] record = Cryptox.seal(blockKey, recordAad(kind, id, gen), plain);
        if (nonceLog != null) {
            nonceLog.add(Arrays.copyOf(record, Cryptox.NONCE_BYTES));
        }
        return record;
    }

    private byte[] readBytes(final long offset, final int length) throws IOException, StoreException {
        final byte[] out = new byte[length];
        raf.seek(offset);
        try {
            raf.readFully(out);
        } catch (EOFException e) {
            throw new StoreException(Reason.CORRUPT, "file is shorter than its index says");
        }
        return out;
    }

    private byte[] recordAad(final int kind, final long id, final long gen) {
        return ByteBuffer.allocate(FILE_ID_BYTES + 1 + 8 + 8)
                .put(fileId)
                .put((byte) kind)
                .putLong(id)
                .putLong(gen)
                .array();
    }

    private byte[] mac(final byte[] data, final int from, final int to) throws GeneralSecurityException {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
        mac.update(data, from, to - from);
        return mac.doFinal();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("container is closed");
        }
    }

    private static byte[] authenticatedPrologue(final byte[] prologue) {
        return Arrays.copyOfRange(prologue, 0, OFF_WRAP);
    }

    /** One HKDF-SHA256 step with a zero salt, enough for a 32-byte key. */
    private static byte[] deriveKey(final byte[] dataKey, final String label) throws GeneralSecurityException {
        final Mac extract = Mac.getInstance("HmacSHA256");
        extract.init(new SecretKeySpec(new byte[Cryptox.KEY_BYTES], "HmacSHA256"));
        final byte[] prk = extract.doFinal(dataKey);
        try {
            final Mac expand = Mac.getInstance("HmacSHA256");
            expand.init(new SecretKeySpec(prk, "HmacSHA256"));
            expand.update(label.getBytes("US-ASCII"));
            expand.update((byte) 1);
            return expand.doFinal();
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        } finally {
            Arrays.fill(prk, (byte) 0);
        }
    }

    private static void discard(final File file, final RandomAccessFile raf) {
        if (raf != null) {
            closeQuietly(raf);
        }
        file.delete();
    }

    private static void closeQuietly(final RandomAccessFile raf) {
        try {
            raf.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
