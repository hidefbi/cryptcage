package com.cryptcage.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.cryptcage.utils.StoreException.Reason;

public class BlockStoreTest {

    private static final int ITERATIONS = Cryptox.MIN_ITERATIONS;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private int counter;

    private File newFile() {
        counter++;
        return new File(folder.getRoot(), "vault" + counter + ".cc3");
    }

    private static char[] password() {
        return "correct horse battery".toCharArray();
    }

    private static byte[] bytes(final String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(final byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    private static byte[] random(final int length, final long seed) {
        final byte[] out = new byte[length];
        new Random(seed).nextBytes(out);
        return out;
    }

    private static byte[] readFile(final File file) throws IOException {
        final byte[] out = new byte[(int) file.length()];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readFully(out);
        }
        return out;
    }

    private static void writeFile(final File file, final byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
    }

    /** Everything a reader can see, as one string. Equal strings mean equal contents. */
    private static String describe(final BlockStore store) throws StoreException {
        final StringBuilder out = new StringBuilder();
        out.append("payload=").append(text(store.payload())).append(';');
        for (Long id : store.blockIds()) {
            out.append(id).append('=').append(text(store.getBlock(id))).append(';');
        }
        return out.toString();
    }

    private static String describe(final File file) throws StoreException {
        try (BlockStore store = BlockStore.open(file, password())) {
            return describe(store);
        }
    }

    @Test
    public void blocksAndPayloadSurviveReopen() throws Exception {
        final File file = newFile();
        final byte[] big = random(1 << 20, 7);
        try (BlockStore store = BlockStore.create(file, password(), ITERATIONS)) {
            store.putBlock(1, bytes("alpha"));
            store.putBlock(2, big);
            store.commit(bytes("index-v1"));
        }
        try (BlockStore store = BlockStore.open(file, password())) {
            assertEquals("index-v1", text(store.payload()));
            assertEquals(new TreeSet<>(Arrays.asList(1L, 2L)), store.blockIds());
            assertEquals("alpha", text(store.getBlock(1)));
            assertArrayEquals(big, store.getBlock(2));
        }
    }

    @Test
    public void wrongPasswordIsRejected() throws Exception {
        final File file = newFile();
        try (BlockStore store = BlockStore.create(file, password(), ITERATIONS)) {
            store.commit(bytes("x"));
        }
        try {
            BlockStore.open(file, "not the password".toCharArray());
            fail("wrong password accepted");
        } catch (StoreException e) {
            assertEquals(Reason.AUTH_FAILED, e.reason());
        }
    }

    @Test
    public void overwriteAndRemoveAreSavedByCommit() throws Exception {
        final File file = newFile();
        try (BlockStore store = BlockStore.create(file, password(), ITERATIONS)) {
            store.putBlock(1, bytes("first"));
            store.putBlock(2, bytes("second"));
            store.commit(bytes("p1"));

            store.putBlock(1, bytes("first, edited"));
            store.removeBlock(2);
            store.commit(bytes("p2"));
        }
        try (BlockStore store = BlockStore.open(file, password())) {
            assertEquals("p2", text(store.payload()));
            assertEquals("first, edited", text(store.getBlock(1)));
            assertNull(store.getBlock(2));
        }
    }

    @Test
    public void changesWithoutCommitAreDropped() throws Exception {
        final File file = newFile();
        try (BlockStore store = BlockStore.create(file, password(), ITERATIONS)) {
            store.putBlock(1, bytes("kept"));
            store.commit(bytes("saved"));
            store.putBlock(1, bytes("lost"));
            store.putBlock(3, bytes("lost too"));
        }
        try (BlockStore store = BlockStore.open(file, password())) {
            assertEquals("saved", text(store.payload()));
            assertEquals("kept", text(store.getBlock(1)));
            assertNull(store.getBlock(3));
        }
    }

    @Test
    public void blockSizeLimitIsExact() throws Exception {
        try (BlockStore store = BlockStore.create(newFile(), password(), ITERATIONS)) {
            store.putBlock(1, new byte[BlockStore.BLOCK_BYTES]);
            try {
                store.putBlock(2, new byte[BlockStore.BLOCK_BYTES + 1]);
                fail("oversized block accepted");
            } catch (StoreException e) {
                assertEquals(Reason.BAD_ARGUMENT, e.reason());
            }
        }
    }

    @Test
    public void noNonceIsEverReused() throws Exception {
        try (BlockStore store = BlockStore.create(newFile(), password(), ITERATIONS)) {
            store.startNonceLog();
            for (int round = 0; round < 30; round++) {
                for (long id = 0; id < 5; id++) {
                    store.putBlock(id, random(100, round * 10 + id));
                }
                store.commit(new byte[round]);
            }
            assertEquals(30 * 6, store.nonces().size());
            final Set<String> seen = new HashSet<>();
            for (byte[] nonce : store.nonces()) {
                assertTrue("nonce reused", seen.add(Arrays.toString(nonce)));
            }
        }
    }

    /**
     * Changing any single byte must either be detected, or leave what a reader sees unchanged.
     * The only other state a flip can produce is the one before the last commit, because the
     * older header slot is then the valid one. That state is authentic, so it is accepted here.
     */
    @Test
    public void everyFlippedByteIsDetectedOrHarmless() throws Exception {
        final File file = newFile();
        final String previous;
        try (BlockStore store = BlockStore.create(file, password(), ITERATIONS)) {
            previous = describe(store);
            store.putBlock(1, bytes("one"));
            store.putBlock(2, bytes("two"));
            store.commit(bytes("payload"));
        }
        final String current = describe(file);
        final byte[] original = readFile(file);
        for (int i = 0; i < original.length; i++) {
            final byte[] changed = original.clone();
            changed[i] ^= (byte) 0x80;
            writeFile(file, changed);
            assertSeenStateIsAllowed(file, i, current, previous);
        }
        writeFile(file, original);
    }

    @Test
    public void everyTruncationIsDetectedOrHarmless() throws Exception {
        final File file = newFile();
        final String previous;
        try (BlockStore store = BlockStore.create(file, password(), ITERATIONS)) {
            previous = describe(store);
            store.putBlock(1, bytes("one"));
            store.commit(bytes("payload"));
        }
        final String current = describe(file);
        final byte[] original = readFile(file);
        for (int length = 0; length < original.length; length++) {
            writeFile(file, Arrays.copyOf(original, length));
            assertSeenStateIsAllowed(file, length, current, previous);
        }
        writeFile(file, original);
    }

    private static void assertSeenStateIsAllowed(final File file, final int where,
                                                 final String current, final String previous) throws IOException {
        final String seen;
        try {
            seen = describe(file);
        } catch (StoreException e) {
            return;
        }
        assertTrue("silent change at " + where + ": " + seen,
                seen.equals(current) || seen.equals(previous));
    }

    @Test
    public void swappedBlocksAreRejected() throws Exception {
        final File file = newFile();
        final long first;
        final long second;
        final int length;
        try (BlockStore store = BlockStore.create(file, password(), ITERATIONS)) {
            store.putBlock(1, bytes("aaaaaaaaaaaaaaaa"));
            store.putBlock(2, bytes("bbbbbbbbbbbbbbbb"));
            store.commit(bytes("p"));
            first = store.offsetOf(1);
            second = store.offsetOf(2);
            length = store.lengthOf(1);
            assertEquals(length, store.lengthOf(2));
        }
        final byte[] data = readFile(file);
        final byte[] a = Arrays.copyOfRange(data, (int) first, (int) first + length);
        final byte[] b = Arrays.copyOfRange(data, (int) second, (int) second + length);
        System.arraycopy(b, 0, data, (int) first, length);
        System.arraycopy(a, 0, data, (int) second, length);
        writeFile(file, data);

        try (BlockStore store = BlockStore.open(file, password())) {
            assertCorrupt(store, 1);
            assertCorrupt(store, 2);
        }
    }

    @Test
    public void blockCopiedFromAnotherVaultIsRejected() throws Exception {
        final File source = newFile();
        final long sourceOffset;
        final int sourceLength;
        try (BlockStore store = BlockStore.create(source, password(), ITERATIONS)) {
            store.putBlock(1, bytes("same length text"));
            store.commit(bytes("p"));
            sourceOffset = store.offsetOf(1);
            sourceLength = store.lengthOf(1);
        }
        final File target = newFile();
        final long targetOffset;
        try (BlockStore store = BlockStore.create(target, password(), ITERATIONS)) {
            store.putBlock(1, bytes("same length text"));
            store.commit(bytes("p"));
            targetOffset = store.offsetOf(1);
        }
        final byte[] copied = Arrays.copyOfRange(readFile(source),
                (int) sourceOffset, (int) sourceOffset + sourceLength);
        try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
            raf.seek(targetOffset);
            raf.write(copied);
        }
        try (BlockStore store = BlockStore.open(target, password())) {
            assertCorrupt(store, 1);
        }
    }

