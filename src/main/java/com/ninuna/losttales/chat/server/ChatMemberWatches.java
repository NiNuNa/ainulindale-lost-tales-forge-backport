package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMembersRequestPacket;
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

/**
 * The member lists each player's windows show, as their client last
 * asked for them, so the server sends a list again the moment it changes
 * rather than at the next ask, as a messenger's member list moves the
 * moment somebody's status does: a status chosen, somebody coming or
 * going, an identity picked. A client keeps asking every few seconds
 * while a list stands, which keeps it watched; a list not asked for in
 * {@link #WATCH_MILLIS} is let go.
 *
 * <p>Nothing is rebuilt until something has changed, and then every
 * watched list at most once in {@link #SWEEP_TICKS}; a list is sent only
 * where it came out different from what its client holds. Static state,
 * cleared with the server's other chat stores.</p>
 */
public final class ChatMemberWatches {
    /** How long a list stays watched after its last ask: a few missed asks, then the client has looked away. */
    static final long WATCH_MILLIS = 10000L;
    /** The fewest ticks between two sweeps over the watched lists, however often something changes. */
    static final int SWEEP_TICKS = 5;
    /** The most lists one player may have watched at once; the oldest ask goes first. */
    static final int MAX_WATCHED = 16;

    private static final Map<UUID, Map<String, Watch>> WATCHES =
            new HashMap<UUID, Map<String, Watch>>();
    private static boolean changed;
    private static int ticks;

    /** One watched list: the ask that named it, what its client holds, and when it last asked. */
    private static final class Watch {
        final LostTalesChatMembersRequestPacket request;
        long fingerprint;
        final long askedAt;

        Watch(LostTalesChatMembersRequestPacket request, long fingerprint,
              long askedAt) {
            this.request = request;
            this.fingerprint = fingerprint;
            this.askedAt = askedAt;
        }
    }

    /**
     * A list asked for and answered: watched from now, the client
     * holding the answer of {@code fingerprint}.
     */
    public static synchronized void watch(UUID player,
                                          LostTalesChatMembersRequestPacket request,
                                          long fingerprint, long now) {
        if (player == null || request == null) {
            return;
        }
        Map<String, Watch> watched = WATCHES.get(player);
        if (watched == null) {
            watched = new LinkedHashMap<String, Watch>();
            WATCHES.put(player, watched);
        }
        String key = request.getConversationKey();
        watched.remove(key);
        watched.put(key, new Watch(request, fingerprint, now));
        Iterator<String> oldest = watched.keySet().iterator();
        while (watched.size() > MAX_WATCHED && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** Something that can move somebody in a list happened; the next sweep looks. */
    public static synchronized void markChanged() {
        changed = true;
    }

    /** A player who left watches nothing, and leaves every list they were in. */
    public static synchronized void forget(UUID player) {
        if (player != null) {
            WATCHES.remove(player);
        }
        changed = true;
    }

    public static synchronized void clear() {
        WATCHES.clear();
        changed = false;
        ticks = 0;
    }

    /** Every few ticks after a change, each watched list again, sent where it moved. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Map<UUID, List<Watch>> due;
        long now = System.currentTimeMillis();
        synchronized (ChatMemberWatches.class) {
            if (ticks < SWEEP_TICKS) {
                ticks++;
            }
            if (!changed || ticks < SWEEP_TICKS) {
                return;
            }
            changed = false;
            ticks = 0;
            due = dueWatches(now);
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        for (Object value : server.getConfigurationManager().playerEntityList) {
            if (!(value instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP)value;
            List<Watch> watches = due.get(player.getUniqueID());
            if (watches == null) {
                continue;
            }
            for (Watch watch : watches) {
                LostTalesChatMembersPacket list =
                        ChatMemberDirectory.listFor(player, watch.request);
                if (list.getFingerprint() == watch.fingerprint) {
                    continue;
                }
                synchronized (ChatMemberWatches.class) {
                    watch.fingerprint = list.getFingerprint();
                }
                LostTalesNetworkHandler.CHANNEL.sendTo(list, player);
            }
        }
    }

    /** How many lists of a player's are still watched at {@code now}; those let go are dropped. */
    static synchronized int watchedAt(UUID player, long now) {
        List<Watch> watched = dueWatches(now).get(player);
        return watched == null ? 0 : watched.size();
    }

    /** The lists still watched at {@code now}, by player; those let go are dropped. */
    private static Map<UUID, List<Watch>> dueWatches(long now) {
        Map<UUID, List<Watch>> due = new HashMap<UUID, List<Watch>>();
        Iterator<Map.Entry<UUID, Map<String, Watch>>> players =
                WATCHES.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Map<String, Watch>> entry = players.next();
            List<Watch> kept = new ArrayList<Watch>();
            Iterator<Watch> watches = entry.getValue().values().iterator();
            while (watches.hasNext()) {
                Watch watch = watches.next();
                if (now - watch.askedAt > WATCH_MILLIS) {
                    watches.remove();
                } else {
                    kept.add(watch);
                }
            }
            if (entry.getValue().isEmpty()) {
                players.remove();
            } else {
                due.put(entry.getKey(), kept);
            }
        }
        return due;
    }
}
