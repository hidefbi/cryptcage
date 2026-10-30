package com.cryptcage.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A small JSON reader for the manifest written by {@link ZipExport}. Objects, arrays, strings, numbers, booleans and null. */
final class JsonLite {

    private final String text;
    private int pos;

    private JsonLite(final String text) {
        this.text = text;
    }

    static Object parse(final String text) {
        final JsonLite reader = new JsonLite(text);
        final Object value = reader.value();
        reader.skipSpace();
        if (reader.pos != text.length()) {
            throw new IllegalArgumentException("unexpected data after the value");
        }
        return value;
    }

    private Object value() {
        skipSpace();
        if (pos >= text.length()) {
            throw new IllegalArgumentException("unexpected end of input");
        }
        final char c = text.charAt(pos);
        if (c == '{') {
            return object();
        }
        if (c == '[') {
            return array();
        }
        if (c == '"') {
            return string();
        }
        if (text.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        return number();
    }

    private Map<String, Object> object() {
        pos++;
        final Map<String, Object> map = new LinkedHashMap<>();
        skipSpace();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            final String key = string();
            skipSpace();
            expect(':');
            map.put(key, value());
            skipSpace();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> array() {
        pos++;
        final List<Object> list = new ArrayList<>();
        skipSpace();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skipSpace();
            if (peek(',')) {
                pos++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String string() {
        expect('"');
        final StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("unterminated string");
            }
            final char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw new IllegalArgumentException("bad escape");
            }
            final char e = text.charAt(pos++);
            switch (e) {
                case '"':
                case '\\':
                case '/':
                    out.append(e);
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    if (pos + 4 > text.length()) {
                        throw new IllegalArgumentException("bad unicode escape");
                    }
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default:
                    throw new IllegalArgumentException("bad escape");
            }
        }
    }

    private Object number() {
        final int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        final String digits = text.substring(start, pos);
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("unexpected character at " + start);
        }
        try {
            return Long.valueOf(digits);
        } catch (NumberFormatException e) {
            return Double.valueOf(digits);
        }
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private boolean peek(final char c) {
        return pos < text.length() && text.charAt(pos) == c;
    }

    private void expect(final char c) {
        if (!peek(c)) {
            throw new IllegalArgumentException("expected '" + c + "' at " + pos);
        }
        pos++;
    }
}
