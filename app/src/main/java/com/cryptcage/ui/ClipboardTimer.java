/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.Locale;
import java.util.UUID;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.cryptcage.R;

/**
 * Clears text copied inside CryptCage after the time set in the settings. Every copy gets its own
 * identifier and restarts the timer. The clear checks that identifier, so a timer from an older copy
 * cannot clear a newer one. If the time passes while the app is in the background, the clear is
 * done when an activity starts again.
 */
final class ClipboardTimer {

    static final String KEY_SECONDS = "pref_clipboardTimeout";
    static final int DEFAULT_SECONDS = 20;
    static final int MAX_SECONDS = 3600;

    static final String PREFS = "clipboard_clean";
    static final String KEY_UID = "copy_uid";
    static final String KEY_EXPIRES = "expires_at";

    private static final String TAG = "CryptCageClipboard";
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

    /** Copies the text and restarts the timer with a new identifier for this copy. */
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
            prefs(app).edit().remove(KEY_UID).remove(KEY_EXPIRES).apply();
            Log.i(TAG, "copy: timeout is 0, not cleared");
            return;
        }
        final String uid = UUID.randomUUID().toString();
        final long expires = System.currentTimeMillis() + seconds * 1000L;
        prefs(app).edit().putString(KEY_UID, uid).putLong(KEY_EXPIRES, expires).apply();
        Log.i(TAG, "copy: uid=" + uid + " seconds=" + seconds);
        pending = () -> {
            pending = null;
            Log.i(TAG, "timer fired: uid=" + uid);
            expire(app, uid);
        };
        HANDLER.postDelayed(pending, seconds * 1000L);
    }

    /** Clears the clipboard if the time of the last copy has passed. Called when an activity starts. */
    static void clearIfExpired(final Context context) {
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = prefs(app);
        final String uid = prefs.getString(KEY_UID, null);
        if (uid != null && ClipboardExpiry.isDue(uid, uid, prefs.getLong(KEY_EXPIRES, 0L),
                System.currentTimeMillis())) {
            cancelPending();
            expire(app, uid);
        }
    }

    private static void expire(final Context app, final String uid) {
        final SharedPreferences prefs = prefs(app);
        final String stored = prefs.getString(KEY_UID, null);
        final long expires = prefs.getLong(KEY_EXPIRES, 0L);
        if (!ClipboardExpiry.isDue(stored, uid, expires, System.currentTimeMillis())) {
            Log.i(TAG, "expire: uid=" + uid + " not due (stored=" + stored + ")");
            return;
        }
        prefs.edit().remove(KEY_UID).remove(KEY_EXPIRES).apply();
        clear(app);
        Log.i(TAG, "expire: cleared uid=" + uid);
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
