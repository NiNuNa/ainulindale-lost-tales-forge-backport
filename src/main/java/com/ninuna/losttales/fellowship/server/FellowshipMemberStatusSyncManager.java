package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.compat.lotr.LotrMapLocations;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.fellowship.FellowshipMemberStatusSyncPacket;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberStatusSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStatusSnapshot;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sends each player the health and availability of the members of the
 * fellowship they travel with, and of no other. A member's world goes with
 * it only while the member's place is shared ({@link FellowshipPlaces}).
 */
public final class FellowshipMemberStatusSyncManager {

    private static final Map<UUID, SentState> SENT_STATES =
            new HashMap<UUID, SentState>();
    /** The recipients whose last send failed, so a fault is logged once. */
    private static final Set<UUID> FAILED = new HashSet<UUID>();

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

    /**
     * Sends each recipient a fresh snapshot after login, character, or
     * fellowship-state changes, all read from one view of the server.
     */
    public static synchronized void sendNow(Collection<EntityPlayerMP> recipients) {
        ServerView view = collectServerView();
        if (view == null) {
            return;
        }
        for (EntityPlayerMP recipient : recipients) {
            if (LostTalesServerPlayers.isServerPlayer(recipient)) {
                sendSafely(recipient, view, true);
            }
        }
    }

    public static synchronized void clearPlayer(UUID ownerId) {
        if (ownerId != null) {
            SENT_STATES.remove(ownerId);
            FAILED.remove(ownerId);
        }
    }

    public static synchronized void clear() {
        SENT_STATES.clear();
        FAILED.clear();
        serverTicks = 0L;
    }

    private static void synchronizeOnlinePlayers(boolean force) {
        ServerView view = collectServerView();
        if (view == null) {
            return;
        }
        for (FellowshipOnlineView.Online online : view.online.all()) {
            sendSafely(online.player, view, force);
        }
    }

    /**
     * {@link #sendForPlayer}, a fault kept to its one recipient: logged the
     * first time it fails for them, not on every pass after.
     */
    private static void sendSafely(EntityPlayerMP recipient, ServerView view,
                                   boolean force) {
        UUID ownerId = recipient.getUniqueID();
        try {
            sendForPlayer(recipient, view, force);
            FAILED.remove(ownerId);
        } catch (RuntimeException exception) {
            if (FAILED.add(ownerId)) {
                FMLLog.warning("[%s] Unable to send fellowship member status to %s: %s",
                        LostTalesMetaData.MOD_ID, ownerId, exception.toString());
            }
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
                receiver.gameplayId, fellowship, view);

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
            ServerView view) {
        ArrayList<FellowshipMemberStatusSnapshot> statuses =
                new ArrayList<FellowshipMemberStatusSnapshot>(fellowship.getMemberCount());
        boolean ruleOn = fellowship.isOn(FellowshipSwitch.SHOWN_ON_MAP);
        for (FellowshipMember member : fellowship.getMembers()) {
            statuses.add(buildMemberStatus(member,
                    view.online.get(member.getOwnerId()), ruleOn, view));
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
            FellowshipMember member, FellowshipOnlineView.Online online,
            boolean ruleOn, ServerView view) {
        UUID identityId = member.getIdentityId();
        if (online == null) {
            return FellowshipMemberStatusSnapshot.offline(identityId);
        }
        // The owner is online as another character, or still making one.
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
                view.placeShared(player, ruleOn)
                        ? player.dimension : FellowshipMemberStatusSnapshot.NO_DIMENSION,
                health,
                maximumHealth,
                view.helmetOf(player),
                view.heldItemOf(player));
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
            // Nothing goes until a login or a fellowship request has checked
            // the members against the rosters; this pass runs too often to
            // check them itself.
            return null;
        }

        return new ServerView(fellowshipData, FellowshipOnlineView.collect(characterData));
    }

    private static final class ServerView {
        private final FellowshipWorldData fellowshipData;
        private final FellowshipOnlineView online;
        /** Whether each player's place is shared under a rule that is on, read once a pass. */
        private final Map<UUID, Boolean> sharedByOwner = new HashMap<UUID, Boolean>();
        /** Each player's helmet and held item as the status carries them, bounded once a pass. */
        private final Map<UUID, ItemStack[]> gearByOwner = new HashMap<UUID, ItemStack[]>();

        private ServerView(FellowshipWorldData fellowshipData,
                           FellowshipOnlineView online) {
            this.fellowshipData = fellowshipData;
            this.online = online;
        }

        /** Whether the player's place goes to a fellowship whose map rule is {@code ruleOn}. */
        private boolean placeShared(EntityPlayerMP player, boolean ruleOn) {
            if (!ruleOn) {
                return false;
            }
            Boolean shared = this.sharedByOwner.get(player.getUniqueID());
            if (shared == null) {
                shared = Boolean.valueOf(FellowshipPlaces.shared(ruleOn,
                        AccessoryEffectService.isConcealed(player),
                        LotrMapLocations.hides(player)));
                this.sharedByOwner.put(player.getUniqueID(), shared);
            }
            return shared.booleanValue();
        }

        private ItemStack helmetOf(EntityPlayerMP player) {
            return gearOf(player)[0];
        }

        private ItemStack heldItemOf(EntityPlayerMP player) {
            return gearOf(player)[1];
        }

        /** The player's helmet and held item, each held to the wire's bound. */
        private ItemStack[] gearOf(EntityPlayerMP player) {
            ItemStack[] gear = this.gearByOwner.get(player.getUniqueID());
            if (gear == null) {
                gear = new ItemStack[] {
                        FellowshipMemberStatusSyncPacket.bounded(
                                player.getEquipmentInSlot(4)),
                        FellowshipMemberStatusSyncPacket.bounded(player.getHeldItem())
                };
                this.gearByOwner.put(player.getUniqueID(), gear);
            }
            return gear;
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
