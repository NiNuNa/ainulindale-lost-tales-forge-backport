package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import java.util.Collections;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A character's missives: a missive's quest is kept while it runs and
 * while it is among the latest ended ones, then forgotten with its History
 * line, so a character who takes missive after missive can always save,
 * send and switch its quest log.
 */
public final class LostTalesQuestPlayerMissiveTest {
    private static final String PREFIX = "losttales:missive/dim0_1_64_1/";

    private static LostTalesQuestDefinition missive(String id) {
        return new LostTalesQuestDefinition(id, "Bounty", "Thin the wolves.",
                true, true, LostTalesQuestDefinition.START_MODE_LOCKED, null,
                Collections.singletonMap("experience", "10"),
                null, null, null, null, null,
                Collections.singletonList(new LostTalesQuestStageDefinition("10",
                        Collections.singletonList(new LostTalesQuestObjectiveDefinition(
                                "kill_wolves", "kill", "", false,
                                Collections.<String, String>singletonMap("entity", "Wolf"))))),
                false, null, null);
    }

    private static void takeAndFinish(LostTalesQuestPlayerData data, int index) {
        String id = PREFIX + index;
        assertTrue(data.rememberMissiveDefinition(missive(id)));
        data.startQuest(id, "10", index, 0L);
        assertTrue(data.completeQuest(id, index + 1L,
                Collections.<String>emptySet()));
    }

    @Test
    public void onlyTheLatestEndedMissivesAreKept() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        int taken = LostTalesQuestPlayerData.MAX_ENDED_MISSIVES + 600;
        for (int index = 0; index < taken; index++) {
            takeAndFinish(data, index);
        }
        assertEquals(LostTalesQuestPlayerData.MAX_ENDED_MISSIVES,
                data.getMissiveDefinitions().size());
        assertEquals(LostTalesQuestPlayerData.MAX_ENDED_MISSIVES,
                data.getQuestHistory().size());
        assertNull("the oldest is forgotten",
                data.getQuestHistoryEntry(PREFIX + 0));
        assertNotNull("the latest stays in the History",
                data.getQuestHistoryEntry(PREFIX + (taken - 1)));

        NBTTagCompound saved = data.writeCharacterState();
        assertEquals("the log saves whole and switches back", saved,
                LostTalesQuestPlayerData.validateCharacterState(saved));
    }

    @Test
    public void aMissiveThatNeverRanIsForgotten() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        assertTrue(data.rememberMissiveDefinition(missive(PREFIX + "unrun")));
        assertTrue(data.forgetEndedMissives());
        assertTrue(data.getMissiveDefinitions().isEmpty());
    }

    @Test
    public void aFullLogRefusesAMissiveUntilOneEnds() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        for (int index = 0; index < LostTalesQuestPlayerData.MAX_MISSIVES; index++) {
            String id = PREFIX + index;
            assertTrue(data.rememberMissiveDefinition(missive(id)));
            data.startQuest(id, "10", 0L, 0L);
        }
        assertFalse("every missive the log holds is running",
                data.rememberMissiveDefinition(missive(PREFIX + "more")));

        assertTrue(data.abandonQuest(PREFIX + 0, "gui.losttales.quest.reason.abandoned", 5L));
        assertTrue("the ended one makes room",
                data.rememberMissiveDefinition(missive(PREFIX + "more")));
        assertNull(data.getQuestHistoryEntry(PREFIX + 0));
        assertEquals(LostTalesQuestPlayerData.MAX_MISSIVES,
                data.getMissiveDefinitions().size());
    }

    /** A loaded log's missives are known to the quest registry, every one of them. */
    @Test
    public void aLoadedLogsMissivesAreRegistered() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        String running = PREFIX + "running";
        assertTrue(data.rememberMissiveDefinition(missive(running)));
        data.startQuest(running, "10", 1L, 0L);
        takeAndFinish(data, 2);
        NBTTagCompound wrapper = new NBTTagCompound();
        data.saveNBTData(wrapper);
        LostTalesQuestRegistry.clearMissives();
        try {
            assertNull(LostTalesQuestRegistry.getQuest(running));
            LostTalesQuestPlayerData loaded = new LostTalesQuestPlayerData();
            loaded.loadNBTData(wrapper);
            assertFalse(loaded.isReadOnlyForNewerVersion());
            assertNotNull(LostTalesQuestRegistry.getQuest(running));
            assertNotNull(LostTalesQuestRegistry.getQuest(PREFIX + 2));
        } finally {
            LostTalesQuestRegistry.clearMissives();
        }
    }

    @Test
    public void aResetMissiveIsForgottenWithItsLine() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        takeAndFinish(data, 1);
        assertTrue(data.resetQuest(PREFIX + 1));
        assertTrue(data.getMissiveDefinitions().isEmpty());
        assertTrue(data.getQuestHistory().isEmpty());
    }
}
