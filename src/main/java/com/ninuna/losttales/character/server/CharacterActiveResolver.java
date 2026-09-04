package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The active server-side character, or null when there is none to apply:
 * the player is on the account, or the store could not be read. Callers
 * that need to tell those two apart ask {@link PlayableIdentityResolver}.
 */
public final class CharacterActiveResolver {

    private CharacterActiveResolver() {}

    public static RoleplayCharacter get(EntityPlayerMP player) {
        PlayableIdentityResolver.Resolution resolution =
                PlayableIdentityResolver.resolve(player);
        return resolution.isAvailable() ? resolution.getCharacter() : null;
    }
}
