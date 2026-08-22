package com.ninuna.losttales.chat;

/** Server routing strategies currently used by Lost Tales chat channels. */
public enum ChatRecipientRule {
    GLOBAL,
    PROXIMITY,
    PARTY,
    FACTION,
    /** Only the sender: a private console that echoes back to its author. */
    SELF,
    /** Only server operators; sending also requires operator status. */
    OPERATORS,
    /** The sender and one named online player. */
    WHISPER
}
