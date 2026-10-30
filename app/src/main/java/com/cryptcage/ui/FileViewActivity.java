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
import java.util.Arrays;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.cryptcage.app.App;
import com.cryptcage.utils.VaultException;
import com.cryptcage.R;

/**
 * Shows a stored PDF one page at a time. Pages are rendered inside the app,
 * so the FLAG_SECURE protection applies and no external viewer is involved.
 */
public class FileViewActivity extends SecureActivity {

    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private ImageView pageView;
    private TextView pageLabel;
    private Button prevButton;
    private Button nextButton;
    private Bitmap shown;
    private int pageIndex;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLocked()) {
            return;
        }
        setContentView(R.layout.activity_file_view);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        pageView = findViewById(R.id.pageImage);
        pageLabel = findViewById(R.id.pageLabel);
        prevButton = findViewById(R.id.buttonPrev);
        nextButton = findViewById(R.id.buttonNext);
        prevButton.setOnClickListener(v -> showPage(pageIndex - 1));
        nextButton.setOnClickListener(v -> showPage(pageIndex + 1));

        final byte[] pdf = loadPdf(getIntent().getLongExtra(EXTRA_NOTE_ID, -1L));
        if (pdf == null || !openRenderer(pdf)) {
            Toast.makeText(this, R.string.toast_pdfFailed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        showPage(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!App.isUnlocked()) {
            redirectIfLocked();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (renderer != null) {
            renderer.close();
        }
        if (descriptor != null) {
            try {
                descriptor.close();
            } catch (IOException ignored) {
                // the renderer is already closed; nothing else to release
            }
        }
        if (shown != null) {
            shown.recycle();
        }
    }

    private static byte[] loadPdf(final long id) {
        final App.Session session = App.session();
        if (session == null) {
            return null;
        }
        try {
            if (!session.vault.summary(id).file) {
                return null;
            }
            return session.vault.fileData(id);
        } catch (VaultException e) {
            return null;
        }
    }

    /**
     * PdfRenderer needs a seekable file. The bytes are written to the app cache,
     * the file is removed right after it is opened, and the copy is wiped from memory.
     */
    private boolean openRenderer(final byte[] pdf) {
        final File file = new File(getCacheDir(), "view-" + System.nanoTime() + ".pdf");
        try {
            final FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(pdf);
                out.flush();
                out.getFD().sync();
            } finally {
                out.close();
            }
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            file.delete();
            renderer = new PdfRenderer(descriptor);
            return true;
        } catch (IOException | RuntimeException e) {
            file.delete();
            return false;
        } finally {
            Arrays.fill(pdf, (byte) 0);
        }
    }

    private void showPage(final int index) {
        if (renderer == null || index < 0 || index >= renderer.getPageCount()) {
            return;
        }
        pageIndex = index;
        final Bitmap bitmap;
        final PdfRenderer.Page page = renderer.openPage(index);
        try {
            final int width = getResources().getDisplayMetrics().widthPixels;
            final int height = Math.max(1, Math.round(width * page.getHeight() / (float) page.getWidth()));
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        } finally {
            page.close();
        }
        pageView.setImageBitmap(bitmap);
        if (shown != null) {
            shown.recycle();
        }
        shown = bitmap;
        final int count = renderer.getPageCount();
        pageLabel.setText(getString(R.string.pdf_page, index + 1, count));
        prevButton.setEnabled(index > 0);
        nextButton.setEnabled(index < count - 1);
    }
}
