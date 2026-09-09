package com.ninuna.losttales.chat;

/** Server routing strategies currently used by Lost Tales chat channels. */
public enum ChatRecipientRule {
    GLOBAL,
    PROXIMITY,
    PARTY,
    FACTION,
    /** Only the sender: a private console that echoes back to its author. */
    SELF,
    /**
     * Everyone the channel's gate admits, and nobody else: the routing
     * a staff channel takes. The rule itself names no role — who may
     * read and send is the gate the config puts on the channel
     * ({@link ChatChannelGates}), which a fresh file seeds with the
     * operator role and which is put back whenever its line is missing
     * ({@link ChatRoleConfig#withRequiredGates}), so a deleted line never
     * opens the channel. What the rule does say is that a line said
     * here is never opened to anyone by a role granted afterwards.
     */
    OPERATORS,
    /** The sender and one named online player. */
    WHISPER
}
