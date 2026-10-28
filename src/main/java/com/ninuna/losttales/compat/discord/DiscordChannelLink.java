package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatCodeNames;

/**
 * One game channel tied to one Discord channel: which channel, for the
 * Faction channel which faction, the Discord channel the bot reads, the
 * webhook the bridge posts through, which way lines cross, and the
 * language its posts and answers are worded in, empty for the server's
 * default ({@code discord.language}, DISC-30). The key
 * is the game channel's code name ({@link ChatCodeNames}): {@code global},
 * {@code ooc}, or a faction's, {@code gondor}. Several links may share
 * it, one per Discord channel the game channel goes to; the id tells them
 * apart, the key for the first and {@code ooc#2} for the next, and is
 * what the log and the bridge's memory of which post went where carry.
 */
public final class DiscordChannelLink {
    /** Separates the key from the ordinal in a link's id. */
    static final char ORDINAL_SEPARATOR = '#';

    private final ChatChannel channel;
    private final String factionScope;
    private final String discordChannelId;
    private final String webhookUrl;
    private final DiscordBridgeDirection direction;
    private final String language;
    private final String id;

    DiscordChannelLink(ChatChannel channel, String factionScope,
                          String discordChannelId, String webhookUrl,
                          DiscordBridgeDirection direction, String language) {
        this(channel, factionScope, discordChannelId, webhookUrl, direction,
                language, null);
    }

    private DiscordChannelLink(ChatChannel channel, String factionScope,
                                  String discordChannelId, String webhookUrl,
                                  DiscordBridgeDirection direction, String language,
                                  String id) {
        if (channel == null || direction == null) {
            throw new IllegalArgumentException("channel and direction are required");
        }
        this.channel = channel;
        this.factionScope = factionScope == null ? "" : factionScope.trim();
        this.discordChannelId = discordChannelId == null ? "" : discordChannelId.trim();
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.direction = direction;
        this.language = language == null ? "" : language;
        this.id = id == null || id.length() == 0 ? key() : id;
    }

    /**
     * What names this link alone: its key, or the key and an ordinal
     * where the same game channel is linked more than once.
     */
    public String id() {
        return this.id;
    }

    public ChatChannel getChannel() {
        return this.channel;
    }

    /** The faction a Faction link is for; empty for any other channel. */
    public String getFactionScope() {
        return this.factionScope;
    }

    public String getDiscordChannelId() {
        return this.discordChannelId;
    }

    public String getWebhookUrl() {
        return this.webhookUrl;
    }

    public DiscordBridgeDirection getDirection() {
        return this.direction;
    }

    /** The language chosen for this link, spelled the game's way; empty for the server's default. */
    public String getLanguage() {
        return this.language;
    }

    /** The language this link writes in: its own, else {@code serverDefault}. */
    public String languageOr(String serverDefault) {
        return this.language.length() > 0 ? this.language
                : serverDefault == null ? "" : serverDefault;
    }

    public String key() {
        return keyOf(this.channel, this.factionScope);
    }

    public boolean sendsToDiscord() {
        return this.direction.sendsToDiscord() && this.webhookUrl.length() > 0;
    }

    public boolean readsFromDiscord() {
        return this.direction.readsFromDiscord() && this.discordChannelId.length() > 0;
    }

    DiscordChannelLink withDirection(DiscordBridgeDirection replacement) {
        return replacement == this.direction ? this : new DiscordChannelLink(
                this.channel, this.factionScope, this.discordChannelId,
                this.webhookUrl, replacement, this.language, this.id);
    }

    /** The same link under the id its place among its key's links gives it. */
    DiscordChannelLink withOrdinal(int ordinal) {
        String named = ordinal <= 1 ? key()
                : key() + ORDINAL_SEPARATOR + ordinal;
        return named.equals(this.id) ? this : new DiscordChannelLink(
                this.channel, this.factionScope, this.discordChannelId,
                this.webhookUrl, this.direction, this.language, named);
    }

    /** The game channel's code name; null for a faction nobody knows. */
    static String keyOf(ChatChannel channel, String factionScope) {
        return ChatCodeNames.of(channel, factionScope);
    }

    @Override
    public String toString() {
        return this.id + "=" + this.direction.name();
    }
}
