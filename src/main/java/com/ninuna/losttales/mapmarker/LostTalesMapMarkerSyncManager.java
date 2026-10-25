package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import com.ninuna.losttales.quest.LostTalesQuestManager;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/**
 * Sends each player the markers they may see, as one snapshot; a marker
 * hidden until discovered only once the character played discovered it
 * ({@link #select}). An unnamed waystone goes called after its placing
 * character by the name it has now (WAY-01), so a rename or a purge sends
 * its viewers their markers again ({@link #placerChanged}).
 *
 * <p>A change only marks who needs a fresh snapshot; every
 * {@link #FLUSH_TICKS} the marked players are sent one each, so however
 * many changes come in, a player is sent at most one snapshot a second.
 * A player who logs in, respawns, changes world or plays another
 * character is sent theirs at once. With each snapshot the server checks
 * that a waystone page the player has open shows a waystone they may
 * still see.</p>
 *
 * <p>A snapshot goes in parts of up to 2,000 markers (WAY-12), each
 * numbered with the snapshot's sequence. The parts wait in an outbox and
 * go out at the end of each tick, round the players, until
 * {@link #BYTES_PER_TICK} are sent; a newer snapshot takes the place of
 * the parts of an older one still waiting. Static state, cleared as the
 * server starts and stops.</p>
 */
public final class LostTalesMapMarkerSyncManager {
    /** The ticks between two sends of the marked players' snapshots. */
    static final int FLUSH_TICKS = 20;
    /**
     * How many bytes of parts go out in one tick, to every player
     * together: a megabyte, some 3,500 markers of the usual size. One
     * part always goes.
     */
    static final int BYTES_PER_TICK = 1024 * 1024;

    private static final Set<UUID> PENDING = new LinkedHashSet<UUID>();
    private static final LostTalesMapMarkerSnapshotOutbox OUTBOX =
            new LostTalesMapMarkerSnapshotOutbox();
    private static int ticks;
    private static int sequence;

