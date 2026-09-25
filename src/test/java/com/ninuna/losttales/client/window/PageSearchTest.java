package com.ninuna.losttales.client.window;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What a page's search narrows its list to, asked here of a quest.
 * Every word has to appear somewhere in what the entry says, in any
 * order, whatever the case — the chat's rule, asked of an entry.
 */
public final class PageSearchTest {

    private static final String[] NIA = {
            "A Conversation with Nia", "Tutorials", "",
            "Gather 4 sticks", "Give Nia the 4 sticks"};

    @Test
    public void nothingTypedNarrowsNothing() {
        assertTrue(PageSearch.of("").isEmpty());
        assertTrue(PageSearch.of("   ").isEmpty());
        assertTrue(PageSearch.of(null).isEmpty());
        assertSame("one search stands for all of them",
                PageSearch.NONE, PageSearch.of("  "));
        assertTrue(PageSearch.of("").matches(NIA));
        assertTrue("and for a quest that says nothing at all",
                PageSearch.of("").matches((String[])null));
    }

    @Test
    public void everyWordHasToAppearSomewhereInWhatTheQuestSays() {
        assertTrue(PageSearch.of("nia").matches(NIA));
        assertTrue("the category counts",
                PageSearch.of("tutorials").matches(NIA));
        assertTrue("so does an objective",
                PageSearch.of("sticks").matches(NIA));
        assertTrue("and the words may come in any order",
                PageSearch.of("sticks nia").matches(NIA));
        assertFalse("one word missing is no match",
                PageSearch.of("nia beech").matches(NIA));
        assertFalse(PageSearch.of("wolves").matches(NIA));
    }

    @Test
    public void caseAndSpacingDoNotMatter() {
        assertTrue(PageSearch.of("NIA").matches(NIA));
        assertTrue(PageSearch.of("  Gather   STICKS ").matches(NIA));
        assertEquals(Arrays.asList("gather", "sticks"),
                PageSearch.of("  Gather   STICKS ").words());
    }

    @Test
    public void aWordNeverMatchesAcrossTwoThingsTheQuestSays() {
        // "Tutorials" ends one part and "Gather" begins another; the
        // join must not read as one word.
        assertFalse(PageSearch.of("tutorialsgather").matches(NIA));
        assertTrue("but each on its own is there",
                PageSearch.of("tutorials gather").matches(NIA));
    }

    @Test
    public void anEmptyOrMissingPartIsSkippedRatherThanMatched() {
        assertTrue(PageSearch.of("nia").matches(
                null, "A Conversation with Nia", "", null));
        assertFalse(PageSearch.of("nia").matches("", null));
    }
}
