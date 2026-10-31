/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * An open vault as the screens use it. Bodies and file contents are read only when asked for,
 * so the list stays cheap even when the vault is large. Changes are saved by {@link #save()}.
 *
 * Every method is synchronized, so the screens and background tasks can share one session.
 * Search takes the lock once per note, which keeps the screen responsive while it runs.
 * After {@link #close()} the throwing methods fail with REJECTED and the others return nothing,
 * so a task that is still running when the vault locks does not crash the app.
 */
public final class VaultSession implements AutoCloseable {

    /** Total content the vault can hold. */
    public static final long CONTENT_LIMIT = NoteRepository.CONTENT_LIMIT;

    /** Largest single file that can be attached. */
    public static final long MAX_FILE_BYTES = FileKinds.MAX_FILE_BYTES;

    /** Largest note body that can be written. Notes already stored above it still open. */
    public static final long MAX_NOTE_BYTES = 8L * 1024 * 1024;

    /** Largest container file that can be imported. Leaves room for unused records. */
    public static final long MAX_CONTAINER_BYTES = 2L * CONTENT_LIMIT;

    /** What a list row needs. Bodies and file contents are not included. */
    public static final class Summary {
        public final long id;
        public final boolean file;
        public final String title;
        public final String tags;
        public final String fileName;
        public final boolean starred;
        public final boolean readOnly;
        public final long order;
        public final long size;

        Summary(final NoteRepository.Entry entry) {
            this.id = entry.id;
            this.file = entry.isFile();
            this.title = entry.title;
            this.tags = entry.tags;
            this.fileName = entry.fileName;
            this.starred = entry.starred;
            this.readOnly = entry.readOnly;
            this.order = entry.order;
            this.size = entry.size;
        }
    }

    private final NoteRepository repo;
    private boolean closed;

    private VaultSession(final NoteRepository repo) {
        this.repo = repo;
    }

    /** Creates a new, empty vault at a path that does not exist yet. */
    public static VaultSession create(final File file, final char[] password) throws VaultException {
        try {
            return new VaultSession(NoteRepository.create(file, password, Cryptox.DEFAULT_ITERATIONS));
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** Opens an existing vault. A wrong password and a damaged file give the same error. */
    public static VaultSession open(final File file, final char[] password) throws VaultException {
        try {
            return new VaultSession(NoteRepository.open(file, password));
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** True if the file starts with the container magic. Only checks the first bytes. */
    public static boolean isVault(final File file) {
        try (InputStream in = new FileInputStream(file)) {
            final byte[] head = new byte[4];
            return in.read(head) == head.length && looksLikeVault(head);
        } catch (IOException e) {
            return false;
        }
    }

    /** True if the bytes start with the container magic. Used to check an import before copying it. */
    public static boolean looksLikeVault(final byte[] head) {
        return head != null && head.length >= 4
                && head[0] == 'C' && head[1] == 'C' && head[2] == '3' && head[3] == 0;
    }

    /** All entries in creation order. Empty after close. */
    public synchronized List<Summary> list() {
        final List<Summary> out = new ArrayList<>();
        if (closed) {
            return out;
        }
        for (NoteRepository.Entry entry : repo.list()) {
            out.add(new Summary(entry));
        }
        return out;
    }

    public synchronized Summary summary(final long id) throws VaultException {
        return new Summary(entry(id));
    }

    /** The text of a note. */
    public synchronized String body(final long id) throws VaultException {
        try {
            return live().readBody(id);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** The contents of a file entry. */
    public synchronized byte[] fileData(final long id) throws VaultException {
        try {
            return live().readFile(id);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized long addNote(final String title, final String body, final String tags)
            throws VaultException {
        checkNoteSize(body);
        try {
            return live().addNote(title, body, tags);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void updateNote(final long id, final String title, final String body, final String tags)
            throws VaultException {
        checkNoteSize(body);
        try {
            live().updateNote(id, title, body, tags);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized long addFile(final String name, final byte[] data) throws VaultException {
        checkFileSize(data);
        try {
            return live().addFile(name, data);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void replaceFile(final long id, final String name, final byte[] data)
            throws VaultException {
        checkFileSize(data);
        try {
            live().replaceFile(id, name, data);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void setStarred(final long id, final boolean starred) throws VaultException {
        try {
            live().setStarred(id, starred);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void setReadOnly(final long id, final boolean readOnly) throws VaultException {
        try {
            live().setReadOnly(id, readOnly);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** Copies a note or file under a new title. The copy is placed right after the original. */
    public synchronized long duplicate(final long id, final String title) throws VaultException {
        try {
            return live().duplicate(id, title);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** Sets the manual order to the given ids, in that order. */
    public synchronized void reorder(final List<Long> ids) throws VaultException {
        try {
            live().reorder(ids);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void delete(final long id) throws VaultException {
        try {
            live().delete(id);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /**
     * Ids of entries whose title, tags or file name contain the text, or whose note body does.
     * Case is ignored. Every note body is read, so this can take a while on a large vault; call it
     * off the UI thread. An empty text matches everything.
     */
    public List<Long> search(final String text) throws VaultException {
        final String needle = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        final List<Long> ids = new ArrayList<>();
        synchronized (this) {
            for (NoteRepository.Entry entry : live().list()) {
                ids.add(entry.id);
            }
        }
        final List<Long> hits = new ArrayList<>();
        for (Long id : ids) {
            synchronized (this) {
                final NoteRepository.Entry entry = live().entry(id);
                if (entry == null) {
                    continue;
                }
                if (needle.isEmpty() || matchesIndex(entry, needle)) {
                    hits.add(id);
                } else if (!entry.isFile() && bodyContains(id, needle)) {
                    hits.add(id);
                }
            }
        }
        return hits;
    }

    /** Lower-cased tag names mapped to how many entries use them. Empty after close. */
    public synchronized Map<String, Integer> tagCounts() {
        final Map<String, Integer> counts = new TreeMap<>();
        if (closed) {
            return counts;
        }
        for (NoteRepository.Entry entry : repo.list()) {
            if (entry.isFile()) {
                continue;
            }
            for (String tag : TagText.splitTags(entry.tags)) {
                final String key = tag.toLowerCase(Locale.ROOT);
                final Integer count = counts.get(key);
                counts.put(key, count == null ? 1 : count + 1);
            }
        }
        return counts;
    }

    /** Ids of notes carrying the tag. Empty after close. */
    public synchronized List<Long> withTag(final String tag) {
        final List<Long> ids = new ArrayList<>();
        if (tag == null || closed) {
            return ids;
        }
        final String key = tag.trim().toLowerCase(Locale.ROOT);
        for (NoteRepository.Entry entry : repo.list()) {
            if (entry.isFile()) {
                continue;
            }
            for (String part : TagText.splitTags(entry.tags)) {
                if (part.toLowerCase(Locale.ROOT).equals(key)) {
                    ids.add(entry.id);
                    break;
                }
            }
        }
        return ids;
    }

    /** Saves every change made since the last save. */
    public synchronized void save() throws VaultException {
        try {
            live().commit();
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /**
     * Makes the vault open with a new password. Pending changes are saved first. The old password
     * stops working as soon as this returns. Slow; run it off the UI thread.
     */
    public synchronized void changePassword(final char[] password) throws VaultException {
        try {
            live().changePassword(password);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** True when the file holds enough unused data that a rewrite would be worth it. */
    public synchronized boolean needsCompaction() throws VaultException {
        try {
            return live().needsCompaction();
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** Rewrites the file without unused data. Saves pending changes first. Slow; run it off the UI thread. */
    public synchronized void compact() throws VaultException {
        try {
            live().compact();
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** Content counted against {@link #CONTENT_LIMIT}. Zero after close. */
    public synchronized long usedBytes() {
        return closed ? 0 : repo.usedBytes();
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            repo.close();
        }
    }

    private NoteRepository live() throws VaultException {
        if (closed) {
            throw new VaultException(VaultException.Reason.REJECTED, null);
        }
        return repo;
    }

    private NoteRepository.Entry entry(final long id) throws VaultException {
        final NoteRepository.Entry entry = live().entry(id);
        if (entry == null) {
            throw new VaultException(VaultException.Reason.REJECTED, null);
        }
        return entry;
    }

    private boolean bodyContains(final long id, final String needle) throws VaultException {
        try {
            return live().readBody(id).toLowerCase(Locale.ROOT).contains(needle);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    private static boolean matchesIndex(final NoteRepository.Entry entry, final String needle) {
        return contains(entry.title, needle) || contains(entry.tags, needle) || contains(entry.fileName, needle);
    }

    private static boolean contains(final String value, final String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** Encrypts a draft with a key that only this vault can derive. The result can be stored anywhere. */
    public synchronized byte[] sealDraft(final byte[] plain) throws VaultException {
        try {
            return live().sealDraft(plain);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** Decrypts a draft made by {@link #sealDraft}. Fails for a draft of another vault. */
    public synchronized byte[] openDraft(final byte[] sealed) throws VaultException {
        try {
            return live().openDraft(sealed);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    private static void checkNoteSize(final String body) throws VaultException {
        if (body != null && body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_NOTE_BYTES) {
            throw new VaultException(VaultException.Reason.TOO_LARGE, null);
        }
    }

    private static void checkFileSize(final byte[] data) throws VaultException {
        if (data != null && data.length > MAX_FILE_BYTES) {
            throw new VaultException(VaultException.Reason.TOO_LARGE, null);
        }
    }

    private static VaultException translate(final StoreException e) {
        switch (e.reason()) {
            case NOT_A_STORE:
                return new VaultException(VaultException.Reason.NOT_A_VAULT, e);
            case AUTH_FAILED:
            case CORRUPT:
                return new VaultException(VaultException.Reason.BAD_PASSWORD_OR_DAMAGED, e);
            case IO:
            case EXISTS:
                return new VaultException(VaultException.Reason.IO, e);
            case LIMIT_REACHED:
                return new VaultException(VaultException.Reason.LIMIT_REACHED, e);
            case BAD_ARGUMENT:
                return new VaultException(VaultException.Reason.REJECTED, e);
            default:
                return new VaultException(VaultException.Reason.INTERNAL, e);
        }
    }
}
