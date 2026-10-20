package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Default policy: a cape of the server's list, which the character may
 * wear by its race and its faction (CHAR-39), needing no item. Another
 * policy can ask for possession, permission or achievements without
 * changing rendering.
 */
public final class AllowlistedCharacterCapeEligibilityPolicy
        implements CharacterCapeEligibilityPolicy {

    @Override
    public CharacterValidationResult validate(EntityPlayerMP player,
                                               RoleplayCharacter character,
                                               String cosmeticCapeId) {
        if (player == null) {
            return CharacterValidationResult.failure(CharacterErrorId.INVALID_PLAYER);
        }
        return validate(character, cosmeticCapeId);
    }

    /** The check itself, from the character alone. */
    static CharacterValidationResult validate(RoleplayCharacter character,
                                              String cosmeticCapeId) {
        if (CharacterCapeCatalog.NONE_ID.equals(cosmeticCapeId)) {
            return CharacterValidationResult.success();
        }
        CharacterCapeDefinition cape = CharacterCapeCatalog.get(cosmeticCapeId);
        if (cape == null || character == null) {
            return CharacterValidationResult.failure(CharacterErrorId.INVALID_CAPE);
        }
        if (!cape.mayWear(character.getRaceId(), character.getFactionId())) {
            return CharacterValidationResult.failure(CharacterErrorId.CAPE_NOT_ELIGIBLE);
        }
        return CharacterValidationResult.success();
    }
}
