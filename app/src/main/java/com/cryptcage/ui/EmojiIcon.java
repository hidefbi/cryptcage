/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Draws an emoji as a toolbar icon. Emoji keep their own colours, so the icon fits every theme
 * without tinting.
 */
final class EmojiIcon {

    static final String EXPORT = "\u23EC\uFE0F";
    static final String DELETE = "\uD83D\uDDD1\uFE0F";

    private static final int SIZE_DP = 24;

    private EmojiIcon() {
    }

    static Drawable of(final Context context, final String emoji) {
        final float density = context.getResources().getDisplayMetrics().density;
        final int size = Math.round(SIZE_DP * density);
        final Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size * 0.8f);
        paint.setTextAlign(Paint.Align.CENTER);
        final Paint.FontMetrics metrics = paint.getFontMetrics();
        final float baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(emoji, size / 2f, baseline, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
