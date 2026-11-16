/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Copies the chosen entries into a new vault file with its own password. The result is an
 * ordinary vault, so it can be imported back with the same password. Names, bodies and tags are
 * all encrypted, and the copy is read back once before it is handed over.
 */
public final class VaultExport {

    private VaultExport() {
    }

    public static void write(final File out, final VaultSession source, final List<Long> ids,
                             final char[] password) throws VaultException, IOException {
        if (out.exists() && !out.delete()) {
            throw new IOException("cannot replace " + out.getName());
        }
        try {
            try (VaultSession copy = VaultSession.create(out, password)) {
                for (final Long id : ids) {
                    final VaultSession.Summary entry = source.summary(id);
                    if (entry.file) {
                        copy.addFile(entry.fileName, source.fileData(id));
                    } else {
                        copy.addNote(entry.title, source.body(id), entry.tags);
                    }
                }
                copy.save();
            }
            try (VaultSession check = VaultSession.open(out, password)) {
                final int count = check.list().size();
                if (count != ids.size()) {
                    throw new IOException("copy holds " + count + " entries, expected " + ids.size());
                }
            }
        } catch (VaultException | IOException | RuntimeException e) {
            out.delete();
            throw e;
        }
    }
}
