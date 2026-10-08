package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.fellowship.FellowshipTrackingSyncPacket;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipGoHereMarker;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.storage.FellowshipGoHereMarkerWorldData;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.fellowship.sync.FellowshipGoHereMarkerSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackedMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends only authorized, quantized fellowship positions and persistent personal
 * markers. No client-reported coordinates are accepted by this subsystem.
 */
public final class FellowshipTrackingSyncManager {

    private static final Map<UUID, SentState> SENT_STATES =
            new HashMap<UUID, SentState>();

    private static long serverTicks;

    private FellowshipTrackingSyncManager() {}

    public static synchronized void tick() {
        serverTicks++;
        int interval = Math.max(2, Math.min(40,
                LostTalesConfig.fellowshipTrackingUpdateIntervalTicks));
        if (serverTicks % interval != 0L) {
            return;
        }
        synchronizeOnlinePlayers(false);
    }

    public static synchronized boolean sendNow(EntityPlayerMP recipient) {
        if (!LostTalesServerPlayers.isServerPlayer(recipient)) {
            return false;
        }
        ServerView view = collectServerView();
        return view != null && sendForPlayer(recipient, view, true);
    }

    public static synchronized void clearPlayer(UUID ownerId) {
        if (ownerId != null) {
            SENT_STATES.remove(ownerId);
        }
    }

    public static synchronized void clear() {
        SENT_STATES.clear();
        serverTicks = 0L;
    }

    /** Immediately removes or restores live coordinates after concealment changes. */
    public static synchronized void refreshAll() {
        synchronizeOnlinePlayers(true);
    }

    private static void synchronizeOnlinePlayers(boolean force) {
        ServerView view = collectServerView();
        if (view == null) {
            return;
        }
        for (OnlinePlayerContext context : view.onlineByOwner.values()) {
            if (context != null && context.player != null) {
                sendForPlayer(context.player, view, force);
            }
        }
    }

