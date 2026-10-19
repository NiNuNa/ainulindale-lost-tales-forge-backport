package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Finding quests by playing (QUEST-11, QUEST-29): after a LOTR NPC
 * offered a quest, the same NPC touched again soon goes to LOTR, and a
 * character's log remembers that it was given its starters.
 */
public final class QuestOfferPauseTest {
    private static final UUID PLAYER = UUID.fromString("80000000-0000-0000-0000-000000000001");

    @After
    public void clear() {
        QuestOfferPause.clear();
    }

    @Test
    public void theSameNpcTouchedAgainSoonGoesToLotr() {
        QuestOfferPause.offered(PLAYER, 42, 1000L);
        assertTrue(QuestOfferPause.isPaused(PLAYER, 42, 1100L));
        assertFalse("another NPC still offers", QuestOfferPause.isPaused(PLAYER, 7, 1100L));
        assertFalse("the pause ends", QuestOfferPause.isPaused(PLAYER, 42,
                1000L + QuestOfferPause.PAUSE_TICKS));
        QuestOfferPause.offered(PLAYER, 42, 2000L);
        QuestOfferPause.forget(PLAYER);
        assertFalse(QuestOfferPause.isPaused(PLAYER, 42, 2001L));
    }

    @Test
    public void aCharactersLogRemembersItsStarters() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        assertFalse(data.wereStartersGiven());
        assertTrue(data.markStartersGiven());
        assertFalse("once only", data.markStartersGiven());
        NBTTagCompound wrapper = new NBTTagCompound();
        data.saveNBTData(wrapper);
        LostTalesQuestPlayerData loaded = new LostTalesQuestPlayerData();
        loaded.loadNBTData(wrapper);
        assertTrue(loaded.wereStartersGiven());
        LostTalesQuestPlayerData.validateCharacterState(loaded.writeCharacterState());
    }
}
