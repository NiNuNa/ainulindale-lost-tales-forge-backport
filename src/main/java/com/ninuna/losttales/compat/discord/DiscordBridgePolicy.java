package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatMessageOrigin;

/**
 * The one rule for what may leave the game: a line a player typed, in a
 * channel that may be bridged and that everyone in the game may read. A
 * line the bridge itself carried in is never sent back out, whatever
 * channel it landed in, so a message can never go round; a private
 * channel is refused here before any binding is asked, so no
 * configuration can carry it; and a channel whose read side asks for a
 * role is refused too, since Discord has no roles of ours to hold — what
 * only role holders may read in the game is not published outside it,
 * and nothing unauthenticated is spoken into it.
 */
public final class DiscordBridgePolicy {

    private DiscordBridgePolicy() {}

    public static boolean relaysOutbound(ChatMessageOrigin origin, ChatChannel channel) {
        return origin == ChatMessageOrigin.PLAYER && isOpenToTheBridge(channel);
    }

    /** Whether a Discord message may be delivered into the channel at all. */
    public static boolean acceptsInbound(ChatChannel channel) {
        return isOpenToTheBridge(channel);
    }

    /**
     * Whether the channel may be carried at all right now: bridgeable by
     * its own word, and readable by everyone in the game. Read against
     * the gates in force, so gating a bridged channel stops the bridge
     * for it until the gate is lifted.
     */
    public static boolean isOpenToTheBridge(ChatChannel channel) {
        if (channel == null || !channel.isBridgeable()) {
            return false;
        }
        ChatChannelGates.Gate gate = ChatChannelGates.current().gateOf(channel);
        return !gate.isReadClosed() && gate.getReadRoles().isEmpty();
    }
}
