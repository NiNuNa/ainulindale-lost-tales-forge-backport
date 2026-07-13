package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.RoleplayCharacter;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
            RoleplayCharacter invitingCharacter,
            CharacterWorldData characterData,
            PartyWorldData partyData,
            Party party,
            UUID targetOwnerId) {
        if (targetOwnerId == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVALID_TARGET, party, null);
        }
        if (party.isFull()) {
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

        EntityPlayerMP targetPlayer = findOnlinePlayer(targetOwnerId);
        if (targetPlayer == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.TARGET_OFFLINE, party, null);
        }
        PartyService.ActiveCharacterContext target =
                this.partyService.resolveActiveCharacter(targetPlayer);
        if (!target.isValid()) {
            PartyErrorId error = target.errorId == PartyErrorId.NO_ACTIVE_CHARACTER
                    ? PartyErrorId.TARGET_NO_ACTIVE_CHARACTER
                    : PartyErrorId.INVALID_TARGET;
            return PartyInvitationOperationResult.failure(error, party, null);
        }
        UUID targetCharacterId = target.character.getCharacterId();
        if (invitingCharacter.getCharacterId().equals(targetCharacterId)) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.CANNOT_INVITE_SELF, party, null);
        }
        Party targetParty = partyData.getPartyForCharacter(targetCharacterId);
        if (targetParty != null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.TARGET_ALREADY_IN_PARTY, party, null);
        }
        if (invitationData.hasInvitationForPartyAndTarget(
                party.getPartyId(), targetCharacterId)) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_ALREADY_EXISTS, party, null);
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
                invitingCharacter.getCharacterId(),
                invitingCharacter.getOwnerId(),
                invitingCharacter.getName(),
                target.character.getCharacterId(),
                target.character.getOwnerId(),
                target.character.getName(),
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
            PartyService.ActiveCharacterContext active,
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
        if (!active.character.getCharacterId().equals(
                invitation.getTargetCharacterId())
                || !player.getUniqueID().equals(invitation.getTargetOwnerId())) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_TARGET_MISMATCH,
                    partyData.getParty(invitation.getPartyId()),
                    null);
        }

        PartyService.CharacterIndex acceptanceIndex =
                this.partyService.buildCharacterIndex(active.characterData);
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

        Party existingParty = partyData.getPartyForCharacter(
                active.character.getCharacterId());
        if (existingParty != null) {
            invitationData.removeInvitationsForTargetCharacter(
                    active.character.getCharacterId());
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
        if (color == null || party.isFull()) {
            invitationData.removeInvitationsForParty(party.getPartyId());
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.PARTY_FULL, true, party, invitation);
        }
        PartyMember joined = new PartyMember(
                active.character.getCharacterId(),
                active.character.getOwnerId(),
                active.character.getName(),
                now,
                color);
        Party updatedParty = copyPartyWithAdditionalMember(party, joined);
        if (updatedParty == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, party, invitation);
        }
        try {
            partyData.saveParty(updatedParty);
            invitationData.removeInvitationsForTargetCharacter(
                    active.character.getCharacterId());
            if (updatedParty.isFull()) {
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
            PartyService.ActiveCharacterContext active,
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
        if (!active.character.getCharacterId().equals(
                invitation.getTargetCharacterId())
                || !player.getUniqueID().equals(invitation.getTargetOwnerId())) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INVITATION_TARGET_MISMATCH, null, null);
        }
        PartyWorldData partyData = this.partyService.getPartyData(player.worldObj);
        Party party = partyData == null ? null
                : partyData.getParty(invitation.getPartyId());
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
            PartyService.ActiveCharacterContext active,
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

        Party party = partyData.getPartyForCharacter(
                active.character.getCharacterId());
        List<PartyInvitation> outgoing = new ArrayList<PartyInvitation>();
        if (party != null && active.character.getCharacterId().equals(
                party.getLeaderCharacterId())) {
            outgoing.addAll(invitationData.getInvitationsForParty(
                    party.getPartyId()));
        }
        return PartyInvitationState.success(
                active.character.getCharacterId(),
                party,
                invitationData.getInvitationsForTargetCharacter(
                        active.character.getCharacterId()),
                outgoing);
    }

    int pruneInvalidInvitations(
            PartyWorldData partyData,
            PartyInvitationWorldData invitationData,
            CharacterWorldData characterData,
            long now) {
        int removed = invitationData.removeExpired(now);
        PartyService.CharacterIndex index =
                this.partyService.buildCharacterIndex(characterData);
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
            Party party = partyData.getParty(invitation.getPartyId());
            boolean stale = party == null
                    || party.isFull()
                    || !party.containsMember(invitation.getInvitingCharacterId())
                    || !invitation.getInvitingCharacterId().equals(
                    party.getLeaderCharacterId())
                    || partyData.getPartyForCharacter(
                    invitation.getTargetCharacterId()) != null;
            if (stale && invitationData.removeInvitation(
                    invitation.getInvitationId()) != null) {
                removed++;
            }
        }
        return removed;
    }

    private PartyErrorId validateInvitationForAcceptance(
            PartyInvitation invitation,
            Party party,
            PartyWorldData partyData,
            CharacterWorldData characterData) {
        if (party == null) {
            return PartyErrorId.INVITATION_INVALID;
        }
        if (party.isFull()) {
            return PartyErrorId.PARTY_FULL;
        }
        if (!party.containsMember(invitation.getInvitingCharacterId())
                || !invitation.getInvitingCharacterId().equals(
                party.getLeaderCharacterId())) {
            return PartyErrorId.INVITATION_INVALID;
        }
        if (partyData.getPartyForCharacter(
                invitation.getTargetCharacterId()) != null) {
            return PartyErrorId.TARGET_ALREADY_IN_PARTY;
        }
        PartyService.CharacterIndex index =
                this.partyService.buildCharacterIndex(characterData);
        if (getInvitationCorruptionReason(invitation, index) != null) {
            return PartyErrorId.INVITATION_INVALID;
        }
        return PartyErrorId.NONE;
    }

    private String getInvitationCorruptionReason(
            PartyInvitation invitation,
            PartyService.CharacterIndex index) {
        UUID invitingId = invitation.getInvitingCharacterId();
        UUID targetId = invitation.getTargetCharacterId();
        if (index.ambiguousCharacterIds.contains(invitingId)) {
            return "ambiguous_inviting_character_uuid";
        }
        if (index.ambiguousCharacterIds.contains(targetId)) {
            return "ambiguous_target_character_uuid";
        }
        RoleplayCharacter inviting = index.characters.get(invitingId);
        RoleplayCharacter target = index.characters.get(targetId);
        if (inviting == null) {
            return "missing_inviting_character";
        }
        if (target == null) {
            return "missing_target_character";
        }
        if (!inviting.getOwnerId().equals(invitation.getInvitingOwnerId())) {
            return "inviting_owner_mismatch";
        }
        if (!target.getOwnerId().equals(invitation.getTargetOwnerId())) {
            return "target_owner_mismatch";
        }
        return null;
    }

    private Party copyPartyWithAdditionalMember(
            Party party, PartyMember member) {
        if (party == null || member == null || party.isFull()
                || party.containsMember(member.getCharacterId())
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
                party.getLeaderCharacterId(),
                members,
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

    private EntityPlayerMP findOnlinePlayer(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }
        List<?> players = server.getConfigurationManager().playerEntityList;
        if (players == null) {
            return null;
        }
        for (Object value : players) {
            if (value instanceof EntityPlayerMP) {
                EntityPlayerMP candidate = (EntityPlayerMP) value;
                if (ownerId.equals(candidate.getUniqueID())) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
