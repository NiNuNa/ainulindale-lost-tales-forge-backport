package com.ninuna.losttales.network.server;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;

/** Shared safety boundary for non-character client-to-server packet handlers. */
public final class LostTalesServerPacketDispatcher {

    private LostTalesServerPacketDispatcher() {}

    public static EntityPlayerMP getPlayer(MessageContext context) {
        if (context == null || context.getServerHandler() == null) {
            return null;
        }
        return context.getServerHandler().playerEntity;
    }

    public static void submit(EntityPlayerMP player,
                              LostTalesRequestRateLimiter.RequestType requestType,
                              boolean malformed,
                              String packetName,
                              LostTalesServerTaskQueue.PlayerTask task) {
        if (player == null || requestType == null || task == null) {
            return;
        }
        if (!LostTalesRequestRateLimiter.allow(player, requestType)) {
            LostTalesRequestRateLimiter.logRateLimited(player, requestType, packetName);
            return;
        }
        if (malformed) {
            LostTalesRequestRateLimiter.logMalformed(player, packetName);
            return;
        }
        if (!LostTalesServerTaskQueue.enqueue(player.getUniqueID(), packetName, task)) {
            LostTalesRequestRateLimiter.logQueueFull(player, packetName);
        }
    }
}
