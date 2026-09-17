package com.ninuna.losttales.gui.screen.quest;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What the journal's search narrows the list to. Every word has to
 * appear somewhere in what a quest says, in any order, whatever the
 * case — the chat's rule, asked of a quest.
 */
public final class QuestSearchQueryTest {

    private static final String[] NIA = {
            "A Conversation with Nia", "Tutorials", "",
            "Gather 4 sticks", "Give Nia the 4 sticks"};

    @Test
    public void nothingTypedNarrowsNothing() {
        assertTrue(QuestSearchQuery.of("").isEmpty());
        assertTrue(QuestSearchQuery.of("   ").isEmpty());
        assertTrue(QuestSearchQuery.of(null).isEmpty());
        assertSame("one search stands for all of them",
                QuestSearchQuery.NONE, QuestSearchQuery.of("  "));
        assertTrue(QuestSearchQuery.of("").matches(NIA));
        assertTrue("and for a quest that says nothing at all",
                QuestSearchQuery.of("").matches((String[])null));
    }

    @Test
    public void everyWordHasToAppearSomewhereInWhatTheQuestSays() {
        assertTrue(QuestSearchQuery.of("nia").matches(NIA));
        assertTrue("the category counts",
                QuestSearchQuery.of("tutorials").matches(NIA));
        assertTrue("so does an objective",
                QuestSearchQuery.of("sticks").matches(NIA));
        assertTrue("and the words may come in any order",
                QuestSearchQuery.of("sticks nia").matches(NIA));
        assertFalse("one word missing is no match",
                QuestSearchQuery.of("nia beech").matches(NIA));
        assertFalse(QuestSearchQuery.of("wolves").matches(NIA));
    }

    @Test
    public void caseAndSpacingDoNotMatter() {
        assertTrue(QuestSearchQuery.of("NIA").matches(NIA));
        assertTrue(QuestSearchQuery.of("  Gather   STICKS ").matches(NIA));
        assertEquals(Arrays.asList("gather", "sticks"),
                QuestSearchQuery.of("  Gather   STICKS ").words());
    }

    @Test
    public void aWordNeverMatchesAcrossTwoThingsTheQuestSays() {
        // "Tutorials" ends one part and "Gather" begins another; the
        // join must not read as one word.
        assertFalse(QuestSearchQuery.of("tutorialsgather").matches(NIA));
        assertTrue("but each on its own is there",
                QuestSearchQuery.of("tutorials gather").matches(NIA));
    }

    @Test
    public void anEmptyOrMissingPartIsSkippedRatherThanMatched() {
        assertTrue(QuestSearchQuery.of("nia").matches(
                null, "A Conversation with Nia", "", null));
        assertFalse(QuestSearchQuery.of("nia").matches("", null));
    }
}
