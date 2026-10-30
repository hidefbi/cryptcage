package com.notecrypt.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileStorageTest {

    private static final char[] PASSWORD = "correct horse 42".toCharArray();

    private File dir;
    private File vault;

    @Before
    public void setUp() throws IOException {
        dir = File.createTempFile("files", "");
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
    public void fileSurvivesSaveAndReopen() throws VaultException {
        final VaultKey key = VaultFile.createKey(PASSWORD);
        final DatabaseForNotes db = new DatabaseForNotes();
        final byte[] data = "remote vpn.example.net 1194 udp\n".getBytes(StandardCharsets.UTF_8);
        final long id = db.addFile("office.ovpn", data);
        VaultFile.save(vault, db, key);

        final Notes item = VaultFile.open(vault, PASSWORD).db.get(id);
        assertTrue(item.isFile());
        assertEquals("office.ovpn", item.getFileName());
        assertEquals("office.ovpn", item.getTitle());
        assertTrue(Arrays.equals(data, item.getData()));
    }

    @Test
    public void replaceFileChangesNameAndContent() {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long id = db.addFile("a.txt", "first".getBytes(StandardCharsets.UTF_8));
        db.replaceFile(id, "b.txt", "second".getBytes(StandardCharsets.UTF_8));
        final Notes item = db.get(id);
        assertEquals("b.txt", item.getFileName());
        assertEquals("second", new String(item.getData(), StandardCharsets.UTF_8));
    }

    @Test
    public void fileCannotBeEditedAsNote() {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long id = db.addFile("a.conf", "x".getBytes(StandardCharsets.UTF_8));
        try {
            db.update(id, "title", "body", "");
            fail("a file must not be changed through the note editor");
        } catch (IllegalStateException expected) {
            assertTrue(db.get(id).isFile());
        }
    }

    @Test
    public void contentBudgetStopsGrowth() {
        final DatabaseForNotes db = new DatabaseForNotes();
        final byte[] chunk = new byte[4 * 1024 * 1024];
        boolean stopped = false;
        int added = 0;
        try {
            for (int i = 0; i < 30; i++) {
                db.addFile("part" + i + ".txt", chunk);
                added++;
            }
        } catch (IllegalStateException e) {
            stopped = true;
        }
        assertTrue("limit must be reached", stopped);
        assertTrue(added >= 12);
        assertTrue(db.estimatedBytes() <= DatabaseForNotes.CONTENT_BUDGET);
    }

    @Test
    public void versionOnePayloadStillDecodes() throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(buffer);
        out.writeInt(1);
        out.writeLong(1L);
        out.writeInt(1);
        out.writeLong(0L);
        out.writeBoolean(true);
        writeUtf(out, "Old title");
        writeUtf(out, "Old body");
        writeUtf(out, "work");
        out.flush();

        final DatabaseForNotes db = DatabaseCodec.decode(buffer.toByteArray());
        final Notes item = db.get(0L);
        assertEquals("Old title", item.getTitle());
        assertEquals("Old body", item.getNote());
        assertTrue(item.isStarred());
        assertTrue(!item.isFile());
        assertEquals(1, db.findTag("work").size());
    }

    private static void writeUtf(final DataOutputStream out, final String s) throws IOException {
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
