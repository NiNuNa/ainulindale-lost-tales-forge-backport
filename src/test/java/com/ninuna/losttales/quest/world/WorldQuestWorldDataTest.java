package com.ninuna.losttales.quest.world;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The world quests kept in the world: a count never passes its goal, a
 * helper's part is what they really added, success leaves each helper who
 * added enough a reward to collect once, and the saved form comes back
 * whole, a newer build's kept read-only, a bad run set aside.
 */
public final class WorldQuestWorldDataTest {
    private static final String QUEST = "losttales:world/greenway";
    private final UUID aldric = UUID.fromString(
            "00000000-0000-0000-0000-00000000a1d1");
    private final UUID borin = UUID.fromString(
            "00000000-0000-0000-0000-00000000b041");

    @Test
    public void aCountStopsAtItsGoalAndAHelperGetsWhatTheyAdded() {
        WorldQuestWorldData data = new WorldQuestWorldData(WorldQuestWorldData.DATA_NAME);
        assertTrue(data.start(QUEST, 100L, 100L + 7 * 24000L));
        assertFalse("running already", data.start(QUEST, 200L, 300L));
        assertEquals(3, data.add(QUEST, "orcs", 3, 5, this.aldric));
        assertEquals("only what the goal leaves", 2,
                data.add(QUEST, "orcs", 4, 5, this.borin));
        assertEquals(0, data.add(QUEST, "orcs", 1, 5, this.borin));
        WorldQuestRun run = data.run(QUEST);
        assertEquals(5, run.getCount("orcs"));
        assertEquals(3, run.getHelped(this.aldric));
        assertEquals(2, run.getHelped(this.borin));
    }

    @Test
    public void successPaysEachHelperWhoAddedEnoughOnce() {
        WorldQuestWorldData data = new WorldQuestWorldData(WorldQuestWorldData.DATA_NAME);
        data.start(QUEST, 0L, 1000L);
        data.add(QUEST, "orcs", 3, 5, this.aldric);
        data.add(QUEST, "orcs", 1, 5, this.borin);
        assertTrue(data.end(QUEST, WorldQuestRun.State.COMPLETED, 500L, 2,
                WorldQuestPayees.EACH_ITS_OWN));
        assertFalse(data.run(QUEST).isRunning());
        assertTrue(data.hasRewards(this.aldric));
        assertFalse("added less than the least", data.hasRewards(this.borin));
        List<String> taken = data.takeRewards(this.aldric);
        assertEquals(1, taken.size());
        assertTrue(data.takeRewards(this.aldric).isEmpty());
        assertEquals("an ended quest counts nothing", 0,
                data.add(QUEST, "orcs", 1, 5, this.aldric));
    }

    @Test
    public void aQuestWonTwiceBeforeItsHelperCollectsPaysTwice() {
        WorldQuestWorldData data = new WorldQuestWorldData(
                WorldQuestWorldData.DATA_NAME);
        data.start(QUEST, 0L, 1000L);
        data.add(QUEST, "orcs", 5, 5, this.aldric);
        data.end(QUEST, WorldQuestRun.State.COMPLETED, 100L, 1,
                WorldQuestPayees.EACH_ITS_OWN);
        data.start(QUEST, 200L, 1200L);
        data.add(QUEST, "orcs", 5, 5, this.aldric);
        data.end(QUEST, WorldQuestRun.State.COMPLETED, 300L, 1,
                WorldQuestPayees.EACH_ITS_OWN);
        assertEquals(2, data.takeRewards(this.aldric).size());
    }

    /**
     * One account playing three characters is paid once, for the
     * character that added most; another account's character is paid
     * too, and a character short of the least is not.
     */
    @Test
    public void aWinPaysEachAccountOnceForItsLargestPart() {
        final UUID account = UUID.fromString(
                "00000000-0000-0000-0000-0000000acc01");
        final UUID first = UUID.fromString(
                "00000000-0000-0000-0000-00000000c001");
        final UUID second = UUID.fromString(
                "00000000-0000-0000-0000-00000000c002");
        WorldQuestPayees.Accounts accounts = new WorldQuestPayees.Accounts() {
            @Override
            public UUID accountOf(UUID identity) {
                return identity.equals(first) || identity.equals(second)
                        ? account : identity;
            }
        };
        WorldQuestWorldData data = new WorldQuestWorldData(
                WorldQuestWorldData.DATA_NAME);
        data.start(QUEST, 0L, 1000L);
        data.add(QUEST, "orcs", 3, 50, first);
        data.add(QUEST, "orcs", 5, 50, second);
        data.add(QUEST, "orcs", 4, 50, account);
        data.add(QUEST, "orcs", 2, 50, this.borin);
        data.add(QUEST, "orcs", 1, 50, this.aldric);
        assertTrue(data.end(QUEST, WorldQuestRun.State.COMPLETED, 500L, 2,
                accounts));
        assertTrue("the account's largest part", data.hasRewards(second));
        assertFalse(data.hasRewards(first));
        assertFalse("the account's own identity is one of its three",
                data.hasRewards(account));
        assertTrue("another account", data.hasRewards(this.borin));
        assertFalse("short of the least", data.hasRewards(this.aldric));
        WorldQuestRun run = data.run(QUEST);
        assertEquals("parts stay counted per character", 3,
                run.getHelped(first));
    }

