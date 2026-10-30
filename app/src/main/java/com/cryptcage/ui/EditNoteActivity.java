package com.cryptcage.ui;

import java.nio.charset.StandardCharsets;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.cryptcage.app.App;
import com.cryptcage.utils.DatabaseForNotes;
import com.cryptcage.utils.FileKinds;
import com.cryptcage.utils.FileTypes;
import com.cryptcage.utils.Notes;
import com.cryptcage.R;

import java.util.ArrayList;

/**
 * Creates or edits a note. A stored text file (.ovpn, .conf, .txt) opens here too,
 * with its name and text as the fields. PDF files cannot be edited.
 */
public class EditNoteActivity extends SecureActivity {

    private long noteId = -1L;
    private boolean fileMode;
    private byte[] originalData;
    private EditText titleField;
    private MultiAutoCompleteTextView tagsField;
    private EditText bodyField;

    private String originalTitle = "";
    private String originalTags = "";
    private String originalBody = "";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLocked()) {
            return;
        }
        setContentView(R.layout.activity_add_new_note);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        titleField = findViewById(R.id.editTextTitle);
        tagsField = findViewById(R.id.editTextTags);
        bodyField = findViewById(R.id.editTextNote);

        final DatabaseForNotes db = App.session().db;
        final ArrayList<String> knownTags = new ArrayList<>();
        synchronized (db) {
            knownTags.addAll(db.tagCounts().keySet());
        }
        tagsField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, knownTags));
        tagsField.setTokenizer(new TagTokenizer());

        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1L);
        if (noteId >= 0) {
            final Notes note;
            synchronized (db) {
                note = db.get(noteId);
            }
            if (note == null) {
                finish();
                return;
            }
            if (note.isFile()) {
                final byte[] stored = note.getData() == null ? new byte[0] : note.getData();
                if (!FileTypes.check(note.getFileName(), stored).editable()) {
                    toast(R.string.file_noPreview);
                    finish();
                    return;
                }
                fileMode = true;
                originalData = stored.clone();
                originalTitle = nullToEmpty(note.getFileName());
                originalBody = new String(originalData, StandardCharsets.UTF_8);
                titleField.setHint(R.string.file_hint_name);
                findViewById(R.id.textView2).setVisibility(View.GONE);
                tagsField.setVisibility(View.GONE);
            } else {
                originalTitle = nullToEmpty(note.getTitle());
                originalTags = nullToEmpty(note.getTags());
                originalBody = nullToEmpty(note.getNote());
            }
            titleField.setText(originalTitle);
            tagsField.setText(originalTags);
            bodyField.setText(originalBody);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.save_change, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_save) {
            save();
        } else if (id == android.R.id.home) {
            onBackPressed();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!isChanged()) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_backNoSave)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean isChanged() {
        return !originalTitle.equals(titleField.getText().toString())
                || !originalTags.equals(tagsField.getText().toString())
                || !originalBody.equals(bodyField.getText().toString());
    }

    private void save() {
        final App.Session session = App.session();
        if (session == null) {
            finish();
            return;
        }
        final String title = titleField.getText().toString();
        final String body = bodyField.getText().toString();
        if (fileMode) {
            saveFile(session, title, body);
            return;
        }
        final String tags = tagsField.getText().toString();
        try {
            synchronized (session.db) {
                if (noteId < 0) {
                    session.db.add(title, body, tags);
                } else {
                    session.db.update(noteId, title, body, tags);
                }
            }
        } catch (DatabaseForNotes.ContentLimitException e) {
            toast(R.string.toast_vaultFull);
            return;
        }
        finishSaved();
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
            synchronized (session.db) {
                session.db.replaceFile(noteId, name, data);
            }
        } catch (DatabaseForNotes.ContentLimitException e) {
            toast(R.string.toast_vaultFull);
            return;
        }
        finishSaved();
    }

    /** The typed name with the original extension, so that renaming never changes the file type. */
    private static String withOriginalExtension(final String title, final String original) {
        final String typed = title.trim();
        final String stem = FileTypes.kindOf(typed) == null ? typed : typed.substring(0, typed.lastIndexOf('.'));
        return stem + "." + FileTypes.extensionOf(original);
    }

    private void finishSaved() {
        App.persist(this);
        toast(R.string.toast_success_newNote);
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void toast(final int message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static String nullToEmpty(final String s) {
        return s == null ? "" : s;
    }
}
