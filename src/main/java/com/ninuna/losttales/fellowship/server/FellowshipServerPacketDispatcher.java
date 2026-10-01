package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;

/** Shared validation, throttling, and queue boundary for fellowship C2S packets. */
public final class FellowshipServerPacketDispatcher {

    private FellowshipServerPacketDispatcher() {}

    public static EntityPlayerMP getPlayer(MessageContext context) {
        if (context == null || context.getServerHandler() == null) {
            return null;
        }
        return context.getServerHandler().playerEntity;
    }

    public static void submit(EntityPlayerMP player,
                              int requestId,
                              FellowshipOperationType operationType,
                              boolean malformed,
                              String packetName,
                              LostTalesServerTaskQueue.PlayerTask task) {
        if (player == null || task == null) {
            return;
        }
        FellowshipOperationType safeOperation = operationType == null
                ? FellowshipOperationType.UNKNOWN : operationType;
        FellowshipOperationType responseOperation =
                safeOperation == FellowshipOperationType.UNKNOWN
                        ? FellowshipOperationType.REQUEST_STATE : safeOperation;
        LostTalesRequestRateLimiter.RequestType requestType =
                safeOperation == FellowshipOperationType.REQUEST_STATE
                        ? LostTalesRequestRateLimiter.RequestType.FELLOWSHIP_SNAPSHOT
                        : LostTalesRequestRateLimiter.RequestType.FELLOWSHIP_MUTATION;
        if (!LostTalesRequestRateLimiter.allow(player, requestType)) {
            LostTalesRequestRateLimiter.logRateLimited(
                    player, requestType, packetName);
            FellowshipSyncManager.sendFailure(
                    player,
                    requestId,
                    responseOperation,
                    FellowshipErrorId.RATE_LIMITED,
                    -1L,
                    false);
            return;
        }
        if (malformed) {
            LostTalesRequestRateLimiter.logMalformed(player, packetName);
            FellowshipSyncManager.sendFailure(
                    player,
                    requestId,
                    responseOperation,
                    FellowshipErrorId.MALFORMED_REQUEST,
                    -1L,
                    false);
            return;
        }
        if (!LostTalesServerTaskQueue.enqueue(
                player.getUniqueID(), packetName, task)) {
            LostTalesRequestRateLimiter.logQueueFull(player, packetName);
            FellowshipSyncManager.sendFailure(
                    player,
                    requestId,
                    responseOperation,
                    FellowshipErrorId.INTERNAL_ERROR,
                    -1L,
                    false);
        }
    }
}
