package com.ninuna.losttales.compat.lotr;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

/**
 * LOTR's coins a quest pays are counted out as LOTR's own giveCoins counts
 * them, the largest coin first, so they can be handed over as items and
 * what finds no room can wait (QUEST-44).
 */
public final class LotrQuestRewardsCoinsTest {
    private static final int[] VALUES = { 1, 10, 100, 1000 };

    @Test
    public void theLargestCoinComesFirst() {
        assertArrayEquals(new int[] { 4, 3, 2, 0 },
                LotrQuestRewards.countOut(VALUES, 234));
        assertArrayEquals(new int[] { 0, 0, 0, 12 },
                LotrQuestRewards.countOut(VALUES, 12000));
        assertArrayEquals(new int[] { 5, 0, 0, 0 },
                LotrQuestRewards.countOut(VALUES, 5));
    }

    @Test
    public void nothingOrUnreadableValuesCountNothing() {
        assertNull(LotrQuestRewards.countOut(VALUES, 0));
        assertNull(LotrQuestRewards.countOut(null, 10));
        assertNull(LotrQuestRewards.countOut(new int[0], 10));
        assertNull(LotrQuestRewards.countOut(new int[] { 0, 10 }, 10));
        assertNull("a value no coins make exactly",
                LotrQuestRewards.countOut(new int[] { 10, 100 }, 15));
    }
}
