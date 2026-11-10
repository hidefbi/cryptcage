/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The file types that the list and the reader treat specially: OpenVPN profiles,
 * generic .conf files, PDF documents and plain text. Other types are described by {@link FileTypes}.
 */
public final class FileKinds {

    /** Largest single file that can be stored. */
    public static final long MAX_FILE_BYTES = 100L * 1024 * 1024;

    public enum Type {
        OVPN,
        CONF,
        PDF,
        TXT
    }

    private FileKinds() {
    }

    /** The type for a file name, judged by its extension. Null if the name is not one of these types. */
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
}
