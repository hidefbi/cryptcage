/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.cryptcage.R;

final class AboutToast {

    private AboutToast() {
    }

    static void show(final Context context) {
        String text;
        try {
            final PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            text = context.getString(R.string.toast_version) + " " + info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            text = context.getString(R.string.toast_errorVersion);
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }
}
