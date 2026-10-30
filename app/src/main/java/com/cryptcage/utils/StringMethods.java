package com.cryptcage.utils;

import java.util.Locale;

/**
 * Small helpers for vault names and extensions.
 */
public final class StringMethods {

    public static final String EXTENSION = ".ncf";
    private static final int MAX_NAME_LENGTH = 64;

    private StringMethods() {
    }

    /** Name of the vault without directories and without the extension. */
    public static String getNameDB(final String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length());
        }
        return name;
    }

    public static boolean containsIgnoreCase(final String str, final String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.toLowerCase(Locale.ROOT).contains(searchStr.toLowerCase(Locale.ROOT));
    }

    /**
     * Turns what the user typed into a safe file name, or returns null if nothing usable is left.
     * Only letters, digits, spaces, dashes and underscores survive, so no path can be built from it.
     */
    public static String sanitizeName(final String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length());
        }
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ') {
                out.append(c);
            }
        }
        String cleaned = out.toString().trim();
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH).trim();
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned;
    }
}
