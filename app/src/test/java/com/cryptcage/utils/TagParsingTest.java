package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TagParsingTest {

    @Test
    public void chineseCommaSeparatesTags() {
        assertEquals("工作, 生活", DatabaseForNotes.cleanTags("工作，生活"));
    }

    @Test
    public void duplicatesAndBlanksAreDropped() {
        assertEquals("Work, home", DatabaseForNotes.cleanTags(" Work ,, work, home ,"));
    }

    @Test
    public void filterIsCaseInsensitive() {
        final DatabaseForNotes db = new DatabaseForNotes();
        db.add("a", "b", "Travel, 旅行");
        assertEquals(1, db.findTag("TRAVEL").size());
        assertEquals(1, db.findTag("旅行").size());
    }

    @Test
    public void unknownTagGivesEmptyResultInsteadOfCrashing() {
        final DatabaseForNotes db = new DatabaseForNotes();
        db.add("a", "b", "x");
        assertEquals(0, db.findTag("never-used").size());
        assertEquals(0, db.findTag(null).size());
    }
}