    @Test
    public void randomFileIsNotAStore() throws Exception {
        final File file = newFile();
        writeFile(file, random(1000, 99));
        try {
            BlockStore.open(file, password());
            fail("random file accepted");
        } catch (StoreException e) {
            assertEquals(Reason.NOT_A_STORE, e.reason());
        }
    }

    @Test
    public void oldFormatHeaderIsNotAStore() throws Exception {
        final File file = newFile();
        final byte[] data = random(600, 5);
        data[0] = 'N';
        data[1] = 'C';
        data[2] = 'P';
        data[3] = '2';
        writeFile(file, data);
        try {
            BlockStore.open(file, password());
            fail("old format accepted");
        } catch (StoreException e) {
            assertEquals(Reason.NOT_A_STORE, e.reason());
        }
    }

    @Test
    public void compactionDropsUnusedRecordsAndKeepsState() throws Exception {
        final File file = newFile();
        final byte[] latest;
        final long before;
        BlockStore store = BlockStore.create(file, password(), ITERATIONS);
        try {
            store.putBlock(1, random(50_000, 1));
            store.putBlock(2, random(50_000, 2));
            store.putBlock(3, bytes("three"));
            store.commit(bytes("p1"));
            for (int round = 0; round < 6; round++) {
                store.putBlock(1, random(50_000, 10 + round));
                store.commit(bytes("p-" + round));
            }
            store.removeBlock(2);
            store.commit(bytes("final"));
            latest = store.getBlock(1);
            before = store.fileBytes();
            assertTrue(store.liveBytes() < before);

            store = store.compact();
            assertEquals(new TreeSet<>(Arrays.asList(1L, 3L)), store.blockIds());
            assertArrayEquals(latest, store.getBlock(1));
            assertEquals("three", text(store.getBlock(3)));
            assertEquals("final", text(store.payload()));
            assertTrue(store.fileBytes() < before / 2);
        } finally {
            store.close();
        }
        assertFalse(new File(folder.getRoot(), "." + file.getName() + ".compact").exists());
        try (BlockStore reopened = BlockStore.open(file, password())) {
            assertArrayEquals(latest, reopened.getBlock(1));
            assertEquals("final", text(reopened.payload()));
        }
    }

