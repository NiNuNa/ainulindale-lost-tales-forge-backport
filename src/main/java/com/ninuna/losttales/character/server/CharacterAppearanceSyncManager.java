package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterAppearanceSyncPacket;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Synchronizes only the public fields required for active-character rendering. */
public final class CharacterAppearanceSyncManager {

    private CharacterAppearanceSyncManager() {}

    public static void sendFullSnapshot(EntityPlayerMP recipient) {
        if (recipient == null || recipient.worldObj == null || recipient.worldObj.isRemote) {
            return;
        }

        CharacterWorldData data = CharacterStorage.get(recipient.worldObj);
        ArrayList<CharacterAppearance> appearances = new ArrayList<CharacterAppearance>();
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            @SuppressWarnings("unchecked")
            List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
            for (EntityPlayerMP player : players) {
                if (player == null || player.getUniqueID() == null) {
                    continue;
                }
                CharacterRoster roster = data.getRoster(player.getUniqueID());
                CharacterAppearance appearance = CharacterAppearance.fromRoster(
                        player.getUniqueID(), roster);
                if (appearance.isPresent()) {
                    appearances.add(appearance);
                }
            }
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new CharacterAppearanceSyncPacket(true, appearances), recipient);
    }


    public static void sendPlayer(EntityPlayerMP recipient, EntityPlayerMP target) {
        if (recipient == null || target == null || recipient.worldObj == null
                || recipient.worldObj.isRemote || target.getUniqueID() == null) {
            return;
        }
        CharacterWorldData data = CharacterStorage.get(recipient.worldObj);
        CharacterRoster roster = data.getRoster(target.getUniqueID());
        CharacterAppearance appearance = CharacterAppearance.fromRoster(
                target.getUniqueID(), roster);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new CharacterAppearanceSyncPacket(
                        false, Collections.singletonList(appearance)), recipient);
    }

    public static void broadcastPlayer(EntityPlayerMP player, CharacterRoster roster) {
        if (player == null || player.getUniqueID() == null) {
            return;
        }
        CharacterAppearance appearance = CharacterAppearance.fromRoster(
                player.getUniqueID(), roster);
        LostTalesNetworkHandler.CHANNEL.sendToAll(
                new CharacterAppearanceSyncPacket(
                        false, Collections.singletonList(appearance)));
    }

    public static void broadcastRemoval(UUID playerId) {
        if (playerId == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToAll(
                new CharacterAppearanceSyncPacket(
                        false,
                        Collections.singletonList(CharacterAppearance.removed(playerId))));
    }
}