    private static boolean sendForPlayer(EntityPlayerMP recipient,
                                         ServerView view,
                                         boolean force) {
        OnlinePlayerContext receiver = view.onlineByOwner.get(
                recipient.getUniqueID());
        if (receiver == null) {
            return false;
        }
        // The character being played owns the marker and travels with the
        // fellowship.
        Fellowship fellowship = view.fellowshipData.getTravellingFellowship(receiver.gameplayId);
        FellowshipTrackingSnapshot content = fellowship == null
                ? buildSoloContent(recipient.getUniqueID(),
                        receiver.gameplayId, receiver.displayName, view)
                : buildFellowshipContent(recipient.getUniqueID(),
                        receiver.gameplayId, fellowship, view);

        SentState sent = SENT_STATES.get(recipient.getUniqueID());
        if (sent == null) {
            sent = new SentState();
            SENT_STATES.put(recipient.getUniqueID(), sent);
        }
        int heartbeat = Math.max(20, Math.min(400,
                LostTalesConfig.fellowshipTrackingHeartbeatTicks));
        boolean heartbeatDue = serverTicks - sent.lastSentTick >= heartbeat;
        if (!force && !heartbeatDue && sent.lastSnapshot != null
                && content.hasSameContent(sent.lastSnapshot)) {
            return false;
        }

        FellowshipTrackingSnapshot outgoing = copyWithSequence(
                content, sent.nextSequence());
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new FellowshipTrackingSyncPacket(outgoing), recipient);
        sent.lastSnapshot = outgoing;
        sent.lastSentTick = serverTicks;
        return true;
    }

    /**
     * One player, no fellowship: their own marker and nothing else.
     *
     * <p>{@code markerOwnerId} is the character being played.</p>
     */
    private static FellowshipTrackingSnapshot buildSoloContent(
            UUID recipientOwnerId, UUID markerOwnerId,
            String ownerName, ServerView view) {
        ArrayList<FellowshipGoHereMarkerSnapshot> markers =
                new ArrayList<FellowshipGoHereMarkerSnapshot>(1);
        FellowshipGoHereMarker marker =
                view.markerData.getMarker(markerOwnerId);
        if (marker != null) {
            markers.add(toMarkerSnapshot(
                    marker, ownerName, FellowshipColor.GREEN));
        }
        return FellowshipTrackingSnapshot.noFellowship(
                recipientOwnerId, 1L, markerOwnerId, markers);
    }

    private static FellowshipTrackingSnapshot buildFellowshipContent(
            UUID recipientOwnerId,
            UUID recipientIdentityId,
            Fellowship fellowship,
            ServerView view) {
        ArrayList<FellowshipTrackedMemberSnapshot> tracked =
                new ArrayList<FellowshipTrackedMemberSnapshot>();
        for (FellowshipMember member : fellowship.getMembers()) {
            if (recipientIdentityId.equals(member.getIdentityId())) {
                continue;
            }
            OnlinePlayerContext online = view.onlineByOwner.get(
                    member.getOwnerId());
            FellowshipTrackedMemberSnapshot snapshot = buildTrackedMember(
                    member, online);
            if (snapshot != null) {
                tracked.add(snapshot);
            }
        }

        ArrayList<FellowshipGoHereMarkerSnapshot> markers =
                new ArrayList<FellowshipGoHereMarkerSnapshot>();
        for (FellowshipMember owner : fellowship.getMembers()) {
            FellowshipGoHereMarker marker = view.markerData.getMarker(
                    owner.getIdentityId());
            if (marker != null) {
                markers.add(toMarkerSnapshot(
                        marker, owner.getCharacterName(), owner.getColor()));
            }
        }
        Collections.sort(markers,
                new Comparator<FellowshipGoHereMarkerSnapshot>() {
                    @Override
                    public int compare(FellowshipGoHereMarkerSnapshot left,
                                       FellowshipGoHereMarkerSnapshot right) {
                        return left.getOwnerIdentityId().toString().compareTo(
                                right.getOwnerIdentityId().toString());
                    }
                });
        return new FellowshipTrackingSnapshot(
                recipientOwnerId,
                1L,
                recipientIdentityId,
                fellowship.getFellowshipId(),
                fellowship.getRevision(),
                tracked,
                markers);
    }

    private static FellowshipGoHereMarkerSnapshot toMarkerSnapshot(
            FellowshipGoHereMarker marker, String ownerName,
            FellowshipColor ownerColor) {
        return new FellowshipGoHereMarkerSnapshot(
                marker.getOwnerIdentityId(), ownerName, ownerColor,
                marker.getDimensionId(),
                marker.getX(), marker.getY(), marker.getZ(),
                marker.getUpdatedAt());
    }

    private static FellowshipTrackedMemberSnapshot buildTrackedMember(
            FellowshipMember member,
            OnlinePlayerContext online) {
        if (online == null || online.player == null
                || !member.getIdentityId().equals(online.gameplayId)) {
            return null;
        }
        EntityPlayerMP player = online.player;
        if (player.isDead || !player.isEntityAlive()
                || AccessoryEffectService.isConcealed(player)
                || !FellowshipGoHereMarker.isValidCoordinates(
                player.posX, player.posY, player.posZ)) {
            return null;
        }
        return new FellowshipTrackedMemberSnapshot(
                member.getIdentityId(),
                member.getCharacterName(),
                member.getColor(),
                player.dimension,
                FellowshipService.quantizeTrackingCoordinate(player.posX),
                FellowshipService.quantizeTrackingCoordinate(player.posY),
                FellowshipService.quantizeTrackingCoordinate(player.posZ));
    }

    private static FellowshipTrackingSnapshot copyWithSequence(
            FellowshipTrackingSnapshot source, long sequence) {
        return source.hasFellowship()
                ? new FellowshipTrackingSnapshot(
                source.getOwnerId(), sequence,
                source.getActiveIdentityId(),
                source.getFellowshipId(), source.getFellowshipRevision(),
                source.getTrackedMembers(),
                source.getGoHereMarkers())
                : FellowshipTrackingSnapshot.noFellowship(
                source.getOwnerId(), sequence,
                source.getActiveIdentityId(),
                source.getGoHereMarkers());
    }

    private static ServerView collectServerView() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }
        WorldServer overworld = server.worldServerForDimension(0);
        if (overworld == null) {
            return null;
        }
        FellowshipWorldData fellowshipData;
        FellowshipGoHereMarkerWorldData markerData;
        try {
            fellowshipData = FellowshipStorage.get(overworld);
            markerData = FellowshipService.getInstance()
                    .getGoHereMarkerData(overworld);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Unable to synchronize fellowship tracking: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
        if (markerData == null || markerData.isReadOnlyForNewerVersion()
                || !fellowshipData.areCharacterReferencesValidated()) {
            return null;
        }

        Map<UUID, OnlinePlayerContext> onlineByOwner =
                new HashMap<UUID, OnlinePlayerContext>();
        List<?> players = server.getConfigurationManager().playerEntityList;
        if (players != null) {
            for (Object value : players) {
                if (!(value instanceof EntityPlayerMP)) {
                    continue;
                }
                EntityPlayerMP player = (EntityPlayerMP) value;
                if (!LostTalesServerPlayers.isServerPlayer(player)) {
                    continue;
                }
                FellowshipService.ActiveIdentityContext active =
                        FellowshipService.getInstance().resolveActiveIdentity(player);
                if (!active.isValid()) {
                    continue;
                }
                onlineByOwner.put(player.getUniqueID(),
                        new OnlinePlayerContext(player, active.gameplayId(),
                                active.displayName));
            }
        }
        return new ServerView(fellowshipData, markerData, onlineByOwner);
    }

    private static final class ServerView {
        private final FellowshipWorldData fellowshipData;
        private final FellowshipGoHereMarkerWorldData markerData;
        private final Map<UUID, OnlinePlayerContext> onlineByOwner;

        private ServerView(FellowshipWorldData fellowshipData,
                           FellowshipGoHereMarkerWorldData markerData,
                           Map<UUID, OnlinePlayerContext> onlineByOwner) {
            this.fellowshipData = fellowshipData;
            this.markerData = markerData;
            this.onlineByOwner = onlineByOwner;
        }
    }

    private static final class OnlinePlayerContext {
        private final EntityPlayerMP player;
        /** The id the player is playing as; never null. */
        private final UUID gameplayId;
        private final String displayName;

        private OnlinePlayerContext(EntityPlayerMP player, UUID gameplayId,
                                    String displayName) {
            this.player = player;
            this.gameplayId = gameplayId;
            this.displayName = displayName;
        }
    }

    private static final class SentState {
        private long sequence;
        private long lastSentTick;
        private FellowshipTrackingSnapshot lastSnapshot;

        private long nextSequence() {
            this.sequence++;
            if (this.sequence <= 0L) {
                this.sequence = 1L;
            }
            return this.sequence;
        }
    }
}
