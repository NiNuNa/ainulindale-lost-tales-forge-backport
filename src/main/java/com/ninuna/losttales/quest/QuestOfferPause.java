package com.ninuna.losttales.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The short pause after a creature offered a player a Lost Tales quest
 * or told a rumour (QUEST-11): touching it again within {@link #PAUSE_TICKS}
 * starts, offers and tells nothing, so a LOTR NPC's own talk, trade or
 * hire goes through and a trader who gives quests can still be traded
 * with. One pause per player, the latest offer's. Cleared as the server
 * starts and stops and as the player leaves.
 */
public final class QuestOfferPause {
    /** How long, in ticks, the pause lasts: thirty seconds. */
    public static final long PAUSE_TICKS = 600L;

    private static final Map<UUID, long[]> PAUSES = new HashMap<UUID, long[]>();

    private QuestOfferPause() {}

    /** Remembers that the creature {@code entityId} offered the player a quest at {@code now}. */
    public static synchronized void offered(UUID player, int entityId, long now) {
        if (player != null) {
            PAUSES.put(player, new long[] { entityId, now + PAUSE_TICKS });
        }
    }

    /** Whether the player's touch of the creature {@code entityId} at {@code now} falls in its pause. */
    public static synchronized boolean isPaused(UUID player, int entityId, long now) {
        long[] pause = player == null ? null : PAUSES.get(player);
        if (pause == null) {
            return false;
        }
        if (now >= pause[1]) {
            PAUSES.remove(player);
            return false;
        }
        return pause[0] == entityId;
    }

    public static synchronized void forget(UUID player) {
        if (player != null) {
            PAUSES.remove(player);
        }
    }

    public static synchronized void clear() {
        PAUSES.clear();
    }
}
