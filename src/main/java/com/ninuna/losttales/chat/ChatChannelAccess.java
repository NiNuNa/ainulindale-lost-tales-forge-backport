package com.ninuna.losttales.chat;

/**
 * What a player must currently hold to use a channel. Routing says who
 * a message reaches; access says who may be in the room at all — two
 * separate facts, and the Discord channel is why: it routes globally
 * yet exists only while the server's bridge is on. The server checks
 * the real thing on every send; the client mirrors the same answer to
 * decide which tabs to show.
 */
public enum ChatChannelAccess {
    /** Open to everyone online. */
    NONE,
    /** An active character with a LOTR faction. */
    CHARACTER_FACTION,
    /** An active character belonging to a Lost Tales party. */
    PARTY_MEMBERSHIP,
    /** Server operator status. */
    OPERATOR,
    /** The server's Discord bridge switched on. */
    DISCORD_BRIDGE
}
