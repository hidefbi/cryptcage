/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.List;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.cryptcage.utils.StoreException.Reason;

public class NoteRepositoryTest {

    private static final int ITERATIONS = Cryptox.MIN_ITERATIONS;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File newFile() {
        return new File(folder.getRoot(), "vault.cc3");
    }

    private static char[] password() {
        return "correct horse battery".toCharArray();
    }

    /** Text with several multi-byte characters, long enough to need more than one block. */
    private static String longText(final int repeats) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < repeats; i++) {
            out.append("第").append(i).append("段 note text, ünïcode 😀\n");
        }
        return out.toString();
    }

    private static byte[] randomBytes(final int length, final long seed) {
        final byte[] out = new byte[length];
        new Random(seed).nextBytes(out);
        return out;
    }

    @Test
    public void notesAndFilesSurviveReopen() throws Exception {
        final File file = newFile();
        final String body = longText(300_000);
        assertTrue("test body should span several blocks", body.getBytes("UTF-8").length > 2 * BlockStore.BLOCK_BYTES);
        final byte[] data = randomBytes(1 << 20, 3);

        final long note;
        final long starred;
        final long file1;
        try (NoteRepository repo = NoteRepository.create(file, password(), ITERATIONS)) {
            note = repo.addNote("Ünïcode title 😀", body, "Work, work , 工作");
            starred = repo.addNote("second", "short", "");
            file1 = repo.addFile("photo.jpg", data);
            repo.setStarred(starred, true);
            repo.commit();
        }
        try (NoteRepository repo = NoteRepository.open(file, password())) {
            assertEquals(3, repo.list().size());
            final NoteRepository.Entry first = repo.entry(note);
            assertEquals("Ünïcode title 😀", first.title);
            assertEquals("Work, 工作", first.tags);
            assertEquals(body, repo.readBody(note));
            assertTrue(repo.entry(starred).starred);
            assertFalse(repo.entry(note).starred);
            assertEquals("photo.jpg", repo.entry(file1).fileName);
            assertTrue(repo.entry(file1).isFile());
            assertArrayEquals(data, repo.readFile(file1));
        }
    }

    @Test
    public void uncommittedChangesAreDropped() throws Exception {
        final File file = newFile();
        try (NoteRepository repo = NoteRepository.create(file, password(), ITERATIONS)) {
            repo.addNote("kept", "saved body", "");
            repo.commit();
            repo.addNote("lost", "lost body", "");
            repo.setStarred(1, true);
        }
        try (NoteRepository repo = NoteRepository.open(file, password())) {
            assertEquals(1, repo.list().size());
            assertEquals("saved body", repo.readBody(1));
            assertFalse(repo.entry(1).starred);
        }
    }

    @Test
    public void updateReleasesOldBlocks() throws Exception {
        try (NoteRepository repo = NoteRepository.create(newFile(), password(), ITERATIONS)) {
            final long id = repo.addNote("t", longText(300_000), "");
            assertEquals(3, repo.blockCount());
            repo.updateNote(id, "t", "small", "");
            assertEquals(1, repo.blockCount());
            assertEquals("small", repo.readBody(id));
        }
    }

    @Test
    public void deleteReleasesBlocks() throws Exception {
        try (NoteRepository repo = NoteRepository.create(newFile(), password(), ITERATIONS)) {
            final long id = repo.addFile("a.bin", randomBytes(100_000, 1));
            assertEquals(1, repo.blockCount());
            repo.delete(id);
            assertEquals(0, repo.blockCount());
            assertTrue(repo.list().isEmpty());
        }
    }

    @Test
    public void readOnlyEntryCannotBeChangedOrStarred() throws Exception {
        try (NoteRepository repo = NoteRepository.create(newFile(), password(), ITERATIONS)) {
            final long id = repo.addNote("t", "body", "");
            repo.setReadOnly(id, true);
            try {
                repo.setStarred(id, true);
                fail("read-only entry starred");
            } catch (StoreException e) {
                assertEquals(Reason.BAD_ARGUMENT, e.reason());
            }
            try {
                repo.updateNote(id, "t", "changed", "");
                fail("read-only entry changed");
            } catch (StoreException e) {
                assertEquals(Reason.BAD_ARGUMENT, e.reason());
            }
            assertEquals("body", repo.readBody(id));
            assertFalse(repo.entry(id).starred);
        }
    }

    @Test
    public void kindsAreChecked() throws Exception {
        try (NoteRepository repo = NoteRepository.create(newFile(), password(), ITERATIONS)) {
            final long note = repo.addNote("t", "body", "");
            final long file = repo.addFile("f.txt", new byte[] {1, 2, 3});
            assertBadArgument(() -> repo.readFile(note));
            assertBadArgument(() -> repo.readBody(file));
            assertBadArgument(() -> repo.updateNote(file, "t", "x", ""));
            assertBadArgument(() -> repo.replaceFile(note, "f", new byte[0]));
        }
    }

    @Test
    public void contentLimitIsEnforcedAndRolledBack() throws Exception {
        try (NoteRepository repo = NoteRepository.create(newFile(), password(), ITERATIONS)) {
            repo.setLimitForTests(1000);
            final long first = repo.addNote("t", repeat('a', 900), "");
            final int blocksBefore = repo.blockCount();
            try {
                repo.addNote("t", repeat('b', 200), "");
                fail("limit not enforced");
            } catch (StoreException e) {
                assertEquals(Reason.LIMIT_REACHED, e.reason());
            }
            assertEquals(blocksBefore, repo.blockCount());
            assertEquals(1, repo.list().size());

            repo.updateNote(first, "t", repeat('c', 950), "");
            assertEquals(repeat('c', 950), repo.readBody(first));

            try {
                repo.updateNote(first, "t", repeat('d', 1100), "");
                fail("limit not enforced on update");
            } catch (StoreException e) {
                assertEquals(Reason.LIMIT_REACHED, e.reason());
            }
            assertEquals(repeat('c', 950), repo.readBody(first));
        }
    }

    @Test
    public void defaultLimitIsOneGiB() {
        assertEquals(1L << 30, NoteRepository.CONTENT_LIMIT);
    }

    @Test
    public void fileSizeLimitIsEnforced() throws Exception {
        try (NoteRepository repo = NoteRepository.create(newFile(), password(), ITERATIONS)) {
            assertBadArgument(() -> repo.addFile("big.bin", new byte[(int) FileKinds.MAX_FILE_BYTES + 1]));
            repo.addFile("ok.bin", new byte[(int) FileKinds.MAX_FILE_BYTES]);
            assertEquals(1, repo.list().size());
        }
    }

    @Test
    public void wrongPasswordIsRejected() throws Exception {
        final File file = newFile();
        try (NoteRepository repo = NoteRepository.create(file, password(), ITERATIONS)) {
            repo.commit();
        }
        try {
            NoteRepository.open(file, "wrong".toCharArray());
            fail("wrong password accepted");
        } catch (StoreException e) {
            assertEquals(Reason.AUTH_FAILED, e.reason());
        }
    }

    @Test
    public void listIsInCreationOrder() throws Exception {
        try (NoteRepository repo = NoteRepository.create(newFile(), password(), ITERATIONS)) {
            repo.addNote("a", "", "");
            repo.addFile("b", new byte[0]);
            repo.addNote("c", "", "");
            final List<NoteRepository.Entry> all = repo.list();
            assertEquals("a", all.get(0).title);
            assertEquals("b", all.get(1).title);
            assertEquals("c", all.get(2).title);
        }
    }

    @Test
    public void compactionShrinksFileAndKeepsContent() throws Exception {
        final File file = newFile();
        final long before;
        try (NoteRepository repo = NoteRepository.create(file, password(), ITERATIONS)) {
            final long id = repo.addNote("t", repeat('a', 2 << 20), "");
            repo.addFile("keep.bin", randomBytes(300_000, 4));
            repo.commit();
            for (char c = 'b'; c <= 'i'; c++) {
                repo.updateNote(id, "t", repeat(c, 2 << 20), "");
                repo.commit();
            }
            before = repo.fileBytes();
            assertFalse(repo.needsCompaction());

            repo.compact();
            assertTrue(repo.fileBytes() < before / 2);
            assertEquals(repeat('i', 2 << 20), repo.readBody(id));
            assertArrayEquals(randomBytes(300_000, 4), repo.readFile(id + 1));
        }
        assertFalse(new File(folder.getRoot(), ".vault.cc3.compact").exists());
        try (NoteRepository repo = NoteRepository.open(file, password())) {
            assertEquals(repeat('i', 2 << 20), repo.readBody(1));
            assertArrayEquals(randomBytes(300_000, 4), repo.readFile(2));
        }
    }

    private static String repeat(final char c, final int count) {
        final char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void assertBadArgument(final Action action) throws Exception {
        try {
            action.run();
            fail("expected BAD_ARGUMENT");
        } catch (StoreException e) {
            assertEquals(Reason.BAD_ARGUMENT, e.reason());
        }
    }
}
