package com.ninuna.losttales.block.vessel;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A vessel item carries what it holds in its NBT, and placing it reads
 * that back bounded and checked, never trusted (CONTENT-09).
 */
public final class VesselContentsTest {
    private static final int STONE = 1;
    private static final int PEARL = 368;
    private static final int SWORD = 267;
    private static final int VESSEL = 4100;
    /** Stone stacks to 64, a pearl to 16, a sword to 1, a vessel to 16; nothing else exists. */
    private static final VesselContents.Items ITEMS = new VesselContents.Items() {
        @Override
        public int stackLimit(int itemId, int damage) {
            switch (itemId) {
                case STONE: return 64;
                case PEARL: return 16;
                case SWORD: return 1;
                case VESSEL: return 16;
                default: return 0;
            }
        }

        @Override
        public boolean isVessel(int itemId) {
            return itemId == VESSEL;
        }
    };

    /** What the block entity writes comes back whole. */
    @Test
    public void contentsRoundTrip() {
        NBTTagList items = new NBTTagList();
        items.appendTag(item(0, STONE, 30, 0));
        NBTTagCompound named = item(1, SWORD, 1, 12);
        NBTTagCompound swordTag = new NBTTagCompound();
        swordTag.setTag("display", new NBTTagCompound());
        named.setTag("tag", swordTag);
        items.appendTag(named);

        NBTTagCompound stackTag = VesselContents.write(null, items);
        assertTrue(VesselContents.holdsItems(stackTag));
        NBTTagList read = VesselContents.read(stackTag, 2, ITEMS);

        assertEquals(items.toString(), read.toString());
        assertEquals(2, read.tagCount());
        assertEquals(12, read.getCompoundTagAt(1).getShort("Damage"));
        assertTrue(read.getCompoundTagAt(1).hasKey("tag", 10));
    }

    @Test
    public void anEmptyVesselCarriesNothing() {
        assertFalse(VesselContents.holdsItems(null));
        assertFalse(VesselContents.holdsItems(new NBTTagCompound()));
        NBTTagCompound stackTag = VesselContents.write(null, new NBTTagList());
        assertFalse(VesselContents.holdsItems(stackTag));
        assertFalse(stackTag.hasKey(VesselContents.TAG));
        NBTTagCompound filled = VesselContents.write(null, listOf(item(0, STONE, 1, 0)));
        assertFalse("emptied, the key goes",
                VesselContents.write(filled, null).hasKey(VesselContents.TAG));
        assertEquals(0, VesselContents.read(null, 4, ITEMS).tagCount());
    }

    /** A count past what one stack holds is held to it; none or less is left out. */
    @Test
    public void theCountIsBounded() {
        NBTTagList read = VesselContents.read(VesselContents.write(null, listOf(
                item(0, STONE, 120, 0), item(1, PEARL, 64, 0), item(2, SWORD, 5, 0),
                item(3, STONE, 0, 0))), 4, ITEMS);

        assertEquals(3, read.tagCount());
        assertEquals(64, read.getCompoundTagAt(0).getByte("Count"));
        assertEquals(16, read.getCompoundTagAt(1).getByte("Count"));
        assertEquals(1, read.getCompoundTagAt(2).getByte("Count"));
    }

    /** An entry of no item, in no slot of the vessel's, or in a slot taken already is left out. */
    @Test
    public void malformedEntriesAreLeftOut() {
        NBTTagCompound noCount = item(1, STONE, 1, 0);
        noCount.removeTag("Count");
        NBTTagCompound badTag = item(1, STONE, 1, 0);
        badTag.setTag("tag", new NBTTagString("text"));
        NBTTagList read = VesselContents.read(VesselContents.write(null, listOf(
                item(0, 9999, 1, 0), item(5, STONE, 1, 0), item(0, STONE, 2, -1),
                noCount, badTag, item(1, STONE, 3, 0), item(1, STONE, 4, 0))), 2, ITEMS);

        assertEquals(1, read.tagCount());
        assertEquals(1, read.getCompoundTagAt(0).getByte("Slot"));
        assertEquals(3, read.getCompoundTagAt(0).getByte("Count"));
    }

    /** No more entries than the vessel has slots, however many the list holds. */
    @Test
    public void noMoreThanTheVesselsSlots() {
        NBTTagList many = new NBTTagList();
        for (int slot = 0; slot < 200; slot++) {
            many.appendTag(item(slot % 4, STONE, 1, 0));
        }
        assertEquals(2, VesselContents.read(VesselContents.write(null, many), 2, ITEMS).tagCount());
        assertEquals(4, VesselContents.read(VesselContents.write(null, many), 4, ITEMS).tagCount());
        assertEquals(0, VesselContents.read(VesselContents.write(null, many), 0, ITEMS).tagCount());
    }

    /** A vessel holding items never rides inside another; an empty one may. */
    @Test
    public void aFilledVesselIsLeftOut() {
        NBTTagCompound filledVessel = item(0, VESSEL, 1, 0);
        filledVessel.setTag("tag", VesselContents.write(null, listOf(item(0, STONE, 1, 0))));
        NBTTagList read = VesselContents.read(VesselContents.write(null, listOf(
                filledVessel, item(1, VESSEL, 3, 0))), 2, ITEMS);

        assertEquals(1, read.tagCount());
        assertEquals(VESSEL, read.getCompoundTagAt(0).getShort("id"));
        assertEquals(1, read.getCompoundTagAt(0).getByte("Slot"));
    }

    private static NBTTagCompound item(int slot, int id, int count, int damage) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Slot", (byte) slot);
        tag.setShort("id", (short) id);
        tag.setByte("Count", (byte) count);
        tag.setShort("Damage", (short) damage);
        return tag;
    }

    private static NBTTagList listOf(NBTTagCompound... entries) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound entry : entries) {
            list.appendTag(entry);
        }
        return list;
    }
}