    @Test
    public void compactionRefusesUncommittedChanges() throws Exception {
        try (BlockStore store = BlockStore.create(newFile(), password(), ITERATIONS)) {
            store.commit(bytes("p"));
            store.putBlock(1, bytes("not yet saved"));
            try {
                store.compact();
                fail("compacted with uncommitted changes");
            } catch (StoreException e) {
                assertEquals(Reason.BAD_ARGUMENT, e.reason());
            }
        }
    }

    @Test
    public void rekeyChangesPasswordAndKeepsData() throws Exception {
        final File file = newFile();
        BlockStore store = BlockStore.create(file, password(), ITERATIONS);
        try {
            store.putBlock(1, bytes("kept"));
            store.commit(bytes("p"));
            store = store.rekey("new password".toCharArray());
            assertEquals("kept", text(store.getBlock(1)));
        } finally {
            store.close();
        }
        try {
            BlockStore.open(file, password());
            fail("old password still opens the container");
        } catch (StoreException e) {
            assertEquals(Reason.AUTH_FAILED, e.reason());
        }
        try (BlockStore reopened = BlockStore.open(file, "new password".toCharArray())) {
            assertEquals("kept", text(reopened.getBlock(1)));
            assertEquals("p", text(reopened.payload()));
        }
    }

    private static void assertCorrupt(final BlockStore store, final long id) {
        try {
            store.getBlock(id);
            fail("block " + id + " read without error");
        } catch (StoreException e) {
            assertEquals(Reason.CORRUPT, e.reason());
        }
    }
}
