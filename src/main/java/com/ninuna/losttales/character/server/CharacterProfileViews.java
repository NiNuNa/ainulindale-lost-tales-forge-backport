package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterProfilePacket;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * The sending of a character's profile. Every profile is readable by
 * anyone at any time, its owner online or not, as a character sheet is.
 * A profile goes out only when asked for, so the appearance
 * every client is sent stays small.
 */
public final class CharacterProfileViews {

    private CharacterProfileViews() {}

    /** Answers {@code viewer} with the character's profile, or that there is none to read. */
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

    /** The character's record, which every player may read the profile of; null for none. */
    static RoleplayCharacter readable(EntityPlayerMP viewer, UUID characterId) {
        CharacterWorldData data = viewer.worldObj == null ? null
                : CharacterStorage.get(viewer.worldObj);
        return data == null ? null : data.findCharacter(characterId);
    }
}
