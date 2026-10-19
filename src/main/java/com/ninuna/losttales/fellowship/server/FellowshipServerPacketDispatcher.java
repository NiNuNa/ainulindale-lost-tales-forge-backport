package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The safety boundary for the fellowship requests clients send: the
 * per-player request window, the throttled diagnostics and the server
 * task queue. A refused request is answered, since a fellowship page
 * waits on a reply; a request refused for its rate is answered at most
 * once every {@link #RATE_LIMIT_REPLY_INTERVAL_MILLIS} per player and
 * request kind, or a flood of requests would be a flood of answers.
 */
public final class FellowshipServerPacketDispatcher {

    /** How often a request refused for its rate may be answered, per player. */
    private static final long RATE_LIMIT_REPLY_INTERVAL_MILLIS = 1000L;

    private FellowshipServerPacketDispatcher() {}

    public static EntityPlayerMP getPlayer(MessageContext context) {
        return LostTalesServerPacketDispatcher.getPlayer(context);
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
            if (LostTalesRequestRateLimiter.allowReply(player, requestType,
                    RATE_LIMIT_REPLY_INTERVAL_MILLIS)) {
                FellowshipSyncManager.sendFailure(
                        player,
                        requestId,
                        responseOperation,
                        FellowshipErrorId.RATE_LIMITED,
                        -1L,
                        false);
            }
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
