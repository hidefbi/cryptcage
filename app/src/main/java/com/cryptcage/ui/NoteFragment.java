/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import android.content.Intent;
import android.os.Bundle;
import android.os.Trace;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.FileKinds;
import com.cryptcage.utils.FileTypes;
import com.cryptcage.utils.VaultException;
import com.cryptcage.utils.VaultSession;
import com.cryptcage.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * One note, or one stored file, read and edited in place. The same screen serves the list's
 * detail pane on wide displays and NoteActivity on phones.
 * A stored text file shows its name and text, and PDF files are opened by the caller instead.
 */
public class NoteFragment extends Fragment {

    /** Told when the note changes or goes away, so the host can refresh or close the screen. */
    public interface Listener {
        void onNoteChanged(long noteId);

        /** A save finished. A phone screen for one note closes here and returns to the list. */
        void onNoteSaved(long noteId);

        void onNoteRemoved();

        /** Export runs in the host, which registers its file picker before the screen starts. */
        void onExportNote(long noteId);
    }

    private static final String ARG_NOTE = "note_id";
    private static final String ARG_TWO_PANE = "two_pane";
    private static final String STATE_EDITING = "editing";
    private static final String STATE_TITLE = "title";
    private static final String STATE_TAGS = "tags";
    private static final String STATE_BODY = "body";

    private long noteId;
    private boolean twoPane;
    private boolean editing;
    private boolean fileMode;
    private boolean canEdit;
    private boolean canView;
    private boolean starred;
    private byte[] originalData;
    private String originalTitle = "";
    private String originalTags = "";
    private String originalBody = "";
    /** True once the note has been shown, so returning to the screen re-reads it. */
    private boolean loaded;
    /** True while a read runs on the worker thread. */
    private boolean loading;
    /** Saved state to restore once the note is read, when an edit was in progress. */
    private Bundle pendingRestore;

    /** What is read from the vault for one note. It is built on the worker thread. */
    private static final class Loaded {
        boolean fileMode;
        boolean canEdit;
        boolean canView;
        boolean starred;
        byte[] data;
        String title;
        String tags;
        String body;
        ArrayList<String> knownTags;
    }

    private MaterialToolbar toolbar;
    private View readGroup;
    private View editGroup;
    private TextView titleView;
    private TextView tagsView;
    private TextView bodyView;
    private TextView emptyView;
    private TextInputLayout titleLayout;
    private TextInputLayout tagsLayout;
    private TextInputEditText titleField;
    private MultiAutoCompleteTextView tagsField;
    private TextInputEditText bodyField;

    /** A fragment for the note with this id. A negative id starts a new note in edit mode. */
    public static NoteFragment newInstance(final long noteId, final boolean twoPane) {
        final NoteFragment fragment = new NoteFragment();
        final Bundle args = new Bundle();
        args.putLong(ARG_NOTE, noteId);
        args.putBoolean(ARG_TWO_PANE, twoPane);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note, container, false);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        toolbar = view.findViewById(R.id.toolbar);
        readGroup = view.findViewById(R.id.read_group);
        editGroup = view.findViewById(R.id.edit_group);
        titleView = view.findViewById(R.id.note_title);
        tagsView = view.findViewById(R.id.note_tags);
        bodyView = view.findViewById(R.id.note_body);
        emptyView = view.findViewById(R.id.note_empty);
        titleLayout = view.findViewById(R.id.title_layout);
        tagsLayout = view.findViewById(R.id.tags_layout);
        titleField = view.findViewById(R.id.field_title);
        tagsField = view.findViewById(R.id.field_tags);
        bodyField = view.findViewById(R.id.field_body);

        twoPane = requireArguments().getBoolean(ARG_TWO_PANE, false);
        noteId = requireArguments().getLong(ARG_NOTE, -1L);

        final App.Session session = App.session();
        if (session == null) {
            removedLater();
            return;
        }
        tagsField.setTokenizer(new TagTokenizer());

