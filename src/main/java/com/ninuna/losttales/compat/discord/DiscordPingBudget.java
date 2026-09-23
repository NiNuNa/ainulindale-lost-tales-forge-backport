package com.ninuna.losttales.compat.discord;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * How many Discord members each player may ping through the bridge: at
 * most {@link #MOST_PINGS} in any {@link #WINDOW_MILLIS}. A line past the
 * budget still posts, and its mentions still read as names; it only pings
 * nobody. The chat's own rate limit holds how often a player speaks; this
 * holds how many people on Discord they can call on while they do.
 * Static state, forgotten with the bridge's session.
 */
final class DiscordPingBudget {
    static final int MOST_PINGS = 10;
    static final long WINDOW_MILLIS = 60000L;
    /** More players than this are more than a server holds at once; the oldest go. */
    private static final int MOST_PLAYERS = 512;

    private final Map<UUID, Deque<Long>> spent = new HashMap<UUID, Deque<Long>>();

    /**
     * Whether {@code sender} may ping {@code pings} more members now, and
     * if so, spends them: all or none.
     */
    synchronized boolean spend(UUID sender, int pings, long now) {
        if (pings <= 0) {
            return true;
        }
        if (sender == null || pings > MOST_PINGS) {
            return false;
        }
        Deque<Long> times = this.spent.get(sender);
        if (times == null) {
            if (this.spent.size() >= MOST_PLAYERS) {
                forgetIdle(now);
            }
            times = new ArrayDeque<Long>();
            this.spent.put(sender, times);
        }
        while (!times.isEmpty() && now - times.peekFirst().longValue() >= WINDOW_MILLIS) {
            times.pollFirst();
        }
        if (times.size() + pings > MOST_PINGS) {
            return false;
        }
        for (int index = 0; index < pings; index++) {
            times.addLast(Long.valueOf(now));
        }
        return true;
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
