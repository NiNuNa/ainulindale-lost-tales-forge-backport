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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import com.ninuna.losttales.util.LostTalesServerPlayers;

/**
 * Sends each player the state of the fellowships of the character they
 * play, and the answers to their requests; each change reaches whoever it
 * concerns, taken before the change.
 */
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
        sendStatePacket(player, requestId, new SyncView(onlineView(player.worldObj)), true);
        List<EntityPlayerMP> recipient = Collections.singletonList(player);
        FellowshipMemberStatusSyncManager.sendNow(recipient);
        FellowshipTrackingSyncManager.sendNow(recipient);
        return true;
    }

    /**
     * Sends each player their state, unasked. Who is online and whom the
     * invite field offers, the invitations pruned, and the members' health
     * and places are read once for all of them.
     */
    private static void sendStateTo(List<EntityPlayerMP> players) {
        if (players.isEmpty()) {
            return;
        }
        World world = players.get(0).worldObj;
        SyncView view = new SyncView(onlineView(world));
        try {
            FellowshipService.getInstance().pruneInvitations(world);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Unable to prune fellowship invitations: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
        }
        for (EntityPlayerMP player : players) {
            sendStatePacket(player, UNSOLICITED_REQUEST_ID, view, false);
        }
        FellowshipMemberStatusSyncManager.sendNow(players);
        FellowshipTrackingSyncManager.sendNow(players);
    }

    /**
     * The fellowship state alone, built for the player from {@code view};
     * the invitations pruned first unless the sync pruned them already.
     */
    private static void sendStatePacket(EntityPlayerMP player, int requestId,
                                        SyncView view, boolean prune) {
        UUID ownerId = player.getUniqueID();
        long sequence = nextSequence(ownerId);
        FellowshipInvitationState state;
        try {
            state = FellowshipService.getInstance().getInvitationState(player, prune);
        } catch (Throwable throwable) {
            FMLLog.warning("[%s] Unable to build fellowship state for player %s: %s",
                    LostTalesMetaData.MOD_ID,
                    ownerId,
                    throwable.toString());
            state = FellowshipInvitationState.failure(FellowshipErrorId.INTERNAL_ERROR);
        }
        InviteTargetCollection inviteTargets = collectInviteTargets(
                player, state, view);
        FellowshipStateSnapshot snapshot = FellowshipStateSnapshot.fromState(
                ownerId, sequence, FellowshipService.memberLimit(), state,
                view.online, lotrFellowshipIds(player.worldObj, state),
                FellowshipService.getInstance().marksSeenBy(player.worldObj, state),
                inviteTargets.targets, inviteTargets.truncated);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new FellowshipStateSyncPacket(requestId, snapshot), player);
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
            // The members are still told if the invitations cannot be read.
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
            // Whoever was found before it failed is told.
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
            // Whoever was found before it failed is told.
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
        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        for (UUID ownerId : audience.getOwnerIds()) {
            if (ownerId == null || ownerId.equals(excludedOwnerId)) {
                continue;
            }
            EntityPlayerMP player = LostTalesServerPlayers.findOnline(ownerId);
            if (LostTalesServerPlayers.isServerPlayer(player)) {
                players.add(player);
            }
        }
        sendStateTo(players);
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

    /**
     * LOTR's fellowship behind each fellowship of the state, by ours: the
     * ones its player may share waypoints into. None while the store
     * cannot be read.
     */
    private static Map<UUID, UUID> lotrFellowshipIds(World world,
                                                     FellowshipInvitationState state) {
        Map<UUID, UUID> ids = new HashMap<UUID, UUID>();
        if (state == null || !state.isSuccessful()) {
            return ids;
        }
        try {
            FellowshipWorldData data = FellowshipStorage.get(world);
            for (Fellowship fellowship : state.getFellowships()) {
                UUID mirrorId = data.getMirrorId(fellowship.getFellowshipId());
                if (mirrorId != null) {
                    ids.put(fellowship.getFellowshipId(), mirrorId);
                }
            }
        } catch (RuntimeException unreadable) {
            ids.clear();
        }
        return ids;
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
        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        for (Object value : new ArrayList<Object>(
                server.getConfigurationManager().playerEntityList)) {
            if (value instanceof EntityPlayerMP
                    && LostTalesServerPlayers.isServerPlayer((EntityPlayerMP) value)) {
                players.add((EntityPlayerMP) value);
            }
        }
        sendStateTo(players);
    }

    public static void clearPlayer(UUID ownerId) {
        if (ownerId != null) {
            SEQUENCES.remove(ownerId);
        }
    }

    /** Forgets the sequences and presence changes, and which unreadable lead limits were logged. */
    public static void clear() {
        SEQUENCES.clear();
        synchronized (FellowshipSyncManager.class) {
            PRESENCE_CHANGED.clear();
        }
        FellowshipLeadLimits.clear();
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
     * guides a fellowship with room: whom the invite field offers, from the
     * list {@code view} reads once for the whole sync. Each fellowship
     * leaves out its own members and those it invited already, on the
     * page; the server refuses those either way.
     */
    private static InviteTargetCollection collectInviteTargets(
            EntityPlayerMP receiver, FellowshipInvitationState state, SyncView view) {
        if (!LostTalesServerPlayers.isServerPlayer(receiver) || state == null
                || !state.isSuccessful() || state.getActiveIdentityId() == null
                || !managesOneWithRoom(state)) {
            return InviteTargetCollection.empty();
        }
        return InviteTargetCollection.offeredTo(view.inviteTargets(),
                receiver.getUniqueID(), state.getActiveIdentityId());
    }

    /**
     * Everyone online who plays an identity, as the invite field lists
     * them: by character name, then account name, then account.
     */
    private static List<FellowshipInviteTargetSnapshot> everyoneInvitable() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return Collections.emptyList();
        }
        List<?> onlinePlayers = server.getConfigurationManager().playerEntityList;
        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<FellowshipInviteTargetSnapshot> candidates =
                new ArrayList<FellowshipInviteTargetSnapshot>();
        for (Object value : new ArrayList<Object>(onlinePlayers)) {
            if (!(value instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP targetPlayer = (EntityPlayerMP) value;
            if (!LostTalesServerPlayers.isServerPlayer(targetPlayer)) {
                continue;
            }
            FellowshipService.ActiveIdentityContext target =
                    FellowshipService.getInstance().resolveActiveIdentity(targetPlayer);
            if (!target.isValid()) {
                continue;
            }
            candidates.add(new FellowshipInviteTargetSnapshot(
                    targetPlayer.getUniqueID(),
                    target.gameplayId(),
                    targetPlayer.getCommandSenderName(),
                    target.displayName));
        }

        Collections.sort(candidates, new Comparator<FellowshipInviteTargetSnapshot>() {
            @Override
            public int compare(FellowshipInviteTargetSnapshot left,
                               FellowshipInviteTargetSnapshot right) {
                int characterComparison = left.getCharacterName().compareToIgnoreCase(
                        right.getCharacterName());
                if (characterComparison != 0) {
                    return characterComparison;
                }
                int playerComparison = left.getPlayerName().compareToIgnoreCase(
                        right.getPlayerName());
                if (playerComparison != 0) {
                    return playerComparison;
                }
                return left.getOwnerId().toString().compareTo(
                        right.getOwnerId().toString());
            }
        });
        return candidates;
    }

    /** What one sync reads once for every player it reaches. */
    private static final class SyncView {
        private final FellowshipOnlineView online;
        private List<FellowshipInviteTargetSnapshot> inviteTargets;

        private SyncView(FellowshipOnlineView online) {
            this.online = online;
        }

        /** Everyone the invite field may offer, read the first time a receiver needs them. */
        private List<FellowshipInviteTargetSnapshot> inviteTargets() {
            if (this.inviteTargets == null) {
                this.inviteTargets = everyoneInvitable();
            }
            return this.inviteTargets;
        }
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

    static final class InviteTargetCollection {
        final List<FellowshipInviteTargetSnapshot> targets;
        final boolean truncated;

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

        /**
         * The first of {@code everyone}, in its order, that one receiver
         * may be offered: never their own account or the identity they
         * play. At most {@link FellowshipStateSnapshot#MAX_INVITE_TARGETS},
         * truncated where more were left.
         */
        static InviteTargetCollection offeredTo(
                List<FellowshipInviteTargetSnapshot> everyone,
                UUID receiverOwnerId, UUID receiverIdentityId) {
            List<FellowshipInviteTargetSnapshot> offered =
                    new ArrayList<FellowshipInviteTargetSnapshot>();
            for (FellowshipInviteTargetSnapshot target : everyone) {
                if (target.getOwnerId().equals(receiverOwnerId)
                        || target.getIdentityId().equals(receiverIdentityId)) {
                    continue;
                }
                if (offered.size() >= FellowshipStateSnapshot.MAX_INVITE_TARGETS) {
                    return new InviteTargetCollection(offered, true);
                }
                offered.add(target);
            }
            return new InviteTargetCollection(offered, false);
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
