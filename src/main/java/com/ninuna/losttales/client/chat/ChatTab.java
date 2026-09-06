package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Locale;
import java.util.UUID;

/**
 * What a tab stands for: a channel, and for a whisper the <em>identity</em>
 * the conversation is with, the identity of this player's own it is held
 * as — or the NPC, since LOTR speech is addressed to one player and reads
 * as a whisper from the NPC.
 *
 * <p>A conversation is between two people as they present themselves,
 * not between the accounts behind them: whispering someone speaking as
 * Aldric and whispering the same player speaking as Beren are two
 * conversations, and neither is the one with their account; and what
 * this player says as Aldric is Aldric's conversation, not Beren's, so
 * switching to Beren shows none of it. The accounts are still carried —
 * they are who the message is routed to, always reachable whatever
 * either happens to be playing — but it is the two identities that name
 * the tab and keep the threads apart.</p>
 *
 * <p>Every plain channel is one tab; every whisper conversation is one
 * more, all of them on the {@link ChatChannel#WHISPER} channel, NPCs
 * kept apart from players of the same name. Tabs are values — equal when
 * channel, account, identity, own identity and kind agree, names
 * compared case-insensitively — and are what windows hold, lines are
 * filed under, and the selection points at.</p>
 */
public final class ChatTab {
    private static final String WHISPER_ID_PREFIX = "whisper:";
    private static final String NPC_ID_PREFIX = "npc:";
    /**
     * Between the account and the identity in a tab's id. A Minecraft
     * account name cannot hold one, so the account is always the part
     * before the first of them and an identity may hold as many as it
     * likes. An id without one is the account's own conversation, which
     * is also what every id stored before identities existed reads as.
     */
    private static final char IDENTITY_SEPARATOR = '|';
    /**
     * The last segment of a whisper id, after a separator, when the
     * conversation is held as one of this player's characters: the
     * character's id, which no name can be mistaken for.
     */
    private static final String OWNER_MARK = "own:";
    private static final ChatTab[] PLAIN = new ChatTab[ChatChannel.values().length];

    static {
        for (ChatChannel channel : ChatChannel.values()) {
            // A whisper is always with someone: it has no plain tab.
            if (channel != ChatChannel.WHISPER) {
                PLAIN[channel.ordinal()] = new ChatTab(channel, "", "", "", false);
            }
        }
    }

    private final ChatChannel channel;
    private final String partner;
    private final String partnerKey;
    private final String identity;
    private final String identityKey;
    /** This player's identity the conversation is held as; empty for the account. */
    private final String ownerKey;
    private final boolean npc;

    private ChatTab(ChatChannel channel, String partner, String identity,
                    String ownerKey, boolean npc) {
        this.channel = channel;
        this.partner = partner == null ? "" : partner.trim();
        this.partnerKey = this.partner.toLowerCase(Locale.ROOT);
        String named = identity == null ? "" : identity.trim();
        this.identity = named.length() == 0 ? this.partner : named;
        this.identityKey = this.identity.toLowerCase(Locale.ROOT);
        this.ownerKey = ownerKey == null ? "" : ownerKey.trim().toLowerCase(Locale.ROOT);
        this.npc = npc;
    }

    /**
     * The tab of a plain channel; null for the whisper channel, whose
     * tabs each name a partner and come from {@link #whisper}. This is
     * the entry the row and the layout hold, one per channel. A channel
     * that is more than one conversation
     * ({@link ChatChannel#isIdentityScoped}) files its lines under a tab
     * per identity — see {@link #of(ChatChannel, String)} — and the one
     * being read is {@link #viewed}.
     */
    public static ChatTab of(ChatChannel channel) {
        return channel == null ? null : PLAIN[channel.ordinal()];
    }

    /**
     * The tab whose lines are shown while this one is on screen. A
     * channel that is one conversation is its own; a scoped channel's
     * row entry stands for whichever of its conversations the chat is
     * being read as, so the lines shown under it are that identity's.
     * The row holds one Faction tab; which faction it is showing
     * follows the identity, and nothing else in the layout has to know.
     */
    public static ChatTab viewed(ChatTab tab) {
        if (tab == null || tab.isWhisper() || tab.channel == null
                || !tab.channel.isIdentityScoped()
                || tab.ownerKey.length() > 0) {
            return tab;
        }
        return of(tab.channel, ClientChatAppearances.viewIdentityKey());
    }

    /**
     * The tab of a plain channel as one identity reads it: a scoped
     * channel is a conversation per identity, so a character in Gondor
     * and a character in Rohan have a Faction tab each and neither
     * shows the other's lines. A channel that is only ever one
     * conversation ignores the identity and answers with its one tab.
     */
    public static ChatTab of(ChatChannel channel, String ownerKey) {
        if (channel == null || !channel.isIdentityScoped()
                || ownerKey == null || ownerKey.trim().length() == 0) {
            return of(channel);
        }
        return new ChatTab(channel, "", "", ownerKey, false);
    }

    /** The whisper tab with an account's own identity, held as this account; null for no name. */
    public static ChatTab whisper(String partner) {
        return whisper(partner, "");
    }

    /**
     * The whisper tab with one identity of an account, held as this
     * account: the person as they were speaking, kept apart from their
     * other characters and from their account. An empty identity is the
     * account's own.
     */
    public static ChatTab whisper(String partner, String identity) {
        return whisper(partner, identity, "");
    }

