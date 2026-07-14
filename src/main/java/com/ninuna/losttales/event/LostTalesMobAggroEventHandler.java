package com.ninuna.losttales.event;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.entity.LostTalesHostilityHelper;
import com.ninuna.losttales.entity.combat.LostTalesCombatEngagement;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.storage.PartyStorage;
import com.ninuna.losttales.party.storage.PartyWorldData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Server-authoritative combat tracker used by compass and map enemy markers.
 * Direct evidence remains player-scoped; authorized online party members in the same world receive a filtered union.
 */
public class LostTalesMobAggroEventHandler {
    public static final int DEFAULT_AGGRO_MOB_SCAN_RADIUS = 64;
    private static final int SNAPSHOT_HEARTBEAT_TICKS = 100;
    private static final Map<UUID, PlayerCombatState> PLAYER_STATES = new HashMap<UUID, PlayerCombatState>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.side != Side.SERVER || event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.worldObj == null) {
            removePlayer(player);
            return;
        }
        if (player.isDead) {
            clearTrackedEnemies(player, true);
            return;
        }

        int interval = Math.max(1, LostTalesConfig.combatMarkerUpdateIntervalTicks);
        if (player.ticksExisted % interval != 0) {
            return;
        }

        updatePlayerSnapshot(player);
    }

    /** Records strong player-specific evidence after a living entity actually hurts the player. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (event == null || event.isCanceled() || event.ammount <= 0.0F
                || !(event.entityLiving instanceof EntityPlayerMP)) {
            return;
        }

        recordIncomingDamage((EntityPlayerMP) event.entityLiving, resolveLivingAttacker(event.source));
    }

    private static synchronized void recordIncomingDamage(EntityPlayerMP player, EntityLivingBase attacker) {
        if (!isValidTrackedEntity(attacker, player, getTrackingRadiusSq())) {
            return;
        }

        PlayerCombatState state = getOrCreateState(player);
        Integer attackerId = Integer.valueOf(attacker.getEntityId());
        if (!makeRoomForStrongEvidence(state, attackerId, null)) {
            return;
        }

        long now = player.worldObj.getTotalWorldTime();
        TrackedEngagement tracked = getOrCreateEntry(state, attacker.getEntityId());
        tracked.lastDamageTick = now;
        int minimumEvidenceTicks = Math.max(1, LostTalesConfig.combatMarkerUpdateIntervalTicks) + 1;
        int retentionTicks = Math.max(minimumEvidenceTicks, LostTalesConfig.combatMarkerDisengagementGraceTicks);
        tracked.expiresAtTick = now + retentionTicks;
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        removePlayer(event == null ? null : event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resetPlayer(event == null ? null : event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resetPlayer(event == null ? null : event.player);
    }

    public static synchronized void clearAll() {
        PLAYER_STATES.clear();
    }

    /** Conservative combat evidence used by character-switch policy. */
    public static synchronized boolean hasTrackedCombat(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null) {
            return false;
        }
        PlayerCombatState state = PLAYER_STATES.get(player.getUniqueID());
        return state != null
                && (!state.entries.isEmpty() || !state.currentSnapshot.isEmpty());
    }

    private static synchronized void updatePlayerSnapshot(EntityPlayerMP player) {
        PlayerCombatState state = getOrCreateState(player);
        int dimensionId = player.worldObj.provider.dimensionId;
        if (state.dimensionId != dimensionId) {
            state.resetForDimension(dimensionId);
        }

        long now = player.worldObj.getTotalWorldTime();
        int trackingRadius = getTrackingRadius();
        double trackingRadiusSq = (double) trackingRadius * (double) trackingRadius;
        int graceTicks = Math.max(0, LostTalesConfig.combatMarkerDisengagementGraceTicks);
        TreeMap<Integer, LostTalesCombatEngagement> snapshot = new TreeMap<Integer, LostTalesCombatEngagement>();

        pruneInvalidOrExpiredEntries(state, player, trackingRadiusSq, now);

        AxisAlignedBB scanBox = player.boundingBox.expand(trackingRadius, trackingRadius, trackingRadius);
        List<?> nearby = player.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, scanBox);
        for (Object object : nearby) {
            if (!(object instanceof EntityLivingBase) || object == player) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) object;
            if (!isValidTrackedEntity(living, player, trackingRadiusSq)) {
                continue;
            }

            LostTalesCombatEngagement direct = LostTalesHostilityHelper.getDirectEngagement(living, player);
            if (direct == LostTalesCombatEngagement.NONE) {
                continue;
            }

            Integer entityId = Integer.valueOf(living.getEntityId());
            if (snapshot.size() >= LostTalesMobAggroSyncPacket.MAX_ENTITY_IDS
                    && !snapshot.containsKey(entityId)) {
                continue;
            }
            if (!makeRoomForStrongEvidence(state, entityId, snapshot)) {
                continue;
            }

            TrackedEngagement tracked = getOrCreateEntry(state, living.getEntityId());
            tracked.expiresAtTick = now + graceTicks;
            snapshot.put(entityId, isRecentDamage(tracked, now)
                    ? LostTalesCombatEngagement.ATTACKING
                    : direct);
        }

        Iterator<Map.Entry<Integer, TrackedEngagement>> iterator = state.entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TrackedEngagement> mapEntry = iterator.next();
            Integer entityId = mapEntry.getKey();
            TrackedEngagement tracked = mapEntry.getValue();

            if (snapshot.containsKey(entityId)) {
                continue;
            }

            Entity entity = player.worldObj.getEntityByID(entityId.intValue());
            if (!(entity instanceof EntityLivingBase)
                    || !isValidTrackedEntity((EntityLivingBase) entity, player, trackingRadiusSq)
                    || tracked.expiresAtTick <= now) {
                iterator.remove();
                continue;
            }

            if (snapshot.size() >= LostTalesMobAggroSyncPacket.MAX_ENTITY_IDS) {
                continue;
            }

            LostTalesCombatEngagement visibleState = isRecentDamage(tracked, now)
                    ? LostTalesCombatEngagement.ATTACKING
                    : LostTalesCombatEngagement.RECENTLY_ENGAGED;
            snapshot.put(entityId, visibleState);
        }

        state.currentSnapshot.clear();
        state.currentSnapshot.putAll(snapshot);
        mergeAuthorizedPartySnapshots(player, snapshot);
        List<LostTalesMobAggroSyncPacket.Entry> packetEntries =
                buildPacketEntries(player, snapshot, state.currentSnapshot);

        boolean heartbeatDue = state.initialSnapshotSent
                && !packetEntries.isEmpty()
                && now - state.lastSentTick >= SNAPSHOT_HEARTBEAT_TICKS;
        if (!state.initialSnapshotSent
                || trackingRadius != state.lastSentTrackingRadius
                || !packetEntries.equals(state.lastSentEntries)
                || heartbeatDue) {
            state.sequence++;
            state.lastSentEntries.clear();
            state.lastSentEntries.addAll(packetEntries);
            state.initialSnapshotSent = true;
            state.lastSentTrackingRadius = trackingRadius;
            state.lastSentTick = now;

            LostTalesNetworkHandler.CHANNEL.sendTo(
                    new LostTalesMobAggroSyncPacket(
                            dimensionId, state.sequence,
                            trackingRadius, packetEntries),
                    player);

            if (LostTalesConfig.combatMarkerDebugLogging) {
                FMLLog.info("[losttales] Combat marker snapshot for %s: dimension=%d sequence=%d entries=%d",
                        player.getCommandSenderName(), Integer.valueOf(dimensionId), Integer.valueOf(state.sequence), Integer.valueOf(packetEntries.size()));
            }
        }
    }

    private static void mergeAuthorizedPartySnapshots(
            EntityPlayerMP recipient,
            TreeMap<Integer, LostTalesCombatEngagement> snapshot) {
        if (!LostTalesConfig.partySharedAggroTracking || recipient == null
                || recipient.worldObj == null || snapshot == null
                || snapshot.size() >= LostTalesMobAggroSyncPacket.MAX_ENTITY_IDS) {
            return;
        }

        RoleplayCharacter activeCharacter = CharacterActiveResolver.get(recipient);
        if (activeCharacter == null) {
            return;
        }

        Party party;
        try {
            PartyWorldData data = PartyStorage.get(recipient.worldObj);
            party = data.getPartyForCharacter(activeCharacter.getCharacterId());
        } catch (RuntimeException ignored) {
            return;
        }
        if (party == null || party.getMemberCount() <= 1) {
            return;
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        List<?> onlinePlayers = server.getConfigurationManager().playerEntityList;
        for (PartyMember member : party.getMembers()) {
            if (member == null || member.getOwnerId().equals(recipient.getUniqueID())) {
                continue;
            }
            EntityPlayerMP sourcePlayer = findOnlinePlayer(onlinePlayers, member.getOwnerId());
            if (sourcePlayer == null || sourcePlayer.worldObj != recipient.worldObj
                    || sourcePlayer.isDead || !sourcePlayer.isEntityAlive()) {
                continue;
            }
            RoleplayCharacter sourceCharacter = CharacterActiveResolver.get(sourcePlayer);
            if (sourceCharacter == null
                    || !member.getCharacterId().equals(sourceCharacter.getCharacterId())) {
                continue;
            }
            PlayerCombatState sourceState = PLAYER_STATES.get(sourcePlayer.getUniqueID());
            if (sourceState == null || sourceState.dimensionId != recipient.dimension) {
                continue;
            }
            for (Map.Entry<Integer, LostTalesCombatEngagement> entry
                    : sourceState.currentSnapshot.entrySet()) {
                if (snapshot.size() >= LostTalesMobAggroSyncPacket.MAX_ENTITY_IDS) {
                    return;
                }
                Entity entity = recipient.worldObj.getEntityByID(
                        entry.getKey().intValue());
                if (!(entity instanceof EntityLivingBase)
                        || !isShareableTrackedEntity(
                        (EntityLivingBase) entity, recipient)) {
                    continue;
                }
                LostTalesCombatEngagement current = snapshot.get(entry.getKey());
                LostTalesCombatEngagement shared = entry.getValue();
                if (current == null || engagementPriority(shared) > engagementPriority(current)) {
                    snapshot.put(entry.getKey(), shared);
                }
            }
        }
    }

    private static List<LostTalesMobAggroSyncPacket.Entry> buildPacketEntries(
            EntityPlayerMP recipient,
            Map<Integer, LostTalesCombatEngagement> combined,
            Map<Integer, LostTalesCombatEngagement> direct) {
        ArrayList<LostTalesMobAggroSyncPacket.Entry> result =
                new ArrayList<LostTalesMobAggroSyncPacket.Entry>(
                        combined.size());
        for (Map.Entry<Integer, LostTalesCombatEngagement> entry
                : combined.entrySet()) {
            Entity entity = recipient.worldObj.getEntityByID(
                    entry.getKey().intValue());
            if (!(entity instanceof EntityLivingBase)
                    || !isShareableTrackedEntity(
                    (EntityLivingBase) entity, recipient)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            result.add(new LostTalesMobAggroSyncPacket.Entry(
                    living.getEntityId(), entry.getValue(),
                    !direct.containsKey(entry.getKey()),
                    boundedEntityName(living.getCommandSenderName()),
                    quantizePacketCoordinate(living.posX),
                    quantizePacketCoordinate(living.posY),
                    quantizePacketCoordinate(living.posZ)));
        }
        return result;
    }

    private static boolean isShareableTrackedEntity(
            EntityLivingBase living, EntityPlayerMP recipient) {
        return living != null && recipient != null
                && living != recipient && !(living instanceof EntityPlayer)
                && living.isEntityAlive() && !living.isDead
                && living.worldObj != null && recipient.worldObj != null
                && living.worldObj == recipient.worldObj
                && living.dimension == recipient.dimension;
    }

    private static double quantizePacketCoordinate(double value) {
        return Math.rint(value * 2.0D) / 2.0D;
    }

    private static String boundedEntityName(String value) {
        String name = value == null ? "Enemy" : value.trim();
        if (name.length() == 0) {
            name = "Enemy";
        }
        while (name.getBytes(StandardCharsets.UTF_8).length
                > LostTalesMobAggroSyncPacket.MAX_ENTITY_NAME_BYTES
                && name.length() > 1) {
            name = name.substring(0, name.length() - 1);
        }
        return name;
    }

    private static int engagementPriority(LostTalesCombatEngagement engagement) {
        if (engagement == LostTalesCombatEngagement.ATTACKING) {
            return 3;
        }
        if (engagement == LostTalesCombatEngagement.TARGETING) {
            return 2;
        }
        if (engagement == LostTalesCombatEngagement.RECENTLY_ENGAGED) {
            return 1;
        }
        return 0;
    }

    private static EntityPlayerMP findOnlinePlayer(List<?> onlinePlayers, UUID accountId) {
        if (onlinePlayers == null || accountId == null) {
            return null;
        }
        for (Object object : onlinePlayers) {
            if (object instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) object;
                if (accountId.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }

    private static EntityLivingBase resolveLivingAttacker(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity responsible = source.getEntity();
        if (responsible instanceof EntityLivingBase) {
            return (EntityLivingBase) responsible;
        }
        Entity direct = source.getSourceOfDamage();
        return direct instanceof EntityLivingBase ? (EntityLivingBase) direct : null;
    }

    private static boolean isValidTrackedEntity(EntityLivingBase living, EntityPlayer player, double trackingRadiusSq) {
        if (living == null || player == null || living == player || living instanceof EntityPlayer
                || !living.isEntityAlive() || living.isDead
                || living.worldObj == null || player.worldObj == null || living.worldObj != player.worldObj
                || living.dimension != player.dimension) {
            return false;
        }
        return living.getDistanceSqToEntity(player) <= trackingRadiusSq;
    }

    private static int getTrackingRadius() {
        return Math.max(8, Math.min(128, LostTalesConfig.combatMarkerTrackingRadius));
    }

    private static double getTrackingRadiusSq() {
        int radius = getTrackingRadius();
        return (double) radius * (double) radius;
    }

    private static PlayerCombatState getOrCreateState(EntityPlayer player) {
        UUID playerId = player.getUniqueID();
        PlayerCombatState state = PLAYER_STATES.get(playerId);
        int dimensionId = player.worldObj == null ? Integer.MIN_VALUE : player.worldObj.provider.dimensionId;
        if (state == null) {
            state = new PlayerCombatState(dimensionId);
            PLAYER_STATES.put(playerId, state);
        } else if (state.dimensionId != dimensionId) {
            state.resetForDimension(dimensionId);
        }
        return state;
    }

    private static TrackedEngagement getOrCreateEntry(PlayerCombatState state, int entityId) {
        Integer key = Integer.valueOf(entityId);
        TrackedEngagement tracked = state.entries.get(key);
        if (tracked == null) {
            tracked = new TrackedEngagement();
            state.entries.put(key, tracked);
        }
        return tracked;
    }

    private static void pruneInvalidOrExpiredEntries(PlayerCombatState state, EntityPlayerMP player,
                                                      double trackingRadiusSq, long now) {
        Iterator<Map.Entry<Integer, TrackedEngagement>> iterator = state.entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TrackedEngagement> entry = iterator.next();
            Entity entity = player.worldObj.getEntityByID(entry.getKey().intValue());
            if (!(entity instanceof EntityLivingBase)
                    || !isValidTrackedEntity((EntityLivingBase) entity, player, trackingRadiusSq)
                    || entry.getValue().expiresAtTick <= now) {
                iterator.remove();
            }
        }
    }

    /** Current targeting or attack evidence takes precedence over older grace entries at the hard bound. */
    private static boolean makeRoomForStrongEvidence(PlayerCombatState state, Integer entityId,
                                                     Map<Integer, LostTalesCombatEngagement> protectedEntries) {
        if (state.entries.containsKey(entityId) || state.entries.size() < LostTalesMobAggroSyncPacket.MAX_ENTITY_IDS) {
            return true;
        }

        Integer evictionId = null;
        long earliestExpiry = Long.MAX_VALUE;
        for (Map.Entry<Integer, TrackedEngagement> entry : state.entries.entrySet()) {
            if (protectedEntries != null && protectedEntries.containsKey(entry.getKey())) {
                continue;
            }
            if (entry.getValue().expiresAtTick < earliestExpiry) {
                earliestExpiry = entry.getValue().expiresAtTick;
                evictionId = entry.getKey();
            }
        }
        if (evictionId == null) {
            return false;
        }
        state.entries.remove(evictionId);
        return true;
    }

    private static boolean isRecentDamage(TrackedEngagement tracked, long now) {
        if (tracked == null || tracked.lastDamageTick == Long.MIN_VALUE) {
            return false;
        }
        long elapsed = now - tracked.lastDamageTick;
        return elapsed >= 0L && elapsed <= Math.max(1, LostTalesConfig.combatMarkerUpdateIntervalTicks);
    }

    private static synchronized void clearTrackedEnemies(EntityPlayerMP player, boolean sendEmptySnapshot) {
        if (player == null) {
            return;
        }
        PlayerCombatState state = PLAYER_STATES.get(player.getUniqueID());
        if (state == null) {
            return;
        }

        state.entries.clear();
        state.currentSnapshot.clear();
        if (sendEmptySnapshot && player.worldObj != null
                && state.initialSnapshotSent
                && !state.lastSentEntries.isEmpty()) {
            int dimensionId = player.worldObj.provider.dimensionId;
            int trackingRadius = getTrackingRadius();
            long now = player.worldObj.getTotalWorldTime();
            state.sequence++;
            state.dimensionId = dimensionId;
            state.lastSentEntries.clear();
            state.lastSentTrackingRadius = trackingRadius;
            state.lastSentTick = now;
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    new LostTalesMobAggroSyncPacket(
                            dimensionId,
                            state.sequence,
                            trackingRadius,
                            new ArrayList<LostTalesMobAggroSyncPacket.Entry>(0)
                    ),
                    player
            );
        }
    }

    private static synchronized void resetPlayer(EntityPlayer player) {
        if (player == null) {
            return;
        }
        PlayerCombatState state = PLAYER_STATES.get(player.getUniqueID());
        if (state != null) {
            int dimensionId = player.worldObj == null ? Integer.MIN_VALUE : player.worldObj.provider.dimensionId;
            state.resetForDimension(dimensionId);
        }
    }

    private static synchronized void removePlayer(EntityPlayer player) {
        if (player != null) {
            PLAYER_STATES.remove(player.getUniqueID());
        }
    }

    private static final class PlayerCombatState {
        private int dimensionId;
        private int sequence;
        private boolean initialSnapshotSent;
        private int lastSentTrackingRadius = -1;
        private long lastSentTick;
        private final Map<Integer, TrackedEngagement> entries = new HashMap<Integer, TrackedEngagement>();
        private final Map<Integer, LostTalesCombatEngagement> currentSnapshot = new TreeMap<Integer, LostTalesCombatEngagement>();
        private final List<LostTalesMobAggroSyncPacket.Entry> lastSentEntries =
                new ArrayList<LostTalesMobAggroSyncPacket.Entry>();

        private PlayerCombatState(int dimensionId) {
            this.dimensionId = dimensionId;
        }

        private void resetForDimension(int dimensionId) {
            this.dimensionId = dimensionId;
            this.entries.clear();
            this.currentSnapshot.clear();
            this.lastSentEntries.clear();
            this.initialSnapshotSent = false;
            this.lastSentTrackingRadius = -1;
            this.lastSentTick = 0L;
        }
    }

    private static final class TrackedEngagement {
        private long lastDamageTick = Long.MIN_VALUE;
        private long expiresAtTick;
    }
}
