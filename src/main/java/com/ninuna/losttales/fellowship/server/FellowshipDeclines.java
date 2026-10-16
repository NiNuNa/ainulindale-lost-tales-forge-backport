package com.ninuna.losttales.fellowship.server;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which fellowship's invitation to whom was declined or taken back a
 * moment ago. After either, nobody of that fellowship may invite that
 * account again for {@link #WAIT_MILLIS}, whoever sent the first, so a
 * player is never chimed at over and over by one guide after another.
 * Static state, at most {@link #MAX_ENTRIES} entries, cleared as the server
 * starts and stops.
 */
public final class FellowshipDeclines {
    static final long WAIT_MILLIS = 60000L;
    static final int MAX_ENTRIES = 1024;

    private static final Map<String, Long> UNTIL = new LinkedHashMap<String, Long>();

    private FellowshipDeclines() {}

    /** The fellowship's invitation to the target account was declined or taken back at {@code now}. */
    static synchronized void startWait(UUID fellowship, UUID target, long now) {
        if (fellowship == null || target == null) {
            return;
        }
        prune(now);
        String key = key(fellowship, target);
        UNTIL.remove(key);
        UNTIL.put(key, Long.valueOf(now + WAIT_MILLIS));
        Iterator<String> oldest = UNTIL.keySet().iterator();
        while (UNTIL.size() > MAX_ENTRIES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** Whether the fellowship must still wait before inviting the target account again. */
    static synchronized boolean mustWait(UUID fellowship, UUID target, long now) {
        if (fellowship == null || target == null) {
            return false;
        }
        Long until = UNTIL.get(key(fellowship, target));
        return until != null && now < until.longValue();
    }

    public static synchronized void clear() {
        UNTIL.clear();
    }

    private static void prune(long now) {
        Iterator<Long> until = UNTIL.values().iterator();
        while (until.hasNext()) {
            if (now >= until.next().longValue()) {
                until.remove();
            }
        }
    }

    private static String key(UUID fellowship, UUID target) {
        return fellowship + ">" + target;
    }
}
