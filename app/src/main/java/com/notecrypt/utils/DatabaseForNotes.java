package com.notecrypt.utils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** In-memory copy of an unlocked vault. Persisted through {@link DatabaseCodec}. */
public class DatabaseForNotes {

    /** Upper bound for note text plus file data. Leaves room under the 64 MB file limit. */
    public static final long CONTENT_BUDGET = 56L * 1024 * 1024;

    /** Rough per-record overhead in the saved payload. */
    private static final long RECORD_OVERHEAD = 64L;

    /** Thrown when a change would push the vault past {@link #CONTENT_BUDGET}. */
    public static final class ContentLimitException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public ContentLimitException() {
            super("vault content limit reached");
        }
    }

    private long position;
    private Map<Long, Notes> notes = new HashMap<>();
    private Map<String, TreeSet<Long>> mapTag = new HashMap<>();

    public long add(final String title, final String note, final String tags) {
        final String cleaned = cleanTags(tags);
        ensureRoom(noteBytes(title, note, cleaned));
        final long id = position++;
        final Notes item = new Notes(title, note);
        item.setTags(cleaned);
        notes.put(id, item);
        indexAdd(id, item.getTags());
        return id;
    }

    public void update(final long id, final String title, final String note, final String tags) {
        final Notes item = requireNote(id);
        final String cleaned = cleanTags(tags);
        final long delta = noteBytes(title, note, cleaned) - itemBytes(item);
        ensureRoom(delta);
        indexRemove(id, item.getTags());
        item.setTitle(title);
        item.setNote(note);
        item.setTags(cleaned);
        indexAdd(id, item.getTags());
    }

    /** Stores a file as a new entry. The file name is also the title shown in the list. */
    public long addFile(final String fileName, final byte[] data) {
        if (fileName == null || data == null || data.length > FileKinds.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("file name or size not acceptable");
        }
        ensureRoom(fileBytes(fileName, data));
        final long id = position++;
        final Notes item = new Notes(fileName, "");
        item.setKind(Notes.KIND_FILE);
        item.setFileName(fileName);
        item.setData(data.clone());
        notes.put(id, item);
        return id;
    }

    /** Replaces the name and content of a stored file. */
    public void replaceFile(final long id, final String fileName, final byte[] data) {
        final Notes item = requireFile(id);
        if (data.length > FileKinds.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("file too large");
        }
        ensureRoom(fileBytes(fileName, data) - itemBytes(item));
        item.setTitle(fileName);
        item.setFileName(fileName);
        item.setData(data.clone());
    }

    /** Marks an entry read-only. Read-only entries cannot be changed until the mark is removed. */
    public void setReadOnly(final long id, final boolean readOnly) {
        final Notes item = notes.get(id);
        if (item == null) {
            throw new IllegalArgumentException("no entry with id " + id);
        }
        item.setReadOnly(readOnly);
    }

    public void setStarred(final long id, final boolean starred) {
        final Notes item = notes.get(id);
        if (item != null) {
            item.setStarred(starred);
        }
    }

    public void delete(final long id) {
        final Notes item = notes.remove(id);
        if (item != null && !item.isFile()) {
            indexRemove(id, item.getTags());
        }
    }

    public Notes get(final long id) {
        return notes.get(id);
    }

    public boolean contains(final long id) {
        return notes.containsKey(id);
    }

    public int size() {
        return notes.size();
    }

    /** Approximate size of the saved payload, in bytes. */
    public long estimatedBytes() {
        long total = 0;
        for (Notes item : notes.values()) {
            total += itemBytes(item);
        }
        return total;
    }

    /** All entry ids, in creation order. */
    public Set<Long> allIds() {
        return new TreeSet<>(notes.keySet());
    }

    /** Ids of notes carrying the tag. Never null; an unknown tag gives an empty set. */
    public Set<Long> findTag(final String tag) {
        if (tag == null) {
            return Collections.emptySet();
        }
        final TreeSet<Long> ids = mapTag.get(tag.toLowerCase(Locale.ROOT));
        return ids == null ? new TreeSet<Long>() : new TreeSet<>(ids);
    }

    /** Lower-cased tag names mapped to the number of notes using them. */
    public Map<String, Integer> tagCounts() {
        final Map<String, Integer> counts = new TreeMap<>();
        for (Map.Entry<String, TreeSet<Long>> entry : mapTag.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    /** Splits a user-typed tag list. Both ',' and the Chinese comma separate tags. */
    public static String cleanTags(final String raw) {
        final StringBuilder joined = new StringBuilder();
        final Map<String, String> seen = new LinkedHashMap<>();
        for (String part : splitTags(raw)) {
            final String key = part.toLowerCase(Locale.ROOT);
            if (!seen.containsKey(key)) {
                seen.put(key, part);
            }
        }
        for (String value : seen.values()) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(value);
        }
        return joined.toString();
    }

    static String[] splitTags(final String raw) {
        if (raw == null) {
            return new String[0];
        }
        final String normalised = raw.replace('\uFF0C', ',').replace('\u3001', ',');
        final String[] parts = normalised.split(",");
        final String[] out = new String[parts.length];
        int n = 0;
        for (String part : parts) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out[n++] = trimmed;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** Rebuilds the tag index from the notes themselves. Used after loading. */
    void rebuildIndex() {
        mapTag.clear();
        for (Map.Entry<Long, Notes> entry : notes.entrySet()) {
            if (entry.getValue().isFile()) {
                continue;
            }
            entry.getValue().setTags(cleanTags(entry.getValue().getTags()));
            indexAdd(entry.getKey(), entry.getValue().getTags());
        }
    }

    private void indexAdd(final long id, final String tags) {
        for (String part : splitTags(tags)) {
            final String key = part.toLowerCase(Locale.ROOT);
            TreeSet<Long> ids = mapTag.get(key);
            if (ids == null) {
                ids = new TreeSet<>();
                mapTag.put(key, ids);
            }
            ids.add(id);
        }
    }

    private void indexRemove(final long id, final String tags) {
        for (String part : splitTags(tags)) {
            final String key = part.toLowerCase(Locale.ROOT);
            final TreeSet<Long> ids = mapTag.get(key);
            if (ids != null) {
                ids.remove(id);
                if (ids.isEmpty()) {
                    mapTag.remove(key);
                }
            }
        }
    }

    /** Makes sure the next id is larger than every existing id. */
    void fixPosition() {
        long max = -1;
        for (Long id : notes.keySet()) {
            if (id > max) {
                max = id;
            }
        }
        if (position <= max) {
            position = max + 1;
        }
    }

    long getPosition() {
        return position;
    }

    void setPosition(final long position) {
        this.position = position;
    }

    Map<Long, Notes> rawNotes() {
        return notes;
    }

    private void ensureRoom(final long delta) {
        if (delta > 0 && estimatedBytes() + delta > CONTENT_BUDGET) {
            throw new ContentLimitException();
        }
    }

    private Notes requireNote(final long id) {
        final Notes item = notes.get(id);
        if (item == null) {
            throw new IllegalArgumentException("no entry with id " + id);
        }
        if (item.isFile()) {
            throw new IllegalStateException("entry " + id + " is a file, not a note");
        }
        if (item.isReadOnly()) {
            throw new IllegalStateException("entry " + id + " is read-only");
        }
        return item;
    }

    private Notes requireFile(final long id) {
        final Notes item = notes.get(id);
        if (item == null || !item.isFile()) {
            throw new IllegalArgumentException("no file with id " + id);
        }
        if (item.isReadOnly()) {
            throw new IllegalStateException("entry " + id + " is read-only");
        }
        return item;
    }

    private static long textBytes(final String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static long noteBytes(final String title, final String note, final String tags) {
        return textBytes(title) + textBytes(note) + textBytes(tags) + RECORD_OVERHEAD;
    }

    private static long fileBytes(final String fileName, final byte[] data) {
        return textBytes(fileName) + data.length + RECORD_OVERHEAD;
    }

    private static long itemBytes(final Notes item) {
        if (item.isFile()) {
            return fileBytes(item.getFileName(), item.getData() == null ? new byte[0] : item.getData());
        }
        return noteBytes(item.getTitle(), item.getNote(), item.getTags());
    }
}
