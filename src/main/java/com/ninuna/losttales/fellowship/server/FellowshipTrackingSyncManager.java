package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.compat.lotr.LotrMapLocations;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.fellowship.FellowshipTrackingSyncPacket;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackedMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends each player the quantized positions of the members of the
 * fellowship they travel with, and nothing else; the marks ride in the
 * fellowship state. A member's position goes only while the fellowship
 * shows members on the map and the member is neither concealed nor hidden
 * on LOTR's map ({@link FellowshipPlaces}). No client-reported coordinates
 * are accepted by this subsystem.
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

    /** Sends each recipient a fresh snapshot, all read from one view of the server. */
    public static synchronized void sendNow(Collection<EntityPlayerMP> recipients) {
        ServerView view = collectServerView();
        if (view == null) {
            return;
        }
        for (EntityPlayerMP recipient : recipients) {
            if (LostTalesServerPlayers.isServerPlayer(recipient)) {
                sendForPlayer(recipient, view, true);
            }
        }
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
        Fellowship fellowship = view.fellowshipData.getTravellingFellowship(receiver.gameplayId);
        FellowshipTrackingSnapshot content = fellowship == null
                ? FellowshipTrackingSnapshot.noFellowship(recipient.getUniqueID(), 1L,
                        receiver.gameplayId)
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

    private static FellowshipTrackingSnapshot buildFellowshipContent(
            UUID recipientOwnerId,
            UUID recipientIdentityId,
            Fellowship fellowship,
            ServerView view) {
        ArrayList<FellowshipTrackedMemberSnapshot> tracked =
                new ArrayList<FellowshipTrackedMemberSnapshot>();
        boolean ruleOn = fellowship.isOn(FellowshipSwitch.SHOWN_ON_MAP);
        for (FellowshipMember member : fellowship.getMembers()) {
            if (recipientIdentityId.equals(member.getIdentityId())) {
                continue;
            }
            OnlinePlayerContext online = view.onlineByOwner.get(
                    member.getOwnerId());
            FellowshipTrackedMemberSnapshot snapshot = buildTrackedMember(
                    member, online, ruleOn);
            if (snapshot != null) {
                tracked.add(snapshot);
            }
        }
        return new FellowshipTrackingSnapshot(
                recipientOwnerId,
                1L,
                recipientIdentityId,
                fellowship.getFellowshipId(),
                fellowship.getRevision(),
                tracked);
    }

    private static FellowshipTrackedMemberSnapshot buildTrackedMember(
            FellowshipMember member,
            OnlinePlayerContext online,
            boolean ruleOn) {
        if (online == null || online.player == null
                || !member.getIdentityId().equals(online.gameplayId)
                || !FellowshipPlaces.shared(ruleOn, online.concealed, online.hidden)) {
            return null;
        }
        EntityPlayerMP player = online.player;
        if (player.isDead || !player.isEntityAlive()
                || !FellowshipTrackedMemberSnapshot.isValidPosition(
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
                source.getTrackedMembers())
                : FellowshipTrackingSnapshot.noFellowship(
                source.getOwnerId(), sequence,
                source.getActiveIdentityId());
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
        try {
            fellowshipData = FellowshipStorage.get(overworld);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Unable to synchronize fellowship tracking: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
        if (!fellowshipData.areCharacterReferencesValidated()) {
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
                                AccessoryEffectService.isConcealed(player),
                                LotrMapLocations.hides(player)));
            }
        }
        return new ServerView(fellowshipData, onlineByOwner);
    }

    private static final class ServerView {
        private final FellowshipWorldData fellowshipData;
        private final Map<UUID, OnlinePlayerContext> onlineByOwner;

        private ServerView(FellowshipWorldData fellowshipData,
                           Map<UUID, OnlinePlayerContext> onlineByOwner) {
            this.fellowshipData = fellowshipData;
            this.onlineByOwner = onlineByOwner;
        }
    }

    private static final class OnlinePlayerContext {
        private final EntityPlayerMP player;
        /** The id the player is playing as; never null. */
        private final UUID gameplayId;
        /** Read once a pass, not for every recipient. */
        private final boolean concealed;
        /** Hidden on LOTR's map; read once a pass, not for every recipient. */
        private final boolean hidden;

        private OnlinePlayerContext(EntityPlayerMP player, UUID gameplayId,
                                    boolean concealed, boolean hidden) {
            this.player = player;
            this.gameplayId = gameplayId;
            this.concealed = concealed;
            this.hidden = hidden;
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
