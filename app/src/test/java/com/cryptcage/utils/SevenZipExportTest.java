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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.junit.Test;

public class SevenZipExportTest {

    @Test
    public void writesEveryEntryAndTheManifest() throws Exception {
        final List<ZipExport.Entry> entries = new ArrayList<>();
        entries.add(new ZipExport.Entry("note.txt", "hello".getBytes(StandardCharsets.UTF_8)));
        entries.add(new ZipExport.Entry("empty.txt", new byte[0]));
        final List<ZipExport.Record> records = new ArrayList<>();
        records.add(new ZipExport.Record(1L, "note", "", false, false, "note.txt"));
        final ZipExport.Plan plan = new ZipExport.Plan(entries, records);
        final File out = File.createTempFile("export", ".7z");
        try {
            SevenZipExport.writePlain(out, plan);
            final Map<String, byte[]> read = readAll(out);
            assertEquals(3, read.size());
            assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), read.get("note.txt"));
            assertArrayEquals(new byte[0], read.get("empty.txt"));
            assertArrayEquals(ZipExport.manifest(records, false), read.get(ZipExport.MANIFEST_NAME));
        } finally {
            assertTrue(out.delete() || !out.exists());
        }
    }

    private static Map<String, byte[]> readAll(final File file) throws IOException {
        final Map<String, byte[]> result = new HashMap<>();
        try (SevenZFile archive = new SevenZFile(file)) {
            SevenZArchiveEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                final byte[] buffer = new byte[4096];
                int n;
                while ((n = archive.read(buffer)) != -1) {
                    bytes.write(buffer, 0, n);
                }
                result.put(entry.getName(), bytes.toByteArray());
            }
        }
        return result;
    }
}
