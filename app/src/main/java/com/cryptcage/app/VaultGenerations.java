/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.cryptcage.utils.GenerationGuard;

/** Keeps the newest vault generation seen per file, in its own preferences, outside the vault. */
public final class VaultGenerations implements GenerationGuard {

    private static final String PREFS = "vault_generations";

    private final SharedPreferences prefs;

    public VaultGenerations(final Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public long seen(final String path) {
        return prefs.getLong(path, 0L);
    }

    @Override
    public void remember(final String path, final long generation) {
        // Only ever move forward, so a late write cannot lower the record.
        if (generation > seen(path)) {
            prefs.edit().putLong(path, generation).apply();
        }
    }

    @Override
    public void forget(final String path) {
        prefs.edit().remove(path).apply();
    }
}
