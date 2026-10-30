package com.notecrypt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.notecrypt.app.App;

/**
 * Base for every screen. Blocks screenshots and the recent-apps preview, keeps the content
 * clear of the system bars, and returns to the database picker when the vault is locked.
 * The background comes from the theme, so the screen is light or dark as a whole.
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
        applySystemBarInsets();
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        final View content = findViewById(android.R.id.content);
        if (content != null) {
            content.setBackgroundColor(themeBackground());
        }
    }

    private void applySystemBarInsets() {
        final View decor = getWindow().getDecorView();
        decor.setBackgroundColor(themeBackground());
        ViewCompat.setOnApplyWindowInsetsListener(decor, (view, insets) -> {
            final Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private int themeBackground() {
        final TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, value, true);
        return value.data;
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
