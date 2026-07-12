package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import net.minecraft.entity.player.EntityPlayerMP;

/** Replaceable server-side validation point for cosmetic cape eligibility. */
public interface CharacterCapeEligibilityPolicy {

    CharacterValidationResult validate(EntityPlayerMP player,
                                       RoleplayCharacter character,
                                       int cosmeticCapeId);
}