    /**
     * Makes the player's snapshot now; its parts start out at the end of
     * this tick. The waystones the character played owns and has not
     * found yet are found first, so one handed to it is on its map from
     * the first snapshot it is sent (WAY-08).
     */
    public static void sync(EntityPlayerMP player) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(player.worldObj);
        final LostTalesQuestPlayerData quests =
                LostTalesQuestPlayerData.get(player);
        Discoveries discoveries = new Discoveries() {
            @Override
            public boolean has(String markerId) {
                return quests != null
                        && quests.isMarkerDiscovered(markerId);
            }
        };
        LostTalesMapMarkerVisibilityPolicy.Viewer viewer =
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(player);
        Collection<LostTalesMapMarkerRecord> records = data.getRecords();
        if (quests != null) {
            for (String markerId : ownedNotFound(records, viewer,
                    discoveries)) {
                LostTalesQuestManager.revealMapMarker(player, markerId);
            }
        }
        synchronized (LostTalesMapMarkerSyncManager.class) {
            PENDING.remove(player.getUniqueID());
        }
        List<LostTalesMapMarkerDefinition> visible = select(
                records, viewer, discoveries,
                LostTalesWaystonePlacers.of(player.worldObj));
        try {
            OUTBOX.queue(player.getUniqueID(),
                    LostTalesMapMarkerSnapshotPacket.split(
                            nextSequence(), visible));
        } catch (RuntimeException exception) {
            LostTalesLog.warning("Could not make %s's map marker snapshot of %d markers; their map keeps the last one: %s",
                    player.getCommandSenderName(),
                    Integer.valueOf(visible.size()), exception.toString());
        }
        // Whatever changed what the player sees may have taken the
        // waystone their page is open on out of their sight.
        LostTalesWaystoneSettingsService.checkSight(player, data);
    }

    /** The same, every waystone called after its placer as the record keeps it. */
    static List<LostTalesMapMarkerDefinition> select(
            Collection<LostTalesMapMarkerRecord> records,
            LostTalesMapMarkerVisibilityPolicy.Viewer viewer,
            Discoveries discovered) {
        return select(records, viewer, discovered, null);
    }

    /**
     * What a snapshot holds for {@code viewer}: every marker it may see,
     * but a marker hidden until discovered only once the character played
     * has discovered it (WAY-11). A manager is sent every marker it may
     * see, discovered or not. An unnamed waystone goes called after its
     * placing character by the name {@code placers} gives it.
     */
    static List<LostTalesMapMarkerDefinition> select(
            Collection<LostTalesMapMarkerRecord> records,
            LostTalesMapMarkerVisibilityPolicy.Viewer viewer,
            Discoveries discovered,
            LostTalesMapMarkerNamedAfter.Names placers) {
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
            visible.add(record.toDefinition(placers));
        }
        return visible;
    }

    /**
     * The discoverable waystones the viewer's character owns and has not
     * found: a waystone counts as found by its owner, the character that
     * placed it or the one it was handed to (WAY-08).
     */
    static List<String> ownedNotFound(
            Collection<LostTalesMapMarkerRecord> records,
            LostTalesMapMarkerVisibilityPolicy.Viewer viewer,
            Discoveries discovered) {
        List<String> found = new ArrayList<String>();
        UUID characterId = viewer == null ? null : viewer.characterId();
        if (records == null || characterId == null || discovered == null) {
            return found;
        }
        for (LostTalesMapMarkerRecord record : records) {
            if (characterId.equals(record.getOwnerCharacterId())
                    && record.isDiscoverable()
                    && !discovered.has(record.getId())) {
                found.add(record.getId());
            }
        }
        return found;
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

    /**
     * The character's name changed, or the character was purged: the
     * players who may see a waystone called after it are sent a fresh
     * snapshot within a second, which names it as it stands now (WAY-01).
     */
    public static void placerChanged(World world, UUID characterId) {
        if (world == null || world.isRemote || characterId == null) {
            return;
        }
        List<LostTalesMapMarkerRecord> calledAfter =
                new ArrayList<LostTalesMapMarkerRecord>();
        for (LostTalesMapMarkerRecord record
                : LostTalesMapMarkerStorage.get(world).getRecords()) {
            if (record.isCalledAfter(characterId)) {
                calledAfter.add(record);
            }
        }
        if (calledAfter.isEmpty()) {
            return;
        }
        for (EntityPlayerMP player : onlinePlayers()) {
            LostTalesMapMarkerVisibilityPolicy.Viewer viewer =
                    LostTalesMapMarkerVisibilityPolicy.Viewer.of(player);
            for (LostTalesMapMarkerRecord record : calledAfter) {
                if (LostTalesMapMarkerVisibilityPolicy.canView(record, viewer)) {
                    mark(player);
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks >= FLUSH_TICKS) {
            ticks = 0;
            syncMarked();
        }
        sendWaitingParts();
    }

    /** Sends every marked player their snapshot. */
    private static void syncMarked() {
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

    /**
     * Sends this tick's share of the waiting parts. A player who left is
     * forgotten; one whose part cannot be written keeps the map they have,
     * and the next snapshot tries again.
     */
    private static void sendWaitingParts() {
        if (OUTBOX.isEmpty()) {
            return;
        }
        final Map<UUID, EntityPlayerMP> online =
                new HashMap<UUID, EntityPlayerMP>();
        for (EntityPlayerMP player : onlinePlayers()) {
            online.put(player.getUniqueID(), player);
        }
        OUTBOX.send(BYTES_PER_TICK, new LostTalesMapMarkerSnapshotOutbox.Sender() {
            @Override
            public boolean send(UUID playerId,
                                LostTalesMapMarkerSnapshotPacket part) {
                EntityPlayerMP player = online.get(playerId);
                if (player == null) {
                    return false;
                }
                try {
                    LostTalesNetworkHandler.CHANNEL.sendTo(part, player);
                    return true;
                } catch (RuntimeException exception) {
                    LostTalesLog.warning("Could not send %s part %d of %d of their map markers: %s",
                            player.getCommandSenderName(),
                            Integer.valueOf(part.getIndex() + 1),
                            Integer.valueOf(part.getTotal()),
                            exception.toString());
                    return false;
                }
            }
        });
    }

    /** The next snapshot's sequence, one past the last. */
    private static synchronized int nextSequence() {
        return ++sequence;
    }

    public static synchronized void clear() {
        PENDING.clear();
        OUTBOX.clear();
        ticks = 0;
        sequence = 0;
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
