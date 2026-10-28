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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Where the count of how many fellowships a member's character may lead
 * comes from (FEL-08): the LOTR state that is the character's own. A
 * character played now is counted from its account's live LOTR data, by
 * the caller; any other from the progression saved with it as it was
 * last played, here. Where none can be read, it may lead one.
 *
 * <p>An account whose saved state cannot be read is logged once, not at
 * every count: static state, cleared with the server's other stores
 * through {@link FellowshipSyncManager#clear}.</p>
 */
final class FellowshipLeadLimits {

    /** The accounts whose saved state could not be read, logged once each. */
    private static final Set<UUID> LOGGED = new HashSet<UUID>();

    private FellowshipLeadLimits() {}

    /** Forgets which accounts were logged, as the server starts or stops. */
    static void clear() {
        synchronized (LOGGED) {
            LOGGED.clear();
        }
    }

    /** Whether an unreadable state of {@code ownerId}'s is news to log: true the first time only. */
    static boolean firstUnreadable(UUID ownerId) {
        synchronized (LOGGED) {
            return LOGGED.add(ownerId);
        }
    }

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
            if (firstUnreadable(member.getOwnerId())) {
                LostTalesLog.warning("The saved LOTR state of fellowship member %s could not "
                                + "be read: %s; it may lead one fellowship",
                        member.getIdentityId(), unreadable.toString());
            }
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
