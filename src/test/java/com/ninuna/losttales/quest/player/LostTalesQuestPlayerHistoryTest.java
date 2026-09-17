package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import java.util.Collections;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        assertFalse(loaded.isQuestFailed("losttales:choice"));
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

    @Test
    public void completionOutcomeUsesTheSameTerminalHistory() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest("losttales:ending", "last", 10L, 0L);

        assertTrue(data.completeQuest("losttales:ending",
                "The scouts survived.", 60L,
                Collections.singleton("rescue_scouts")));

        assertTrue(data.isQuestCompleted("losttales:ending"));
        assertEquals("The scouts survived.", data.getQuestHistoryEntry(
                "losttales:ending").getDetail());
        assertEquals(Collections.singleton("losttales:ending"),
                data.getCompletedQuestIds());
        assertTrue(data.getQuestHistoryEntry("losttales:ending")
                .isOptionalObjectiveCompleted("rescue_scouts"));
    }
}
