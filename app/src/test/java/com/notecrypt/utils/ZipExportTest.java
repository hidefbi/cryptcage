package com.notecrypt.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Test;

public class ZipExportTest {

    private static Map<String, byte[]> readZip(final File file) throws IOException {
        final Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                final ByteArrayOutputStream buf = new ByteArrayOutputStream();
                final byte[] chunk = new byte[4096];
                int n;
                while ((n = zin.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
                out.put(entry.getName(), buf.toByteArray());
            }
        }
        return out;
    }

    private static File exportToTemp(final DatabaseForNotes db, final long... ids) throws IOException {
        final java.util.List<Long> list = new java.util.ArrayList<>();
        for (long id : ids) {
            list.add(id);
        }
        final File file = File.createTempFile("export", ".zip");
        ZipExport.writePlain(file, ZipExport.collect(db, list));
        return file;
    }

    @Test
    public void singleNoteIsStillAZip() throws IOException {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long id = db.add("Hello", "body text", "work");
        final File file = exportToTemp(db, id);
        try {
            final Map<String, byte[]> zip = readZip(file);
            assertEquals(2, zip.size());
            assertTrue(Arrays.equals("body text".getBytes(StandardCharsets.UTF_8), zip.get("Hello.txt")));
            assertTrue(zip.containsKey(ZipExport.MANIFEST_NAME));
        } finally {
            file.delete();
        }
    }

    @Test
    public void onlySelectedItemsAreExported() throws IOException {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long keep = db.add("Keep", "k", "");
        db.add("Skip", "s", "");
        final long fileId = db.addFile("office.ovpn", "client\n".getBytes(StandardCharsets.UTF_8));
        final File file = exportToTemp(db, keep, fileId);
        try {
            final Map<String, byte[]> zip = readZip(file);
            assertEquals(3, zip.size());
            assertTrue(zip.containsKey("Keep.txt"));
            assertTrue(zip.containsKey("office.ovpn"));
            assertTrue(!zip.containsKey("Skip.txt"));
            assertTrue(Arrays.equals("client\n".getBytes(StandardCharsets.UTF_8), zip.get("office.ovpn")));
        } finally {
            file.delete();
        }
    }

    @Test
    public void duplicateTitlesGetDistinctNames() throws IOException {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long a = db.add("Same", "one", "");
        final long b = db.add("same", "two", "");
        final File file = exportToTemp(db, a, b);
        try {
            final Map<String, byte[]> zip = readZip(file);
            assertTrue(zip.containsKey("Same.txt"));
            assertTrue(zip.containsKey("same (2).txt"));
        } finally {
            file.delete();
        }
    }

    @Test
    public void manifestEscapesQuotesAndNewlines() {
        final java.util.List<ZipExport.Record> records = new java.util.ArrayList<>();
        records.add(new ZipExport.Record(7L, "a \"quoted\"\nline", "x", true, false, "a.txt"));
        final String json = new String(ZipExport.manifest(records, false), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"title\": \"a \\\"quoted\\\"\\nline\""));
        assertTrue(json.contains("\"encrypted\": false"));
    }

    @Test
    public void unknownIdsAreSkipped() {
        final DatabaseForNotes db = new DatabaseForNotes();
        final long id = db.add("One", "1", "");
        final ZipExport.Plan plan = ZipExport.collect(db, java.util.Arrays.asList(id, 999L));
        assertEquals(1, plan.entries.size());
    }
}
