/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.cryptcage.utils.VaultException.Reason;

public class ReadOnlyTest {

    private static final char[] PASSWORD = "correct horse 42".toCharArray();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readOnlyNoteRefusesChanges() throws Exception {
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "a.cc3"), PASSWORD)) {
            final long id = vault.addNote("Title", "body", "");
            vault.setReadOnly(id, true);
            try {
                vault.updateNote(id, "Changed", "body", "");
                fail("a read-only note must not change");
            } catch (VaultException expected) {
                assertEquals(Reason.REJECTED, expected.getReason());
                assertEquals("Title", vault.summary(id).title);
            }
            vault.setReadOnly(id, false);
            vault.updateNote(id, "Changed", "body", "");
            assertEquals("Changed", vault.summary(id).title);
        }
    }

    @Test
    public void readOnlyFileRefusesReplacement() throws Exception {
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "b.cc3"), PASSWORD)) {
            final long id = vault.addFile("a.conf", "x".getBytes(StandardCharsets.UTF_8));
            vault.setReadOnly(id, true);
            try {
                vault.replaceFile(id, "b.conf", "y".getBytes(StandardCharsets.UTF_8));
                fail("a read-only file must not change");
            } catch (VaultException expected) {
                assertEquals(Reason.REJECTED, expected.getReason());
                assertEquals("a.conf", vault.summary(id).fileName);
            }
        }
    }

    @Test
    public void readOnlyCanStillBeDeleted() throws Exception {
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "c.cc3"), PASSWORD)) {
            final long id = vault.addNote("Title", "body", "");
            vault.setReadOnly(id, true);
            vault.delete(id);
            assertTrue(vault.list().isEmpty());
        }
    }

    @Test
    public void readOnlyFlagSurvivesReopen() throws Exception {
        final File file = new File(folder.getRoot(), "d.cc3");
        final long locked;
        final long open;
        try (VaultSession vault = VaultSession.create(file, PASSWORD)) {
            locked = vault.addNote("Locked", "x", "");
            open = vault.addNote("Open", "y", "");
            vault.setReadOnly(locked, true);
            vault.save();
        }
        try (VaultSession vault = VaultSession.open(file, PASSWORD)) {
            assertTrue(vault.summary(locked).readOnly);
            assertFalse(vault.summary(open).readOnly);
        }
    }
}
