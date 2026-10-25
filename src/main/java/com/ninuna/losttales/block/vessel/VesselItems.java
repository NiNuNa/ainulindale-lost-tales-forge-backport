package com.ninuna.losttales.block.vessel;

import com.ninuna.losttales.item.block.LostTalesItemBlockUrn;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

/**
 * Vessel items as the running game has them (CONTENT-09): the items the
 * game knows by id, a vessel item made with what it holds, and the stacks
 * a vessel item's checked contents read back as, slot by slot.
 */
public final class VesselItems {
    /** The game's own items. */
    public static final VesselContents.Items GAME = new VesselContents.Items() {
        @Override
        public int stackLimit(int itemId, int damage) {
            Item item = Item.getItemById(itemId);
            return item == null ? 0 : Math.max(1, new ItemStack(item, 1, damage).getMaxStackSize());
        }

        @Override
        public boolean isVessel(int itemId) {
            return Item.getItemById(itemId) instanceof LostTalesItemBlockUrn;
        }
    };

    private VesselItems() {}

    /** Whether the stack is a vessel holding items, which no vessel takes in. */
    public static boolean isFilledVessel(ItemStack stack) {
        return stack != null && stack.getItem() instanceof LostTalesItemBlockUrn
                && VesselContents.holdsItems(stack.getTagCompound());
    }

    /** One vessel item of {@code block} holding {@code items}, as its block entity saves them. */
    public static ItemStack filled(Block block, NBTTagList items) {
        ItemStack stack = new ItemStack(block, 1, 0);
        stack.setTagCompound(VesselContents.write(null, items));
        return stack;
    }

    /**
     * The stacks a vessel item holds, checked as a vessel of {@code slots}
     * slots takes them: one entry per slot, null for an empty one.
     */
    public static ItemStack[] slots(ItemStack vessel, int slots) {
        ItemStack[] bySlot = new ItemStack[Math.max(0, slots)];
        if (vessel == null) {
            return bySlot;
        }
        NBTTagList items = VesselContents.read(vessel.getTagCompound(), slots, GAME);
        for (int index = 0; index < items.tagCount(); index++) {
            int slot = items.getCompoundTagAt(index).getByte("Slot") & 255;
            ItemStack stack = ItemStack.loadItemStackFromNBT(items.getCompoundTagAt(index));
            if (stack != null && stack.getItem() != null && slot < bySlot.length) {
                bySlot[slot] = stack;
            }
        }
        return bySlot;
    }
}
