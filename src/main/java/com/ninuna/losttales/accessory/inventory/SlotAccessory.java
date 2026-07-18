package com.ninuna.losttales.accessory.inventory;

import com.ninuna.losttales.accessory.player.AccessoryInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Validated one-item accessory slot. */
public final class SlotAccessory extends Slot {

    private final EntityPlayer owner;

    public SlotAccessory(AccessoryInventory inventory,
                         EntityPlayer owner,
                         int x,
                         int y) {
        super(inventory, AccessoryInventory.RING_SLOT, x, y);
        this.owner = owner;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return ((AccessoryInventory)this.inventory)
                .isItemValidForSlot(getSlotIndex(), stack);
    }

    @Override
    public int getSlotStackLimit() {
        return 1;
    }

    @Override
    public boolean canTakeStack(EntityPlayer player) {
        return player == this.owner && super.canTakeStack(player);
    }
}
