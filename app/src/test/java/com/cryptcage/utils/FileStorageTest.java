package com.cryptcage.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.cryptcage.utils.VaultException.Reason;

public class FileStorageTest {

    private static final char[] PASSWORD = "correct horse 42".toCharArray();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void fileSurvivesSaveAndReopen() throws Exception {
        final File file = new File(folder.getRoot(), "notes.cc3");
        final byte[] data = "remote vpn.example.net 1194 udp\n".getBytes(StandardCharsets.UTF_8);
        final long id;
        try (VaultSession vault = VaultSession.create(file, PASSWORD)) {
            id = vault.addFile("office.ovpn", data);
            vault.save();
        }
        try (VaultSession vault = VaultSession.open(file, PASSWORD)) {
            final VaultSession.Summary item = vault.summary(id);
            assertTrue(item.file);
            assertEquals("office.ovpn", item.fileName);
            assertEquals("office.ovpn", item.title);
            assertArrayEquals(data, vault.fileData(id));
        }
    }

    @Test
    public void replaceFileChangesNameAndContent() throws Exception {
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "a.cc3"), PASSWORD)) {
            final long id = vault.addFile("a.txt", "first".getBytes(StandardCharsets.UTF_8));
            vault.replaceFile(id, "b.txt", "second".getBytes(StandardCharsets.UTF_8));
            assertEquals("b.txt", vault.summary(id).fileName);
            assertEquals("second", new String(vault.fileData(id), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void fileCannotBeEditedAsNote() throws Exception {
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "b.cc3"), PASSWORD)) {
            final long id = vault.addFile("a.conf", "x".getBytes(StandardCharsets.UTF_8));
            try {
                vault.updateNote(id, "title", "body", "");
                fail("a file must not be changed through the note editor");
            } catch (VaultException expected) {
                assertEquals(Reason.REJECTED, expected.getReason());
            }
            assertTrue(vault.summary(id).file);
        }
    }
}
