package com.ninuna.losttales.quest.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The saved form of the rewards waiting for room (QUEST-44): it round
 * trips, fails closed on a version or shape it does not write, and sets a
 * quest's entry it cannot read aside whole, keeping it through every save.
 */
public final class WaitingRewardsNbtCodecTest {
    private static final String CACHE = "losttales:tutorial/cheese_cache";
    private static final String SPARKS = "losttales:path/survivalist/first_sparks";

    private static WaitingRewards sample() {
        WaitingRewards rewards = new WaitingRewards();
        rewards.add(CACHE, WaitingReward.item("minecraft:bread", 0, 70));
        rewards.add(CACHE, WaitingReward.item("lotr:item.silverCoin", 2, 3));
        rewards.add(SPARKS, WaitingReward.starter(
                "losttales:path/survivalist/stone_and_shelter", 1));
        return rewards;
    }

    @Test
    public void rewardsRoundTrip() {
        NBTTagCompound saved = WaitingRewardsNbtCodec.write(sample(),
                Collections.<NBTTagCompound>emptyList());
        WaitingRewardsNbtCodec.ReadResult read = WaitingRewardsNbtCodec.read(saved);
        assertTrue(read.isReadable());
        assertEquals(sample().all(), read.getRewards().all());
        assertTrue(read.getQuarantined().isEmpty());
        assertEquals("written again it is the same", saved, WaitingRewardsNbtCodec.write(
                read.getRewards(), read.getQuarantined()));
    }

    @Test
    public void nothingSavedReadsAsNothingWaiting() {
        WaitingRewardsNbtCodec.ReadResult read = WaitingRewardsNbtCodec.read(null);
        assertTrue(read.isReadable());
        assertTrue(read.getRewards().isEmpty());
    }

    @Test
    public void aVersionOrShapeItDoesNotWriteFailsClosed() {
        NBTTagCompound newer = WaitingRewardsNbtCodec.write(sample(),
                Collections.<NBTTagCompound>emptyList());
        newer.setInteger(WaitingRewardsNbtCodec.TAG_VERSION,
                WaitingRewardsNbtCodec.DATA_VERSION + 1);
        assertFalse(WaitingRewardsNbtCodec.read(newer).isReadable());

        NBTTagCompound noList = WaitingRewardsNbtCodec.write(sample(),
                Collections.<NBTTagCompound>emptyList());
        noList.setString(WaitingRewardsNbtCodec.TAG_REWARDS, "bread");
        assertFalse(WaitingRewardsNbtCodec.read(noList).isReadable());

        NBTTagCompound noQuarantine = WaitingRewardsNbtCodec.write(sample(),
                Collections.<NBTTagCompound>emptyList());
        noQuarantine.removeTag("Quarantine");
        assertFalse(WaitingRewardsNbtCodec.read(noQuarantine).isReadable());
    }

    @Test
    public void tooManyQuestsFailClosed() {
        NBTTagCompound saved = WaitingRewardsNbtCodec.write(new WaitingRewards(),
                Collections.<NBTTagCompound>emptyList());
        NBTTagList list = new NBTTagList();
        for (int index = 0; index <= WaitingRewards.MAX_QUESTS; index++) {
            list.appendTag(new NBTTagCompound());
        }
        saved.setTag(WaitingRewardsNbtCodec.TAG_REWARDS, list);
        assertFalse(WaitingRewardsNbtCodec.read(saved).isReadable());
    }

    /** An entry that cannot be read is set aside whole; the rest read, and the save keeps it. */
    @Test
    public void anUnreadableEntryIsQuarantined() {
        NBTTagCompound saved = WaitingRewardsNbtCodec.write(sample(),
                Collections.<NBTTagCompound>emptyList());
        NBTTagList list = saved.getTagList(WaitingRewardsNbtCodec.TAG_REWARDS,
                Constants.NBT.TAG_COMPOUND);
        for (NBTTagCompound broken : Arrays.asList(
                entry("losttales:test/a", item("minecraft:bread", 0, 0)),
                entry("losttales:test/b", item("minecraft:bread", -3, 2)),
                entry("losttales:test/c", starterWithMeta()),
                entry("losttales:test/d", item("minecraft:bread", 0, 1),
                        item("minecraft:bread", 0, 2)),
                entry(CACHE, item("minecraft:apple", 0, 1)),
                new NBTTagCompound())) {
            list.appendTag(broken);
        }
        WaitingRewardsNbtCodec.ReadResult read = WaitingRewardsNbtCodec.read(saved);
        assertTrue(read.isReadable());
        assertEquals("the readable entries read", sample().all(), read.getRewards().all());
        assertEquals(6, read.getQuarantined().size());

        WaitingRewardsNbtCodec.ReadResult again = WaitingRewardsNbtCodec.read(
                WaitingRewardsNbtCodec.write(read.getRewards(), read.getQuarantined()));
        assertTrue(again.isReadable());
        assertEquals("the quarantine is kept as it was", read.getQuarantined(),
                new ArrayList<NBTTagCompound>(again.getQuarantined()));
    }

    /** An item the game no longer has is no fault: it waits until the game has it again. */
    @Test
    public void anItemTheGameLacksStillWaits() {
        WaitingRewards rewards = new WaitingRewards();
        rewards.add(CACHE, WaitingReward.item("goneMod:pipeweed", 0, 3));
        WaitingRewardsNbtCodec.ReadResult read = WaitingRewardsNbtCodec.read(
                WaitingRewardsNbtCodec.write(rewards,
                        Collections.<NBTTagCompound>emptyList()));
        assertEquals(rewards.all(), read.getRewards().all());
    }

    private static NBTTagCompound entry(String questId, NBTTagCompound... items) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(WaitingRewardsNbtCodec.TAG_QUEST_ID, questId);
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound item : items) {
            list.appendTag(item);
        }
        entry.setTag(WaitingRewardsNbtCodec.TAG_ITEMS, list);
        return entry;
    }

    private static NBTTagCompound item(String id, int meta, int count) {
        NBTTagCompound item = new NBTTagCompound();
        item.setString(WaitingRewardsNbtCodec.TAG_ITEM, id);
        item.setInteger(WaitingRewardsNbtCodec.TAG_META, meta);
        item.setInteger(WaitingRewardsNbtCodec.TAG_COUNT, count);
        return item;
    }

    private static NBTTagCompound starterWithMeta() {
        NBTTagCompound item = new NBTTagCompound();
        item.setString(WaitingRewardsNbtCodec.TAG_STARTER, "losttales:test/next");
        item.setInteger(WaitingRewardsNbtCodec.TAG_META, 4);
        item.setInteger(WaitingRewardsNbtCodec.TAG_COUNT, 1);
        return item;
    }
}
