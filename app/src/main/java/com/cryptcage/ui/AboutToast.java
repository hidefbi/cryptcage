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
            text = context.getString(R.string.toast_version) + " " + info.versionName
                    + context.getString(R.string.toast_createdBy);
        } catch (PackageManager.NameNotFoundException e) {
            text = context.getString(R.string.toast_errorVersion);
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }
}
