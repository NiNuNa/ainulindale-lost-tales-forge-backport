package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyGoHereMarker;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.storage.PartyGoHereMarkerStorage;
import com.ninuna.losttales.party.storage.PartyGoHereMarkerWorldData;
import com.ninuna.losttales.party.storage.PartyInvitationWorldData;
import com.ninuna.losttales.party.storage.PartyStorage;
import com.ninuna.losttales.party.storage.PartyWorldData;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Single authoritative mutation boundary for character-based parties.
 *
 * Public methods must run on the logical server thread. Every mutating method
 * is synchronized so invitation acceptance and the four-member limit remain
 * atomic even when multiple network requests arrive in the same tick.
 */
public final class PartyService {

    public static final long INVITATION_LIFETIME_MILLIS = 5L * 60L * 1000L;

    private static final int UUID_GENERATION_ATTEMPTS = 8;
    private static final PartyService INSTANCE = new PartyService();

    private final PartyInvitationCoordinator invitationCoordinator =
            new PartyInvitationCoordinator(this);

    private PartyService() {}

    public static PartyService getInstance() {
        return INSTANCE;
    }

    public synchronized PartyOperationResult createParty(EntityPlayerMP player) {
        ActiveCharacterContext context = resolveActiveCharacter(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, null);
        }
        PartyWorldData partyData = getPartyData(player.worldObj);
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (partyData == null) {
            return PartyOperationResult.failure(PartyErrorId.INTERNAL_ERROR, null);
        }
        if (partyData.isReadOnlyForNewerVersion()) {
            return PartyOperationResult.failure(
                    PartyErrorId.PARTY_STORAGE_READ_ONLY, null);
        }
        if (invitationData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, null);
        }
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, null);
        }
        if (!ensurePartyIntegrity(player.worldObj, partyData,
                context.characterData)) {
            return PartyOperationResult.failure(
                    PartyErrorId.CHARACTER_STORAGE_READ_ONLY, null);
        }
        Party existing = partyData.getPartyForCharacter(
                context.character.getCharacterId());
        if (existing != null) {
            return PartyOperationResult.failure(
                    PartyErrorId.ALREADY_IN_PARTY, existing);
        }

        UUID partyId = createUniquePartyId(partyData);
        if (partyId == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, null);
        }
        long now = System.currentTimeMillis();
        PartyMember leader = new PartyMember(
                context.character.getCharacterId(),
                context.character.getOwnerId(),
                context.character.getName(),
                now,
                PartyColor.GREEN);
        Party party = Party.createNew(partyId, leader, now);
        try {
            partyData.saveParty(party);
            invitationData.removeInvitationsForTargetCharacter(
                    context.character.getCharacterId());
            return PartyOperationResult.success(true, party, leader);
        } catch (RuntimeException exception) {
            logFailure("create", player, exception);
            return PartyOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, null);
        }
    }

    public synchronized PartyOperationResult leaveParty(EntityPlayerMP player) {
        return leavePartyInternal(player, -1L, false);
    }

    public synchronized PartyOperationResult leaveParty(EntityPlayerMP player,
                                                         long expectedPartyRevision) {
        return leavePartyInternal(player, expectedPartyRevision, true);
    }

    private PartyOperationResult leavePartyInternal(EntityPlayerMP player,
                                                     long expectedPartyRevision,
                                                     boolean requireRevision) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision, requireRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, context.party);
        }
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, context.party);
        }

        UUID leavingCharacterId = context.character.getCharacterId();
        PartyMember leaving = context.party.getMember(leavingCharacterId);
        boolean leaderLeaving = leavingCharacterId.equals(
                context.party.getLeaderCharacterId());
        if (leaderLeaving) {
            invitationData.removeInvitationsForParty(
                    context.party.getPartyId());
        }
        invitationData.removeInvitationsInvolvingCharacter(leavingCharacterId);
        if (context.party.getMemberCount() == 1) {
            markerData.removeMarkersForParty(context.party.getPartyId());
            context.partyData.removeParty(context.party.getPartyId());
            return PartyOperationResult.disbanded(leaving);
        }
        markerData.removeMarker(leavingCharacterId);
        context.party.removeMember(leavingCharacterId);
        context.partyData.saveParty(context.party);
        return PartyOperationResult.success(true, context.party, leaving);
    }

    public synchronized PartyOperationResult removeMember(
            EntityPlayerMP player, UUID targetCharacterId) {
        return removeMemberInternal(player, -1L, targetCharacterId, false);
    }

    public synchronized PartyOperationResult removeMember(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID targetCharacterId) {
        return removeMemberInternal(
                player, expectedPartyRevision, targetCharacterId, true);
    }

    private PartyOperationResult removeMemberInternal(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID targetCharacterId,
            boolean requireRevision) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision, requireRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        if (!isLeader(context)) {
            return PartyOperationResult.failure(
                    PartyErrorId.NOT_LEADER, context.party);
        }
        PartyMember target = context.party.getMember(targetCharacterId);
        if (target == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.TARGET_NOT_MEMBER, context.party);
        }
        if (targetCharacterId.equals(context.party.getLeaderCharacterId())) {
            return PartyOperationResult.failure(
                    PartyErrorId.CANNOT_REMOVE_LEADER, context.party);
        }
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, context.party);
        }
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, context.party);
        }

        invitationData.removeInvitationsInvolvingCharacter(targetCharacterId);
        markerData.removeMarker(targetCharacterId);
        context.party.removeMember(targetCharacterId);
        context.partyData.saveParty(context.party);
        return PartyOperationResult.success(true, context.party, target);
    }

    public synchronized PartyOperationResult disbandParty(EntityPlayerMP player) {
        return disbandPartyInternal(player, -1L, false);
    }

    public synchronized PartyOperationResult disbandParty(
            EntityPlayerMP player, long expectedPartyRevision) {
        return disbandPartyInternal(player, expectedPartyRevision, true);
    }

    private PartyOperationResult disbandPartyInternal(
            EntityPlayerMP player,
            long expectedPartyRevision,
            boolean requireRevision) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision, requireRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        if (!isLeader(context)) {
            return PartyOperationResult.failure(
                    PartyErrorId.NOT_LEADER, context.party);
        }
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, context.party);
        }
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, context.party);
        }

        PartyMember leader = context.party.getLeader();
        invitationData.removeInvitationsForParty(context.party.getPartyId());
        for (PartyMember member : context.party.getMembers()) {
            invitationData.removeInvitationsInvolvingCharacter(
                    member.getCharacterId());
        }
        markerData.removeMarkersForParty(context.party.getPartyId());
        context.partyData.removeParty(context.party.getPartyId());
        return PartyOperationResult.disbanded(leader);
    }

    public synchronized PartyOperationResult transferLeadership(
            EntityPlayerMP player, UUID targetCharacterId) {
        return transferLeadershipInternal(
                player, -1L, targetCharacterId, false);
    }

    public synchronized PartyOperationResult transferLeadership(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID targetCharacterId) {
        return transferLeadershipInternal(
                player, expectedPartyRevision, targetCharacterId, true);
    }

    private PartyOperationResult transferLeadershipInternal(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID targetCharacterId,
            boolean requireRevision) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision, requireRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        if (!isLeader(context)) {
            return PartyOperationResult.failure(
                    PartyErrorId.NOT_LEADER, context.party);
        }
        PartyMember target = context.party.getMember(targetCharacterId);
        if (target == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.TARGET_NOT_MEMBER, context.party);
        }
        if (targetCharacterId.equals(context.party.getLeaderCharacterId())) {
            return PartyOperationResult.success(false, context.party, target);
        }
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, context.party);
        }

        invitationData.removeInvitationsForParty(context.party.getPartyId());
        context.party.transferLeadership(targetCharacterId);
        context.partyData.saveParty(context.party);
        return PartyOperationResult.success(true, context.party, target);
    }

    public synchronized PartyOperationResult setMemberColor(
            EntityPlayerMP player, PartyColor color) {
        return setMemberColorInternal(player, -1L, color, false);
    }

    public synchronized PartyOperationResult setMemberColor(
            EntityPlayerMP player,
            long expectedPartyRevision,
            PartyColor color) {
        return setMemberColorInternal(
                player, expectedPartyRevision, color, true);
    }

    private PartyOperationResult setMemberColorInternal(
            EntityPlayerMP player,
            long expectedPartyRevision,
            PartyColor color,
            boolean requireRevision) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision, requireRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        if (color == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVALID_COLOR, context.party);
        }
        UUID characterId = context.character.getCharacterId();
        PartyMember member = context.party.getMember(characterId);
        if (member.getColor() == color) {
            return PartyOperationResult.success(false, context.party, member);
        }
        if (!context.party.isColorAvailable(color, characterId)) {
            return PartyOperationResult.failure(
                    PartyErrorId.COLOR_IN_USE, context.party);
        }
        context.party.changeMemberColor(characterId, color);
        context.partyData.saveParty(context.party);
        return PartyOperationResult.success(
                true,
                context.party,
                context.party.getMember(characterId));
    }

    public synchronized PartyOperationResult setGoHereMarker(
            EntityPlayerMP player, long expectedPartyRevision) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision, true);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        if (player.isDead || !player.isEntityAlive()
                || !PartyGoHereMarker.isValidCoordinates(
                player.posX, player.posY, player.posZ)
                || !DimensionManager.isDimensionRegistered(player.dimension)) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVALID_MARKER_POSITION, context.party);
        }
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, context.party);
        }

        UUID characterId = context.character.getCharacterId();
        double x = quantizeTrackingCoordinate(player.posX);
        double y = quantizeTrackingCoordinate(player.posY);
        double z = quantizeTrackingCoordinate(player.posZ);
        PartyGoHereMarker previous = markerData.getMarker(characterId);
        if (previous != null
                && previous.getPartyId().equals(context.party.getPartyId())
                && previous.getDimensionId() == player.dimension
                && Double.doubleToLongBits(previous.getX())
                == Double.doubleToLongBits(x)
                && Double.doubleToLongBits(previous.getY())
                == Double.doubleToLongBits(y)
                && Double.doubleToLongBits(previous.getZ())
                == Double.doubleToLongBits(z)) {
            return PartyOperationResult.success(
                    false, context.party,
                    context.party.getMember(characterId));
        }
        PartyGoHereMarker marker = new PartyGoHereMarker(
                context.party.getPartyId(),
                characterId,
                player.dimension,
                x, y, z,
                System.currentTimeMillis());
        markerData.saveMarker(marker);
        return PartyOperationResult.success(
                true, context.party,
                context.party.getMember(characterId));
    }

    public synchronized PartyOperationResult removeGoHereMarker(
            EntityPlayerMP player, long expectedPartyRevision) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision, true);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, context.party);
        }
        UUID characterId = context.character.getCharacterId();
        PartyGoHereMarker existing = markerData.getMarker(characterId);
        if (existing == null
                || !context.party.getPartyId().equals(existing.getPartyId())) {
            return PartyOperationResult.success(
                    false, context.party,
                    context.party.getMember(characterId));
        }
        markerData.removeMarker(characterId);
        return PartyOperationResult.success(
                true, context.party,
                context.party.getMember(characterId));
    }

    public synchronized PartyInvitationOperationResult invitePlayer(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID targetOwnerId) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyInvitationOperationResult.failure(
                    context.errorId, context.party, null);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision, true);
        if (revisionError != PartyErrorId.NONE) {
            return PartyInvitationOperationResult.failure(
                    revisionError, context.party, null);
        }
        if (!isLeader(context)) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.NOT_LEADER, context.party, null);
        }
        return this.invitationCoordinator.invitePlayer(
                player,
                context.character,
                context.characterData,
                context.partyData,
                context.party,
                targetOwnerId);
    }

    public synchronized PartyInvitationOperationResult acceptInvitation(
            EntityPlayerMP player, UUID invitationId) {
        ActiveCharacterContext active = resolveActiveCharacter(player);
        if (!active.isValid()) {
            return PartyInvitationOperationResult.failure(
                    active.errorId, null, null);
        }
        PartyWorldData partyData = getPartyData(player.worldObj);
        if (partyData == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, null, null);
        }
        if (partyData.isReadOnlyForNewerVersion()) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.PARTY_STORAGE_READ_ONLY, null, null);
        }
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, null, null);
        }
        if (!ensurePartyIntegrity(
                player.worldObj, partyData, active.characterData)) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.CHARACTER_STORAGE_READ_ONLY, null, null);
        }
        pruneInvalidGoHereMarkers(partyData, markerData);
        return this.invitationCoordinator.acceptInvitation(
                player, active, partyData, invitationId);
    }

    public synchronized PartyInvitationOperationResult declineInvitation(
            EntityPlayerMP player, UUID invitationId) {
        ActiveCharacterContext active = resolveActiveCharacter(player);
        if (!active.isValid()) {
            return PartyInvitationOperationResult.failure(
                    active.errorId, null, null);
        }
        return this.invitationCoordinator.declineInvitation(
                player, active, invitationId);
    }

    public synchronized PartyInvitationOperationResult cancelInvitation(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID invitationId) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyInvitationOperationResult.failure(
                    context.errorId, context.party, null);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision, true);
        if (revisionError != PartyErrorId.NONE) {
            return PartyInvitationOperationResult.failure(
                    revisionError, context.party, null);
        }
        if (!isLeader(context)) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.NOT_LEADER, context.party, null);
        }
        return this.invitationCoordinator.cancelInvitation(
                player, context.party, invitationId);
    }

    public synchronized PartyInvitationState getInvitationState(
            EntityPlayerMP player) {
        ActiveCharacterContext active = resolveActiveCharacter(player);
        if (!active.isValid()) {
            return PartyInvitationState.failure(active.errorId);
        }
        PartyWorldData partyData = getPartyData(player.worldObj);
        if (partyData == null) {
            return PartyInvitationState.failure(PartyErrorId.INTERNAL_ERROR);
        }
        if (partyData.isReadOnlyForNewerVersion()) {
            return PartyInvitationState.failure(
                    PartyErrorId.PARTY_STORAGE_READ_ONLY);
        }
        if (!ensurePartyIntegrity(
                player.worldObj, partyData, active.characterData)) {
            return PartyInvitationState.failure(
                    PartyErrorId.CHARACTER_STORAGE_READ_ONLY);
        }
        return this.invitationCoordinator.getInvitationState(
                player, active, partyData);
    }

    public synchronized Party getPartyForActiveCharacter(EntityPlayerMP player) {
        PartyContext context = resolvePartyContext(player);
        return context.isValid() ? context.party : null;
    }

    /**
     * Removes all party and invitation references before a character record is
     * deleted. Deletion is rejected if either store cannot be updated safely.
     */
    public synchronized PartyOperationResult removeCharacterForDeletion(
            World world, RoleplayCharacter character) {
        if (world == null || world.isRemote || character == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVALID_PLAYER, null);
        }
        PartyWorldData partyData = getPartyData(world);
        CharacterWorldData characterData = getCharacterData(world);
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(world);
        if (partyData == null || characterData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, null);
        }
        if (partyData.isReadOnlyForNewerVersion()) {
            return PartyOperationResult.failure(
                    PartyErrorId.PARTY_STORAGE_READ_ONLY, null);
        }
        if (invitationData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, null);
        }
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, null);
        }
        if (!ensurePartyIntegrity(world, partyData, characterData)) {
            return PartyOperationResult.failure(
                    PartyErrorId.CHARACTER_STORAGE_READ_ONLY, null);
        }

        UUID characterId = character.getCharacterId();
        int removedInvitations =
                invitationData.removeInvitationsInvolvingCharacter(characterId);
        PartyGoHereMarker removedMarker = markerData.removeMarker(characterId);
        Party party = partyData.getPartyForCharacter(characterId);
        if (party == null) {
            return PartyOperationResult.success(
                    removedInvitations > 0 || removedMarker != null,
                    null, null);
        }
        PartyMember removed = party.getMember(characterId);
        boolean removingLeader = characterId.equals(
                party.getLeaderCharacterId());
        if (removingLeader || party.getMemberCount() == 1) {
            invitationData.removeInvitationsForParty(party.getPartyId());
        }
        if (party.getMemberCount() == 1) {
            markerData.removeMarkersForParty(party.getPartyId());
            partyData.removeParty(party.getPartyId());
            return PartyOperationResult.disbanded(removed);
        }
        party.removeMember(characterId);
        partyData.saveParty(party);
        return PartyOperationResult.success(true, party, removed);
    }

    /** Validates both persistent stores and removes stale invitations. */
    public synchronized boolean ensureIntegrity(World world) {
        PartyWorldData partyData = getPartyData(world);
        CharacterWorldData characterData = getCharacterData(world);
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(world);
        if (partyData == null || characterData == null
                || invitationData == null || markerData == null) {
            return false;
        }
        if (!ensurePartyIntegrity(world, partyData, characterData)) {
            return false;
        }
        this.invitationCoordinator.pruneInvalidInvitations(
                partyData,
                invitationData,
                characterData,
                System.currentTimeMillis());
        pruneInvalidGoHereMarkers(partyData, markerData);
        return true;
    }

    /** Periodic expiration and referential-integrity cleanup. */
    public synchronized int pruneInvalidInvitations(World world) {
        PartyWorldData partyData = getPartyData(world);
        CharacterWorldData characterData = getCharacterData(world);
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(world);
        if (partyData == null || characterData == null
                || invitationData == null || markerData == null
                || !ensurePartyIntegrity(world, partyData, characterData)) {
            return -1;
        }
        int removedInvitations =
                this.invitationCoordinator.pruneInvalidInvitations(
                        partyData,
                        invitationData,
                        characterData,
                        System.currentTimeMillis());
        int removedMarkers = pruneInvalidGoHereMarkers(
                partyData, markerData);
        return removedInvitations + removedMarkers;
    }

    private boolean ensurePartyIntegrity(World world,
                                         PartyWorldData partyData,
                                         CharacterWorldData characterData) {
        if (partyData.areCharacterReferencesValidated()) {
            return true;
        }
        if (partyData.isReadOnlyForNewerVersion()
                || characterData.isReadOnlyForNewerVersion()) {
            return false;
        }

        CharacterIndex index = buildCharacterIndex(characterData);
        List<Party> parties = new ArrayList<Party>(partyData.getParties());
        for (Party party : parties) {
            boolean changed = false;
            List<PartyMember> members =
                    new ArrayList<PartyMember>(party.getMembers());
            for (PartyMember member : members) {
                UUID characterId = member.getCharacterId();
                RoleplayCharacter character = index.characters.get(characterId);
                String removalReason = null;
                if (index.ambiguousCharacterIds.contains(characterId)) {
                    removalReason = "ambiguous_character_uuid";
                } else if (character == null) {
                    removalReason = "missing_character";
                } else if (!character.getOwnerId().equals(member.getOwnerId())) {
                    removalReason = "character_owner_mismatch";
                }

                if (removalReason != null) {
                    party.removeMember(characterId);
                    partyData.quarantine(
                            removalReason,
                            party.getPartyId(),
                            characterId);
                    changed = true;
                    continue;
                }
                if (party.refreshMemberIdentity(
                        characterId,
                        character.getOwnerId(),
                        character.getName())) {
                    changed = true;
                }
            }

            if (party.getMemberCount() == 0) {
                partyData.removeParty(party.getPartyId());
                continue;
            }
            if (party.repairLeaderIfNecessary()) {
                changed = true;
            }
            if (changed) {
                partyData.saveParty(party);
            }
        }
        partyData.markCharacterReferencesValidated();
        return true;
    }

    private PartyContext resolvePartyContext(EntityPlayerMP player) {
        ActiveCharacterContext active = resolveActiveCharacter(player);
        if (!active.isValid()) {
            return PartyContext.failure(active.errorId);
        }
        PartyWorldData partyData = getPartyData(player.worldObj);
        if (partyData == null) {
            return PartyContext.failure(PartyErrorId.INTERNAL_ERROR);
        }
        if (partyData.isReadOnlyForNewerVersion()) {
            return PartyContext.failure(
                    PartyErrorId.PARTY_STORAGE_READ_ONLY);
        }
        if (!ensurePartyIntegrity(
                player.worldObj, partyData, active.characterData)) {
            return PartyContext.failure(
                    PartyErrorId.CHARACTER_STORAGE_READ_ONLY);
        }
        Party party = partyData.getPartyForCharacter(
                active.character.getCharacterId());
        if (party == null) {
            return PartyContext.failure(PartyErrorId.NOT_IN_PARTY);
        }
        return PartyContext.success(
                active.character,
                active.characterData,
                partyData,
                party);
    }

    ActiveCharacterContext resolveActiveCharacter(
            EntityPlayerMP player) {
        if (player == null || player.worldObj == null) {
            return ActiveCharacterContext.failure(
                    PartyErrorId.INVALID_PLAYER);
        }
        if (player.worldObj.isRemote) {
            return ActiveCharacterContext.failure(
                    PartyErrorId.CLIENT_SIDE_REQUEST);
        }
        CharacterWorldData data = getCharacterData(player.worldObj);
        if (data == null) {
            return ActiveCharacterContext.failure(
                    PartyErrorId.INTERNAL_ERROR);
        }
        if (data.isReadOnlyForNewerVersion()) {
            return ActiveCharacterContext.failure(
                    PartyErrorId.CHARACTER_STORAGE_READ_ONLY);
        }
        CharacterRoster roster = data.getRoster(player.getUniqueID());
        if (roster == null || roster.getActiveCharacter() == null) {
            return ActiveCharacterContext.failure(
                    PartyErrorId.NO_ACTIVE_CHARACTER);
        }
        RoleplayCharacter character = roster.getActiveCharacter();
        int matches = countCharacters(data, character.getCharacterId());
        if (matches == 0) {
            return ActiveCharacterContext.failure(
                    PartyErrorId.CHARACTER_NOT_FOUND);
        }
        if (matches > 1) {
            return ActiveCharacterContext.failure(
                    PartyErrorId.CHARACTER_ID_AMBIGUOUS);
        }
        if (!player.getUniqueID().equals(character.getOwnerId())) {
            return ActiveCharacterContext.failure(
                    PartyErrorId.CHARACTER_NOT_FOUND);
        }
        return ActiveCharacterContext.success(data, character);
    }

    CharacterIndex buildCharacterIndex(CharacterWorldData data) {
        Map<UUID, RoleplayCharacter> characters =
                new HashMap<UUID, RoleplayCharacter>();
        Set<UUID> ambiguous = new HashSet<UUID>();
        for (CharacterRoster roster : data.getRosters()) {
            for (RoleplayCharacter character : roster.getCharacters()) {
                UUID characterId = character.getCharacterId();
                if (ambiguous.contains(characterId)) {
                    continue;
                }
                if (characters.containsKey(characterId)) {
                    characters.remove(characterId);
                    ambiguous.add(characterId);
                } else {
                    characters.put(characterId, character);
                }
            }
        }
        return new CharacterIndex(characters, ambiguous);
    }

    private int countCharacters(CharacterWorldData data, UUID characterId) {
        int count = 0;
        for (CharacterRoster roster : data.getRosters()) {
            if (roster.getCharacter(characterId) != null) {
                count++;
            }
        }
        return count;
    }

    private PartyErrorId validateRevision(Party party,
                                          long expectedRevision,
                                          boolean required) {
        if (!required) {
            return PartyErrorId.NONE;
        }
        if (expectedRevision < 0L) {
            return PartyErrorId.INVALID_REVISION;
        }
        return party != null && party.getRevision() == expectedRevision
                ? PartyErrorId.NONE
                : PartyErrorId.STALE_PARTY_REVISION;
    }

    private boolean isLeader(PartyContext context) {
        return context != null
                && context.character != null
                && context.party != null
                && context.character.getCharacterId().equals(
                context.party.getLeaderCharacterId());
    }

    private UUID createUniquePartyId(PartyWorldData data) {
        for (int attempt = 0; attempt < UUID_GENERATION_ATTEMPTS; attempt++) {
            UUID partyId = UUID.randomUUID();
            if (!data.containsParty(partyId)) {
                return partyId;
            }
        }
        return null;
    }

    PartyGoHereMarkerWorldData getGoHereMarkerData(World world) {
        try {
            return PartyGoHereMarkerStorage.get(world);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access party marker storage: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
    }

    private PartyGoHereMarkerWorldData getWritableGoHereMarkerData(
            World world) {
        PartyGoHereMarkerWorldData data = getGoHereMarkerData(world);
        return data == null || data.isReadOnlyForNewerVersion()
                ? null : data;
    }

    private int pruneInvalidGoHereMarkers(
            PartyWorldData partyData,
            PartyGoHereMarkerWorldData markerData) {
        int removed = 0;
        List<PartyGoHereMarker> markers =
                new ArrayList<PartyGoHereMarker>(markerData.getMarkers());
        for (PartyGoHereMarker marker : markers) {
            Party party = partyData.getParty(marker.getPartyId());
            String reason = null;
            if (party == null) {
                reason = "missing_party";
            } else if (!party.containsMember(marker.getOwnerCharacterId())) {
                reason = "owner_not_party_member";
            } else if (!DimensionManager.isDimensionRegistered(
                    marker.getDimensionId())) {
                reason = "unregistered_dimension";
            }
            if (reason != null) {
                markerData.quarantine(reason, marker);
                markerData.removeMarker(marker.getOwnerCharacterId());
                removed++;
            }
        }
        return removed;
    }

    static double quantizeTrackingCoordinate(double value) {
        return Math.floor(value * 4.0D + 0.5D) / 4.0D;
    }

    PartyWorldData getPartyData(World world) {
        try {
            return PartyStorage.get(world);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access party storage: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
    }

    private CharacterWorldData getCharacterData(World world) {
        try {
            return CharacterStorage.get(world);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access character storage for party operation: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
    }

    void logFailure(String action,
                    EntityPlayerMP player,
                    RuntimeException exception) {
        FMLLog.warning("[%s] Party %s failed for player %s: %s",
                LostTalesMetaData.MOD_ID,
                action,
                player == null ? "unknown" : player.getUniqueID(),
                exception.toString());
    }

    static final class ActiveCharacterContext {
        final CharacterWorldData characterData;
        final RoleplayCharacter character;
        final PartyErrorId errorId;

        private ActiveCharacterContext(CharacterWorldData characterData,
                                       RoleplayCharacter character,
                                       PartyErrorId errorId) {
            this.characterData = characterData;
            this.character = character;
            this.errorId = errorId;
        }

        private static ActiveCharacterContext success(
                CharacterWorldData data, RoleplayCharacter character) {
            return new ActiveCharacterContext(
                    data, character, PartyErrorId.NONE);
        }

        private static ActiveCharacterContext failure(PartyErrorId errorId) {
            return new ActiveCharacterContext(null, null, errorId);
        }

        boolean isValid() {
            return this.errorId == PartyErrorId.NONE
                    && this.characterData != null
                    && this.character != null;
        }
    }

    private static final class PartyContext {
        private final RoleplayCharacter character;
        private final CharacterWorldData characterData;
        private final PartyWorldData partyData;
        private final Party party;
        private final PartyErrorId errorId;

        private PartyContext(RoleplayCharacter character,
                             CharacterWorldData characterData,
                             PartyWorldData partyData,
                             Party party,
                             PartyErrorId errorId) {
            this.character = character;
            this.characterData = characterData;
            this.partyData = partyData;
            this.party = party;
            this.errorId = errorId;
        }

        private static PartyContext success(
                RoleplayCharacter character,
                CharacterWorldData characterData,
                PartyWorldData data,
                Party party) {
            return new PartyContext(
                    character,
                    characterData,
                    data,
                    party,
                    PartyErrorId.NONE);
        }

        private static PartyContext failure(PartyErrorId errorId) {
            return new PartyContext(null, null, null, null, errorId);
        }

        private boolean isValid() {
            return this.errorId == PartyErrorId.NONE
                    && this.character != null
                    && this.characterData != null
                    && this.partyData != null
                    && this.party != null;
        }
    }

    static final class CharacterIndex {
        final Map<UUID, RoleplayCharacter> characters;
        final Set<UUID> ambiguousCharacterIds;

        private CharacterIndex(Map<UUID, RoleplayCharacter> characters,
                               Set<UUID> ambiguousCharacterIds) {
            this.characters = characters;
            this.ambiguousCharacterIds = ambiguousCharacterIds;
        }
    }
}