    /**
     * The whisper tab with one identity of an account, held as one of
     * this player's own identities: {@code ownerKey} is the character's
     * id ({@link #ownerKeyOf}), or empty for the account.
     */
    public static ChatTab whisper(String partner, String identity, String ownerKey) {
        String name = partner == null ? "" : partner.trim();
        return name.length() == 0 ? null
                : new ChatTab(ChatChannel.WHISPER, name, identity, ownerKey, false);
    }

    /** The owner key of a character id; empty for null, the account. */
    public static String ownerKeyOf(UUID characterId) {
        return characterId == null ? "" : characterId.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * The conversation tab with a named NPC: a whisper nobody is on the
     * other end of, so replies are echoed locally rather than sent.
     */
    public static ChatTab npc(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() == 0 ? null
                : new ChatTab(ChatChannel.WHISPER, trimmed, trimmed, "", true);
    }

    public ChatChannel getChannel() { return this.channel; }
    /** The account a whisper is routed to; empty otherwise. */
    public String getPartner() { return this.partner; }
    /**
     * The identity the conversation is with — a character's name, or the
     * account's own. Never empty for a whisper.
     */
    public String getPartnerIdentity() { return this.identity; }
    /**
     * This player's identity the tab is read and spoken as: a
     * character's id, lower-cased, or empty for the account. A whisper
     * carries the identity the conversation is held as; a plain tab
     * carries one only on a channel that is more than one conversation.
     */
    public String getOwnerKey() { return this.ownerKey; }
    /** Whether the conversation is with the account rather than a character. */
    public boolean isAccountConversation() {
        return this.identityKey.equals(this.partnerKey);
    }
    public boolean isWhisper() { return this.channel == ChatChannel.WHISPER; }
    /** Whether the partner is an NPC rather than a player. */
    public boolean isNpc() { return this.npc; }

    /**
     * Stable id: the channel id, {@code channel|own:<character id>} for a
     * channel read as one identity, {@code whisper:Name},
     * {@code whisper:Name|Identity|own:<character id>} or {@code npc:Name}.
     */
    public String id() {
        if (this.npc) {
            return NPC_ID_PREFIX + this.partner;
        }
        if (!isWhisper()) {
            return this.ownerKey.length() == 0 ? this.channel.getId()
                    : this.channel.getId() + IDENTITY_SEPARATOR
                            + OWNER_MARK + this.ownerKey;
        }
        StringBuilder id = new StringBuilder(WHISPER_ID_PREFIX).append(this.partner);
        if (!isAccountConversation() || this.ownerKey.length() > 0) {
            id.append(IDENTITY_SEPARATOR).append(this.identity);
        }
        if (this.ownerKey.length() > 0) {
            id.append(IDENTITY_SEPARATOR).append(OWNER_MARK).append(this.ownerKey);
        }
        return id.toString();
    }

    /** The inverse of {@link #id()}; null for anything unknown. */
    public static ChatTab fromId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(WHISPER_ID_PREFIX)) {
            String rest = trimmed.substring(WHISPER_ID_PREFIX.length());
            String owner = "";
            int lastSeparator = rest.lastIndexOf(IDENTITY_SEPARATOR);
            if (lastSeparator >= 0 && rest.substring(lastSeparator + 1)
                    .toLowerCase(Locale.ROOT).startsWith(OWNER_MARK)) {
                owner = rest.substring(lastSeparator + 1 + OWNER_MARK.length());
                rest = rest.substring(0, lastSeparator);
            }
            int separator = rest.indexOf(IDENTITY_SEPARATOR);
            return separator < 0 ? whisper(rest, "", owner)
                    : whisper(rest.substring(0, separator),
                            rest.substring(separator + 1), owner);
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(NPC_ID_PREFIX)) {
            return npc(trimmed.substring(NPC_ID_PREFIX.length()));
        }
        String owner = "";
        String channelPart = trimmed;
        int separator = trimmed.indexOf(IDENTITY_SEPARATOR);
        if (separator >= 0) {
            String rest = trimmed.substring(separator + 1);
            if (!rest.toLowerCase(Locale.ROOT).startsWith(OWNER_MARK)) {
                return null;
            }
            owner = rest.substring(OWNER_MARK.length());
            channelPart = trimmed.substring(0, separator);
        }
        ChatChannel channel = ChatChannel.fromId(channelPart);
        if (channel == null || channel == ChatChannel.WHISPER) {
            return null;
        }
        return owner.length() == 0 ? of(channel) : of(channel, owner);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatTab)) {
            return false;
        }
        ChatTab tab = (ChatTab)other;
        return tab.channel == this.channel && tab.npc == this.npc
                && tab.partnerKey.equals(this.partnerKey)
                && tab.identityKey.equals(this.identityKey)
                && tab.ownerKey.equals(this.ownerKey);
    }

    @Override
    public int hashCode() {
        return (((this.channel.ordinal() * 31 + this.partnerKey.hashCode())
                * 31 + this.identityKey.hashCode()) * 31 + this.ownerKey.hashCode()) * 2
                + (this.npc ? 1 : 0);
    }

    @Override
    public String toString() {
        return id();
    }
}
