/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The file types CryptCage accepts. Each type has a category, a flag for the text editor,
 * and a content check so that a renamed file is noticed.
 */
public final class FileTypes {

    public enum Category {
        TEXT,
        IMAGE,
        PDF,
        OFFICE,
        ARCHIVE,
        PACKAGE,
        SECRET,
        DATABASE
    }

    /** One supported extension. */
    public static final class Kind {
        public final String extension;
        public final Category category;
        public final boolean editable;

        Kind(final String extension, final Category category, final boolean editable) {
            this.extension = extension;
            this.category = category;
            this.editable = editable;
        }
    }

    /** The outcome of checking a file against its extension. */
    public static final class Check {
        /** Null when the extension is not supported. */
        public final Kind kind;
        /** False when the content does not agree with the extension. */
        public final boolean matches;

        Check(final Kind kind, final boolean matches) {
            this.kind = kind;
            this.matches = matches;
        }

        /** Editing is offered only when the type allows it and the content agrees with it. */
        public boolean editable() {
            return kind != null && kind.editable && matches;
        }

        /** True for text of a supported text type, whether or not it may be edited. */
        public boolean text() {
            return kind != null && kind.category == Category.TEXT && matches;
        }

        /** Keys and certificates get a warning when they are imported. */
        public boolean secret() {
            return kind != null && kind.category == Category.SECRET;
        }
    }

    private static final Map<String, Kind> KINDS = new HashMap<>();

    static {
        for (String ext : new String[]{"txt", "md", "json", "ini", "yaml", "yml", "toml", "env", "xml",
                "html", "css", "sh", "ovpn", "conf", "config", "java", "kt", "py", "js", "ts", "svg"}) {
            add(ext, Category.TEXT, true);
        }
        for (String ext : new String[]{"pub", "pem", "crt", "cer"}) {
            add(ext, Category.TEXT, false);
        }
        for (String ext : new String[]{"key", "p12", "pfx", "der"}) {
            add(ext, Category.SECRET, false);
        }
        for (String ext : new String[]{"png", "jpg", "webp"}) {
            add(ext, Category.IMAGE, false);
        }
        add("pdf", Category.PDF, false);
        for (String ext : new String[]{"docx", "xlsx"}) {
            add(ext, Category.OFFICE, false);
        }
        for (String ext : new String[]{"zip", "7z", "rar", "tar", "gz", "tgz", "xz", "zst"}) {
            add(ext, Category.ARCHIVE, false);
        }
        for (String ext : new String[]{"aab", "apks", "jar", "aar", "dex", "so"}) {
            add(ext, Category.PACKAGE, false);
        }
        add("kdbx", Category.DATABASE, false);
    }

    private FileTypes() {
    }

    private static void add(final String ext, final Category category, final boolean editable) {
        KINDS.put(ext, new Kind(ext, category, editable));
    }

    /** The kind for a file name, judged by its extension. Null if the extension is not supported. */
    public static Kind kindOf(final String fileName) {
        final String ext = extensionOf(fileName);
        return ext.isEmpty() ? null : KINDS.get(ext);
    }

    /** Checks the bytes against the extension. */
    public static Check check(final String fileName, final byte[] data) {
        final Kind kind = kindOf(fileName);
        if (kind == null || data == null || data.length == 0) {
            return new Check(kind, false);
        }
        return new Check(kind, matchesSignature(kind, data));
    }

    /**
     * A file name that is safe to store: directories are dropped, the name is sanitized,
     * and the extension is kept in lower case. Null if the extension is not supported
     * or nothing usable is left of the name.
     */
    public static String cleanFileName(final String raw) {
        if (raw == null) {
            return null;
        }
        final String path = raw.replace('\\', '/');
        final String name = path.substring(path.lastIndexOf('/') + 1);
        final String ext = extensionOf(name);
        if (ext.isEmpty() || KINDS.get(ext) == null) {
            return null;
        }
        final String stem = name.substring(0, name.length() - ext.length() - 1);
        final String cleaned = StringMethods.sanitizeName(stem);
        return cleaned == null ? null : cleaned + "." + ext;
    }

    /** Lower-case extension without the dot, or an empty string. */
    public static String extensionOf(final String fileName) {
        if (fileName == null) {
            return "";
        }
        final String base = fileName.replace('\\', '/');
        final String name = base.substring(base.lastIndexOf('/') + 1);
        final int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesSignature(final Kind kind, final byte[] data) {
        switch (kind.category) {
            case TEXT:
                return FileKinds.isUtf8Text(data);
            case IMAGE:
                return imageMatches(kind.extension, data);
            case PDF:
                return startsWith(data, 0, "%PDF-");
            case OFFICE:
            case PACKAGE:
                return startsWithPackage(kind.extension, data);
            case DATABASE:
                // KDBX 3 and KDBX 4 files both begin with these two signatures.
                return startsWith(data, 0, "\u0003\u00D9\u00A2\u009A") && startsWith(data, 4, "\u0067\u00FB\u004B\u00B5");
            case ARCHIVE:
                return archiveMatches(kind.extension, data);
            case SECRET:
                return true;
            default:
                return false;
        }
    }

    private static boolean imageMatches(final String ext, final byte[] data) {
        switch (ext) {
            case "png":
                return data.length >= 8 && (data[0] & 0xff) == 0x89 && startsWith(data, 1, "PNG\r\n\u001a\n");
            case "jpg":
                return data.length >= 3 && (data[0] & 0xff) == 0xFF && (data[1] & 0xff) == 0xD8
                        && (data[2] & 0xff) == 0xFF;
            default:
                return startsWith(data, 0, "RIFF") && startsWith(data, 8, "WEBP");
        }
    }

    private static boolean startsWithPackage(final String ext, final byte[] data) {
        if (ext.equals("dex")) {
            return startsWith(data, 0, "dex\n");
        }
        if (ext.equals("so")) {
            return data.length >= 4 && data[0] == 0x7F && startsWith(data, 1, "ELF");
        }
        return startsWith(data, 0, "PK\u0003\u0004");
    }

    private static boolean archiveMatches(final String ext, final byte[] data) {
        switch (ext) {
            case "zip":
                return startsWith(data, 0, "PK\u0003\u0004") || startsWith(data, 0, "PK\u0005\u0006");
            case "7z":
                return data.length > 5 && (data[0] & 0xff) == 0x37 && (data[1] & 0xff) == 0x7A
                        && (data[2] & 0xff) == 0xBC && (data[3] & 0xff) == 0xAF;
            case "rar":
                return startsWith(data, 0, "Rar!\u001a\u0007");
            case "tar":
                return startsWith(data, 257, "ustar");
            case "gz":
            case "tgz":
                return data.length > 1 && (data[0] & 0xff) == 0x1F && (data[1] & 0xff) == 0x8B;
            case "xz":
                return data.length > 5 && (data[0] & 0xff) == 0xFD && startsWith(data, 1, "7zXZ\u0000");
            default:
                return data.length > 3 && (data[0] & 0xff) == 0x28 && (data[1] & 0xff) == 0xB5
                        && (data[2] & 0xff) == 0x2F && (data[3] & 0xff) == 0xFD;
        }
    }

    private static boolean startsWith(final byte[] data, final int offset, final String ascii) {
        final byte[] want = ascii.getBytes(StandardCharsets.ISO_8859_1);
        if (data.length < offset + want.length) {
            return false;
        }
        for (int i = 0; i < want.length; i++) {
            if (data[offset + i] != want[i]) {
                return false;
            }
        }
        return true;
    }
}
