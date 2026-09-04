package com.ninuna.losttales.chat;

/**
 * What a player must currently hold to use a channel. Routing says who
 * a message reaches; access says who may be in the room at all — two
 * separate facts: the Party channel routes to a party's members, and a
 * player is in the room only while they have one. The server checks the
 * real thing on every send; the client mirrors the same answer to
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
    OPERATOR
}
