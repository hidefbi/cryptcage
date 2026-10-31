/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.cryptcage.app.App;
import com.cryptcage.R;

/**
 * Base for every screen. Blocks screenshots and the recent-apps preview, and returns to the
 * database picker when the vault is locked. The window keeps content clear of the system bars,
 * so no extra inset padding is applied here. The background comes from the theme, so the
 * screen is light or dark as a whole.
 */
public abstract class SecureActivity extends AppCompatActivity {

    /** Extra key for the id of a note. */
    public static final String EXTRA_NOTE_ID = "note_id";

    /** Whether the sand palette was applied when this screen was created. */
    private boolean paletteAtCreate;

    /** Screens that show note data return true. The database picker returns false. */
    protected boolean needsVault() {
        return true;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        applyScreenshotPolicy(this);
        ClipboardTimer.install(this);
        paletteAtCreate = App.usesSandPalette(this);
        if (paletteAtCreate) {
            getTheme().applyStyle(R.style.PaletteSand, true);
        }
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setBackgroundColor(themeBackground());
        applySystemBars();
    }

    /** Blocks screenshots and the recent-apps preview unless the screenshot mode is on. */
    static void applyScreenshotPolicy(final Activity activity) {
        final Window window = activity.getWindow();
        if (App.allowsScreenshots(activity)) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    /**
     * Screens without a toolbar at the top return true, so the status bar takes the background
     * colour. Screens with a toolbar keep the default, which is the surface colour.
     */
    protected boolean statusBarFollowsBackground() {
        return false;
    }

    /**
     * Colours the status bar to match the top of the screen, so no seam appears between them,
     * and sets the status icons to dark on light screens and light in night mode.
     */
    private void applySystemBars() {
        final Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(statusBarFollowsBackground()
                ? themeColor(android.R.attr.colorBackground)
                : themeColor(com.google.android.material.R.attr.colorSurface));
        final boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(window, window.getDecorView())
                .setAppearanceLightStatusBars(!night);
    }

    @Override
    protected void onPause() {
        ClipboardTimer.setForeground(false);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyScreenshotPolicy(this);
        ClipboardTimer.setForeground(true);
        ClipboardTimer.clearIfExpired(this);
        // The colour palette may have been changed on another screen while this one was in the back.
        if (paletteAtCreate != App.usesSandPalette(this)) {
            recreate();
        }
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        final View content = findViewById(android.R.id.content);
        if (content != null) {
            content.setBackgroundColor(themeBackground());
        }
    }

    private int themeBackground() {
        return themeColor(android.R.attr.colorBackground);
    }

    private int themeColor(final int attr) {
        final TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    /** The drawable that a theme attribute points to, such as a toolbar icon. */
    static Drawable themeDrawable(final Context context, final int attr) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return ContextCompat.getDrawable(context, value.resourceId);
    }

    /** Returns true if this screen was replaced by the database picker. */
    protected boolean redirectIfLocked() {
        if (needsVault() && !App.isUnlocked()) {
            final Intent intent = new Intent(this, SelectDatabaseActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }
}
