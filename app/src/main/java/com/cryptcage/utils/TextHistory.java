/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Undo and redo for one text field. A run of single characters typed in quick succession counts
 * as one step, so an undo does not remove one character at a time. A step also ends after a
 * pause, and any change that is not a single character starts a new step.
 */
public final class TextHistory {

    /** The text of a field and where its cursor was. */
    public static final class State {
        public final String text;
        public final int selectionStart;
        public final int selectionEnd;

        State(final String text, final int selectionStart, final int selectionEnd) {
            this.text = text;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }
    }

    /** Typing pauses longer than this start a new step. */
    static final long GROUP_MILLIS = 1500;
    /** Steps kept for undo. The oldest are dropped first. */
    static final int MAX_STEPS = 200;

    private final List<State> undoSteps = new ArrayList<>();
    private final List<State> redoSteps = new ArrayList<>();
    private State current;
    private long lastEdit;
    private boolean grouping;

    public TextHistory(final String text) {
        this.current = new State(text, text.length(), text.length());
    }

    /** Clears both stacks and starts again from this text, for example after a note is loaded. */
    public void reset(final String text, final int selectionStart, final int selectionEnd) {
        undoSteps.clear();
        redoSteps.clear();
        current = new State(text, selectionStart, selectionEnd);
        grouping = false;
    }

    /** Records the field after a change made by the user. */
    public void edited(final String text, final int selectionStart, final int selectionEnd, final long now) {
        if (text.equals(current.text)) {
            current = new State(text, selectionStart, selectionEnd);
            return;
        }
        final boolean single = Math.abs(text.length() - current.text.length()) == 1;
        final boolean joins = grouping && single && now - lastEdit < GROUP_MILLIS;
        if (!joins) {
            undoSteps.add(current);
            if (undoSteps.size() > MAX_STEPS) {
                undoSteps.remove(0);
            }
        }
        grouping = single;
        redoSteps.clear();
        current = new State(text, selectionStart, selectionEnd);
        lastEdit = now;
    }

    public boolean canUndo() {
        return !undoSteps.isEmpty();
    }

    public boolean canRedo() {
        return !redoSteps.isEmpty();
    }

    /** Steps back. Returns the state to show, or null when there is nothing to undo. */
    public State undo() {
        if (undoSteps.isEmpty()) {
            return null;
        }
        redoSteps.add(current);
        current = undoSteps.remove(undoSteps.size() - 1);
        grouping = false;
        return current;
    }

    /** Steps forward again. Returns the state to show, or null when there is nothing to redo. */
    public State redo() {
        if (redoSteps.isEmpty()) {
            return null;
        }
        undoSteps.add(current);
        current = redoSteps.remove(redoSteps.size() - 1);
        grouping = false;
        return current;
    }
}
