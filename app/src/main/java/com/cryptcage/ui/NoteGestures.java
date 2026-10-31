/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.util.List;

import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Long press and drag for the note list.
 *
 * A press that stays in place for the long-press time opens the menu for its row. Moving the
 * finger after that closes the menu and drags the row to a new place. Other rows move out of the
 * way while the finger moves, and the new order is saved when the finger lifts. Short presses,
 * and presses that move before the menu opens, are left to the rows and to the list's scrolling.
 */
final class NoteGestures implements RecyclerView.OnItemTouchListener {

    /** What the list asks of the screen. */
    interface Host {
        /** True when a long press may open the menu. */
        boolean menuAllowed();

        /** True when the row may be dragged right now. */
        boolean dragAllowed();

        /** Shows the menu for a row. The coordinates are relative to the list. */
        void showMenu(long id, View row, float x, float y);

        /** Closes the menu if it is open. */
        void dismissMenu();

        /** Called after a drag ends with a new order. The ids are in the shown order. */
        void orderChanged(List<Long> ids);
    }

    private enum Phase { IDLE, PRESSED, MENU, DRAG, IGNORE }

    private static final int EDGE_DP = 56;
    private static final int SCROLL_DP = 12;

    private final NotesAdapter adapter;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int slop;
    private final int longPressTimeout;

    private RecyclerView list;
    private Phase phase = Phase.IDLE;
    private View row;
    private float downX;
    private float downY;
    private int dragPosition = RecyclerView.NO_POSITION;
    private boolean moved;

    private final Runnable openMenu = this::openMenuNow;

    NoteGestures(final RecyclerView list, final NotesAdapter adapter, final Host host) {
        this.list = list;
        this.adapter = adapter;
        this.host = host;
        this.slop = ViewConfiguration.get(list.getContext()).getScaledTouchSlop();
        this.longPressTimeout = ViewConfiguration.getLongPressTimeout();
    }

    /** Runs when the press has lasted the long-press time without moving far. */
    private void openMenuNow() {
        if (phase != Phase.PRESSED || row == null || list == null) {
            return;
        }
        if (!host.menuAllowed()) {
            return;
        }
        phase = Phase.MENU;
        row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        host.showMenu(adapter.idAt(list.getChildAdapterPosition(row)), row, downX, downY);
    }

    @Override
    public boolean onInterceptTouchEvent(final RecyclerView rv, final MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                reset();
                row = rv.findChildViewUnder(e.getX(), e.getY());
                if (row != null) {
                    phase = Phase.PRESSED;
                    downX = e.getX();
                    downY = e.getY();
                    handler.postDelayed(openMenu, longPressTimeout);
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (phase == Phase.PRESSED && distance(e) > slop) {
                    // Not a long press: the row is scrolled or tapped as usual.
                    cancelTimer();
                    phase = Phase.IDLE;
                }
                return phase == Phase.MENU || phase == Phase.DRAG || phase == Phase.IGNORE;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelTimer();
                return phase == Phase.MENU || phase == Phase.DRAG || phase == Phase.IGNORE;
            default:
                return phase == Phase.MENU || phase == Phase.DRAG || phase == Phase.IGNORE;
        }
    }

    @Override
    public void onTouchEvent(final RecyclerView rv, final MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (phase == Phase.MENU && distance(e) > slop) {
                    host.dismissMenu();
                    if (host.dragAllowed() && row != null) {
                        phase = Phase.DRAG;
                        dragPosition = rv.getChildAdapterPosition(row);
                        moved = false;
                    } else {
                        phase = Phase.IGNORE;
                    }
                }
                if (phase == Phase.DRAG) {
                    dragTo(rv, e);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (phase == Phase.DRAG && moved) {
                    host.orderChanged(adapter.idsInOrder());
                }
                reset();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(final boolean disallowIntercept) {
        // Nothing to do: the list is not nested in another scrolling view.
    }

    /** Moves the dragged row under the finger and scrolls the list when the finger nears an edge. */
    private void dragTo(final RecyclerView rv, final MotionEvent e) {
        final View under = rv.findChildViewUnder(e.getX(), e.getY());
        if (under != null) {
            final int target = rv.getChildAdapterPosition(under);
            if (target != RecyclerView.NO_POSITION && dragPosition != RecyclerView.NO_POSITION
                    && target != dragPosition) {
                adapter.move(dragPosition, target);
                dragPosition = target;
                moved = true;
            }
        }
        final float density = rv.getResources().getDisplayMetrics().density;
        final int edge = Math.round(EDGE_DP * density);
        final int step = Math.round(SCROLL_DP * density);
        if (e.getY() < edge) {
            rv.scrollBy(0, -step);
        } else if (e.getY() > rv.getHeight() - edge) {
            rv.scrollBy(0, step);
        }
    }

    private float distance(final MotionEvent e) {
        final float dx = e.getX() - downX;
        final float dy = e.getY() - downY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void cancelTimer() {
        handler.removeCallbacks(openMenu);
    }

    private void reset() {
        cancelTimer();
        phase = Phase.IDLE;
        row = null;
        dragPosition = RecyclerView.NO_POSITION;
        moved = false;
    }

    /** Stops a press that is in progress, for example when the screen goes to the background. */
    void cancel() {
        reset();
    }
}
