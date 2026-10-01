package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterIndex;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.storage.FellowshipInvitationStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipInvitationWorldData;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ninuna.losttales.util.LostTalesServerPlayers;

/**
 * Invitation-specific operations kept separate from the core fellowship lifecycle.
 *
 * Every method is package-private and must be invoked while holding the
 * synchronized {@link FellowshipService} mutation boundary.
 */
final class FellowshipInvitationCoordinator {

    private static final int UUID_GENERATION_ATTEMPTS = 8;

    private final FellowshipService fellowshipService;

    FellowshipInvitationCoordinator(FellowshipService fellowshipService) {
        if (fellowshipService == null) {
            throw new IllegalArgumentException("fellowshipService must not be null");
        }
        this.fellowshipService = fellowshipService;
    }

    FellowshipInvitationWorldData getWritableData(World world) {
        try {
            FellowshipInvitationWorldData data = FellowshipInvitationStorage.get(world);
            return data.isReadOnlyForNewerVersion() ? null : data;
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access fellowship invitation storage: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
    }

    FellowshipInvitationOperationResult invitePlayer(
            EntityPlayerMP player,
            FellowshipService.ActiveIdentityContext inviting,
            FellowshipWorldData fellowshipData,
            Fellowship fellowship,
            UUID targetOwnerId) {
        CharacterWorldData characterData = inviting.characterData;
        if (targetOwnerId == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVALID_TARGET, fellowship, null);
        }
        if (fellowship.isFull(FellowshipService.memberLimit())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.FELLOWSHIP_FULL, fellowship, null);
        }

        FellowshipInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY, fellowship, null);
        }
        pruneInvalidInvitations(
                fellowshipData,
                invitationData,
                characterData,
                System.currentTimeMillis());

        EntityPlayerMP targetPlayer = LostTalesServerPlayers.findOnline(targetOwnerId);
        if (targetPlayer == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.TARGET_OFFLINE, fellowship, null);
        }
        FellowshipService.ActiveIdentityContext target =
                this.fellowshipService.resolveActiveIdentity(targetPlayer);
        if (!target.isValid()) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVALID_TARGET, fellowship, null);
        }
        UUID targetIdentityId = target.gameplayId();
        if (inviting.gameplayId().equals(targetIdentityId)) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.CANNOT_INVITE_SELF, fellowship, null);
        }
        if (fellowship.containsMember(targetIdentityId)) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.TARGET_ALREADY_MEMBER, fellowship, null);
        }
        if (fellowshipData.getFellowshipsForIdentity(targetIdentityId).size()
                >= Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.TARGET_TOO_MANY_FELLOWSHIPS, fellowship, null);
        }
        if (fellowship.hasMemberOwnedBy(target.ownerId())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.ACCOUNT_ALREADY_IN_FELLOWSHIP, fellowship, null);
        }
        if (invitationData.hasInvitationForFellowshipAndTarget(
                fellowship.getFellowshipId(), targetIdentityId)) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_ALREADY_EXISTS, fellowship, null);
        }
        if (FellowshipDeclines.mustWait(player.getUniqueID(), target.ownerId(),
                System.currentTimeMillis())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.RECENTLY_DECLINED, fellowship, null);
        }
        UUID invitationId = createUniqueInvitationId(invitationData);
        if (invitationId == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INTERNAL_ERROR, fellowship, null);
        }

        long now = System.currentTimeMillis();
        FellowshipInvitation invitation = new FellowshipInvitation(
                invitationId,
                fellowship.getFellowshipId(),
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
            return FellowshipInvitationOperationResult.success(
                    true, fellowship, invitation, null);
        } catch (RuntimeException exception) {
            this.fellowshipService.logFailure("invite", player, exception);
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INTERNAL_ERROR, fellowship, invitation);
        }
    }

    FellowshipInvitationOperationResult acceptInvitation(
            EntityPlayerMP player,
            FellowshipService.ActiveIdentityContext active,
            FellowshipWorldData fellowshipData,
            UUID invitationId) {
        if (invitationId == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_NOT_FOUND, null, null);
        }
        FellowshipInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY, null, null);
        }

        FellowshipInvitation invitation = invitationData.getInvitation(invitationId);
        if (invitation == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_NOT_FOUND, null, null);
        }
        long now = System.currentTimeMillis();
        if (invitation.isExpired(now)) {
            invitationData.removeInvitation(invitationId);
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_EXPIRED,
                    true,
                    fellowshipData.getFellowship(invitation.getFellowshipId()),
                    invitation);
        }
        if (!active.gameplayId().equals(
                invitation.getTargetIdentityId())
                || !player.getUniqueID().equals(invitation.getTargetOwnerId())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_TARGET_MISMATCH,
                    fellowshipData.getFellowship(invitation.getFellowshipId()),
                    null);
        }

        CharacterIndex acceptanceIndex =
                active.characterData.characterIndex();
        String corruptionReason = getInvitationCorruptionReason(
                invitation, acceptanceIndex);
        if (corruptionReason != null) {
            invitationData.quarantine(corruptionReason, invitation);
            invitationData.removeInvitation(invitationId);
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_INVALID,
                    true,
                    fellowshipData.getFellowship(invitation.getFellowshipId()),
                    invitation);
        }

        Fellowship fellowship = fellowshipData.getFellowship(invitation.getFellowshipId());
        List<Fellowship> joined = fellowshipData.getFellowshipsForIdentity(
                active.gameplayId());
        if (fellowship != null && joined.size()
                >= Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.TOO_MANY_FELLOWSHIPS, fellowship, invitation);
        }
        if (fellowship != null && FellowshipService.hasFellowshipNamed(joined,
                fellowship.getName(), fellowship.getFellowshipId())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.NAME_IN_USE, fellowship, invitation);
        }
        FellowshipErrorId invitationError = validateInvitationForAcceptance(
                invitation, fellowship, fellowshipData, active.characterData);
        if (invitationError != FellowshipErrorId.NONE) {
            if (invitationError == FellowshipErrorId.FELLOWSHIP_FULL && fellowship != null) {
                invitationData.removeInvitationsForFellowship(fellowship.getFellowshipId());
            } else {
                invitationData.removeInvitation(invitationId);
            }
            return FellowshipInvitationOperationResult.failure(
                    invitationError, true, fellowship, invitation);
        }

        if (fellowship.isFull(FellowshipService.memberLimit())) {
            invitationData.removeInvitationsForFellowship(fellowship.getFellowshipId());
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.FELLOWSHIP_FULL, true, fellowship, invitation);
        }
        FellowshipMember member = new FellowshipMember(
                active.gameplayId(),
                active.ownerId(),
                active.displayName,
                now,
                fellowship.nextColor());
        if (!fellowship.addMember(member)) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INTERNAL_ERROR, fellowship, invitation);
        }
        try {
            fellowshipData.saveFellowship(fellowship);
            invitationData.removeInvitationsForFellowshipAndTarget(
                    fellowship.getFellowshipId(), active.gameplayId());
            if (fellowship.isFull(FellowshipService.memberLimit())) {
                invitationData.removeInvitationsForFellowship(fellowship.getFellowshipId());
            }
            return FellowshipInvitationOperationResult.success(
                    true, fellowship, invitation, member);
        } catch (RuntimeException exception) {
            this.fellowshipService.logFailure(
                    "accept invitation", player, exception);
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INTERNAL_ERROR, fellowship, invitation);
        }
    }

    FellowshipInvitationOperationResult declineInvitation(
            EntityPlayerMP player,
            FellowshipService.ActiveIdentityContext active,
            UUID invitationId) {
        FellowshipInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY, null, null);
        }
        FellowshipInvitation invitation = invitationData.getInvitation(invitationId);
        if (invitation == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_NOT_FOUND, null, null);
        }
        if (!active.gameplayId().equals(
                invitation.getTargetIdentityId())
                || !player.getUniqueID().equals(invitation.getTargetOwnerId())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_TARGET_MISMATCH, null, null);
        }
        FellowshipWorldData fellowshipData = this.fellowshipService.getFellowshipData(player.worldObj);
        Fellowship fellowship = fellowshipData == null ? null
                : fellowshipData.getFellowship(invitation.getFellowshipId());
        invitationData.removeInvitation(invitationId);
        long now = System.currentTimeMillis();
        if (invitation.isExpired(now)) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_EXPIRED,
                    true,
                    fellowship,
                    invitation);
        }
        FellowshipDeclines.declined(invitation.getInvitingOwnerId(),
                invitation.getTargetOwnerId(), now);
        return FellowshipInvitationOperationResult.success(
                true, fellowship, invitation, null);
    }

    FellowshipInvitationOperationResult cancelInvitation(
            EntityPlayerMP player,
            Fellowship fellowship,
            UUID invitationId) {
        FellowshipInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY, fellowship, null);
        }
        FellowshipInvitation invitation = invitationData.getInvitation(invitationId);
        if (invitation == null
                || !fellowship.getFellowshipId().equals(invitation.getFellowshipId())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_NOT_FOUND, fellowship, null);
        }
        invitationData.removeInvitation(invitationId);
        if (invitation.isExpired(System.currentTimeMillis())) {
            return FellowshipInvitationOperationResult.failure(
                    FellowshipErrorId.INVITATION_EXPIRED,
                    true,
                    fellowship,
                    invitation);
        }
        return FellowshipInvitationOperationResult.success(
                true, fellowship, invitation, null);
    }

    FellowshipInvitationState getInvitationState(
            EntityPlayerMP player,
            FellowshipService.ActiveIdentityContext active,
            FellowshipWorldData fellowshipData) {
        FellowshipInvitationWorldData invitationData = getWritableData(player.worldObj);
        if (invitationData == null) {
            return FellowshipInvitationState.failure(
                    FellowshipErrorId.INVITATION_STORAGE_READ_ONLY);
        }
        pruneInvalidInvitations(
                fellowshipData,
                invitationData,
                active.characterData,
                System.currentTimeMillis());

        List<Fellowship> fellowships = fellowshipData.getFellowshipsForIdentity(
                active.gameplayId());
        List<FellowshipInvitation> outgoing = new ArrayList<FellowshipInvitation>();
        for (Fellowship fellowship : fellowships) {
            if (fellowship.canManage(active.gameplayId())) {
                outgoing.addAll(invitationData.getInvitationsForFellowship(
                        fellowship.getFellowshipId()));
            }
        }
        List<FellowshipInvitation> incoming =
                invitationData.getInvitationsForTargetIdentity(active.gameplayId());
        Map<UUID, String> names = new HashMap<UUID, String>();
        for (FellowshipInvitation invitation : incoming) {
            Fellowship inviting = fellowshipData.getFellowship(invitation.getFellowshipId());
            if (inviting != null) {
                names.put(inviting.getFellowshipId(), inviting.getName());
            }
        }
        for (Fellowship fellowship : fellowships) {
            names.put(fellowship.getFellowshipId(), fellowship.getName());
        }
        Fellowship travelling = fellowshipData.getTravellingFellowship(active.gameplayId());
        return FellowshipInvitationState.success(
                active.gameplayId(),
                fellowships,
                travelling == null ? null : travelling.getFellowshipId(),
                FellowshipService.createRefusal(player, active.gameplayId(), fellowships),
                incoming,
                outgoing,
                names);
    }

    int pruneInvalidInvitations(
            FellowshipWorldData fellowshipData,
            FellowshipInvitationWorldData invitationData,
            CharacterWorldData characterData,
            long now) {
        int removed = invitationData.removeExpired(now);
        CharacterIndex index =
                characterData.characterIndex();
        List<FellowshipInvitation> invitations =
                new ArrayList<FellowshipInvitation>(invitationData.getInvitations());
        for (FellowshipInvitation invitation : invitations) {
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
            if (isStale(invitation, fellowshipData)
                    && invitationData.removeInvitation(
                    invitation.getInvitationId()) != null) {
                removed++;
            }
        }
        return removed;
    }

    /** How many invitations {@link #pruneInvalidInvitations} would remove, removing none. */
    int countInvalidInvitations(
            FellowshipWorldData fellowshipData,
            FellowshipInvitationWorldData invitationData,
            CharacterWorldData characterData,
            long now) {
        int count = 0;
        CharacterIndex index = characterData.characterIndex();
        for (FellowshipInvitation invitation : invitationData.getInvitations()) {
            if (invitation.isExpired(now)
                    || getInvitationCorruptionReason(invitation, index) != null
                    || isStale(invitation, fellowshipData)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether an invitation no longer stands: its fellowship is gone or full,
     * the one who sent it neither leads nor guides it any more, or the
     * invited identity is in it now.
     */
    private static boolean isStale(FellowshipInvitation invitation,
                                   FellowshipWorldData fellowshipData) {
        Fellowship fellowship = fellowshipData.getFellowship(invitation.getFellowshipId());
        return fellowship == null
                || fellowship.isFull(FellowshipService.memberLimit())
                || !fellowship.canManage(invitation.getInvitingIdentityId())
                || fellowship.containsMember(invitation.getTargetIdentityId());
    }

    private FellowshipErrorId validateInvitationForAcceptance(
            FellowshipInvitation invitation,
            Fellowship fellowship,
            FellowshipWorldData fellowshipData,
            CharacterWorldData characterData) {
        if (fellowship == null) {
            return FellowshipErrorId.INVITATION_INVALID;
        }
        if (fellowship.isFull(FellowshipService.memberLimit())) {
            return FellowshipErrorId.FELLOWSHIP_FULL;
        }
        if (!fellowship.canManage(invitation.getInvitingIdentityId())) {
            return FellowshipErrorId.INVITATION_INVALID;
        }
        if (fellowship.containsMember(invitation.getTargetIdentityId())) {
            return FellowshipErrorId.TARGET_ALREADY_MEMBER;
        }
        // Another character of the account may have joined while this
        // invitation waited.
        if (fellowship.hasMemberOwnedBy(invitation.getTargetOwnerId())) {
            return FellowshipErrorId.ACCOUNT_ALREADY_IN_FELLOWSHIP;
        }
        CharacterIndex index =
                characterData.characterIndex();
        if (getInvitationCorruptionReason(invitation, index) != null) {
            return FellowshipErrorId.INVITATION_INVALID;
        }
        return FellowshipErrorId.NONE;
    }

    private String getInvitationCorruptionReason(
            FellowshipInvitation invitation,
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

    private UUID createUniqueInvitationId(FellowshipInvitationWorldData data) {
        for (int attempt = 0; attempt < UUID_GENERATION_ATTEMPTS; attempt++) {
            UUID invitationId = UUID.randomUUID();
            if (!data.containsInvitation(invitationId)) {
                return invitationId;
            }
        }
        return null;
    }

    private long safeExpiration(long now) {
        return now > Long.MAX_VALUE - FellowshipService.INVITATION_LIFETIME_MILLIS
                ? Long.MAX_VALUE
                : now + FellowshipService.INVITATION_LIFETIME_MILLIS;
    }

}
