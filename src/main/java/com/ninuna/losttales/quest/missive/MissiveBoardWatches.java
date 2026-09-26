package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMissiveBoardStatePacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;

/**
 * The missive board each player's page shows, as the server last sent
 * it, so a change reaches everybody standing at a board rather than only
 * the player who made it: somebody else taking, pinning or accepting a
 * notice, and the board posting new ones or taking old ones down.
 *
 * <p>A player watches the one board last sent to them, from opening it
 * on. Every change a board makes marks the watches to be looked at; at
 * most once in {@link #SWEEP_TICKS} each watched board is read again and
 * sent where it came out different from what its player holds. A watch
 * ends once its player is gone, in another world, out of the board's
 * reach, or the board is gone. Static state, cleared as the server
 * starts and stops.</p>
 */
public final class MissiveBoardWatches {
    /** The fewest ticks between two sweeps, however often boards change. */
    static final int SWEEP_TICKS = 5;
    /** The most watches kept; the oldest goes first. One per player, so only a crowd reaches it. */
    static final int MAX_WATCHES = 512;

    private static final Map<UUID, Watch> WATCHES =
            new LinkedHashMap<UUID, Watch>();
    private static boolean changed;
    private static int ticks;

    /** One player's board and what they hold of it. */
    static final class Watch {
        final int dimensionId;
        final int x;
        final int y;
        final int z;
        long fingerprint;

        Watch(int dimensionId, int x, int y, int z, long fingerprint) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fingerprint = fingerprint;
        }
    }

    /** The player was sent a board's notices of {@code fingerprint}: they watch that board from now on. */
    public static synchronized void watch(UUID player, int dimensionId,
                                          int x, int y, int z,
                                          long fingerprint) {
        if (player == null) {
            return;
        }
        WATCHES.remove(player);
        WATCHES.put(player, new Watch(dimensionId, x, y, z, fingerprint));
        Iterator<UUID> oldest = WATCHES.keySet().iterator();
        while (WATCHES.size() > MAX_WATCHES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** A board changed its notices; the next sweep looks. */
    public static synchronized void markChanged() {
        changed = true;
    }

    /** A player who left watches nothing. */
    public static synchronized void forget(UUID player) {
        if (player != null) {
            WATCHES.remove(player);
        }
    }

    public static synchronized void clear() {
        WATCHES.clear();
        changed = false;
        ticks = 0;
    }

    /** How many watches stand. */
    static synchronized int size() {
        return WATCHES.size();
    }

    /** The board the player watches; null for none. */
    static synchronized Watch watchOf(UUID player) {
        return player == null ? null : WATCHES.get(player);
    }

    /** Every few ticks after a change, each watched board again, sent where it moved. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Map<UUID, Watch> due;
        synchronized (MissiveBoardWatches.class) {
            if (ticks < SWEEP_TICKS) {
                ticks++;
            }
            if (!changed || ticks < SWEEP_TICKS || WATCHES.isEmpty()) {
                return;
            }
            changed = false;
            ticks = 0;
            due = new HashMap<UUID, Watch>(WATCHES);
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        List<UUID> ended = new ArrayList<UUID>();
        Map<UUID, EntityPlayerMP> online = new HashMap<UUID, EntityPlayerMP>();
        for (Object value : server.getConfigurationManager().playerEntityList) {
            if (value instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP)value;
                online.put(player.getUniqueID(), player);
            }
        }
        for (Map.Entry<UUID, Watch> entry : due.entrySet()) {
            EntityPlayerMP player = online.get(entry.getKey());
            Watch watch = entry.getValue();
            LostTalesTileEntityMissiveBoard board = boardFor(player, watch);
            if (board == null) {
                ended.add(entry.getKey());
                continue;
            }
            LostTalesMissiveBoardStatePacket state = MissiveBoardService.stateOf(
                    board, MissiveBoardStateReason.CHANGED);
            if (state.getFingerprint() == watch.fingerprint) {
                continue;
            }
            synchronized (MissiveBoardWatches.class) {
                watch.fingerprint = state.getFingerprint();
            }
            LostTalesNetworkHandler.CHANNEL.sendTo(state, player);
        }
        synchronized (MissiveBoardWatches.class) {
            for (UUID player : ended) {
                if (WATCHES.get(player) == due.get(player)) {
                    WATCHES.remove(player);
                }
            }
        }
    }

    /** The board a watch names, where its player still stands at it; null where they do not. */
    private static LostTalesTileEntityMissiveBoard boardFor(
            EntityPlayerMP player, Watch watch) {
        // The distance first, so a board left far behind never has its
        // chunk loaded to be asked.
        if (player == null || player.worldObj == null
                || player.worldObj.provider == null
                || player.worldObj.provider.dimensionId != watch.dimensionId
                || !LostTalesTileEntityMissiveBoard.isWithinReach(player,
                        watch.x, watch.y, watch.z)) {
            return null;
        }
        TileEntity tile = player.worldObj.getTileEntity(watch.x, watch.y,
                watch.z);
        if (!(tile instanceof LostTalesTileEntityMissiveBoard)) {
            return null;
        }
        LostTalesTileEntityMissiveBoard board =
                (LostTalesTileEntityMissiveBoard)tile;
        return board.isUseableByPlayer(player) ? board : null;
    }
}
