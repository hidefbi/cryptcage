package com.notecrypt.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class VaultFileTest {

    private static final char[] PASSWORD = "correct horse 42".toCharArray();

    private File dir;
    private File vault;

    @Before
    public void setUp() throws IOException {
        dir = File.createTempFile("vaults", "");
        dir.delete();
        dir.mkdirs();
        vault = new File(dir, "notes.ncf");
    }

    @After
    public void tearDown() {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir.delete();
    }

    @Test
    public void savedVaultOpensWithSamePassword() throws VaultException {
        final VaultKey key = VaultFile.createKey(PASSWORD);
        final DatabaseForNotes db = new DatabaseForNotes();
        db.add("first", "body one", "Work, 工作");
        db.add("second", "body two", "");
        VaultFile.save(vault, db, key);

        final VaultFile.Opened opened = VaultFile.open(vault, PASSWORD);
        assertEquals(2, opened.db.size());
        assertEquals("body one", opened.db.get(0L).getNote());
        assertEquals("Work, 工作", opened.db.get(0L).getTags());
        assertEquals(1, opened.db.findTag("work").size());
        assertEquals(1, opened.db.findTag("工作").size());
    }

    @Test
    public void wrongPasswordIsRejected() throws VaultException {
        final VaultKey key = VaultFile.createKey(PASSWORD);
        VaultFile.save(vault, new DatabaseForNotes(), key);
        try {
            VaultFile.open(vault, "not the password".toCharArray());
            fail("wrong password must not open the vault");
        } catch (VaultException e) {
            assertEquals(VaultException.Reason.BAD_PASSWORD_OR_DAMAGED, e.getReason());
        }
    }

    @Test
    public void flippedCiphertextByteIsDetected() throws Exception {
        final VaultKey key = VaultFile.createKey(PASSWORD);
        final DatabaseForNotes db = new DatabaseForNotes();
        db.add("title", "a fairly long note body that will be encrypted", "");
        VaultFile.save(vault, db, key);
        flipByte(vault, (int) vault.length() - 20);
        try {
            VaultFile.open(vault, PASSWORD);
            fail("tampered ciphertext must be rejected");
        } catch (VaultException e) {
            assertEquals(VaultException.Reason.BAD_PASSWORD_OR_DAMAGED, e.getReason());
        }
    }

    @Test
    public void flippedHeaderByteIsDetected() throws Exception {
        final VaultKey key = VaultFile.createKey(PASSWORD);
        VaultFile.save(vault, new DatabaseForNotes(), key);
        // Change the iteration count inside the header. The header is authenticated,
        // so the result must be an error, not a silent change.
        flipByte(vault, 9);
        try {
            VaultFile.open(vault, PASSWORD);
            fail("header change must be rejected");
        } catch (VaultException e) {
            assertTrue(e.getReason() == VaultException.Reason.BAD_PASSWORD_OR_DAMAGED
                    || e.getReason() == VaultException.Reason.NOT_A_VAULT);
        }
    }

    @Test
    public void emptyFileIsNotAVault() throws Exception {
        new FileOutputStream(vault).close();
        try {
            VaultFile.open(vault, PASSWORD);
            fail();
        } catch (VaultException e) {
            assertEquals(VaultException.Reason.NOT_A_VAULT, e.getReason());
        }
    }

    @Test
    public void saveLeavesNoTemporaryFile() throws VaultException {
        final VaultKey key = VaultFile.createKey(PASSWORD);
        VaultFile.save(vault, new DatabaseForNotes(), key);
        VaultFile.save(vault, new DatabaseForNotes(), key);
        final File[] files = dir.listFiles();
        assertEquals(1, files.length);
        assertEquals("notes.ncf", files[0].getName());
    }

    @Test
    public void tagIndexFollowsEdits() throws VaultException {
        final VaultKey key = VaultFile.createKey(PASSWORD);
        final DatabaseForNotes db = new DatabaseForNotes();
        final long id = db.add("t", "n", "alpha");
        assertEquals(1, db.findTag("alpha").size());
        db.update(id, "t", "n", "beta");
        assertTrue(db.findTag("alpha").isEmpty());
        assertEquals(1, db.findTag("beta").size());
        db.delete(id);
        assertTrue(db.findTag("beta").isEmpty());
        VaultFile.save(vault, db, key);
    }

    private static void flipByte(final File file, final int offset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(offset);
            final int b = raf.read();
            raf.seek(offset);
            raf.write(b ^ 0x01);
        }
    }
}
