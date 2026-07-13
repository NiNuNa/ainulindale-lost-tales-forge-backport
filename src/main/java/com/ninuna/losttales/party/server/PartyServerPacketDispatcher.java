package com.ninuna.losttales.party.server;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.party.sync.PartyOperationType;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;

/** Shared validation, throttling, and queue boundary for party C2S packets. */
public final class PartyServerPacketDispatcher {

    private PartyServerPacketDispatcher() {}

    public static EntityPlayerMP getPlayer(MessageContext context) {
        if (context == null || context.getServerHandler() == null) {
            return null;
        }
        return context.getServerHandler().playerEntity;
    }

    public static void submit(EntityPlayerMP player,
                              int requestId,
                              PartyOperationType operationType,
                              boolean malformed,
                              String packetName,
                              LostTalesServerTaskQueue.PlayerTask task) {
        if (player == null || task == null) {
            return;
        }
        PartyOperationType safeOperation = operationType == null
                ? PartyOperationType.UNKNOWN : operationType;
        PartyOperationType responseOperation =
                safeOperation == PartyOperationType.UNKNOWN
                        ? PartyOperationType.REQUEST_STATE : safeOperation;
        LostTalesRequestRateLimiter.RequestType requestType =
                safeOperation == PartyOperationType.REQUEST_STATE
                        ? LostTalesRequestRateLimiter.RequestType.PARTY_SNAPSHOT
                        : LostTalesRequestRateLimiter.RequestType.PARTY_MUTATION;
        if (!LostTalesRequestRateLimiter.allow(player, requestType)) {
            LostTalesRequestRateLimiter.logRateLimited(
                    player, requestType, packetName);
            PartySyncManager.sendFailure(
                    player,
                    requestId,
                    responseOperation,
                    PartyErrorId.RATE_LIMITED,
                    -1L,
                    false);
            return;
        }
        if (malformed) {
            LostTalesRequestRateLimiter.logMalformed(player, packetName);
            PartySyncManager.sendFailure(
                    player,
                    requestId,
                    responseOperation,
                    PartyErrorId.MALFORMED_REQUEST,
                    -1L,
                    false);
            return;
        }
        if (!LostTalesServerTaskQueue.enqueue(
                player.getUniqueID(), packetName, task)) {
            LostTalesRequestRateLimiter.logQueueFull(player, packetName);
            PartySyncManager.sendFailure(
                    player,
                    requestId,
                    responseOperation,
                    PartyErrorId.INTERNAL_ERROR,
                    -1L,
                    false);
        }
    }
}
