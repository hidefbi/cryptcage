/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextHistoryTest {

    @Test
    public void quickTypingIsOneStep() {
        final TextHistory history = new TextHistory("");
        history.edited("a", 1, 1, 0);
        history.edited("ab", 2, 2, 100);
        history.edited("abc", 3, 3, 200);
        assertEquals("", history.undo().text);
        assertFalse(history.canUndo());
    }

    @Test
    public void pauseStartsANewStep() {
        final TextHistory history = new TextHistory("");
        history.edited("a", 1, 1, 0);
        history.edited("ab", 2, 2, 5000);
        assertEquals("a", history.undo().text);
        assertEquals("", history.undo().text);
    }

    @Test
    public void pasteIsItsOwnStep() {
        final TextHistory history = new TextHistory("x");
        history.edited("xpasted", 7, 7, 0);
        history.edited("xpasted!", 8, 8, 100);
        assertEquals("xpasted", history.undo().text);
        assertEquals("x", history.undo().text);
    }

    @Test
    public void undoThenRedoRestoresText() {
        final TextHistory history = new TextHistory("");
        history.edited("one", 3, 3, 0);
        history.edited("one two", 7, 7, 10000);
        assertEquals("one", history.undo().text);
        assertTrue(history.canRedo());
        assertEquals("one two", history.redo().text);
        assertFalse(history.canRedo());
    }

    @Test
    public void newEditClearsRedo() {
        final TextHistory history = new TextHistory("");
        history.edited("a", 1, 1, 0);
        history.edited("b", 1, 1, 10000);
        history.undo();
        history.edited("c", 1, 1, 20000);
        assertFalse(history.canRedo());
    }

    @Test
    public void selectionIsKept() {
        final TextHistory history = new TextHistory("");
        history.edited("hello", 2, 4, 0);
        history.edited("hello world", 11, 11, 10000);
        final TextHistory.State state = history.undo();
        assertEquals("hello", state.text);
        assertEquals(2, state.selectionStart);
        assertEquals(4, state.selectionEnd);
    }

    @Test
    public void emptyHistoryReturnsNull() {
        final TextHistory history = new TextHistory("abc");
        assertNull(history.undo());
        assertNull(history.redo());
    }

    @Test
    public void resetForgetsEarlierSteps() {
        final TextHistory history = new TextHistory("");
        history.edited("a", 1, 1, 0);
        history.reset("loaded", 6, 6);
        assertFalse(history.canUndo());
        history.edited("loaded!", 7, 7, 10000);
        assertEquals("loaded", history.undo().text);
    }
}
