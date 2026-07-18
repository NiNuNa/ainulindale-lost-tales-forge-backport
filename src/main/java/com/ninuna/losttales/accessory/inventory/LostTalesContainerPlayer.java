package com.ninuna.losttales.accessory.inventory;

import com.ninuna.losttales.accessory.player.AccessoryInventory;
import com.ninuna.losttales.accessory.player.AccessoryPlayerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

/** Vanilla player container with one appended, server-validated ring slot. */
public final class LostTalesContainerPlayer extends ContainerPlayer {

    public static final int VANILLA_SLOT_COUNT = 45;
    public static final int ACCESSORY_SLOT_INDEX = 45;
    public static final int TOTAL_SLOT_COUNT = 46;
    public static final int ACCESSORY_X = 77;
    public static final int ACCESSORY_Y = 62;

    private final EntityPlayer owner;

    public LostTalesContainerPlayer(InventoryPlayer inventory,
                                    boolean localWorld,
                                    EntityPlayer player) {
        super(inventory, localWorld, player);
        this.owner = player;
        AccessoryPlayerData data = AccessoryPlayerData.getOrCreate(player);
        if (data == null || data.getInventory() == null) {
            throw new IllegalStateException(
                    "Lost Tales accessory data was not attached before player container construction");
        }
        addSlotToContainer(new SlotAccessory(
                data.getInventory(), player, ACCESSORY_X, ACCESSORY_Y));
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (player != this.owner || index < 0
                || index >= this.inventorySlots.size()) {
            return null;
        }
        Slot slot = (Slot)this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }

        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        boolean moved;

        if (index == 0) {
            moved = mergeItemStack(source, 9, VANILLA_SLOT_COUNT, true);
            if (moved) {
                slot.onSlotChange(source, original);
            }
        } else if (index >= 1 && index < 9) {
            moved = mergeItemStack(source, 9, VANILLA_SLOT_COUNT, false);
        } else if (index == ACCESSORY_SLOT_INDEX) {
            moved = mergeItemStack(source, 9, VANILLA_SLOT_COUNT, false);
        } else if (index >= 9 && index < VANILLA_SLOT_COUNT
                && moveOneIntoAccessory(source)) {
            moved = true;
        } else if (source.getItem() instanceof ItemArmor
                && !((Slot)this.inventorySlots.get(
                5 + ((ItemArmor)source.getItem()).armorType)).getHasStack()) {
            int armorSlot = 5 + ((ItemArmor)source.getItem()).armorType;
            moved = mergeItemStack(source, armorSlot, armorSlot + 1, false);
        } else if (index >= 9 && index < 36) {
            moved = mergeItemStack(source, 36, VANILLA_SLOT_COUNT, false);
        } else if (index >= 36 && index < VANILLA_SLOT_COUNT) {
            moved = mergeItemStack(source, 9, 36, false);
        } else {
            moved = mergeItemStack(source, 9, VANILLA_SLOT_COUNT, false);
        }

        if (!moved) {
            return null;
        }
        if (source.stackSize <= 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        if (source.stackSize == original.stackSize) {
            return null;
        }
        slot.onPickupFromSlot(player, source);
        return original;
    }

    private boolean moveOneIntoAccessory(ItemStack source) {
        Slot accessory = (Slot)this.inventorySlots.get(ACCESSORY_SLOT_INDEX);
        if (source == null || source.stackSize <= 0
                || accessory.getHasStack()
                || !accessory.isItemValid(source)) {
            return false;
        }
        ItemStack equipped = source.copy();
        equipped.stackSize = 1;
        accessory.putStack(equipped);
        accessory.onSlotChanged();
        source.stackSize--;
        return true;
    }
}
