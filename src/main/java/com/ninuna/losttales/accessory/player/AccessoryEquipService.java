package com.ninuna.losttales.accessory.player;

import com.ninuna.losttales.accessory.AccessoryCompatibilityRegistry;
import com.ninuna.losttales.accessory.AccessorySlotType;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Server-authoritative convenience actions for moving held accessories. */
public final class AccessoryEquipService {

    private AccessoryEquipService() {}

    /**
     * Atomically swaps the held one-item ring with the equipped ring.
     *
     * <p>The hotbar is changed directly and the item use method returns its
     * original stack. This avoids vanilla creative mode restoring a consumed
     * stack after {@code onItemRightClick} and duplicating the accessory.</p>
     */
    public static boolean trySwapHeldRing(
            EntityPlayer player, ItemStack heldStack) {
        if (!(player instanceof EntityPlayerMP)
                || player.worldObj == null || player.worldObj.isRemote
                || player.inventory == null || heldStack == null
                || heldStack.stackSize != 1
                || heldStack.getItem() == null
                || player.inventory.currentItem < 0
                || player.inventory.currentItem >= 9
                || player.inventory.getCurrentItem() != heldStack) {
            return false;
        }

        AccessoryPlayerData data = AccessoryPlayerData.getOrCreate(player);
        AccessoryInventory accessory = data == null
                ? null : data.getInventory();
        if (accessory == null || !accessory.isItemValidForSlot(
                AccessoryInventory.RING_SLOT, heldStack)) {
            return false;
        }

        ItemStack previous = accessory.getStackInSlot(
                AccessoryInventory.RING_SLOT);
        if (previous == heldStack || previous != null
                && (previous.stackSize != 1
                || !AccessoryCompatibilityRegistry.getInstance()
                .isCompatible(AccessorySlotType.RING, previous))) {
            return false;
        }

        ItemStack equipped = heldStack.copy();
        equipped.stackSize = 1;
        accessory.setInventorySlotContents(
                AccessoryInventory.RING_SLOT, equipped);
        if (accessory.getStackInSlot(
                AccessoryInventory.RING_SLOT) != equipped) {
            return false;
        }

        player.inventory.setInventorySlotContents(
                player.inventory.currentItem, previous);
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        if (player.openContainer != null
                && player.openContainer != player.inventoryContainer) {
            player.openContainer.detectAndSendChanges();
        }
        ((EntityPlayerMP)player).sendContainerToPlayer(
                player.inventoryContainer);
        AccessoryInventorySyncManager.send(player);
        AccessoryEffectService.refresh(player);
        return true;
    }
}
