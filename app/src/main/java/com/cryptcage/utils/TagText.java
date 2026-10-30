/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Tag text as the user types it and as it is stored: comma-separated, without duplicates. */
final class TagText {

    private TagText() {
    }

    /** Splits a user-typed tag list. Both ',' and the Chinese comma separate tags. */
    static String cleanTags(final String raw) {
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
        final String normalised = raw.replace('，', ',').replace('、', ',');
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
}
