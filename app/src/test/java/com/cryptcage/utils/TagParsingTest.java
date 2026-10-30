package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TagParsingTest {

    private static final char[] PASSWORD = "correct horse 42".toCharArray();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void chineseCommaSeparatesTags() {
        assertEquals("工作, 生活", TagText.cleanTags("工作，生活"));
    }

    @Test
    public void duplicatesAndBlanksAreDropped() {
        assertEquals("Work, home", TagText.cleanTags(" Work ,, work, home ,"));
    }

    @Test
    public void filterIsCaseInsensitive() throws Exception {
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "a.cc3"), PASSWORD)) {
            vault.addNote("a", "b", "Travel, 旅行");
            assertEquals(1, vault.withTag("TRAVEL").size());
            assertEquals(1, vault.withTag("旅行").size());
        }
    }

    @Test
    public void unknownTagGivesEmptyResultInsteadOfCrashing() throws Exception {
        try (VaultSession vault = VaultSession.create(new File(folder.getRoot(), "b.cc3"), PASSWORD)) {
            vault.addNote("a", "b", "x");
            assertTrue(vault.withTag("never-used").isEmpty());
            assertTrue(vault.withTag(null).isEmpty());
        }
    }
}
