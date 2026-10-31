/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import android.widget.PopupWindow;
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
import com.cryptcage.utils.FileKinds;
import com.cryptcage.utils.FileTypes;
import com.cryptcage.utils.StringMethods;
import com.cryptcage.utils.VaultException;
import com.cryptcage.utils.VaultSession;
import com.cryptcage.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * The notes of an open vault. On a phone the list fills the screen and a note opens in
 * NoteActivity. On a wide screen the list sits on the left and the note on the right.
 *
 * The list is built from the index only, so bodies are not read to show it. A text search reads
 * the bodies on a worker thread and the newest search wins.
 */
public class MainActivity extends SecureActivity
        implements ChoosePasswordDialogFragment.Callback, NoteFragment.Listener {

    private static final String PREF_ALPHABETICAL = "isAlphabeticalOrder";
    private static final String PREF_MANUAL = "isManualOrder";
    private static final String DETAIL_TAG = "detail";
    private static final long NO_NOTE = Long.MIN_VALUE;

    private MaterialToolbar toolbar;
    private TextInputEditText searchField;
    private HorizontalScrollView tagScroll;
    private ChipGroup tagGroup;
    private RecyclerView notesList;
    private TextView emptyView;
    private FloatingActionButton fab;
    private View fabScrim;
    private View fabMenu;
    private boolean fabMenuOpen;
    private View detailPane;
    private TextView detailEmpty;
    private NotesAdapter adapter;
    private ActivityResultLauncher<String[]> importLauncher;
    private ExportFlow exportFlow;
    private NoteGestures gestures;
    private View screenshotBar;
    private PopupWindow notePopup;

    private final Map<Integer, String> chipTags = new HashMap<>();
    private final Set<Long> selectedIds = new TreeSet<>();
    private final Set<Long> readOnlyIds = new HashSet<>();

    private String selectedTag;
    private String query = "";
    /** Ids that match the query, or null when no search is applied. */
    private Set<Long> searchHits;
    private long searchToken;
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
        fab = findViewById(R.id.fab);
        fabScrim = findViewById(R.id.fab_scrim);
        fabMenu = findViewById(R.id.fab_menu);
        screenshotBar = findViewById(R.id.screenshot_bar);
        detailPane = findViewById(R.id.detail_pane);
        detailEmpty = findViewById(R.id.detail_empty);
        twoPane = detailPane != null;

        toolbar.setOnMenuItemClickListener(this::onMenuItem);
        toolbar.setNavigationOnClickListener(v -> exitSelection());
        fab.setOnClickListener(v -> toggleFabMenu());
        fabScrim.setOnClickListener(v -> closeFabMenu());
        findViewById(R.id.fab_action_new).setOnClickListener(v -> {
            closeFabMenu();
            onAddClicked();
        });
        findViewById(R.id.fab_action_import).setOnClickListener(v -> {
            closeFabMenu();
            importLauncher.launch(new String[]{"*/*"});
        });
        findViewById(R.id.fab_action_export).setOnClickListener(v -> {
            closeFabMenu();
            exportFlow.start(allIds());
        });

        adapter = new NotesAdapter(new NotesAdapter.Listener() {
            @Override
            public void onClick(final long id) {
                if (selecting) {
                    toggleSelected(id);
                } else {
                    openNote(id);
                }
            }

        });
        notesList.setLayoutManager(new LinearLayoutManager(this));
        notesList.setAdapter(adapter);
        gestures = new NoteGestures(notesList, adapter, new NoteGestures.Host() {
            @Override
            public boolean menuAllowed() {
                return !selecting;
            }

            @Override
            public boolean dragAllowed() {
                return !selecting && query.isEmpty() && selectedTag == null;
            }

            @Override
            public void showMenu(final long id, final View row, final float x, final float y) {
                MainActivity.this.dismissMenu();
                final VaultSession.Summary note = summaryOrNull(id);
                if (note == null) {
                    gestures.menuClosed();
                    return;
                }
                notePopup = NoteMenu.show(MainActivity.this, notesList, id, note.readOnly, x, y, noteActions,
                        () -> gestures.menuClosed());
            }

            @Override
            public void dismissMenu() {
                MainActivity.this.dismissMenu();
            }

            @Override
            public void orderChanged(final List<Long> ids) {
                saveOrder(ids);
            }
        });
        notesList.addOnItemTouchListener(gestures);

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
                startSearch();
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
    protected void onPause() {
        dismissMenu();
        if (gestures != null) {
            gestures.cancel();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (redirectIfLocked()) {
            return;
        }
        screenshotBar.setVisibility(App.allowsScreenshots(this) ? View.VISIBLE : View.GONE);
        ClipboardTimer.clearIfExpired(this);
        refresh();
    }

    @Override
    public void onBackPressed() {
        if (fabMenuOpen) {
            closeFabMenu();
            return;
        }
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
        if (!ChoosePasswordDialogFragment.PURPOSE_CHANGE.equals(purpose)) {
            Arrays.fill(password, '\0');
            return;
        }
        PasswordChange.apply(this, password);
    }

    @Override
    public void onNoteChanged(final long noteId) {
        if (twoPane) {
            detailId = noteId;
        }
        refresh();
    }

    @Override
    public void onNoteSaved(final long noteId) {
        onNoteChanged(noteId);
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
        } else if (id == R.id.action_sort) {
            final boolean alpha = !prefs().getBoolean(PREF_ALPHABETICAL, false);
            prefs().edit().putBoolean(PREF_ALPHABETICAL, alpha).putBoolean(PREF_MANUAL, false).apply();
            refresh();
            toast(alpha ? R.string.toast_sort_alphabet : R.string.toast_sort_creation);
        } else if (id == R.id.action_changePassword) {
            ChoosePasswordDialogFragment.newInstance(ChoosePasswordDialogFragment.PURPOSE_CHANGE)
                    .show(getSupportFragmentManager(), "change_password");
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
        if (App.session() == null) {
            return;
        }
        // The row already knows its file type, so the vault is not read on the UI thread here.
        final boolean pdf = adapter.typeOf(id) == FileKinds.Type.PDF;
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
            toolbar.getMenu().findItem(R.id.action_exportSelected)
                    .setIcon(EmojiIcon.of(this, EmojiIcon.EXPORT));
            toolbar.getMenu().findItem(R.id.action_deleteSelected)
                    .setIcon(EmojiIcon.of(this, EmojiIcon.DELETE));
            toolbar.setNavigationIcon(SecureActivity.themeDrawable(this,
                    androidx.appcompat.R.attr.actionModeCloseDrawable));
            toolbar.setNavigationContentDescription(R.string.action_cancelSelection);
        } else {
            toolbar.inflateMenu(R.menu.database_opened);
            toolbar.inflateMenu(R.menu.main);
            toolbar.setNavigationIcon(null);
        }
        if (selecting) {
            closeFabMenu();
            fab.hide();
        } else {
            fab.show();
        }
    }

    private void toggleFabMenu() {
        if (fabMenuOpen) {
            closeFabMenu();
        } else {
            openFabMenu();
        }
    }

    /** Opens the add menu above the button. The button turns into a close icon. */
    private void openFabMenu() {
        fabMenuOpen = true;
        fabScrim.setVisibility(View.VISIBLE);
        fabMenu.setVisibility(View.VISIBLE);
        fab.animate().rotation(45f).setDuration(150).start();
    }

    private void closeFabMenu() {
        if (!fabMenuOpen) {
            return;
        }
        fabMenuOpen = false;
        fabScrim.setVisibility(View.GONE);
        fabMenu.setVisibility(View.GONE);
        fab.animate().rotation(0f).setDuration(150).start();
    }

    private void confirmDeleteSelected() {
        confirmDelete(new ArrayList<>(selectedIds));
    }

    /** Asks before deleting. Read-only entries are left out, and the user is told how many were kept. */
    private void confirmDelete(final List<Long> ids) {
        if (ids.isEmpty()) {
            toast(R.string.toast_nothingSelected);
            return;
        }
        final List<Long> deletable = new ArrayList<>();
        int kept = 0;
        for (Long id : ids) {
            if (readOnlyIds.contains(id)) {
                kept++;
            } else {
                deletable.add(id);
            }
        }
        if (deletable.isEmpty()) {
            toastText(getString(R.string.toast_readOnlyKept, kept));
            return;
        }
        final int keptCount = kept;
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_deleteNote)
                .setMessage(getString(R.string.dialog_deleteSelected, deletable.size()))
                .setPositiveButton(R.string.yes, (dialog, which) -> deleteEntries(deletable, keptCount))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void deleteEntries(final List<Long> ids, final int kept) {
        final App.Session session = App.session();
        if (session != null) {
            for (Long id : ids) {
                try {
                    session.vault.delete(id);
                } catch (VaultException e) {
                    toast(VaultMessages.messageFor(e.getReason()));
                    break;
                }
            }
            App.persist(this);
        }
        exitSelection();
        if (twoPane && ids.contains(detailId)) {
            clearDetail();
        }
        if (kept > 0) {
            toastText(getString(R.string.toast_readOnlyKept, kept));
        } else {
            toast(R.string.toast_success_deleteNote);
        }
    }

    /** Actions of the long-press menu. Each one runs after the menu has closed. */
    private final NoteMenu.Actions noteActions = new NoteMenu.Actions() {
        @Override
        public void select(final long id) {
            if (!selecting) {
                enterSelection(id);
            }
        }

        @Override
        public void export(final long id) {
            exportFlow.start(Collections.singletonList(id));
        }

        @Override
        public void copy(final long id) {
            copyEntry(id);
        }

        @Override
        public void toggleReadOnly(final long id) {
            setReadOnly(id, !readOnlyIds.contains(id));
        }

        @Override
        public void delete(final long id) {
            confirmDelete(Collections.singletonList(id));
        }
    };

    /** Closes the menu. The listener is removed first, so the highlight is kept by the caller. */
    private void dismissMenu() {
        if (notePopup != null) {
            notePopup.setOnDismissListener(null);
            notePopup.dismiss();
            notePopup = null;
        }
    }

    /** The summary of a row, or null when it is gone. */
    private VaultSession.Summary summaryOrNull(final long id) {
        final App.Session session = App.session();
        if (session == null) {
            return null;
        }
        try {
            return session.vault.summary(id);
        } catch (VaultException e) {
            return null;
        }
    }

    /**
     * Copies a note or file next to the original, with a copy suffix on the title. The copy is
     * placed right after the original, which only shows in manual order, so the list switches to it.
     */
    private void copyEntry(final long id) {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        try {
            final VaultSession.Summary note = session.vault.summary(id);
            final String base = note.title == null || note.title.isEmpty() ? getString(R.string.no_title) : note.title;
            session.vault.duplicate(id, getString(R.string.title_copyName, base));
        } catch (VaultException e) {
            toast(VaultMessages.messageFor(e.getReason()));
            return;
        }
        prefs().edit().putBoolean(PREF_MANUAL, true).apply();
        App.persist(this);
        refresh();
    }

    private void setReadOnly(final long id, final boolean readOnly) {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        try {
            session.vault.setReadOnly(id, readOnly);
        } catch (VaultException e) {
            toast(VaultMessages.messageFor(e.getReason()));
            return;
        }
        App.persist(this);
        refresh();
    }

    /** Saves the order left by a drag and switches the list to manual order. */
    private void saveOrder(final List<Long> ids) {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        try {
            session.vault.reorder(ids);
        } catch (VaultException e) {
            toast(VaultMessages.messageFor(e.getReason()));
            refresh();
            return;
        }
        prefs().edit().putBoolean(PREF_MANUAL, true).apply();
        App.persist(this);
        refresh();
    }

    private List<Long> allIds() {
        final List<Long> ids = new ArrayList<>();
        final App.Session session = App.session();
        if (session != null) {
            for (VaultSession.Summary note : session.vault.list()) {
                ids.add(note.id);
            }
        }
        return ids;
    }

    /** Runs the text search on a worker thread. The newest request wins; an empty query clears it. */
    private void startSearch() {
        final long token = ++searchToken;
        final String text = query;
        final App.Session session = App.session();
        if (text.isEmpty() || session == null) {
            searchHits = null;
            refresh();
            return;
        }
        Tasks.io(() -> {
            final List<Long> hits;
            try {
                hits = session.vault.search(text);
            } catch (VaultException e) {
                final int message = VaultMessages.messageFor(e.getReason());
                Tasks.ui(() -> {
                    if (App.session() == session) {
                        toast(message);
                    }
                });
                return;
            }
            Tasks.ui(() -> {
                if (token == searchToken && App.session() == session) {
                    searchHits = new HashSet<>(hits);
                    refresh();
                }
            });
        });
    }

    /** Rebuilds the list and the tag chips from the open vault's index. */
    private void refresh() {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        final VaultSession vault = session.vault;
        final boolean alphabetical = prefs().getBoolean(PREF_ALPHABETICAL, false);
        final boolean manual = prefs().getBoolean(PREF_MANUAL, false);
        final String untitled = getString(R.string.no_title);

        final List<VaultSession.Summary> all = vault.list();
        final Map<String, Integer> counts = vault.tagCounts();
        final int total = all.size();

        final Set<Long> existing = new HashSet<>();
        for (VaultSession.Summary note : all) {
            existing.add(note.id);
        }
        selectedIds.retainAll(existing);
        if (selectedTag != null && !counts.containsKey(selectedTag)) {
            selectedTag = null;
        }
        final Set<Long> tagged = selectedTag == null ? null : new HashSet<>(vault.withTag(selectedTag));

        final List<VaultSession.Summary> rows = new ArrayList<>();
        for (VaultSession.Summary note : all) {
            if (tagged != null && !tagged.contains(note.id)) {
                continue;
            }
            if (searchHits != null && !query.isEmpty() && !searchHits.contains(note.id)) {
                continue;
            }
            rows.add(note);
        }
        readOnlyIds.clear();
        for (VaultSession.Summary note : all) {
            if (note.readOnly) {
                readOnlyIds.add(note.id);
            }
        }
        Collections.sort(rows, (a, b) -> {
            if (manual) {
                return Long.compare(a.order, b.order);
            }
            final int starred = Boolean.compare(b.starred, a.starred);
            if (starred != 0) {
                return starred;
            }
            if (alphabetical) {
                final String ta = a.title == null ? "" : a.title;
                final String tb = b.title == null ? "" : b.title;
                return ta.compareToIgnoreCase(tb);
            }
            return Long.compare(a.id, b.id);
        });

        final List<NotesAdapter.Item> shown = new ArrayList<>();
        for (VaultSession.Summary note : rows) {
            shown.add(itemFor(note, untitled, selecting && selectedIds.contains(note.id)));
        }

        rebuildChips(total, counts);
        tagScroll.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        adapter.submit(shown);
        toolbar.setTitle(selecting
                ? getString(R.string.selected_count, selectedIds.size())
                : StringMethods.getNameDB(session.file.getName()));
    }

    private static NotesAdapter.Item itemFor(final VaultSession.Summary note, final String untitled,
                                             final boolean selected) {
        final String title = note.title;
        final String shownTitle = title == null || title.isEmpty() ? untitled : title;
        if (note.file) {
            final FileKinds.Type type = FileKinds.typeOf(note.fileName);
            final FileKinds.Type shownType = type == null ? FileKinds.Type.TXT : type;
            return new NotesAdapter.Item(note.id, shownTitle, shownType.name(), shownType, false,
                    note.readOnly, selected);
        }
        final String tags = note.tags;
        final String subtitle = tags == null || tags.isEmpty() ? null : tags.replace(",", ", ");
        return new NotesAdapter.Item(note.id, shownTitle, subtitle, null, note.starred, note.readOnly, selected);
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
            session.vault.addFile(name, data);
        } catch (VaultException e) {
            toast(app, VaultMessages.messageFor(e.getReason()));
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

    private void toastText(final String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static void toast(final Context context, final int message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
