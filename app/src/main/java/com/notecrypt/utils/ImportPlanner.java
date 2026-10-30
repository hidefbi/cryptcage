package com.notecrypt.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns imported files into notes and files of a vault.
 *
 * An archive made by this app (it contains the manifest) is opened and its notes and files
 * are restored. Any other archive is stored as one file and is not opened.
 * A file whose content does not match its extension is stored as a binary file with a warning.
 * Vault files (.ncf) are handled by the caller, because they replace a whole database.
 */
public final class ImportPlanner {

    /** Most entries an archive may hold. */
    public static final int MAX_ENTRIES = 1000;
    /** Uncompressed size above which the compression ratio is checked. */
    private static final long RATIO_CHECK_FROM = 1024L * 1024L;
    /** A ratio above this is treated as an archive bomb. */
    private static final long MAX_RATIO = 100L;
    private static final long RECORD_OVERHEAD = 64L;

    private ImportPlanner() {
    }

    /** One note or file, ready to be added to a database. */
    public static final class Item {
        public final boolean file;
        public final String title;
        public final String tags;
        public final boolean starred;
        public final String text;
        public final byte[] data;
        /** The content does not agree with the extension. */
        public final boolean mismatch;
        /** A key or certificate. */
        public final boolean secret;
        /** Text that may be opened in the editor. */
        public final boolean editable;

        Item(final boolean file, final String title, final String tags, final boolean starred,
             final String text, final byte[] data, final boolean mismatch, final boolean secret,
             final boolean editable) {
            this.file = file;
            this.title = title;
            this.tags = tags;
            this.starred = starred;
            this.text = text;
            this.data = data;
            this.mismatch = mismatch;
            this.secret = secret;
            this.editable = editable;
        }

        boolean warns() {
            return mismatch || secret;
        }
    }

    /** What was found in an input, and how many entries were left out. */
    public static final class Plan {
        public final List<Item> items;
        public final int skipped;

        Plan(final List<Item> items, final int skipped) {
            this.items = items;
            this.skipped = skipped;
        }
    }

    /** Result of adding a plan to a database. */
    public static final class Report {
        public final int added;
        /** How many of the added items carry a warning. */
        public final int warned;

        Report(final int added, final int warned) {
            this.added = added;
            this.warned = warned;
        }
    }

    /** A rejected input. The message is for developers; the screen shows a translated text. */
    public static final class ImportException extends Exception {
        private static final long serialVersionUID = 1L;

        public ImportException(final String message) {
            super(message);
        }
    }

    /** Imports a file of any supported type. The name is the original file name. */
    public static Plan fromFile(final String name, final File file) throws ImportException, IOException {
        if (FileTypes.extensionOf(name).equals("zip")) {
            return fromZip(name, file);
        }
        if (file.length() > FileKinds.MAX_FILE_BYTES) {
            throw new ImportException("too large");
        }
        return fromSingle(name, readAll(file));
    }

    /** Imports one file that is not an archive. */
    public static Plan fromSingle(final String name, final byte[] data) throws ImportException {
        final String ext = FileTypes.extensionOf(name);
        if (ext.isEmpty()) {
            throw new ImportException("no extension");
        }
        if (ext.equals("ncf")) {
            throw new ImportException("vault file");
        }
        if (data == null || data.length == 0) {
            throw new ImportException("empty");
        }
        if (data.length > FileKinds.MAX_FILE_BYTES) {
            throw new ImportException("too large");
        }
        if (FileTypes.kindOf(name) == null) {
            throw new ImportException("unsupported type");
        }
        if (ext.equals("md") && FileKinds.isUtf8Text(data)) {
            return new Plan(oneItem(noteFrom(stem(name), data, "", false)), 0);
        }
        return new Plan(oneItem(fileItem(name, data)), 0);
    }

    /** Reads a ZIP file. A manifest-less archive is stored whole; a NoteCrypt archive is opened. */
    public static Plan fromZip(final String name, final File zip) throws ImportException, IOException {
        try (ZipFile archive = new ZipFile(zip)) {
            if (archive.size() > MAX_ENTRIES) {
                throw new ImportException("too many entries");
            }
            final ZipEntry manifestEntry = findManifest(archive);
            if (manifestEntry == null) {
                if (zip.length() > FileKinds.MAX_FILE_BYTES) {
                    throw new ImportException("too large");
                }
                return new Plan(oneItem(fileItem(name, readAll(zip))), 0);
            }
            return openNoteCryptArchive(archive, manifestEntry);
        }
    }

