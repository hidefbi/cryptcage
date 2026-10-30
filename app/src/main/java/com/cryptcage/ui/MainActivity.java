package com.cryptcage.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.DatabaseForNotes;
import com.cryptcage.utils.FileKinds;
import com.cryptcage.utils.FileTypes;
import com.cryptcage.utils.Notes;
import com.cryptcage.utils.StringMethods;
import com.cryptcage.utils.VaultException;
import com.cryptcage.utils.VaultFile;
import com.cryptcage.utils.VaultKey;
import com.cryptcage.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * The notes of an open vault. On a phone the list fills the screen and a note opens in
 * NoteActivity. On a wide screen the list sits on the left and the note on the right.
 */
public class MainActivity extends SecureActivity
        implements ChoosePasswordDialogFragment.Callback, NoteFragment.Listener {

    private static final String PREF_ALPHABETICAL = "isAlphabeticalOrder";
    private static final String DETAIL_TAG = "detail";
    private static final long NO_NOTE = Long.MIN_VALUE;

    private MaterialToolbar toolbar;
    private TextInputEditText searchField;
    private HorizontalScrollView tagScroll;
    private ChipGroup tagGroup;
    private RecyclerView notesList;
    private TextView emptyView;
    private BottomAppBar bottomBar;
    private FloatingActionButton fab;
    private View detailPane;
    private TextView detailEmpty;
    private NotesAdapter adapter;
    private ActivityResultLauncher<String[]> importLauncher;
    private ExportFlow exportFlow;

    private final Map<Integer, String> chipTags = new HashMap<>();
    private final Set<Long> selectedIds = new TreeSet<>();

    private String selectedTag;
    private String query = "";
    private boolean selecting;
    private boolean rebuildingChips;
    private boolean twoPane;
    private long detailId = NO_NOTE;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLocked()) {
            return;
        }
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        searchField = findViewById(R.id.search_field);
        tagScroll = findViewById(R.id.tag_scroll);
        tagGroup = findViewById(R.id.tag_group);
        notesList = findViewById(R.id.notes_list);
        emptyView = findViewById(R.id.empty);
        bottomBar = findViewById(R.id.bottom_bar);
        fab = findViewById(R.id.fab);
        detailPane = findViewById(R.id.detail_pane);
        detailEmpty = findViewById(R.id.detail_empty);
        twoPane = detailPane != null;

        toolbar.setOnMenuItemClickListener(this::onMenuItem);
        toolbar.setNavigationOnClickListener(v -> exitSelection());
        bottomBar.replaceMenu(R.menu.bottom_bar);
        bottomBar.setOnMenuItemClickListener(this::onMenuItem);
        fab.setOnClickListener(v -> onAddClicked());

        adapter = new NotesAdapter(new NotesAdapter.Listener() {
            @Override
            public void onClick(final long id) {
                if (selecting) {
                    toggleSelected(id);
                } else {
                    openNote(id);
                }
            }

            @Override
            public void onLongClick(final long id) {
                if (!selecting) {
                    enterSelection(id);
                }
            }
        });
        notesList.setLayoutManager(new LinearLayoutManager(this));
        notesList.setAdapter(adapter);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                query = s == null ? "" : s.toString().trim();
                refresh();
            }
        });

        tagGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (rebuildingChips || checkedIds.isEmpty()) {
                return;
            }
            final String tag = chipTags.get(checkedIds.get(0));
            if (!Objects.equals(tag, selectedTag)) {
                selectedTag = tag;
                refresh();
            }
        });

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::importFile);
        exportFlow = new ExportFlow(this);
        updateToolbarMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (redirectIfLocked()) {
            return;
        }
        refresh();
    }

    @Override
    public void onBackPressed() {
        final Fragment detail = getSupportFragmentManager().findFragmentByTag(DETAIL_TAG);
        if (detail instanceof NoteFragment && ((NoteFragment) detail).handleBack()) {
            return;
        }
        if (selecting) {
            exitSelection();
        } else if (selectedTag != null || !query.isEmpty()) {
            selectedTag = null;
            searchField.setText("");
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

    @Override
    public void onNoteChanged(final long noteId) {
        if (twoPane) {
            detailId = noteId;
        }
        refresh();
    }

    @Override
    public void onNoteRemoved() {
        if (twoPane) {
            clearDetail();
        }
        refresh();
    }

    @Override
    public void onExportNote(final long noteId) {
        exportFlow.start(Collections.singletonList(noteId));
    }

    private boolean onMenuItem(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_exportSelected) {
            final List<Long> ids = new ArrayList<>(selectedIds);
            exitSelection();
            exportFlow.start(ids);
        } else if (id == R.id.action_deleteSelected) {
            confirmDeleteSelected();
        } else if (id == R.id.action_cancelSelection) {
            exitSelection();
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
            return false;
        }
        return true;
    }

    private void onAddClicked() {
        if (twoPane) {
            showDetail(-1L);
        } else {
            startActivity(new Intent(this, NoteActivity.class)
                    .putExtra(SecureActivity.EXTRA_NOTE_ID, -1L));
        }
    }

    private void openNote(final long id) {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        final boolean pdf;
        synchronized (session.db) {
            final Notes note = session.db.get(id);
            pdf = note != null && note.isFile() && FileKinds.typeOf(note.getFileName()) == FileKinds.Type.PDF;
        }
        if (pdf) {
            startActivity(new Intent(this, FileViewActivity.class)
                    .putExtra(SecureActivity.EXTRA_NOTE_ID, id));
        } else if (twoPane) {
            showDetail(id);
        } else {
            startActivity(new Intent(this, NoteActivity.class)
                    .putExtra(SecureActivity.EXTRA_NOTE_ID, id));
        }
    }

    private void showDetail(final long id) {
        detailId = id;
        detailEmpty.setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.detail_pane, NoteFragment.newInstance(id, true), DETAIL_TAG)
                .commit();
    }

    private void clearDetail() {
        detailId = NO_NOTE;
        final Fragment detail = getSupportFragmentManager().findFragmentByTag(DETAIL_TAG);
        if (detail != null) {
            getSupportFragmentManager().beginTransaction().remove(detail).commit();
        }
        detailEmpty.setVisibility(View.VISIBLE);
    }

    private void enterSelection(final long id) {
        selecting = true;
        selectedIds.clear();
        selectedIds.add(id);
        updateToolbarMenu();
        refresh();
    }

    private void exitSelection() {
        selecting = false;
        selectedIds.clear();
        updateToolbarMenu();
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

    /** The toolbar shows the selection actions while items are selected, and the vault actions otherwise. */
    private void updateToolbarMenu() {
        toolbar.getMenu().clear();
        if (selecting) {
            toolbar.inflateMenu(R.menu.selection);
            toolbar.setNavigationIcon(SecureActivity.themeDrawable(this,
                    androidx.appcompat.R.attr.actionModeCloseDrawable));
            toolbar.setNavigationContentDescription(R.string.action_cancelSelection);
        } else {
            toolbar.inflateMenu(R.menu.database_opened);
            toolbar.inflateMenu(R.menu.main);
            toolbar.setNavigationIcon(null);
        }
        if (selecting) {
            fab.hide();
        } else {
            fab.show();
        }
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
        if (twoPane && ids.contains(detailId)) {
            clearDetail();
        }
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

    /** Rebuilds the list and the tag chips from the open database. */
    private void refresh() {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        final DatabaseForNotes db = session.db;
        final boolean alphabetical = prefs().getBoolean(PREF_ALPHABETICAL, false);
        final String untitled = getString(R.string.no_title);
        final List<NotesAdapter.Item> shown = new ArrayList<>();
        final Map<String, Integer> counts;
        final int total;

        synchronized (db) {
            selectedIds.retainAll(db.allIds());
            counts = db.tagCounts();
            total = db.size();
            if (selectedTag != null && !counts.containsKey(selectedTag)) {
                selectedTag = null;
            }
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
                shown.add(itemFor(id, note, untitled, selecting && selectedIds.contains(id)));
            }
        }

        rebuildChips(total, counts);
        tagScroll.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        adapter.submit(shown);
        toolbar.setTitle(selecting
                ? getString(R.string.selected_count, selectedIds.size())
                : StringMethods.getNameDB(session.file.getName()));
    }

    private static NotesAdapter.Item itemFor(final long id, final Notes note, final String untitled,
                                             final boolean selected) {
        final String title = note.getTitle();
        final String shownTitle = title == null || title.isEmpty() ? untitled : title;
        if (note.isFile()) {
            final FileKinds.Type type = FileKinds.typeOf(note.getFileName());
            final FileKinds.Type shownType = type == null ? FileKinds.Type.TXT : type;
            return new NotesAdapter.Item(id, shownTitle, shownType.name(), shownType, false, selected);
        }
        final String tags = note.getTags();
        final String subtitle = tags == null || tags.isEmpty() ? null : tags.replace(",", ", ");
        return new NotesAdapter.Item(id, shownTitle, subtitle, null, note.isStarred(), selected);
    }

    private void rebuildChips(final int total, final Map<String, Integer> counts) {
        rebuildingChips = true;
        tagGroup.removeAllViews();
        chipTags.clear();
        addChip(null, getString(R.string.all_items) + " (" + total + ")");
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            addChip(entry.getKey(), entry.getKey() + " (" + entry.getValue() + ")");
        }
        rebuildingChips = false;
    }

    private void addChip(final String tag, final String label) {
        final Chip chip = (Chip) LayoutInflater.from(this).inflate(R.layout.item_tag_chip, tagGroup, false);
        final int id = View.generateViewId();
        chip.setId(id);
        chip.setText(label);
        chipTags.put(id, tag);
        chip.setChecked(Objects.equals(tag, selectedTag));
        tagGroup.addView(chip);
    }

    private void importFile(final Uri uri) {
        if (uri == null || App.session() == null) {
            return;
        }
        final Context app = getApplicationContext();
        final ContentResolver resolver = getContentResolver();
        Tasks.io(() -> {
            final String name = FileTypes.cleanFileName(ImportSource.displayName(resolver, uri));
            if (name == null) {
                Tasks.ui(() -> toast(app, R.string.toast_fileUnsupported));
                return;
            }
            final byte[] data;
            try {
                data = ImportSource.readLimited(resolver, uri);
            } catch (IOException e) {
                Tasks.ui(() -> toast(app, R.string.toast_IOError));
                return;
            }
            if (data == null) {
                Tasks.ui(() -> toast(app, R.string.toast_tooLarge));
                return;
            }
            final FileTypes.Check check = FileTypes.check(name, data);
            if (!check.matches) {
                Tasks.ui(() -> toast(app, R.string.toast_fileInvalid));
                return;
            }
            final boolean secret = check.secret();
            Tasks.ui(() -> addStoredFile(app, name, data, secret));
        });
    }

    private void addStoredFile(final Context app, final String name, final byte[] data, final boolean secret) {
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
        toast(app, secret ? R.string.toast_secretFile : R.string.toast_fileImported);
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
