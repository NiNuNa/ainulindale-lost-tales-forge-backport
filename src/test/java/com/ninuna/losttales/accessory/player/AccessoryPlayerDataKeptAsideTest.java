package com.ninuna.losttales.accessory.player;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A worn ring the slot cannot take as the player's data is read is kept
 * aside even when sixteen are kept aside already (ACC-07); a list past the
 * safety bound is kept whole. No item is in the game while the tests run,
 * so every worn ring here is one the slot cannot take.
 */
public final class AccessoryPlayerDataKeptAsideTest {

    @Test
    public void aWornRingIsKeptPastSixteen() throws Exception {
        AccessoryPlayerData data = attached();
        data.loadNBTData(saved(AccessoryPlayerData.MAX_REJECTED_ENTRIES, true));

        assertEquals(AccessoryPlayerData.MAX_REJECTED_ENTRIES + 1,
                data.getRejectedEntries().size());
        assertEquals("the worn ring is the last kept", 7,
                data.getRejectedEntries().get(AccessoryPlayerData.MAX_REJECTED_ENTRIES)
                        .getShort("id"));
        assertNull(data.getInventory().getStackInSlot(AccessoryInventory.RING_SLOT));
        assertTrue(data.hasRejectedEntry());
    }

    @Test
    public void everyRingKeptAsideIsReadUpToTheBound() throws Exception {
        AccessoryPlayerData data = attached();
        data.loadNBTData(saved(AccessoryPlayerData.MAX_KEPT_ENTRIES, false));

        assertEquals(AccessoryPlayerData.MAX_KEPT_ENTRIES, data.getRejectedEntries().size());
    }

    @Test
    public void aListPastTheBoundIsKeptWhole() throws Exception {
        AccessoryPlayerData data = attached();
        data.loadNBTData(saved(AccessoryPlayerData.MAX_KEPT_ENTRIES + 1, true));

        assertEquals(1, data.getRejectedEntries().size());
        assertTrue("the data whole, the worn ring in it",
                data.getRejectedEntries().get(0).hasKey("Equipped"));
    }

    private static NBTTagCompound saved(int keptAside, boolean worn) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("Version", 2);
        NBTTagList rejected = new NBTTagList();
        for (int index = 0; index < keptAside; index++) {
            rejected.appendTag(ring(3));
        }
        data.setTag("Rejected", rejected);
        if (worn) {
            data.setTag("Equipped", ring(7));
        }
        NBTTagCompound player = new NBTTagCompound();
        player.setTag(AccessoryPlayerData.PROPERTY_ID, data);
        return player;
    }

    private static NBTTagCompound ring(int id) {
        NBTTagCompound ring = new NBTTagCompound();
        ring.setShort("id", (short)id);
        ring.setByte("Count", (byte)1);
        return ring;
    }

    private static AccessoryPlayerData attached() throws Exception {
        EntityPlayerMP player = allocate(EntityPlayerMP.class);
        AccessoryPlayerData data = new AccessoryPlayerData();
        data.init(player, null);
        return data;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe)unsafeField.get(null);
        return type.cast(unsafe.allocateInstance(type));
    }
}
