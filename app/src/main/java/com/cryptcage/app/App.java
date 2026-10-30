package com.cryptcage.app;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.cryptcage.utils.DatabaseForNotes;
import com.cryptcage.utils.VaultException;
import com.cryptcage.utils.VaultFile;
import com.cryptcage.utils.VaultKey;
import com.cryptcage.R;

import java.io.File;

/**
 * Holds the unlocked vault for the whole process. Nothing is kept in Intents;
 * screens read the open database from here.
 *
 * The vault locks when any of these happens: the screen turns off, the last screen is
 * closed, or the app stays in the background for a minute. Locking saves pending changes
 * first and then overwrites the key bytes.
 */
public class App extends Application {

    /** How long the app may stay in the background before the vault locks itself. */
    private static final long AUTO_LOCK_MS = 60_000L;

    private static Session session;
    private static int startedActivities;
    private static int aliveActivities;

    private static final Runnable AUTO_LOCK = () -> lock();

    /** An unlocked vault: where it lives on disk, its notes, and the key used to save them. */
    public static final class Session {
        public final File file;
        public final DatabaseForNotes db;
        public final VaultKey key;

        public Session(final File file, final DatabaseForNotes db, final VaultKey key) {
            this.file = file;
            this.db = db;
            this.key = key;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(themeMode(
                PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "system")));
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(final Activity activity, final Bundle savedInstanceState) {
                synchronized (App.class) {
                    aliveActivities++;
                }
            }

            @Override
            public void onActivityStarted(final Activity activity) {
                synchronized (App.class) {
                    startedActivities++;
                }
                Tasks.cancelUi(AUTO_LOCK);
            }

            @Override
            public void onActivityStopped(final Activity activity) {
                boolean idle;
                synchronized (App.class) {
                    startedActivities--;
                    idle = startedActivities <= 0;
                }
                if (idle) {
                    Tasks.uiDelayed(AUTO_LOCK, AUTO_LOCK_MS);
                }
            }

            @Override
            public void onActivityDestroyed(final Activity activity) {
                boolean last;
                synchronized (App.class) {
                    aliveActivities--;
                    last = aliveActivities <= 0;
                }
                if (last && !activity.isChangingConfigurations()) {
                    lock();
                }
            }

            @Override
            public void onActivityResumed(final Activity activity) {
            }

            @Override
            public void onActivityPaused(final Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(final Activity activity, final Bundle outState) {
            }
        });

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                lock();
            }
        }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    /** Maps the saved preference value to an AppCompat night mode. */
    public static int themeMode(final String value) {
        if ("light".equals(value)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if ("dark".equals(value)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public static synchronized Session session() {
        return session;
    }

    public static synchronized boolean isUnlocked() {
        return session != null;
    }

    /** Makes the given vault the open one. The previous key (if any) is wiped. */
    public static void setSession(final Session next) {
        final Session previous;
        synchronized (App.class) {
            previous = session;
            session = next;
        }
        if (previous != null && previous.key != next.key) {
            Tasks.io(() -> previous.key.wipe());
        }
    }

    /**
     * Closes the open vault. A final save is queued first, so the last edit is never lost,
     * and the key is wiped once the worker thread has finished with it.
     */
    public static void lock() {
        final Session closing;
        synchronized (App.class) {
            closing = session;
            session = null;
        }
        Tasks.cancelUi(AUTO_LOCK);
        if (closing == null) {
            return;
        }
        Tasks.io(() -> {
            try {
                VaultFile.save(closing.file, closing.db, closing.key);
            } catch (VaultException ignored) {
                // the previous good file is still on disk; nothing else to do here
            } finally {
                closing.key.wipe();
            }
        });
    }

    /**
     * Saves the open vault on the worker thread. The session is read when the task runs,
     * so a save queued before a password change still uses the key that is current then.
     */
    public static void persist(final Context context) {
        final Context app = context.getApplicationContext();
        Tasks.io(() -> {
            final Session current = session();
            if (current == null) {
                return;
            }
            try {
                VaultFile.save(current.file, current.db, current.key);
            } catch (VaultException e) {
                Tasks.ui(() -> Toast.makeText(app, R.string.toast_saveFailed, Toast.LENGTH_LONG).show());
            }
        });
    }
}
