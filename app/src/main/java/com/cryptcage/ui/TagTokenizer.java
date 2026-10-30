package com.cryptcage.ui;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.MultiAutoCompleteTextView;

/**
 * Splits tags on the ASCII comma, the full-width comma and the enumeration comma,
 * so Chinese input works the same as English input.
 */
final class TagTokenizer implements MultiAutoCompleteTextView.Tokenizer {

    private static boolean isSeparator(final char c) {
        return c == ',' || c == '\uFF0C' || c == '\u3001';
    }

    @Override
    public int findTokenStart(final CharSequence text, final int cursor) {
        int i = cursor;
        while (i > 0 && !isSeparator(text.charAt(i - 1))) {
            i--;
        }
        while (i < cursor && text.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    @Override
    public int findTokenEnd(final CharSequence text, final int cursor) {
        int i = cursor;
        final int len = text.length();
        while (i < len) {
            if (isSeparator(text.charAt(i))) {
                return i;
            }
            i++;
        }
        return len;
    }

    @Override
    public CharSequence terminateToken(final CharSequence text) {
        int i = text.length();
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        if (i > 0 && isSeparator(text.charAt(i - 1))) {
            return text;
        }
        if (text instanceof Spanned) {
            final SpannableString sp = new SpannableString(text + ", ");
            TextUtils.copySpansFrom((Spanned) text, 0, text.length(), Object.class, sp, 0);
            return sp;
        }
        return text + ", ";
    }
}
