package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageOrigin;

/**
 * The one rule for what may leave the game: a line a player typed, in
 * a channel that may be bridged. A line the bridge itself carried in is
 * never sent back out, whatever channel it landed in, so a message can
 * never go round; and a private channel is refused here before any
 * binding is asked, so no configuration can carry it.
 */
public final class DiscordBridgePolicy {

    private DiscordBridgePolicy() {}

    public static boolean relaysOutbound(ChatMessageOrigin origin, ChatChannel channel) {
        return origin == ChatMessageOrigin.PLAYER && channel != null
                && channel.isBridgeable();
    }

    /** Whether a Discord message may be delivered into the channel at all. */
    public static boolean acceptsInbound(ChatChannel channel) {
        return channel != null && channel.isBridgeable();
    }
}
