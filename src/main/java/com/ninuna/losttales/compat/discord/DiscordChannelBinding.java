package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;

/**
 * One game channel tied to one Discord channel: which channel, for the
 * Faction channel which faction, the Discord channel the bot reads, the
 * webhook the bridge posts through, and which way lines cross. The key
 * names the game channel — {@code all}, {@code ooc},
 * {@code faction:gondor} — and several bindings may share it, one per
 * Discord channel the game channel goes to; the id tells them apart,
 * the key for the first and {@code ooc#2} for the next, and is what the
 * log and the bridge's memory of which post went where carry.
 */
public final class DiscordChannelBinding {
    /** Separates the channel id from a faction scope in a key. */
    static final char SCOPE_SEPARATOR = ':';
    /** Separates the key from the ordinal in a binding's id. */
    static final char ORDINAL_SEPARATOR = '#';

    private final ChatChannel channel;
    private final String factionScope;
    private final String discordChannelId;
    private final String webhookUrl;
    private final DiscordBridgeDirection direction;
    private final String id;

    DiscordChannelBinding(ChatChannel channel, String factionScope,
                          String discordChannelId, String webhookUrl,
                          DiscordBridgeDirection direction) {
        this(channel, factionScope, discordChannelId, webhookUrl, direction,
                null);
    }

    private DiscordChannelBinding(ChatChannel channel, String factionScope,
                                  String discordChannelId, String webhookUrl,
                                  DiscordBridgeDirection direction, String id) {
        if (channel == null || direction == null) {
            throw new IllegalArgumentException("channel and direction are required");
        }
        this.channel = channel;
        this.factionScope = factionScope == null ? "" : factionScope.trim();
        this.discordChannelId = discordChannelId == null ? "" : discordChannelId.trim();
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.direction = direction;
        this.id = id == null || id.length() == 0 ? key() : id;
    }

    /**
     * What names this binding alone: its key, or the key and an ordinal
     * where the same game channel is bound more than once.
     */
    public String id() {
        return this.id;
    }

    public ChatChannel getChannel() {
        return this.channel;
    }

    /** The faction a Faction binding is for; empty for any other channel. */
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

    public String key() {
        return keyOf(this.channel, this.factionScope);
    }

    public boolean sendsToDiscord() {
        return this.direction.sendsToDiscord() && this.webhookUrl.length() > 0;
    }

    public boolean readsFromDiscord() {
        return this.direction.readsFromDiscord() && this.discordChannelId.length() > 0;
    }

    DiscordChannelBinding withDirection(DiscordBridgeDirection replacement) {
        return replacement == this.direction ? this : new DiscordChannelBinding(
                this.channel, this.factionScope, this.discordChannelId,
                this.webhookUrl, replacement, this.id);
    }

    /** The same binding under the id its place among its key's bindings gives it. */
    DiscordChannelBinding withOrdinal(int ordinal) {
        String named = ordinal <= 1 ? key()
                : key() + ORDINAL_SEPARATOR + ordinal;
        return named.equals(this.id) ? this : new DiscordChannelBinding(
                this.channel, this.factionScope, this.discordChannelId,
                this.webhookUrl, this.direction, named);
    }

    static String keyOf(ChatChannel channel, String factionScope) {
        String scope = factionScope == null ? "" : factionScope.trim();
        return scope.length() == 0 ? channel.getId()
                : channel.getId() + SCOPE_SEPARATOR + scope;
    }

    @Override
    public String toString() {
        return this.id + "=" + this.direction.name();
    }
}
