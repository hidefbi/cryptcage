/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.Collections;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.cryptcage.R;

/** One note on a phone. Wide screens show the same NoteFragment beside the list instead. */
public class NoteActivity extends SecureActivity implements NoteFragment.Listener {

    private ExportFlow exportFlow;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exportFlow = new ExportFlow(this);
        if (redirectIfLocked()) {
            return;
        }
        setContentView(R.layout.activity_note);
        if (savedInstanceState == null) {
            final long noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1L);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.note_container, NoteFragment.newInstance(noteId, false))
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        redirectIfLocked();
    }

    @Override
    public void onBackPressed() {
        final Fragment content = getSupportFragmentManager().findFragmentById(R.id.note_container);
        if (content instanceof NoteFragment && ((NoteFragment) content).handleBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onNoteChanged(final long noteId) {
        // The list refreshes when this screen closes, so nothing to do here.
    }

    @Override
    public void onNoteSaved(final long noteId) {
        finish();
    }

    @Override
    public void onNoteRemoved() {
        finish();
    }

    @Override
    public void onExportNote(final long noteId) {
        exportFlow.start(Collections.singletonList(noteId));
    }
}
