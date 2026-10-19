package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Sends each player the markers they may see, as one snapshot; a marker
 * hidden until discovered only once the character played discovered it
 * ({@link #select}).
 *
 * <p>A change only marks who needs a fresh snapshot; every
 * {@link #FLUSH_TICKS} the marked players are sent one each, so however
 * many changes come in, a player is sent at most one snapshot a second.
 * A player who logs in, respawns, changes world or plays another
 * character is sent theirs at once. Static state, cleared as the server
 * starts and stops.</p>
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
        final LostTalesQuestPlayerData quests =
                LostTalesQuestPlayerData.get(player);
        List<LostTalesMapMarkerDefinition> visible = select(
                data.getRecords(),
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(player),
                new Discoveries() {
                    @Override
                    public boolean has(String markerId) {
                        return quests != null
                                && quests.isMarkerDiscovered(markerId);
                    }
                });
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesMapMarkerSnapshotPacket(visible), player);
    }

    /**
     * What a snapshot holds for {@code viewer}: every marker it may see,
     * but a marker hidden until discovered only once the character played
     * has discovered it (WAY-11). A manager is sent every marker it may
     * see, discovered or not.
     */
    static List<LostTalesMapMarkerDefinition> select(
            Collection<LostTalesMapMarkerRecord> records,
            LostTalesMapMarkerVisibilityPolicy.Viewer viewer,
            Discoveries discovered) {
        ArrayList<LostTalesMapMarkerDefinition> visible =
                new ArrayList<LostTalesMapMarkerDefinition>();
        if (records == null || viewer == null) {
            return visible;
        }
        for (LostTalesMapMarkerRecord record : records) {
            if (!LostTalesMapMarkerVisibilityPolicy.canView(record, viewer)) {
                continue;
            }
            if (record.isHiddenUntilDiscovered() && !viewer.isManager()
                    && (discovered == null
                        || !discovered.has(record.getId()))) {
                continue;
            }
            visible.add(record.toDefinition());
        }
        return visible;
    }

    /**
     * The character played found a marker, or forgot one: where it is
     * hidden until discovered, the player is sent a fresh snapshot within
     * a second, which now holds it, or no longer does.
     */
    public static void discoveryChanged(EntityPlayer player,
                                        String markerId) {
        if (!(player instanceof EntityPlayerMP) || player.worldObj == null
                || player.worldObj.isRemote || markerId == null) {
            return;
        }
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerStorage.get(player.worldObj)
                        .getRecord(markerId.trim());
        if (record != null && record.isHiddenUntilDiscovered()) {
            mark((EntityPlayerMP)player);
        }
    }

    /** The markers the character played has discovered, as a snapshot asks. */
    interface Discoveries {
        boolean has(String markerId);
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
            LostTalesMapMarkerVisibilityPolicy.Viewer viewer =
                    LostTalesMapMarkerVisibilityPolicy.Viewer.of(player);
            if (LostTalesMapMarkerVisibilityPolicy.canView(before, viewer)
                    || LostTalesMapMarkerVisibilityPolicy.canView(after, viewer)) {
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
