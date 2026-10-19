package com.ninuna.losttales.gui.screen.quest;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * A conversation about a quest: what is said, what may be said back, and
 * what asking for more changes. The screen draws exactly this, so
 * everything a player can reach is decided here.
 */
public final class QuestDialogueModelTest {

    private static final String[] EVERY_LABEL = {
            "I'll do it.", "Tell me more.", "Here you are.", "Not now.",
            "Farewell."};

    /** The kinds of reply the model offers, in its order. */
    private static java.util.List<QuestDialogueModel.Reply> replies(QuestDialogueModel model) {
        java.util.List<QuestDialogueModel.Reply> kinds =
                new java.util.ArrayList<QuestDialogueModel.Reply>();
        for (QuestDialogueModel.Option option : model.getReplies()) {
            kinds.add(option.getReply());
        }
        return kinds;
    }

    /** A hand-in with a pick offers one hand-over per item, each naming its choice (QUEST-33). */
    @Test
    public void aHandInWithAPickOffersEachItem() {
        QuestDialogueModel model = QuestDialogueModel.of("Nia", "",
                "A Word with Nia", "Return to Nia.",
                QuestDialogueModel.Mood.HAND_IN, "You came back!", "",
                EVERY_LABEL).withChoices(Arrays.asList(
                        "I'll take the sword.", "I'll take the bow."));
        java.util.List<QuestDialogueModel.Option> options = model.getReplies();
        assertEquals(3, options.size());
        assertEquals(QuestDialogueModel.Reply.HAND_OVER, options.get(0).getReply());
        assertEquals(0, options.get(0).getChoice());
        assertEquals("I'll take the bow.", options.get(1).getLabel());
        assertEquals(1, options.get(1).getChoice());
        assertEquals(QuestDialogueModel.Reply.LEAVE, options.get(2).getReply());
        assertEquals(-1, options.get(2).getChoice());
    }

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
                replies(model));
        assertEquals("Four sticks would do it.", model.getSaid());
        assertEquals("I'll do it.",
                model.labelOf(QuestDialogueModel.Reply.ACCEPT));
    }

    @Test
    public void askingForMoreSaysTheFurtherLineAndIsNotOfferedTwice() {
        QuestDialogueModel model = offer("Any dry stick will do.");
        QuestDialogueModel told = model.told();

        assertEquals("Any dry stick will do.", told.getSaid());
        assertEquals(Arrays.asList(QuestDialogueModel.Reply.ACCEPT,
                        QuestDialogueModel.Reply.DECLINE),
                replies(told));
        assertSame("being told twice changes nothing", told, told.told());
        assertEquals("the first conversation is untouched",
                "Four sticks would do it.", model.getSaid());
    }

    @Test
    public void aQuestWithNothingFurtherToSayNeverOffersToTell() {
        QuestDialogueModel model = offer("");
        assertEquals(Arrays.asList(QuestDialogueModel.Reply.ACCEPT,
                        QuestDialogueModel.Reply.DECLINE),
                replies(model));
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
                replies(model));
        assertEquals("Here you are.",
                model.labelOf(QuestDialogueModel.Reply.HAND_OVER));
    }

    @Test
    public void aQuestPartWayThroughOnlyLetsThePlayerLeave() {
        QuestDialogueModel model = QuestDialogueModel.of("Nia", "",
                "A Word with Nia", "", QuestDialogueModel.Mood.PROGRESS,
                "Still looking?", "", EVERY_LABEL);
        assertEquals(Arrays.asList(QuestDialogueModel.Reply.LEAVE),
                replies(model));
    }

    @Test
    public void aConversationIsAlwaysEndableEvenWithNoLabelsWritten() {
        QuestDialogueModel model = offer("", "", "", "", "", "");
        assertEquals("nothing may be taken without words for it",
                Arrays.asList(QuestDialogueModel.Reply.LEAVE),
                replies(model));
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
        assertEquals("", bare.labelOf(null));
    }
}
