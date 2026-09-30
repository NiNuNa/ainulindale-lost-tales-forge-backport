package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatMessageOrigin;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the bridge may carry. A channel says whether it is bridgeable at
 * all, and the gates in force say whether anybody may read it right now.
 * A channel only some players read may be linked: on Discord, the Discord
 * channel's own permissions decide who reads it.
 */
public final class DiscordBridgePolicyTest {

    @After
    public void tearDown() {
        ChatChannelGates.install(ChatChannelGates.defaults());
    }

    @Test
    public void aLineFromDiscordNeverGoesBackOut() {
        for (ChatChannel channel : ChatChannel.values()) {
            assertFalse("a line from Discord never goes back out: " + channel,
                    DiscordBridgePolicy.relaysOutbound(
                            ChatMessageOrigin.DISCORD, channel));
        }
        assertFalse(DiscordBridgePolicy.relaysOutbound(
                ChatMessageOrigin.PLAYER, null));
        assertFalse(DiscordBridgePolicy.acceptsInbound(null));
    }

    @Test
    public void aChannelOnlySomeMayReadMayBeLinkedAndSaysSo() {
        for (ChatChannel channel : ChatChannel.values()) {
            assertEquals(channel.getId(), channel.isBridgeable(),
                    DiscordBridgePolicy.relaysOutbound(
                            ChatMessageOrigin.PLAYER, channel));
            assertEquals(channel.getId(), channel.isBridgeable(),
                    DiscordBridgePolicy.acceptsInbound(channel));
        }
        assertTrue(DiscordBridgePolicy.isLimitedInGame(ChatChannel.OPERATOR));
        assertFalse(DiscordBridgePolicy.isLimitedInGame(ChatChannel.GLOBAL));
    }

    @Test
    public void anOperatorChannelItsServerOpensIsNoLongerLimited() {
        ChatChannel admin = ChatChannel.OPERATOR;
        Map<ChatChannel, ChatChannelGates.Gate> gates =
                new HashMap<ChatChannel, ChatChannelGates.Gate>();
        gates.put(admin, new ChatChannelGates.Gate(
                Collections.<String>emptySet(), Collections.<String>emptySet()));
        ChatChannelGates.install(ChatChannelGates.of(gates));

        assertTrue(DiscordBridgePolicy.acceptsInbound(admin));
        assertFalse(DiscordBridgePolicy.isLimitedInGame(admin));
    }

    @Test
    public void aRoleGateLimitsAndAClosedGateStopsTheBridge() {
        ChatChannel ooc = ChatChannel.OOC;
        Map<ChatChannel, ChatChannelGates.Gate> gates =
                new HashMap<ChatChannel, ChatChannelGates.Gate>();
        gates.put(ooc, new ChatChannelGates.Gate(
                Collections.singleton("mod"), Collections.singleton("mod")));
        ChatChannelGates.install(ChatChannelGates.of(gates));
        assertTrue(DiscordBridgePolicy.acceptsInbound(ooc));
        assertTrue(DiscordBridgePolicy.isLimitedInGame(ooc));

        gates.put(ooc, new ChatChannelGates.Gate(
                Collections.<String>emptySet(), Collections.<String>emptySet(), true, true));
        ChatChannelGates.install(ChatChannelGates.of(gates));
        assertFalse("nobody reads it, so nothing crosses",
                DiscordBridgePolicy.acceptsInbound(ooc));
    }
}
