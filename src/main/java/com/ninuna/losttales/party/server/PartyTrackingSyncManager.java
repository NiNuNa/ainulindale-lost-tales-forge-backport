package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.party.PartyTrackingSyncPacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyGoHereMarker;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.storage.PartyGoHereMarkerWorldData;
import com.ninuna.losttales.party.storage.PartyStorage;
import com.ninuna.losttales.party.storage.PartyWorldData;
import com.ninuna.losttales.party.sync.PartyGoHereMarkerSnapshot;
import com.ninuna.losttales.party.sync.PartyTrackedMemberSnapshot;
import com.ninuna.losttales.party.sync.PartyTrackingSnapshot;
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
 * Sends only authorized, quantized party positions and persistent personal
 * markers. No client-reported coordinates are accepted by this subsystem.
 */
public final class PartyTrackingSyncManager {

    private static final Map<UUID, SentState> SENT_STATES =
            new HashMap<UUID, SentState>();

    private static long serverTicks;

    private PartyTrackingSyncManager() {}

    public static synchronized void tick() {
        serverTicks++;
        int interval = Math.max(2, Math.min(40,
                LostTalesConfig.partyTrackingUpdateIntervalTicks));
        if (serverTicks % interval != 0L) {
            return;
        }
        synchronizeOnlinePlayers(false);
    }

    public static synchronized boolean sendNow(EntityPlayerMP recipient) {
        if (!isServerPlayer(recipient)) {
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
        if (receiver == null || receiver.activeCharacter == null) {
            return false;
        }
        UUID activeCharacterId = receiver.activeCharacter.getCharacterId();
        Party party = view.partyData.getPartyForCharacter(activeCharacterId);
        PartyTrackingSnapshot content = party == null
                ? buildSoloContent(recipient.getUniqueID(),
                receiver.activeCharacter, view)
                : buildPartyContent(recipient.getUniqueID(),
                activeCharacterId, party, view);

        SentState sent = SENT_STATES.get(recipient.getUniqueID());
        if (sent == null) {
            sent = new SentState();
            SENT_STATES.put(recipient.getUniqueID(), sent);
        }
        int heartbeat = Math.max(20, Math.min(400,
                LostTalesConfig.partyTrackingHeartbeatTicks));
        boolean heartbeatDue = serverTicks - sent.lastSentTick >= heartbeat;
        if (!force && !heartbeatDue && sent.lastSnapshot != null
                && content.hasSameContent(sent.lastSnapshot)) {
            return false;
        }

        PartyTrackingSnapshot outgoing = copyWithSequence(
                content, sent.nextSequence());
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new PartyTrackingSyncPacket(outgoing), recipient);
        sent.lastSnapshot = outgoing;
        sent.lastSentTick = serverTicks;
        return true;
    }

    private static PartyTrackingSnapshot buildSoloContent(
            UUID recipientOwnerId, RoleplayCharacter activeCharacter,
            ServerView view) {
        ArrayList<PartyGoHereMarkerSnapshot> markers =
                new ArrayList<PartyGoHereMarkerSnapshot>(1);
        PartyGoHereMarker marker = view.markerData.getMarker(
                activeCharacter.getCharacterId());
        if (marker != null) {
            markers.add(toMarkerSnapshot(
                    marker, activeCharacter.getName(), PartyColor.GREEN));
        }
        return PartyTrackingSnapshot.noParty(
                recipientOwnerId, 1L,
                activeCharacter.getCharacterId(), markers);
    }

    private static PartyTrackingSnapshot buildPartyContent(
            UUID recipientOwnerId,
            UUID recipientCharacterId,
            Party party,
            ServerView view) {
        ArrayList<PartyTrackedMemberSnapshot> tracked =
                new ArrayList<PartyTrackedMemberSnapshot>();
        for (PartyMember member : party.getMembers()) {
            if (recipientCharacterId.equals(member.getCharacterId())) {
                continue;
            }
            OnlinePlayerContext online = view.onlineByOwner.get(
                    member.getOwnerId());
            PartyTrackedMemberSnapshot snapshot = buildTrackedMember(
                    member, online);
            if (snapshot != null) {
                tracked.add(snapshot);
            }
        }

        ArrayList<PartyGoHereMarkerSnapshot> markers =
                new ArrayList<PartyGoHereMarkerSnapshot>();
        for (PartyMember owner : party.getMembers()) {
            PartyGoHereMarker marker = view.markerData.getMarker(
                    owner.getCharacterId());
            if (marker != null) {
                markers.add(toMarkerSnapshot(
                        marker, owner.getCharacterName(), owner.getColor()));
            }
        }
        Collections.sort(markers,
                new Comparator<PartyGoHereMarkerSnapshot>() {
                    @Override
                    public int compare(PartyGoHereMarkerSnapshot left,
                                       PartyGoHereMarkerSnapshot right) {
                        return left.getOwnerCharacterId().toString().compareTo(
                                right.getOwnerCharacterId().toString());
                    }
                });
        return new PartyTrackingSnapshot(
                recipientOwnerId,
                1L,
                recipientCharacterId,
                party.getPartyId(),
                party.getRevision(),
                tracked,
                markers);
    }

