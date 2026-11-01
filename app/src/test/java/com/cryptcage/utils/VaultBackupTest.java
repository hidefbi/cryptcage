/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VaultBackupTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static char[] password() {
        return "correct horse 42 battery".toCharArray();
    }

    @Test
    public void backupOpensWithTheSamePasswordAndHoldsTheSameNotes() throws Exception {
        final File vault = new File(folder.getRoot(), "work.ncf");
        final long note;
        try (VaultSession session = VaultSession.create(vault, password())) {
            note = session.addNote("Title", "Body text 本文", "tag");
            session.save();
        }
        final File backup = new File(folder.getRoot(), "work-backup.ncf");
        final VaultBackup.Digest written;
        try (InputStream in = new FileInputStream(vault);
                OutputStream out = new FileOutputStream(backup)) {
            written = VaultBackup.copy(in, out);
        }
        final VaultBackup.Digest read;
        try (InputStream in = new FileInputStream(backup)) {
            read = VaultBackup.digest(in);
        }
        assertEquals(written, read);
        assertEquals(vault.length(), backup.length());
        try (VaultSession session = VaultSession.open(backup, password())) {
            assertEquals("Body text 本文", session.body(note));
        }
    }

    @Test
    public void backupStillNeedsTheVaultPassword() throws Exception {
        final File vault = new File(folder.getRoot(), "work.ncf");
        try (VaultSession session = VaultSession.create(vault, password())) {
            session.addNote("Title", "Body", "");
            session.save();
        }
        final File backup = new File(folder.getRoot(), "copy.ncf");
        try (InputStream in = new FileInputStream(vault);
                OutputStream out = new FileOutputStream(backup)) {
            VaultBackup.copy(in, out);
        }
        try {
            VaultSession.open(backup, "wrong password here".toCharArray()).close();
            fail("a wrong password must not open the backup");
        } catch (VaultException expected) {
            // expected
        }
    }

    @Test
    public void suggestedNameKeepsTheVaultName() {
        assertEquals("work-backup.ncf", VaultBackup.suggestedName("work.ncf"));
        assertEquals("my vault-backup.ncf", VaultBackup.suggestedName("my vault.ncf"));
    }

    @Test
    public void digestOfKnownBytesMatches() throws Exception {
        final VaultBackup.Digest digest = VaultBackup.digest(
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(3, digest.length);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest.sha256);
    }
}
