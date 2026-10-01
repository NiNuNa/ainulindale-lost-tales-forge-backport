package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.fellowship.FellowshipMemberStatusSyncPacket;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberStatusSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStatusSnapshot;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends each player the health and availability of the members of the
 * fellowship they travel with, and of no other. The logical server is the
 * sole source of runtime status data.
 */
public final class FellowshipMemberStatusSyncManager {

    private static final Map<UUID, SentState> SENT_STATES =
            new HashMap<UUID, SentState>();

    private static long serverTicks;

    private FellowshipMemberStatusSyncManager() {}

    /** Called once at the end of each logical server tick. */
    public static synchronized void tick() {
        serverTicks++;
        int interval = Math.max(2, Math.min(40,
                LostTalesConfig.fellowshipStatusUpdateIntervalTicks));
        if (serverTicks % interval != 0L) {
            return;
        }
        synchronizeOnlinePlayers(false);
    }

    /** Sends a fresh snapshot after login, character, or fellowship-state changes. */
    public static synchronized boolean sendNow(EntityPlayerMP recipient) {
        if (!LostTalesServerPlayers.isServerPlayer(recipient)) {
            return false;
        }
        ServerView view = collectServerView();
        if (view == null) {
            return false;
        }
        return sendForPlayer(recipient, view, true);
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

    private static void synchronizeOnlinePlayers(boolean force) {
        ServerView view = collectServerView();
        if (view == null) {
            return;
        }
        for (FellowshipOnlineView.Online online : view.online.all()) {
            sendForPlayer(online.player, view, force);
        }
    }

    private static boolean sendForPlayer(EntityPlayerMP recipient,
                                         ServerView view,
                                         boolean force) {
        FellowshipOnlineView.Online receiver = view.online.get(recipient.getUniqueID());
        if (receiver == null) {
            return false;
        }

        Fellowship fellowship = view.fellowshipData.getTravellingFellowship(
                receiver.gameplayId);
        FellowshipStatusSnapshot content = fellowship == null
                ? FellowshipStatusSnapshot.noFellowship(
                recipient.getUniqueID(), 1L,
                receiver.gameplayId)
                : buildFellowshipContent(recipient.getUniqueID(),
                receiver.gameplayId, fellowship, view.online);

        SentState sent = SENT_STATES.get(recipient.getUniqueID());
        if (!content.hasFellowship() && !force
                && (sent == null || sent.lastSnapshot == null
                || !sent.lastSnapshot.hasFellowship())) {
            return false;
        }
        if (sent == null) {
            sent = new SentState();
            SENT_STATES.put(recipient.getUniqueID(), sent);
        }
        int heartbeat = Math.max(20, Math.min(400,
                LostTalesConfig.fellowshipStatusHeartbeatTicks));
        boolean heartbeatDue = serverTicks - sent.lastSentTick >= heartbeat;
        if (!force && !heartbeatDue && sent.lastSnapshot != null
                && content.hasSameContent(sent.lastSnapshot)) {
            return false;
        }

        long sequence = sent.nextSequence();
        FellowshipStatusSnapshot outgoing = copyWithSequence(content, sequence);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new FellowshipMemberStatusSyncPacket(outgoing), recipient);
        sent.lastSnapshot = outgoing;
        sent.lastSentTick = serverTicks;
        return true;
    }

    private static FellowshipStatusSnapshot buildFellowshipContent(
            UUID recipientOwnerId,
            UUID activeIdentityId,
            Fellowship fellowship,
            FellowshipOnlineView online) {
        ArrayList<FellowshipMemberStatusSnapshot> statuses =
                new ArrayList<FellowshipMemberStatusSnapshot>(fellowship.getMemberCount());
        for (FellowshipMember member : fellowship.getMembers()) {
            statuses.add(buildMemberStatus(member, online.get(member.getOwnerId())));
        }
        return new FellowshipStatusSnapshot(
                recipientOwnerId,
                1L,
                activeIdentityId,
                fellowship.getFellowshipId(),
                fellowship.getRevision(),
                statuses);
    }

    private static FellowshipMemberStatusSnapshot buildMemberStatus(
            FellowshipMember member, FellowshipOnlineView.Online online) {
        UUID identityId = member.getIdentityId();
        if (online == null) {
            return FellowshipMemberStatusSnapshot.offline(identityId);
        }
        // The owner is online as another identity: a different character,
        // or the account itself.
        if (!identityId.equals(online.gameplayId)) {
            return FellowshipMemberStatusSnapshot.inactive(identityId);
        }

        EntityPlayerMP player = online.player;
        float maximumHealth;
        float health;
        try {
            maximumHealth = player.getMaxHealth();
            health = player.getHealth();
        } catch (RuntimeException exception) {
            return FellowshipMemberStatusSnapshot.unavailable(identityId);
        }
        if (!Float.isFinite(maximumHealth) || !Float.isFinite(health)
                || maximumHealth <= 0.0F) {
            return FellowshipMemberStatusSnapshot.unavailable(identityId);
        }
        maximumHealth = Math.min(maximumHealth,
                FellowshipMemberStatusSnapshot.MAX_SYNCHRONIZED_HEALTH);
        health = Math.max(0.0F, Math.min(health, maximumHealth));
        boolean dead = player.isDead || !player.isEntityAlive()
                || health <= 0.0F;
        return FellowshipMemberStatusSnapshot.online(
                identityId,
                dead,
                player.dimension,
                health,
                maximumHealth,
                player.getEquipmentInSlot(4),
                player.getHeldItem());
    }

    private static FellowshipStatusSnapshot copyWithSequence(
            FellowshipStatusSnapshot source, long sequence) {
        return source.hasFellowship()
                ? new FellowshipStatusSnapshot(
                source.getOwnerId(), sequence,
                source.getActiveIdentityId(),
                source.getFellowshipId(), source.getFellowshipRevision(),
                source.getMemberStatuses())
                : FellowshipStatusSnapshot.noFellowship(
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
        CharacterWorldData characterData;
        try {
            fellowshipData = FellowshipStorage.get(overworld);
            characterData = CharacterStorage.get(overworld);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Unable to synchronize fellowship member status: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }

        if (characterData.isReadOnlyForNewerVersion()
                || !fellowshipData.areCharacterReferencesValidated()) {
            // Login and authoritative fellowship operations perform the full
            // referential-integrity pass. Do not rescan every stored roster on
            // each health update; defer runtime status until that pass succeeds.
            return null;
        }

        return new ServerView(fellowshipData, FellowshipOnlineView.collect(characterData));
    }

    private static final class ServerView {
        private final FellowshipWorldData fellowshipData;
        private final FellowshipOnlineView online;

        private ServerView(FellowshipWorldData fellowshipData,
                           FellowshipOnlineView online) {
            this.fellowshipData = fellowshipData;
            this.online = online;
        }
    }

    private static final class SentState {
        private long sequence;
        private long lastSentTick = Long.MIN_VALUE / 2L;
        private FellowshipStatusSnapshot lastSnapshot;

        private long nextSequence() {
            this.sequence++;
            if (this.sequence <= 0L) {
                this.sequence = 1L;
            }
            return this.sequence;
        }
    }
}
