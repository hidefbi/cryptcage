/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-11-01. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.cryptcage.R;

/**
 * Draws a rounded card behind the rows of each preference group, with thin dividers
 * between the rows. The category titles stay outside the cards.
 */
final class CardGroupDecoration extends RecyclerView.ItemDecoration {

    private static final Comparator<View> TOP_FIRST = (a, b) -> Integer.compare(a.getTop(), b.getTop());

    private final float radius;
    private final float stroke;
    private final float dividerInset;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final List<View> rows = new ArrayList<>();

    CardGroupDecoration(final float radius, final float stroke, final float dividerInset,
                        final int fillColor, final int borderColor) {
        this.radius = radius;
        this.stroke = stroke;
        this.dividerInset = dividerInset;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(fillColor);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(stroke);
        border.setColor(borderColor);
        divider.setStyle(Paint.Style.STROKE);
        divider.setStrokeWidth(stroke);
        divider.setColor(borderColor);
    }

    @Override
    public void onDraw(final Canvas canvas, final RecyclerView parent, final RecyclerView.State state) {
        final List<View> children = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            children.add(parent.getChildAt(i));
        }
        Collections.sort(children, TOP_FIRST);
        rows.clear();
        for (View child : children) {
            if (child.findViewById(R.id.pref_category_marker) != null) {
                drawGroup(canvas);
            } else {
                rows.add(child);
            }
        }
        drawGroup(canvas);
    }

    /** Draws the card for the rows collected so far, then clears them. */
    private void drawGroup(final Canvas canvas) {
        if (rows.isEmpty()) {
            return;
        }
        final View first = rows.get(0);
        final View last = rows.get(rows.size() - 1);
        bounds.set(first.getLeft() + stroke / 2, first.getTop() + stroke / 2,
                first.getRight() - stroke / 2, last.getBottom() - stroke / 2);
        canvas.drawRoundRect(bounds, radius, radius, fill);
        canvas.drawRoundRect(bounds, radius, radius, border);
        for (int i = 0; i < rows.size() - 1; i++) {
            final float y = rows.get(i).getBottom();
            canvas.drawLine(first.getLeft() + dividerInset, y, first.getRight() - dividerInset, y, divider);
        }
        rows.clear();
    }
}
