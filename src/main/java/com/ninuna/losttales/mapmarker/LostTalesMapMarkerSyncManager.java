package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Sends each player the markers they may see, as one snapshot.
 *
 * <p>A change only marks who needs a fresh snapshot; every
 * {@link #FLUSH_TICKS} the marked players are sent one each, so however
 * many changes come in, a player is sent at most one snapshot a second.
 * A player who logs in, respawns or changes world is sent theirs at once.
 * Static state, cleared as the server starts and stops.</p>
 */
public final class LostTalesMapMarkerSyncManager {
    /** The ticks between two sends of the marked players' snapshots. */
    static final int FLUSH_TICKS = 20;

    private static final Set<UUID> PENDING = new LinkedHashSet<UUID>();
    private static int ticks;

    /** Sends the player their snapshot now. */
    public static void sync(EntityPlayerMP player) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        synchronized (LostTalesMapMarkerSyncManager.class) {
            PENDING.remove(player.getUniqueID());
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(player.worldObj);
        ArrayList<LostTalesMapMarkerDefinition> visible =
                new ArrayList<LostTalesMapMarkerDefinition>();
        for (LostTalesMapMarkerRecord record : data.getRecords()) {
            if (LostTalesMapMarkerVisibilityPolicy.canView(
                    record, player)) {
                visible.add(record.toDefinition());
            }
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesMapMarkerSnapshotPacket(visible), player);
    }

    /** Every online player is sent a fresh snapshot within a second. */
    public static void syncAll() {
        for (EntityPlayerMP player : onlinePlayers()) {
            mark(player);
        }
    }

    /**
     * The players who could see the marker before the change or can see it
     * after are sent a fresh snapshot within a second; nobody else is.
     */
    public static void syncViewersOf(
            LostTalesMapMarkerRecord before, LostTalesMapMarkerRecord after) {
        for (EntityPlayerMP player : onlinePlayers()) {
            if ((before != null && LostTalesMapMarkerVisibilityPolicy.canView(before, player))
                    || (after != null && LostTalesMapMarkerVisibilityPolicy.canView(after, player))) {
                mark(player);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < FLUSH_TICKS) {
            return;
        }
        ticks = 0;
        List<UUID> due;
        synchronized (LostTalesMapMarkerSyncManager.class) {
            if (PENDING.isEmpty()) {
                return;
            }
            due = new ArrayList<UUID>(PENDING);
            PENDING.clear();
        }
        for (EntityPlayerMP player : onlinePlayers()) {
            if (due.contains(player.getUniqueID())) {
                sync(player);
            }
        }
    }

    public static synchronized void clear() {
        PENDING.clear();
        ticks = 0;
    }

    private static synchronized void mark(EntityPlayerMP player) {
        if (player != null && player.getUniqueID() != null) {
            PENDING.add(player.getUniqueID());
        }
    }

    private static List<EntityPlayerMP> onlinePlayers() {
        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return players;
        }
        Collection<?> online = server.getConfigurationManager().playerEntityList;
        if (online == null) {
            return players;
        }
        for (Object value : online) {
            if (value instanceof EntityPlayerMP) {
                players.add((EntityPlayerMP) value);
            }
        }
        return players;
    }
}
