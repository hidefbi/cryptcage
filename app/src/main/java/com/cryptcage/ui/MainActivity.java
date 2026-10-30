package com.cryptcage.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.DatabaseForNotes;
import com.cryptcage.utils.FileKinds;
import com.cryptcage.utils.Notes;
import com.cryptcage.utils.StringMethods;
import com.cryptcage.utils.VaultException;
import com.cryptcage.utils.VaultFile;
import com.cryptcage.utils.VaultKey;
import com.cryptcage.R;

/** The notes of an open vault: list, search, tag filter, selection for export, and the menu. */
public class MainActivity extends SecureActivity implements ChoosePasswordDialogFragment.Callback {

    private static final String KEY_TITLE = "title";
    private static final String KEY_STAR = "star";
    private static final String PREF_ALPHABETICAL = "isAlphabeticalOrder";
    private static final String PREF_DRAWER = "pref_drawer";

    private DrawerLayout drawer;
    private ListView listView;
    private ListView tagList;
    private TextView emptyView;
    private CustomAdapter adapter;
    private ArrayAdapter<String> tagAdapter;
    private ActivityResultLauncher<String[]> importLauncher;
    private ExportFlow exportFlow;

    private final List<Map<String, String>> rows = new ArrayList<>();
    private final List<Long> shownIds = new ArrayList<>();
    private final List<String> tagKeys = new ArrayList<>();
    private final Set<Long> selectedIds = new TreeSet<>();

