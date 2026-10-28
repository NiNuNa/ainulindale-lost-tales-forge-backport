package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import java.util.Collections;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesQuestPlayerHistoryTest {

    @Test
    public void failedAndAbandonedQuestsKeepTheirReasonAndWorldDate() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest("losttales:timed", "start", 10L, 20L);
        assertTrue(data.failQuest("losttales:timed",
                "Time limit expired.", 20L));
        data.startQuest("losttales:choice", "start", 30L, 0L);
        assertTrue(data.abandonQuest("losttales:choice",
                "Abandoned by the player.", 40L));

        NBTTagCompound wrapper = new NBTTagCompound();
        data.saveNBTData(wrapper);
        LostTalesQuestPlayerData loaded = new LostTalesQuestPlayerData();
        loaded.loadNBTData(wrapper);

        LostTalesQuestHistoryEntry failed =
                loaded.getQuestHistoryEntry("losttales:timed");
        LostTalesQuestHistoryEntry abandoned =
                loaded.getQuestHistoryEntry("losttales:choice");
        assertNotNull(failed);
        assertTrue(failed.isFailed());
        assertEquals("Time limit expired.", failed.getDetail());
        assertEquals(20L, failed.getWorldTime());
        assertNotNull(abandoned);
        assertTrue(abandoned.isAbandoned());
        assertEquals(40L, abandoned.getWorldTime());
        assertFalse(abandoned.isFailed());
    }

    @Test
    public void stageAdvanceKeepsEarlierOptionalObjectiveProgress() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest("losttales:optional", "first", 10L, 0L);
        data.setObjectiveProgress("losttales:optional", "help_scout", 2);

        assertTrue(data.setQuestStage(
                "losttales:optional", 1, "second"));

        assertEquals(2, data.getObjectiveProgress(
                "losttales:optional", "help_scout"));
    }

    /**
     * A retake keeps the last end's line while it runs, counts each
     * completion, and a retake given up keeps the count: the quest stays
     * "ever completed" for every prerequisite (QUEST-10).
     */
    @Test
    public void aRetakeKeepsTheHistoryLineAndCountsCompletions() {
        String id = "losttales:again";
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest(id, "first", 10L, 0L);
        assertTrue(data.completeQuest(id, 20L, Collections.<String>emptySet()));
        assertEquals(1, data.getQuestHistoryEntry(id).getCompletions());

        data.startQuest(id, "first", 30L, 0L);
        assertTrue("the line stays while the retake runs",
                data.getQuestHistoryEntry(id) != null);
        assertTrue(data.isQuestActive(id));
        assertTrue(data.isQuestCompleted(id));
        assertTrue(data.completeQuest(id, 40L, Collections.<String>emptySet()));
        assertEquals(2, data.getQuestHistoryEntry(id).getCompletions());
        assertEquals(40L, data.getQuestHistoryEntry(id).getWorldTime());

        data.startQuest(id, "first", 50L, 0L);
        assertTrue(data.abandonQuest(id, "gui.losttales.quest.reason.abandoned", 60L));
        LostTalesQuestHistoryEntry line = data.getQuestHistoryEntry(id);
        assertTrue(line.isAbandoned());
        assertEquals("an abandoned retake keeps the count", 2, line.getCompletions());
        assertTrue("still ever completed", data.isQuestCompleted(id));
        assertEquals(Collections.singleton(id), data.getCompletedQuestIds());

        data.startQuest(id, "first", 70L, 0L);
        assertTrue(data.failQuest(id, "gui.losttales.quest.reason.expired", 80L));
        assertEquals(2, data.getQuestHistoryEntry(id).getCompletions());

        NBTTagCompound wrapper = new NBTTagCompound();
        data.saveNBTData(wrapper);
        LostTalesQuestPlayerData loaded = new LostTalesQuestPlayerData();
        loaded.loadNBTData(wrapper);
        assertEquals(2, loaded.getQuestHistoryEntry(id).getCompletions());
        assertTrue(loaded.getQuestHistoryEntry(id).isFailed());

        assertTrue("only an operator's reset clears it", loaded.resetQuest(id));
        assertFalse(loaded.isQuestCompleted(id));
    }

    /** The stages a quest reached are kept while it runs and with its line (QUEST-30). */
    @Test
    public void theStagesReachedAreKept() {
        String id = "losttales:tale";
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest(id, "start", 10L, 0L);
        data.setQuestStage(id, 2, "end");
        assertEquals(java.util.Arrays.asList("start", "end"),
                data.getActiveQuest(id).getStagePath());

        NBTTagCompound wrapper = new NBTTagCompound();
        data.saveNBTData(wrapper);
        LostTalesQuestPlayerData loaded = new LostTalesQuestPlayerData();
        loaded.loadNBTData(wrapper);
        assertEquals(java.util.Arrays.asList("start", "end"),
                loaded.getActiveQuest(id).getStagePath());

        loaded.completeQuest(id, 20L, Collections.<String>emptySet());
        assertEquals(java.util.Arrays.asList("start", "end"),
                loaded.getQuestHistoryEntry(id).getStagePath());
        NBTTagCompound again = new NBTTagCompound();
        loaded.saveNBTData(again);
        LostTalesQuestPlayerData reloaded = new LostTalesQuestPlayerData();
        reloaded.loadNBTData(again);
        assertEquals(java.util.Arrays.asList("start", "end"),
                reloaded.getQuestHistoryEntry(id).getStagePath());
        assertFalse(reloaded.isReadOnlyForNewerVersion());
        // A character's copy of the log reads back exactly as written.
        LostTalesQuestPlayerData.validateCharacterState(
                reloaded.writeCharacterState());
    }

    /**
     * Every History line is written with its outcome, its count and its
     * path, and every running quest with its path: a log missing one, or
     * naming an outcome in other letters, is no log this build wrote, and
     * is kept read-only (MOD-10).
     */
    @Test
    public void aLogIsReadOnlyAsThisBuildWritesIt() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest("losttales:tale", "start", 10L, 0L);
        assertTrue(data.completeQuest("losttales:tale", 20L,
                Collections.<String>emptySet()));
        data.startQuest("losttales:running", "start", 30L, 0L);
        NBTTagCompound state = data.writeCharacterState();
        assertTrue(LostTalesQuestPlayerData.isStructurallyReasonable(state));

        assertFalse(LostTalesQuestPlayerData.isStructurallyReasonable(
                without(state, "QuestHistory", "Completions")));
        assertFalse(LostTalesQuestPlayerData.isStructurallyReasonable(
                without(state, "QuestHistory", "StagePath")));
        assertFalse(LostTalesQuestPlayerData.isStructurallyReasonable(
                without(state, "ActiveQuests", "StagePath")));
        NBTTagCompound otherLetters = (NBTTagCompound) state.copy();
        otherLetters.getTagList("QuestHistory", 10).getCompoundTagAt(0)
                .setString("Outcome", "completed");
        assertFalse(LostTalesQuestPlayerData.isStructurallyReasonable(
                otherLetters));
        assertEquals(LostTalesQuestHistoryEntry.Outcome.COMPLETED,
                LostTalesQuestHistoryEntry.Outcome.fromName("COMPLETED"));
        assertNull(LostTalesQuestHistoryEntry.Outcome.fromName("Completed"));
    }

    /** A copy of the log with {@code tag} taken from the first entry of {@code list}. */
    private static NBTTagCompound without(NBTTagCompound state, String list,
                                          String tag) {
        NBTTagCompound copy = (NBTTagCompound) state.copy();
        copy.getTagList(list, 10).getCompoundTagAt(0).removeTag(tag);
        return copy;
    }

    /**
     * A completion's line keeps the optional objectives it achieved and no
     * words: each reader's game words what they add (QUEST-07).
     */
    @Test
    public void completionOutcomeUsesTheSameTerminalHistory() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest("losttales:ending", "last", 10L, 0L);

        assertTrue(data.completeQuest("losttales:ending", 60L,
                Collections.singleton("rescue_scouts")));

        assertTrue(data.isQuestCompleted("losttales:ending"));
        assertEquals("", data.getQuestHistoryEntry(
                "losttales:ending").getDetail());
        assertEquals(Collections.singleton("losttales:ending"),
                data.getCompletedQuestIds());
        assertTrue(data.getQuestHistoryEntry("losttales:ending")
                .isOptionalObjectiveCompleted("rescue_scouts"));
    }
}
