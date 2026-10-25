package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateStorage;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.state.component.LotrProgressionStateComponent;
import com.ninuna.losttales.compat.lotr.LotrFellowshipRules;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * Where the count of how many fellowships a member's character may lead
 * comes from (FEL-08): the LOTR state that is the character's own. A
 * character played now is counted from its account's live LOTR data, by
 * the caller; any other from the progression saved with it as it was
 * last played, here. Where none can be read, it may lead one.
 */
final class FellowshipLeadLimits {

    private FellowshipLeadLimits() {}

    /** How many fellowships the member's character may lead, from the state saved with it. */
    static int savedLimitOf(World world, FellowshipMember member) {
        return LotrFellowshipRules.leadLimit(savedProgressionOf(world, member));
    }

    /**
     * The LOTR progression saved with the member's character as it was last
     * played; null for a figure nobody holds, and where none can be read.
     */
    static NBTTagCompound savedProgressionOf(World world, FellowshipMember member) {
        if (world == null || member == null || member.isUnheld()) {
            return null;
        }
        try {
            return savedProgressionOf(CharacterPlayerStateStorage.get(world,
                    member.getOwnerId()), member);
        } catch (RuntimeException unreadable) {
            LostTalesLog.warning("The saved LOTR state of fellowship member %s could not be read: %s",
                    member.getIdentityId(), unreadable.toString());
            return null;
        }
    }

    /**
     * The same, from the account's state file: the current state the
     * account keeps for the character. Null while the file is closed, the
     * account's states are set aside, or no state is kept for it.
     */
    static NBTTagCompound savedProgressionOf(CharacterPlayerStateWorldData states,
                                             FellowshipMember member) {
        if (states == null || member == null || member.isUnheld()
                || states.isReadOnlyForNewerVersion()
                || states.isOwnerBlocked(member.getOwnerId())) {
            return null;
        }
        CharacterPlayerStateAccount account = states.getAccount(member.getOwnerId());
        CharacterPlayerStateRecord record = account == null ? null
                : account.getRecord(member.getIdentityId());
        return record == null || record.getCurrent() == null ? null
                : LotrProgressionStateComponent.progressionOf(record.getCurrent()
                        .getComponent(LotrProgressionStateComponent.ID));
    }
}
