/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

/**
 * The rule for clearing a copy. Only the copy that is still stored is cleared, and only when its
 * time has passed. Kept free of Android types so it can be tested on the JVM.
 */
final class ClipboardExpiry {

    private ClipboardExpiry() {
    }

    /**
     * True when the copy with identifier {@code uid} should be cleared now. A timer from an older
     * copy has an old identifier, so it never clears the text of a newer copy.
     */
    static boolean isDue(final String storedUid, final String uid, final long expiresAt, final long now) {
        return uid != null && uid.equals(storedUid) && expiresAt > 0 && now >= expiresAt;
    }
}
