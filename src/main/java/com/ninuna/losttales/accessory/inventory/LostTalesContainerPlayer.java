package com.ninuna.losttales.accessory.inventory;

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
    /** The click mode of a hotbar number key over a slot. */
    private static final int HOTBAR_SWAP = 2;

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

    /**
     * A hotbar number key over the empty ring slot moves one ring in and
     * leaves the rest in the hotbar, as shift-click does (ACC-06); vanilla
     * would hand the slot the whole stack, which it refuses unseen. Both
     * sides answer the same, so a changed client changes nothing.
     */
    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (mode == HOTBAR_SWAP && slotId == ACCESSORY_SLOT_INDEX
                && player == this.owner
                && moveOneFromHotbar((Slot)this.inventorySlots.get(ACCESSORY_SLOT_INDEX),
                player.inventory, button, player)) {
            return null;
        }
        return super.slotClick(slotId, button, mode, player);
    }

    /**
     * Moves one item of hotbar slot {@code hotbar} into the empty
     * {@code ring} slot, when the key names a hotbar slot, the ring slot is
     * empty and may be taken from, and it takes the item. Answers whether
     * it moved one; when not, nothing is touched and vanilla's own
     * handling stands.
     */
    static boolean moveOneFromHotbar(Slot ring, InventoryPlayer inventory,
                                     int hotbar, EntityPlayer player) {
        if (ring == null || inventory == null || hotbar < 0
                || hotbar >= InventoryPlayer.getHotbarSize()
                || ring.getHasStack() || !ring.canTakeStack(player)) {
            return false;
        }
        ItemStack held = inventory.getStackInSlot(hotbar);
        if (held == null || held.stackSize <= 0 || !ring.isItemValid(held)) {
            return false;
        }
        ItemStack one = held.copy();
        one.stackSize = 1;
        ring.putStack(one);
        if (ring.getStack() == null) {
            return false;
        }
        held.stackSize--;
        inventory.setInventorySlotContents(hotbar, held.stackSize <= 0 ? null : held);
        inventory.markDirty();
        return true;
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
