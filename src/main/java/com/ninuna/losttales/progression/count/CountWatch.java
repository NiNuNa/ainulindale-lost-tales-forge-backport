package com.ninuna.losttales.progression.count;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the counting watches between two moments, kept while the server
 * runs and never saved: where each rider stood a second ago, the mount it
 * rode and whether that was tamed, the keg it had open and its state, the
 * regions its character had opened, and the touches (taming, feeding)
 * whose outcome shows a tick later. Cleared as the server starts and
 * stops, and per player as they leave or switch character.
 */
public final class CountWatch {
    /** The most touches waiting for their outcome at once. */
    public static final int MAX_PENDING = 256;

    private static final Map<UUID, Rider> RIDERS = new HashMap<UUID, Rider>();
    private static final Map<UUID, Integer> KEGS = new HashMap<UUID, Integer>();
    private static final Map<UUID, Set<String>> REGIONS = new HashMap<UUID, Set<String>>();
    private static final List<Pending> PENDING = new ArrayList<Pending>();

    private CountWatch() {}

    public static synchronized void clear() {
        RIDERS.clear();
        KEGS.clear();
        REGIONS.clear();
        PENDING.clear();
    }

    /** Forgets one player, as they leave or switch character. */
    public static synchronized void forget(UUID playerId) {
        RIDERS.remove(playerId);
        KEGS.remove(playerId);
        REGIONS.remove(playerId);
        Iterator<Pending> pending = PENDING.iterator();
        while (pending.hasNext()) {
            if (pending.next().playerId.equals(playerId)) {
                pending.remove();
            }
        }
    }

    static synchronized Rider rider(UUID playerId) {
        Rider rider = RIDERS.get(playerId);
        if (rider == null) {
            rider = new Rider();
            RIDERS.put(playerId, rider);
        }
        return rider;
    }

    /** The keg state last seen for the player, and what it is now; -1 for none. */
    static synchronized int swapKegMode(UUID playerId, int mode) {
        Integer before = mode < 0 ? KEGS.remove(playerId)
                : KEGS.put(playerId, Integer.valueOf(mode));
        return before == null ? -1 : before.intValue();
    }

    /** The regions last seen for the player, and what they are now; null the first time. */
    static synchronized Set<String> swapRegions(UUID playerId, Set<String> regions) {
        return REGIONS.put(playerId, regions);
    }

    static synchronized void await(Pending pending) {
        if (PENDING.size() >= MAX_PENDING) {
            PENDING.remove(0);
        }
        PENDING.add(pending);
    }

    /** The touches due by {@code tick}, taken off the list. */
    static synchronized List<Pending> due(long tick) {
        List<Pending> due = new ArrayList<Pending>();
        Iterator<Pending> pending = PENDING.iterator();
        while (pending.hasNext()) {
            Pending next = pending.next();
            if (next.dueTick <= tick) {
                due.add(next);
                pending.remove();
            }
        }
        return due;
    }

    /** One rider: where it stood at the last sample, the mount it rode and whether it was tamed. */
    static final class Rider {
        int dimension = Integer.MIN_VALUE;
        double x;
        double z;
        int mountId = -1;
        boolean mountTamed;
    }

    /** A touch whose outcome shows a tick later: a taming try or a feeding. */
    static final class Pending {
        final UUID playerId;
        final int dimension;
        final int entityId;
        final boolean taming;
        final float healthBefore;
        final long dueTick;

        Pending(UUID playerId, int dimension, int entityId, boolean taming,
                float healthBefore, long dueTick) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.entityId = entityId;
            this.taming = taming;
            this.healthBefore = healthBefore;
            this.dueTick = dueTick;
        }
    }
}
