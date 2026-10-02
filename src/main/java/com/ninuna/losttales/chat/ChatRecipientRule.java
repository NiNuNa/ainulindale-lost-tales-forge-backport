package com.ninuna.losttales.chat;

/** How the server decides who hears a channel's lines. */
public enum ChatRecipientRule {
    /** Everyone online: Global and OOC. */
    EVERYONE,
    PROXIMITY,
    FELLOWSHIP,
    FACTION,
    /** Only the sender: a private console that echoes back to its author. */
    SELF,
    /**
     * Everyone holding {@code chat.server_console.read}: the server's own
     * console, one stream shared by the staff who may watch it. The rule
     * names a capability rather than a role, because reading the
     * server's doings is something the code grants, not something a
     * channel gate describes — see {@code ChatChannelPolicy.readsConsole}.
     */
    CONSOLE_READERS,
    /**
     * Everyone the channel's gate admits, and nobody else: the routing
     * a staff channel takes. The rule itself names no role — who may
     * read and send is the gate the config puts on the channel
     * ({@link ChatChannelGates}), which a fresh file seeds with the
     * operator role and which is put back whenever its line is missing
     * ({@link ChatRoleConfig#withRequiredGates}), so a deleted line never
     * opens the channel. A line said here is kept for whoever the gate
     * admits when they ask, not for those it reached: a role granted
     * afterwards opens what was said before, and a role taken away closes
     * it ({@code ChatChannelPolicy.audienceFor}).
     */
    OPERATORS,
    /** The sender and one named online player. */
    WHISPER
}
