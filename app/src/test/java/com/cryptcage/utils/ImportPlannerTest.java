package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class ImportPlannerTest {

    private static File zipWith(final String... nameAndText) throws IOException {
        final File file = File.createTempFile("import", ".zip");
        final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file));
        for (int i = 0; i < nameAndText.length; i += 2) {
            zos.putNextEntry(new ZipEntry(nameAndText[i]));
            zos.write(nameAndText[i + 1].getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        zos.close();
        return file;
    }

    @Test
    public void cryptCageArchiveRestoresTagsAndStars() throws Exception {
        final DatabaseForNotes source = new DatabaseForNotes();
        final long note = source.add("Plan", "step one", "work, 工作");
        source.setStarred(note, true);
        source.addFile("office.ovpn", "client\n".getBytes(StandardCharsets.UTF_8));
        final File zip = File.createTempFile("export", ".zip");
        ZipExport.writePlain(zip, ZipExport.collect(source, Arrays.asList(note, 1L)));

        final ImportPlanner.Plan plan = ImportPlanner.fromZip("export.zip", zip);
        assertEquals(0, plan.skipped);
        assertEquals(2, plan.items.size());
        final ImportPlanner.Item noteItem = plan.items.get(0);
        assertFalse(noteItem.file);
        assertEquals("Plan", noteItem.title);
        assertEquals("work, 工作", noteItem.tags);
        assertTrue(noteItem.starred);
        assertTrue(plan.items.get(1).file);
        assertEquals("office.ovpn", plan.items.get(1).title);
        zip.delete();
    }

    @Test
    public void foreignArchiveIsStoredWholeAndNotOpened() throws Exception {
        final File zip = zipWith("Shopping.txt", "milk", "vpn.ovpn", "client\n");
        final ImportPlanner.Plan plan = ImportPlanner.fromZip("bundle.zip", zip);
        assertEquals(1, plan.items.size());
        final ImportPlanner.Item item = plan.items.get(0);
        assertTrue(item.file);
        assertEquals("bundle.zip", item.title);
        assertFalse(item.editable);
        assertFalse(item.mismatch);
        zip.delete();
    }

    @Test
    public void markdownBecomesANote() throws Exception {
        final ImportPlanner.Plan plan = ImportPlanner.fromSingle("Ideas.md",
                "# Title\nbody".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, plan.items.size());
        assertFalse(plan.items.get(0).file);
        assertEquals("Ideas", plan.items.get(0).title);
        assertEquals("# Title\nbody", plan.items.get(0).text);
    }

    @Test
    public void binaryMarkdownIsStoredAsAFileWithAWarning() throws Exception {
        final ImportPlanner.Plan plan = ImportPlanner.fromSingle("bad.md", new byte[]{'a', 0, 'b'});
        final ImportPlanner.Item item = plan.items.get(0);
        assertTrue(item.file);
        assertTrue(item.mismatch);
        assertFalse(item.editable);
    }

    @Test
    public void contentThatDoesNotMatchTheExtensionIsStoredWithAWarning() throws Exception {
        final ImportPlanner.Plan plan = ImportPlanner.fromSingle("photo.png",
                "plain text, not a picture".getBytes(StandardCharsets.UTF_8));
        final ImportPlanner.Item item = plan.items.get(0);
        assertTrue(item.file);
        assertTrue(item.mismatch);
        assertFalse(item.editable);
    }

    @Test
    public void keysAreStoredAndFlagged() throws Exception {
        final ImportPlanner.Plan plan = ImportPlanner.fromSingle("id_rsa.key",
                new byte[]{0x30, (byte) 0x82, 0x01, 0x0a});
        final ImportPlanner.Item item = plan.items.get(0);
        assertTrue(item.file);
        assertTrue(item.secret);
        assertFalse(item.editable);
    }

    @Test
    public void sourceAndSvgAreEditableText() throws Exception {
        final ImportPlanner.Plan source = ImportPlanner.fromSingle("Main.java",
                "class Main {}".getBytes(StandardCharsets.UTF_8));
        assertTrue(source.items.get(0).file);
        assertTrue(source.items.get(0).editable);
        final ImportPlanner.Plan svg = ImportPlanner.fromSingle("logo.svg",
                "<svg/>".getBytes(StandardCharsets.UTF_8));
        assertTrue(svg.items.get(0).editable);
    }

    @Test
    public void unsupportedAndVaultFilesAreRefused() {
        assertRefused("setup.exe", new byte[]{1, 2, 3});
        assertRefused("old.ncf", new byte[]{1, 2, 3});
        assertRefused("noextension", new byte[]{1, 2, 3});
    }

    private static void assertRefused(final String name, final byte[] data) {
        try {
            ImportPlanner.fromSingle(name, data);
            fail(name + " must be refused");
        } catch (ImportPlanner.ImportException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    @Test
    public void duplicateNamesGetANumber() throws Exception {
        final DatabaseForNotes db = new DatabaseForNotes();
        db.add("Note", "existing", "");
        final ImportPlanner.Plan plan = ImportPlanner.fromSingle("Note.md",
                "new".getBytes(StandardCharsets.UTF_8));
        final ImportPlanner.Report report = ImportPlanner.addTo(db, plan.items);
        assertEquals(1, report.added);
        assertEquals(2, db.size());
        final List<String> titles = new ArrayList<>();
        for (Long id : db.allIds()) {
            titles.add(db.get(id).getTitle());
        }
        assertTrue(titles.contains("Note"));
        assertTrue(titles.contains("Note (2)"));
    }

    @Test
    public void warningsAreCounted() throws Exception {
        final DatabaseForNotes db = new DatabaseForNotes();
        final List<ImportPlanner.Item> items = new ArrayList<>();
        items.addAll(ImportPlanner.fromSingle("photo.png",
                "text".getBytes(StandardCharsets.UTF_8)).items);
        items.addAll(ImportPlanner.fromSingle("id.key", new byte[]{1, 2}).items);
        items.addAll(ImportPlanner.fromSingle("a.md", "ok".getBytes(StandardCharsets.UTF_8)).items);
        final ImportPlanner.Report report = ImportPlanner.addTo(db, items);
        assertEquals(3, report.added);
        assertEquals(2, report.warned);
    }

    @Test
    public void importRefusesWhenTheDatabaseIsFull() throws Exception {
        final DatabaseForNotes db = new DatabaseForNotes();
        final byte[] big = new byte[(int) FileKinds.MAX_FILE_BYTES];
        final List<ImportPlanner.Item> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(new ImportPlanner.Item(true, "big" + i + ".txt", "", false, null, big,
                    false, false, true));
        }
        try {
            ImportPlanner.addTo(db, items);
            fail("the plan does not fit and must be refused");
        } catch (ImportPlanner.ImportException expected) {
            assertEquals(0, db.size());
        }
    }
}
