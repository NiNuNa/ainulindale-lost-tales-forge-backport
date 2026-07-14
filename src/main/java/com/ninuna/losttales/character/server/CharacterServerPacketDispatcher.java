package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.switching.CharacterLifecycleStateTracker;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;

/** Shared safety boundary used by every character C2S packet handler. */
public final class CharacterServerPacketDispatcher {

    private CharacterServerPacketDispatcher() {}

    public static EntityPlayerMP getPlayer(MessageContext context) {
        if (context == null || context.getServerHandler() == null) {
            return null;
        }
        return context.getServerHandler().playerEntity;
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
        if (!CharacterNetworkSecurity.allowRequest(player)) {
            CharacterNetworkSecurity.logRateLimited(player);
            if (CharacterNetworkSecurity.shouldSendRateLimitResponse(player)) {
                CharacterSyncManager.sendFailure(
                        player, requestId, operationType,
                        CharacterErrorId.RATE_LIMITED, -1L);
            }
            return;
        }
        if (malformed) {
            CharacterNetworkSecurity.logMalformed(player, packetName == null ? "character packet" : packetName);
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
            CharacterNetworkSecurity.logQueueFull(player);
            CharacterSyncManager.sendFailure(
                    player, requestId, operationType,
                    CharacterErrorId.INTERNAL_ERROR, -1L);
        }
    }

    public static void clearSecurityState() {
        CharacterNetworkSecurity.clear();
    }
}
