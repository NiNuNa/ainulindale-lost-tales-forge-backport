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
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * Client replacement snapshot of server-approved combat marker entities,
 * each named in this game's language from the kind the server sent
 * (MAP-16).
 */
@SideOnly(Side.CLIENT)
public final class LostTalesClientMobAggroCache {
    /** The word for an enemy whose kind this game has no name for. */
    public static final String ENEMY_KEY = "gui.losttales.marker.enemy";

    /** The lang lines a contact's name is read from. */
    interface Words {
        boolean has(String key);

        String read(String key);
    }

    /** This game's own lang lines, in the language it is set to. */
    private static final Words GAME_WORDS = new Words() {
        @Override
        public boolean has(String key) {
            return StatCollector.canTranslate(key);
        }

        @Override
        public String read(String key) {
            return StatCollector.translateToLocal(key);
        }
    };
    private static final int STALE_SNAPSHOT_TICKS = 240;
    private static volatile List<TrackedEnemy> trackedEnemies =
            Collections.emptyList();
    private static volatile int dimensionId = Integer.MIN_VALUE;
    private static volatile int sequence = -1;
    private static volatile int serverTrackingRadius;
    private static long lastSnapshotWorldTick = Long.MIN_VALUE;
    private static World contextWorld;
    private static EntityPlayer contextPlayer;
    private static int contextPlayerEntityId = Integer.MIN_VALUE;

    private LostTalesClientMobAggroCache() {}

    public static synchronized void accept(
            LostTalesMobAggroSyncPacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null
                || minecraft.thePlayer == null) {
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
                || dimensionId != Integer.MIN_VALUE
                && dimensionId != currentDimension)) {
            clearInternal();
        }
        if (dimensionId == currentDimension
                && packet.getSequence() <= sequence) {
            return;
        }

        contextWorld = minecraft.theWorld;
        contextPlayer = minecraft.thePlayer;
        contextPlayerEntityId = currentPlayerEntityId;
        dimensionId = currentDimension;
        sequence = packet.getSequence();
        serverTrackingRadius = packet.getTrackingRadius();
        lastSnapshotWorldTick = minecraft.theWorld.getTotalWorldTime();

        List<LostTalesMobAggroSyncPacket.Entry> packetEntries =
                packet.getEntries();
        List<TrackedEnemy> replacement =
                new ArrayList<TrackedEnemy>(packetEntries.size());
        for (LostTalesMobAggroSyncPacket.Entry entry : packetEntries) {
            if (entry != null && entry.getEntityId() >= 0
                    && entry.getEngagement()
                    != LostTalesCombatEngagement.NONE) {
                replacement.add(new TrackedEnemy(entry));
            }
        }
        trackedEnemies = replacement.isEmpty()
                ? Collections.<TrackedEnemy>emptyList()
                : Collections.unmodifiableList(replacement);
    }

    public static synchronized void validateContext(EntityPlayer player) {
        if (player == null || player.worldObj == null) {
            clearInternal();
            return;
        }
        if (contextWorld != null && (contextWorld != player.worldObj
                || contextPlayer != player
                || contextPlayerEntityId != player.getEntityId()
                || dimensionId != Integer.MIN_VALUE
                && dimensionId != player.worldObj.provider.dimensionId)) {
            clearInternal();
        }
        if (!trackedEnemies.isEmpty()
                && lastSnapshotWorldTick != Long.MIN_VALUE) {
            long elapsed = player.worldObj.getTotalWorldTime()
                    - lastSnapshotWorldTick;
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

    /** What an enemy with no name of its own is called, in the game's language. */
    public static String enemyWord() {
        return StatCollector.translateToLocal(ENEMY_KEY);
    }

    /** The lang key a creature's kind is named by, as the game names every creature. */
    static String kindKey(String kind) {
        return "entity." + kind + ".name";
    }

    /**
     * What a contact is called: the name a player gave it, as written;
     * else its kind's name in {@code words}; else the word for an enemy.
     */
    static String nameOf(String kind, String ownName, Words words) {
        if (ownName != null && ownName.trim().length() > 0) {
            return ownName.trim();
        }
        if (kind != null && kind.length() > 0 && words.has(kindKey(kind))) {
            return words.read(kindKey(kind));
        }
        return words.read(ENEMY_KEY);
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
        private final boolean sharedFromFellowship;
        private final String name;
        private final double x;
        private final double y;
        private final double z;

        private TrackedEnemy(LostTalesMobAggroSyncPacket.Entry entry) {
            this.entityId = entry.getEntityId();
            this.sharedFromFellowship = entry.isSharedFromFellowship();
            this.name = nameOf(entry.getKind(), entry.getOwnName(), GAME_WORDS);
            this.x = entry.getX();
            this.y = entry.getY();
            this.z = entry.getZ();
        }

        public int getEntityId() {
            return this.entityId;
        }

        public boolean isSharedFromFellowship() {
            return this.sharedFromFellowship;
        }

        /** What the contact is called in this game's language ({@link #nameOf}). */
        public String getName() {
            return this.name;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}