    @Test
    public void aTieGoesToTheIdentityThatJoinedFirst() {
        UUID account = UUID.fromString("00000000-0000-0000-0000-0000000acc02");
        java.util.Map<UUID, Integer> helpers =
                new java.util.LinkedHashMap<UUID, Integer>();
        helpers.put(this.borin, Integer.valueOf(4));
        helpers.put(this.aldric, Integer.valueOf(4));
        final UUID owner = account;
        List<UUID> paid = WorldQuestPayees.of(helpers, 1,
                new WorldQuestPayees.Accounts() {
                    @Override
                    public UUID accountOf(UUID identity) {
                        return owner;
                    }
                });
        assertEquals(java.util.Collections.singletonList(this.borin), paid);
        assertEquals("every identity its own account", 2, WorldQuestPayees.of(
                helpers, 1, WorldQuestPayees.EACH_ITS_OWN).size());
    }

    @Test
    public void aRunCountsNoMoreObjectivesThanTheWorldKeeps() {
        WorldQuestWorldData data = new WorldQuestWorldData(
                WorldQuestWorldData.DATA_NAME);
        data.start(QUEST, 0L, 1000L);
        for (int index = 0; index < WorldQuestNbtCodec.MAX_COUNTS; index++) {
            assertEquals(1, data.add(QUEST, "o" + index, 1, 5, this.aldric));
        }
        assertEquals("one past what the world keeps", 0,
                data.add(QUEST, "one-more", 1, 5, this.aldric));
        assertEquals("a kept objective still counts", 1,
                data.add(QUEST, "o0", 1, 5, this.aldric));
        assertEquals(2, data.count(QUEST, "o0"));
        assertEquals(1, data.runningQuestIds(500L).size());
        assertTrue("past its days", data.runningQuestIds(1000L).isEmpty());
    }

    @Test
    public void aFailedOrStoppedQuestPaysNobody() {
        WorldQuestWorldData data = new WorldQuestWorldData(WorldQuestWorldData.DATA_NAME);
        data.start(QUEST, 0L, 1000L);
        data.add(QUEST, "orcs", 5, 5, this.aldric);
        assertFalse("a win is ended only with who owns whom",
                data.end(QUEST, WorldQuestRun.State.COMPLETED, 1000L));
        assertTrue(data.end(QUEST, WorldQuestRun.State.FAILED, 1000L));
        assertFalse(data.hasRewards(this.aldric));
        assertTrue("an ended quest starts again", data.start(QUEST, 2000L,
                3000L));
        assertEquals(0, data.run(QUEST).getCount("orcs"));
    }

    @Test
    public void theSavedFormComesBackWhole() {
        WorldQuestWorldData data = new WorldQuestWorldData(WorldQuestWorldData.DATA_NAME);
        data.start(QUEST, 10L, 20L);
        data.add(QUEST, "orcs", 2, 5, this.aldric);
        data.start("losttales:world/other", 30L, 40L);
        data.add("losttales:world/other", "arrows", 5, 5, this.borin);
        data.end("losttales:world/other", WorldQuestRun.State.COMPLETED, 35L,
                1, WorldQuestPayees.EACH_ITS_OWN);
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);

        WorldQuestWorldData read = new WorldQuestWorldData(WorldQuestWorldData.DATA_NAME);
        read.readFromNBT(saved);
        assertEquals(2, read.runs().size());
        assertEquals(2, read.run(QUEST).getCount("orcs"));
        assertEquals(2, read.run(QUEST).getHelped(this.aldric));
        assertEquals(20L, read.run(QUEST).getEndsAt());
        assertEquals(WorldQuestRun.State.COMPLETED,
                read.run("losttales:world/other").getState());
        assertTrue(read.hasRewards(this.borin));
        assertFalse(read.isReadOnly());
    }

    @Test
    public void aNewerBuildsStoreIsKeptWholeAndReadOnly() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion", WorldQuestNbtCodec.CURRENT_DATA_VERSION
                + 1);
        newer.setString("Future", "kept");
        WorldQuestWorldData data = new WorldQuestWorldData(WorldQuestWorldData.DATA_NAME);
        data.readFromNBT(newer);
        assertTrue(data.isReadOnly());
        assertFalse(data.start(QUEST, 0L, 1L));
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals("kept", written.getString("Future"));
    }

    @Test
    public void aRunThatCannotBeReadIsSetAsideNotDropped() {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("DataVersion", WorldQuestNbtCodec.CURRENT_DATA_VERSION);
        NBTTagList runs = new NBTTagList();
        NBTTagCompound broken = new NBTTagCompound();
        broken.setString("QuestId", QUEST);
        broken.setString("State", "SOMEDAY");
        runs.appendTag(broken);
        saved.setTag("Runs", runs);
        WorldQuestWorldData data = new WorldQuestWorldData(WorldQuestWorldData.DATA_NAME);
        data.readFromNBT(saved);
        assertNull(data.run(QUEST));
        assertFalse(data.isReadOnly());
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(1, written.getCompoundTag("Quarantine")
                .getTagList("Entries", 10).tagCount());
    }
}
