package com.ninuna.losttales.quest;

import com.ninuna.losttales.storage.NbtQuarantine;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The world's record of quest givers killed (QUEST-32): it outlasts a
 * save, so a player away when their giver fell sees the quest fail at
 * their next login, holds its bound, sets aside what it cannot read in
 * the shared quarantine (DATA-10), and fails closed on data it cannot
 * read.
 */
public final class FallenGiversWorldDataTest {
    private static final String NIA = "8d6b2a51-0a4c-4a39-9a3a-1b7c2a0b6c11";
    private static final String BREG = "1f0e7c2d-3b4a-4c5d-8e9f-a0b1c2d3e4f5";

    private static FallenGiversWorldData data() {
        return new FallenGiversWorldData(FallenGiversWorldData.DATA_NAME);
    }

    @Test
    public void aFallenGiverIsRememberedOverASave() {
        FallenGiversWorldData data = data();
        assertFalse(data.hasFallen(NIA));
        assertTrue(data.record(NIA));
        assertFalse("only an entity id is kept", data.record("Nia"));
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        FallenGiversWorldData read = data();
        read.readFromNBT(tag);
        assertTrue(read.hasFallen(NIA));
        assertTrue("a store read whole records more", read.record(BREG));
        assertTrue(read.hasFallen(BREG));
    }

    /** Past its bound the oldest giver goes, the newest stays. */
    @Test
    public void theRecordKeepsTheLatestGivers() {
        FallenGiversWorldData data = data();
        assertTrue(data.record(NIA));
        for (int index = 0; index < FallenGiversWorldData.MAX_ENTRIES; index++) {
            assertTrue(data.record(String.format(
                    "00000000-0000-4000-8000-%012d", index)));
        }
        assertFalse("the oldest went", data.hasFallen(NIA));
        assertTrue(data.hasFallen(String.format("00000000-0000-4000-8000-%012d",
                FallenGiversWorldData.MAX_ENTRIES - 1)));
    }

    @Test
    public void unreadableDataIsKeptAndNothingMoreRecorded() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger(FallenGiversWorldData.TAG_VERSION,
                FallenGiversWorldData.DATA_VERSION + 1);
        FallenGiversWorldData data = data();
        data.readFromNBT(newer);
        assertFalse(data.record(NIA));
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(newer, written);
    }

    /** An entry that cannot be read, and a second for one giver, are set aside whole; the rest still read. */
    @Test
    public void aBrokenEntryIsQuarantined() {
        FallenGiversWorldData data = data();
        data.record(NIA);
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        NBTTagList fallen = tag.getTagList(FallenGiversWorldData.TAG_FALLEN, 10);
        NBTTagCompound broken = new NBTTagCompound();
        broken.setString(FallenGiversWorldData.TAG_ENTITY_ID, "Nia");
        fallen.appendTag(broken);
        NBTTagCompound twice = new NBTTagCompound();
        twice.setString(FallenGiversWorldData.TAG_ENTITY_ID, NIA);
        fallen.appendTag(twice);

        FallenGiversWorldData read = data();
        read.readFromNBT(tag);
        assertTrue(read.hasFallen(NIA));
        assertTrue("still records", read.record(BREG));
        NBTTagCompound again = new NBTTagCompound();
        read.writeToNBT(again);
        List<NBTTagCompound> aside = NbtQuarantine.read(again).getEntries();
        assertEquals(2, aside.size());
        assertEquals("unreadable_entity_id", aside.get(0).getString("Reason"));
        assertEquals(1, aside.get(0).getInteger(FallenGiversWorldData.TAG_FALLEN_INDEX));
        assertEquals(broken, aside.get(0).getCompoundTag("OriginalData"));
        assertEquals("duplicate_giver", aside.get(1).getString("Reason"));
    }

    /** A quarantine that would pass its bound keeps the store as it was, recording nothing. */
    @Test
    public void aQuarantinePastItsBoundKeepsTheStoreAsItWas() {
        NBTTagCompound tag = new NBTTagCompound();
        data().writeToNBT(tag);
        NBTTagList fallen = tag.getTagList(FallenGiversWorldData.TAG_FALLEN, 10);
        // The list holds at most the store's own bound, so the quarantine
        // already saved carries the rest past the quarantine's.
        for (int index = 0; index < FallenGiversWorldData.MAX_ENTRIES; index++) {
            fallen.appendTag(new NBTTagCompound());
        }
        List<NBTTagCompound> stored = new java.util.ArrayList<NBTTagCompound>();
        stored.add(NbtQuarantine.entry("unreadable_entity_id",
                FallenGiversWorldData.TAG_FALLEN_INDEX, 0, new NBTTagCompound()));
        NbtQuarantine.write(tag, stored);
        FallenGiversWorldData data = data();
        data.readFromNBT(tag);
        assertFalse(data.record(NIA));
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(tag, written);
    }
}
