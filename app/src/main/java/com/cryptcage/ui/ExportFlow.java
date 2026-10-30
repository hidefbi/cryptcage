package com.cryptcage.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.EncryptedZip;
import com.cryptcage.utils.PasswordPolicy;
import com.cryptcage.utils.ZipExport;
import com.cryptcage.R;

/**
 * Asks for plain or encrypted export, then writes a ZIP of the chosen entries
 * to a location the user picks. Entry names are kept as they are.
 */
final class ExportFlow {

    private static final String MIME_ZIP = "application/zip";

    private final AppCompatActivity activity;
    private final ActivityResultLauncher<String> launcher;
    private List<Long> pendingIds;
    private boolean pendingEncrypted;
    private char[] pendingPassword;

    ExportFlow(final AppCompatActivity activity) {
        this.activity = activity;
        this.launcher = activity.registerForActivityResult(
                new ActivityResultContracts.CreateDocument(MIME_ZIP),
                uri -> onTarget(uri));
    }

    /** Starts an export of the given entry ids. Must be called on the UI thread. */
    void start(final List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            toast(R.string.toast_nothingSelected);
            return;
        }
        pendingIds = new ArrayList<>(ids);
        final String[] modes = {
                activity.getString(R.string.export_plain),
                activity.getString(R.string.export_encrypted)
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.export_mode_title)
                .setItems(modes, (dialog, which) -> {
                    if (which == 0) {
                        pendingEncrypted = false;
                        launcher.launch(fileName(false));
                    } else {
                        askPassword();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> pendingIds = null)
                .show();
    }

    private void askPassword() {
        final int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        final LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, 0);
        final EditText first = passwordField(R.string.hint_field_new_password);
        final EditText second = passwordField(R.string.hint_field_conf_password);
        layout.addView(first);
        layout.addView(second);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.export_password_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, (d, which) -> pendingIds = null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String a = first.getText().toString();
            final String b = second.getText().toString();
            final PasswordPolicy.Result result = PasswordPolicy.check(a);
            if (result == PasswordPolicy.Result.TOO_SHORT) {
                first.setError(activity.getString(R.string.pw_too_short, PasswordPolicy.MIN_LENGTH));
                return;
            }
            if (result == PasswordPolicy.Result.TOO_SIMPLE) {
                first.setError(activity.getString(R.string.pw_too_simple));
                return;
            }
            if (!a.equals(b)) {
                second.setError(activity.getString(R.string.toast_wrongNewPassword));
                return;
            }
            pendingPassword = a.toCharArray();
            pendingEncrypted = true;
            dialog.dismiss();
            launcher.launch(fileName(true));
        });
    }

    private EditText passwordField(final int hint) {
        final EditText field = new EditText(activity);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return field;
    }

    private static String fileName(final boolean encrypted) {
        return encrypted ? "cryptcage-export-encrypted.zip" : "cryptcage-export.zip";
    }

    private void onTarget(final Uri uri) {
        final List<Long> ids = pendingIds;
        final boolean encrypted = pendingEncrypted;
        final char[] password = pendingPassword;
        pendingIds = null;
        pendingPassword = null;
        pendingEncrypted = false;
        final App.Session session = App.session();
        if (uri == null || ids == null) {
            wipe(password);
            return;
        }
        if (session == null) {
            wipe(password);
            toast(R.string.toast_locked);
            return;
        }
        final Context app = activity.getApplicationContext();
        Tasks.io(() -> {
            final File temp = new File(app.getCacheDir(), "export-" + System.nanoTime() + ".zip");
            boolean ok;
            try {
                final ZipExport.Plan plan = ZipExport.collect(session.vault, ids);
                if (encrypted) {
                    EncryptedZip.write(temp, plan, password);
                } else {
                    ZipExport.writePlain(temp, plan);
                }
                ok = copyTo(app, temp, uri);
            } catch (Exception e) {
                ok = false;
            } finally {
                temp.delete();
                wipe(password);
            }
            final boolean success = ok;
            Tasks.ui(() -> Toast.makeText(app,
                    success ? R.string.toast_zipSaved : R.string.toast_exportFailed, Toast.LENGTH_LONG).show());
        });
    }

    private static boolean copyTo(final Context app, final File source, final Uri uri) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = app.getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                return false;
            }
            final byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        }
    }

    private static void wipe(final char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, '\0');
        }
    }

    private void toast(final int message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
