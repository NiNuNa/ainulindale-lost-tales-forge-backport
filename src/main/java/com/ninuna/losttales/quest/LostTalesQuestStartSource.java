package com.ninuna.losttales.quest;

/**
 * Coarse-grained start source used by the 1.7.10 quest manager.
 *
 * The server uses this to enforce item, NPC/script, command, and validated
 * fellowship-share start policies without trusting a client-supplied quest ID.
 */
public enum LostTalesQuestStartSource {
    COMMAND,
    ITEM,
    INTERACTION,
    /** Accepted from a server-validated chat card shared by a fellowship member. */
    SHARED
}
