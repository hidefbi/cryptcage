/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.Locale;
import java.util.UUID;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.cryptcage.R;

/**
 * Copies text to the clipboard and clears it after the time set in the settings. Only the latest
 * copy is cleared: an older timer finds a different token and does nothing.
 */
final class ClipboardTimer {

    static final String KEY_SECONDS = "pref_clipboardTimeout";
    static final int DEFAULT_SECONDS = 20;
    static final int MAX_SECONDS = 3600;

    static final String PREFS = "clipboard_clean";
    static final String KEY_TOKEN = "latest_token";
    static final String KEY_EXPIRES = "expires_at";
    static final String EXTRA_TOKEN = "com.cryptcage.EXTRA_CLIPBOARD_TOKEN";

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

    static void copy(final Context context, final String text) {
        final Context app = context.getApplicationContext();
        final ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(app.getString(R.string.app_name), text));
        final SharedPreferences prefs = prefs(app);
        final int seconds = seconds(app);
        if (seconds == 0) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_EXPIRES).apply();
            return;
        }
        final String token = UUID.randomUUID().toString();
        final long expires = System.currentTimeMillis() + seconds * 1000L;
        prefs.edit().putString(KEY_TOKEN, token).putLong(KEY_EXPIRES, expires).apply();

        final Intent intent = new Intent(app, ClipboardCleanReceiver.class).putExtra(EXTRA_TOKEN, token);
        final PendingIntent pending = PendingIntent.getBroadcast(app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expires, pending);
        } else {
            alarms.setExact(AlarmManager.RTC_WAKEUP, expires, pending);
        }
    }

    /** Clears the clipboard if the timer of the latest copy has run out. Called when the app comes back. */
    static void clearIfExpired(final Context context) {
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = prefs(app);
        if (prefs.getString(KEY_TOKEN, null) != null
                && System.currentTimeMillis() >= prefs.getLong(KEY_EXPIRES, 0L)) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_EXPIRES).apply();
            clear(app);
        }
    }

    /** Called by the alarm. Clears only when the token is still the latest one. */
    static void clearIfLatest(final Context context, final String token) {
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = prefs(app);
        if (token.equals(prefs.getString(KEY_TOKEN, null))) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_EXPIRES).apply();
            clear(app);
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
