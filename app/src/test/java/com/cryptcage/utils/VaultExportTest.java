/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VaultExportTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void copiesOnlyTheChosenEntriesUnderANewPassword() throws Exception {
        final char[] original = "correct horse battery staple 42".toCharArray();
        final char[] copyPassword = "another long passphrase 2026".toCharArray();
        final File source = new File(folder.getRoot(), "source.ncf");
        final long keep;
        final long keepFile;
        try (VaultSession session = VaultSession.create(source, original)) {
            keep = session.addNote("first", "body one", "work");
            session.addNote("second", "body two", "");
            keepFile = session.addFile("doc.pdf", new byte[]{1, 2, 3});
            session.save();
        }
        final File out = new File(folder.getRoot(), "copy.ncf");
        try (VaultSession session = VaultSession.open(source, original)) {
            VaultExport.write(out, session, Arrays.asList(keep, keepFile), copyPassword);
        }
        try (VaultSession copy = VaultSession.open(out, copyPassword)) {
            assertEquals(2, copy.list().size());
            boolean sawNote = false;
            boolean sawFile = false;
            for (VaultSession.Summary entry : copy.list()) {
                if (entry.file) {
                    sawFile = true;
                    assertEquals("doc.pdf", entry.fileName);
                    assertArrayEquals(new byte[]{1, 2, 3}, copy.fileData(entry.id));
                } else {
                    sawNote = true;
                    assertEquals("first", entry.title);
                    assertEquals("body one", copy.body(entry.id));
                    assertEquals("work", entry.tags);
                }
            }
            assertTrue(sawNote && sawFile);
        }
        try {
            VaultSession.open(out, original).close();
            fail("the copy opened with the original password");
        } catch (VaultException expected) {
            // The copy must refuse the original password.
        }
    }

    @Test
    public void replacesAnOlderFileAtThePath() throws Exception {
        final char[] password = "correct horse battery staple 42".toCharArray();
        final File source = new File(folder.getRoot(), "source.ncf");
        final long id;
        try (VaultSession session = VaultSession.create(source, password)) {
            id = session.addNote("only", "text", "");
            session.save();
        }
        final File out = new File(folder.getRoot(), "copy.ncf");
        assertTrue(out.createNewFile());
        try (VaultSession session = VaultSession.open(source, password)) {
            VaultExport.write(out, session, Arrays.asList(id), password);
        }
        try (VaultSession copy = VaultSession.open(out, password)) {
            assertEquals(1, copy.list().size());
        }
    }
}
