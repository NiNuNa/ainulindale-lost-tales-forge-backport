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
public final class QuestConversationModelTest {

    private static final String[] EVERY_LABEL = {
            "I'll do it.", "Tell me more.", "Here you are.", "Not now.",
            "Farewell."};

    /** The kinds of reply the model offers, in its order. */
    private static java.util.List<QuestConversationModel.Reply> replies(QuestConversationModel model) {
        java.util.List<QuestConversationModel.Reply> kinds =
                new java.util.ArrayList<QuestConversationModel.Reply>();
        for (QuestConversationModel.Option option : model.getReplies()) {
            kinds.add(option.getReply());
        }
        return kinds;
    }

    /** A hand-in with a pick offers one hand-over per item, each naming its choice (QUEST-33). */
    @Test
    public void aHandInWithAPickOffersEachItem() {
        QuestConversationModel model = QuestConversationModel.of("Nia", "",
                "A Word with Nia", "Return to Nia.",
                QuestConversationModel.Mood.HAND_IN, "You came back!", "",
                EVERY_LABEL).withChoices(Arrays.asList(
                        "I'll take the sword.", "I'll take the bow."));
        java.util.List<QuestConversationModel.Option> options = model.getReplies();
        assertEquals(3, options.size());
        assertEquals(QuestConversationModel.Reply.HAND_OVER, options.get(0).getReply());
        assertEquals(0, options.get(0).getChoice());
        assertEquals("I'll take the bow.", options.get(1).getLabel());
        assertEquals(1, options.get(1).getChoice());
        assertEquals(QuestConversationModel.Reply.LEAVE, options.get(2).getReply());
        assertEquals(-1, options.get(2).getChoice());
    }

    private static QuestConversationModel offer(String more, String... labels) {
        return QuestConversationModel.of("Nia", "Bree", "A Word with Nia",
                "Gather 4 sticks", QuestConversationModel.Mood.OFFER,
                "Four sticks would do it.", more,
                labels.length == 0 ? EVERY_LABEL : labels);
    }

    @Test
    public void anOfferLetsThePlayerTakeItAskMoreOrRefuse() {
        QuestConversationModel model = offer("Any dry stick will do.");
        assertEquals(Arrays.asList(QuestConversationModel.Reply.ACCEPT,
                        QuestConversationModel.Reply.MORE,
                        QuestConversationModel.Reply.DECLINE),
                replies(model));
        assertEquals("Four sticks would do it.", model.getSaid());
        assertEquals("I'll do it.",
                model.labelOf(QuestConversationModel.Reply.ACCEPT));
    }

    @Test
    public void askingForMoreSaysTheFurtherLineAndIsNotOfferedTwice() {
        QuestConversationModel model = offer("Any dry stick will do.");
        QuestConversationModel told = model.told();

        assertEquals("Any dry stick will do.", told.getSaid());
        assertEquals(Arrays.asList(QuestConversationModel.Reply.ACCEPT,
                        QuestConversationModel.Reply.DECLINE),
                replies(told));
        assertSame("being told twice changes nothing", told, told.told());
        assertEquals("the first conversation is untouched",
                "Four sticks would do it.", model.getSaid());
    }

    @Test
    public void aQuestWithNothingFurtherToSayNeverOffersToTell() {
        QuestConversationModel model = offer("");
        assertEquals(Arrays.asList(QuestConversationModel.Reply.ACCEPT,
                        QuestConversationModel.Reply.DECLINE),
                replies(model));
        assertSame("and asking is not possible", model, model.told());
    }

    @Test
    public void aHandInOffersToGiveOverRatherThanToTake() {
        QuestConversationModel model = QuestConversationModel.of("Nia", "",
                "A Word with Nia", "Give Nia the 4 sticks",
                QuestConversationModel.Mood.HAND_IN, "Have you my sticks?", "",
                EVERY_LABEL);
        assertEquals(Arrays.asList(QuestConversationModel.Reply.HAND_OVER,
                        QuestConversationModel.Reply.LEAVE),
                replies(model));
        assertEquals("Here you are.",
                model.labelOf(QuestConversationModel.Reply.HAND_OVER));
    }

    @Test
    public void aQuestPartWayThroughOnlyLetsThePlayerLeave() {
        QuestConversationModel model = QuestConversationModel.of("Nia", "",
                "A Word with Nia", "", QuestConversationModel.Mood.PROGRESS,
                "Still looking?", "", EVERY_LABEL);
        assertEquals(Arrays.asList(QuestConversationModel.Reply.LEAVE),
                replies(model));
    }

    @Test
    public void aConversationIsAlwaysEndableEvenWithNoLabelsWritten() {
        QuestConversationModel model = offer("", "", "", "", "", "");
        assertEquals("nothing may be taken without words for it",
                Arrays.asList(QuestConversationModel.Reply.LEAVE),
                replies(model));
    }

    @Test
    public void whatTheScreenShowsIsWhatItWasGiven() {
        QuestConversationModel model = offer("more");
        assertEquals("Nia", model.getSpeaker());
        assertEquals("Bree", model.getSubtitle());
        assertEquals("A Word with Nia", model.getQuestTitle());
        assertEquals("Gather 4 sticks", model.getObjective());
        // Nothing given is nothing shown, never null.
        QuestConversationModel bare = QuestConversationModel.of(null, null, null,
                null, null, null, null, null);
        assertEquals("", bare.getSpeaker());
        assertEquals("", bare.getSaid());
        assertEquals("", bare.labelOf(null));
    }
}