        toolbar.inflateMenu(R.menu.note);
        toolbar.setOnMenuItemClickListener(this::onMenuItem);
        toolbar.setNavigationOnClickListener(v -> onNavigation());

        final boolean restoring = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_EDITING, false);
        if (noteId < 0) {
            fileMode = false;
            canEdit = true;
            canView = true;
            loadTags();
            enterEdit();
            if (restoring) {
                restoreFields(savedInstanceState);
            }
        } else {
            pendingRestore = restoring ? savedInstanceState : null;
            load();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // The first read is started by onViewCreated. Returning to a screen that is not being
        // edited reads the note again, since it may have changed elsewhere.
        if (toolbar == null || editing || noteId < 0 || !loaded) {
            return;
        }
        load();
    }

    /** Reads the note on the worker thread, then shows it on the UI thread. */
    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        final long id = noteId;
        Tasks.io(() -> {
            final Loaded in = readNote(id);
            Tasks.ui(() -> {
                loading = false;
                if (!isAdded()) {
                    return;
                }
                if (in == null) {
                    removedLater();
                    return;
                }
                apply(in);
            });
        });
    }

    /** Shows what {@link #readNote} read. If an edit was being restored, the screen goes back into it. */
    private void apply(final Loaded in) {
        loaded = true;
        fileMode = in.fileMode;
        canEdit = in.canEdit;
        canView = in.canView;
        starred = in.starred;
        originalData = in.data;
        originalTitle = in.title;
        originalTags = in.tags;
        originalBody = in.body;
        tagsField.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, in.knownTags));
        final Bundle restore = pendingRestore;
        pendingRestore = null;
        if (restore != null && canEdit) {
            enterEdit();
            restoreFields(restore);
        } else if (!editing) {
            showRead();
        }
    }

    /** For a new note only the list of known tags is needed. */
    private void loadTags() {
        Tasks.io(() -> {
            final App.Session session = App.session();
            final ArrayList<String> known = session == null
                    ? new ArrayList<>() : new ArrayList<>(session.vault.tagCounts().keySet());
            Tasks.ui(() -> {
                if (isAdded() && tagsField != null) {
                    tagsField.setAdapter(new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_dropdown_item_1line, known));
                }
            });
        });
    }

    private void restoreFields(final Bundle state) {
        titleField.setText(state.getString(STATE_TITLE, ""));
        tagsField.setText(state.getString(STATE_TAGS, ""));
        bodyField.setText(state.getString(STATE_BODY, ""));
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_EDITING, editing);
        if (editing) {
            outState.putString(STATE_TITLE, titleField.getText().toString());
            outState.putString(STATE_TAGS, tagsField.getText().toString());
            outState.putString(STATE_BODY, bodyField.getText().toString());
        }
    }

    /** Leaves edit mode if it is on. Returns true when the back press was used here. */
    public boolean handleBack() {
        if (!editing) {
            return false;
        }
        cancelEdit();
        return true;
    }

    private Listener listener() {
        return (Listener) requireActivity();
    }

    /** Reports a removed note after the current fragment transaction, never inside it. */
    private void removedLater() {
        final View view = getView();
        if (view != null) {
            view.post(() -> {
                if (isAdded()) {
                    listener().onNoteRemoved();
                }
            });
        }
    }

    /**
     * Reads the stored note or file. Runs on the worker thread, never on the UI thread.
     * Returns null if the note no longer exists.
     */
    private static Loaded readNote(final long id) {
        final App.Session session = App.session();
        if (session == null) {
            return null;
        }
        Trace.beginSection("cc.readNote");
        try {
            final Loaded in = new Loaded();
            final VaultSession.Summary note = session.vault.summary(id);
            if (note.file) {
                final byte[] stored = session.vault.fileData(id);
                final FileTypes.Check check = FileTypes.check(note.fileName, stored);
                in.fileMode = true;
                in.canEdit = check.editable();
                in.canView = check.text();
                in.starred = false;
                in.data = stored;
                in.title = nullToEmpty(note.fileName);
                in.tags = "";
                in.body = in.canView ? new String(stored, StandardCharsets.UTF_8) : "";
            } else {
                in.fileMode = false;
                in.canEdit = true;
                in.canView = true;
                in.starred = note.starred;
                in.data = null;
                in.title = nullToEmpty(note.title);
                in.tags = nullToEmpty(note.tags);
                in.body = session.vault.body(id);
            }
            in.knownTags = new ArrayList<>(session.vault.tagCounts().keySet());
            return in;
        } catch (VaultException e) {
            return null;
        } finally {
            Trace.endSection();
        }
    }

    private void showRead() {
        editing = false;
        readGroup.setVisibility(View.VISIBLE);
        editGroup.setVisibility(View.GONE);

        titleView.setText(originalTitle.isEmpty() ? getString(R.string.no_title) : originalTitle);
        tagsView.setText(originalTags);
        tagsView.setVisibility(originalTags.isEmpty() ? View.GONE : View.VISIBLE);
        final String body = canView ? originalBody : "";
        bodyView.setText(body);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize());
        bodyView.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
        if (!canView) {
            emptyView.setText(R.string.file_noPreview);
            emptyView.setVisibility(View.VISIBLE);
        } else if (body.isEmpty()) {
            emptyView.setText(R.string.no_textNote);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
        updateToolbar();
    }

    private void enterEdit() {
        if (!canEdit) {
            return;
        }
        editing = true;
        titleField.setText(originalTitle);
        tagsField.setText(originalTags);
        bodyField.setText(originalBody);
        titleLayout.setHint(getString(fileMode ? R.string.file_hint_name : R.string.hint_field_title));
        tagsLayout.setVisibility(fileMode ? View.GONE : View.VISIBLE);
        readGroup.setVisibility(View.GONE);
        editGroup.setVisibility(View.VISIBLE);
        updateToolbar();
    }

    /** Navigation icon: a close icon while editing, back on a phone, none in the two-pane layout. */
    private void updateToolbar() {
        if (editing) {
            toolbar.setNavigationIcon(SecureActivity.themeDrawable(requireContext(),
                    androidx.appcompat.R.attr.actionModeCloseDrawable));
            toolbar.setNavigationContentDescription(R.string.action_cancelEdit);
        } else if (twoPane) {
            toolbar.setNavigationIcon(null);
        } else {
            toolbar.setNavigationIcon(SecureActivity.themeDrawable(requireContext(),
                    androidx.appcompat.R.attr.homeAsUpIndicator));
            toolbar.setNavigationContentDescription(null);
        }

        final Menu menu = toolbar.getMenu();
        menu.findItem(R.id.action_starred)
                .setVisible(!editing && !fileMode)
                .setIcon(starred ? R.drawable.icon_star : R.drawable.icon_star_outline);
        menu.findItem(R.id.action_edit).setVisible(!editing && canEdit);
        menu.findItem(R.id.action_save).setVisible(editing);
        menu.findItem(R.id.action_share).setVisible(!editing && !fileMode);
        menu.findItem(R.id.action_exportFile).setVisible(!editing);
        menu.findItem(R.id.action_delete).setVisible(!editing);
    }

    private void onNavigation() {
        if (editing) {
            cancelEdit();
        } else {
            requireActivity().onBackPressed();
        }
    }

    private boolean onMenuItem(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_starred) {
            toggleStar();
        } else if (id == R.id.action_edit) {
            enterEdit();
        } else if (id == R.id.action_save) {
            save();
        } else if (id == R.id.action_exportFile) {
            listener().onExportNote(noteId);
        } else if (id == R.id.action_share) {
            share();
        } else if (id == R.id.action_delete) {
            confirmDelete();
        } else {
            return false;
        }
        return true;
    }

    private void cancelEdit() {
        if (!isChanged()) {
            discardEdit();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.dialog_backNoSave)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> discardEdit())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void discardEdit() {
        if (noteId < 0) {
            listener().onNoteRemoved();
        } else {
            showRead();
        }
    }

    private boolean isChanged() {
        return !originalTitle.equals(titleField.getText().toString())
                || !originalTags.equals(tagsField.getText().toString())
                || !originalBody.equals(bodyField.getText().toString());
    }

    private void save() {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        final String title = titleField.getText().toString();
        final String body = bodyField.getText().toString();
        if (fileMode) {
            saveFile(session, title, body);
        } else {
            saveNote(session, title, body, tagsField.getText().toString());
        }
    }

    private void saveNote(final App.Session session, final String title, final String body, final String tags) {
        final long id;
        try {
            if (noteId < 0) {
                id = session.vault.addNote(title, body, tags);
            } else {
                session.vault.updateNote(noteId, title, body, tags);
                id = noteId;
            }
        } catch (VaultException e) {
            toast(VaultMessages.messageFor(e.getReason()));
            return;
        }
        noteId = id;
        originalTitle = title;
        originalTags = tags;
        originalBody = body;
        finishSaved(id);
    }

    /**
     * Saves a stored text file. The name keeps the original extension.
     * If the text was not touched, the original bytes are written back unchanged.
     */
    private void saveFile(final App.Session session, final String title, final String body) {
        final String name = FileTypes.cleanFileName(withOriginalExtension(title, originalTitle));
        if (name == null) {
            toast(R.string.toast_nameInvalid);
            return;
        }
        final boolean textChanged = !body.equals(originalBody);
        final byte[] data = textChanged ? body.getBytes(StandardCharsets.UTF_8) : originalData;
        if (data.length > FileKinds.MAX_FILE_BYTES) {
            toast(R.string.toast_tooLarge);
            return;
        }
        try {
            session.vault.replaceFile(noteId, name, data);
        } catch (VaultException e) {
            toast(VaultMessages.messageFor(e.getReason()));
            return;
        }
        originalTitle = name;
        originalBody = body;
        originalData = data.clone();
        finishSaved(noteId);
    }

    private void finishSaved(final long id) {
        App.persist(requireContext());
        toast(R.string.toast_success_newNote);
        editing = false;
        showRead();
        listener().onNoteSaved(id);
    }

    private void toggleStar() {
        final App.Session session = App.session();
        if (session == null) {
            return;
        }
        try {
            session.vault.setStarred(noteId, !starred);
        } catch (VaultException e) {
            toast(VaultMessages.messageFor(e.getReason()));
            return;
        }
        starred = !starred;
        App.persist(requireContext());
        updateToolbar();
        listener().onNoteChanged(noteId);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.title_deleteNote)
                .setMessage(R.string.dialog_deleteNote)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    final App.Session session = App.session();
                    if (session != null) {
                        try {
                            session.vault.delete(noteId);
                        } catch (VaultException e) {
                            toast(VaultMessages.messageFor(e.getReason()));
                            return;
                        }
                        App.persist(requireContext());
                    }
                    toast(R.string.toast_success_deleteNote);
                    listener().onNoteRemoved();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void share() {
        if (fileMode) {
            return;
        }
        final Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, originalTitle);
        send.putExtra(Intent.EXTRA_TEXT, originalBody);
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser)));
    }

    /** The typed name with the original extension, so that renaming never changes the file type. */
    private static String withOriginalExtension(final String title, final String original) {
        final String typed = title.trim();
        final String stem = FileTypes.kindOf(typed) == null ? typed : typed.substring(0, typed.lastIndexOf('.'));
        return stem + "." + FileTypes.extensionOf(original);
    }

    private float textSize() {
        try {
            return Float.parseFloat(PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("pref_textSize", getString(R.string.pref_textSize_default)));
        } catch (NumberFormatException e) {
            return 20f;
        }
    }

    private void toast(final int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private static String nullToEmpty(final String s) {
        return s == null ? "" : s;
    }
}
