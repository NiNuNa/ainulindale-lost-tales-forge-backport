package com.ninuna.losttales.accessory.player;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.AccessoryInventorySyncPacket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sends the accessory owner an explicit authoritative slot snapshot. */
public final class AccessoryInventorySyncManager {

    private static final Map<UUID, Long> SEQUENCES =
            new HashMap<UUID, Long>();

    private AccessoryInventorySyncManager() {}

    public static void send(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        AccessoryPlayerData data = AccessoryPlayerData.getOrCreate(player);
        AccessoryInventory inventory = data == null ? null : data.getInventory();
        if (inventory == null) {
            return;
        }
        ItemStack equipped = inventory.getStackInSlot(AccessoryInventory.RING_SLOT);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new AccessoryInventorySyncPacket(
                        nextSequence((EntityPlayerMP)player), equipped,
                        data.hasRejectedEntry()),
                (EntityPlayerMP)player);
    }

    public static synchronized void clearPlayer(UUID playerId) {
        if (playerId != null) {
            SEQUENCES.remove(playerId);
        }
    }

    public static synchronized void clearAll() {
        SEQUENCES.clear();
    }

    private static synchronized long nextSequence(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        Long previous = SEQUENCES.get(playerId);
        long next = previous == null ? 0L : previous.longValue() + 1L;
        if (next < 0L) {
            throw new IllegalStateException("accessory sync sequence exhausted");
        }
        SEQUENCES.put(playerId, Long.valueOf(next));
        return next;
    }
}