    /** Adds the plan to the database. Names that already exist get a number, as in "Note (2)". */
    public static Report addTo(final DatabaseForNotes db, final List<Item> items) throws ImportException {
        long needed = 0;
        for (Item item : items) {
            needed += itemBytes(item);
        }
        synchronized (db) {
            if (db.estimatedBytes() + needed > DatabaseForNotes.CONTENT_BUDGET) {
                throw new ImportException("not enough room in this database");
            }
            final Set<String> taken = new HashSet<>();
            for (Long id : db.allIds()) {
                final Notes note = db.get(id);
                taken.add(key(note.isFile(), note.isFile() ? note.getFileName() : note.getTitle()));
            }
            int added = 0;
            int warned = 0;
            for (Item item : items) {
                if (item.file) {
                    db.addFile(unique(taken, true, item.title), item.data);
                } else {
                    final long id = db.add(unique(taken, false, item.title), item.text, item.tags);
                    if (item.starred) {
                        db.setStarred(id, true);
                    }
                }
                added++;
                if (item.warns()) {
                    warned++;
                }
            }
            return new Report(added, warned);
        }
    }

    private static final class Pending {
        final String name;
        final byte[] data;

        Pending(final String name, final byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    private static final class Meta {
        final boolean file;
        final String title;
        final String tags;
        final boolean starred;

        Meta(final boolean file, final String title, final String tags, final boolean starred) {
            this.file = file;
            this.title = title;
            this.tags = tags;
            this.starred = starred;
        }
    }

    private static ZipEntry findManifest(final ZipFile archive) {
        final Enumeration<? extends ZipEntry> entries = archive.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && baseName(entry.getName()).equals(ZipExport.MANIFEST_NAME)) {
                return entry;
            }
        }
        return null;
    }

