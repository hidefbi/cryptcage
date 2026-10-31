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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DraftTest {

    private static final char[] PASSWORD = "correct horse 42".toCharArray();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void draftRoundTripsWithUnicode() throws Exception {
        final Draft draft = new Draft(7, "\u6807\u9898 \ud83d\ude00", "tag1, \u5de5\u4f5c", "\u6b63\u6587\n\u7b2c\u4e8c\u884c");
        final Draft back = Draft.decode(draft.encode());
        assertEquals(7, back.noteId);
        assertEquals("\u6807\u9898 \ud83d\ude00", back.title);
        assertEquals("tag1, \u5de5\u4f5c", back.tags);
        assertEquals("\u6b63\u6587\n\u7b2c\u4e8c\u884c", back.body);
    }

    @Test
    public void unknownDraftVersionIsRejected() throws Exception {
        final byte[] encoded = new Draft(-1, "", "", "").encode();
        encoded[0] = 9;
        try {
            Draft.decode(encoded);
            fail("version not checked");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("version"));
        }
    }

    @Test
    public void sealedDraftNeedsTheSameVault() throws Exception {
        final File vault = new File(folder.getRoot(), "a.cc3");
        final byte[] plain = new Draft(-1, "t", "", "secret body").encode();
        final byte[] sealed;
        try (VaultSession session = VaultSession.create(vault, PASSWORD)) {
            sealed = session.sealDraft(plain);
            assertFalse(new String(sealed, StandardCharsets.ISO_8859_1).contains("secret body"));
            assertArrayEquals(plain, session.openDraft(sealed));
        }
        try (VaultSession other = VaultSession.create(new File(folder.getRoot(), "b.cc3"), PASSWORD)) {
            try {
                other.openDraft(sealed);
                fail("another vault opened the draft");
            } catch (VaultException expected) {
                assertEquals(VaultException.Reason.BAD_PASSWORD_OR_DAMAGED, expected.getReason());
            }
        }
    }

    @Test
    public void draftStoreSavesLoadsAndDeletes() throws Exception {
        final DraftStore store = new DraftStore(new File(folder.getRoot(), "drafts"));
        final String name = DraftStore.nameFor("/data/a.cc3", 3);
        assertNull(store.load(name));
        store.save(name, new byte[] {1, 2, 3});
        assertArrayEquals(new byte[] {1, 2, 3}, store.load(name));
        store.save(name, new byte[] {4});
        assertArrayEquals(new byte[] {4}, store.load(name));
        store.delete(name);
        assertNull(store.load(name));
    }

    @Test
    public void draftNamesDependOnVaultAndNote() {
        final String a = DraftStore.nameFor("/data/a.cc3", 1);
        assertFalse(a.equals(DraftStore.nameFor("/data/b.cc3", 1)));
        assertFalse(a.equals(DraftStore.nameFor("/data/a.cc3", 2)));
        assertTrue(DraftStore.nameFor("/data/a.cc3", -1).endsWith("-" + DraftStore.NEW_NOTE));
        assertFalse(a.contains("/"));
    }
}
