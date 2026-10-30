package com.cryptcage.ui;

import java.util.Collections;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.cryptcage.app.App;
import com.cryptcage.app.Tasks;
import com.cryptcage.utils.FileKinds;
import com.cryptcage.utils.Notes;
import com.cryptcage.R;

/** Shows one note, or one stored text file. PDF files are handed to FileViewActivity. */
public class ReadNoteActivity extends SecureActivity {

    private long noteId;
    private TextView bodyView;
    private TextView emptyView;
    private ExportFlow exportFlow;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLocked()) {
            return;
        }
        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1L);
        final Notes first = currentNote();
        if (first != null && isPdf(first)) {
            final Intent viewer = new Intent(this, FileViewActivity.class);
            viewer.putExtra(EXTRA_NOTE_ID, noteId);
            startActivity(viewer);
            finish();
            return;
        }
        setContentView(R.layout.activity_read_note);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        bodyView = findViewById(R.id.textViewNote);
        emptyView = findViewById(R.id.textViewNoNote);
        exportFlow = new ExportFlow(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (redirectIfLocked()) {
            return;
        }
        final Notes note = currentNote();
        if (note == null) {
            finish();
            return;
        }
        setTitle(note.getTitle());
        final String text = note.isFile() ? decode(note.getData()) : note.getNote();
        final boolean blank = text == null || text.isEmpty();
        bodyView.setText(blank ? "" : text);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize());
        emptyView.setText(blank ? getString(R.string.no_textNote) : "");
        emptyView.setVisibility(blank ? View.VISIBLE : View.GONE);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.read_note, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final Notes note = currentNote();
        final boolean file = note != null && note.isFile();
        final MenuItem star = menu.findItem(R.id.action_starred);
        if (star != null) {
            star.setVisible(!file);
            if (note != null && !file) {
                star.setIcon(note.isStarred() ? R.drawable.icon_star : R.drawable.icon_star_outline);
            }
        }
        final MenuItem share = menu.findItem(R.id.action_share);
        if (share != null) {
            share.setVisible(!file);
        }
        final MenuItem export = menu.findItem(R.id.action_exportFile);
        if (export != null) {
            export.setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_starred) {
            toggleStar();
        } else if (id == R.id.action_edit) {
            final Intent edit = new Intent(this, EditNoteActivity.class);
            edit.putExtra(EXTRA_NOTE_ID, noteId);
            startActivity(edit);
        } else if (id == R.id.action_delete) {
            confirmDelete();
        } else if (id == R.id.action_share) {
            share();
        } else if (id == R.id.action_exportFile) {
            exportFlow.start(Collections.singletonList(noteId));
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private Notes currentNote() {
        final App.Session session = App.session();
        if (session == null) {
            return null;
        }
        synchronized (session.db) {
            return session.db.get(noteId);
        }
    }

    private static boolean isPdf(final Notes note) {
        return note.isFile() && FileKinds.typeOf(note.getFileName()) == FileKinds.Type.PDF;
    }

    private static String decode(final byte[] data) {
        return data == null ? "" : new String(data, StandardCharsets.UTF_8);
    }

    private void toggleStar() {
        final App.Session session = App.session();
        final Notes note = currentNote();
        if (session == null || note == null) {
            return;
        }
        synchronized (session.db) {
            session.db.setStarred(noteId, !note.isStarred());
        }
        App.persist(this);
        invalidateOptionsMenu();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_deleteNote)
                .setMessage(R.string.dialog_deleteNote)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    final App.Session session = App.session();
                    if (session != null) {
                        synchronized (session.db) {
                            session.db.delete(noteId);
                        }
                        App.persist(this);
                    }
                    finish();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void share() {
        final Notes note = currentNote();
        if (note == null || note.isFile()) {
            return;
        }
        final Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, note.getTitle());
        send.putExtra(Intent.EXTRA_TEXT, note.getNote());
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser)));
    }

    private void toast(final int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private float textSize() {
        try {
            return Float.parseFloat(PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("pref_textSize", getString(R.string.pref_textSize_default)));
        } catch (NumberFormatException e) {
            return 20f;
        }
    }
}
