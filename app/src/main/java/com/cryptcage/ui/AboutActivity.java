/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;

import com.cryptcage.R;

/** Version, licence and the donation addresses. Needs no vault, so it opens from the picker too. */
public class AboutActivity extends SecureActivity {

    @Override
    protected boolean needsVault() {
        return false;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setSupportActionBar(findViewById(R.id.toolbar));
        final ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
        ((TextView) findViewById(R.id.version)).setText(versionText());
        copyOnTap(R.id.donation_btc, R.string.donation_btc_address);
        copyOnTap(R.id.donation_eth, R.string.donation_eth_address);
        copyOnTap(R.id.donation_xmr, R.string.donation_xmr_address);
        bindLink(R.id.link_update, R.string.about_update, getString(R.string.about_update_hint),
                R.string.about_releases_url);
        bindLink(R.id.link_source, R.string.about_source,
                getString(R.string.about_repo_url).replace("https://", ""), R.string.about_repo_url);
        bindLink(R.id.link_feedback, R.string.about_feedback,
                getString(R.string.about_issues_url).replace("https://", ""), R.string.about_issues_url);
    }

    private void bindLink(final int row, final int title, final CharSequence subtitle, final int url) {
        final View view = findViewById(row);
        ((TextView) view.findViewById(R.id.link_title)).setText(title);
        ((TextView) view.findViewById(R.id.link_subtitle)).setText(subtitle);
        view.setOnClickListener(v -> openLink(url));
    }

    /** Opens the address in whatever browser the device has. The app itself makes no connection. */
    private void openLink(final int url) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(url)));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.about_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Copies through the app's own clipboard path, so the usual clipboard timeout applies. */
    private void copyOnTap(final int row, final int address) {
        findViewById(row).setOnClickListener(v -> {
            ClipboardTimer.copy(this, getString(address));
            Toast.makeText(this, R.string.about_copied, Toast.LENGTH_SHORT).show();
        });
    }

    private String versionText() {
        try {
            final PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return getString(R.string.toast_version) + " " + info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return getString(R.string.toast_errorVersion);
        }
    }
}
