package com.ninuna.losttales.network.server;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The door for client requests whose refusals go unanswered, among them
 * chat, quests, waystones, missives, quick loot and the character profile
 * read: a request is counted against its rate limit, refused if
 * malformed, then queued. A refused request is logged and dropped.
 * Character and fellowship requests have doors of their own, which
 * answer a refusal, a rate-limit refusal at most once a second.
 */
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
