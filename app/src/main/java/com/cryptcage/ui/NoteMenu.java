/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.cryptcage.R;

/** The menu that opens on a long press of a note row. */
final class NoteMenu {

    /** What a menu row does. The menu closes before the action runs. */
    interface Actions {
        void select(long id);

        void export(long id);

        void copy(long id);

        void toggleReadOnly(long id);

        void delete(long id);
    }

    private static final int MARGIN_DP = 8;

    private NoteMenu() {
    }

    /**
     * Shows the menu near the touch point. The coordinates are relative to the parent view.
     * A read-only note shows the writable row and no copy or delete row.
     */
    static PopupWindow show(final Context context, final View parent, final long id, final boolean readOnly,
                            final float x, final float y, final Actions actions, final Runnable onClosed) {
        final View content = LayoutInflater.from(context).inflate(R.layout.popup_note_menu, null);
        final PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setOnDismissListener(() -> onClosed.run());

        content.findViewById(R.id.note_menu_select).setOnClickListener(v -> {
            popup.dismiss();
            actions.select(id);
        });
        content.findViewById(R.id.note_menu_export).setOnClickListener(v -> {
            popup.dismiss();
            actions.export(id);
        });
        final View copy = content.findViewById(R.id.note_menu_copy);
        copy.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        copy.setOnClickListener(v -> {
            popup.dismiss();
            actions.copy(id);
        });
        content.findViewById(R.id.note_menu_readonly).setOnClickListener(v -> {
            popup.dismiss();
            actions.toggleReadOnly(id);
        });

        final TextView readOnlyIcon = content.findViewById(R.id.note_menu_readonly_icon);
        final TextView readOnlyLabel = content.findViewById(R.id.note_menu_readonly_label);
        readOnlyIcon.setText(readOnly ? "\u2B55" : "\u26D4");
        readOnlyLabel.setText(readOnly ? R.string.action_writable : R.string.action_readOnly);

        final int deleteVisibility = readOnly ? View.GONE : View.VISIBLE;
        content.findViewById(R.id.note_menu_divider_delete).setVisibility(deleteVisibility);
        final View delete = content.findViewById(R.id.note_menu_delete);
        delete.setVisibility(deleteVisibility);
        delete.setOnClickListener(v -> {
            popup.dismiss();
            actions.delete(id);
        });

        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        final int width = content.getMeasuredWidth();
        final int height = content.getMeasuredHeight();
        final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        final int margin = Math.round(MARGIN_DP * metrics.density);

        final int[] origin = new int[2];
        parent.getLocationOnScreen(origin);
        int left = origin[0] + Math.round(x) - width / 2;
        int top = origin[1] + Math.round(y);
        left = Math.max(margin, Math.min(left, metrics.widthPixels - width - margin));
        if (top + height > metrics.heightPixels - margin) {
            top = metrics.heightPixels - height - margin;
        }
        top = Math.max(margin, top);

        popup.showAtLocation(parent, Gravity.NO_GRAVITY, left, top);
        return popup;
    }
}
