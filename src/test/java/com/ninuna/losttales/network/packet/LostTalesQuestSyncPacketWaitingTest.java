package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.player.WaitingReward;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rewards waiting for room travel with the quest log (QUEST-44):
 * whole and in order, each kind once, and a payload naming a kind twice
 * is malformed, the client keeping the log it holds.
 */
public final class LostTalesQuestSyncPacketWaitingTest {
    private static final String CACHE = "losttales:tutorial/cheese_cache";
    private static final List<WaitingReward> WAITING = Arrays.asList(
            WaitingReward.item("minecraft:bread", 0, 3),
            WaitingReward.item("lotr:item.silverCoin", 2, 7),
            WaitingReward.starter("losttales:path/survivalist/night_watch", 1),
            WaitingReward.relic("lotr:item.bootsRanger", "losttales:striders_garb", 1));

    private static LostTalesQuestSyncPacket roundTrip(LostTalesQuestSyncPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesQuestSyncPacket decoded = new LostTalesQuestSyncPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    @Test
    public void waitingRewardsTravelWithTheLog() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.addWaitingRewards(CACHE, WAITING);
        LostTalesQuestSyncPacket decoded = roundTrip(
                LostTalesQuestSyncPacket.fromPlayerData(data));
        assertFalse(decoded.isMalformed());
        assertEquals(Collections.singletonMap(CACHE, WAITING),
                decoded.getWaitingRewards());
    }

    @Test
    public void nothingWaitingTravelsAsNothing() {
        LostTalesQuestSyncPacket decoded = roundTrip(
                LostTalesQuestSyncPacket.fromPlayerData(new LostTalesQuestPlayerData()));
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getWaitingRewards().isEmpty());
    }

    /** What changes in the waiting rewards changes the log's shape, so the whole log is sent. */
    @Test
    public void waitingRewardsAreOfTheLogsShape() {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.addWaitingRewards(CACHE, WAITING);
        assertEquals(Collections.singletonMap(CACHE, WAITING),
                LostTalesQuestSyncPacket.fromPlayerData(data)
                        .withoutObjectiveCounts().getWaitingRewards());
    }

    @Test
    public void aKindNamedTwiceIsMalformed() {
        Map<String, List<WaitingReward>> twice =
                new LinkedHashMap<String, List<WaitingReward>>();
        twice.put(CACHE, Arrays.asList(WaitingReward.item("minecraft:bread", 0, 3),
                WaitingReward.item("minecraft:bread", 0, 4)));
        LostTalesQuestSyncPacket decoded = roundTrip(new LostTalesQuestSyncPacket()
                .withWaitingRewards(twice));
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getWaitingRewards().isEmpty());
    }

    /** A reward that could not wait is never sent. */
    @Test
    public void anInvalidRewardIsLeftOut() {
        Map<String, List<WaitingReward>> rewards =
                new LinkedHashMap<String, List<WaitingReward>>();
        rewards.put(CACHE, Arrays.asList(WaitingReward.item("minecraft:bread", 0, 0),
                WaitingReward.item("minecraft:apple", 0, 1)));
        rewards.put("", Collections.singletonList(WaitingReward.item("minecraft:apple", 0, 1)));
        LostTalesQuestSyncPacket decoded = roundTrip(new LostTalesQuestSyncPacket()
                .withWaitingRewards(rewards));
        assertFalse(decoded.isMalformed());
        assertEquals(Collections.singletonMap(CACHE, Collections.singletonList(
                WaitingReward.item("minecraft:apple", 0, 1))), decoded.getWaitingRewards());
    }
}
