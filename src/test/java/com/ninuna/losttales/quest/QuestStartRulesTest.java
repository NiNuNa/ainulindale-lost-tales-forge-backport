package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.QuestStartProof.Way;
import com.ninuna.losttales.quest.QuestStartRules.Verdict;
import com.ninuna.losttales.quest.missive.MissiveWords;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.world.WorldQuestFixtures;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The one set of rules every quest start is judged by: the quest a
 * player's to take, the proof, the way the quest and the server allow,
 * an offered quest kept to its conversation, and the take-again rule.
 */
public final class QuestStartRulesTest {
    private static final String ID = "losttales:test/start";
    private static final UUID AUTHOR =
            UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID MEMBER =
            UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    public void onlyAQuestAPlayerTakesStarts() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        assertEquals(Verdict.UNKNOWN_QUEST, judge(null, data, Way.COMMAND));
        assertEquals(Verdict.WORLD_QUEST,
                judge(WorldQuestFixtures.greenway(), data, Way.COMMAND));
        assertEquals(Verdict.NO_PLAYER_DATA,
                judge(quest(LostTalesQuestDefinition.START_MODE_ANY, false), null,
                        Way.COMMAND));
    }

    @Test
    public void aStartWhoseProofFailsStartsNothing() {
        LostTalesQuestDefinition any = quest(LostTalesQuestDefinition.START_MODE_ANY, false);
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        for (Way way : Way.values()) {
            assertEquals(way.name(), Verdict.UNPROVEN, QuestStartRules.judge(
                    any, data, way, false, true, true, 0L));
        }
    }

    @Test
    public void aCommandStartsEvenALockedQuest() {
        assertEquals(Verdict.ADMIT, judge(
                quest(LostTalesQuestDefinition.START_MODE_LOCKED, false),
                new LostTalesQuestPlayerData(), Way.COMMAND));
    }

    @Test
    public void anItemStartsOnlyAnItemQuestWhileTheServerAllowsIt() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        LostTalesQuestDefinition item = quest(LostTalesQuestDefinition.START_MODE_ITEM, false);
        assertEquals(Verdict.ADMIT, judge(item, data, Way.ITEM));
        assertEquals(Verdict.WRONG_START, QuestStartRules.judge(item, data,
                Way.ITEM, true, false, true, 0L));
        assertEquals(Verdict.WRONG_START, judge(
                quest(LostTalesQuestDefinition.START_MODE_INTERACTION, false),
                data, Way.ITEM));
    }

    @Test
    public void aTouchStartsAQuestThatIsNotOffered() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        LostTalesQuestDefinition touched = quest(LostTalesQuestDefinition.START_MODE_INTERACTION, false);
        assertEquals(Verdict.ADMIT, judge(touched, data, Way.TOUCH));
        assertEquals(Verdict.WRONG_START, QuestStartRules.judge(touched, data,
                Way.TOUCH, true, true, false, 0L));
        assertEquals(Verdict.WRONG_START, judge(
                quest(LostTalesQuestDefinition.START_MODE_ITEM, false), data,
                Way.TOUCH));
    }

    /** An offered quest starts by its conversation, a card or a command; a touch offers it, an item is refused. */
    @Test
    public void anOfferedQuestKeepsToItsConversation() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        LostTalesQuestDefinition offered = quest(LostTalesQuestDefinition.START_MODE_ANY, true);
        assertEquals(Verdict.ADMIT, judge(offered, data, Way.CONVERSATION));
        assertEquals(Verdict.ADMIT, judge(offered, data, Way.CARD));
        assertEquals(Verdict.ADMIT, judge(offered, data, Way.COMMAND));
        assertEquals(Verdict.OFFER, judge(offered, data, Way.TOUCH));
        assertEquals(Verdict.WRONG_START, judge(offered, data, Way.ITEM));
        assertEquals(Verdict.WRONG_START, QuestStartRules.judge(offered, data,
                Way.CONVERSATION, true, true, false, 0L));
    }

    @Test
    public void aConversationStartsOnlyAnOfferedQuest() {
        assertEquals(Verdict.WRONG_START, judge(
                quest(LostTalesQuestDefinition.START_MODE_INTERACTION, false),
                new LostTalesQuestPlayerData(), Way.CONVERSATION));
    }

    @Test
    public void aCardIsJoinedOnlyByAWayTheServerAllows() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        LostTalesQuestDefinition item = quest(LostTalesQuestDefinition.START_MODE_ITEM, false);
        assertEquals(Verdict.ADMIT, QuestStartRules.judge(item, data, Way.CARD,
                true, true, false, 0L));
        assertEquals(Verdict.WRONG_START, QuestStartRules.judge(item, data,
                Way.CARD, true, false, true, 0L));
        assertEquals(Verdict.WRONG_START, judge(
                quest(LostTalesQuestDefinition.START_MODE_LOCKED, false), data,
                Way.CARD));
    }

    @Test
    public void aLetterStartsOnlyAMissive() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        assertEquals(Verdict.ADMIT, judge(missive(), data, Way.LETTER));
        assertEquals(Verdict.WRONG_START, judge(
                quest(LostTalesQuestDefinition.START_MODE_ANY, false), data,
                Way.LETTER));
        assertEquals(Verdict.WRONG_START, judge(missive(), data, Way.CARD));
    }

    @Test
    public void aQuestIsTakenAgainOnlyAsItAllows() {
        LostTalesQuestDefinition once = quest(LostTalesQuestDefinition.START_MODE_ANY, false);
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest(ID, "10", 0L, 0L);
        assertEquals(Verdict.ALREADY_ACTIVE, judge(once, data, Way.COMMAND));

        data.completeQuest(ID, 10L, Collections.<String>emptySet());
        assertEquals(Verdict.ALREADY_COMPLETED, judge(once, data, Way.COMMAND));

        LostTalesQuestPlayerData gaveUp = new LostTalesQuestPlayerData();
        gaveUp.startQuest(ID, "10", 0L, 0L);
        gaveUp.abandonQuest(ID, "gui.losttales.quest.reason.abandoned", 10L);
        assertEquals(Verdict.NOT_RESTARTABLE, judge(once, gaveUp, Way.COMMAND));

        LostTalesQuestDefinition again = new LostTalesQuestDefinition(ID, "Start",
                "", true, true, LostTalesQuestDefinition.START_MODE_ANY, null,
                null, null, null, null, null, null, stages(), false, null,
                null);
        assertEquals(Verdict.ADMIT, judge(again, data, Way.COMMAND));
        assertEquals(Verdict.ADMIT, judge(again, gaveUp, Way.COMMAND));
    }

    /** A repeatable quest done waits its repeatAfter before any way takes it again (QUEST-45). */
    @Test
    public void aRepeatableQuestWaitsBeforeItComesBack() {
        LostTalesQuestDefinition daily = new LostTalesQuestDefinition(ID, "Start",
                "", true, true, LostTalesQuestDefinition.START_MODE_ANY, null,
                null, null, null, null, null, null, stages(), false, null,
                Collections.singletonMap(LostTalesQuestRules.REPEAT_AFTER, "1"));
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        assertEquals("never taken, no wait", Verdict.ADMIT,
                QuestStartRules.judge(daily, data, Way.COMMAND, true, true, true, 0L));
        data.startQuest(ID, "10", 0L, 0L);
        data.completeQuest(ID, 100L, Collections.<String>emptySet());
        assertEquals(24100L, daily.takenAgainAt(data.getQuestHistoryEntry(ID)));
        for (Way way : new Way[] { Way.COMMAND, Way.TOUCH, Way.ITEM, Way.CARD }) {
            assertEquals(way.name(), Verdict.TOO_SOON, QuestStartRules.judge(
                    daily, data, way, true, true, true, 24099L));
        }
        assertEquals(Verdict.ADMIT, QuestStartRules.judge(daily, data,
                Way.COMMAND, true, true, true, 24100L));

        // Given up, it comes back as restartable says, with no wait.
        data.startQuest(ID, "10", 24100L, 0L);
        data.abandonQuest(ID, "gui.losttales.quest.reason.abandoned", 24200L);
        assertEquals(0L, daily.takenAgainAt(data.getQuestHistoryEntry(ID)));
        assertEquals(Verdict.ADMIT, QuestStartRules.judge(daily, data,
                Way.COMMAND, true, true, true, 24200L));

        // The count of completions goes on: every completion counts.
        data.startQuest(ID, "10", 24200L, 0L);
        data.completeQuest(ID, 30000L, Collections.<String>emptySet());
        assertEquals(2, data.getQuestHistoryEntry(ID).getCompletions());
        assertEquals(Verdict.TOO_SOON, QuestStartRules.judge(daily, data,
                Way.COMMAND, true, true, true, 30000L));
    }

    /** A quest with no wait, or one that is not repeatable, keeps the plain take-again rule. */
    @Test
    public void onlyARepeatableQuestWithAWaitWaits() {
        LostTalesQuestDefinition again = new LostTalesQuestDefinition(ID, "Start",
                "", true, true, LostTalesQuestDefinition.START_MODE_ANY, null,
                null, null, null, null, null, null, stages(), false, null, null);
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest(ID, "10", 0L, 0L);
        data.completeQuest(ID, 100L, Collections.<String>emptySet());
        assertEquals(0L, again.takenAgainAt(data.getQuestHistoryEntry(ID)));
        assertEquals(Verdict.ADMIT, QuestStartRules.judge(again, data,
                Way.COMMAND, true, true, true, 100L));
        LostTalesQuestDefinition once = new LostTalesQuestDefinition(ID, "Start",
                "", false, true, LostTalesQuestDefinition.START_MODE_ANY, null,
                null, null, null, null, null, null, stages(), false, null,
                Collections.singletonMap(LostTalesQuestRules.REPEAT_AFTER, "1"));
        assertEquals(Verdict.ALREADY_COMPLETED, QuestStartRules.judge(once, data,
                Way.COMMAND, true, true, true, 1000000L));
    }

    @Test
    public void theAuthorNeverJoinsTheirOwnCard() {
        assertFalse(QuestStartRules.cardHolds(AUTHOR, AUTHOR, true));
        assertTrue(QuestStartRules.cardHolds(MEMBER, AUTHOR, true));
    }

    @Test
    public void nobodyJoinsOnceTheAuthorNoLongerRunsTheQuest() {
        assertFalse(QuestStartRules.cardHolds(MEMBER, AUTHOR, false));
        assertFalse(QuestStartRules.cardHolds(MEMBER, null, true));
        assertFalse(QuestStartRules.cardHolds(null, AUTHOR, true));
    }

    private static Verdict judge(LostTalesQuestDefinition quest,
                                 LostTalesQuestPlayerData data, Way way) {
        return QuestStartRules.judge(quest, data, way, true, true, true, 0L);
    }

    private static LostTalesQuestDefinition quest(String startMode,
                                                  boolean offered) {
        Map<String, String> dialogue = offered
                ? Collections.singletonMap(LostTalesQuestDialogue.OFFER,
                        "Will you help me?")
                : Collections.<String, String>emptyMap();
        return new LostTalesQuestDefinition(ID, "Start", "", false, false,
                startMode, null, null,
                Collections.singletonMap("entity", "losttales.Nia"), null,
                null, dialogue, null, stages(), false, null, null);
    }

    private static LostTalesQuestDefinition missive() {
        return new LostTalesQuestDefinition(
                "losttales:missive/generated/dim0_1_2_3/100_0", "", "", true,
                true, LostTalesQuestDefinition.START_MODE_LOCKED, null, null,
                null, null, null, null, null, stages(), false,
                Collections.singletonMap(MissiveWords.TITLE, "materials_wanted"),
                null);
    }

    private static java.util.List<LostTalesQuestStageDefinition> stages() {
        return Collections.singletonList(new LostTalesQuestStageDefinition("10",
                Collections.singletonList(new LostTalesQuestObjectiveDefinition(
                        "logs", "gather", "", false,
                        Collections.singletonMap("item", "minecraft:log")))));
    }
}
