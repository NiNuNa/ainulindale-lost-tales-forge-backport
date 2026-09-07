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
    /**
     * One conversation per LOTR faction. Which one a player is in
     * follows the identity they are <em>reading</em> as: an account may
     * read the talk of any faction it has a character in, whichever
     * character it happens to be playing.
     */
    FACTION,
    /**
     * One conversation per party. Which one a player is in follows the
     * identity they are <em>playing</em>: membership is that identity's,
     * so a party is not something another of the account's characters
     * can read while it is not in it.
     */
    PARTY;

    /**
     * Whether a channel of this scope is more than one conversation. The
     * row holds one tab for it either way; which conversation that tab
     * stands for follows the scope's own rule.
     */
    public boolean isScoped() {
        return this != NONE;
    }
}
