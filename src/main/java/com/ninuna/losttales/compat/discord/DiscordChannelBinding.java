package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;

/**
 * One game channel tied to one Discord channel: which channel, for the
 * Faction channel which faction, the Discord channel the bot reads, the
 * webhook the bridge posts through, and which way lines cross. The key
 * is what everything else carries about it — {@code all}, {@code discord},
 * {@code faction:gondor} — so a binding is named the same in the config,
 * the log and the bridge's memory of which post went where.
 */
public final class DiscordChannelBinding {
    /** Separates the channel id from a faction scope in a key. */
    static final char SCOPE_SEPARATOR = ':';

    private final ChatChannel channel;
    private final String factionScope;
    private final String discordChannelId;
    private final String webhookUrl;
    private final DiscordBridgeDirection direction;

    DiscordChannelBinding(ChatChannel channel, String factionScope,
                          String discordChannelId, String webhookUrl,
                          DiscordBridgeDirection direction) {
        if (channel == null || direction == null) {
            throw new IllegalArgumentException("channel and direction are required");
        }
        this.channel = channel;
        this.factionScope = factionScope == null ? "" : factionScope.trim();
        this.discordChannelId = discordChannelId == null ? "" : discordChannelId.trim();
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.direction = direction;
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

    /** Whether this is the game's own Discord channel, the one both ways. */
    public boolean isDiscordChannel() {
        return this.channel == ChatChannel.DISCORD;
    }

    DiscordChannelBinding withDirection(DiscordBridgeDirection replacement) {
        return replacement == this.direction ? this : new DiscordChannelBinding(
                this.channel, this.factionScope, this.discordChannelId,
                this.webhookUrl, replacement);
    }

    static String keyOf(ChatChannel channel, String factionScope) {
        String scope = factionScope == null ? "" : factionScope.trim();
        return scope.length() == 0 ? channel.getId()
                : channel.getId() + SCOPE_SEPARATOR + scope;
    }

    @Override
    public String toString() {
        return key() + "=" + this.direction.name();
    }
}
