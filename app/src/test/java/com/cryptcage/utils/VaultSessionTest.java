package com.cryptcage.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.cryptcage.utils.VaultException.Reason;

public class VaultSessionTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File newFile() {
        return new File(folder.getRoot(), "vault.cc3");
    }

    private static char[] password() {
        return "correct horse battery".toCharArray();
    }

    private static void assertReason(final Reason expected, final Action action) throws Exception {
        try {
            action.run();
            fail("expected " + expected);
        } catch (VaultException e) {
            assertEquals(expected, e.getReason());
        }
    }

    private interface Action {
        void run() throws Exception;
    }

    @Test
    public void notesAndFilesSurviveReopen() throws Exception {
        final File file = newFile();
        final byte[] data = {1, 2, 3, 4, 5};
        final long note;
        final long attachment;
        try (VaultSession session = VaultSession.create(file, password())) {
            note = session.addNote("Ünï title", "body 本文", "Home, work");
            attachment = session.addFile("key.ovpn", data);
            session.setStarred(note, true);
            session.save();
        }
        try (VaultSession session = VaultSession.open(file, password())) {
            final List<VaultSession.Summary> all = session.list();
            assertEquals(2, all.size());
            final VaultSession.Summary first = session.summary(note);
            assertEquals("Ünï title", first.title);
            assertEquals("Home, work", first.tags);
            assertTrue(first.starred);
            assertFalse(first.file);
            assertEquals("body 本文", session.body(note));
            assertTrue(session.summary(attachment).file);
            assertEquals("key.ovpn", session.summary(attachment).fileName);
            assertArrayEquals(data, session.fileData(attachment));
        }
    }

    @Test
    public void unsavedChangesAreNotKept() throws Exception {
        final File file = newFile();
        try (VaultSession session = VaultSession.create(file, password())) {
            session.addNote("kept", "x", "");
            session.save();
            session.addNote("not kept", "y", "");
        }
        try (VaultSession session = VaultSession.open(file, password())) {
            assertEquals(1, session.list().size());
        }
    }

    @Test
    public void searchMatchesTitleTagsFileNameAndBodyIgnoringCase() throws Exception {
        try (VaultSession session = VaultSession.create(newFile(), password())) {
            final long title = session.addNote("Alpha Plan", "nothing here", "");
            final long tag = session.addNote("Beta", "nothing here", "Work");
            final long body = session.addNote("Gamma", "a hidden NEEDLE inside", "");
            final long file = session.addFile("Photo.jpg", new byte[] {9});
            final long other = session.addNote("Delta", "unrelated", "");

            assertEquals(Collections.singletonList(title), session.search("alpha"));
            assertEquals(Collections.singletonList(tag), session.search("WORK"));
            assertEquals(Collections.singletonList(body), session.search("needle"));
            assertEquals(Collections.singletonList(file), session.search("photo"));
            assertTrue(session.search("zzz-not-there").isEmpty());
            assertEquals(5, session.search("").size());
            assertTrue(session.search("").contains(other));
        }
    }

    @Test
    public void tagCountsAndTagLookupIgnoreCase() throws Exception {
        try (VaultSession session = VaultSession.create(newFile(), password())) {
            final long a = session.addNote("a", "", "Work, home");
            final long b = session.addNote("b", "", "work");
            session.addFile("work.txt", new byte[0]);

            final Map<String, Integer> counts = session.tagCounts();
            assertEquals(Integer.valueOf(2), counts.get("work"));
            assertEquals(Integer.valueOf(1), counts.get("home"));
            assertEquals(Arrays.asList(a, b), session.withTag("WORK"));
            assertTrue(session.withTag(null).isEmpty());
        }
    }

    @Test
    public void readOnlyEntryRejectsEdits() throws Exception {
        try (VaultSession session = VaultSession.create(newFile(), password())) {
            final long id = session.addNote("t", "body", "");
            session.setReadOnly(id, true);
            assertReason(Reason.REJECTED, () -> session.updateNote(id, "t", "changed", ""));
            assertEquals("body", session.body(id));
        }
    }

    @Test
    public void fileSizeLimitIsReportedAsTooLarge() throws Exception {
        try (VaultSession session = VaultSession.create(newFile(), password())) {
            assertReason(Reason.TOO_LARGE, () -> session.addFile("big.bin", new byte[(int) VaultSession.MAX_FILE_BYTES + 1]));
            session.addFile("ok.bin", new byte[(int) VaultSession.MAX_FILE_BYTES]);
        }
    }

    @Test
    public void wrongPasswordAndNonVaultFilesAreReported() throws Exception {
        final File file = newFile();
        try (VaultSession session = VaultSession.create(file, password())) {
            session.save();
        }
        assertReason(Reason.BAD_PASSWORD_OR_DAMAGED,
                () -> VaultSession.open(file, "wrong".toCharArray()));

        final File junk = new File(folder.getRoot(), "junk.bin");
        try (FileOutputStream out = new FileOutputStream(junk)) {
            out.write(new byte[600]);
        }
        assertReason(Reason.NOT_A_VAULT, () -> VaultSession.open(junk, password()));
    }

    @Test
    public void magicCheckRecognisesOnlyCurrentVaults() throws Exception {
        final File file = newFile();
        try (VaultSession session = VaultSession.create(file, password())) {
            session.save();
        }
        assertTrue(VaultSession.isVault(file));
        final File old = new File(folder.getRoot(), "old.bin");
        try (FileOutputStream out = new FileOutputStream(old)) {
            out.write(new byte[] {'N', 'C', 'P', '2', 0, 0});
        }
        assertFalse(VaultSession.isVault(old));
        assertFalse(VaultSession.isVault(new File(folder.getRoot(), "missing")));
    }

    @Test
    public void changePasswordKeepsContentAndDropsOldPassword() throws Exception {
        final File file = newFile();
        final long id;
        try (VaultSession session = VaultSession.create(file, password())) {
            id = session.addNote("t", "body", "");
            session.changePassword("second password".toCharArray());
            assertEquals("body", session.body(id));
        }
        assertReason(Reason.BAD_PASSWORD_OR_DAMAGED, () -> VaultSession.open(file, password()));
        try (VaultSession session = VaultSession.open(file, "second password".toCharArray())) {
            assertEquals("body", session.body(id));
        }
    }

    @Test
    public void compactionKeepsContent() throws Exception {
        final File file = newFile();
        final long id;
        try (VaultSession session = VaultSession.create(file, password())) {
            id = session.addNote("t", "first", "");
            session.save();
            session.updateNote(id, "t", "second", "");
            session.save();
            assertFalse(session.needsCompaction());
            session.compact();
            assertEquals("second", session.body(id));
        }
        try (VaultSession session = VaultSession.open(file, password())) {
            assertEquals("second", session.body(id));
        }
    }
}
