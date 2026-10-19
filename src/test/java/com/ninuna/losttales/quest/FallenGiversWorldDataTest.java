package com.ninuna.losttales.quest;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The world's record of quest givers killed (QUEST-32): it outlasts a
 * save, so a player away when their giver fell sees the quest fail at
 * their next login, holds its bound, and fails closed on data it cannot
 * read.
 */
public final class FallenGiversWorldDataTest {
    private static final String NIA = "8d6b2a51-0a4c-4a39-9a3a-1b7c2a0b6c11";

    private static FallenGiversWorldData data() {
        return new FallenGiversWorldData(FallenGiversWorldData.DATA_NAME);
    }

    @Test
    public void aFallenGiverIsRememberedOverASave() {
        FallenGiversWorldData data = data();
        assertFalse(data.hasFallen(NIA));
        assertTrue(data.record(NIA, 500L));
        assertFalse("only an entity id is kept", data.record("Nia", 500L));
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        FallenGiversWorldData read = data();
        read.readFromNBT(tag);
        assertTrue(read.isUsable());
        assertTrue(read.hasFallen(NIA));
    }

    @Test
    public void unreadableDataIsKeptAndNothingMoreRecorded() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger(FallenGiversWorldData.TAG_VERSION, 9);
        FallenGiversWorldData data = data();
        data.readFromNBT(newer);
        assertFalse(data.isUsable());
        assertFalse(data.record(NIA, 1L));
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(newer, written);
    }
}
