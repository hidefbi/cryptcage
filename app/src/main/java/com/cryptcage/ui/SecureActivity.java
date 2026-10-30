/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cryptcage.app.App;

/**
 * Base for every screen. Blocks screenshots and the recent-apps preview, and returns to the
 * database picker when the vault is locked. The window keeps content clear of the system bars,
 * so no extra inset padding is applied here. The background comes from the theme, so the
 * screen is light or dark as a whole.
 */
public abstract class SecureActivity extends AppCompatActivity {

    /** Extra key for the id of a note. */
    public static final String EXTRA_NOTE_ID = "note_id";

    /** Screens that show note data return true. The database picker returns false. */
    protected boolean needsVault() {
        return true;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setBackgroundColor(themeBackground());
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
        final TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, value, true);
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
