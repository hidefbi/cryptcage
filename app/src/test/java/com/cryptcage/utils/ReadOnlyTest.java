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
    public void readOnlyCannotBeDeletedOrStarred() throws Exception {
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "c.cc3"), PASSWORD)) {
            final long id = vault.addNote("Title", "body", "");
            vault.setReadOnly(id, true);
            try {
                vault.delete(id);
                fail("a read-only note must not be deleted");
            } catch (VaultException expected) {
                assertEquals(Reason.REJECTED, expected.getReason());
            }
            try {
                vault.setStarred(id, true);
                fail("a read-only note must not be starred");
            } catch (VaultException expected) {
                assertEquals(Reason.REJECTED, expected.getReason());
            }
            assertEquals(1, vault.list().size());
            assertFalse(vault.summary(id).starred);
        }
    }

    @Test
    public void copyIsPlacedAfterOriginalAndIsWritable() throws Exception {
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "e.cc3"), PASSWORD)) {
            final long first = vault.addNote("First", "a", "");
            final long second = vault.addNote("Second", "b", "");
            final long third = vault.addNote("Third", "c", "");
            vault.setReadOnly(first, true);
            final long copy = vault.duplicate(first, "First(copy)");
            assertEquals("a", vault.body(copy));
            assertFalse(vault.summary(copy).readOnly);
            assertTrue(vault.summary(first).order < vault.summary(copy).order);
            assertTrue(vault.summary(copy).order < vault.summary(second).order);
            vault.updateNote(copy, "First(copy)", "changed", "");
        }
    }

    @Test
    public void manualOrderSurvivesReopen() throws Exception {
        final File file = new File(folder.getRoot(), "f.cc3");
        final long a;
        final long b;
        final long c;
        try (VaultSession vault = VaultSession.create(file, PASSWORD)) {
            a = vault.addNote("A", "x", "");
            b = vault.addNote("B", "y", "");
            c = vault.addNote("C", "z", "");
            vault.reorder(java.util.Arrays.asList(c, a, b));
            vault.save();
        }
        try (VaultSession vault = VaultSession.open(file, PASSWORD)) {
            assertTrue(vault.summary(c).order < vault.summary(a).order);
            assertTrue(vault.summary(a).order < vault.summary(b).order);
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
