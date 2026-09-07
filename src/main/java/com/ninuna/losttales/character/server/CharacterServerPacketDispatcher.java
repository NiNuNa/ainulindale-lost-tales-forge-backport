package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.switching.CharacterLifecycleStateTracker;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The safety boundary every character C2S packet handler comes through.
 *
 * <p>The gate itself — the per-player request window, the throttled
 * diagnostics — is every family's and is
 * {@link LostTalesRequestRateLimiter}'s. What is this family's own is
 * that a refused request is <em>answered</em>: a character screen is
 * waiting on a reply and would otherwise sit on a spinner, so each
 * refusal carries a {@link CharacterErrorId}. The reply is itself
 * throttled, or a flood of requests would be a flood of answers.</p>
 *
 * <p>Also this family's own is the lifecycle epoch: a request is stamped
 * when it arrives and dropped if the player has switched character, died
 * or changed dimension before the server thread reaches it.</p>
 */
public final class CharacterServerPacketDispatcher {

    /** How often a refused request may be answered, per player. */
    private static final long RATE_LIMIT_REPLY_INTERVAL_MILLIS = 1000L;
    /** The budget every character request draws on. */
    private static final LostTalesRequestRateLimiter.RequestType REQUESTS =
            LostTalesRequestRateLimiter.RequestType.CHARACTER_REQUEST;

    private CharacterServerPacketDispatcher() {}

    public static EntityPlayerMP getPlayer(MessageContext context) {
        return LostTalesServerPacketDispatcher.getPlayer(context);
    }

    public static void submit(EntityPlayerMP player,
                              int requestId,
                              CharacterOperationType operationType,
                              boolean malformed,
                              String packetName,
                              LostTalesServerTaskQueue.PlayerTask task) {
        if (player == null || operationType == null || task == null) {
            return;
        }
        if (!LostTalesRequestRateLimiter.allow(player, REQUESTS)) {
            LostTalesRequestRateLimiter.logRateLimited(player, REQUESTS, packetName);
            if (LostTalesRequestRateLimiter.allowReply(player, REQUESTS,
                    RATE_LIMIT_REPLY_INTERVAL_MILLIS)) {
                CharacterSyncManager.sendFailure(
                        player, requestId, operationType,
                        CharacterErrorId.RATE_LIMITED, -1L);
            }
            return;
        }
        if (malformed) {
            LostTalesRequestRateLimiter.logMalformed(player, packetName);
            CharacterSyncManager.sendFailure(
                    player, requestId, operationType,
                    CharacterErrorId.MALFORMED_REQUEST, -1L);
            return;
        }
        final long requestEpoch = CharacterLifecycleStateTracker.captureRequestEpoch(player);
        if (requestEpoch <= 0L) {
            CharacterSyncManager.sendFailure(
                    player, requestId, operationType,
                    CharacterErrorId.INVALID_PLAYER, -1L);
            return;
        }
        final LostTalesServerTaskQueue.PlayerTask guardedTask = task;
        final int guardedRequestId = requestId;
        final CharacterOperationType guardedOperationType = operationType;
        if (!LostTalesServerTaskQueue.enqueue(player.getUniqueID(), packetName,
                new LostTalesServerTaskQueue.PlayerTask() {
                    @Override
                    public void run(EntityPlayerMP livePlayer) {
                        if (!CharacterLifecycleStateTracker.isRequestEpochCurrent(
                                livePlayer, requestEpoch)) {
                            CharacterSyncManager.sendFailure(
                                    livePlayer,
                                    guardedRequestId,
                                    guardedOperationType,
                                    guardedOperationType == CharacterOperationType.SELECT
                                            ? CharacterErrorId.SWITCH_SESSION_CHANGED
                                            : CharacterErrorId.INVALID_PLAYER,
                                    -1L);
                            return;
                        }
                        guardedTask.run(livePlayer);
                    }
                })) {
            LostTalesRequestRateLimiter.logQueueFull(player, packetName);
            CharacterSyncManager.sendFailure(
                    player, requestId, operationType,
                    CharacterErrorId.INTERNAL_ERROR, -1L);
        }
    }
}
