package com.cryptcage.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.DatabaseForNotes;
import com.cryptcage.utils.FileTypes;
import com.cryptcage.utils.StringMethods;
import com.cryptcage.utils.VaultException;
import com.cryptcage.utils.VaultFile;
import com.cryptcage.utils.VaultKey;
import com.cryptcage.R;

/**
 * Lists the vaults stored in the app's private folder and lets the user
 * open, create, import or delete one. Vaults are never read from shared storage.
 */
public class SelectDatabaseActivity extends SecureActivity
        implements InsertPasswordDialogFragment.Callback, ChoosePasswordDialogFragment.Callback {

    private static final String VAULT_DIR = "vaults";

    private EditText nameField;
    private ProgressBar spinner;
    private ArrayAdapter<String> adapter;
    private ActivityResultLauncher<String[]> importLauncher;
    private String pendingCreateName;
    private PendingImport pendingImport;

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

        nameField = findViewById(R.id.editTextPath);
        spinner = findViewById(R.id.progressBar1);
        final ListView listView = findViewById(android.R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                askPassword(adapter.getItem(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(adapter.getItem(position));
            return true;
        });

        findViewById(R.id.buttonOpen).setOnClickListener(v -> onOpenClicked());
        findViewById(R.id.buttonCreate).setOnClickListener(v -> onCreateClicked());
        findViewById(R.id.buttonStorage).setOnClickListener(v -> importLauncher.launch(new String[]{"*/*"}));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.action_about) {
            AboutToast.show(this);
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
        adapter.clear();
        final File[] files = vaultDir().listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                final String n = f.getName();
                if (f.isFile() && !n.startsWith(".") && n.endsWith(StringMethods.EXTENSION)) {
                    adapter.add(StringMethods.getNameDB(n));
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void onOpenClicked() {
        final String name = StringMethods.sanitizeName(nameField.getText().toString());
        if (name == null) {
            toast(R.string.toast_nameInvalid);
            return;
        }
        if (!fileFor(name).isFile()) {
            toast(R.string.toast_wrongPath);
            return;
        }
        askPassword(name);
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

    private void askPassword(final String name) {
        InsertPasswordDialogFragment.newInstance(name).show(getSupportFragmentManager(), "insert_password");
    }

    private void confirmDelete(final String name) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_delete)
                .setMessage(R.string.deleteRecent_message)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    if (fileFor(name).delete()) {
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
                final VaultFile.Opened opened = VaultFile.open(file, password);
                Tasks.ui(() -> {
                    setBusy(false);
                    App.setSession(new App.Session(file, opened.db, opened.key));
                    startActivity(new Intent(this, MainActivity.class));
                });
            } catch (VaultException e) {
                final int message = messageFor(e.getReason());
                Tasks.ui(() -> {
                    setBusy(false);
                    toast(message);
                });
            } finally {
                Arrays.fill(password, '\0');
            }
        });
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
            VaultKey key = null;
            try {
                key = VaultFile.createKey(password);
                final DatabaseForNotes db = new DatabaseForNotes();
                if (imported != null) {
                    db.addFile(imported.fileName, imported.data);
                }
                VaultFile.save(file, db, key);
                final VaultKey created = key;
                Tasks.ui(() -> {
                    setBusy(false);
                    App.setSession(new App.Session(file, db, created));
                    toast(imported != null && imported.secret
                            ? R.string.toast_secretFile : R.string.toast_success_newDB);
                    startActivity(new Intent(this, MainActivity.class));
                });
            } catch (VaultException e) {
                if (key != null) {
                    key.wipe();
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
                result = messageFor(e.getReason());
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
                    if (!VaultFile.looksLikeVault(Arrays.copyOf(buf, n))) {
                        throw new VaultException(VaultException.Reason.NOT_A_VAULT, null);
                    }
                    checked = true;
                }
                total += n;
                if (total > VaultFile.MAX_BYTES) {
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

    private void setBusy(final boolean busy) {
        spinner.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private int messageFor(final VaultException.Reason reason) {
        switch (reason) {
            case BAD_PASSWORD_OR_DAMAGED:
                return R.string.toast_wrongPassword;
            case NOT_A_VAULT:
                return R.string.toast_notVault;
            case TOO_LARGE:
                return R.string.toast_tooLarge;
            case IO:
                return R.string.toast_IOError;
            default:
                return R.string.toast_fatalError;
        }
    }

    private void toast(final int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
