package com.ninuna.losttales.client.chat;

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
public final class ChatPageSearchTest {

    private static final String[] NIA = {
            "A Conversation with Nia", "Tutorials", "",
            "Gather 4 sticks", "Give Nia the 4 sticks"};

    @Test
    public void nothingTypedNarrowsNothing() {
        assertTrue(ChatPageSearch.of("").isEmpty());
        assertTrue(ChatPageSearch.of("   ").isEmpty());
        assertTrue(ChatPageSearch.of(null).isEmpty());
        assertSame("one search stands for all of them",
                ChatPageSearch.NONE, ChatPageSearch.of("  "));
        assertTrue(ChatPageSearch.of("").matches(NIA));
        assertTrue("and for a quest that says nothing at all",
                ChatPageSearch.of("").matches((String[])null));
    }

    @Test
    public void everyWordHasToAppearSomewhereInWhatTheQuestSays() {
        assertTrue(ChatPageSearch.of("nia").matches(NIA));
        assertTrue("the category counts",
                ChatPageSearch.of("tutorials").matches(NIA));
        assertTrue("so does an objective",
                ChatPageSearch.of("sticks").matches(NIA));
        assertTrue("and the words may come in any order",
                ChatPageSearch.of("sticks nia").matches(NIA));
        assertFalse("one word missing is no match",
                ChatPageSearch.of("nia beech").matches(NIA));
        assertFalse(ChatPageSearch.of("wolves").matches(NIA));
    }

    @Test
    public void caseAndSpacingDoNotMatter() {
        assertTrue(ChatPageSearch.of("NIA").matches(NIA));
        assertTrue(ChatPageSearch.of("  Gather   STICKS ").matches(NIA));
        assertEquals(Arrays.asList("gather", "sticks"),
                ChatPageSearch.of("  Gather   STICKS ").words());
    }

    @Test
    public void aWordNeverMatchesAcrossTwoThingsTheQuestSays() {
        // "Tutorials" ends one part and "Gather" begins another; the
        // join must not read as one word.
        assertFalse(ChatPageSearch.of("tutorialsgather").matches(NIA));
        assertTrue("but each on its own is there",
                ChatPageSearch.of("tutorials gather").matches(NIA));
    }

    @Test
    public void anEmptyOrMissingPartIsSkippedRatherThanMatched() {
        assertTrue(ChatPageSearch.of("nia").matches(
                null, "A Conversation with Nia", "", null));
        assertFalse(ChatPageSearch.of("nia").matches("", null));
    }
}
