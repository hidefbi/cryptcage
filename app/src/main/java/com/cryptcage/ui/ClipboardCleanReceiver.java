/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives the clipboard timer alarm. */
public class ClipboardCleanReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String token = intent.getStringExtra(ClipboardTimer.EXTRA_TOKEN);
        if (token != null) {
            ClipboardTimer.clearIfLatest(context, token);
        }
    }
}
