package com.cryptcage.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.cryptcage.utils.FileKinds;

/** Reads the files that the user picks for import. */
final class ImportSource {

    private ImportSource() {
    }

    /** The display name the provider gives for the picked file, or null if it has none. */
    static String displayName(final ContentResolver resolver, final Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // some providers refuse the query; the name check then rejects the file
        }
        return null;
    }

    /** Reads the whole stream, or returns null when it is larger than the single-file limit. */
    static byte[] readLimited(final ContentResolver resolver, final Uri uri) throws IOException {
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
}
