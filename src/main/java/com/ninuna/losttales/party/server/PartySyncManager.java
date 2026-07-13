package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.party.PartyOperationResultPacket;
import com.ninuna.losttales.network.packet.party.PartyStateSyncPacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyInvitation;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.storage.PartyInvitationStorage;
import com.ninuna.losttales.party.storage.PartyInvitationWorldData;
import com.ninuna.losttales.party.storage.PartyStorage;
import com.ninuna.losttales.party.storage.PartyWorldData;
import com.ninuna.losttales.party.sync.PartyInviteTargetSnapshot;
import com.ninuna.losttales.party.sync.PartyOperationType;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Builds and sends private, revisioned party snapshots to authorized clients. */
public final class PartySyncManager {

    public static final int UNSOLICITED_REQUEST_ID = 0;

    private static final ConcurrentMap<UUID, AtomicLong> SEQUENCES =
            new ConcurrentHashMap<UUID, AtomicLong>();

    private PartySyncManager() {}

    public static boolean sendState(EntityPlayerMP player, int requestId) {
        if (!isServerPlayer(player)) {
            return false;
        }
        UUID ownerId = player.getUniqueID();
        long sequence = nextSequence(ownerId);
        PartyInvitationState state;
        try {
            state = PartyService.getInstance().getInvitationState(player);
        } catch (Throwable throwable) {
            FMLLog.warning("[%s] Unable to build party state for player %s: %s",
                    LostTalesMetaData.MOD_ID,
                    ownerId,
                    throwable.toString());
            state = PartyInvitationState.failure(PartyErrorId.INTERNAL_ERROR);
        }
        InviteTargetCollection inviteTargets = collectInviteTargets(
                player, state);
        PartyStateSnapshot snapshot = PartyStateSnapshot.fromState(
                ownerId, sequence, state,
                inviteTargets.targets, inviteTargets.truncated);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new PartyStateSyncPacket(requestId, snapshot), player);
        PartyMemberStatusSyncManager.sendNow(player);
        PartyTrackingSyncManager.sendNow(player);
        return true;
    }

    public static void sendResultAndState(EntityPlayerMP player,
                                          int requestId,
                                          PartyOperationType operationType,
                                          PartyOperationResult result) {
        if (!isServerPlayer(player) || result == null || operationType == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new PartyOperationResultPacket(
                        requestId, operationType, result, true),
                player);
        sendState(player, requestId);
    }

    public static void sendResultAndState(EntityPlayerMP player,
                                          int requestId,
                                          PartyOperationType operationType,
                                          PartyInvitationOperationResult result) {
        if (!isServerPlayer(player) || result == null || operationType == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new PartyOperationResultPacket(
                        requestId, operationType, result, true),
                player);
        sendState(player, requestId);
    }

    public static void sendFailure(EntityPlayerMP player,
                                   int requestId,
                                   PartyOperationType operationType,
                                   PartyErrorId errorId,
                                   long partyRevision,
                                   boolean stateFollows) {
        if (!isServerPlayer(player)) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new PartyOperationResultPacket(
                        requestId,
                        operationType,
                        errorId,
                        partyRevision,
                        stateFollows),
                player);
        if (stateFollows) {
            sendState(player, requestId);
        }
    }

    public static AudienceSnapshot captureActivePartyAudience(
            EntityPlayerMP player) {
        if (!isServerPlayer(player)) {
            return AudienceSnapshot.empty();
        }
        Party party;
        try {
            party = PartyService.getInstance()
                    .getPartyForActiveCharacter(player);
        } catch (Throwable ignored) {
            return AudienceSnapshot.empty();
        }
        AudienceSnapshot result = AudienceSnapshot.fromParty(party);
        if (party == null) {
            return result;
        }
        try {
            PartyInvitationWorldData invitationData =
                    PartyInvitationStorage.get(player.worldObj);
            for (PartyInvitation invitation
                    : invitationData.getInvitationsForParty(
                    party.getPartyId())) {
                result.addInvitation(invitation);
            }
        } catch (Throwable ignored) {
            // Party member recipients remain available even if invite data is unavailable.
        }
        return result;
    }

    public static AudienceSnapshot captureCharacterPartyAudience(
            World world, UUID characterId) {
        if (world == null || world.isRemote || characterId == null) {
            return AudienceSnapshot.empty();
        }
        try {
            PartyWorldData data = PartyStorage.get(world);
            return AudienceSnapshot.fromParty(
                    data.getPartyForCharacter(characterId));
        } catch (Throwable ignored) {
            return AudienceSnapshot.empty();
        }
    }

    public static AudienceSnapshot captureCharacterRelationsAudience(
            World world, UUID characterId) {
        AudienceSnapshot result = captureCharacterPartyAudience(
                world, characterId);
        if (world == null || world.isRemote || characterId == null) {
            return result;
        }
        try {
            PartyWorldData partyData = PartyStorage.get(world);
            Party party = partyData.getPartyForCharacter(characterId);
            PartyInvitationWorldData invitationData =
                    PartyInvitationStorage.get(world);
            if (party != null) {
                for (PartyInvitation invitation
                        : invitationData.getInvitationsForParty(
                        party.getPartyId())) {
                    result.addInvitation(invitation);
                }
            }
            for (PartyInvitation invitation : invitationData.getInvitations()) {
                if (characterId.equals(invitation.getInvitingCharacterId())
                        || characterId.equals(invitation.getTargetCharacterId())) {
                    result.addInvitation(invitation);
                }
            }
        } catch (Throwable ignored) {
            // Party membership audience is still useful if invitation data is unavailable.
        }
        return result;
    }

    public static AudienceSnapshot captureAllInvitationAudience(World world) {
        AudienceSnapshot result = AudienceSnapshot.empty();
        if (world == null || world.isRemote) {
            return result;
        }
        try {
            PartyInvitationWorldData invitationData =
                    PartyInvitationStorage.get(world);
            for (PartyInvitation invitation : invitationData.getInvitations()) {
                result.addInvitation(invitation);
            }
        } catch (Throwable ignored) {
            // Return the safely collected subset.
        }
        return result;
    }

    public static AudienceSnapshot captureInvitationAudience(
            World world, UUID invitationId) {
        AudienceSnapshot result = AudienceSnapshot.empty();
        if (world == null || world.isRemote || invitationId == null) {
            return result;
        }
        try {
            PartyInvitationWorldData invitationData =
                    PartyInvitationStorage.get(world);
            PartyInvitation invitation =
                    invitationData.getInvitation(invitationId);
            if (invitation == null) {
                return result;
            }
            result.addInvitation(invitation);
            Party party = PartyStorage.get(world)
                    .getParty(invitation.getPartyId());
            result.addParty(party);
            for (PartyInvitation related
                    : invitationData.getInvitationsForParty(
                    invitation.getPartyId())) {
                result.addInvitation(related);
            }
        } catch (Throwable ignored) {
            // Return the safely collected subset.
        }
        return result;
    }

    public static AudienceSnapshot combineAudiences(
            AudienceSnapshot first, AudienceSnapshot second) {
        AudienceSnapshot result = new AudienceSnapshot();
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    public static AudienceSnapshot collectAudience(
            AudienceSnapshot before,
            Party party,
            PartyMember affectedMember,
            PartyInvitation invitation) {
        AudienceSnapshot result = new AudienceSnapshot();
        result.addAll(before);
        result.addParty(party);
        result.addMember(affectedMember);
        result.addInvitation(invitation);
        return result;
    }

    public static void sendStateToAudience(AudienceSnapshot audience,
                                           UUID excludedOwnerId) {
        if (audience == null || audience.isEmpty()) {
            return;
        }
        for (UUID ownerId : audience.getOwnerIds()) {
            if (ownerId == null || ownerId.equals(excludedOwnerId)) {
                continue;
            }
            EntityPlayerMP player = findOnlinePlayer(ownerId);
            if (player != null) {
                sendState(player, UNSOLICITED_REQUEST_ID);
            }
        }
    }

    public static void clearPlayer(UUID ownerId) {
        if (ownerId != null) {
            SEQUENCES.remove(ownerId);
        }
    }

    public static void clear() {
        SEQUENCES.clear();
    }

    private static long nextSequence(UUID ownerId) {
        AtomicLong counter = SEQUENCES.get(ownerId);
        if (counter == null) {
            AtomicLong created = new AtomicLong();
            AtomicLong previous = SEQUENCES.putIfAbsent(ownerId, created);
            counter = previous == null ? created : previous;
        }
        long next = counter.incrementAndGet();
        if (next <= 0L) {
            synchronized (counter) {
                if (counter.get() <= 0L) {
                    counter.set(1L);
                }
                next = counter.get();
            }
        }
        return next;
    }

    private static InviteTargetCollection collectInviteTargets(
            EntityPlayerMP receiver, PartyInvitationState state) {
        if (!isServerPlayer(receiver) || state == null
                || !state.isSuccessful() || state.getParty() == null
                || state.getActiveCharacterId() == null
                || !state.getActiveCharacterId().equals(
                state.getParty().getLeaderCharacterId())
                || state.getParty().isFull()) {
            return InviteTargetCollection.empty();
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return InviteTargetCollection.empty();
        }
        List<?> onlinePlayers = server.getConfigurationManager().playerEntityList;
        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            return InviteTargetCollection.empty();
        }

        PartyWorldData partyData;
        try {
            partyData = PartyStorage.get(receiver.worldObj);
        } catch (RuntimeException exception) {
            return InviteTargetCollection.empty();
        }

        Set<UUID> alreadyInvitedCharacters = new HashSet<UUID>();
        for (PartyInvitation invitation : state.getOutgoingInvitations()) {
            if (invitation != null) {
                alreadyInvitedCharacters.add(invitation.getTargetCharacterId());
            }
        }

        ArrayList<PartyInviteTargetSnapshot> candidates =
                new ArrayList<PartyInviteTargetSnapshot>();
        for (Object value : onlinePlayers) {
            if (!(value instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP targetPlayer = (EntityPlayerMP) value;
            UUID targetOwnerId = targetPlayer.getUniqueID();
            if (!isServerPlayer(targetPlayer)
                    || receiver.getUniqueID().equals(targetOwnerId)) {
                continue;
            }
            PartyService.ActiveCharacterContext target =
                    PartyService.getInstance().resolveActiveCharacter(targetPlayer);
            if (!target.isValid()) {
                continue;
            }
            RoleplayCharacter character = target.character;
            if (character == null
                    || state.getActiveCharacterId().equals(character.getCharacterId())
                    || partyData.getPartyForCharacter(character.getCharacterId()) != null
                    || alreadyInvitedCharacters.contains(character.getCharacterId())) {
                continue;
            }
            candidates.add(new PartyInviteTargetSnapshot(
                    targetOwnerId,
                    character.getCharacterId(),
                    targetPlayer.getCommandSenderName(),
                    character.getName()));
        }

        Collections.sort(candidates, new Comparator<PartyInviteTargetSnapshot>() {
            @Override
            public int compare(PartyInviteTargetSnapshot left,
                               PartyInviteTargetSnapshot right) {
                int playerComparison = left.getPlayerName().compareToIgnoreCase(
                        right.getPlayerName());
                if (playerComparison != 0) {
                    return playerComparison;
                }
                int characterComparison = left.getCharacterName().compareToIgnoreCase(
                        right.getCharacterName());
                if (characterComparison != 0) {
                    return characterComparison;
                }
                return left.getOwnerId().toString().compareTo(
                        right.getOwnerId().toString());
            }
        });

        boolean truncated = candidates.size()
                > PartyStateSnapshot.MAX_INVITE_TARGETS;
        if (truncated) {
            candidates = new ArrayList<PartyInviteTargetSnapshot>(
                    candidates.subList(0, PartyStateSnapshot.MAX_INVITE_TARGETS));
        }
        return new InviteTargetCollection(candidates, truncated);
    }

    private static EntityPlayerMP findOnlinePlayer(UUID ownerId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || ownerId == null) {
            return null;
        }
        List<?> players = server.getConfigurationManager().playerEntityList;
        if (players == null) {
            return null;
        }
        for (Object value : players) {
            if (value instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) value;
                if (ownerId.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }

    private static boolean isServerPlayer(EntityPlayerMP player) {
        return player != null && player.getUniqueID() != null
                && player.worldObj != null && !player.worldObj.isRemote;
    }

    private static final class InviteTargetCollection {
        private final List<PartyInviteTargetSnapshot> targets;
        private final boolean truncated;

        private InviteTargetCollection(
                List<PartyInviteTargetSnapshot> targets, boolean truncated) {
            this.targets = targets == null
                    ? Collections.<PartyInviteTargetSnapshot>emptyList()
                    : Collections.unmodifiableList(
                    new ArrayList<PartyInviteTargetSnapshot>(targets));
            this.truncated = truncated;
        }

        private static InviteTargetCollection empty() {
            return new InviteTargetCollection(
                    Collections.<PartyInviteTargetSnapshot>emptyList(), false);
        }
    }

    /** Immutable-by-exposure owner UUID set captured before a mutation. */
    public static final class AudienceSnapshot {
        private final LinkedHashSet<UUID> ownerIds =
                new LinkedHashSet<UUID>();

        private AudienceSnapshot() {}

        public static AudienceSnapshot empty() {
            return new AudienceSnapshot();
        }

        public static AudienceSnapshot fromParty(Party party) {
            AudienceSnapshot result = new AudienceSnapshot();
            result.addParty(party);
            return result;
        }

        public boolean isEmpty() {
            return this.ownerIds.isEmpty();
        }

        public Set<UUID> getOwnerIds() {
            return Collections.unmodifiableSet(
                    new LinkedHashSet<UUID>(this.ownerIds));
        }

        private void addAll(AudienceSnapshot other) {
            if (other != null) {
                this.ownerIds.addAll(other.ownerIds);
            }
        }

        private void addParty(Party party) {
            if (party == null) {
                return;
            }
            for (PartyMember member : party.getMembers()) {
                addMember(member);
            }
        }

        private void addMember(PartyMember member) {
            if (member != null && member.getOwnerId() != null) {
                this.ownerIds.add(member.getOwnerId());
            }
        }

        private void addInvitation(PartyInvitation invitation) {
            if (invitation == null) {
                return;
            }
            this.ownerIds.add(invitation.getInvitingOwnerId());
            this.ownerIds.add(invitation.getTargetOwnerId());
        }
    }
}
