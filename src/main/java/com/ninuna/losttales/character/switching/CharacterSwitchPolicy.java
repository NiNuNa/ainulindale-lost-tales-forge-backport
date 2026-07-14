package com.ninuna.losttales.character.switching;

import net.minecraft.entity.player.EntityPlayerMP;

/** Extensible centralized policy boundary for every character switch. */
public interface CharacterSwitchPolicy {
    CharacterSwitchPolicyResult evaluate(EntityPlayerMP player,
                                         CharacterSwitchAccountState accountState,
                                         long safeNow);

    CharacterSwitchPolicyResult evaluateDuringOwnedSwitch(
            EntityPlayerMP player,
            CharacterSwitchAccountState accountState,
            long safeNow);
}
