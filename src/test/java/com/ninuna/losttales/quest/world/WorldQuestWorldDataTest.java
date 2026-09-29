package com.ninuna.losttales.quest.world;

import java.util.Set;
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
        WorldQuestWorldData data = new WorldQuestWorldData();
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
        WorldQuestWorldData data = new WorldQuestWorldData();
        data.start(QUEST, 0L, 1000L);
        data.add(QUEST, "orcs", 3, 5, this.aldric);
        data.add(QUEST, "orcs", 1, 5, this.borin);
        assertTrue(data.end(QUEST, WorldQuestRun.State.COMPLETED, 500L, 2));
        assertFalse(data.run(QUEST).isRunning());
        assertTrue(data.hasRewards(this.aldric));
        assertFalse("added less than the least", data.hasRewards(this.borin));
        Set<String> taken = data.takeRewards(this.aldric);
        assertEquals(1, taken.size());
        assertTrue(data.takeRewards(this.aldric).isEmpty());
        assertEquals("an ended quest counts nothing", 0,
                data.add(QUEST, "orcs", 1, 5, this.aldric));
    }

    @Test
    public void aFailedOrStoppedQuestPaysNobody() {
        WorldQuestWorldData data = new WorldQuestWorldData();
        data.start(QUEST, 0L, 1000L);
        data.add(QUEST, "orcs", 5, 5, this.aldric);
        assertTrue(data.end(QUEST, WorldQuestRun.State.FAILED, 1000L, 1));
        assertFalse(data.hasRewards(this.aldric));
        assertTrue("an ended quest starts again", data.start(QUEST, 2000L,
                3000L));
        assertEquals(0, data.run(QUEST).getCount("orcs"));
    }

    @Test
    public void theSavedFormComesBackWhole() {
        WorldQuestWorldData data = new WorldQuestWorldData();
        data.start(QUEST, 10L, 20L);
        data.add(QUEST, "orcs", 2, 5, this.aldric);
        data.start("losttales:world/other", 30L, 40L);
        data.add("losttales:world/other", "arrows", 5, 5, this.borin);
        data.end("losttales:world/other", WorldQuestRun.State.COMPLETED, 35L,
                1);
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);

        WorldQuestWorldData read = new WorldQuestWorldData();
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
        WorldQuestWorldData data = new WorldQuestWorldData();
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
        WorldQuestWorldData data = new WorldQuestWorldData();
        data.readFromNBT(saved);
        assertNull(data.run(QUEST));
        assertFalse(data.isReadOnly());
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(1, written.getCompoundTag("Quarantine")
                .getTagList("Entries", 10).tagCount());
    }
}
