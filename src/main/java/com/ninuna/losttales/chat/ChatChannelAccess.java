package com.ninuna.losttales.chat;

/**
 * What a player must currently hold to use a channel, beyond any role
 * its gate names. Routing says who a message reaches; access says who
 * may be in the room at all — two separate facts: the Fellowship channel
 * routes to a fellowship's members, and a player is in the room only while
 * they have one. Role requirements are not listed here: they are the
 * config's ({@link ChatChannelGates}), the Operator channel's included.
 * The server checks the real thing on every send; the client mirrors
 * the same answer to decide which tabs to show.
 */
public enum ChatChannelAccess {
    /** Open to everyone online. */
    NONE,
    /**
     * The faction of the chat identity, which decides the conversation a
     * line goes to rather than whether it may be sent: every identity has
     * one, the account and a character with no LOTR faction speaking as
     * Unaligned.
     */
    CHARACTER_FACTION,
    /** An active character belonging to a Lost Tales fellowship. */
    FELLOWSHIP_MEMBERSHIP
}
