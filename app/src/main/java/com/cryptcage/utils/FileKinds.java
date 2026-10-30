package com.cryptcage.utils;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The file types CryptCage accepts: OpenVPN profiles, generic .conf files,
 * plain text, and PDF documents.
 */
public final class FileKinds {

    /** Largest single file that can be stored. */
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    public enum Type {
        OVPN,
        CONF,
        PDF,
        TXT
    }

    private static final byte[] PDF_HEADER = {'%', 'P', 'D', 'F', '-'};

    private FileKinds() {
    }

    /** The type for a file name, judged by its extension. Null if the type is not supported. */
    public static Type typeOf(final String fileName) {
        if (fileName == null) {
            return null;
        }
        final int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        switch (fileName.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "ovpn":
                return Type.OVPN;
            case "conf":
                return Type.CONF;
            case "pdf":
                return Type.PDF;
            case "txt":
                return Type.TXT;
            default:
                return null;
        }
    }

    public static String extensionOf(final Type type) {
        switch (type) {
            case OVPN:
                return ".ovpn";
            case CONF:
                return ".conf";
            case PDF:
                return ".pdf";
            default:
                return ".txt";
        }
    }

    /** True for the types that can be edited as text. */
    public static boolean isEditableText(final Type type) {
        return type == Type.OVPN || type == Type.CONF || type == Type.TXT;
    }

    /** Checks that the bytes really are of the claimed type, so a renamed file is refused. */
    public static boolean validContent(final Type type, final byte[] data) {
        if (type == null || data == null || data.length == 0 || data.length > MAX_FILE_BYTES) {
            return false;
        }
        if (type == Type.PDF) {
            return startsWith(data, PDF_HEADER);
        }
        return isUtf8Text(data);
    }

    /**
     * Strict UTF-8 check. Text containing a NUL byte is treated as binary,
     * because such a file cannot be shown or edited as text.
     */
    public static boolean isUtf8Text(final byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /**
     * Turns a file name from the outside world into a safe name ending in the type's extension.
     * Directories are dropped, and so is any existing extension. Returns null if nothing usable is left.
     */
    public static String cleanFileName(final String raw, final Type type) {
        if (raw == null || type == null) {
            return null;
        }
        String name = raw;
        final int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        final int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        final String base = StringMethods.sanitizeName(name);
        return base == null ? null : base + extensionOf(type);
    }

    private static boolean startsWith(final byte[] data, final byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