    private static Plan openNoteCryptArchive(final ZipFile archive, final ZipEntry manifestEntry)
            throws ImportException, IOException {
        long total = 0;
        int skipped = 0;
        final List<Pending> pending = new ArrayList<>();
        Map<String, Meta> meta = new HashMap<>();
        final Enumeration<? extends ZipEntry> entries = archive.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            final String name = baseName(entry.getName());
            final long size = entry.getSize();
            if (name.isEmpty() || size < 0) {
                skipped++;
                continue;
            }
            final long packed = entry.getCompressedSize();
            if (size > RATIO_CHECK_FROM && packed > 0 && size / packed > MAX_RATIO) {
                throw new ImportException("compression ratio too high");
            }
            if (size > FileKinds.MAX_FILE_BYTES) {
                skipped++;
                continue;
            }
            total += size;
            if (total > DatabaseForNotes.CONTENT_BUDGET) {
                throw new ImportException("archive is larger than a database can hold");
            }
            final byte[] data = readEntry(archive, entry);
            if (name.equals(ZipExport.MANIFEST_NAME)) {
                meta = readManifest(data);
            } else {
                pending.add(new Pending(name, data));
            }
        }
        final List<Item> items = new ArrayList<>();
        for (Pending p : pending) {
            final Meta m = meta.get(p.name);
            final Item item = m == null ? null : itemFromMeta(m, p.data);
            if (item == null) {
                skipped++;
            } else {
                items.add(item);
            }
        }
        return new Plan(items, skipped);
    }

    private static Item itemFromMeta(final Meta meta, final byte[] data) {
        if (meta.file) {
            return fileItem(meta.title, data);
        }
        if (!FileKinds.isUtf8Text(data)) {
            return fileItem(meta.title == null ? "note.txt" : meta.title + ".txt", data);
        }
        return noteFrom(meta.title, data, meta.tags, meta.starred);
    }

    /** A file item. The name is cleaned, and the content is checked against the extension. */
    private static Item fileItem(final String name, final byte[] data) {
        final FileTypes.Check check = FileTypes.check(name, data);
        final String cleaned = cleanName(name);
        return new Item(true, cleaned, "", false, null, data,
                !check.matches, check.secret(), check.editable());
    }

    private static Item noteFrom(final String title, final byte[] data, final String tags, final boolean starred) {
        final String cleanTitle = title == null || title.trim().isEmpty() ? "untitled" : title.trim();
        return new Item(false, cleanTitle, DatabaseForNotes.cleanTags(tags), starred,
                new String(data, StandardCharsets.UTF_8), null, false, false, true);
    }

    private static List<Item> oneItem(final Item item) {
        final List<Item> items = new ArrayList<>();
        items.add(item);
        return items;
    }

    private static Map<String, Meta> readManifest(final byte[] data) {
        final Map<String, Meta> out = new HashMap<>();
        try {
            final Object root = JsonLite.parse(new String(data, StandardCharsets.UTF_8));
            if (!(root instanceof Map)) {
                return out;
            }
            final Object items = ((Map<?, ?>) root).get("items");
            if (!(items instanceof List)) {
                return out;
            }
            for (Object row : (List<?>) items) {
                if (!(row instanceof Map)) {
                    continue;
                }
                final Map<?, ?> map = (Map<?, ?>) row;
                final Object entry = map.get("entry");
                if (!(entry instanceof String)) {
                    continue;
                }
                final boolean file = "file".equals(map.get("kind"));
                final Object title = map.get("title");
                final Object tags = map.get("tags");
                final boolean starred = Boolean.TRUE.equals(map.get("starred"));
                out.put(baseName((String) entry), new Meta(file,
                        title instanceof String ? (String) title : null,
                        tags instanceof String ? (String) tags : "", starred));
            }
        } catch (IllegalArgumentException ignored) {
            // a damaged manifest is ignored; no entry is then restored
        }
        return out;
    }

    private static byte[] readEntry(final ZipFile archive, final ZipEntry entry) throws IOException {
        final int size = (int) entry.getSize();
        final byte[] data = new byte[size];
        try (InputStream in = archive.getInputStream(entry)) {
            int offset = 0;
            while (offset < size) {
                final int n = in.read(data, offset, size - offset);
                if (n < 0) {
                    throw new IOException("entry is shorter than declared: " + entry.getName());
                }
                offset += n;
            }
            if (in.read() != -1) {
                throw new IOException("entry is longer than declared: " + entry.getName());
            }
        }
        return data;
    }

    private static byte[] readAll(final File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String baseName(final String name) {
        if (name == null) {
            return "";
        }
        final String unified = name.replace('\\', '/');
        final String base = unified.substring(unified.lastIndexOf('/') + 1).trim();
        return base.equals(".") || base.equals("..") ? "" : base;
    }

    /** A safe file name that keeps its extension. */
    static String cleanName(final String raw) {
        final String base = baseName(raw);
        final String ext = FileTypes.extensionOf(base);
        final String stem = ext.isEmpty() ? base : base.substring(0, base.length() - ext.length() - 1);
        final String safe = StringMethods.sanitizeName(stem);
        if (safe == null) {
            return ext.isEmpty() ? "file" : "file." + ext;
        }
        return ext.isEmpty() ? safe : safe + "." + ext;
    }

    private static String stem(final String name) {
        final String base = baseName(name);
        final int dot = base.lastIndexOf('.');
        final String stem = dot > 0 ? base.substring(0, dot) : base;
        return stem.trim().isEmpty() ? "untitled" : stem.trim();
    }

    private static String key(final boolean file, final String name) {
        return (file ? "F:" : "N:") + (name == null ? "" : name.toLowerCase(Locale.ROOT));
    }

    private static String unique(final Set<String> taken, final boolean file, final String wanted) {
        String candidate = wanted;
        String stem = wanted;
        String ext = "";
        if (file) {
            final int dot = wanted.lastIndexOf('.');
            if (dot > 0) {
                stem = wanted.substring(0, dot);
                ext = wanted.substring(dot);
            }
        }
        int n = 2;
        while (taken.contains(key(file, candidate))) {
            candidate = stem + " (" + n + ")" + ext;
            n++;
        }
        taken.add(key(file, candidate));
        return candidate;
    }

    private static long itemBytes(final Item item) {
        if (item.file) {
            return bytes(item.title) + item.data.length + RECORD_OVERHEAD;
        }
        return bytes(item.title) + bytes(item.text) + bytes(item.tags) + RECORD_OVERHEAD;
    }

    private static long bytes(final String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
