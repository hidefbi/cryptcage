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
 * Search takes the lock once per note, so the screen stays responsive while it runs.
 */
public final class VaultSession implements AutoCloseable {

    /** Total content the vault can hold. */
    public static final long CONTENT_LIMIT = NoteRepository.CONTENT_LIMIT;

    /** Largest single file that can be attached. */
    public static final long MAX_FILE_BYTES = FileKinds.MAX_FILE_BYTES;

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
        public final long size;

        Summary(final NoteRepository.Entry entry) {
            this.id = entry.id;
            this.file = entry.isFile();
            this.title = entry.title;
            this.tags = entry.tags;
            this.fileName = entry.fileName;
            this.starred = entry.starred;
            this.readOnly = entry.readOnly;
            this.size = entry.size;
        }
    }

    private final NoteRepository repo;

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
            return in.read(head) == head.length && head[0] == 'C' && head[1] == 'C' && head[2] == '3' && head[3] == 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** All entries in creation order. */
    public synchronized List<Summary> list() {
        final List<Summary> out = new ArrayList<>();
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
            return repo.readBody(id);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** The contents of a file entry. */
    public synchronized byte[] fileData(final long id) throws VaultException {
        try {
            return repo.readFile(id);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized long addNote(final String title, final String body, final String tags)
            throws VaultException {
        try {
            return repo.addNote(title, body, tags);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void updateNote(final long id, final String title, final String body, final String tags)
            throws VaultException {
        try {
            repo.updateNote(id, title, body, tags);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized long addFile(final String name, final byte[] data) throws VaultException {
        checkFileSize(data);
        try {
            return repo.addFile(name, data);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void replaceFile(final long id, final String name, final byte[] data)
            throws VaultException {
        checkFileSize(data);
        try {
            repo.replaceFile(id, name, data);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void setStarred(final long id, final boolean starred) throws VaultException {
        try {
            repo.setStarred(id, starred);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void setReadOnly(final long id, final boolean readOnly) throws VaultException {
        try {
            repo.setReadOnly(id, readOnly);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    public synchronized void delete(final long id) throws VaultException {
        try {
            repo.delete(id);
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
        final List<Long> ids;
        synchronized (this) {
            ids = new ArrayList<>();
            for (NoteRepository.Entry entry : repo.list()) {
                ids.add(entry.id);
            }
        }
        final List<Long> hits = new ArrayList<>();
        for (Long id : ids) {
            synchronized (this) {
                final NoteRepository.Entry entry = repo.entry(id);
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

    /** Lower-cased tag names mapped to how many entries use them. */
    public synchronized Map<String, Integer> tagCounts() {
        final Map<String, Integer> counts = new TreeMap<>();
        for (NoteRepository.Entry entry : repo.list()) {
            if (entry.isFile()) {
                continue;
            }
            for (String tag : DatabaseForNotes.splitTags(entry.tags)) {
                final String key = tag.toLowerCase(Locale.ROOT);
                final Integer count = counts.get(key);
                counts.put(key, count == null ? 1 : count + 1);
            }
        }
        return counts;
    }

    /** Ids of notes carrying the tag. */
    public synchronized List<Long> withTag(final String tag) {
        final List<Long> ids = new ArrayList<>();
        if (tag == null) {
            return ids;
        }
        final String key = tag.trim().toLowerCase(Locale.ROOT);
        for (NoteRepository.Entry entry : repo.list()) {
            if (entry.isFile()) {
                continue;
            }
            for (String part : DatabaseForNotes.splitTags(entry.tags)) {
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
            repo.commit();
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
            repo.changePassword(password);
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** True when the file holds enough unused data that a rewrite would be worth it. */
    public synchronized boolean needsCompaction() throws VaultException {
        try {
            return repo.needsCompaction();
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** Rewrites the file without unused data. Saves pending changes first. Slow; run it off the UI thread. */
    public synchronized void compact() throws VaultException {
        try {
            repo.compact();
        } catch (StoreException e) {
            throw translate(e);
        }
    }

    /** Content counted against {@link #CONTENT_LIMIT}. */
    public synchronized long usedBytes() {
        return repo.usedBytes();
    }

    @Override
    public synchronized void close() {
        repo.close();
    }

    private NoteRepository.Entry entry(final long id) throws VaultException {
        final NoteRepository.Entry entry = repo.entry(id);
        if (entry == null) {
            throw new VaultException(VaultException.Reason.REJECTED, null);
        }
        return entry;
    }

    private boolean bodyContains(final long id, final String needle) throws VaultException {
        try {
            return repo.readBody(id).toLowerCase(Locale.ROOT).contains(needle);
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
