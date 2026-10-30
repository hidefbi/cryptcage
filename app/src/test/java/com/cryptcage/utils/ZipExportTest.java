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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ZipExportTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private int counter;

    private VaultSession newVault() throws VaultException {
        counter++;
        final File file = new File(folder.getRoot(), "vault" + counter + ".cc3");
        return VaultSession.create(file, "pw".toCharArray());
    }

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

    private File exportToTemp(final VaultSession vault, final long... ids) throws Exception {
        final List<Long> list = new ArrayList<>();
        for (long id : ids) {
            list.add(id);
        }
        final File file = folder.newFile();
        ZipExport.writePlain(file, ZipExport.collect(vault, list));
        return file;
    }

    @Test
    public void singleNoteIsStillAZip() throws Exception {
        try (VaultSession vault = newVault()) {
            final long id = vault.addNote("Hello", "body text", "work");
            final Map<String, byte[]> zip = readZip(exportToTemp(vault, id));
            assertEquals(2, zip.size());
            assertTrue(Arrays.equals("body text".getBytes(StandardCharsets.UTF_8), zip.get("Hello.txt")));
            assertTrue(zip.containsKey(ZipExport.MANIFEST_NAME));
        }
    }

    @Test
    public void onlySelectedItemsAreExported() throws Exception {
        try (VaultSession vault = newVault()) {
            final long keep = vault.addNote("Keep", "k", "");
            vault.addNote("Skip", "s", "");
            final long fileId = vault.addFile("office.ovpn", "client\n".getBytes(StandardCharsets.UTF_8));
            final Map<String, byte[]> zip = readZip(exportToTemp(vault, keep, fileId));
            assertEquals(3, zip.size());
            assertTrue(zip.containsKey("Keep.txt"));
            assertTrue(zip.containsKey("office.ovpn"));
            assertFalse(zip.containsKey("Skip.txt"));
            assertTrue(Arrays.equals("client\n".getBytes(StandardCharsets.UTF_8), zip.get("office.ovpn")));
        }
    }

    @Test
    public void duplicateTitlesGetDistinctNames() throws Exception {
        try (VaultSession vault = newVault()) {
            final long a = vault.addNote("Same", "one", "");
            final long b = vault.addNote("same", "two", "");
            final Map<String, byte[]> zip = readZip(exportToTemp(vault, a, b));
            assertTrue(zip.containsKey("Same.txt"));
            assertTrue(zip.containsKey("same (2).txt"));
        }
    }

    @Test
    public void manifestEscapesQuotesAndNewlines() {
        final List<ZipExport.Record> records = new ArrayList<>();
        records.add(new ZipExport.Record(7L, "a \"quoted\"\nline", "x", true, false, "a.txt"));
        final String json = new String(ZipExport.manifest(records, false), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"title\": \"a \\\"quoted\\\"\\nline\""));
        assertTrue(json.contains("\"encrypted\": false"));
    }

    @Test
    public void missingIdIsRejected() throws Exception {
        try (VaultSession vault = newVault()) {
            final long id = vault.addNote("One", "1", "");
            try {
                ZipExport.collect(vault, Arrays.asList(id, 999L));
                fail("a missing id was exported");
            } catch (VaultException e) {
                assertEquals(VaultException.Reason.REJECTED, e.getReason());
            }
        }
    }
}
