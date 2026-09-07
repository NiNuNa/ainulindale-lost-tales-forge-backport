package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatMessageOrigin;
import com.ninuna.losttales.chat.ChatRecipientRule;
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
 * all, and the gates in force say whether it is open to everyone in the
 * game right now; the bridge needs both, because a Discord member holds
 * no role and passes no gate.
 */
public final class DiscordBridgePolicyTest {

    @After
    public void tearDown() {
        ChatChannelGates.resetToDefaults();
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
    public void anUngatedOperatorChannelIsNotOpenToTheBridge() {
        // An operators-routed channel with no gate written for it is
        // operator-only in game. Reading the empty gate as "everyone may"
        // would post staff talk to Discord and let anyone there answer.
        for (ChatChannel channel : ChatChannel.values()) {
            boolean staffOnly = channel.getRecipientRule()
                    == ChatRecipientRule.OPERATORS
                    && !ChatChannelGates.current().hasEntry(channel);
            boolean expected = channel.isBridgeable() && !staffOnly;
            assertEquals(channel.getId(), expected,
                    DiscordBridgePolicy.relaysOutbound(
                            ChatMessageOrigin.PLAYER, channel));
            assertEquals(channel.getId(), expected,
                    DiscordBridgePolicy.acceptsInbound(channel));
        }
    }

    @Test
    public void anOperatorChannelItsServerOpensIsCarried() {
        ChatChannel admin = ChatChannel.ADMIN;
        assertTrue("this test needs a bridgeable operator channel",
                admin.isBridgeable());
        assertFalse(DiscordBridgePolicy.acceptsInbound(admin));

        // A gate that names nobody on either side is a server saying it
        // wants the channel open.
        Map<ChatChannel, ChatChannelGates.Gate> gates =
                new HashMap<ChatChannel, ChatChannelGates.Gate>();
        gates.put(admin, new ChatChannelGates.Gate(
                Collections.<String>emptySet(), Collections.<String>emptySet()));
        ChatChannelGates.install(ChatChannelGates.of(gates));

        assertTrue(DiscordBridgePolicy.acceptsInbound(admin));
    }

    @Test
    public void aGatedChannelIsClosedToTheBridgeWhileItIsGated() {
        ChatChannel ooc = ChatChannel.OOC;
        assertTrue("this test needs a bridgeable open channel",
                DiscordBridgePolicy.acceptsInbound(ooc));

        Map<ChatChannel, ChatChannelGates.Gate> gates =
                new HashMap<ChatChannel, ChatChannelGates.Gate>();
        gates.put(ooc, new ChatChannelGates.Gate(
                Collections.singleton("mod"), Collections.singleton("mod")));
        ChatChannelGates.install(ChatChannelGates.of(gates));

        assertFalse("a Discord member holds no role and passes no gate",
                DiscordBridgePolicy.acceptsInbound(ooc));
    }
}