    private String selectedTag;
    private String query = "";
    private boolean firstDraw = true;
    private boolean selecting;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLocked()) {
            return;
        }
        setContentView(R.layout.activity_main);

        drawer = findViewById(R.id.drawer_layout);
        listView = findViewById(android.R.id.list);
        tagList = findViewById(R.id.left_drawer);
        emptyView = findViewById(R.id.empty);

        adapter = new CustomAdapter(this, rows, R.layout.listview_layout,
                new String[]{KEY_TITLE, KEY_STAR}, new int[]{R.id.txt, R.id.star});
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            final long noteId = shownIds.get(position);
            if (selecting) {
                toggleSelected(noteId);
            } else {
                openNote(noteId);
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!selecting) {
                enterSelection(shownIds.get(position));
            }
            return true;
        });

        tagAdapter = new ArrayAdapter<>(this, R.layout.drawer_list_item, new ArrayList<String>());
        tagList.setAdapter(tagAdapter);
        tagList.setOnItemClickListener((parent, view, position, id) -> {
            selectedTag = position == 0 ? null : tagKeys.get(position - 1);
            drawer.closeDrawer(GravityCompat.END);
            refresh();
        });

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> importFile(uri));
        exportFlow = new ExportFlow(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (redirectIfLocked()) {
            return;
        }
        refresh();
        if (firstDraw) {
            firstDraw = false;
            if (prefs().getBoolean(PREF_DRAWER, true) && !tagKeys.isEmpty()) {
                drawer.openDrawer(GravityCompat.END);
                Tasks.uiDelayed(() -> drawer.closeDrawer(GravityCompat.END), 1500);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (selecting) {
            getMenuInflater().inflate(R.menu.selection, menu);
            return true;
        }
        getMenuInflater().inflate(R.menu.database_opened, menu);
        getMenuInflater().inflate(R.menu.main, menu);
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint(getString(R.string.action_search));
        searchView.setQuery(query, false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String text) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(final String text) {
                query = text == null ? "" : text.trim();
                refresh();
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_exportSelected) {
            final List<Long> ids = new ArrayList<>(selectedIds);
            exitSelection();
            exportFlow.start(ids);
        } else if (id == R.id.action_deleteSelected) {
            confirmDeleteSelected();
        } else if (id == R.id.action_cancelSelection) {
            exitSelection();
        } else if (id == R.id.action_filter) {
            if (App.session() != null && App.session().db.size() > 0) {
                if (drawer.isDrawerOpen(GravityCompat.END)) {
                    drawer.closeDrawer(GravityCompat.END);
                } else {
                    drawer.openDrawer(GravityCompat.END);
                }
            }
        } else if (id == R.id.action_sort) {
            final boolean alpha = !prefs().getBoolean(PREF_ALPHABETICAL, false);
            prefs().edit().putBoolean(PREF_ALPHABETICAL, alpha).apply();
            refresh();
            toast(alpha ? R.string.toast_sort_alphabet : R.string.toast_sort_creation);
        } else if (id == R.id.action_changePassword) {
            ChoosePasswordDialogFragment.newInstance(ChoosePasswordDialogFragment.PURPOSE_CHANGE)
                    .show(getSupportFragmentManager(), "change_password");
        } else if (id == R.id.action_export) {
            exportFlow.start(allIds());
        } else if (id == R.id.action_importFile) {
            importLauncher.launch(new String[]{"*/*"});
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.action_about) {
            AboutToast.show(this);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (selecting) {
            exitSelection();
        } else if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END);
        } else if (selectedTag != null || !query.isEmpty()) {
            selectedTag = null;
            query = "";
            refresh();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.dialog_backClose)
                    .setTitle(R.string.title_backClose)
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> closeVault())
                    .setNegativeButton(android.R.string.no, null)
                    .show();
        }
    }

    /** Called by the import button in activity_main.xml. */
    public void onImportClicked(final View view) {
        importLauncher.launch(new String[]{"*/*"});
    }

    /** Called by the floating add button in activity_main.xml. */
    public void onFabClicked(final View view) {
        final Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra(SecureActivity.EXTRA_NOTE_ID, -1L);
        startActivity(intent);
    }

    @Override
    public void onPasswordChosen(final String purpose, final char[] password) {
        final App.Session current = App.session();
        if (!ChoosePasswordDialogFragment.PURPOSE_CHANGE.equals(purpose) || current == null) {
            Arrays.fill(password, '\0');
            return;
        }
        final Context app = getApplicationContext();
        Tasks.io(() -> {
            try {
                final VaultKey key = VaultFile.createKey(password);
                VaultFile.save(current.file, current.db, key);
                Tasks.ui(() -> {
                    App.setSession(new App.Session(current.file, current.db, key));
                    toast(app, R.string.toast_success_changePsw);
                });
            } catch (VaultException e) {
                Tasks.ui(() -> toast(app, R.string.toast_IOError));
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void enterSelection(final long id) {
        selecting = true;
        selectedIds.clear();
        selectedIds.add(id);
        invalidateOptionsMenu();
        refresh();
    }

    private void exitSelection() {
        selecting = false;
        selectedIds.clear();
        invalidateOptionsMenu();
        refresh();
    }

    private void toggleSelected(final long id) {
        if (!selectedIds.remove(id)) {
            selectedIds.add(id);
        }
        if (selectedIds.isEmpty()) {
            exitSelection();
            return;
        }
        refresh();
    }

    private void confirmDeleteSelected() {
        final List<Long> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty()) {
            toast(R.string.toast_nothingSelected);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_deleteNote)
                .setMessage(getString(R.string.dialog_deleteSelected, ids.size()))
                .setPositiveButton(R.string.yes, (dialog, which) -> deleteEntries(ids))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void deleteEntries(final List<Long> ids) {
        final App.Session session = App.session();
        if (session != null) {
            synchronized (session.db) {
                for (Long id : ids) {
                    session.db.delete(id);
                }
            }
            App.persist(this);
        }
        exitSelection();
        toast(R.string.toast_success_deleteNote);
    }

    private List<Long> allIds() {
        final App.Session session = App.session();
        if (session == null) {
            return new ArrayList<>();
        }
        synchronized (session.db) {
            return new ArrayList<>(session.db.allIds());
        }
    }

    /** Rebuilds the visible list and the tag drawer from the open database. */
    private void refresh() {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        final DatabaseForNotes db = session.db;
        final boolean alphabetical = prefs().getBoolean(PREF_ALPHABETICAL, false);
        final String untitled = getString(R.string.no_title);

        rows.clear();
        shownIds.clear();
        tagKeys.clear();
        final List<String> labels = new ArrayList<>();
        synchronized (db) {
            selectedIds.retainAll(db.allIds());
            final List<Long> ids = new ArrayList<>(selectedTag == null ? db.allIds() : db.findTag(selectedTag));
            Collections.sort(ids, (a, b) -> {
                final Notes na = db.get(a);
                final Notes nb = db.get(b);
                final int starred = Boolean.compare(nb.isStarred(), na.isStarred());
                if (starred != 0) {
                    return starred;
                }
                if (alphabetical) {
                    final String ta = na.getTitle() == null ? "" : na.getTitle();
                    final String tb = nb.getTitle() == null ? "" : nb.getTitle();
                    return ta.compareToIgnoreCase(tb);
                }
                return Long.compare(a, b);
            });
            for (Long id : ids) {
                final Notes note = db.get(id);
                if (!query.isEmpty()
                        && !StringMethods.containsIgnoreCase(note.getTitle(), query)
                        && !StringMethods.containsIgnoreCase(note.getNote(), query)) {
                    continue;
                }
                final Map<String, String> row = new HashMap<>();
                final String title = note.getTitle();
                row.put(KEY_TITLE, title == null || title.isEmpty() ? untitled : title);
                row.put(KEY_STAR, rowImage(note));
                row.put(CustomAdapter.KEY_SELECTED, String.valueOf(selecting && selectedIds.contains(id)));
                rows.add(row);
                shownIds.add(id);
            }

            labels.add(getString(R.string.all_items) + " (" + db.size() + ")");
            for (Map.Entry<String, Integer> entry : db.tagCounts().entrySet()) {
                tagKeys.add(entry.getKey());
                labels.add(entry.getKey() + " (" + entry.getValue() + ")");
            }
            if (selectedTag != null && !db.tagCounts().containsKey(selectedTag)) {
                selectedTag = null;
            }
        }
        tagAdapter.clear();
        tagAdapter.addAll(labels);
        tagAdapter.notifyDataSetChanged();
        adapter.notifyDataSetChanged();

        final boolean empty = db.size() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        drawer.setDrawerLockMode(empty ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
        setTitle(selecting
                ? getString(R.string.selected_count, selectedIds.size())
                : StringMethods.getNameDB(session.file.getName()));
    }

    private void openNote(final long id) {
        final Intent intent = new Intent(this, ReadNoteActivity.class);
        intent.putExtra(SecureActivity.EXTRA_NOTE_ID, id);
        startActivity(intent);
    }

    private void importFile(final Uri uri) {
        if (uri == null || App.session() == null) {
            return;
        }
        final Context app = getApplicationContext();
        final ContentResolver resolver = getContentResolver();
        Tasks.io(() -> {
            final String display = displayName(resolver, uri);
            final FileKinds.Type type = FileKinds.typeOf(display);
            final String name = type == null ? null : FileKinds.cleanFileName(display, type);
            if (type == null || name == null) {
                Tasks.ui(() -> toast(app, R.string.toast_fileUnsupported));
                return;
            }
            byte[] read = null;
            try {
                read = readLimited(resolver, uri);
            } catch (IOException e) {
                Tasks.ui(() -> toast(app, R.string.toast_IOError));
                return;
            }
            if (read == null) {
                Tasks.ui(() -> toast(app, R.string.toast_tooLarge));
                return;
            }
            if (!FileKinds.validContent(type, read)) {
                Tasks.ui(() -> toast(app, R.string.toast_fileInvalid));
                return;
            }
            final byte[] data = read;
            Tasks.ui(() -> addStoredFile(app, name, data));
        });
    }

    private void addStoredFile(final Context app, final String name, final byte[] data) {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        try {
            synchronized (session.db) {
                session.db.addFile(name, data);
            }
        } catch (DatabaseForNotes.ContentLimitException e) {
            toast(app, R.string.toast_vaultFull);
            return;
        }
        App.persist(app);
        refresh();
        toast(app, R.string.toast_fileImported);
    }

    private static String displayName(final ContentResolver resolver, final Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // some providers refuse the query; the type check then rejects the file
        }
        return null;
    }

    /** Reads the whole stream, or returns null when it is larger than the single-file limit. */
    private static byte[] readLimited(final ContentResolver resolver, final Uri uri) throws IOException {
        final InputStream in = resolver.openInputStream(uri);
        if (in == null) {
            throw new IOException("cannot open " + uri);
        }
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > FileKinds.MAX_FILE_BYTES) {
                    return null;
                }
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The image slot of a list row: the star for a note, the type icon for a file. */
    private static String rowImage(final Notes note) {
        if (!note.isFile()) {
            return String.valueOf(note.isStarred());
        }
        final FileKinds.Type type = FileKinds.typeOf(note.getFileName());
        final String name = type == null ? "txt" : type.name().toLowerCase(Locale.ROOT);
        return CustomAdapter.FILE_PREFIX + name;
    }

    private void closeVault() {
        App.lock();
        startActivity(new Intent(this, SelectDatabaseActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    private void toast(final int message) {
        toast(this, message);
    }

    private static void toast(final Context context, final int message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
