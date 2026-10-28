package com.ninuna.losttales.accessory.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A hotbar number key over the empty ring slot moves one ring and leaves
 * the rest (ACC-06). The ring slot here holds one item and refuses more,
 * as the accessory inventory does.
 */
public final class LostTalesContainerPlayerHotbarKeyTest {

    private static final Item RING = new Item();
    private static final Item STONE = new Item();

    @Test
    public void aStackOfRingsGivesOneAndKeepsTheRest() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        ItemStack rings = new ItemStack(RING, 5);
        inventory.setInventorySlotContents(3, rings);
        Slot ring = ringSlot();

        assertTrue(LostTalesContainerPlayer.moveOneFromHotbar(ring, inventory, 3, null));

        assertEquals(1, ring.getStack().stackSize);
        assertSame(RING, ring.getStack().getItem());
        assertEquals(4, inventory.getStackInSlot(3).stackSize);
    }

    @Test
    public void aSingleRingLeavesItsHotbarSlotEmpty() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        inventory.setInventorySlotContents(0, new ItemStack(RING, 1));
        Slot ring = ringSlot();

        assertTrue(LostTalesContainerPlayer.moveOneFromHotbar(ring, inventory, 0, null));

        assertEquals(1, ring.getStack().stackSize);
        assertNull(inventory.getStackInSlot(0));
    }

    @Test
    public void aWornRingIsLeftToVanilla() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        inventory.setInventorySlotContents(2, new ItemStack(RING, 3));
        Slot ring = ringSlot();
        ring.putStack(new ItemStack(RING, 1));

        assertFalse(LostTalesContainerPlayer.moveOneFromHotbar(ring, inventory, 2, null));

        assertEquals(3, inventory.getStackInSlot(2).stackSize);
    }

    @Test
    public void whatTheSlotRefusesStaysWhereItIs() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        inventory.setInventorySlotContents(1, new ItemStack(STONE, 7));
        Slot ring = ringSlot();

        assertFalse(LostTalesContainerPlayer.moveOneFromHotbar(ring, inventory, 1, null));

        assertNull(ring.getStack());
        assertEquals(7, inventory.getStackInSlot(1).stackSize);
    }

    @Test
    public void onlyAHotbarKeyMovesARing() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        inventory.setInventorySlotContents(9, new ItemStack(RING, 2));
        Slot ring = ringSlot();

        assertFalse(LostTalesContainerPlayer.moveOneFromHotbar(ring, inventory, 9, null));
        assertFalse(LostTalesContainerPlayer.moveOneFromHotbar(ring, inventory, -1, null));
        assertFalse(LostTalesContainerPlayer.moveOneFromHotbar(ring, inventory, 4, null));

        assertNull(ring.getStack());
        assertEquals(2, inventory.getStackInSlot(9).stackSize);
    }

    @Test
    public void aSlotThatCannotBeTakenFromTakesNothing() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        inventory.setInventorySlotContents(5, new ItemStack(RING, 2));
        Slot ring = new Slot(oneRing(), 0, 0, 0) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() == RING;
            }

            @Override
            public boolean canTakeStack(EntityPlayer player) {
                return false;
            }
        };

        assertFalse(LostTalesContainerPlayer.moveOneFromHotbar(ring, inventory, 5, null));
        assertEquals(2, inventory.getStackInSlot(5).stackSize);
    }

    /** A ring slot that takes only the ring, and only one at a time. */
    private static Slot ringSlot() {
        return new Slot(oneRing(), 0, 0, 0) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() == RING;
            }
        };
    }

    private static InventoryBasic oneRing() {
        return new InventoryBasic("ring", false, 1) {
            @Override
            public void setInventorySlotContents(int slot, ItemStack stack) {
                if (stack == null || stack.stackSize == 1) {
                    super.setInventorySlotContents(slot, stack);
                }
            }
        };
    }
}
