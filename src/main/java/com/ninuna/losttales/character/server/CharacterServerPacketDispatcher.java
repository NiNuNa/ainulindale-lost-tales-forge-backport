package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.validation.CharacterErrorId;
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
                              Runnable task) {
        if (player == null || operationType == null) {
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
        if (!CharacterServerTaskQueue.enqueue(player.getUniqueID(), task)) {
            CharacterNetworkSecurity.logQueueFull(player);
            CharacterSyncManager.sendFailure(
                    player, requestId, operationType,
                    CharacterErrorId.INTERNAL_ERROR, -1L);
        }
    }
}
