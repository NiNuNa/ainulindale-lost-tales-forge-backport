package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The rewards waiting for room in the quest log (QUEST-44): saved and
 * read with it, kept across death and a character switch, refused by a
 * log held read-only, and keeping their missive in the log until they are
 * collected.
 */
public final class LostTalesQuestPlayerWaitingTest {
    private static final String CACHE = "losttales:tutorial/cheese_cache";
    private static final List<WaitingReward> BREAD_AND_STARTER = Arrays.asList(
            WaitingReward.item("minecraft:bread", 0, 3),
            WaitingReward.starter("losttales:path/survivalist/stone_and_shelter", 1),
            WaitingReward.relic("lotr:item.helmetRanger", "losttales:striders_garb", 1));

    private static NBTTagCompound saved(LostTalesQuestPlayerData data) {
        NBTTagCompound compound = new NBTTagCompound();
        data.saveNBTData(compound);
        return compound;
    }

    @Test
    public void waitingRewardsAreSavedWithTheLog() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        assertTrue(data.addWaitingRewards(CACHE, BREAD_AND_STARTER).isEmpty());
        LostTalesQuestPlayerData read = new LostTalesQuestPlayerData();
        read.loadNBTData(saved(data));
        assertFalse(read.isReadOnlyForNewerVersion());
        assertEquals(BREAD_AND_STARTER, read.getWaitingRewards(CACHE));
        assertTrue("the log names the quest", read.getQuestIdsInLog().contains(CACHE));
    }

    /** A log with nothing waiting writes nothing for it, so a character's saved state stays as it was. */
    @Test
    public void nothingWaitingWritesNothing() {
        NBTTagCompound compound = saved(new LostTalesQuestPlayerData());
        assertFalse(compound.getCompoundTag(LostTalesQuestPlayerData.PROPERTY_ID)
                .hasKey(LostTalesQuestPlayerData.TAG_WAITING_REWARDS));
    }

    /** A character's saved state with rewards waiting is read and written the same way: it may be switched to. */
    @Test
    public void aCharacterStateWithWaitingRewardsIsCanonical() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.addWaitingRewards(CACHE, BREAD_AND_STARTER);
        NBTTagCompound state = data.writeCharacterState();
        assertEquals(state, LostTalesQuestPlayerData.validateCharacterState(state));
        LostTalesQuestPlayerData switched = new LostTalesQuestPlayerData();
        switched.replaceCharacterState(state);
        assertEquals(BREAD_AND_STARTER, switched.getWaitingRewards(CACHE));
    }

    /** A relic piece waits with its mark, a kind apart from the same item unmarked (CONTENT-11). */
    @Test
    public void aRelicPieceWaitsWithItsMark() {
        WaitingReward relic = WaitingReward.relic("lotr:item.helmetRanger",
                "losttales:striders_garb", 1);
        WaitingReward plain = WaitingReward.item("lotr:item.helmetRanger", 0, 1);
        assertFalse(relic.isSameKind(plain));
        assertTrue(relic.isValid());
        assertEquals("losttales:striders_garb", relic.getRelic());
        assertEquals("", plain.getRelic());
        assertEquals(relic, relic.withCount(2).withCount(1));
    }

    @Test
    public void deathKeepsWhatWaits() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.addWaitingRewards(CACHE, BREAD_AND_STARTER);
        LostTalesQuestPlayerData respawned = new LostTalesQuestPlayerData();
        respawned.copyFrom(data);
        assertEquals(BREAD_AND_STARTER, respawned.getWaitingRewards(CACHE));
    }

    /**
     * Waiting rewards this build cannot read, of another version or not a
     * block at all, hold the whole log read-only: a character whose saved
     * state holds them cannot be switched to.
     */
    @Test(expected = IllegalArgumentException.class)
    public void unreadableWaitingRewardsHoldTheLogReadOnly() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.addWaitingRewards(CACHE, BREAD_AND_STARTER);
        NBTTagCompound state = data.writeCharacterState();
        state.getCompoundTag(LostTalesQuestPlayerData.TAG_WAITING_REWARDS)
                .setInteger(WaitingRewardsNbtCodec.TAG_VERSION, 99);
        LostTalesQuestPlayerData.validateCharacterState(state);
    }

    @Test(expected = IllegalArgumentException.class)
    public void waitingRewardsThatAreNoBlockHoldTheLogReadOnly() {
        NBTTagCompound state = new LostTalesQuestPlayerData().writeCharacterState();
        state.setString(LostTalesQuestPlayerData.TAG_WAITING_REWARDS, "bread");
        LostTalesQuestPlayerData.validateCharacterState(state);
    }

    /** What a collection leaves takes the place of what waited. */
    @Test
    public void aCollectionReplacesWhatWaited() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.addWaitingRewards(CACHE, BREAD_AND_STARTER);
        assertTrue(data.replaceWaitingRewards(CACHE, Collections.singletonList(
                WaitingReward.item("minecraft:bread", 0, 1))));
        assertEquals(Collections.singletonList(WaitingReward.item("minecraft:bread", 0, 1)),
                data.getWaitingRewards(CACHE));
        data.replaceWaitingRewards(CACHE, Collections.<WaitingReward>emptyList());
        assertTrue(data.getWaitingRewards().isEmpty());
    }

    /** A missive whose rewards wait stays in the log, past the latest ended ones kept, until they are collected. */
    @Test
    public void aMissiveStaysWhileItsRewardsWait() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        String prefix = "losttales:missive/dim0_1_64_1/";
        String waiting = prefix + "waiting";
        finishMissive(data, waiting, 0L);
        data.addWaitingRewards(waiting, Collections.singletonList(
                WaitingReward.item("lotr:item.silverCoin", 0, 5)));
        for (int index = 0; index < LostTalesQuestPlayerData.MAX_ENDED_MISSIVES + 10; index++) {
            finishMissive(data, prefix + index, index + 1L);
        }
        assertNotNull("kept while its coins wait", data.getQuestHistoryEntry(waiting));
        data.replaceWaitingRewards(waiting, Collections.<WaitingReward>emptyList());
        data.forgetEndedMissives();
        assertEquals("collected, it goes as the oldest ended", null,
                data.getQuestHistoryEntry(waiting));
    }

    private static void finishMissive(LostTalesQuestPlayerData data, String id, long time) {
        assertTrue(data.rememberMissiveDefinition(new LostTalesQuestDefinition(id,
                "Bounty", "", false, false, LostTalesQuestDefinition.START_MODE_LOCKED,
                null, Collections.singletonMap("coins", "5"), null, null, null, null,
                null, Collections.singletonList(new LostTalesQuestStageDefinition("10",
                        Collections.singletonList(new LostTalesQuestObjectiveDefinition(
                                "kill_wolves", "kill", "", false,
                                Collections.singletonMap("entity", "Wolf"))))),
                false, null, null)));
        data.startQuest(id, "10", time, 0L);
        assertTrue(data.completeQuest(id, time, Collections.<String>emptySet()));
    }
}
