package com.ninuna.losttales.quest;

/**
 * Where a quest start comes from. The quest manager checks it against the
 * quest's start mode and the server's start settings, so a client naming
 * a quest id starts nothing by that alone.
 */
public enum LostTalesQuestStartSource {
    /** The server's own path: an operator's command, or a missive whose seal the server checked. */
    COMMAND,
    /** A quest item used by the player. */
    ITEM,
    /** The quest's giver touched or spoken to: an entity or block the quest names, or an entity carrying its id. */
    INTERACTION,
    /** Accepted from a server-validated chat card shared by a fellowship member. */
    SHARED
}
