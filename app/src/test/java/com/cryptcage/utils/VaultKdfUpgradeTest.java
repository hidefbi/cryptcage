/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VaultKdfUpgradeTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void oldRoundsAreRaisedOnOpenAndNotesStay() throws Exception {
        final File file = new File(folder.getRoot(), "old.ncf");
        final char[] password = "correct horse battery staple 42".toCharArray();

        final NoteRepository made = NoteRepository.create(file, password, Cryptox.MIN_ITERATIONS);
        made.addNote("kept", "still here", "");
        made.commit();
        made.close();

        final NoteRepository before = NoteRepository.open(file, password);
        assertEquals(Cryptox.MIN_ITERATIONS, before.iterations());
        before.close();

        VaultSession.open(file, password).close();

        final NoteRepository after = NoteRepository.open(file, password);
        assertEquals(Cryptox.DEFAULT_ITERATIONS, after.iterations());
        after.close();

        try (VaultSession session = VaultSession.open(file, password)) {
            assertEquals(1, session.list().size());
            assertEquals("still here", session.body(session.list().get(0).id));
        }
    }
}
