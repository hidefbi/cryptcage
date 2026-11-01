/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.cryptcage.app.App;
import com.cryptcage.utils.IdleRules;

/**
 * Locks the vault after the time set in the settings with no touch or key press. The rule follows
 * KeePassPN: each touch records the time, and a check at the end of the timeout either locks the
 * vault or waits for the time that is left. The timer keeps running while the app is in the
 * background. A dialog that is open on the front screen counts as activity, because its taps do
 * not reach the screen underneath.
 */
final class IdleLock {

    static final String KEY = "pref_autoLock";
    static final String DEFAULT_SECONDS = "300";

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable CHECK = () -> {
        scheduled = false;
        check();
    };

    private static long lastTouch;
    private static boolean scheduled;
    private static Context appContext;
    private static SecureActivity front;
    /** The session the times above belong to. A new unlock starts the count again. */
    private static App.Session seen;

    private IdleLock() {
    }

    /** The timeout in milliseconds from the settings. Zero means the idle lock is off. */
    static long timeoutMs(final Context context) {
        final String value = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY, DEFAULT_SECONDS);
        try {
            return Math.max(0, Long.parseLong(value)) * 1000L;
        } catch (NumberFormatException e) {
            return Long.parseLong(DEFAULT_SECONDS) * 1000L;
        }
    }

    /**
     * Called when a screen comes to the front. If the time has already passed, the vault is
     * locked here, and the screen sends the user to the picker in its own onResume.
     */
    static void resume(final SecureActivity activity) {
        front = activity;
        syncSession();
        if (App.isUnlocked() && IdleRules.isExpired(idleMs(), timeoutMs(activity))) {
            App.lock();
            return;
        }
        touch(activity);
    }

    /** Called when a screen goes to the back, so a dialog on it no longer counts. */
    static void pause(final SecureActivity activity) {
        if (front == activity) {
            front = null;
        }
    }

    /** Called on each touch and key press. Starts the timer when it is not running yet. */
    static void touch(final Context context) {
        syncSession();
        lastTouch = SystemClock.elapsedRealtime();
        if (scheduled || !App.isUnlocked()) {
            return;
        }
        final long timeout = timeoutMs(context);
        if (timeout > 0) {
            schedule(context.getApplicationContext(), timeout);
        }
    }

    private static void schedule(final Context app, final long delayMs) {
        appContext = app;
        scheduled = true;
        HANDLER.postDelayed(CHECK, delayMs);
    }

    private static void check() {
        if (appContext == null || !App.isUnlocked()) {
            return;
        }
        final long timeout = timeoutMs(appContext);
        if (timeout <= 0) {
            return;
        }
        final long idle = idleMs();
        if (!IdleRules.isExpired(idle, timeout)) {
            schedule(appContext, IdleRules.remaining(idle, timeout));
            return;
        }
        if (dialogOpen()) {
            lastTouch = SystemClock.elapsedRealtime();
            schedule(appContext, timeout);
            return;
        }
        App.lock();
        final SecureActivity screen = front;
        if (screen != null && screen.needsVault()) {
            screen.showDatabasePicker();
        }
    }

    /** Starts the count again when a different vault has been opened, so an old time does not lock it. */
    private static void syncSession() {
        final App.Session current = App.session();
        if (current != seen) {
            seen = current;
            lastTouch = SystemClock.elapsedRealtime();
        }
    }

    private static long idleMs() {
        return SystemClock.elapsedRealtime() - lastTouch;
    }

    private static boolean dialogOpen() {
        final SecureActivity screen = front;
        if (screen == null) {
            return false;
        }
        for (final Fragment fragment : screen.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof DialogFragment && fragment.isVisible()) {
                return true;
            }
        }
        return false;
    }
}
