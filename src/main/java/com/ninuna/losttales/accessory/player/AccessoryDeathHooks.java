package com.ninuna.losttales.accessory.player;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.core.LostTalesClassTransformer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * Drops the worn ring inside a player's Forge drop capture, before
 * {@code PlayerDropsEvent}: the coremod calls it from
 * {@code EntityPlayer.onDeath} and from {@code EntityPlayerMP.onDeath},
 * which a server's player runs instead (ACC-05). With {@code keepInventory},
 * or with the server's {@code ringDropsAtDeath} off, the ring stays on.
 */
public final class AccessoryDeathHooks {

    private AccessoryDeathHooks() {}

    /**
     * Whether the server player's death drops the ring. The patch lands as
     * {@code EntityPlayerMP} is loaded, so this loads the class first.
     */
    public static boolean serverDropLanded() {
        // Naming the class loads it, and the coremod patches it as it loads.
        Class<?> serverPlayer = EntityPlayerMP.class;
        return serverPlayer != null && Boolean.getBoolean(
                LostTalesClassTransformer.ACCESSORY_SERVER_DEATH_ACTIVE_PROPERTY);
    }

    public static void captureAccessoryDrop(EntityPlayer player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || !player.captureDrops
                || !LostTalesConfig.ringDropsAtDeath
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
