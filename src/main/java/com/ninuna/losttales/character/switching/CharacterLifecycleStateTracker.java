package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.event.LostTalesMobAggroEventHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-only lifecycle and movement evidence used by the switching policy.
 * Every respawn, dimension transfer, and login advances the request epoch so
 * queued packets from an older entity/lifecycle cannot mutate the new one.
 */
public final class CharacterLifecycleStateTracker {

    private static final ConcurrentMap<UUID, SessionState> STATES =
            new ConcurrentHashMap<UUID, SessionState>();
    private static final ConcurrentMap<UUID, Long> DISCONNECTED_COMBAT =
            new ConcurrentHashMap<UUID, Long>();
    private static final AtomicLong NEXT_EPOCH = new AtomicLong(1L);
    private static volatile boolean serverStopping;
    private static volatile long lastDisconnectedCombatPruneAt;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event != null && event.player instanceof EntityPlayerMP) {
            beginSession((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event != null && event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            if (LostTalesMobAggroEventHandler.hasTrackedCombat(player)) {
                recordCombat(player, System.currentTimeMillis());
            }
            endSession(player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event != null && event.entityPlayer instanceof EntityPlayerMP) {
            beginTransition((EntityPlayerMP) event.entityPlayer, true);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event != null && event.player instanceof EntityPlayerMP) {
            beginTransition((EntityPlayerMP) event.player, true);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        if (event != null && event.player instanceof EntityPlayerMP) {
            beginTransition((EntityPlayerMP) event.player, false);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.side != Side.SERVER || event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        updateMovement((EntityPlayerMP) event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (event == null || event.isCanceled() || event.ammount <= 0.0F) {
            return;
        }
        long now = System.currentTimeMillis();
        if (event.entityLiving instanceof EntityPlayerMP) {
            recordCombat((EntityPlayerMP) event.entityLiving, now);
        }
        DamageSource source = event.source;
        Entity attacker = source == null ? null : source.getEntity();
        if (attacker instanceof EntityPlayerMP) {
            recordCombat((EntityPlayerMP) attacker, now);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAttackEntity(AttackEntityEvent event) {
        if (event != null && !event.isCanceled() && event.entityPlayer instanceof EntityPlayerMP) {
            recordCombat((EntityPlayerMP) event.entityPlayer, System.currentTimeMillis());
        }
    }

    public static void beginSession(EntityPlayerMP player) {
        if (!isServerPlayer(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        pruneDisconnectedCombat(now);
        SessionState previous = STATES.get(player.getUniqueID());
        SessionState state = new SessionState(nextEpoch());
        if (previous != null) {
            synchronized (previous) {
                state.lastCombatAt = previous.lastCombatAt;
            }
        }
        Long disconnectedCombatAt = DISCONNECTED_COMBAT.remove(player.getUniqueID());
        if (disconnectedCombatAt != null
                && isCombatTimestampActive(disconnectedCombatAt.longValue(), now)) {
            state.lastCombatAt = Math.max(
                    state.lastCombatAt, disconnectedCombatAt.longValue());
        }
        state.dimensionId = player.dimension;
        state.lastX = player.posX;
        state.lastY = player.posY;
        state.lastZ = player.posZ;
        state.hasPosition = true;
        STATES.put(player.getUniqueID(), state);
    }

    public static void markReady(EntityPlayerMP player) {
        SessionState state = getState(player);
        if (state == null) {
            beginSession(player);
            state = getState(player);
        }
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.ready = true;
            state.loggingOut = false;
            state.respawning = false;
            state.dimensionChanging = false;
            state.dimensionId = player.dimension;
            state.lastX = player.posX;
            state.lastY = player.posY;
            state.lastZ = player.posZ;
            state.hasPosition = true;
        }
    }

    public static void endSession(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        SessionState state = STATES.remove(ownerId);
        long lastCombatAt = 0L;
        if (state != null) {
            synchronized (state) {
                state.loggingOut = true;
                state.ready = false;
                state.switching = false;
                state.epoch = nextEpoch();
                lastCombatAt = state.lastCombatAt;
            }
        }
        long now = System.currentTimeMillis();
        if (isCombatTimestampActive(lastCombatAt, now)) {
            DISCONNECTED_COMBAT.put(ownerId, Long.valueOf(lastCombatAt));
        } else {
            DISCONNECTED_COMBAT.remove(ownerId);
        }
        pruneDisconnectedCombat(now);
    }

    public static long captureRequestEpoch(EntityPlayerMP player) {
        SessionState state = getState(player);
        if (state == null) {
            return -1L;
        }
        synchronized (state) {
            return state.epoch;
        }
    }

    public static boolean isRequestEpochCurrent(EntityPlayerMP player, long epoch) {
        SessionState state = getState(player);
        if (state == null || epoch <= 0L) {
            return false;
        }
        synchronized (state) {
            return state.epoch == epoch && state.ready && !state.loggingOut
                    && !state.respawning && !state.dimensionChanging && !serverStopping;
        }
    }

    public static boolean beginSwitch(EntityPlayerMP player) {
        SessionState state = getState(player);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (!state.ready || state.loggingOut || state.respawning
                    || state.dimensionChanging || state.switching || serverStopping) {
                return false;
            }
            state.switching = true;
            return true;
        }
    }

    public static void endSwitch(EntityPlayerMP player) {
        SessionState state = getState(player);
        if (state != null) {
            synchronized (state) {
                state.switching = false;
            }
        }
    }

    public static Snapshot snapshot(EntityPlayerMP player) {
        SessionState state = getState(player);
        if (state == null) {
            return Snapshot.missing(serverStopping);
        }
        synchronized (state) {
            return new Snapshot(
                    true,
                    state.ready,
                    state.loggingOut,
                    state.respawning,
                    state.dimensionChanging,
                    state.switching,
                    serverStopping,
                    state.epoch,
                    state.lastCombatAt,
                    state.transitionUntil,
                    state.stableGroundTicks);
        }
    }

    public static void markServerStarting() {
        serverStopping = false;
        STATES.clear();
        DISCONNECTED_COMBAT.clear();
        lastDisconnectedCombatPruneAt = 0L;
    }

    public static void markServerStopping() {
        serverStopping = true;
        STATES.clear();
        DISCONNECTED_COMBAT.clear();
        lastDisconnectedCombatPruneAt = 0L;
    }

    public static void clearAll() {
        STATES.clear();
        DISCONNECTED_COMBAT.clear();
        lastDisconnectedCombatPruneAt = 0L;
    }

    private static void beginTransition(EntityPlayerMP player, boolean respawning) {
        if (!isServerPlayer(player)) {
            return;
        }
        SessionState state = getState(player);
        if (state == null) {
            state = new SessionState(nextEpoch());
            STATES.put(player.getUniqueID(), state);
        }
        long now = System.currentTimeMillis();
        pruneDisconnectedCombat(now);
        synchronized (state) {
            state.epoch = nextEpoch();
            state.ready = false;
            state.respawning = respawning;
            state.dimensionChanging = !respawning;
            state.switching = false;
            state.stableGroundTicks = 0;
            state.transitionUntil = safeAdd(now,
                    Math.max(0L, LostTalesConfig.characterSwitchTeleportGraceMillis));
            state.dimensionId = player.dimension;
            state.lastX = player.posX;
            state.lastY = player.posY;
            state.lastZ = player.posZ;
            state.hasPosition = true;
        }
    }

    private static void updateMovement(EntityPlayerMP player) {
        SessionState state = getState(player);
        if (state == null || player.worldObj == null) {
            return;
        }
        long now = System.currentTimeMillis();
        pruneDisconnectedCombat(now);
        synchronized (state) {
            if (state.hasPosition) {
                double dx = player.posX - state.lastX;
                double dy = player.posY - state.lastY;
                double dz = player.posZ - state.lastZ;
                double distanceSq = dx * dx + dy * dy + dz * dz;
                double threshold = Math.max(1.0D,
                        LostTalesConfig.characterSwitchTeleportDistancePerTick);
                if (player.dimension != state.dimensionId
                        || distanceSq > threshold * threshold) {
                    state.transitionUntil = Math.max(state.transitionUntil,
                            safeAdd(now, Math.max(0L,
                                    LostTalesConfig.characterSwitchTeleportGraceMillis)));
                    state.stableGroundTicks = 0;
                }
            }
            state.dimensionId = player.dimension;
            state.lastX = player.posX;
            state.lastY = player.posY;
            state.lastZ = player.posZ;
            state.hasPosition = true;

            boolean stable = player.onGround
                    && player.ridingEntity == null
                    && player.riddenByEntity == null
                    && !player.isInWater()
                    && !player.handleLavaMovement()
                    && Math.abs(player.motionY) <= 0.08D
                    && player.fallDistance <= 0.0F;
            if (stable) {
                if (state.stableGroundTicks < Integer.MAX_VALUE) {
                    state.stableGroundTicks++;
                }
            } else {
                state.stableGroundTicks = 0;
            }
        }
    }

    private static void recordCombat(EntityPlayerMP player, long now) {
        SessionState state = getState(player);
        if (state != null) {
            synchronized (state) {
                state.lastCombatAt = Math.max(state.lastCombatAt, now);
            }
        }
    }

    private static void pruneDisconnectedCombat(long now) {
        long previousPrune = lastDisconnectedCombatPruneAt;
        if (previousPrune > 0L && now >= previousPrune
                && now - previousPrune < 5000L) {
            return;
        }
        lastDisconnectedCombatPruneAt = now;
        for (Map.Entry<UUID, Long> entry : DISCONNECTED_COMBAT.entrySet()) {
            Long timestamp = entry.getValue();
            if (timestamp == null) {
                DISCONNECTED_COMBAT.remove(entry.getKey());
            } else if (!isCombatTimestampActive(timestamp.longValue(), now)) {
                DISCONNECTED_COMBAT.remove(entry.getKey(), timestamp);
            }
        }
    }

    private static boolean isCombatTimestampActive(long timestamp, long now) {
        long grace = Math.max(0L, LostTalesConfig.characterSwitchCombatGraceMillis);
        return timestamp > 0L && grace > 0L
                && (now < timestamp || now - timestamp < grace);
    }

    private static SessionState getState(EntityPlayerMP player) {
        if (!isServerPlayer(player)) {
            return null;
        }
        return STATES.get(player.getUniqueID());
    }

    private static boolean isServerPlayer(EntityPlayerMP player) {
        return player != null && player.getUniqueID() != null
                && player.worldObj != null && !player.worldObj.isRemote;
    }

    private static long nextEpoch() {
        long value = NEXT_EPOCH.getAndIncrement();
        if (value <= 0L) {
            NEXT_EPOCH.compareAndSet(value + 1L, 1L);
            return NEXT_EPOCH.getAndIncrement();
        }
        return value;
    }

    private static long safeAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }

    private static final class SessionState {
        private long epoch;
        private boolean ready;
        private boolean loggingOut;
        private boolean respawning;
        private boolean dimensionChanging;
        private boolean switching;
        private long lastCombatAt;
        private long transitionUntil;
        private int stableGroundTicks;
        private int dimensionId;
        private double lastX;
        private double lastY;
        private double lastZ;
        private boolean hasPosition;

        private SessionState(long epoch) {
            this.epoch = epoch;
        }
    }

    public static final class Snapshot {
        private final boolean present;
        private final boolean ready;
        private final boolean loggingOut;
        private final boolean respawning;
        private final boolean dimensionChanging;
        private final boolean switching;
        private final boolean serverStopping;
        private final long requestEpoch;
        private final long lastCombatAt;
        private final long transitionUntil;
        private final int stableGroundTicks;

        private Snapshot(boolean present, boolean ready, boolean loggingOut,
                         boolean respawning, boolean dimensionChanging,
                         boolean switching, boolean serverStopping,
                         long requestEpoch, long lastCombatAt,
                         long transitionUntil, int stableGroundTicks) {
            this.present = present;
            this.ready = ready;
            this.loggingOut = loggingOut;
            this.respawning = respawning;
            this.dimensionChanging = dimensionChanging;
            this.switching = switching;
            this.serverStopping = serverStopping;
            this.requestEpoch = requestEpoch;
            this.lastCombatAt = lastCombatAt;
            this.transitionUntil = transitionUntil;
            this.stableGroundTicks = stableGroundTicks;
        }

        private static Snapshot missing(boolean serverStopping) {
            return new Snapshot(false, false, false, false, false,
                    false, serverStopping, -1L, 0L, 0L, 0);
        }

        public boolean isPresent() { return this.present; }
        public boolean isReady() { return this.ready; }
        public boolean isLoggingOut() { return this.loggingOut; }
        public boolean isRespawning() { return this.respawning; }
        public boolean isDimensionChanging() { return this.dimensionChanging; }
        public boolean isSwitching() { return this.switching; }
        public boolean isServerStopping() { return this.serverStopping; }
        public long getRequestEpoch() { return this.requestEpoch; }
        public long getLastCombatAt() { return this.lastCombatAt; }
        public long getTransitionUntil() { return this.transitionUntil; }
        public int getStableGroundTicks() { return this.stableGroundTicks; }
    }
}
