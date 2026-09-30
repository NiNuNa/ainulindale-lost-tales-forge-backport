package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterIndex;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityFilter;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import com.ninuna.losttales.config.LostTalesConfig;
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
import java.util.List;
import java.util.UUID;

/**
 * Single authoritative mutation boundary for parties.
 *
 * Public methods must run on the logical server thread. Every mutating method
 * is synchronized so invitation acceptance and the member limit stay atomic
 * even when several requests arrive in the same tick. Every change a player
 * asks for names the party revision it was made against, and a party changed
 * since refuses it.
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
        ActiveIdentityContext context = resolveActiveIdentity(player);
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
        Party existing = partyData.getPartyForIdentity(context.gameplayId());
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
            invitationData.removeInvitationsForTargetIdentity(
                    context.gameplayId());
            return PartyOperationResult.success(true, party, leader);
        } catch (RuntimeException exception) {
            logFailure("create", player, exception);
            return PartyOperationResult.failure(
                    PartyErrorId.INTERNAL_ERROR, null);
        }
    }

    public synchronized PartyOperationResult leaveParty(EntityPlayerMP player,
                                                         long expectedPartyRevision) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, context.party);
        }
        UUID leavingIdentityId = context.gameplayId();
        PartyMember leaving = context.party.getMember(leavingIdentityId);
        boolean leaderLeaving = leavingIdentityId.equals(
                context.party.getLeaderIdentityId());
        // A leader with others in the party hands it on first, so it never
        // loses its leader to a slip; alone, leaving ends the party.
        if (leaderLeaving && context.party.getMemberCount() > 1) {
            return PartyOperationResult.failure(
                    PartyErrorId.LEADER_MUST_HAND_OVER, context.party);
        }
        if (leaderLeaving) {
            invitationData.removeInvitationsForParty(
                    context.party.getPartyId());
        }
        invitationData.removeInvitationsInvolvingIdentity(leavingIdentityId);
        if (context.party.getMemberCount() == 1) {
            context.partyData.removeParty(context.party.getPartyId());
            return PartyOperationResult.disbanded(leaving);
        }
        context.party.removeMember(leavingIdentityId);
        context.partyData.saveParty(context.party);
        return PartyOperationResult.success(true, context.party, leaving);
    }

    public synchronized PartyOperationResult removeMember(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID targetIdentityId) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        if (!isLeader(context)) {
            return PartyOperationResult.failure(
                    PartyErrorId.NOT_LEADER, context.party);
        }
        PartyMember target = context.party.getMember(targetIdentityId);
        if (target == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.TARGET_NOT_MEMBER, context.party);
        }
        if (targetIdentityId.equals(context.party.getLeaderIdentityId())) {
            return PartyOperationResult.failure(
                    PartyErrorId.CANNOT_REMOVE_LEADER, context.party);
        }
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, context.party);
        }
        invitationData.removeInvitationsInvolvingIdentity(targetIdentityId);
        context.party.removeMember(targetIdentityId);
        context.partyData.saveParty(context.party);
        return PartyOperationResult.success(true, context.party, target);
    }

    public synchronized PartyOperationResult disbandParty(
            EntityPlayerMP player, long expectedPartyRevision) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision);
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
            invitationData.removeInvitationsInvolvingIdentity(
                    member.getIdentityId());
        }
        context.partyData.removeParty(context.party.getPartyId());
        return PartyOperationResult.disbanded(leader);
    }

    public synchronized PartyOperationResult transferLeadership(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID targetIdentityId) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        if (!isLeader(context)) {
            return PartyOperationResult.failure(
                    PartyErrorId.NOT_LEADER, context.party);
        }
        PartyMember target = context.party.getMember(targetIdentityId);
        if (target == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.TARGET_NOT_MEMBER, context.party);
        }
        if (targetIdentityId.equals(context.party.getLeaderIdentityId())) {
            return PartyOperationResult.success(false, context.party, target);
        }
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(player.worldObj);
        if (invitationData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVITATION_STORAGE_READ_ONLY, context.party);
        }

        invitationData.removeInvitationsForParty(context.party.getPartyId());
        context.party.transferLeadership(targetIdentityId);
        context.partyData.saveParty(context.party);
        return PartyOperationResult.success(true, context.party, target);
    }

    public synchronized PartyOperationResult setMemberColor(
            EntityPlayerMP player,
            long expectedPartyRevision,
            PartyColor color) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        if (color == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.INVALID_COLOR, context.party);
        }
        UUID identityId = context.gameplayId();
        PartyMember member = context.party.getMember(identityId);
        if (member.getColor() == color) {
            return PartyOperationResult.success(false, context.party, member);
        }
        if (!context.party.isColorAvailable(color, identityId)) {
            return PartyOperationResult.failure(
                    PartyErrorId.COLOR_IN_USE, context.party);
        }
        context.party.changeMemberColor(identityId, color);
        context.partyData.saveParty(context.party);
        return PartyOperationResult.success(
                true,
                context.party,
                context.party.getMember(identityId));
    }

    /**
     * The leader names the party, or takes its name away with an empty
     * one. The name is trimmed and must pass {@link #checkName}.
     */
    public synchronized PartyOperationResult renameParty(
            EntityPlayerMP player,
            long expectedPartyRevision,
            String requestedName) {
        PartyContext context = resolvePartyContext(player);
        if (!context.isValid()) {
            return PartyOperationResult.failure(context.errorId, context.party);
        }
        PartyErrorId revisionError = validateRevision(
                context.party, expectedPartyRevision);
        if (revisionError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(revisionError, context.party);
        }
        if (!isLeader(context)) {
            return PartyOperationResult.failure(
                    PartyErrorId.NOT_LEADER, context.party);
        }
        String name = requestedName == null ? "" : requestedName.trim();
        PartyErrorId nameError = checkName(
                name, ChatProfanityCatalog.effective());
        if (nameError != PartyErrorId.NONE) {
            return PartyOperationResult.failure(nameError, context.party);
        }
        PartyMember leader = context.party.getLeader();
        if (!context.party.rename(name)) {
            return PartyOperationResult.success(false, context.party, leader);
        }
        context.partyData.saveParty(context.party);
        return PartyOperationResult.success(true, context.party, leader);
    }

    /**
     * Whether a trimmed name may be given to a party: an empty one takes
     * the name away; any other is at most {@link Party#MAX_NAME_LENGTH}
     * characters, has no formatting codes or control characters, and holds
     * no word of the profanity list.
     */
    static PartyErrorId checkName(String name, ChatProfanityWords words) {
        if (name == null) {
            return PartyErrorId.NAME_NOT_ALLOWED;
        }
        if (name.length() == 0) {
            return PartyErrorId.NONE;
        }
        if (name.codePointCount(0, name.length()) > Party.MAX_NAME_LENGTH) {
            return PartyErrorId.NAME_TOO_LONG;
        }
        if (!Party.isWellFormedName(name)
                || ChatProfanityFilter.hasListedWord(name, words)) {
            return PartyErrorId.NAME_NOT_ALLOWED;
        }
        return PartyErrorId.NONE;
    }

    /** The server's setting for how many members make a party full. */
    public static int memberLimit() {
        return Party.clampMemberLimit(LostTalesConfig.partyMaxMembers);
    }

    public synchronized PartyOperationResult setGoHereMarker(
            EntityPlayerMP player,
            boolean hasMarkerPosition, int markerDimensionId,
            double markerX, double markerZ) {
        PersonalMarkerContext owner = resolvePersonalMarkerOwner(player);
        if (!owner.isValid()) {
            return PartyOperationResult.failure(owner.errorId, null);
        }
        PartyWorldData partyData = getPartyData(player.worldObj);
        Party party = partyData == null
                ? null : partyData.getPartyForIdentity(owner.ownerId);
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

        UUID identityId = owner.ownerId;
        double x = quantizeTrackingCoordinate(markerX);
        double y = quantizeTrackingCoordinate(player.posY);
        double z = quantizeTrackingCoordinate(markerZ);
        PartyGoHereMarker previous = markerData.getMarker(identityId);
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
                    party == null ? null : party.getMember(identityId));
        }
        PartyGoHereMarker marker = new PartyGoHereMarker(
                party == null ? null : party.getPartyId(),
                identityId,
                markerDimensionId,
                x, y, z,
                System.currentTimeMillis());
        markerData.saveMarker(marker);
        return PartyOperationResult.success(
                true, party,
                party == null ? null : party.getMember(identityId));
    }

    public synchronized PartyOperationResult removeGoHereMarker(
            EntityPlayerMP player) {
        PersonalMarkerContext owner = resolvePersonalMarkerOwner(player);
        if (!owner.isValid()) {
            return PartyOperationResult.failure(owner.errorId, null);
        }
        PartyWorldData partyData = getPartyData(player.worldObj);
        Party party = partyData == null
                ? null : partyData.getPartyForIdentity(owner.ownerId);
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(player.worldObj);
        if (markerData == null) {
            return PartyOperationResult.failure(
                    PartyErrorId.MARKER_STORAGE_READ_ONLY, party);
        }
        UUID identityId = owner.ownerId;
        PartyGoHereMarker existing = markerData.getMarker(identityId);
        if (existing == null) {
            return PartyOperationResult.success(
                    false, party,
                    party == null ? null : party.getMember(identityId));
        }
        markerData.removeMarker(identityId);
        return PartyOperationResult.success(
                true, party,
                party == null ? null : party.getMember(identityId));
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
                context.party, expectedPartyRevision);
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
        ActiveIdentityContext active = resolveActiveIdentity(player);
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
        ActiveIdentityContext active = resolveActiveIdentity(player);
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
                context.party, expectedPartyRevision);
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
        ActiveIdentityContext active = resolveActiveIdentity(player);
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

    public synchronized Party getPartyForActiveIdentity(EntityPlayerMP player) {
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
                invitationData.removeInvitationsInvolvingIdentity(characterId);
        PartyGoHereMarker removedMarker = markerData.removeMarker(characterId);
        Party party = partyData.getPartyForIdentity(characterId);
        if (party == null) {
            return PartyOperationResult.success(
                    removedInvitations > 0 || removedMarker != null,
                    null, null);
        }
        PartyMember removed = party.getMember(characterId);
        boolean removingLeader = characterId.equals(
                party.getLeaderIdentityId());
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

    /**
     * Counts what {@link #repairIntegrity} would remove, changing nothing.
     * Null while a store cannot be read or is read-only.
     */
    public synchronized PartyIntegrityReport inspectIntegrity(World world) {
        PartyWorldData partyData = getPartyData(world);
        CharacterWorldData characterData = getCharacterData(world);
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(world);
        if (partyData == null || characterData == null
                || invitationData == null || markerData == null
                || partyData.isReadOnlyForNewerVersion()
                || characterData.isReadOnlyForNewerVersion()) {
            return null;
        }
        CharacterIndex index = characterData.characterIndex();
        int members = 0;
        for (Party party : partyData.getParties()) {
            for (PartyMember member : party.getMembers()) {
                if (memberRemovalReason(member, index) != null) {
                    members++;
                }
            }
        }
        int invitations = this.invitationCoordinator.countInvalidInvitations(
                partyData, invitationData, characterData,
                System.currentTimeMillis());
        int markers = 0;
        for (PartyGoHereMarker marker : markerData.getMarkers()) {
            if (markerRemovalReason(marker, index) != null) {
                markers++;
            }
        }
        return new PartyIntegrityReport(members, invitations, markers);
    }

    /**
     * Removes what no longer stands from the party stores: members, stale
     * invitations and go-here markers, the members checked again even when
     * they were checked since the world loaded. Returns what it removed;
     * null, having changed nothing, while a store cannot be written.
     */
    public synchronized PartyIntegrityReport repairIntegrity(World world) {
        PartyWorldData partyData = getPartyData(world);
        CharacterWorldData characterData = getCharacterData(world);
        PartyInvitationWorldData invitationData =
                this.invitationCoordinator.getWritableData(world);
        PartyGoHereMarkerWorldData markerData =
                getWritableGoHereMarkerData(world);
        if (partyData == null || characterData == null
                || invitationData == null || markerData == null
                || partyData.isReadOnlyForNewerVersion()
                || characterData.isReadOnlyForNewerVersion()) {
            return null;
        }
        int members = repairMemberReferences(partyData, characterData);
        partyData.markCharacterReferencesValidated();
        int invitations = this.invitationCoordinator.pruneInvalidInvitations(
                partyData, invitationData, characterData,
                System.currentTimeMillis());
        int markers = pruneInvalidGoHereMarkers(characterData, markerData);
        return new PartyIntegrityReport(members, invitations, markers);
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
        repairMemberReferences(partyData, characterData);
        partyData.markCharacterReferencesValidated();
        return true;
    }

    /**
     * Quarantines every member that no longer stands, brings the names of
     * the others up to date, and mends a party's leader or ends an empty
     * party. Returns how many members it removed.
     */
    private int repairMemberReferences(PartyWorldData partyData,
                                       CharacterWorldData characterData) {
        int removed = 0;
        CharacterIndex index = characterData.characterIndex();
        List<Party> parties = new ArrayList<Party>(partyData.getParties());
        for (Party party : parties) {
            boolean changed = false;
            List<PartyMember> members =
                    new ArrayList<PartyMember>(party.getMembers());
            for (PartyMember member : members) {
                UUID identityId = member.getIdentityId();
                String removalReason = memberRemovalReason(member, index);
                if (removalReason != null) {
                    party.removeMember(identityId);
                    partyData.quarantine(
                            removalReason,
                            party.getPartyId(),
                            identityId);
                    changed = true;
                    removed++;
                    continue;
                }
                RoleplayCharacter character = index.find(identityId);
                String name = character != null ? character.getName()
                        : RoleplayCharacterIdentityHook.resolveGameplayName(identityId);
                if (name != null && name.length() > 0
                        && party.refreshMemberIdentity(
                                identityId, member.getOwnerId(), name)) {
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
        return removed;
    }

    /**
     * Why a member no longer stands: its id is held by two rosters, names
     * nobody, or names a character of another account. Null while it
     * stands.
     */
    private static String memberRemovalReason(PartyMember member,
                                              CharacterIndex index) {
        UUID identityId = member.getIdentityId();
        if (index.isAmbiguous(identityId)) {
            return "ambiguous_character_uuid";
        }
        RoleplayCharacter character = index.find(identityId);
        // A member whose id is its own owner's is that account playing as
        // itself; it stands as long as the account has a roster, exactly as
        // a character stands while it exists.
        boolean accountMember = character == null
                && identityId.equals(member.getOwnerId())
                && index.isAccountOwner(identityId);
        if (character == null && !accountMember) {
            return "missing_character";
        }
        if (character != null
                && !character.getOwnerId().equals(member.getOwnerId())) {
            return "character_owner_mismatch";
        }
        return null;
    }

    private PartyContext resolvePartyContext(EntityPlayerMP player) {
        ActiveIdentityContext active = resolveActiveIdentity(player);
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
        Party party = partyData.getPartyForIdentity(active.gameplayId());
        if (party == null) {
            return PartyContext.failure(PartyErrorId.NOT_IN_PARTY);
        }
        return PartyContext.success(active, partyData, party);
    }

    /**
     * The identity the player is playing, as the party system needs it:
     * the shared resolver's answer — the account, a character, or a
     * store that cannot say — plus the party's own integrity checks on a
     * character, since a member is filed by its identity id and an id held
     * by two rosters or by another owner would file it under the wrong
     * person.
     */
    ActiveIdentityContext resolveActiveIdentity(
            EntityPlayerMP player) {
        PlayableIdentityResolver.Resolution resolution =
                PlayableIdentityResolver.resolve(player);
        if (!resolution.isAvailable()) {
            return ActiveIdentityContext.failure(
                    partyErrorOf(resolution.getError()));
        }
        CharacterWorldData data = resolution.getData();
        RoleplayCharacter character = resolution.getCharacter();
        if (character == null) {
            return ActiveIdentityContext.success(data,
                    resolution.getIdentity(), player.getCommandSenderName());
        }
        int matches = data.characterIndex().countOf(character.getCharacterId());
        if (matches == 0) {
            return ActiveIdentityContext.failure(
                    PartyErrorId.CHARACTER_NOT_FOUND);
        }
        if (matches > 1) {
            return ActiveIdentityContext.failure(
                    PartyErrorId.CHARACTER_ID_AMBIGUOUS);
        }
        if (!player.getUniqueID().equals(character.getOwnerId())) {
            return ActiveIdentityContext.failure(
                    PartyErrorId.CHARACTER_NOT_FOUND);
        }
        return ActiveIdentityContext.success(data,
                resolution.getIdentity(),
                PlayableIdentityResolver.displayName(resolution, player));
    }

    /** The party's word for the shared resolver's failure. */
    private static PartyErrorId partyErrorOf(CharacterErrorId error) {
        if (error == CharacterErrorId.INVALID_PLAYER) {
            return PartyErrorId.INVALID_PLAYER;
        }
        if (error == CharacterErrorId.CLIENT_SIDE_REQUEST) {
            return PartyErrorId.CLIENT_SIDE_REQUEST;
        }
        if (error == CharacterErrorId.STORAGE_READ_ONLY) {
            return PartyErrorId.CHARACTER_STORAGE_READ_ONLY;
        }
        return PartyErrorId.INTERNAL_ERROR;
    }

    private PartyErrorId validateRevision(Party party,
                                          long expectedRevision) {
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
                context.party.getLeaderIdentityId());
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
        CharacterIndex characters = characterData.characterIndex();
        List<PartyGoHereMarker> markers =
                new ArrayList<PartyGoHereMarker>(markerData.getMarkers());
        for (PartyGoHereMarker marker : markers) {
            String reason = markerRemovalReason(marker, characters);
            if (reason != null) {
                markerData.quarantine(reason, marker);
                markerData.removeMarker(marker.getOwnerIdentityId());
                removed++;
            }
        }
        return removed;
    }

    /**
     * Why a go-here marker no longer stands: its owner's id is held by two
     * rosters or by nobody, or its dimension is gone. A marker filed under
     * an account stands while that account has a roster, as a character's
     * stands while the character exists. Null while it stands.
     */
    private static String markerRemovalReason(PartyGoHereMarker marker,
                                              CharacterIndex characters) {
        if (characters.isAmbiguous(marker.getOwnerIdentityId())) {
            return "ambiguous_owner_character";
        }
        if (!characters.hasOwner(marker.getOwnerIdentityId())) {
            return "missing_owner_character";
        }
        if (!DimensionManager.isDimensionRegistered(marker.getDimensionId())) {
            return "unregistered_dimension";
        }
        return null;
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
        ActiveIdentityContext active = resolveActiveIdentity(player);
        if (!active.isValid()) {
            return PersonalMarkerContext.failure(active.errorId);
        }
        return PersonalMarkerContext.owned(
                PartyPersonalMarkerOwner.resolve(
                        active.identity.getCharacterId(), player.getUniqueID()));
    }

    /** Who a personal marker is filed under, and which character if any. */
    static final class PersonalMarkerContext {
        final UUID ownerId;
        final PartyErrorId errorId;

        private PersonalMarkerContext(UUID ownerId, PartyErrorId errorId) {
            this.ownerId = ownerId;
            this.errorId = errorId;
        }

        private static PersonalMarkerContext owned(UUID ownerId) {
            return new PersonalMarkerContext(ownerId, PartyErrorId.NONE);
        }

        private static PersonalMarkerContext failure(PartyErrorId errorId) {
            return new PersonalMarkerContext(null,
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
    static final class ActiveIdentityContext {
        final CharacterWorldData characterData;
        final PlayableIdentity identity;
        /** The name the identity goes by: the character's, else the account's. */
        final String displayName;
        final PartyErrorId errorId;

        private ActiveIdentityContext(CharacterWorldData characterData,
                                       PlayableIdentity identity,
                                       String displayName,
                                       PartyErrorId errorId) {
            this.characterData = characterData;
            this.identity = identity;
            this.displayName = displayName;
            this.errorId = errorId;
        }

        private static ActiveIdentityContext success(
                CharacterWorldData data, PlayableIdentity identity,
                String displayName) {
            return new ActiveIdentityContext(
                    data, identity, displayName, PartyErrorId.NONE);
        }

        private static ActiveIdentityContext failure(PartyErrorId errorId) {
            return new ActiveIdentityContext(null, null, "", errorId);
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
        private final ActiveIdentityContext active;
        private final PartyWorldData partyData;
        private final Party party;
        private final PartyErrorId errorId;

        private PartyContext(ActiveIdentityContext active,
                             PartyWorldData partyData,
                             Party party,
                             PartyErrorId errorId) {
            this.active = active;
            this.partyData = partyData;
            this.party = party;
            this.errorId = errorId;
        }

        private static PartyContext success(
                ActiveIdentityContext active,
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

}
