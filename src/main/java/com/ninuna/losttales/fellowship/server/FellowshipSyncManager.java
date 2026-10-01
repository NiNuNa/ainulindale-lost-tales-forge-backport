package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.fellowship.FellowshipOperationResultPacket;
import com.ninuna.losttales.network.packet.fellowship.FellowshipStateSyncPacket;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.storage.FellowshipInvitationStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipInvitationWorldData;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.fellowship.sync.FellowshipInviteTargetSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
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
import com.ninuna.losttales.util.LostTalesServerPlayers;

/** Builds and sends private, revisioned fellowship snapshots to authorized clients. */
public final class FellowshipSyncManager {

    public static final int UNSOLICITED_REQUEST_ID = 0;

    private static final ConcurrentMap<UUID, AtomicLong> SEQUENCES =
            new ConcurrentHashMap<UUID, AtomicLong>();
    /**
     * Accounts that came, went or changed character since the last tick:
     * their fellowships' members are sent their state again on the next
     * one, once the player list shows the change.
     */
    private static final Set<UUID> PRESENCE_CHANGED = new LinkedHashSet<UUID>();

    private FellowshipSyncManager() {}

    public static boolean sendState(EntityPlayerMP player, int requestId) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
            return false;
        }
        UUID ownerId = player.getUniqueID();
        long sequence = nextSequence(ownerId);
        FellowshipInvitationState state;
        try {
            state = FellowshipService.getInstance().getInvitationState(player);
        } catch (Throwable throwable) {
            FMLLog.warning("[%s] Unable to build fellowship state for player %s: %s",
                    LostTalesMetaData.MOD_ID,
                    ownerId,
                    throwable.toString());
            state = FellowshipInvitationState.failure(FellowshipErrorId.INTERNAL_ERROR);
        }
        InviteTargetCollection inviteTargets = collectInviteTargets(
                player, state);
        FellowshipStateSnapshot snapshot = FellowshipStateSnapshot.fromState(
                ownerId, sequence, FellowshipService.memberLimit(), state,
                onlineView(player.worldObj), inviteTargets.targets,
                inviteTargets.truncated);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new FellowshipStateSyncPacket(requestId, snapshot), player);
        FellowshipMemberStatusSyncManager.sendNow(player);
        FellowshipTrackingSyncManager.sendNow(player);
        return true;
    }

    public static void sendResultAndState(EntityPlayerMP player,
                                          int requestId,
                                          FellowshipOperationType operationType,
                                          FellowshipOperationResult result) {
        if (!LostTalesServerPlayers.isServerPlayer(player) || result == null || operationType == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new FellowshipOperationResultPacket(
                        requestId, operationType, result, true),
                player);
        sendState(player, requestId);
    }

    public static void sendResultAndState(EntityPlayerMP player,
                                          int requestId,
                                          FellowshipOperationType operationType,
                                          FellowshipInvitationOperationResult result) {
        if (!LostTalesServerPlayers.isServerPlayer(player) || result == null || operationType == null) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new FellowshipOperationResultPacket(
                        requestId, operationType, result, true),
                player);
        sendState(player, requestId);
    }

    public static void sendFailure(EntityPlayerMP player,
                                   int requestId,
                                   FellowshipOperationType operationType,
                                   FellowshipErrorId errorId,
                                   long fellowshipRevision,
                                   boolean stateFollows) {
        if (!LostTalesServerPlayers.isServerPlayer(player)) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new FellowshipOperationResultPacket(
                        requestId,
                        operationType,
                        errorId,
                        fellowshipRevision,
                        stateFollows),
                player);
        if (stateFollows) {
            sendState(player, requestId);
        }
    }

    /**
     * Who a change to one fellowship reaches, taken before the change: its
     * members and everyone its invitations name. Nobody for no fellowship.
     */
    public static AudienceSnapshot captureFellowshipAudience(World world,
                                                             UUID fellowshipId) {
        if (world == null || world.isRemote || fellowshipId == null) {
            return AudienceSnapshot.empty();
        }
        AudienceSnapshot result;
        try {
            result = AudienceSnapshot.fromFellowship(
                    FellowshipStorage.get(world).getFellowship(fellowshipId));
        } catch (Throwable ignored) {
            return AudienceSnapshot.empty();
        }
        try {
            for (FellowshipInvitation invitation : FellowshipInvitationStorage.get(world)
                    .getInvitationsForFellowship(fellowshipId)) {
                result.addInvitation(invitation);
            }
        } catch (Throwable ignored) {
            // The members are still told if the invitations cannot be read.
        }
        return result;
    }

    /** Everyone in a fellowship with a character: whom its switch or deletion concerns. */
    public static AudienceSnapshot captureCharacterFellowshipAudience(
            World world, UUID characterId) {
        if (world == null || world.isRemote || characterId == null) {
            return AudienceSnapshot.empty();
        }
        try {
            AudienceSnapshot result = AudienceSnapshot.empty();
            for (Fellowship fellowship
                    : FellowshipStorage.get(world).getFellowshipsForIdentity(characterId)) {
                result.addFellowship(fellowship);
            }
            return result;
        } catch (Throwable ignored) {
            return AudienceSnapshot.empty();
        }
    }

    public static AudienceSnapshot captureCharacterRelationsAudience(
            World world, UUID characterId) {
        AudienceSnapshot result = captureCharacterFellowshipAudience(
                world, characterId);
        if (world == null || world.isRemote || characterId == null) {
            return result;
        }
        try {
            FellowshipWorldData fellowshipData = FellowshipStorage.get(world);
            FellowshipInvitationWorldData invitationData =
                    FellowshipInvitationStorage.get(world);
            for (Fellowship fellowship
                    : fellowshipData.getFellowshipsForIdentity(characterId)) {
                for (FellowshipInvitation invitation
                        : invitationData.getInvitationsForFellowship(
                        fellowship.getFellowshipId())) {
                    result.addInvitation(invitation);
                }
            }
            for (FellowshipInvitation invitation : invitationData.getInvitations()) {
                if (characterId.equals(invitation.getInvitingIdentityId())
                        || characterId.equals(invitation.getTargetIdentityId())) {
                    result.addInvitation(invitation);
                }
            }
        } catch (Throwable ignored) {
            // Fellowship membership audience is still useful if invitation data is unavailable.
        }
        return result;
    }

    public static AudienceSnapshot captureAllInvitationAudience(World world) {
        AudienceSnapshot result = AudienceSnapshot.empty();
        if (world == null || world.isRemote) {
            return result;
        }
        try {
            FellowshipInvitationWorldData invitationData =
                    FellowshipInvitationStorage.get(world);
            for (FellowshipInvitation invitation : invitationData.getInvitations()) {
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
            FellowshipInvitationWorldData invitationData =
                    FellowshipInvitationStorage.get(world);
            FellowshipInvitation invitation =
                    invitationData.getInvitation(invitationId);
            if (invitation == null) {
                return result;
            }
            result.addInvitation(invitation);
            Fellowship fellowship = FellowshipStorage.get(world)
                    .getFellowship(invitation.getFellowshipId());
            result.addFellowship(fellowship);
            for (FellowshipInvitation related
                    : invitationData.getInvitationsForFellowship(
                    invitation.getFellowshipId())) {
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
            Fellowship fellowship,
            FellowshipMember affectedMember,
            FellowshipInvitation invitation) {
        AudienceSnapshot result = new AudienceSnapshot();
        result.addAll(before);
        result.addFellowship(fellowship);
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
            EntityPlayerMP player = LostTalesServerPlayers.findOnline(ownerId);
            if (player != null) {
                sendState(player, UNSOLICITED_REQUEST_ID);
            }
        }
    }

    /**
     * Notes that an account came, went or changed character, so its
     * fellowships' members see it on the next tick.
     */
    public static synchronized void presenceChanged(UUID ownerId) {
        if (ownerId != null) {
            PRESENCE_CHANGED.add(ownerId);
        }
    }

    /** Sends the members of every fellowship of the accounts noted since the last tick their state. */
    public static void sendPresenceChanges(World world) {
        List<UUID> changed;
        synchronized (FellowshipSyncManager.class) {
            if (PRESENCE_CHANGED.isEmpty()) {
                return;
            }
            changed = new ArrayList<UUID>(PRESENCE_CHANGED);
            PRESENCE_CHANGED.clear();
        }
        AudienceSnapshot audience = AudienceSnapshot.empty();
        for (UUID ownerId : changed) {
            audience.addAll(captureAccountAudience(world, ownerId));
        }
        sendStateToAudience(audience, null);
        FellowshipMirrors.reconcileAccounts(world, changed);
    }

    /** Everyone in a fellowship with one of the account's identities. */
    private static AudienceSnapshot captureAccountAudience(World world, UUID ownerId) {
        AudienceSnapshot result = AudienceSnapshot.empty();
        if (world == null || world.isRemote || ownerId == null) {
            return result;
        }
        try {
            for (Fellowship fellowship : FellowshipStorage.get(world).getFellowships()) {
                if (fellowship.hasMemberOwnedBy(ownerId)) {
                    result.addFellowship(fellowship);
                }
            }
        } catch (Throwable ignored) {
            // Nobody is told while the store cannot be read.
        }
        return result;
    }

    /** Who is online and as whom; nobody while the character store cannot be read. */
    private static FellowshipOnlineView onlineView(World world) {
        try {
            return FellowshipOnlineView.collect(CharacterStorage.get(world));
        } catch (Throwable ignored) {
            return FellowshipOnlineView.none();
        }
    }

    /** Sends every online player their fellowship state, after a repair changed what they may see. */
    public static void sendStateToEveryone() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        for (Object value : new ArrayList<Object>(
                server.getConfigurationManager().playerEntityList)) {
            if (value instanceof EntityPlayerMP) {
                sendState((EntityPlayerMP) value, UNSOLICITED_REQUEST_ID);
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
        synchronized (FellowshipSyncManager.class) {
            PRESENCE_CHANGED.clear();
        }
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

    /**
     * The online players, as the characters they play, for one who leads or
     * guides a fellowship with room: whom the invite field offers. Each
     * fellowship leaves out its own members and those it invited already,
     * on the page; the server refuses those either way.
     */
    private static InviteTargetCollection collectInviteTargets(
            EntityPlayerMP receiver, FellowshipInvitationState state) {
        if (!LostTalesServerPlayers.isServerPlayer(receiver) || state == null
                || !state.isSuccessful() || state.getActiveIdentityId() == null
                || !managesOneWithRoom(state)) {
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

        ArrayList<FellowshipInviteTargetSnapshot> candidates =
                new ArrayList<FellowshipInviteTargetSnapshot>();
        for (Object value : onlinePlayers) {
            if (!(value instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP targetPlayer = (EntityPlayerMP) value;
            UUID targetOwnerId = targetPlayer.getUniqueID();
            if (!LostTalesServerPlayers.isServerPlayer(targetPlayer)
                    || receiver.getUniqueID().equals(targetOwnerId)) {
                continue;
            }
            FellowshipService.ActiveIdentityContext target =
                    FellowshipService.getInstance().resolveActiveIdentity(targetPlayer);
            if (!target.isValid()) {
                continue;
            }
            if (state.getActiveIdentityId().equals(target.gameplayId())) {
                continue;
            }
            candidates.add(new FellowshipInviteTargetSnapshot(
                    targetOwnerId,
                    target.gameplayId(),
                    targetPlayer.getCommandSenderName(),
                    target.displayName));
        }

        Collections.sort(candidates, new Comparator<FellowshipInviteTargetSnapshot>() {
            @Override
            public int compare(FellowshipInviteTargetSnapshot left,
                               FellowshipInviteTargetSnapshot right) {
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
                > FellowshipStateSnapshot.MAX_INVITE_TARGETS;
        if (truncated) {
            candidates = new ArrayList<FellowshipInviteTargetSnapshot>(
                    candidates.subList(0, FellowshipStateSnapshot.MAX_INVITE_TARGETS));
        }
        return new InviteTargetCollection(candidates, truncated);
    }

    /** Whether the identity leads or guides a fellowship that still has room. */
    private static boolean managesOneWithRoom(FellowshipInvitationState state) {
        for (Fellowship fellowship : state.getFellowships()) {
            if (fellowship.canManage(state.getActiveIdentityId())
                    && !fellowship.isFull(FellowshipService.memberLimit())) {
                return true;
            }
        }
        return false;
    }

    private static final class InviteTargetCollection {
        private final List<FellowshipInviteTargetSnapshot> targets;
        private final boolean truncated;

        private InviteTargetCollection(
                List<FellowshipInviteTargetSnapshot> targets, boolean truncated) {
            this.targets = targets == null
                    ? Collections.<FellowshipInviteTargetSnapshot>emptyList()
                    : Collections.unmodifiableList(
                    new ArrayList<FellowshipInviteTargetSnapshot>(targets));
            this.truncated = truncated;
        }

        private static InviteTargetCollection empty() {
            return new InviteTargetCollection(
                    Collections.<FellowshipInviteTargetSnapshot>emptyList(), false);
        }
    }

    /**
     * The accounts a fellowship change must reach, taken before the change is
     * made, so members who leave with it are told too. It hands out
     * copies only, so nothing outside changes it.
     */
    public static final class AudienceSnapshot {
        private final LinkedHashSet<UUID> ownerIds =
                new LinkedHashSet<UUID>();

        private AudienceSnapshot() {}

        public static AudienceSnapshot empty() {
            return new AudienceSnapshot();
        }

        public static AudienceSnapshot fromFellowship(Fellowship fellowship) {
            AudienceSnapshot result = new AudienceSnapshot();
            result.addFellowship(fellowship);
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

        private void addFellowship(Fellowship fellowship) {
            if (fellowship == null) {
                return;
            }
            for (FellowshipMember member : fellowship.getMembers()) {
                addMember(member);
            }
        }

        private void addMember(FellowshipMember member) {
            if (member != null && member.getOwnerId() != null) {
                this.ownerIds.add(member.getOwnerId());
            }
        }

        private void addInvitation(FellowshipInvitation invitation) {
            if (invitation == null) {
                return;
            }
            this.ownerIds.add(invitation.getInvitingOwnerId());
            this.ownerIds.add(invitation.getTargetOwnerId());
        }
    }
}
