package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.server.ChatChannelPolicy;
import com.ninuna.losttales.chat.ChatMessageOrigin;

/**
 * The one rule for what may leave the game: a line a player typed, in a
 * channel that may be bridged and that somebody in the game may read. A
 * line the bridge itself carried in does not go out this way: the bridge
 * carries it on to the other Discord channels of its game channel itself,
 * never back into the one it came from, and never reads a post of its
 * own webhooks, so a message can never go round. A private channel
 * (a party, a whisper, a console) is refused here before any binding is
 * asked, so no configuration can carry it. A channel only some players may
 * read, Operator Chat among them, may be linked (Nils, 2026-09-28, D1 b):
 * on Discord the channel's own permissions then decide who reads it, and
 * whoever links it is told so ({@link #isLimitedInGame}).
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
     * its own word, and not closed to every reader by its gate. Read
     * against the gates in force, so closing a bridged channel stops the
     * bridge for it until the gate opens again.
     */
    public static boolean isOpenToTheBridge(ChatChannel channel) {
        return channel != null && channel.isBridgeable()
                && !ChatChannelGates.current().gateOf(channel).isReadClosed();
    }

    /**
     * Whether only some players may read the channel in the game: staff
     * talk, or a channel whose gate asks for a role to read. Linked, such
     * a channel is read on Discord by whoever can see the Discord channel.
     */
    public static boolean isLimitedInGame(ChatChannel channel) {
        if (channel == null) {
            return false;
        }
        ChatChannelGates gates = ChatChannelGates.current();
        return ChatChannelPolicy.staffOnly(channel, gates)
                || !gates.gateOf(channel).getReadRoles().isEmpty();
    }
}
