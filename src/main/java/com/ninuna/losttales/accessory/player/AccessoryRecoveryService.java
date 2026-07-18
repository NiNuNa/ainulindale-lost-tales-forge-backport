package com.ninuna.losttales.accessory.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

/** Returns decodable quarantined entries to normal inventory without duplication. */
public final class AccessoryRecoveryService {

    private AccessoryRecoveryService() {}

    public static boolean recover(EntityPlayer player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.inventory == null || player.isDead) {
            return false;
        }
        AccessoryPlayerData data = AccessoryPlayerData.getOrCreate(player);
        List<NBTTagCompound> rejectedEntries = data == null
                ? null : data.getRejectedEntries();
        if (rejectedEntries == null || rejectedEntries.isEmpty()) {
            return false;
        }

        ItemStack decoded = null;
        int rejectedIndex = -1;
        for (int index = 0; index < rejectedEntries.size(); index++) {
            try {
                ItemStack candidate = ItemStack.loadItemStackFromNBT(
                        rejectedEntries.get(index));
                if (candidate != null && candidate.stackSize > 0
                        && candidate.stackSize <= Math.max(
                        1, candidate.getMaxStackSize())) {
                    decoded = candidate;
                    rejectedIndex = index;
                    break;
                }
            } catch (RuntimeException ignored) {
                // Unknown entries remain byte-for-byte quarantined.
            }
        }
        if (decoded == null) {
            return false;
        }

        int originalSize = decoded.stackSize;
        player.inventory.addItemStackToInventory(decoded);
        if (decoded.stackSize == originalSize) {
            return false;
        }
        data.replaceRejectedEntry(rejectedIndex,
                decoded.stackSize <= 0 ? null : decoded);
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        if (player.openContainer != null
                && player.openContainer != player.inventoryContainer) {
            player.openContainer.detectAndSendChanges();
        }
        return true;
    }
}
