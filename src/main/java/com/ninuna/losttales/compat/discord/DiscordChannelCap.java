package com.ninuna.losttales.compat.discord;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How many lines each linked Discord channel may send into the game: at
 * most the {@code linesPerChannel} option's number in any
 * {@link #WINDOW_MILLIS} (DISC-31). A member is held to a player's
 * limits already; this holds a channel as a whole, so a raid on a
 * public Discord server, or one person with many accounts, costs the
 * game a few lines a second at most. The rest is dropped, counted per
 * channel, and the counts are told at most once every
 * {@link #REPORT_MILLIS} ({@link #takeReport}). Times are read on the
 * monotonic clock. Forgotten with the bridge's session.
 */
final class DiscordChannelCap {
    static final long WINDOW_MILLIS = 5000L;
    static final long REPORT_MILLIS = 60000L;
    /** The most lines a channel's window holds, whatever the option says: the bridge's own queue. */
    static final int MOST_LINES = 256;
    /** More channels than this are more than a bridge links; the idle ones go. */
    private static final int MOST_CHANNELS = 512;

    /** When each channel's lines in the window arrived, oldest first. */
    private final Map<String, Deque<Long>> taken = new HashMap<String, Deque<Long>>();
    /** The lines dropped per channel since the last report, in the order first dropped. */
    private final Map<String, Integer> dropped = new LinkedHashMap<String, Integer>();
    /** When the drops were last told; none yet before the first. */
    private long reportedAt;
    private boolean reported;

    /**
     * Whether a line from {@code channelId} may go in now, with at most
     * {@code most} lines a window; counted as taken when it may, as
     * dropped when it may not.
     */
    synchronized boolean admits(String channelId, int most, long now) {
        String channel = channelId == null ? "" : channelId;
        int cap = Math.max(1, Math.min(MOST_LINES, most));
        Deque<Long> times = this.taken.get(channel);
        if (times == null) {
            if (this.taken.size() >= MOST_CHANNELS) {
                forgetIdle(now);
            }
            times = new ArrayDeque<Long>();
            this.taken.put(channel, times);
        }
        while (!times.isEmpty() && now - times.peekFirst().longValue() >= WINDOW_MILLIS) {
            times.pollFirst();
        }
        if (times.size() >= cap) {
            Integer count = this.dropped.get(channel);
            if (count == null && this.dropped.size() >= MOST_CHANNELS) {
                return false;
            }
            this.dropped.put(channel, Integer.valueOf(count == null ? 1
                    : count.intValue() + 1));
            return false;
        }
        times.addLast(Long.valueOf(now));
        return true;
    }

    /**
     * The lines dropped per channel since the last report, once a
     * {@link #REPORT_MILLIS} has passed since it, and then forgotten;
     * empty while nothing was dropped or the minute is not up.
     */
    synchronized Map<String, Integer> takeReport(long now) {
        if (this.dropped.isEmpty()
                || (this.reported && now - this.reportedAt < REPORT_MILLIS)) {
            return Collections.emptyMap();
        }
        Map<String, Integer> report = new LinkedHashMap<String, Integer>(this.dropped);
        this.dropped.clear();
        this.reported = true;
        this.reportedAt = now;
        return report;
    }

    synchronized void clear() {
        this.taken.clear();
        this.dropped.clear();
        this.reported = false;
        this.reportedAt = 0L;
    }

    /** Forgets every channel whose lines have all left the window. */
    private void forgetIdle(long now) {
        Iterator<Map.Entry<String, Deque<Long>>> entries = this.taken.entrySet().iterator();
        while (entries.hasNext()) {
            Deque<Long> times = entries.next().getValue();
            if (times.isEmpty() || now - times.peekLast().longValue() >= WINDOW_MILLIS) {
                entries.remove();
            }
        }
    }
}
