package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.party.PartyMemberStatusSyncPacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.storage.PartyStorage;
import com.ninuna.losttales.party.storage.PartyWorldData;
import com.ninuna.losttales.party.sync.PartyMemberStatusSnapshot;
import com.ninuna.losttales.party.sync.PartyStatusSnapshot;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends bounded party health and availability snapshots only to current party
 * members. The logical server is the sole source of runtime status data.
 */
public final class PartyMemberStatusSyncManager {

    private static final Map<UUID, SentState> SENT_STATES =
            new HashMap<UUID, SentState>();

    private static long serverTicks;

    private PartyMemberStatusSyncManager() {}

    /** Called once at the end of each logical server tick. */
    public static synchronized void tick() {
        serverTicks++;
        int interval = Math.max(2, Math.min(40,
                LostTalesConfig.partyStatusUpdateIntervalTicks));
        if (serverTicks % interval != 0L) {
            return;
        }
        synchronizeOnlinePlayers(false);
    }

    /** Sends a fresh snapshot after login, character, or party-state changes. */
    public static synchronized boolean sendNow(EntityPlayerMP recipient) {
        if (!isServerPlayer(recipient)) {
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
        if (receiver == null || receiver.activeCharacterId == null) {
            // Keep the per-connection sequence while the player temporarily has
            // no valid active character. Structural state hides the old status,
            // and a later character selection must still advance the sequence.
            return false;
        }

        Party party = view.partyData.getPartyForCharacter(
                receiver.activeCharacterId);
        PartyStatusSnapshot content = party == null
                ? PartyStatusSnapshot.noParty(
                recipient.getUniqueID(), 1L,
                receiver.activeCharacterId)
                : buildPartyContent(recipient.getUniqueID(),
                receiver.activeCharacterId, party, view.onlineByOwner);

        SentState sent = SENT_STATES.get(recipient.getUniqueID());
        if (!content.hasParty() && !force
                && (sent == null || sent.lastSnapshot == null
                || !sent.lastSnapshot.hasParty())) {
            return false;
        }
        if (sent == null) {
            sent = new SentState();
            SENT_STATES.put(recipient.getUniqueID(), sent);
        }
        int heartbeat = Math.max(20, Math.min(400,
                LostTalesConfig.partyStatusHeartbeatTicks));
        boolean heartbeatDue = serverTicks - sent.lastSentTick >= heartbeat;
        if (!force && !heartbeatDue && sent.lastSnapshot != null
                && content.hasSameContent(sent.lastSnapshot)) {
            return false;
        }

        long sequence = sent.nextSequence();
        PartyStatusSnapshot outgoing = copyWithSequence(content, sequence);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new PartyMemberStatusSyncPacket(outgoing), recipient);
        sent.lastSnapshot = outgoing;
        sent.lastSentTick = serverTicks;
        return true;
    }

    private static PartyStatusSnapshot buildPartyContent(
            UUID recipientOwnerId,
            UUID activeCharacterId,
            Party party,
            Map<UUID, OnlinePlayerContext> onlineByOwner) {
        ArrayList<PartyMemberStatusSnapshot> statuses =
                new ArrayList<PartyMemberStatusSnapshot>(party.getMemberCount());
        for (PartyMember member : party.getMembers()) {
            OnlinePlayerContext online = onlineByOwner.get(member.getOwnerId());
            statuses.add(buildMemberStatus(member, online));
        }
        return new PartyStatusSnapshot(
                recipientOwnerId,
                1L,
                activeCharacterId,
                party.getPartyId(),
                party.getRevision(),
                statuses);
    }

