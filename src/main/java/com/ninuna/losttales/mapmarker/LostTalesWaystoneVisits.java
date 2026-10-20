package com.ninuna.losttales.mapmarker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The waystone each player used last, as the server opened its page for
 * them: the page closes once they may no longer see it, and Destinations
 * opens a window of two minutes in which the next place they pick on the
 * map is travelled to from it. Static state, cleared as the server starts
 * and stops and as the player leaves.
 */
public final class LostTalesWaystoneVisits {
    /** How long after Destinations the next pick travels from the waystone: two minutes. */
    public static final long TRAVEL_WINDOW_MILLIS = 120000L;
    /** A visit without a window to travel. */
    private static final long NO_WINDOW = Long.MIN_VALUE;

    private static final Map<UUID, Visit> VISITS = new HashMap<UUID, Visit>();

    private LostTalesWaystoneVisits() {}

    /** The player used a waystone, and its page opened for them. */
    public static synchronized void opened(UUID playerId, String markerId,
                                           int dimensionId, int x, int y,
                                           int z) {
        if (playerId != null && markerId != null) {
            VISITS.put(playerId, new Visit(markerId, dimensionId, x, y, z,
                    NO_WINDOW));
        }
    }

    /** The player took Destinations at a waystone: its window opens from {@code nowMillis}. */
    public static synchronized void destinations(UUID playerId,
                                                 String markerId,
                                                 int dimensionId, int x,
                                                 int y, int z,
                                                 long nowMillis) {
        if (playerId != null && markerId != null) {
            VISITS.put(playerId, new Visit(markerId, dimensionId, x, y, z,
                    nowMillis + TRAVEL_WINDOW_MILLIS));
        }
    }

    /**
     * Whether the player's window to travel from that very waystone is
     * open at {@code nowMillis}. Whatever it answers, the window closes:
     * one pick is all it gives.
     */
    public static synchronized boolean takeTravel(UUID playerId,
                                                  String markerId,
                                                  int dimensionId, int x,
                                                  int y, int z,
                                                  long nowMillis) {
        Visit visit = playerId == null ? null : VISITS.get(playerId);
        if (visit == null) {
            return false;
        }
        boolean open = visit.windowEnds != NO_WINDOW
                && nowMillis <= visit.windowEnds
                && visit.isAt(markerId, dimensionId, x, y, z);
        VISITS.put(playerId, new Visit(visit.markerId, visit.dimensionId,
                visit.x, visit.y, visit.z, NO_WINDOW));
        return open;
    }

    /** The waystone the player used last; null for none. */
    public static synchronized Visit get(UUID playerId) {
        return playerId == null ? null : VISITS.get(playerId);
    }

    public static synchronized void forget(UUID playerId) {
        if (playerId != null) {
            VISITS.remove(playerId);
        }
    }

    public static synchronized void clear() {
        VISITS.clear();
    }

    /** A waystone a player used: its marker, its world and its block. */
    public static final class Visit {
        private final String markerId;
        private final int dimensionId;
        private final int x;
        private final int y;
        private final int z;
        /** When the window to travel closes; {@link #NO_WINDOW} for none. */
        private final long windowEnds;

        private Visit(String markerId, int dimensionId, int x, int y, int z,
                      long windowEnds) {
            this.markerId = markerId;
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.windowEnds = windowEnds;
        }

        boolean isAt(String marker, int dimension, int blockX, int blockY,
                     int blockZ) {
            return this.markerId.equals(marker)
                    && this.dimensionId == dimension && this.x == blockX
                    && this.y == blockY && this.z == blockZ;
        }

        public String getMarkerId() { return this.markerId; }
        public int getDimensionId() { return this.dimensionId; }
        public int getX() { return this.x; }
        public int getY() { return this.y; }
        public int getZ() { return this.z; }
    }
}
