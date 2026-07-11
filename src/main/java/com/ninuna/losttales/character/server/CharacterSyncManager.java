package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterOperationResultPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterSyncPacket;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

/** Constructs and sends private authoritative character snapshots. */
public final class CharacterSyncManager {

    public static final int UNSOLICITED_REQUEST_ID = 0;

    private CharacterSyncManager() {}

    public static void sendResultAndRoster(EntityPlayerMP player,
                                           int requestId,
                                           CharacterOperationType operationType,
                                           CharacterOperationResult result) {
        if (player == null || result == null || operationType == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new CharacterOperationResultPacket(requestId, operationType, result), player);
        if (result.getRoster() != null) {
            sendRoster(player, requestId, result.getRoster());
        }
    }

    public static void sendFailure(EntityPlayerMP player,
                                   int requestId,
                                   CharacterOperationType operationType,
                                   CharacterErrorId errorId,
                                   long rosterRevision) {
        if (player == null || operationType == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new CharacterOperationResultPacket(
                        requestId, operationType, errorId, rosterRevision),
                player
        );
    }

    public static boolean sendRoster(EntityPlayerMP player,
                                     int requestId,
                                     CharacterRoster roster) {
        if (player == null || roster == null || player.getUniqueID() == null) {
            return false;
        }
        if (!player.getUniqueID().equals(roster.getOwnerId())) {
            FMLLog.warning("[%s] Refused to send roster owned by %s to player %s",
                    LostTalesMetaData.MOD_ID, roster.getOwnerId(), player.getUniqueID());
            return false;
        }
        CharacterRosterSnapshot snapshot = CharacterRosterSnapshot.fromRoster(roster);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new CharacterRosterSyncPacket(requestId, snapshot), player);
        return true;
    }
}
