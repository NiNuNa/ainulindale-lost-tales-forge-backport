package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Default policy: every entry in the server-owned catalog is cosmetic-only and
 * does not require an inventory item. Replacing this policy can add possession,
 * permission, faction, or achievement requirements without changing rendering.
 */
public final class AllowlistedCharacterCapeEligibilityPolicy
        implements CharacterCapeEligibilityPolicy {

    @Override
    public CharacterValidationResult validate(EntityPlayerMP player,
                                               RoleplayCharacter character,
                                               int cosmeticCapeId) {
        if (player == null || character == null) {
            return CharacterValidationResult.failure(CharacterErrorId.INVALID_PLAYER);
        }
        if (!CharacterCapeCatalog.isValidSelection(cosmeticCapeId)) {
            return CharacterValidationResult.failure(CharacterErrorId.INVALID_CAPE);
        }
        return CharacterValidationResult.success();
    }
}
