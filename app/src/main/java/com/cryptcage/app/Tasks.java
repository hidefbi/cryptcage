/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.app;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two threads only: one worker that does every file and key operation in order,
 * and the main thread for UI work. Running all vault I/O on one thread keeps
 * saves from overlapping and makes locking safe (see {@link App#lock()}).
 */
public final class Tasks {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Tasks() {
    }

    public static void io(final Runnable task) {
        IO.execute(task);
    }

    public static void ui(final Runnable task) {
        UI.post(task);
    }

    public static void uiDelayed(final Runnable task, final long delayMs) {
        UI.postDelayed(task, delayMs);
    }

    public static void cancelUi(final Runnable task) {
        UI.removeCallbacks(task);
    }
}
