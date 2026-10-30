package com.notecrypt.utils;

/** A note or an attached file. Stored through DatabaseCodec. */
public class Notes {

    public static final int KIND_NOTE = 0;
    public static final int KIND_FILE = 1;

    private String title;
    private String note;
    private String tags;
    private boolean starred;
    private boolean readOnly;
    private int kind;
    private String fileName;
    private byte[] data;

    public Notes(final String title, final String note) {
        this.title = title;
        this.note = note;
        this.tags = "";
    }

    public String getTitle() {
        return title;
    }

    public String getNote() {
        return note;
    }

    public String getTags() {
        return tags;
    }

    public boolean isStarred() {
        return starred;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public int getKind() {
        return kind;
    }

    public boolean isFile() {
        return kind == KIND_FILE;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getData() {
        return data;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public void setNote(final String note) {
        this.note = note;
    }

    public void setTags(final String tags) {
        this.tags = tags;
    }

    public void setStarred(final boolean starred) {
        this.starred = starred;
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
    }

    public void setKind(final int kind) {
        this.kind = kind;
    }

    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    public void setData(final byte[] data) {
        this.data = data;
    }
}
