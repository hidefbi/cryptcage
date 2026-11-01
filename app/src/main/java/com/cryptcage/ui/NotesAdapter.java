/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.cryptcage.utils.FileKinds;
import com.cryptcage.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/** Cards of the note list. A stored file shows its type icon, a starred note shows a star. */
final class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.Holder> {

    interface Listener {
        void onClick(long id);
    }

    /** One row. The type is null for a note, and set for a stored file. */
    static final class Item {
        final long id;
        final String title;
        final String subtitle;
        final FileKinds.Type type;
        final boolean starred;
        final boolean readOnly;
        final boolean selected;

        Item(final long id, final String title, final String subtitle, final FileKinds.Type type,
             final boolean starred, final boolean readOnly, final boolean selected) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.starred = starred;
            this.readOnly = readOnly;
            this.selected = selected;
        }
    }

    /** Alpha of the on-surface colour laid over a row that is pressed or dragged (about 18%). */
    private static final int PRESSED_ALPHA = 0x2E;

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;
    private long pressedId = -1;

    NotesAdapter(final Listener listener) {
        this.listener = listener;
    }

    void submit(final List<Item> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    /** The file type of the shown row with this id, or null for a note. Reads no vault data. */
    FileKinds.Type typeOf(final long id) {
        for (final Item item : items) {
            if (item.id == id) {
                return item.type;
            }
        }
        return null;
    }

    /** Ids in the order they are shown. */
    List<Long> idsInOrder() {
        final List<Long> ids = new ArrayList<>(items.size());
        for (final Item item : items) {
            ids.add(item.id);
        }
        return ids;
    }

    /** The id of the row at this position, or -1 when the position is out of range. */
    long idAt(final int position) {
        if (position < 0 || position >= items.size()) {
            return -1;
        }
        return items.get(position).id;
    }

    /** Shows the row with this id as pressed, darker than the other rows. Pass -1 to clear it. */
    void setPressed(final long id) {
        if (pressedId == id) {
            return;
        }
        final long previous = pressedId;
        pressedId = id;
        refreshRow(previous);
        refreshRow(id);
    }

    void clearPressed() {
        setPressed(-1);
    }

    private void refreshRow(final long id) {
        if (id < 0) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    /** Moves a row while a drag is running. The rows change only on the screen until the drop. */
    void move(final int from, final int to) {
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, final int position) {
        final Item item = items.get(position);
        final View view = holder.itemView;

        holder.title.setText(item.title);
        holder.subtitle.setText(item.subtitle);
        holder.subtitle.setVisibility(item.subtitle == null ? View.GONE : View.VISIBLE);
        holder.type.setVisibility(View.VISIBLE);
        if (item.type == null) {
            holder.type.setImageResource(R.drawable.icon_edit);
            holder.type.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorOnPrimaryContainer)));
            holder.tile.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorPrimaryContainer)));
        } else {
            holder.type.setImageResource(iconFor(item.type));
            holder.type.setImageTintList(null);
            holder.tile.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorSecondaryContainer)));
        }
        holder.star.setVisibility(item.starred ? View.VISIBLE : View.GONE);

        final int text = item.readOnly
                ? MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)
                : MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface);
        holder.title.setTextColor(text);
        holder.subtitle.setTextColor(MaterialColors.getColor(view,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        holder.type.setAlpha(item.readOnly ? 0.6f : 1f);

        holder.card.setCardBackgroundColor(backgroundFor(view, item));
        holder.card.setStrokeColor(MaterialColors.getColor(view,
                item.selected ? androidx.appcompat.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorOutline));
        holder.card.setStrokeWidth(view.getResources().getDimensionPixelSize(
                item.selected ? R.dimen.stroke_selected : R.dimen.stroke_normal));

        holder.card.setOnClickListener(v -> listener.onClick(item.id));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * The card colour. A selected row uses the primary colour. A pressed or dragged row is the
     * surface with the on-surface colour laid over it, so it is darker on light themes and still
     * stands out on dark ones.
     */
    private int backgroundFor(final View view, final Item item) {
        if (item.selected) {
            return MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary);
        }
        final int surface = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface);
        if (item.id != pressedId) {
            return surface;
        }
        final int onSurface = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface);
        return ColorUtils.compositeColors(ColorUtils.setAlphaComponent(onSurface, PRESSED_ALPHA), surface);
    }

    private static int iconFor(final FileKinds.Type type) {
        switch (type) {
            case OVPN:
                return R.drawable.ic_type_vpn;
            case CONF:
                return R.drawable.ic_type_conf;
            case PDF:
                return R.drawable.ic_type_pdf;
            default:
                return R.drawable.ic_type_text;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final View tile;
        final ImageView type;
        final TextView title;
        final TextView subtitle;
        final ImageView star;

        Holder(final View view) {
            super(view);
            card = view.findViewById(R.id.card);
            tile = view.findViewById(R.id.tile);
            type = view.findViewById(R.id.type);
            title = view.findViewById(R.id.title);
            subtitle = view.findViewById(R.id.subtitle);
            star = view.findViewById(R.id.star);
        }
    }
}
