package com.ninuna.losttales.compat.discord;

import java.util.HashMap;
import java.util.Map;

/**
 * When a question Discord left unanswered may be asked again, per key.
 * A failure holds the key back for a pause that doubles with each
 * failure in a row, from five seconds up to a minute; an answer forgets
 * the key. Not thread-safe: each belongs to one worker thread. The keys
 * are the bindings' own, so the map is as bounded as the config.
 */
final class DiscordRetryClock {
    static final long MIN_WAIT_MILLIS = 5000L;
    static final long MAX_WAIT_MILLIS = 60000L;

    /** A key held back: until when, and how long the next pause is. */
    private static final class Hold {
        long notBeforeMillis;
        long nextWaitMillis = MIN_WAIT_MILLIS;
    }

    private final Map<String, Hold> held = new HashMap<String, Hold>();

    /** Whether {@code key} may be asked now: never failed, or its pause is over. */
    boolean mayAsk(String key, long nowMillis) {
        Hold hold = this.held.get(key);
        return hold == null || nowMillis >= hold.notBeforeMillis;
    }

    /** {@code key} was asked at {@code nowMillis} and got no answer. */
    void failed(String key, long nowMillis) {
        Hold hold = this.held.get(key);
        if (hold == null) {
            hold = new Hold();
            this.held.put(key, hold);
        }
        hold.notBeforeMillis = nowMillis + hold.nextWaitMillis;
        hold.nextWaitMillis = Math.min(MAX_WAIT_MILLIS, hold.nextWaitMillis * 2L);
    }

    /** {@code key} was answered; its next failure starts from the shortest pause. */
    void answered(String key) {
        this.held.remove(key);
    }
}
