/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

/**
 * When the vault locks after it has been left alone. A timeout of zero means it never locks for
 * idleness; the screen-off lock still applies.
 */
public final class IdleRules {

    private IdleRules() {
    }

    /** True when the timeout is set and the time without any touch has reached it. */
    public static boolean isExpired(final long idleMs, final long timeoutMs) {
        return timeoutMs > 0 && idleMs >= timeoutMs;
    }

    /** How long is left before the timeout, never below zero. Zero when the timeout is off. */
    public static long remaining(final long idleMs, final long timeoutMs) {
        if (timeoutMs <= 0) {
            return 0;
        }
        return Math.max(0, timeoutMs - idleMs);
    }
}
