package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatPresence;

/**
 * The rule by which Away sets in and lifts on its own: after
 * {@link #IDLE_NANOS} with no key, no click, no mouse movement and no
 * movement in the world, a player who chose Online is told to be Away;
 * their first move brings them back. A presence chosen by hand — Away
 * or Do Not Disturb — is never touched. Kept apart from the sampling
 * so the rule can be tested on its own.
 */
final class ChatAutoAway {
    /** Five minutes of nothing before Away sets in. */
    static final long IDLE_NANOS = 5L * 60L * 1000000000L;

    /** What one tick's sampling means for the presence the server is told. */
    enum Change {
        NONE,
        GO_AWAY,
        COME_BACK
    }

    private ChatAutoAway() {}

    static Change step(boolean active, long nowNanos, long lastActivityNanos,
                       ChatPresence chosen, boolean autoAway) {
        if (active) {
            return autoAway ? Change.COME_BACK : Change.NONE;
        }
        if (!autoAway && chosen == ChatPresence.ONLINE
                && nowNanos - lastActivityNanos >= IDLE_NANOS) {
            return Change.GO_AWAY;
        }
        return Change.NONE;
    }
}
