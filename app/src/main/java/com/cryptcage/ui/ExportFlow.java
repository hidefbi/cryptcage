/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
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
import android.os.Build;
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
import com.cryptcage.utils.SevenZipExport;
import com.cryptcage.utils.ZipExport;
import com.cryptcage.R;

/**
 * Asks for the export format, then writes the chosen entries to a location the user picks.
 * Entry names are kept as they are. Formats: plain ZIP, encrypted ZIP, and plain 7z, which is
 * offered from Android 8.0 because its writer needs java.nio.file.
 */
final class ExportFlow {

    private static final String MIME_ZIP = "application/zip";
    private static final String MIME_7Z = "application/x-7z-compressed";

    private enum Format { ZIP_PLAIN, ZIP_ENCRYPTED, SEVEN_ZIP_PLAIN }

    private final AppCompatActivity activity;
    private final ActivityResultLauncher<String> zipLauncher;
    private final ActivityResultLauncher<String> sevenZipLauncher;
    private List<Long> pendingIds;
    private Format pendingFormat;
    private char[] pendingPassword;

    ExportFlow(final AppCompatActivity activity) {
        this.activity = activity;
        this.zipLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.CreateDocument(MIME_ZIP),
                uri -> onTarget(uri));
        this.sevenZipLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.CreateDocument(MIME_7Z),
                uri -> onTarget(uri));
    }

    /** Starts an export of the given entry ids. Must be called on the UI thread. */
    void start(final List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            toast(R.string.toast_nothingSelected);
            return;
        }
        pendingIds = new ArrayList<>(ids);
        final List<String> modes = new ArrayList<>();
        modes.add(activity.getString(R.string.export_plain));
        modes.add(activity.getString(R.string.export_encrypted));
        if (sevenZipAvailable()) {
            modes.add(activity.getString(R.string.export_7z));
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.export_mode_title)
                .setItems(modes.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        pendingFormat = Format.ZIP_PLAIN;
                        zipLauncher.launch(fileName(Format.ZIP_PLAIN));
                    } else if (which == 1) {
                        askPassword();
                    } else {
                        pendingFormat = Format.SEVEN_ZIP_PLAIN;
                        sevenZipLauncher.launch(fileName(Format.SEVEN_ZIP_PLAIN));
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> pendingIds = null)
                .show();
    }

    /** 7z is written with java.nio.file, which Android has from version 8.0. */
    private static boolean sevenZipAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
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
            pendingFormat = Format.ZIP_ENCRYPTED;
            dialog.dismiss();
            zipLauncher.launch(fileName(Format.ZIP_ENCRYPTED));
        });
    }

    private EditText passwordField(final int hint) {
        final EditText field = new EditText(activity);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return field;
    }

    private static String fileName(final Format format) {
        switch (format) {
            case ZIP_ENCRYPTED:
                return "cryptcage-export-encrypted.zip";
            case SEVEN_ZIP_PLAIN:
                return "cryptcage-export.7z";
            default:
                return "cryptcage-export.zip";
        }
    }

    private void onTarget(final Uri uri) {
        final List<Long> ids = pendingIds;
        final Format format = pendingFormat;
        final char[] password = pendingPassword;
        pendingIds = null;
        pendingPassword = null;
        pendingFormat = null;
        final App.Session session = App.session();
        if (uri == null || ids == null || format == null) {
            wipe(password);
            return;
        }
        if (session == null) {
            wipe(password);
            toast(R.string.toast_locked);
            return;
        }
        final Context app = activity.getApplicationContext();
        final String suffix = format == Format.SEVEN_ZIP_PLAIN ? ".7z" : ".zip";
        Tasks.io(() -> {
            final File temp = new File(app.getCacheDir(), "export-" + System.nanoTime() + suffix);
            boolean ok;
            try {
                final ZipExport.Plan plan = ZipExport.collect(session.vault, ids);
                switch (format) {
                    case ZIP_ENCRYPTED:
                        EncryptedZip.write(temp, plan, password);
                        break;
                    case SEVEN_ZIP_PLAIN:
                        SevenZipExport.writePlain(temp, plan);
                        break;
                    default:
                        ZipExport.writePlain(temp, plan);
                        break;
                }
                ok = copyTo(app, temp, uri);
            } catch (Exception e) {
                ok = false;
            } finally {
                temp.delete();
                wipe(password);
            }
            final boolean success = ok;
            final int message = !success ? R.string.toast_exportFailed
                    : format == Format.SEVEN_ZIP_PLAIN ? R.string.toast_archiveSaved : R.string.toast_zipSaved;
            Tasks.ui(() -> Toast.makeText(app, message, Toast.LENGTH_LONG).show());
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
