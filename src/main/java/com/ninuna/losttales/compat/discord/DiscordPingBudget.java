package com.ninuna.losttales.compat.discord;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * How many pings each player may make on Discord through the bridge: at
 * most {@link #MOST_PINGS} in any {@link #WINDOW_MILLIS}. Every ping a
 * post really makes counts, so a member pinged in three linked channels
 * is three pings, and a post that pings nobody costs nothing. A post past
 * the budget still goes, and its mentions still read as names; it only
 * pings nobody. The chat's own rate limit holds how often a player
 * speaks; this holds how many calls on people on Discord they can make
 * while they do. Forgotten with the bridge's session.
 */
final class DiscordPingBudget {
    static final int MOST_PINGS = 10;
    static final long WINDOW_MILLIS = 60000L;
    /** More players than this are more than a server holds at once; the oldest go. */
    private static final int MOST_PLAYERS = 512;

    private final Map<UUID, Deque<Long>> spent = new HashMap<UUID, Deque<Long>>();

    /**
     * Whether {@code sender} may make {@code pings} more pings now. Asking
     * spends nothing; {@link #spend} counts the pings once they are made.
     */
    synchronized boolean allows(UUID sender, int pings, long now) {
        if (pings <= 0) {
            return true;
        }
        if (sender == null || pings > MOST_PINGS) {
            return false;
        }
        Deque<Long> times = this.spent.get(sender);
        if (times == null) {
            return true;
        }
        dropExpired(times, now);
        return times.size() + pings <= MOST_PINGS;
    }

    /**
     * Counts {@code pings} pings {@code sender} made now. Only the newest
     * {@link #MOST_PINGS} are kept, which is all the budget looks at.
     */
    synchronized void spend(UUID sender, int pings, long now) {
        if (sender == null || pings <= 0) {
            return;
        }
        Deque<Long> times = this.spent.get(sender);
        if (times == null) {
            if (this.spent.size() >= MOST_PLAYERS) {
                forgetIdle(now);
            }
            times = new ArrayDeque<Long>();
            this.spent.put(sender, times);
        }
        dropExpired(times, now);
        for (int index = 0; index < Math.min(pings, MOST_PINGS); index++) {
            times.addLast(Long.valueOf(now));
        }
        while (times.size() > MOST_PINGS) {
            times.pollFirst();
        }
    }

    /** Forgets the pings that have run out of the window. */
    private static void dropExpired(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst().longValue() >= WINDOW_MILLIS) {
            times.pollFirst();
        }
    }

    synchronized void clear() {
        this.spent.clear();
    }

    /** Forgets everyone whose pings have all run out of the window. */
    private void forgetIdle(long now) {
        Iterator<Map.Entry<UUID, Deque<Long>>> entries =
                this.spent.entrySet().iterator();
        while (entries.hasNext()) {
            Deque<Long> times = entries.next().getValue();
            if (times.isEmpty() || now - times.peekLast().longValue() >= WINDOW_MILLIS) {
                entries.remove();
            }
        }
    }
}
