package com.ninuna.losttales.compat.discord;

import java.util.Locale;

/** Which way lines cross a Discord binding. */
public enum DiscordBridgeDirection {
    DISABLED(false, false),
    GAME_TO_DISCORD(true, false),
    DISCORD_TO_GAME(false, true),
    BIDIRECTIONAL(true, true);

    private final boolean sends;
    private final boolean reads;

    DiscordBridgeDirection(boolean sends, boolean reads) {
        this.sends = sends;
        this.reads = reads;
    }

    /** Whether game lines of the bound channel are posted to Discord. */
    public boolean sendsToDiscord() {
        return this.sends;
    }

    /** Whether Discord messages are delivered into the bound channel. */
    public boolean readsFromDiscord() {
        return this.reads;
    }

    /** The same direction with its Discord-to-game half taken away. */
    public DiscordBridgeDirection withoutReads() {
        return of(this.sends, false);
    }

    /** The same direction with its game-to-Discord half taken away. */
    public DiscordBridgeDirection withoutSends() {
        return of(false, this.reads);
    }

    public static DiscordBridgeDirection of(boolean sends, boolean reads) {
        if (sends && reads) {
            return BIDIRECTIONAL;
        }
        if (sends) {
            return GAME_TO_DISCORD;
        }
        return reads ? DISCORD_TO_GAME : DISABLED;
    }

    /** The direction named, case-insensitively; null for a name that is none. */
    public static DiscordBridgeDirection parse(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (DiscordBridgeDirection direction : values()) {
            if (direction.name().equals(wanted)) {
                return direction;
            }
        }
        return null;
    }
}
