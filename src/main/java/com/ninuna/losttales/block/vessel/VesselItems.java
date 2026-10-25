package com.ninuna.losttales.block.vessel;

import com.ninuna.losttales.item.block.LostTalesItemBlockUrn;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

/**
 * Vessel items as the running game has them (CONTENT-09): the items the
 * game knows by id, a vessel item made with what it holds, and the stacks
 * a vessel item's checked contents read back as.
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

    /** The stacks a vessel item holds, checked as a vessel of {@code slots} slots takes them, in slot order. */
    public static List<ItemStack> contents(ItemStack vessel, int slots) {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        if (vessel == null) {
            return stacks;
        }
        NBTTagList items = VesselContents.read(vessel.getTagCompound(), slots, GAME);
        ItemStack[] bySlot = new ItemStack[Math.max(0, slots)];
        for (int index = 0; index < items.tagCount(); index++) {
            int slot = items.getCompoundTagAt(index).getByte("Slot") & 255;
            ItemStack stack = ItemStack.loadItemStackFromNBT(items.getCompoundTagAt(index));
            if (stack != null && stack.getItem() != null && slot < bySlot.length) {
                bySlot[slot] = stack;
            }
        }
        for (ItemStack stack : bySlot) {
            if (stack != null) {
                stacks.add(stack);
            }
        }
        return stacks;
    }
}
