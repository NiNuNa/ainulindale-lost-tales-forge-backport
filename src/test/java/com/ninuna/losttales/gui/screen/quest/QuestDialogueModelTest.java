package com.ninuna.losttales.gui.screen.quest;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A conversation about a quest: what is said, what may be said back, and
 * what asking for more changes. The screen draws exactly this, so
 * everything a player can reach is decided here.
 */
public final class QuestDialogueModelTest {

    private static final String[] EVERY_LABEL = {
            "I'll do it.", "Tell me more.", "Here you are.", "Not now.",
            "Farewell."};

    private static QuestDialogueModel offer(String more, String... labels) {
        return QuestDialogueModel.of("Nia", "Bree", "A Word with Nia",
                "Gather 4 sticks", QuestDialogueModel.Mood.OFFER,
                "Four sticks would do it.", more,
                labels.length == 0 ? EVERY_LABEL : labels);
    }

    @Test
    public void anOfferLetsThePlayerTakeItAskMoreOrRefuse() {
        QuestDialogueModel model = offer("Any dry stick will do.");
        assertEquals(Arrays.asList(QuestDialogueModel.Reply.ACCEPT,
                        QuestDialogueModel.Reply.MORE,
                        QuestDialogueModel.Reply.DECLINE),
                model.getReplies());
        assertEquals("Four sticks would do it.", model.getSaid());
        assertEquals("I'll do it.",
                model.labelOf(QuestDialogueModel.Reply.ACCEPT));
        assertEquals(QuestDialogueModel.Mood.OFFER, model.getMood());
    }

    @Test
    public void askingForMoreSaysTheFurtherLineAndIsNotOfferedTwice() {
        QuestDialogueModel model = offer("Any dry stick will do.");
        assertFalse(model.isToldMore());
        QuestDialogueModel told = model.told();

        assertTrue(told.isToldMore());
        assertEquals("Any dry stick will do.", told.getSaid());
        assertEquals(Arrays.asList(QuestDialogueModel.Reply.ACCEPT,
                        QuestDialogueModel.Reply.DECLINE),
                told.getReplies());
        assertSame("being told twice changes nothing", told, told.told());
        assertEquals("the first conversation is untouched",
                "Four sticks would do it.", model.getSaid());
    }

    @Test
    public void aQuestWithNothingFurtherToSayNeverOffersToTell() {
        QuestDialogueModel model = offer("");
        assertEquals(Arrays.asList(QuestDialogueModel.Reply.ACCEPT,
                        QuestDialogueModel.Reply.DECLINE),
                model.getReplies());
        assertSame("and asking is not possible", model, model.told());
    }

    @Test
    public void aHandInOffersToGiveOverRatherThanToTake() {
        QuestDialogueModel model = QuestDialogueModel.of("Nia", "",
                "A Word with Nia", "Give Nia the 4 sticks",
                QuestDialogueModel.Mood.HAND_IN, "Have you my sticks?", "",
                EVERY_LABEL);
        assertEquals(Arrays.asList(QuestDialogueModel.Reply.HAND_OVER,
                        QuestDialogueModel.Reply.LEAVE),
                model.getReplies());
        assertEquals("Here you are.",
                model.labelOf(QuestDialogueModel.Reply.HAND_OVER));
    }

    @Test
    public void aQuestPartWayThroughOnlyLetsThePlayerLeave() {
        QuestDialogueModel model = QuestDialogueModel.of("Nia", "",
                "A Word with Nia", "", QuestDialogueModel.Mood.PROGRESS,
                "Still looking?", "", EVERY_LABEL);
        assertEquals(Arrays.asList(QuestDialogueModel.Reply.LEAVE),
                model.getReplies());
    }

    @Test
    public void aConversationIsAlwaysEndableEvenWithNoLabelsWritten() {
        QuestDialogueModel model = offer("", "", "", "", "", "");
        assertEquals("nothing may be taken without words for it",
                Arrays.asList(QuestDialogueModel.Reply.LEAVE),
                model.getReplies());
    }

    @Test
    public void onlyAskingForMoreKeepsTheConversationOpen() {
        assertFalse(QuestDialogueModel.ends(QuestDialogueModel.Reply.MORE));
        assertTrue(QuestDialogueModel.ends(QuestDialogueModel.Reply.ACCEPT));
        assertTrue(QuestDialogueModel.ends(QuestDialogueModel.Reply.DECLINE));
        assertTrue(QuestDialogueModel.ends(QuestDialogueModel.Reply.HAND_OVER));
        assertTrue(QuestDialogueModel.ends(QuestDialogueModel.Reply.LEAVE));
    }

    @Test
    public void whatTheScreenShowsIsWhatItWasGiven() {
        QuestDialogueModel model = offer("more");
        assertEquals("Nia", model.getSpeaker());
        assertEquals("Bree", model.getSubtitle());
        assertEquals("A Word with Nia", model.getQuestTitle());
        assertEquals("Gather 4 sticks", model.getObjective());
        // Nothing given is nothing shown, never null.
        QuestDialogueModel bare = QuestDialogueModel.of(null, null, null,
                null, null, null, null, null);
        assertEquals("", bare.getSpeaker());
        assertEquals("", bare.getSaid());
        assertEquals(QuestDialogueModel.Mood.OFFER, bare.getMood());
        assertEquals("", bare.labelOf(null));
    }
}
