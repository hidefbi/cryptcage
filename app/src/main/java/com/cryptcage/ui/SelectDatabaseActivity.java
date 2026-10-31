/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.FileTypes;
import com.cryptcage.utils.StringMethods;
import com.cryptcage.utils.VaultException;
import com.cryptcage.utils.VaultSession;
import com.cryptcage.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * The start screen. It shows the most recent database, a list of the saved databases to open,
 * a field to create a new one, and an import for a file. Vaults are never read from shared storage.
 */
public class SelectDatabaseActivity extends SecureActivity
        implements InsertPasswordDialogFragment.Callback, ChoosePasswordDialogFragment.Callback,
        PinDialogFragment.Callback {

    private static final String VAULT_DIR = "vaults";

    private View textRecentTitle;
    private View cardRecent;
    private TextView textRecentName;
    private TextView textRecentInfo;
    private View rowSelect;
    private TextView textSelected;
    private ImageView imageExpand;
    private MaterialButton buttonOpen;
    private MaterialCardView cardDatabaseList;
    private LinearLayout listDatabases;
    private EditText nameField;
    private ProgressBar spinner;
    private ActivityResultLauncher<String[]> importLauncher;
    private String pendingCreateName;
    private PendingImport pendingImport;
    /** A vault that is open while the PIN dialog asks whether to save its password. */
    private File pendingRegisterFile;
    private VaultSession pendingRegisterVault;

    /** Names of the saved databases, newest first. */
    private final List<String> names = new ArrayList<>();
    /** The database picked from the list, or null when none is picked. */
    private String selectedName;
    private boolean listExpanded;

    /** A file picked on the start screen, to be stored in the database that is about to be created. */
    private static final class PendingImport {
        final String fileName;
        final byte[] data;
        final boolean secret;

        PendingImport(final String fileName, final byte[] data, final boolean secret) {
            this.fileName = fileName;
            this.data = data;
            this.secret = secret;
        }
    }

    @Override
    protected boolean needsVault() {
        return false;
    }

    /** The start screen has no toolbar, so its top is the background. */
    @Override
    protected boolean statusBarFollowsBackground() {
        return true;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_database);
        setTitle(R.string.app_name);

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                new androidx.activity.result.ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(final Uri uri) {
                        if (uri != null) {
                            onFilePicked(uri);
                        }
                    }
                });

        textRecentTitle = findViewById(R.id.textRecentTitle);
        cardRecent = findViewById(R.id.cardRecent);
        textRecentName = findViewById(R.id.textRecentName);
        textRecentInfo = findViewById(R.id.textRecentInfo);
        rowSelect = findViewById(R.id.rowSelect);
        textSelected = findViewById(R.id.textSelected);
        imageExpand = findViewById(R.id.imageExpand);
        buttonOpen = findViewById(R.id.buttonOpen);
        cardDatabaseList = findViewById(R.id.cardDatabaseList);
        listDatabases = findViewById(R.id.listDatabases);
        nameField = findViewById(R.id.editTextName);
        spinner = findViewById(R.id.progressBar1);

        cardRecent.setOnClickListener(v -> {
            if (!names.isEmpty()) {
                askPassword(names.get(0));
            }
        });
        rowSelect.setOnClickListener(v -> toggleList());
        buttonOpen.setOnClickListener(v -> onOpenClicked());
        findViewById(R.id.buttonCreate).setOnClickListener(v -> onCreateClicked());
        findViewById(R.id.cardImport).setOnClickListener(v -> importLauncher.launch(new String[]{"*/*"}));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.start, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private File vaultDir() {
        final File dir = new File(getFilesDir(), VAULT_DIR);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File fileFor(final String name) {
        return new File(vaultDir(), name + StringMethods.EXTENSION);
    }

    private void refreshList() {
        final File[] files = vaultDir().listFiles(f -> f.isFile() && !f.getName().startsWith(".")
                && f.getName().endsWith(StringMethods.EXTENSION));
        names.clear();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                names.add(StringMethods.getNameDB(f.getName()));
            }
        }
        if (selectedName != null && !names.contains(selectedName)) {
            selectedName = null;
        }
        showRecent(files != null && files.length > 0 ? files[0] : null);
        showList();
        updateSelection();
    }

    private void showRecent(final File newest) {
        final int visibility = newest == null ? View.GONE : View.VISIBLE;
        textRecentTitle.setVisibility(visibility);
        cardRecent.setVisibility(visibility);
        if (newest != null) {
            textRecentName.setText(StringMethods.getNameDB(newest.getName()));
            final String when = DateUtils.formatDateTime(this, newest.lastModified(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH);
            textRecentInfo.setText(getString(R.string.recent_folder) + " · " + when);
        }
    }

    /** Fills the expandable list with one row per saved database. */
    private void showList() {
        listDatabases.removeAllViews();
        if (names.isEmpty()) {
            listDatabases.addView(listRow(getString(R.string.list_empty), null));
            return;
        }
        for (String name : names) {
            listDatabases.addView(listRow(name, name));
        }
    }

    private TextView listRow(final String text, final String name) {
        final TextView row = new TextView(this);
        row.setText(text);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final int padH = dp(16);
        final int padV = dp(14);
        row.setPadding(padH, padV, padH, padV);
        row.setMinHeight(dp(52));
        row.setBackground(SecureActivity.themeDrawable(this, android.R.attr.selectableItemBackground));
        if (name == null) {
            row.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            return row;
        }
        row.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
        row.setTypeface(null, name.equals(selectedName) ? Typeface.BOLD : Typeface.NORMAL);
        row.setOnClickListener(v -> choose(name));
        row.setOnLongClickListener(v -> {
            confirmDelete(name);
            return true;
        });
        return row;
    }

    private void choose(final String name) {
        selectedName = name;
        listExpanded = false;
        cardDatabaseList.setVisibility(View.GONE);
        imageExpand.setRotation(0f);
        showList();
        updateSelection();
    }

    private void toggleList() {
        listExpanded = !listExpanded;
        cardDatabaseList.setVisibility(listExpanded ? View.VISIBLE : View.GONE);
        imageExpand.animate().rotation(listExpanded ? 180f : 0f).setDuration(150).start();
    }

    private void updateSelection() {
        if (selectedName == null) {
            textSelected.setText(R.string.hint_chooseDatabase);
            textSelected.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        } else {
            textSelected.setText(selectedName);
            textSelected.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
        }
    }

    private void onOpenClicked() {
        if (selectedName == null) {
            toast(R.string.toast_selectDatabase);
            return;
        }
        askPassword(selectedName);
    }

    private void onCreateClicked() {
        final String name = StringMethods.sanitizeName(nameField.getText().toString());
        if (name == null) {
            toast(R.string.toast_nameInvalid);
            return;
        }
        if (fileFor(name).exists()) {
            toast(R.string.toast_fileExists);
            return;
        }
        pendingImport = null;
        pendingCreateName = name;
        ChoosePasswordDialogFragment.newInstance(ChoosePasswordDialogFragment.PURPOSE_CREATE)
                .show(getSupportFragmentManager(), "create_password");
    }

    /** Asks for the PIN when this database has a saved password, and the master password otherwise. */
    private void askPassword(final String name) {
        if (PinStore.supported() && PinStore.hasCredential(this, name)) {
            PinDialogFragment.newInstance(PinDialogFragment.MODE_UNLOCK, name)
                    .show(getSupportFragmentManager(), "pin_unlock");
        } else {
            askMasterPassword(name);
        }
    }

    private void askMasterPassword(final String name) {
        InsertPasswordDialogFragment.newInstance(name).show(getSupportFragmentManager(), "insert_password");
    }

    private void confirmDelete(final String name) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_delete)
                .setMessage(R.string.deleteRecent_message)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    if (fileFor(name).delete()) {
                        PinStore.removeCredential(this, name);
                        toast(R.string.toast_success_deleteNote);
                    }
                    refreshList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onPasswordEntered(final String fileName, final char[] password) {
        final File file = fileFor(fileName);
        setBusy(true);
        Tasks.io(() -> {
            try {
                final VaultSession vault = VaultSession.open(file, password);
                // With a PIN set, a vault without a saved password is offered one. The copy is held
                // here, because the caller wipes its array as soon as this method returns.
                final boolean offerRegister = PinStore.supported() && PinStore.isEnabled(this)
                        && !PinStore.hasCredential(this, fileName);
                if (offerRegister) {
                    PinStore.holdPassword(password);
                }
                Tasks.ui(() -> {
                    setBusy(false);
                    if (offerRegister) {
                        pendingRegisterFile = file;
                        pendingRegisterVault = vault;
                        PinDialogFragment.newInstance(PinDialogFragment.MODE_REGISTER, fileName)
                                .show(getSupportFragmentManager(), "pin_register");
                    } else {
                        openVault(file, vault);
                    }
                });
            } catch (VaultException e) {
                final int message = VaultMessages.messageFor(e.getReason());
                Tasks.ui(() -> {
                    setBusy(false);
                    toast(message);
                });
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void openVault(final File file, final VaultSession vault) {
        App.setSession(new App.Session(file, vault));
        startActivity(new Intent(this, MainActivity.class));
    }

    /** Finishes opening the vault that was waiting for the PIN dialog. */
    private void continueRegisteredOpen() {
        if (pendingRegisterVault != null) {
            openVault(pendingRegisterFile, pendingRegisterVault);
        }
        pendingRegisterFile = null;
        pendingRegisterVault = null;
    }

    @Override
    public void onPinFinished(final int mode, final String vault, final char[] password) {
        if (mode == PinDialogFragment.MODE_UNLOCK && password != null) {
            onPasswordEntered(vault, password);
        } else if (mode == PinDialogFragment.MODE_REGISTER) {
            continueRegisteredOpen();
        }
    }

    @Override
    public void onPinDismissed(final int mode, final String vault, final boolean usePassword) {
        if (mode == PinDialogFragment.MODE_REGISTER) {
            continueRegisteredOpen();
        } else if (mode == PinDialogFragment.MODE_UNLOCK && usePassword) {
            askMasterPassword(vault);
        }
    }

    @Override
    public void onPasswordChosen(final String purpose, final char[] password) {
        if (!ChoosePasswordDialogFragment.PURPOSE_CREATE.equals(purpose) || pendingCreateName == null) {
            Arrays.fill(password, '\0');
            return;
        }
        final File file = fileFor(pendingCreateName);
        final PendingImport imported = pendingImport;
        pendingCreateName = null;
        pendingImport = null;
        setBusy(true);
        Tasks.io(() -> {
            VaultSession vault = null;
            try {
                vault = VaultSession.create(file, password);
                if (imported != null) {
                    vault.addFile(imported.fileName, imported.data);
                }
                vault.save();
                final VaultSession created = vault;
                Tasks.ui(() -> {
                    setBusy(false);
                    App.setSession(new App.Session(file, created));
                    toast(imported != null && imported.secret
                            ? R.string.toast_secretFile : R.string.toast_success_newDB);
                    startActivity(new Intent(this, MainActivity.class));
                });
            } catch (VaultException e) {
                if (vault != null) {
                    vault.close();
                }
                file.delete();
                Tasks.ui(() -> {
                    setBusy(false);
                    toast(R.string.toast_errorCreatingDB);
                });
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    /** A picked file that is a supported file type is imported into a new database; anything else is a vault. */
    private void onFilePicked(final Uri uri) {
        final String display = displayName(uri);
        if (FileTypes.kindOf(display) == null) {
            importVault(uri);
            return;
        }
        importAsDatabase(uri, display);
    }

    private void importAsDatabase(final Uri uri, final String display) {
        final String fileName = FileTypes.cleanFileName(display);
        if (fileName == null) {
            toast(R.string.toast_fileUnsupported);
            return;
        }
        setBusy(true);
        Tasks.io(() -> {
            final byte[] data;
            try {
                data = ImportSource.readLimited(getContentResolver(), uri);
            } catch (IOException e) {
                Tasks.ui(() -> {
                    setBusy(false);
                    toast(R.string.toast_IOError);
                });
                return;
            }
            if (data == null) {
                Tasks.ui(() -> {
                    setBusy(false);
                    toast(R.string.toast_tooLarge);
                });
                return;
            }
            final FileTypes.Check check = FileTypes.check(fileName, data);
            if (!check.matches) {
                Tasks.ui(() -> {
                    setBusy(false);
                    toast(R.string.toast_fileInvalid);
                });
                return;
            }
            Tasks.ui(() -> {
                setBusy(false);
                askCreateDatabase(new PendingImport(fileName, data, check.secret()));
            });
        });
    }

    private void askCreateDatabase(final PendingImport imported) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.import_createTitle)
                .setMessage(R.string.import_createMessage)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    pendingImport = imported;
                    pendingCreateName = uniqueDatabaseName(imported.fileName);
                    ChoosePasswordDialogFragment.newInstance(ChoosePasswordDialogFragment.PURPOSE_CREATE)
                            .show(getSupportFragmentManager(), "create_password");
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** A database name taken from the file name, with a number added if that name is already in use. */
    private String uniqueDatabaseName(final String fileName) {
        final String stem = fileName.substring(0, fileName.lastIndexOf('.'));
        String base = StringMethods.sanitizeName(stem);
        if (base == null) {
            base = getString(R.string.imported_name);
        }
        String candidate = base;
        int i = 2;
        while (fileFor(candidate).exists()) {
            candidate = base + " (" + i + ")";
            i++;
        }
        return candidate;
    }

    private void importVault(final Uri uri) {
        setBusy(true);
        Tasks.io(() -> {
            final String display = displayName(uri);
            String base = StringMethods.sanitizeName(display);
            if (base == null) {
                base = getString(R.string.imported_name);
            }
            final File target = uniqueFile(vaultDir(), base);
            final File tmp = new File(vaultDir(), "." + target.getName() + ".part");
            int result;
            try {
                copyVault(getContentResolver(), uri, tmp);
                if (!tmp.renameTo(target)) {
                    throw new IOException("rename failed");
                }
                result = R.string.toast_imported;
            } catch (VaultException e) {
                result = VaultMessages.messageFor(e.getReason());
            } catch (IOException e) {
                result = R.string.toast_IOError;
            } finally {
                if (tmp.exists()) {
                    tmp.delete();
                }
            }
            final int message = result;
            Tasks.ui(() -> {
                setBusy(false);
                refreshList();
                toast(message);
            });
        });
    }

    private static void copyVault(final ContentResolver resolver, final Uri uri, final File tmp)
            throws VaultException, IOException {
        final InputStream in = resolver.openInputStream(uri);
        if (in == null) {
            throw new IOException("cannot open " + uri);
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            final byte[] buf = new byte[8192];
            long total = 0;
            int n;
            boolean checked = false;
            while ((n = in.read(buf)) != -1) {
                if (!checked) {
                    if (!VaultSession.looksLikeVault(Arrays.copyOf(buf, n))) {
                        throw new VaultException(VaultException.Reason.NOT_A_VAULT, null);
                    }
                    checked = true;
                }
                total += n;
                if (total > VaultSession.MAX_CONTAINER_BYTES) {
                    throw new VaultException(VaultException.Reason.TOO_LARGE, null);
                }
                out.write(buf, 0, n);
            }
            if (!checked) {
                throw new VaultException(VaultException.Reason.NOT_A_VAULT, null);
            }
            out.flush();
            out.getFD().sync();
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }
    }

    private String displayName(final Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // some providers refuse the query; fall back to the default name
        }
        return null;
    }

    private static File uniqueFile(final File dir, final String base) {
        File candidate = new File(dir, base + StringMethods.EXTENSION);
        int i = 2;
        while (candidate.exists()) {
            candidate = new File(dir, base + " (" + i + ")" + StringMethods.EXTENSION);
            i++;
        }
        return candidate;
    }

    private int themeColor(final int attr) {
        final TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setBusy(final boolean busy) {
        spinner.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void toast(final int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
