/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

/**
 * Remembers, outside the vault file, the newest generation seen for each vault path. A file that
 * comes back with an older generation has been replaced with an older copy, so it is refused.
 */
public interface GenerationGuard {

    /** The newest generation recorded for this path, or 0 when nothing is recorded. */
    long seen(String path);

    /** Records a generation for this path. */
    void remember(String path, long generation);

    /** Drops the record for this path. */
    void forget(String path);
}
