package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Locale;

/**
 * What a tab stands for: a channel, and for a whisper the <em>identity</em>
 * the conversation is with — or the NPC, since LOTR speech is addressed
 * to one player and reads as a whisper from the NPC.
 *
 * <p>A conversation is with a person as they present themselves, not
 * with the account behind them: whispering someone speaking as Aldric
 * and whispering the same player speaking as Beren are two
 * conversations, and neither is the one with their account. The account
 * is still carried — it is who the message is routed to, and it is
 * always reachable whatever they happen to be playing — but it is the
 * identity that names the tab and keeps the threads apart.</p>
 *
 * <p>Every plain channel is one tab; every whisper identity is one more,
 * all of them on the {@link ChatChannel#WHISPER} channel, NPCs kept
 * apart from players of the same name. Tabs are values — equal when
 * channel, account, identity and kind agree, names compared
 * case-insensitively — and are what windows hold, lines are filed
 * under, and the selection points at.</p>
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
    private static final ChatTab[] PLAIN = new ChatTab[ChatChannel.values().length];

    static {
        for (ChatChannel channel : ChatChannel.values()) {
            PLAIN[channel.ordinal()] = new ChatTab(channel, "");
        }
    }

    private final ChatChannel channel;
    private final String partner;
    private final String partnerKey;
    private final String identity;
    private final String identityKey;
    private final boolean npc;

    private ChatTab(ChatChannel channel, String partner) {
        this(channel, partner, partner, false);
    }

    private ChatTab(ChatChannel channel, String partner, String identity,
                    boolean npc) {
        this.channel = channel;
        this.partner = partner == null ? "" : partner.trim();
        this.partnerKey = this.partner.toLowerCase(Locale.ROOT);
        String named = identity == null ? "" : identity.trim();
        this.identity = named.length() == 0 ? this.partner : named;
        this.identityKey = this.identity.toLowerCase(Locale.ROOT);
        this.npc = npc;
    }

    /** The tab of a plain channel. */
    public static ChatTab of(ChatChannel channel) {
        return channel == null ? null : PLAIN[channel.ordinal()];
    }

    /** The whisper tab with an account's own identity; null for no name. */
    public static ChatTab whisper(String partner) {
        return whisper(partner, "");
    }

    /**
     * The whisper tab with one identity of an account: the person as
     * they were speaking, kept apart from their other characters and
     * from their account. An empty identity is the account's own.
     */
    public static ChatTab whisper(String partner, String identity) {
        String name = partner == null ? "" : partner.trim();
        return name.length() == 0 ? null
                : new ChatTab(ChatChannel.WHISPER, name, identity, false);
    }

    /**
     * The conversation tab with a named NPC: a whisper nobody is on the
     * other end of, so replies are echoed locally rather than sent.
     */
    public static ChatTab npc(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() == 0 ? null
                : new ChatTab(ChatChannel.WHISPER, trimmed, trimmed, true);
    }

    public ChatChannel getChannel() { return this.channel; }
    /** The account a whisper is routed to; empty otherwise. */
    public String getPartner() { return this.partner; }
    /**
     * The identity the conversation is with — a character's name, or the
     * account's own. Never empty for a whisper.
     */
    public String getPartnerIdentity() { return this.identity; }
    /** Whether the conversation is with the account rather than a character. */
    public boolean isAccountConversation() {
        return this.identityKey.equals(this.partnerKey);
    }
    public boolean isWhisper() { return this.channel == ChatChannel.WHISPER; }
    /** Whether the partner is an NPC rather than a player. */
    public boolean isNpc() { return this.npc; }

    /** Stable id: the channel id, {@code whisper:Name} or {@code npc:Name}. */
    public String id() {
        if (this.npc) {
            return NPC_ID_PREFIX + this.partner;
        }
        if (!isWhisper()) {
            return this.channel.getId();
        }
        return WHISPER_ID_PREFIX + this.partner
                + (isAccountConversation() ? ""
                        : IDENTITY_SEPARATOR + this.identity);
    }

    /** The inverse of {@link #id()}; null for anything unknown. */
    public static ChatTab fromId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(WHISPER_ID_PREFIX)) {
            String rest = trimmed.substring(WHISPER_ID_PREFIX.length());
            int separator = rest.indexOf(IDENTITY_SEPARATOR);
            return separator < 0 ? whisper(rest)
                    : whisper(rest.substring(0, separator),
                            rest.substring(separator + 1));
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(NPC_ID_PREFIX)) {
            return npc(trimmed.substring(NPC_ID_PREFIX.length()));
        }
        ChatChannel channel = ChatChannel.fromId(trimmed);
        return channel == null || channel == ChatChannel.WHISPER ? null
                : of(channel);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatTab)) {
            return false;
        }
        ChatTab tab = (ChatTab)other;
        return tab.channel == this.channel && tab.npc == this.npc
                && tab.partnerKey.equals(this.partnerKey)
                && tab.identityKey.equals(this.identityKey);
    }

    @Override
    public int hashCode() {
        return ((this.channel.ordinal() * 31 + this.partnerKey.hashCode())
                * 31 + this.identityKey.hashCode()) * 2
                + (this.npc ? 1 : 0);
    }

    @Override
    public String toString() {
        return id();
    }
}
