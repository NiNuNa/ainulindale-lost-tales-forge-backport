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

    /**
     * A player waiting for their first character, played as it the moment
     * it is made: only the account and the session can hold that back (a
     * freeze, a repair, an unsettled death, a session not ready). Nothing
     * the player did can, for while they wait they can do nothing.
     */
    CharacterSwitchPolicyResult evaluateFirstCharacter(
            EntityPlayerMP player,
            CharacterSwitchAccountState accountState,
            long safeNow);
}
