package com.ninuna.losttales.client.cache;

import com.ninuna.losttales.entity.combat.LostTalesCombatEngagement;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Client-side replacement snapshot of entities the server approved for combat markers.
 */
@SideOnly(Side.CLIENT)
public final class LostTalesClientMobAggroCache {
    private static final int STALE_SNAPSHOT_TICKS = 240;
    private static volatile List<TrackedEnemy> trackedEnemies = Collections.emptyList();
    private static volatile int dimensionId = Integer.MIN_VALUE;
    private static volatile int sequence = -1;
    private static volatile int serverTrackingRadius;
    private static long lastSnapshotWorldTick = Long.MIN_VALUE;
    private static World contextWorld;
    private static EntityPlayer contextPlayer;
    private static int contextPlayerEntityId = Integer.MIN_VALUE;

    private LostTalesClientMobAggroCache() {}

    public static synchronized void accept(LostTalesMobAggroSyncPacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null || minecraft.thePlayer == null) {
            clearInternal();
            return;
        }

        int currentDimension = minecraft.theWorld.provider.dimensionId;
        if (packet.getDimensionId() != currentDimension) {
            return;
        }

        int currentPlayerEntityId = minecraft.thePlayer.getEntityId();
        if (contextWorld != null && (contextWorld != minecraft.theWorld
                || contextPlayer != minecraft.thePlayer
                || contextPlayerEntityId != currentPlayerEntityId
                || dimensionId != Integer.MIN_VALUE && dimensionId != currentDimension)) {
            clearInternal();
        }
        if (dimensionId == currentDimension && packet.getSequence() <= sequence) {
            return;
        }

        contextWorld = minecraft.theWorld;
        contextPlayer = minecraft.thePlayer;
        contextPlayerEntityId = currentPlayerEntityId;
        dimensionId = currentDimension;
        sequence = packet.getSequence();
        serverTrackingRadius = packet.getTrackingRadius();
        lastSnapshotWorldTick = minecraft.theWorld.getTotalWorldTime();

        List<LostTalesMobAggroSyncPacket.Entry> packetEntries = packet.getEntries();
        List<TrackedEnemy> replacement = new ArrayList<TrackedEnemy>(packetEntries.size());
        for (LostTalesMobAggroSyncPacket.Entry entry : packetEntries) {
            if (entry != null && entry.getEntityId() >= 0
                    && entry.getEngagement() != LostTalesCombatEngagement.NONE) {
                replacement.add(new TrackedEnemy(entry.getEntityId(), entry.getEngagement()));
            }
        }
        trackedEnemies = replacement.isEmpty()
                ? Collections.<TrackedEnemy>emptyList()
                : Collections.unmodifiableList(replacement);
    }

    /** Clears stale state when the local world, dimension, or player entity changes. */
    public static synchronized void validateContext(EntityPlayer player) {
        if (player == null || player.worldObj == null) {
            clearInternal();
            return;
        }
        if (contextWorld != null && (contextWorld != player.worldObj
                || contextPlayer != player
                || contextPlayerEntityId != player.getEntityId()
                || dimensionId != Integer.MIN_VALUE && dimensionId != player.worldObj.provider.dimensionId)) {
            clearInternal();
        }
        if (!trackedEnemies.isEmpty() && lastSnapshotWorldTick != Long.MIN_VALUE) {
            long elapsed = player.worldObj.getTotalWorldTime() - lastSnapshotWorldTick;
            if (elapsed < 0L || elapsed > STALE_SNAPSHOT_TICKS) {
                clearInternal();
            }
        }
        contextWorld = player.worldObj;
        contextPlayer = player;
        contextPlayerEntityId = player.getEntityId();
    }

    public static List<TrackedEnemy> getTrackedEnemies() {
        return trackedEnemies;
    }

    public static int getServerTrackingRadius() {
        return serverTrackingRadius;
    }

    public static boolean isAggro(int entityId) {
        for (TrackedEnemy trackedEnemy : trackedEnemies) {
            if (trackedEnemy.getEntityId() == entityId) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void clear() {
        clearInternal();
    }

    private static void clearInternal() {
        trackedEnemies = Collections.emptyList();
        dimensionId = Integer.MIN_VALUE;
        sequence = -1;
        serverTrackingRadius = 0;
        lastSnapshotWorldTick = Long.MIN_VALUE;
        contextWorld = null;
        contextPlayer = null;
        contextPlayerEntityId = Integer.MIN_VALUE;
    }

    public static final class TrackedEnemy {
        private final int entityId;
        private final LostTalesCombatEngagement engagement;

        private TrackedEnemy(int entityId, LostTalesCombatEngagement engagement) {
            this.entityId = entityId;
            this.engagement = engagement;
        }

        public int getEntityId() {
            return this.entityId;
        }

        public LostTalesCombatEngagement getEngagement() {
            return this.engagement;
        }
    }
}
