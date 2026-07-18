package com.ninuna.losttales.accessory.player;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Runs inside EntityPlayer's Forge drop-capture transaction before PlayerDropsEvent. */
public final class AccessoryDeathHooks {

    private AccessoryDeathHooks() {}

    public static void captureAccessoryDrop(EntityPlayer player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || !player.captureDrops
                || player.worldObj.getGameRules().getGameRuleBooleanValue(
                "keepInventory")) {
            return;
        }
        AccessoryPlayerData data = AccessoryPlayerData.get(player);
        AccessoryInventory inventory = data == null ? null : data.getInventory();
        if (inventory == null) {
            return;
        }
        ItemStack removed = inventory.decrStackSize(
                AccessoryInventory.RING_SLOT, 1);
        if (removed == null) {
            return;
        }
        int capturedBefore = player.capturedDrops == null
                ? 0 : player.capturedDrops.size();
        try {
            EntityItem drop = player.func_146097_a(removed, true, false);
            if (drop == null) {
                inventory.restoreValidated(removed);
            }
        } catch (RuntimeException exception) {
            // A failed drop creation must never silently destroy the accessory.
            if (player.capturedDrops == null
                    || player.capturedDrops.size() <= capturedBefore) {
                inventory.restoreValidated(removed);
            }
            throw exception;
        }
    }
}
