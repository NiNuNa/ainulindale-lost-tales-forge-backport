package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.Locale;

/**
 * What a tab stands for: a channel, and for a whisper the account the
 * conversation is with — or the NPC, since LOTR speech is addressed to
 * one player and reads as a whisper from the NPC. Every plain channel is
 * one tab; every whisper partner is one more, all of them on the
 * {@link ChatChannel#WHISPER} channel, NPCs kept apart from players of
 * the same name. Tabs are values — equal when channel, partner and kind
 * agree, partners compared case-insensitively — and are what windows
 * hold, lines are filed under, and the selection points at.
 */
public final class ChatTab {
    private static final String WHISPER_ID_PREFIX = "whisper:";
    private static final String NPC_ID_PREFIX = "npc:";
    private static final ChatTab[] PLAIN = new ChatTab[ChatChannel.values().length];

    static {
        for (ChatChannel channel : ChatChannel.values()) {
            PLAIN[channel.ordinal()] = new ChatTab(channel, "");
        }
    }

    private final ChatChannel channel;
    private final String partner;
    private final String partnerKey;
    private final boolean npc;

    private ChatTab(ChatChannel channel, String partner) {
        this(channel, partner, false);
    }

    private ChatTab(ChatChannel channel, String partner, boolean npc) {
        this.channel = channel;
        this.partner = partner == null ? "" : partner.trim();
        this.partnerKey = this.partner.toLowerCase(Locale.ROOT);
        this.npc = npc;
    }

    /** The tab of a plain channel. */
    public static ChatTab of(ChatChannel channel) {
        return channel == null ? null : PLAIN[channel.ordinal()];
    }

    /** The whisper tab with the named account; null for no name. */
    public static ChatTab whisper(String partner) {
        String name = partner == null ? "" : partner.trim();
        return name.length() == 0 ? null
                : new ChatTab(ChatChannel.WHISPER, name);
    }

    /**
     * The conversation tab with a named NPC: a whisper nobody is on the
     * other end of, so replies are echoed locally rather than sent.
     */
    public static ChatTab npc(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() == 0 ? null
                : new ChatTab(ChatChannel.WHISPER, trimmed, true);
    }

    public ChatChannel getChannel() { return this.channel; }
    /** The whisper partner's name as first seen; empty otherwise. */
    public String getPartner() { return this.partner; }
    public boolean isWhisper() { return this.channel == ChatChannel.WHISPER; }
    /** Whether the partner is an NPC rather than a player. */
    public boolean isNpc() { return this.npc; }

    /** Stable id: the channel id, {@code whisper:Name} or {@code npc:Name}. */
    public String id() {
        if (this.npc) {
            return NPC_ID_PREFIX + this.partner;
        }
        return isWhisper() ? WHISPER_ID_PREFIX + this.partner
                : this.channel.getId();
    }

    /** The inverse of {@link #id()}; null for anything unknown. */
    public static ChatTab fromId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(WHISPER_ID_PREFIX)) {
            return whisper(trimmed.substring(WHISPER_ID_PREFIX.length()));
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
                && tab.partnerKey.equals(this.partnerKey);
    }

    @Override
    public int hashCode() {
        return (this.channel.ordinal() * 31 + this.partnerKey.hashCode()) * 2
                + (this.npc ? 1 : 0);
    }

    @Override
    public String toString() {
        return id();
    }
}
