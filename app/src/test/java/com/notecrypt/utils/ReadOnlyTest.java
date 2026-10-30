package com.notecrypt.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class ReadOnlyTest {

    @Test
    public void readOnlyNoteRefusesChanges() {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long id = db.add("Title", "body", "");
        db.setReadOnly(id, true);
        try {
            db.update(id, "Changed", "body", "");
            fail("a read-only note must not change");
        } catch (IllegalStateException expected) {
            assertEquals("Title", db.get(id).getTitle());
        }
        db.setReadOnly(id, false);
        db.update(id, "Changed", "body", "");
        assertEquals("Changed", db.get(id).getTitle());
    }

    @Test
    public void readOnlyFileRefusesReplacement() {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long id = db.addFile("a.conf", "x".getBytes(StandardCharsets.UTF_8));
        db.setReadOnly(id, true);
        try {
            db.replaceFile(id, "b.conf", "y".getBytes(StandardCharsets.UTF_8));
            fail("a read-only file must not change");
        } catch (IllegalStateException expected) {
            assertEquals("a.conf", db.get(id).getFileName());
        }
    }

    @Test
    public void readOnlyCanStillBeDeleted() {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long id = db.add("Title", "body", "");
        db.setReadOnly(id, true);
        db.delete(id);
        assertEquals(0, db.size());
    }

    @Test
    public void readOnlyFlagSurvivesSaveAndLoad() throws Exception {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long locked = db.add("Locked", "x", "");
        final long open = db.add("Open", "y", "");
        db.setReadOnly(locked, true);
        final DatabaseForNotes loaded = DatabaseCodec.decode(DatabaseCodec.encode(db));
        assertTrue(loaded.get(locked).isReadOnly());
        assertFalse(loaded.get(open).isReadOnly());
    }
}
