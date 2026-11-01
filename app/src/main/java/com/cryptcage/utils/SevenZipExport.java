/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;

/**
 * Writes an export as an unencrypted 7z archive, with the same entries and manifest as a ZIP.
 *
 * Encrypted 7z is not written here: the library in use reads encrypted 7z files but cannot write
 * them. The writer uses java.nio.file, which Android has from version 8.0, so ExportFlow only
 * offers this format there.
 */
public final class SevenZipExport {

    private SevenZipExport() {
    }

    /** Writes a 7z archive without a password, compressed with LZMA2. */
    public static void writePlain(final File out, final ZipExport.Plan plan) throws IOException {
        write(out, plan, SevenZMethod.LZMA2);
    }

    /** The method is a parameter so the tests can write without the xz library, which LZMA2 needs. */
    static void write(final File out, final ZipExport.Plan plan, final SevenZMethod method) throws IOException {
        try (SevenZOutputFile archive = new SevenZOutputFile(out)) {
            archive.setContentMethods(Collections.singletonList(new SevenZMethodConfiguration(method)));
            for (ZipExport.Entry entry : plan.entries) {
                add(archive, entry.name, entry.bytes);
            }
            add(archive, ZipExport.MANIFEST_NAME, ZipExport.manifest(plan.records, false));
        }
    }

    private static void add(final SevenZOutputFile archive, final String name, final byte[] bytes)
            throws IOException {
        final SevenZArchiveEntry entry = new SevenZArchiveEntry();
        entry.setName(name);
        entry.setDirectory(false);
        entry.setSize(bytes.length);
        entry.setHasStream(bytes.length > 0);
        archive.putArchiveEntry(entry);
        if (bytes.length > 0) {
            archive.write(bytes);
        }
        archive.closeArchiveEntry();
    }
}
