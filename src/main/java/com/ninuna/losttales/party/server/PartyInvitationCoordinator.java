package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterIndex;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyInvitation;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.storage.PartyInvitationStorage;
import com.ninuna.losttales.party.storage.PartyInvitationWorldData;
import com.ninuna.losttales.party.storage.PartyWorldData;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.ninuna.losttales.util.LostTalesServerPlayers;

/**
 * Invitation-specific operations kept separate from the core party lifecycle.
 *
 * Every method is package-private and must be invoked while holding the
 * synchronized {@link PartyService} mutation boundary.
 */
final class PartyInvitationCoordinator {

    private static final int UUID_GENERATION_ATTEMPTS = 8;

    private final PartyService partyService;

    PartyInvitationCoordinator(PartyService partyService) {
        if (partyService == null) {
            throw new IllegalArgumentException("partyService must not be null");
        }
        this.partyService = partyService;
    }

    PartyInvitationWorldData getWritableData(World world) {
        try {
            PartyInvitationWorldData data = PartyInvitationStorage.get(world);
            return data.isReadOnlyForNewerVersion() ? null : data;
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access party invitation storage: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
    }

    PartyInvitationOperationResult invitePlayer(
            EntityPlayerMP player,
            PartyService.ActiveIdentityContext inviting,
            PartyWorldData partyData,
            Party party,
            UUID targetOwnerId) {
        CharacterWorldData characterData = inviting.characterData;
        if (targetOwnerId == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVALID_TARGET, party, null);
        }
        if (party.isFull(PartyService.memberLimit())) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.PARTY_FULL, party, null);
        }

        PartyInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, party, null);
        }
        pruneInvalidInvitations(
                partyData,
                invitationData,
                characterData,
                System.currentTimeMillis());

        EntityPlayerMP targetPlayer = LostTalesServerPlayers.findOnline(targetOwnerId);
        if (targetPlayer == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.TARGET_OFFLINE, party, null);
        }
        PartyService.ActiveIdentityContext target =
                this.partyService.resolveActiveIdentity(targetPlayer);
        if (!target.isValid()) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVALID_TARGET, party, null);
        }
        UUID targetIdentityId = target.gameplayId();
        if (inviting.gameplayId().equals(targetIdentityId)) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.CANNOT_INVITE_SELF, party, null);
        }
        Party targetParty = partyData.getPartyForIdentity(targetIdentityId);
        if (targetParty != null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.TARGET_ALREADY_IN_PARTY, party, null);
        }
        if (party.hasMemberOwnedBy(target.ownerId())) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.ACCOUNT_ALREADY_IN_PARTY, party, null);
        }
        if (invitationData.hasInvitationForPartyAndTarget(
                party.getPartyId(), targetIdentityId)) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_ALREADY_EXISTS, party, null);
        }
        if (PartyDeclines.mustWait(player.getUniqueID(), target.ownerId(),
                System.currentTimeMillis())) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.RECENTLY_DECLINED, party, null);
        }
        UUID invitationId = createUniqueInvitationId(invitationData);
        if (invitationId == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, party, null);
        }

        long now = System.currentTimeMillis();
        PartyInvitation invitation = new PartyInvitation(
                invitationId,
                party.getPartyId(),
                inviting.gameplayId(),
                inviting.ownerId(),
                inviting.displayName,
                target.gameplayId(),
                target.ownerId(),
                target.displayName,
                now,
                safeExpiration(now));
        try {
            invitationData.saveInvitation(invitation);
            return PartyInvitationOperationResult.success(
                    true, party, invitation, null);
        } catch (RuntimeException exception) {
            this.partyService.logFailure("invite", player, exception);
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, party, invitation);
        }
    }

    PartyInvitationOperationResult acceptInvitation(
            EntityPlayerMP player,
            PartyService.ActiveIdentityContext active,
            PartyWorldData partyData,
            UUID invitationId) {
        if (invitationId == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_NOT_FOUND, null, null);
        }
        PartyInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, null, null);
        }

        PartyInvitation invitation = invitationData.getInvitation(invitationId);
        if (invitation == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_NOT_FOUND, null, null);
        }
        long now = System.currentTimeMillis();
        if (invitation.isExpired(now)) {
            invitationData.removeInvitation(invitationId);
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_EXPIRED,
                    true,
                    partyData.getParty(invitation.getPartyId()),
                    invitation);
        }
        if (!active.gameplayId().equals(
                invitation.getTargetIdentityId())
                || !player.getUniqueID().equals(invitation.getTargetOwnerId())) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_TARGET_MISMATCH,
                    partyData.getParty(invitation.getPartyId()),
                    null);
        }

        CharacterIndex acceptanceIndex =
                active.characterData.characterIndex();
        String corruptionReason = getInvitationCorruptionReason(
                invitation, acceptanceIndex);
        if (corruptionReason != null) {
            invitationData.quarantine(corruptionReason, invitation);
            invitationData.removeInvitation(invitationId);
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_INVALID,
                    true,
                    partyData.getParty(invitation.getPartyId()),
                    invitation);
        }

        Party existingParty = partyData.getPartyForIdentity(
                active.gameplayId());
        if (existingParty != null) {
            invitationData.removeInvitationsForTargetIdentity(
                    active.gameplayId());
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.TARGET_ALREADY_IN_PARTY,
                    true,
                    existingParty,
                    invitation);
        }
        Party party = partyData.getParty(invitation.getPartyId());
        PartyErrorId invitationError = validateInvitationForAcceptance(
                invitation, party, partyData, active.characterData);
        if (invitationError != PartyErrorId.NONE) {
            if (invitationError == PartyErrorId.PARTY_FULL && party != null) {
                invitationData.removeInvitationsForParty(party.getPartyId());
            } else {
                invitationData.removeInvitation(invitationId);
            }
            return PartyInvitationOperationResult.failure(
                    invitationError, true, party, invitation);
        }

        PartyColor color = party.getFirstAvailableColor();
        if (color == null || party.isFull(PartyService.memberLimit())) {
            invitationData.removeInvitationsForParty(party.getPartyId());
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.PARTY_FULL, true, party, invitation);
        }
        PartyMember joined = new PartyMember(
                active.gameplayId(),
                active.ownerId(),
                active.displayName,
                now,
                color);
        Party updatedParty = copyPartyWithAdditionalMember(party, joined);
        if (updatedParty == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, party, invitation);
        }
        try {
            partyData.saveParty(updatedParty);
            invitationData.removeInvitationsForTargetIdentity(
                    active.gameplayId());
            if (updatedParty.isFull(PartyService.memberLimit())) {
                invitationData.removeInvitationsForParty(updatedParty.getPartyId());
            }
            return PartyInvitationOperationResult.success(
                    true, updatedParty, invitation, joined);
        } catch (RuntimeException exception) {
            this.partyService.logFailure(
                    "accept invitation", player, exception);
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, party, invitation);
        }
    }

    PartyInvitationOperationResult declineInvitation(
            EntityPlayerMP player,
            PartyService.ActiveIdentityContext active,
            UUID invitationId) {
        PartyInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, null, null);
        }
        PartyInvitation invitation = invitationData.getInvitation(invitationId);
        if (invitation == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_NOT_FOUND, null, null);
        }
        if (!active.gameplayId().equals(
                invitation.getTargetIdentityId())
                || !player.getUniqueID().equals(invitation.getTargetOwnerId())) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_TARGET_MISMATCH, null, null);
        }
        PartyWorldData partyData = this.partyService.getPartyData(player.worldObj);
        Party party = partyData == null ? null
                : partyData.getParty(invitation.getPartyId());
        invitationData.removeInvitation(invitationId);
        long now = System.currentTimeMillis();
        if (invitation.isExpired(now)) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_EXPIRED,
                    true,
                    party,
                    invitation);
        }
        PartyDeclines.declined(invitation.getInvitingOwnerId(),
                invitation.getTargetOwnerId(), now);
        return PartyInvitationOperationResult.success(
                true, party, invitation, null);
    }

    PartyInvitationOperationResult cancelInvitation(
            EntityPlayerMP player,
            Party party,
            UUID invitationId) {
        PartyInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, party, null);
        }
        PartyInvitation invitation = invitationData.getInvitation(invitationId);
        if (invitation == null
                || !party.getPartyId().equals(invitation.getPartyId())) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_NOT_FOUND, party, null);
        }
        invitationData.removeInvitation(invitationId);
        if (invitation.isExpired(System.currentTimeMillis())) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_EXPIRED,
                    true,
                    party,
                    invitation);
        }
        return PartyInvitationOperationResult.success(
                true, party, invitation, null);
    }

    PartyInvitationState getInvitationState(
            EntityPlayerMP player,
            PartyService.ActiveIdentityContext active,
            PartyWorldData partyData) {
        PartyInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyInvitationState.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY);
        }
        pruneInvalidInvitations(
                partyData,
                invitationData,
                active.characterData,
                System.currentTimeMillis());

        Party party = partyData.getPartyForIdentity(
                active.gameplayId());
        List<PartyInvitation> outgoing = new ArrayList<PartyInvitation>();
        if (party != null && active.gameplayId().equals(
                party.getLeaderIdentityId())) {
            outgoing.addAll(invitationData.getInvitationsForParty(
                    party.getPartyId()));
        }
        return PartyInvitationState.success(
                active.gameplayId(),
                party,
                invitationData.getInvitationsForTargetIdentity(
                        active.gameplayId()),
                outgoing);
    }

    int pruneInvalidInvitations(
            PartyWorldData partyData,
            PartyInvitationWorldData invitationData,
            CharacterWorldData characterData,
            long now) {
        int removed = invitationData.removeExpired(now);
        CharacterIndex index =
                characterData.characterIndex();
        List<PartyInvitation> invitations =
                new ArrayList<PartyInvitation>(invitationData.getInvitations());
        for (PartyInvitation invitation : invitations) {
            String corruptionReason = getInvitationCorruptionReason(
                    invitation, index);
            if (corruptionReason != null) {
                invitationData.quarantine(corruptionReason, invitation);
                if (invitationData.removeInvitation(
                        invitation.getInvitationId()) != null) {
                    removed++;
                }
                continue;
            }
            if (isStale(invitation, partyData)
                    && invitationData.removeInvitation(
                    invitation.getInvitationId()) != null) {
                removed++;
            }
        }
        return removed;
    }

    /** How many invitations {@link #pruneInvalidInvitations} would remove, removing none. */
    int countInvalidInvitations(
            PartyWorldData partyData,
            PartyInvitationWorldData invitationData,
            CharacterWorldData characterData,
            long now) {
        int count = 0;
        CharacterIndex index = characterData.characterIndex();
        for (PartyInvitation invitation : invitationData.getInvitations()) {
            if (invitation.isExpired(now)
                    || getInvitationCorruptionReason(invitation, index) != null
                    || isStale(invitation, partyData)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether an invitation no longer stands: its party is gone or full,
     * the one who sent it no longer leads it, or the invited identity is
     * in a party now.
     */
    private static boolean isStale(PartyInvitation invitation,
                                   PartyWorldData partyData) {
        Party party = partyData.getParty(invitation.getPartyId());
        return party == null
                || party.isFull(PartyService.memberLimit())
                || !party.containsMember(invitation.getInvitingIdentityId())
                || !invitation.getInvitingIdentityId().equals(
                party.getLeaderIdentityId())
                || partyData.getPartyForIdentity(
                invitation.getTargetIdentityId()) != null;
    }

    private PartyErrorId validateInvitationForAcceptance(
            PartyInvitation invitation,
            Party party,
            PartyWorldData partyData,
            CharacterWorldData characterData) {
        if (party == null) {
            return PartyErrorId.INVITATION_INVALID;
        }
        if (party.isFull(PartyService.memberLimit())) {
            return PartyErrorId.PARTY_FULL;
        }
        if (!party.containsMember(invitation.getInvitingIdentityId())
                || !invitation.getInvitingIdentityId().equals(
                party.getLeaderIdentityId())) {
            return PartyErrorId.INVITATION_INVALID;
        }
        if (partyData.getPartyForIdentity(
                invitation.getTargetIdentityId()) != null) {
            return PartyErrorId.TARGET_ALREADY_IN_PARTY;
        }
        // Another character of the account may have joined while this
        // invitation waited.
        if (party.hasMemberOwnedBy(invitation.getTargetOwnerId())) {
            return PartyErrorId.ACCOUNT_ALREADY_IN_PARTY;
        }
        CharacterIndex index =
                characterData.characterIndex();
        if (getInvitationCorruptionReason(invitation, index) != null) {
            return PartyErrorId.INVITATION_INVALID;
        }
        return PartyErrorId.NONE;
    }

    private String getInvitationCorruptionReason(
            PartyInvitation invitation,
            CharacterIndex index) {
        UUID invitingId = invitation.getInvitingIdentityId();
        UUID targetId = invitation.getTargetIdentityId();
        if (index.isAmbiguous(invitingId)) {
            return "ambiguous_inviting_character_uuid";
        }
        if (index.isAmbiguous(targetId)) {
            return "ambiguous_target_character_uuid";
        }
        // Either side may be an account playing as itself: its id is then
        // its own owner's and stands as long as that account has a roster.
        RoleplayCharacter inviting = index.find(invitingId);
        RoleplayCharacter target = index.find(targetId);
        boolean invitingAccount = inviting == null
                && invitingId.equals(invitation.getInvitingOwnerId())
                && index.isAccountOwner(invitingId);
        boolean targetAccount = target == null
                && targetId.equals(invitation.getTargetOwnerId())
                && index.isAccountOwner(targetId);
        if (inviting == null && !invitingAccount) {
            return "missing_inviting_character";
        }
        if (target == null && !targetAccount) {
            return "missing_target_character";
        }
        if (inviting != null
                && !inviting.getOwnerId().equals(invitation.getInvitingOwnerId())) {
            return "inviting_owner_mismatch";
        }
        if (target != null
                && !target.getOwnerId().equals(invitation.getTargetOwnerId())) {
            return "target_owner_mismatch";
        }
        return null;
    }

    private Party copyPartyWithAdditionalMember(
            Party party, PartyMember member) {
        if (party == null || member == null
                || party.getMemberCount() >= Party.MAX_MEMBERS
                || party.containsMember(member.getIdentityId())
                || !party.isColorAvailable(member.getColor(), null)) {
            return null;
        }
        ArrayList<PartyMember> members =
                new ArrayList<PartyMember>(party.getMembers());
        members.add(member);
        long nextRevision = party.getRevision() == Long.MAX_VALUE
                ? Long.MAX_VALUE : party.getRevision() + 1L;
        return new Party(
                party.getPartyId(),
                party.getLeaderIdentityId(),
                members,
                party.getName(),
                party.getCreatedAt(),
                nextRevision,
                party.getDataVersion());
    }

    private UUID createUniqueInvitationId(PartyInvitationWorldData data) {
        for (int attempt = 0; attempt < UUID_GENERATION_ATTEMPTS; attempt++) {
            UUID invitationId = UUID.randomUUID();
            if (!data.containsInvitation(invitationId)) {
                return invitationId;
            }
        }
        return null;
    }

    private long safeExpiration(long now) {
        return now > Long.MAX_VALUE - PartyService.INVITATION_LIFETIME_MILLIS
                ? Long.MAX_VALUE
                : now + PartyService.INVITATION_LIFETIME_MILLIS;
    }

}
