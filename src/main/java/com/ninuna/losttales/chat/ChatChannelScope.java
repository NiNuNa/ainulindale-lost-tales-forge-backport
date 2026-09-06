package com.ninuna.losttales.chat;

/**
 * What tells one conversation on a channel from another. Most channels
 * are one conversation: everyone who may read them reads the same
 * lines, and the identity a player speaks as changes only how a line is
 * signed. A scoped channel is as many conversations as it has scope
 * values, and which one a player is in follows the identity they are
 * reading and speaking as — so an account with characters in two
 * factions has two faction conversations, and neither is the other.
 */
public enum ChatChannelScope {
    /** One conversation, whoever is being played. */
    NONE,
    /** One conversation per LOTR faction; the identity names which. */
    FACTION;

    /**
     * Whether a tab on this channel carries the identity it is read as.
     * Two identities of one account then have tabs of their own, and
     * the lines of one are never shown under the other.
     */
    public boolean isIdentityScoped() {
        return this != NONE;
    }
}
