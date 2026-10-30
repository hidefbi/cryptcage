/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.ArrayList;
import java.util.List;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cryptcage.utils.FileKinds;
import com.cryptcage.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/** Cards of the note list. A stored file shows its type icon, a starred note shows a star. */
final class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.Holder> {

    interface Listener {
        void onClick(long id);

        void onLongClick(long id);
    }

    /** One row. The type is null for a note, and set for a stored file. */
    static final class Item {
        final long id;
        final String title;
        final String subtitle;
        final FileKinds.Type type;
        final boolean starred;
        final boolean selected;

        Item(final long id, final String title, final String subtitle, final FileKinds.Type type,
             final boolean starred, final boolean selected) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.starred = starred;
            this.selected = selected;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;

    NotesAdapter(final Listener listener) {
        this.listener = listener;
    }

    void submit(final List<Item> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
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
        holder.type.setVisibility(item.type == null ? View.GONE : View.VISIBLE);
        if (item.type != null) {
            holder.type.setImageResource(iconFor(item.type));
        }
        holder.star.setVisibility(item.starred ? View.VISIBLE : View.GONE);

        holder.card.setCardBackgroundColor(MaterialColors.getColor(view,
                item.selected ? androidx.appcompat.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorSurface));
        holder.card.setStrokeColor(MaterialColors.getColor(view,
                item.selected ? androidx.appcompat.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorOutline));
        holder.card.setStrokeWidth(view.getResources().getDimensionPixelSize(
                item.selected ? R.dimen.stroke_selected : R.dimen.stroke_normal));

        holder.card.setOnClickListener(v -> listener.onClick(item.id));
        holder.card.setOnLongClickListener(v -> {
            listener.onLongClick(item.id);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
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
        final ImageView type;
        final TextView title;
        final TextView subtitle;
        final ImageView star;

        Holder(final View view) {
            super(view);
            card = view.findViewById(R.id.card);
            type = view.findViewById(R.id.type);
            title = view.findViewById(R.id.title);
            subtitle = view.findViewById(R.id.subtitle);
            star = view.findViewById(R.id.star);
        }
    }
}
