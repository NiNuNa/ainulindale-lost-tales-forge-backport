package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.registry.CharacterArmsRegistry;
import com.ninuna.losttales.character.skin.AccountSkinProfile;
import com.ninuna.losttales.character.skin.ProfileTexturesDecoder;
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

/**
 * Sends every client what each player looks like: only the public fields
 * that draw and describe the identity they play, the character played or
 * the account itself with the arms its skin declares.
 */
public final class CharacterAppearanceSyncManager {

    private CharacterAppearanceSyncManager() {}

    /**
     * Everyone's appearance, to one player: the first packet replaces what
     * the player knew, and a server with more players than one packet
     * carries sends the rest after it.
     */
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
                        player.getUniqueID(), accountName(player), roster,
                        accountArms(player));
                if (appearance.isPresent()) {
                    appearances.add(appearance);
                }
            }
        }
        int limit = CharacterAppearanceSyncPacket.MAX_APPEARANCES;
        int from = 0;
        do {
            int to = Math.min(appearances.size(), from + limit);
            LostTalesNetworkHandler.CHANNEL.sendTo(new CharacterAppearanceSyncPacket(
                    from == 0, appearances.subList(from, to)), recipient);
            from = to;
        } while (from < appearances.size());
    }


    public static void sendPlayer(EntityPlayerMP recipient, EntityPlayerMP target) {
        if (recipient == null || target == null || recipient.worldObj == null
                || recipient.worldObj.isRemote || target.getUniqueID() == null) {
            return;
        }
        CharacterWorldData data = CharacterStorage.get(recipient.worldObj);
        CharacterRoster roster = data.getRoster(target.getUniqueID());
        CharacterAppearance appearance = CharacterAppearance.fromRoster(
                target.getUniqueID(), accountName(target), roster,
                accountArms(target));
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new CharacterAppearanceSyncPacket(
                        false, Collections.singletonList(appearance)), recipient);
    }

    /**
     * Tells everyone what the player looks like now. The game caches the
     * player's display name, which names their character, so it is
     * refreshed here: a broadcast is exactly when that may have changed.
     */
    public static void broadcastPlayer(EntityPlayerMP player, CharacterRoster roster) {
        if (player == null || player.getUniqueID() == null) {
            return;
        }
        player.refreshDisplayName();
        CharacterAppearance appearance = CharacterAppearance.fromRoster(
                player.getUniqueID(), accountName(player), roster,
                accountArms(player));
        LostTalesNetworkHandler.CHANNEL.sendToAll(
                new CharacterAppearanceSyncPacket(
                        false, Collections.singletonList(appearance)));
    }

    /**
     * What the player looks like as their saved roster says now; null
     * while it cannot be read. Taken before a request runs, so that only a
     * look it changes goes to everyone.
     */
    public static CharacterAppearance appearanceOf(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null
                || player.worldObj == null || player.worldObj.isRemote) {
            return null;
        }
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            return appearanceOf(player, roster);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * {@link #broadcastPlayer}, only where the player's look differs from
     * {@code before}; always where {@code before} is unknown. Whether it
     * was sent.
     */
    public static boolean broadcastIfChanged(EntityPlayerMP player,
                                             CharacterAppearance before,
                                             CharacterRoster roster) {
        if (player == null || player.getUniqueID() == null) {
            return false;
        }
        if (before != null && before.sameAs(appearanceOf(player, roster))) {
            return false;
        }
        broadcastPlayer(player, roster);
        return true;
    }

    private static CharacterAppearance appearanceOf(EntityPlayerMP player,
                                                    CharacterRoster roster) {
        return CharacterAppearance.fromRoster(player.getUniqueID(),
                accountName(player), roster, accountArms(player));
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

    /**
     * The arms the account's profile skin declares. An offline-mode
     * profile carries no textures and reads as the wide body.
     */
    private static String accountArms(EntityPlayerMP player) {
        AccountSkinProfile profile = ProfileTexturesDecoder.decode(
                player.getGameProfile());
        return profile != null && profile.isSlim()
                ? CharacterArmsRegistry.SLENDER : CharacterArmsRegistry.BROAD;
    }

    /** The authenticated account name, never the roleplay identity. */
    private static String accountName(EntityPlayerMP player) {
        return player.getGameProfile() == null
                || player.getGameProfile().getName() == null
                ? player.getCommandSenderName()
                : player.getGameProfile().getName();
    }
}
