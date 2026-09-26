package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.chat.server.ChatIdentitySelection;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterProfilePacket;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Who may read which character's profile, and the sending of it. A player
 * reads their own characters' whenever they like, and another player's
 * only while that player is online and using it — the character they
 * play, or the one they speak as in the chat — which is who their card
 * and the member lists show. A profile goes out only when asked for, so
 * the appearance every client is sent stays small.
 */
public final class CharacterProfileViews {

    private CharacterProfileViews() {}

    /** Answers {@code viewer} with the character's profile, or that they may not read it. */
    public static void send(EntityPlayerMP viewer, UUID characterId) {
        if (viewer == null || characterId == null) {
            return;
        }
        RoleplayCharacter character = readable(viewer, characterId);
        LostTalesNetworkHandler.CHANNEL.sendTo(character == null
                ? CharacterProfilePacket.unavailable(characterId)
                : CharacterProfilePacket.of(characterId, character.getProfile()),
                viewer);
    }

    /** The character's record when {@code viewer} may read its profile, else null. */
    static RoleplayCharacter readable(EntityPlayerMP viewer, UUID characterId) {
        CharacterWorldData data = viewer.worldObj == null ? null
                : CharacterStorage.get(viewer.worldObj);
        RoleplayCharacter character = data == null ? null
                : data.findCharacter(characterId);
        if (character == null) {
            return null;
        }
        if (viewer.getUniqueID().equals(character.getOwnerId())) {
            return character;
        }
        EntityPlayerMP owner = LostTalesServerPlayers.findOnline(
                character.getOwnerId());
        return owner != null && inUse(owner, characterId) ? character : null;
    }

    /** Whether {@code owner} plays the character or speaks as it in the chat. */
    private static boolean inUse(EntityPlayerMP owner, UUID characterId) {
        RoleplayCharacter played = CharacterActiveResolver.get(owner);
        if (played != null && characterId.equals(played.getCharacterId())) {
            return true;
        }
        RoleplayCharacter speaking = ChatIdentitySelection.character(owner);
        return speaking != null && characterId.equals(speaking.getCharacterId());
    }
}