    private static PartyGoHereMarkerSnapshot toMarkerSnapshot(
            PartyGoHereMarker marker, String ownerName,
            PartyColor ownerColor) {
        return new PartyGoHereMarkerSnapshot(
                marker.getOwnerCharacterId(), ownerName, ownerColor,
                marker.getDimensionId(),
                marker.getX(), marker.getY(), marker.getZ(),
                marker.getUpdatedAt());
    }

    private static PartyTrackedMemberSnapshot buildTrackedMember(
            PartyMember member,
            OnlinePlayerContext online) {
        if (online == null || online.player == null
                || online.activeCharacter == null
                || !member.getCharacterId().equals(
                online.activeCharacter.getCharacterId())) {
            return null;
        }
        EntityPlayerMP player = online.player;
        if (player.isDead || !player.isEntityAlive()
                || AccessoryEffectService.isConcealed(player)
                || !PartyGoHereMarker.isValidCoordinates(
                player.posX, player.posY, player.posZ)) {
            return null;
        }
        return new PartyTrackedMemberSnapshot(
                member.getCharacterId(),
                member.getCharacterName(),
                member.getColor(),
                player.dimension,
                PartyService.quantizeTrackingCoordinate(player.posX),
                PartyService.quantizeTrackingCoordinate(player.posY),
                PartyService.quantizeTrackingCoordinate(player.posZ));
    }

    private static PartyTrackingSnapshot copyWithSequence(
            PartyTrackingSnapshot source, long sequence) {
        return source.hasParty()
                ? new PartyTrackingSnapshot(
                source.getOwnerId(), sequence,
                source.getActiveCharacterId(),
                source.getPartyId(), source.getPartyRevision(),
                source.getTrackedMembers(),
                source.getGoHereMarkers())
                : PartyTrackingSnapshot.noParty(
                source.getOwnerId(), sequence,
                source.getActiveCharacterId(),
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
        PartyWorldData partyData;
        PartyGoHereMarkerWorldData markerData;
        try {
            partyData = PartyStorage.get(overworld);
            markerData = PartyService.getInstance()
                    .getGoHereMarkerData(overworld);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Unable to synchronize party tracking: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }
        if (markerData == null || markerData.isReadOnlyForNewerVersion()
                || !partyData.areCharacterReferencesValidated()) {
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
                if (!isServerPlayer(player)) {
                    continue;
                }
                PartyService.ActiveCharacterContext active =
                        PartyService.getInstance().resolveActiveCharacter(player);
                onlineByOwner.put(player.getUniqueID(),
                        new OnlinePlayerContext(
                                player,
                                active.isValid() ? active.character : null));
            }
        }
        return new ServerView(partyData, markerData, onlineByOwner);
    }

    private static boolean isServerPlayer(EntityPlayerMP player) {
        return player != null && player.getUniqueID() != null
                && player.worldObj != null && !player.worldObj.isRemote;
    }

    private static final class ServerView {
        private final PartyWorldData partyData;
        private final PartyGoHereMarkerWorldData markerData;
        private final Map<UUID, OnlinePlayerContext> onlineByOwner;

        private ServerView(PartyWorldData partyData,
                           PartyGoHereMarkerWorldData markerData,
                           Map<UUID, OnlinePlayerContext> onlineByOwner) {
            this.partyData = partyData;
            this.markerData = markerData;
            this.onlineByOwner = onlineByOwner;
        }
    }

    private static final class OnlinePlayerContext {
        private final EntityPlayerMP player;
        private final RoleplayCharacter activeCharacter;

        private OnlinePlayerContext(EntityPlayerMP player,
                                    RoleplayCharacter activeCharacter) {
            this.player = player;
            this.activeCharacter = activeCharacter;
        }
    }

    private static final class SentState {
        private long sequence;
        private long lastSentTick;
        private PartyTrackingSnapshot lastSnapshot;

        private long nextSequence() {
            this.sequence++;
            if (this.sequence <= 0L) {
                this.sequence = 1L;
            }
            return this.sequence;
        }
    }
}
