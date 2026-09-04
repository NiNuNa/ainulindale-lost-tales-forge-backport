package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyPersonalMarkerOwner;
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
        if (!ensurePartyIntegrity(player.worldObj, partyData,
                context.characterData)) {
            return PartyOperationResult.failure(
                    PartyErrorId.CHARACTER_STORAGE_READ_ONLY, null);
        }
        Party existing = partyData.getPartyForCharacter(context.gameplayId());
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
                context.gameplayId(),
                context.ownerId(),
                context.displayName,
                now,
                PartyColor.GREEN);
        Party party = Party.createNew(partyId, leader, now);
        try {
            partyData.saveParty(party);
            invitationData.removeInvitationsForTargetCharacter(
                    context.gameplayId());
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
        UUID leavingCharacterId = context.gameplayId();
        PartyMember leaving = context.party.getMember(leavingCharacterId);
        boolean leaderLeaving = leavingCharacterId.equals(
                context.party.getLeaderCharacterId());
        if (leaderLeaving) {
            invitationData.removeInvitationsForParty(
                    context.party.getPartyId());
        }
        invitationData.removeInvitationsInvolvingCharacter(leavingCharacterId);
        if (context.party.getMemberCount() == 1) {
            context.partyData.removeParty(context.party.getPartyId());
            return PartyOperationResult.disbanded(leaving);
        }
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
        invitationData.removeInvitationsInvolvingCharacter(targetCharacterId);
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
        PartyMember leader = context.party.getLeader();
        invitationData.removeInvitationsForParty(context.party.getPartyId());
        for (PartyMember member : context.party.getMembers()) {
            invitationData.removeInvitationsInvolvingCharacter(
                    member.getCharacterId());
        }
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
        UUID characterId = context.gameplayId();
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
            EntityPlayerMP player, long expectedPartyRevision,
            boolean hasMarkerPosition, int markerDimensionId,
            double markerX, double markerZ) {
        PersonalMarkerContext owner = resolvePersonalMarkerOwner(player);
        if (!owner.isValid()) {
            return PartyOperationResult.failure(owner.errorId, null);
        }
        PartyWorldData partyData = getPartyData(player.worldObj);
        Party party = partyData == null
                ? null : partyData.getPartyForCharacter(owner.ownerId);
        if (player.isDead || !player.isEntityAlive()
                || !hasMarkerPosition
                || markerDimensionId != player.dimension
                || !DimensionManager.isDimensionRegistered(markerDimensionId)
                || !PartyGoHereMarker.isValidCoordinates(
                markerX, player.posY, markerZ)) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVALID_MARKER_POSITION, party);
        }
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, party);
        }

        UUID characterId = owner.ownerId;
        double x = quantizeTrackingCoordinate(markerX);
        double y = quantizeTrackingCoordinate(player.posY);
        double z = quantizeTrackingCoordinate(markerZ);
        PartyGoHereMarker previous = markerData.getMarker(characterId);
        if (previous != null
                && previous.getDimensionId() == markerDimensionId
                && Double.doubleToLongBits(previous.getX())
                == Double.doubleToLongBits(x)
                && Double.doubleToLongBits(previous.getY())
                == Double.doubleToLongBits(y)
                && Double.doubleToLongBits(previous.getZ())
                == Double.doubleToLongBits(z)) {
            return PartyOperationResult.success(
                    false, party,
                    party == null ? null : party.getMember(characterId));
        }
        PartyGoHereMarker marker = new PartyGoHereMarker(
                party == null ? null : party.getPartyId(),
                characterId,
                markerDimensionId,
                x, y, z,
                System.currentTimeMillis());
        markerData.saveMarker(marker);
        return PartyOperationResult.success(
                true, party,
                party == null ? null : party.getMember(characterId));
    }

    public synchronized PartyOperationResult removeGoHereMarker(
            EntityPlayerMP player, long expectedPartyRevision) {
        PersonalMarkerContext owner = resolvePersonalMarkerOwner(player);
        if (!owner.isValid()) {
            return PartyOperationResult.failure(owner.errorId, null);
        }
        PartyWorldData partyData = getPartyData(player.worldObj);
        Party party = partyData == null
                ? null : partyData.getPartyForCharacter(owner.ownerId);
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, party);
        }
        UUID characterId = owner.ownerId;
        PartyGoHereMarker existing = markerData.getMarker(characterId);
        if (existing == null) {
            return PartyOperationResult.success(
                    false, party,
                    party == null ? null : party.getMember(characterId));
        }
        markerData.removeMarker(characterId);
        return PartyOperationResult.success(
                true, party,
                party == null ? null : party.getMember(characterId));
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
                context.active,
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
        if (!ensurePartyIntegrity(
                player.worldObj, partyData, active.characterData)) {
            return PartyInvitationOperationResult.failure(
                    PartyErrorId.CHARACTER_STORAGE_READ_ONLY, null, null);
        }
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
        pruneInvalidGoHereMarkers(characterData, markerData);
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
                characterData, markerData);
        return removedInvitations + removedMarkers;
    }

    boolean ensurePartyIntegrity(World world,
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
                // A member whose id is its own owner's is that account
                // playing as itself; it stands as long as the account has
                // a roster, exactly as a character stands while it exists.
                boolean accountMember = character == null
                        && characterId.equals(member.getOwnerId())
                        && index.isAccountOwner(characterId);
                String removalReason = null;
                if (index.ambiguousCharacterIds.contains(characterId)) {
                    removalReason = "ambiguous_character_uuid";
                } else if (character == null && !accountMember) {
                    removalReason = "missing_character";
                } else if (character != null
                        && !character.getOwnerId().equals(member.getOwnerId())) {
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
                String name = character != null ? character.getName()
                        : RoleplayCharacterIdentityHook.resolveGameplayName(characterId);
                if (name != null && name.length() > 0
                        && party.refreshMemberIdentity(
                                characterId, member.getOwnerId(), name)) {
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
        Party party = partyData.getPartyForCharacter(active.gameplayId());
        if (party == null) {
            return PartyContext.failure(PartyErrorId.NOT_IN_PARTY);
        }
        return PartyContext.success(active, partyData, party);
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
        // No roster yet, or no active character, is the account playing as
        // itself: a full identity, filed under the account's own id.
        CharacterRoster roster = data.getRoster(player.getUniqueID());
        RoleplayCharacter character = roster == null ? null : roster.getActiveCharacter();
        if (character == null) {
            return ActiveCharacterContext.success(data,
                    PlayableIdentity.account(player.getUniqueID()), null,
                    player.getCommandSenderName());
        }
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
        String name = character.getName() == null
                || character.getName().trim().length() == 0
                ? player.getCommandSenderName() : character.getName();
        return ActiveCharacterContext.success(data,
                PlayableIdentity.character(player.getUniqueID(), character.getCharacterId()),
                character, name);
    }

    CharacterIndex buildCharacterIndex(CharacterWorldData data) {
        Map<UUID, RoleplayCharacter> characters =
                new HashMap<UUID, RoleplayCharacter>();
        Set<UUID> ambiguous = new HashSet<UUID>();
        Set<UUID> rosterOwners = new HashSet<UUID>();
        for (CharacterRoster roster : data.getRosters()) {
            if (roster.getOwnerId() != null) {
                rosterOwners.add(roster.getOwnerId());
            }
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
        return new CharacterIndex(characters, ambiguous, rosterOwners);
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
                && context.isValid()
                && context.gameplayId().equals(
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
            CharacterWorldData characterData,
            PartyGoHereMarkerWorldData markerData) {
        int removed = 0;
        CharacterIndex characters = buildCharacterIndex(characterData);
        List<PartyGoHereMarker> markers =
                new ArrayList<PartyGoHereMarker>(markerData.getMarkers());
        for (PartyGoHereMarker marker : markers) {
            String reason = null;
            if (characters.ambiguousCharacterIds.contains(
                    marker.getOwnerCharacterId())) {
                reason = "ambiguous_owner_character";
            } else if (!characters.hasOwner(
                    marker.getOwnerCharacterId())) {
                // A marker filed under a player rather than a character is
                // not an orphan: that is who owns it while no character is
                // selected. Treating it as one deleted the marker from under
                // a character-less player a few seconds after they placed it.
                reason = "missing_owner_character";
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

    /**
     * Resolves who a personal marker belongs to: the identity being played,
     * which is the account itself when no character is. Unreadable storage,
     * an ambiguous or stolen character id are still refusals, because those
     * say the request cannot be trusted rather than who owns the marker.
     */
    PersonalMarkerContext resolvePersonalMarkerOwner(
            EntityPlayerMP player) {
        ActiveCharacterContext active = resolveActiveCharacter(player);
        if (!active.isValid()) {
            return PersonalMarkerContext.failure(active.errorId);
        }
        return PersonalMarkerContext.owned(
                PartyPersonalMarkerOwner.resolve(
                        active.identity.getCharacterId(), player.getUniqueID()),
                active.identity.getCharacterId());
    }

    /** Who a personal marker is filed under, and which character if any. */
    static final class PersonalMarkerContext {
        final UUID ownerId;
        final UUID characterId;
        final PartyErrorId errorId;

        private PersonalMarkerContext(
                UUID ownerId, UUID characterId, PartyErrorId errorId) {
            this.ownerId = ownerId;
            this.characterId = characterId;
            this.errorId = errorId;
        }

        private static PersonalMarkerContext owned(
                UUID ownerId, UUID characterId) {
            return new PersonalMarkerContext(
                    ownerId, characterId, PartyErrorId.NONE);
        }

        private static PersonalMarkerContext failure(PartyErrorId errorId) {
            return new PersonalMarkerContext(null, null,
                    errorId == PartyErrorId.NONE
                            ? PartyErrorId.INTERNAL_ERROR : errorId);
        }

        boolean isValid() {
            return this.errorId == PartyErrorId.NONE
                    && this.ownerId != null;
        }
    }

    /**
     * The identity a player is acting as in the party system: one of their
     * characters, or the account itself. Parties key members by the
     * identity's gameplay id, so the account is a member like any other.
     */
    static final class ActiveCharacterContext {
        final CharacterWorldData characterData;
        final PlayableIdentity identity;
        /** The active character; null when the identity is the account. */
        final RoleplayCharacter character;
        /** The name the identity goes by: the character's, else the account's. */
        final String displayName;
        final PartyErrorId errorId;

        private ActiveCharacterContext(CharacterWorldData characterData,
                                       PlayableIdentity identity,
                                       RoleplayCharacter character,
                                       String displayName,
                                       PartyErrorId errorId) {
            this.characterData = characterData;
            this.identity = identity;
            this.character = character;
            this.displayName = displayName;
            this.errorId = errorId;
        }

        private static ActiveCharacterContext success(
                CharacterWorldData data, PlayableIdentity identity,
                RoleplayCharacter character, String displayName) {
            return new ActiveCharacterContext(
                    data, identity, character, displayName, PartyErrorId.NONE);
        }

        private static ActiveCharacterContext failure(PartyErrorId errorId) {
            return new ActiveCharacterContext(null, null, null, "", errorId);
        }

        boolean isValid() {
            return this.errorId == PartyErrorId.NONE
                    && this.characterData != null
                    && this.identity != null;
        }

        /** The id the party system files this identity under. */
        UUID gameplayId() {
            return this.identity.getGameplayId();
        }

        UUID ownerId() {
            return this.identity.getOwnerId();
        }
    }

    private static final class PartyContext {
        private final ActiveCharacterContext active;
        private final PartyWorldData partyData;
        private final Party party;
        private final PartyErrorId errorId;

        private PartyContext(ActiveCharacterContext active,
                             PartyWorldData partyData,
                             Party party,
                             PartyErrorId errorId) {
            this.active = active;
            this.partyData = partyData;
            this.party = party;
            this.errorId = errorId;
        }

        private static PartyContext success(
                ActiveCharacterContext active,
                PartyWorldData data,
                Party party) {
            return new PartyContext(active, data, party, PartyErrorId.NONE);
        }

        private static PartyContext failure(PartyErrorId errorId) {
            return new PartyContext(null, null, null, errorId);
        }

        private boolean isValid() {
            return this.errorId == PartyErrorId.NONE
                    && this.active != null && this.active.isValid()
                    && this.partyData != null
                    && this.party != null;
        }

        private UUID gameplayId() {
            return this.active.gameplayId();
        }
    }

    static final class CharacterIndex {
        final Map<UUID, RoleplayCharacter> characters;
        final Set<UUID> ambiguousCharacterIds;
        /**
         * The players who own a roster. A personal marker may be filed under
         * one of these instead of under a character, because a player who has
         * no character selected owns their marker themselves.
         */
        final Set<UUID> rosterOwnerIds;

        private CharacterIndex(Map<UUID, RoleplayCharacter> characters,
                               Set<UUID> ambiguousCharacterIds,
                               Set<UUID> rosterOwnerIds) {
            this.characters = characters;
            this.ambiguousCharacterIds = ambiguousCharacterIds;
            this.rosterOwnerIds = rosterOwnerIds;
        }

        /** Whether a personal marker filed under this id still has an owner. */
        boolean hasOwner(UUID ownerId) {
            return ownerId != null
                    && (this.characters.containsKey(ownerId)
                            || this.rosterOwnerIds.contains(ownerId));
        }

        /** Whether the id is a player's own: the account as a playable identity. */
        boolean isAccountOwner(UUID id) {
            return id != null && this.rosterOwnerIds.contains(id);
        }
    }
}