    private static PartyMemberStatusSnapshot buildMemberStatus(
            PartyMember member, OnlinePlayerContext online) {
        UUID characterId = member.getCharacterId();
        if (online == null || online.player == null) {
            return PartyMemberStatusSnapshot.offline(characterId);
        }
        if (online.activeCharacterId == null) {
            return PartyMemberStatusSnapshot.unavailable(characterId);
        }
        if (!characterId.equals(online.activeCharacterId)) {
            return PartyMemberStatusSnapshot.inactive(characterId);
        }

        EntityPlayerMP player = online.player;
        float maximumHealth;
        float health;
        try {
            maximumHealth = player.getMaxHealth();
            health = player.getHealth();
        } catch (RuntimeException exception) {
            return PartyMemberStatusSnapshot.unavailable(characterId);
        }
        if (!isFinite(maximumHealth) || !isFinite(health)
                || maximumHealth <= 0.0F) {
            return PartyMemberStatusSnapshot.unavailable(characterId);
        }
        maximumHealth = Math.min(maximumHealth,
                PartyMemberStatusSnapshot.MAX_SYNCHRONIZED_HEALTH);
        health = Math.max(0.0F, Math.min(health, maximumHealth));
        boolean dead = player.isDead || !player.isEntityAlive()
                || health <= 0.0F;
        return PartyMemberStatusSnapshot.online(
                characterId,
                dead,
                player.dimension,
                health,
                maximumHealth);
    }

    private static PartyStatusSnapshot copyWithSequence(
            PartyStatusSnapshot source, long sequence) {
        return source.hasParty()
                ? new PartyStatusSnapshot(
                source.getOwnerId(), sequence,
                source.getActiveCharacterId(),
                source.getPartyId(), source.getPartyRevision(),
                source.getMemberStatuses())
                : PartyStatusSnapshot.noParty(
                source.getOwnerId(), sequence,
                source.getActiveCharacterId());
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
        CharacterWorldData characterData;
        try {
            partyData = PartyStorage.get(overworld);
            characterData = CharacterStorage.get(overworld);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Unable to synchronize party member status: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
            return null;
        }

        if (characterData.isReadOnlyForNewerVersion()
                || !partyData.areCharacterReferencesValidated()) {
            // Login and authoritative party operations perform the full
            // referential-integrity pass. Do not rescan every stored roster on
            // each health update; defer runtime status until that pass succeeds.
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
                UUID ownerId = player.getUniqueID();
                RoleplayCharacter character = resolveActiveCharacter(
                        characterData, ownerId);
                onlineByOwner.put(
                        ownerId,
                        new OnlinePlayerContext(
                                player,
                                character == null ? null
                                        : character.getCharacterId()));
            }
        }
        return new ServerView(partyData, onlineByOwner);
    }


    private static RoleplayCharacter resolveActiveCharacter(
            CharacterWorldData characterData, UUID ownerId) {
        if (characterData == null || ownerId == null) {
            return null;
        }
        CharacterRoster roster = characterData.getRoster(ownerId);
        RoleplayCharacter active = roster == null
                ? null : roster.getActiveCharacter();
        return active != null && ownerId.equals(active.getOwnerId())
                ? active : null;
    }

    private static boolean isServerPlayer(EntityPlayerMP player) {
        return player != null && player.getUniqueID() != null
                && player.worldObj != null && !player.worldObj.isRemote;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static final class ServerView {
        private final PartyWorldData partyData;
        private final Map<UUID, OnlinePlayerContext> onlineByOwner;

        private ServerView(PartyWorldData partyData,
                           Map<UUID, OnlinePlayerContext> onlineByOwner) {
            this.partyData = partyData;
            this.onlineByOwner = onlineByOwner;
        }
    }

    private static final class OnlinePlayerContext {
        private final EntityPlayerMP player;
        private final UUID activeCharacterId;

        private OnlinePlayerContext(EntityPlayerMP player,
                                    UUID activeCharacterId) {
            this.player = player;
            this.activeCharacterId = activeCharacterId;
        }
    }

    private static final class SentState {
        private long sequence;
        private long lastSentTick = Long.MIN_VALUE / 2L;
        private PartyStatusSnapshot lastSnapshot;

        private long nextSequence() {
            this.sequence++;
            if (this.sequence <= 0L) {
                this.sequence = 1L;
            }
            return this.sequence;
        }
    }
}
