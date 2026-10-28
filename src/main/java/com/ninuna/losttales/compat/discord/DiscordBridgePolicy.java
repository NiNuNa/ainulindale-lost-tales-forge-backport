package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.server.ChatChannelPolicy;

/**
 * The one rule for what may leave the game: a line a player typed, in a
 * channel that may be linked and that somebody in the game may read. A
 * line the bridge itself carried in does not go out this way: the bridge
 * carries it on to the other Discord channels of its game channel itself,
 * never back into the one it came from, and never reads a post of its
 * own webhooks, so a message can never go round. A private channel
 * (a fellowship, a whisper, a console) and a channel heard only nearby
 * (Proximity, a server's own with {@code rule:proximity}) are refused here
 * before any link is asked, so no configuration can carry them
 * (DISC-03). A channel only some players may read, Operator Chat among
 * them, may be linked: on Discord the channel's own permissions then
 * decide who reads it, and whoever links it is told so
 * ({@link #isLimitedInGame}).
 */
public final class DiscordBridgePolicy {

    private DiscordBridgePolicy() {}

    /**
     * Whether a line a player typed, or their typing, may be posted to
     * the channel's Discord links. A line from Discord takes the bridge's
     * own way and is never asked about here.
     */
    public static boolean relaysOutbound(ChatChannel channel) {
        return isOpenToTheBridge(channel);
    }

    /** Whether a Discord message may be delivered into the channel at all. */
    public static boolean acceptsInbound(ChatChannel channel) {
        return isOpenToTheBridge(channel);
    }

    /**
     * Whether the channel may ever be linked: bridgeable by its own word,
     * and heard by more than those standing near the speaker. Nobody on
     * Discord stands near anyone, so a channel heard nearby never crosses,
     * either way, whatever its config says.
     */
    public static boolean mayCross(ChatChannel channel) {
        return channel != null && channel.isBridgeable() && !isHeardNearby(channel);
    }

    /** Whether only those near the speaker hear the channel's lines. */
    public static boolean isHeardNearby(ChatChannel channel) {
        return channel != null && channel.getRecipientRule() == ChatRecipientRule.PROXIMITY;
    }

    /**
     * Whether the channel may be carried at all right now: one that may
     * cross ({@link #mayCross}), not closed to every reader by its gate.
     * Read against the gates in force, so closing a bridged channel stops
     * the bridge for it until the gate opens again.
     */
    public static boolean isOpenToTheBridge(ChatChannel channel) {
        return mayCross(channel)
                && !ChatChannelGates.current().gateOf(channel).isReadClosed();
    }

    /**
     * Which way a link of the channel carries when the operator names no
     * direction (DISC-29): an in-character channel takes game lines to
     * Discord only, since a Discord member speaks out of character; an
     * out-of-character one carries both ways.
     */
    public static DiscordBridgeDirection defaultDirection(ChatChannel channel) {
        return channel != null
                && channel.getPresentation() == ChatPresentationMode.OUT_OF_CHARACTER
                ? DiscordBridgeDirection.BIDIRECTIONAL
                : DiscordBridgeDirection.GAME_TO_DISCORD;
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
