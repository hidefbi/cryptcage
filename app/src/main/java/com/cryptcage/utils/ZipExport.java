/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Chooses the entries for an export and writes them as a ZIP archive.
 * Every export is a ZIP, including exports of a single item.
 */
public final class ZipExport {

    public static final String MANIFEST_NAME = "cryptcage-manifest.json";

    /** One file inside the archive. */
    public static final class Entry {
        public final String name;
        public final byte[] bytes;

        Entry(final String name, final byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    /** One row of the manifest, describing an exported note or file. */
    public static final class Record {
        public final long id;
        public final String title;
        public final String tags;
        public final boolean starred;
        public final boolean file;
        public final String entryName;

        Record(final long id, final String title, final String tags, final boolean starred,
               final boolean file, final String entryName) {
            this.id = id;
            this.title = title;
            this.tags = tags;
            this.starred = starred;
            this.file = file;
            this.entryName = entryName;
        }
    }

    /** The entries and manifest rows for one export. */
    public static final class Plan {
        public final List<Entry> entries;
        public final List<Record> records;

        Plan(final List<Entry> entries, final List<Record> records) {
            this.entries = entries;
            this.records = records;
        }
    }

    private ZipExport() {
    }

    /**
     * Builds the plan for the given entry ids, in id order. Every id must exist: the ids come
     * from the list, so a missing one means the vault changed underneath the export.
     */
    public static Plan collect(final VaultSession vault, final Collection<Long> ids) throws VaultException {
        final List<Entry> entries = new ArrayList<>();
        final List<Record> records = new ArrayList<>();
        final Set<String> used = new HashSet<>();
        for (Long id : new TreeSet<>(ids)) {
            final VaultSession.Summary item = vault.summary(id);
            if (item.file) {
                final String name = unique(used, fileEntryName(item.fileName));
                entries.add(new Entry(name, vault.fileData(id)));
                records.add(new Record(id, item.title, "", false, true, name));
            } else {
                final String name = unique(used, noteEntryName(item.title));
                entries.add(new Entry(name, vault.body(id).getBytes(StandardCharsets.UTF_8)));
                records.add(new Record(id, item.title, item.tags, item.starred, false, name));
            }
        }
        return new Plan(entries, records);
    }

    /** Writes a ZIP without a password. */
    public static void writePlain(final File out, final Plan plan) throws IOException {
        final ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out));
        try {
            for (Entry entry : plan.entries) {
                zip.putNextEntry(new ZipEntry(entry.name));
                zip.write(entry.bytes);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(MANIFEST_NAME));
            zip.write(manifest(plan.records, false));
            zip.closeEntry();
        } finally {
            zip.close();
        }
    }

    /** The manifest as UTF-8 JSON. */
    public static byte[] manifest(final List<Record> records, final boolean encrypted) {
        final StringBuilder json = new StringBuilder();
        json.append("{\n  \"format\": \"cryptcage-export\",\n  \"version\": 1,\n  \"encrypted\": ")
                .append(encrypted).append(",\n  \"items\": [");
        for (int i = 0; i < records.size(); i++) {
            final Record r = records.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("\n    {\"id\": ").append(r.id)
                    .append(", \"kind\": \"").append(r.file ? "file" : "note").append('"')
                    .append(", \"entry\": ").append(quote(r.entryName))
                    .append(", \"title\": ").append(quote(r.title))
                    .append(", \"tags\": ").append(quote(r.tags))
                    .append(", \"starred\": ").append(r.starred)
                    .append('}');
        }
        json.append(records.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String quote(final String value) {
        if (value == null) {
            return "null";
        }
        final StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    static String fileEntryName(final String fileName) {
        final String cleaned = FileTypes.cleanFileName(fileName);
        return cleaned == null ? "attachment.bin" : cleaned;
    }

    static String noteEntryName(final String title) {
        final String base = StringMethods.sanitizeName(title);
        return (base == null ? "untitled" : base) + ".txt";
    }

    /** Adds a counter when two entries would have the same name (compared without case). */
    static String unique(final Set<String> used, final String name) {
        if (used.add(name.toLowerCase(Locale.ROOT))) {
            return name;
        }
        final int dot = name.lastIndexOf('.');
        final String stem = dot > 0 ? name.substring(0, dot) : name;
        final String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; ; i++) {
            final String candidate = stem + " (" + i + ")" + ext;
            if (used.add(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
    }
}
