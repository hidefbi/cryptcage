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
 * A press that stays in place for the long-press time opens the menu for its row, and the row is
 * shown darker. Moving the finger after that closes the menu and drags the row. The dragged card
 * follows the finger, and the other rows step aside once the finger passes their middle. The new
 * order is saved when the finger lifts. Short presses, and presses that move before the menu
 * opens, are left to the rows and to the list's scrolling.
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

        /** Closes the menu without running the listener that clears the highlight. */
        void dismissMenu();

        /** Called after a drag that moved rows. The ids are in the shown order. */
        void orderChanged(List<Long> ids);
    }

    private enum Phase { IDLE, PRESSED, MENU, DRAG, IGNORE }

    private static final int EDGE_DP = 56;
    private static final int SCROLL_DP = 12;
    private static final int LIFT_DP = 6;

    private final RecyclerView list;
    private final NotesAdapter adapter;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int slop;
    private final int longPressTimeout;
    private final float density;
    private final Runnable openMenu = this::openMenuNow;

    private Phase phase = Phase.IDLE;
    private View row;
    private float downX;
    private float downY;
    private float grabOffset;
    private int dragPosition = RecyclerView.NO_POSITION;
    private boolean moved;
    private RecyclerView.ItemAnimator savedAnimator;

    NoteGestures(final RecyclerView list, final NotesAdapter adapter, final Host host) {
        this.list = list;
        this.adapter = adapter;
        this.host = host;
        this.slop = ViewConfiguration.get(list.getContext()).getScaledTouchSlop();
        this.longPressTimeout = ViewConfiguration.getLongPressTimeout();
        this.density = list.getResources().getDisplayMetrics().density;
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
                return isOwning();
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelTimer();
                return isOwning();
            default:
                return isOwning();
        }
    }

    @Override
    public void onTouchEvent(final RecyclerView rv, final MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (phase == Phase.MENU && distance(e) > slop) {
                    if (host.dragAllowed() && row != null) {
                        startDrag();
                    } else {
                        phase = Phase.IGNORE;
                    }
                    host.dismissMenu();
                }
                if (phase == Phase.DRAG) {
                    dragTo(e);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (phase == Phase.DRAG) {
                    endDrag();
                    if (moved) {
                        host.orderChanged(adapter.idsInOrder());
                    }
                } else if (phase == Phase.IGNORE) {
                    adapter.clearPressed();
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

    /** Called when the menu closes by itself: a tap outside it or a menu row. */
    void menuClosed() {
        if (phase == Phase.DRAG) {
            return;
        }
        if (phase == Phase.MENU) {
            phase = Phase.IDLE;
        }
        adapter.clearPressed();
    }

    /** Stops a press that is in progress, for example when the screen goes to the background. */
    void cancel() {
        if (phase == Phase.DRAG) {
            endDrag();
        }
        adapter.clearPressed();
        reset();
    }

    /** Runs when the press has lasted the long-press time without moving far. */
    private void openMenuNow() {
        if (phase != Phase.PRESSED || row == null || !host.menuAllowed()) {
            return;
        }
        final long id = adapter.idAt(list.getChildAdapterPosition(row));
        if (id < 0) {
            return;
        }
        phase = Phase.MENU;
        row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        adapter.setPressed(id);
        host.showMenu(id, row, downX, downY);
    }

    /** True while this class handles the touch, so the card does not also receive it. */
    private boolean isOwning() {
        return phase == Phase.MENU || phase == Phase.DRAG || phase == Phase.IGNORE;
    }

    private void startDrag() {
        dragPosition = list.getChildAdapterPosition(row);
        if (dragPosition == RecyclerView.NO_POSITION) {
            phase = Phase.IGNORE;
            return;
        }
        phase = Phase.DRAG;
        moved = false;
        grabOffset = downY - row.getTop();
        row.setTranslationZ(LIFT_DP * density);
        // Animated steps make the other rows arrive late. Without animation they follow the finger.
        savedAnimator = list.getItemAnimator();
        list.setItemAnimator(null);
    }

    /** Puts the dragged card under the finger and steps the other rows once the finger passes them. */
    private void dragTo(final MotionEvent e) {
        final float y = e.getY();
        row.setTranslationY(y - grabOffset - row.getTop());

        final View under = list.findChildViewUnder(e.getX(), y);
        if (under != null) {
            final int target = list.getChildAdapterPosition(under);
            if (target != RecyclerView.NO_POSITION && target != dragPosition) {
                final float middle = under.getTop() + under.getHeight() / 2f;
                final boolean passed = target > dragPosition ? y > middle : y < middle;
                if (passed) {
                    adapter.move(dragPosition, target);
                    dragPosition = target;
                    moved = true;
                }
            }
        }

        final int edge = Math.round(EDGE_DP * density);
        final int step = Math.round(SCROLL_DP * density);
        if (y < edge) {
            list.scrollBy(0, -step);
        } else if (y > list.getHeight() - edge) {
            list.scrollBy(0, step);
        }
    }

    private void endDrag() {
        if (row != null) {
            row.setTranslationY(0);
            row.setTranslationZ(0);
        }
        list.setItemAnimator(savedAnimator);
        savedAnimator = null;
        adapter.clearPressed();
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
}
