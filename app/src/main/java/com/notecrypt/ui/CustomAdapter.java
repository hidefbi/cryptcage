package com.notecrypt.ui;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.notecrypt.utils.FileKinds;
import com.notecryptpro.R;

/**
 * Row adapter for the note list. The image slot shows the star for a note and the type icon
 * for a stored file. A selected row is drawn with the selection colour of the current theme.
 */
class CustomAdapter extends SimpleAdapter {

    static final String FILE_PREFIX = "file:";
    static final String KEY_SELECTED = "sel";

    private final Context context;

    CustomAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
        super(context, data, resource, from, to);
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View row = super.getView(position, convertView, parent);
        final Object item = getItem(position);
        final boolean selected = item instanceof Map && "true".equals(((Map<?, ?>) item).get(KEY_SELECTED));
        final int colour = ContextCompat.getColor(context,
                selected ? R.color.tech_selected : R.color.tech_surface);
        if (row instanceof MaterialCardView) {
            ((MaterialCardView) row).setCardBackgroundColor(colour);
        } else {
            row.setBackgroundColor(colour);
        }
        return row;
    }

    @Override
    public void setViewImage(ImageView v, String value) {
        if (value.startsWith(FILE_PREFIX)) {
            v.setVisibility(View.VISIBLE);
            v.setImageResource(iconFor(value.substring(FILE_PREFIX.length())));
        } else if (value.equals("false")) {
            v.setVisibility(View.GONE);
        } else {
            v.setVisibility(View.VISIBLE);
            v.setImageResource(R.drawable.ic_action_important_blue);
        }
    }

    static int iconFor(final String typeName) {
        final FileKinds.Type type = FileKinds.Type.valueOf(typeName.toUpperCase(Locale.ROOT));
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
}
