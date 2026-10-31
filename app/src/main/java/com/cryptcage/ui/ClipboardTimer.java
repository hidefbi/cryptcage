/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.Locale;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import com.cryptcage.R;

/**
 * Clears text copied inside CryptCage after the time set in the settings. The timer runs while the
 * app is alive. If the time passes while the app is in the background, the clipboard is cleared
 * when the app comes back, because the system does not let a background app change the clipboard.
 */
final class ClipboardTimer {

    static final String KEY_SECONDS = "pref_clipboardTimeout";
    static final int DEFAULT_SECONDS = 20;
    static final int MAX_SECONDS = 3600;

    static final String PREFS = "clipboard_clean";
    static final String KEY_EXPIRES = "expires_at";

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable pending;

    private ClipboardTimer() {
    }

    static int seconds(final Context context) {
        final int value = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(KEY_SECONDS, DEFAULT_SECONDS);
        return Math.max(0, Math.min(value, MAX_SECONDS));
    }

    /** Saves the timeout in seconds, capped at the maximum. */
    static void save(final Context context, final int total) {
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit().putInt(KEY_SECONDS, Math.max(0, Math.min(total, MAX_SECONDS))).apply();
    }

    /** Shows a time as hours:minutes:seconds. */
    static String format(final int total) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60);
    }

    /** Copies the text. A new copy restarts the timer, so an older copy is never cleared early. */
    static void copy(final Context context, final String text) {
        final Context app = context.getApplicationContext();
        final ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(app.getString(R.string.app_name), text));
        cancelPending();
        final int seconds = seconds(app);
        if (seconds == 0) {
            prefs(app).edit().remove(KEY_EXPIRES).apply();
            return;
        }
        prefs(app).edit().putLong(KEY_EXPIRES, System.currentTimeMillis() + seconds * 1000L).apply();
        pending = () -> {
            pending = null;
            prefs(app).edit().remove(KEY_EXPIRES).apply();
            clear(app);
        };
        HANDLER.postDelayed(pending, seconds * 1000L);
    }

    /** Clears the clipboard if the time of the last copy has passed. Called when an activity starts. */
    static void clearIfExpired(final Context context) {
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = prefs(app);
        final long expires = prefs.getLong(KEY_EXPIRES, 0L);
        if (expires > 0 && System.currentTimeMillis() >= expires) {
            cancelPending();
            prefs.edit().remove(KEY_EXPIRES).apply();
            clear(app);
        }
    }

    private static void cancelPending() {
        if (pending != null) {
            HANDLER.removeCallbacks(pending);
            pending = null;
        }
    }

    private static void clear(final Context app) {
        final ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            clipboard.clearPrimaryClip();
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
        }
    }

    private static SharedPreferences prefs(final Context app) {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
