package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import net.minecraft.entity.player.EntityPlayerMP;

/** Small, failure-safe helper for reading the active server-side character. */
public final class CharacterActiveResolver {

    private CharacterActiveResolver() {}

    public static RoleplayCharacter get(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return null;
        }
        try {
            CharacterWorldData data = CharacterStorage.get(player.worldObj);
            CharacterRoster roster = data.getRoster(player.getUniqueID());
            return roster == null ? null : roster.getActiveCharacter();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
