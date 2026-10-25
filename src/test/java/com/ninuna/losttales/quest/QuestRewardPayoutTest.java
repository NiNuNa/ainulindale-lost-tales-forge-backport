package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.player.WaitingReward;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Paying reward items with an inventory that has no room for them all
 * (QUEST-44): what fits is handed over, what does not waits under its
 * quest, kind by kind, and a later collection hands over what fits then
 * and leaves the rest waiting. Nothing is lost on the way.
 */
public final class QuestRewardPayoutTest {
    private static final String QUEST = "losttales:path/survivalist/first_sparks";
    private static final WaitingReward APPLES = WaitingReward.item("minecraft:apple", 0, 2);
    private static final WaitingReward TORCHES = WaitingReward.item("minecraft:torch", 0, 4);
    private static final WaitingReward STARTER = WaitingReward.starter(
            "losttales:path/survivalist/stone_and_shelter", 1);

    /** Hands with room for so many items, of any kind, that write down what they took. */
    private static final class Room implements QuestRewardPayout.Hands {
        int room;
        final List<WaitingReward> taken = new ArrayList<WaitingReward>();

        Room(int room) {
            this.room = room;
        }

        @Override
        public int hand(WaitingReward reward) {
            int fits = Math.min(this.room, reward.getCount());
            this.room -= fits;
            if (fits > 0) {
                this.taken.add(reward.withCount(fits));
            }
            return reward.getCount() - fits;
        }
    }

    @Test
    public void everythingFitsAndNothingWaits() {
        Room hands = new Room(64);
        assertTrue(QuestRewardPayout.leftOver(Arrays.asList(APPLES, TORCHES, STARTER),
                hands).isEmpty());
        assertEquals(Arrays.asList(APPLES, TORCHES, STARTER), hands.taken);
    }

    @Test
    public void aFullInventoryLeavesEverythingWaiting() {
        Room hands = new Room(0);
        assertEquals(Arrays.asList(APPLES, TORCHES, STARTER),
                QuestRewardPayout.leftOver(Arrays.asList(APPLES, TORCHES, STARTER), hands));
        assertTrue(hands.taken.isEmpty());
    }

    @Test
    public void whatDoesNotFitWaitsThePartThatFitsIsHanded() {
        Room hands = new Room(3);
        List<WaitingReward> left = QuestRewardPayout.leftOver(
                Arrays.asList(APPLES, TORCHES, STARTER), hands);
        assertEquals(Arrays.asList(APPLES, TORCHES.withCount(1)), hands.taken);
        assertEquals(Arrays.asList(TORCHES.withCount(3), STARTER), left);
    }

    /** Two rewards of one kind, the quest's and an optional objective's, wait as one. */
    @Test
    public void oneKindWaitsAsOne() {
        List<WaitingReward> left = QuestRewardPayout.leftOver(Arrays.asList(TORCHES,
                APPLES, TORCHES.withCount(6)), new Room(0));
        assertEquals(Arrays.asList(TORCHES.withCount(10), APPLES), left);
    }

    /** Hands that say they took more than they were given never make a reward out of nothing. */
    @Test
    public void handsCannotTakeMoreThanTheyWereGiven() {
        List<WaitingReward> left = QuestRewardPayout.leftOver(
                Collections.singletonList(TORCHES), new QuestRewardPayout.Hands() {
                    @Override
                    public int hand(WaitingReward reward) {
                        return reward.getCount() + 50;
                    }
                });
        assertEquals(Collections.singletonList(TORCHES), left);
    }

    /**
     * The whole round: a payment into a full inventory waits in the log,
     * a collection with a little room hands over that much, and a second
     * with room enough empties the quest's rewards.
     */
    @Test
    public void whatWaitsIsCollectedAsRoomComes() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        List<WaitingReward> left = QuestRewardPayout.leftOver(
                Arrays.asList(APPLES, TORCHES, STARTER), new Room(0));
        assertTrue(data.addWaitingRewards(QUEST, left).isEmpty());

        Room some = new Room(4);
        data.replaceWaitingRewards(QUEST, QuestRewardPayout.leftOver(
                data.getWaitingRewards(QUEST), some));
        assertEquals(Arrays.asList(APPLES, TORCHES.withCount(2)), some.taken);
        assertEquals(Arrays.asList(TORCHES.withCount(2), STARTER),
                data.getWaitingRewards(QUEST));

        Room enough = new Room(64);
        data.replaceWaitingRewards(QUEST, QuestRewardPayout.leftOver(
                data.getWaitingRewards(QUEST), enough));
        assertEquals(Arrays.asList(TORCHES.withCount(2), STARTER), enough.taken);
        assertTrue(data.getWaitingRewards().isEmpty());
    }

    @Test
    public void stacksAreNoLargerThanTheItemsOwn() {
        int total = 0;
        for (int size : LostTalesQuestRewardHelper.stackSizes(130, 64)) {
            assertTrue(size <= 64);
            total += size;
        }
        assertEquals(130, total);
    }
}
