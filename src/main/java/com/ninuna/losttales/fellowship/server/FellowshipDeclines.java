package com.ninuna.losttales.fellowship.server;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Whose invitation to whom was declined or taken back a moment ago. After
 * either the inviting account waits {@link #WAIT_MILLIS} before it may
 * invite that account again, so nobody can be chimed at over and over.
 * Static state, at most {@link #MAX_ENTRIES} entries, cleared as the server
 * starts and stops.
 */
public final class FellowshipDeclines {
    static final long WAIT_MILLIS = 60000L;
    static final int MAX_ENTRIES = 1024;

    private static final Map<String, Long> UNTIL = new LinkedHashMap<String, Long>();

    private FellowshipDeclines() {}

    /** The inviter's invitation to the target was declined or taken back at {@code now}. */
    static synchronized void startWait(UUID inviter, UUID target, long now) {
        if (inviter == null || target == null) {
            return;
        }
        prune(now);
        String key = key(inviter, target);
        UNTIL.remove(key);
        UNTIL.put(key, Long.valueOf(now + WAIT_MILLIS));
        Iterator<String> oldest = UNTIL.keySet().iterator();
        while (UNTIL.size() > MAX_ENTRIES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** Whether the inviter must still wait before inviting the target again. */
    static synchronized boolean mustWait(UUID inviter, UUID target, long now) {
        if (inviter == null || target == null) {
            return false;
        }
        Long until = UNTIL.get(key(inviter, target));
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

    private static String key(UUID inviter, UUID target) {
        return inviter + ">" + target;
    }
}
