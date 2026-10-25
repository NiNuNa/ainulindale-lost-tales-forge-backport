package com.ninuna.losttales.quest.player;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rewards a character's inventory had no room for (QUEST-44): each
 * kind waits as one under its quest, and every bound refuses what would
 * pass it while keeping what is there.
 */
public final class WaitingRewardsTest {
    private static final String CACHE = "losttales:tutorial/cheese_cache";
    private static final String SPARKS = "losttales:path/survivalist/first_sparks";

    @Test
    public void rewardsOfOneKindWaitAsOne() {
        WaitingRewards rewards = new WaitingRewards();
        assertEquals(0, rewards.add(CACHE, WaitingReward.item("minecraft:bread", 0, 2)));
        assertEquals(0, rewards.add(CACHE, WaitingReward.item("minecraft:bread", 0, 3)));
        assertEquals(0, rewards.add(CACHE, WaitingReward.item("minecraft:coal", 1, 4)));
        assertEquals(0, rewards.add(CACHE, WaitingReward.starter(SPARKS, 1)));
        assertEquals(Arrays.asList(WaitingReward.item("minecraft:bread", 0, 5),
                WaitingReward.item("minecraft:coal", 1, 4),
                WaitingReward.starter(SPARKS, 1)), rewards.get(CACHE));
        assertTrue(rewards.has(CACHE));
        assertFalse(rewards.has(SPARKS));
        assertEquals(Collections.singleton(CACHE), rewards.questIds());
    }

    @Test
    public void anItemAndAStarterOfTheSameIdAreDifferentKinds() {
        WaitingReward item = WaitingReward.item("losttales:a", 0, 1);
        WaitingReward starter = WaitingReward.starter("losttales:a", 1);
        assertFalse(item.isSameKind(starter));
        assertFalse(WaitingReward.item("minecraft:coal", 0, 1)
                .isSameKind(WaitingReward.item("minecraft:coal", 1, 1)));
    }

    @Test
    public void anInvalidRewardIsNotKept() {
        WaitingRewards rewards = new WaitingRewards();
        assertEquals(3, rewards.add(CACHE, WaitingReward.item("", 0, 3)));
        assertEquals(3, rewards.add(CACHE, WaitingReward.item("minecraft:bread", -1, 3)));
        assertEquals(3, rewards.add(CACHE, WaitingReward.item("minecraft:bread",
                WaitingReward.MAX_META + 1, 3)));
        assertEquals(0, rewards.add(CACHE, WaitingReward.item("minecraft:bread", 0, 0)));
        assertEquals(2, rewards.add("", WaitingReward.item("minecraft:bread", 0, 2)));
        assertTrue(rewards.isEmpty());
    }

    /** A kind full to its bound keeps what it holds and refuses the rest. */
    @Test
    public void aKindRefusesPastItsCount() {
        WaitingRewards rewards = new WaitingRewards();
        assertEquals(0, rewards.add(CACHE, WaitingReward.item("minecraft:bread", 0,
                WaitingRewards.MAX_COUNT - 1)));
        assertEquals(4, rewards.add(CACHE, WaitingReward.item("minecraft:bread", 0, 5)));
        assertEquals(WaitingRewards.MAX_COUNT, rewards.get(CACHE).get(0).getCount());
    }

    @Test
    public void aQuestRefusesPastItsKinds() {
        WaitingRewards rewards = new WaitingRewards();
        for (int meta = 0; meta < WaitingRewards.MAX_KINDS_EACH; meta++) {
            assertEquals(0, rewards.add(CACHE, WaitingReward.item("minecraft:wool", meta, 1)));
        }
        assertEquals(1, rewards.add(CACHE, WaitingReward.item("minecraft:dye", 0, 1)));
        assertEquals("one already there still takes more", 0,
                rewards.add(CACHE, WaitingReward.item("minecraft:wool", 0, 1)));
        assertEquals(WaitingRewards.MAX_KINDS_EACH, rewards.get(CACHE).size());
    }

    @Test
    public void theRewardsRefuseANewQuestPastTheirBound() {
        WaitingRewards rewards = new WaitingRewards();
        for (int index = 0; index < WaitingRewards.MAX_QUESTS; index++) {
            assertEquals(0, rewards.add("losttales:test/" + index,
                    WaitingReward.item("minecraft:bread", 0, 1)));
        }
        assertEquals(1, rewards.add("losttales:test/new",
                WaitingReward.item("minecraft:bread", 0, 1)));
        assertEquals(0, rewards.add("losttales:test/0",
                WaitingReward.item("minecraft:bread", 0, 1)));
        assertEquals(WaitingRewards.MAX_QUESTS, rewards.questIds().size());
    }

    /** What a collection leaves takes the place of what waited; nothing left takes the quest off. */
    @Test
    public void aCollectionLeavesWhatIsLeft() {
        WaitingRewards rewards = new WaitingRewards();
        rewards.add(CACHE, WaitingReward.item("minecraft:bread", 0, 5));
        rewards.add(SPARKS, WaitingReward.item("minecraft:torch", 0, 4));
        rewards.replace(CACHE, Collections.singletonList(
                WaitingReward.item("minecraft:bread", 0, 2)));
        assertEquals(2, rewards.get(CACHE).get(0).getCount());
        rewards.replace(CACHE, Collections.<WaitingReward>emptyList());
        assertFalse(rewards.has(CACHE));
        assertTrue(rewards.has(SPARKS));
    }
}
