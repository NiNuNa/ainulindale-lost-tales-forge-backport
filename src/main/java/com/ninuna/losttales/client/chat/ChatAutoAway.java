package com.ninuna.losttales.client.chat;

/**
 * The rule by which a player goes idle and comes back: after
 * {@link #IDLE_NANOS} with no key, no click, no mouse movement and no
 * movement in the world the player is idle, and their first move brings
 * them back. The server turns an idle player's Online identities Away;
 * a status chosen by hand — Away, Do Not Disturb or Invisible — is never
 * touched. Kept apart from the sampling so the rule can be tested on its
 * own.
 */
final class ChatAutoAway {
    /** Five minutes of nothing before a player is idle. */
    static final long IDLE_NANOS = 5L * 60L * 1000000000L;

    /** What one tick's sampling means for whether the player is idle. */
    enum Change {
        NONE,
        GO_IDLE,
        COME_BACK
    }

    private ChatAutoAway() {}

    static Change step(boolean active, long nowNanos, long lastActivityNanos,
                       boolean idle) {
        if (active) {
            return idle ? Change.COME_BACK : Change.NONE;
        }
        if (!idle && nowNanos - lastActivityNanos >= IDLE_NANOS) {
            return Change.GO_IDLE;
        }
        return Change.NONE;
    }
}
