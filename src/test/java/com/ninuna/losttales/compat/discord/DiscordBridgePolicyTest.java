package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageOrigin;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class DiscordBridgePolicyTest {

    @Test
    public void onlyAPlayersLineInABridgeableChannelLeavesTheGame() {
        for (ChatChannel channel : ChatChannel.values()) {
            assertEquals(channel.name(), channel.isBridgeable(),
                    DiscordBridgePolicy.relaysOutbound(ChatMessageOrigin.PLAYER, channel));
            assertFalse("a line from Discord never goes back out: " + channel,
                    DiscordBridgePolicy.relaysOutbound(ChatMessageOrigin.DISCORD, channel));
            assertEquals(channel.name(), channel.isBridgeable(),
                    DiscordBridgePolicy.acceptsInbound(channel));
        }
        assertFalse(DiscordBridgePolicy.relaysOutbound(ChatMessageOrigin.PLAYER, null));
        assertFalse(DiscordBridgePolicy.acceptsInbound(null));
    }
}
