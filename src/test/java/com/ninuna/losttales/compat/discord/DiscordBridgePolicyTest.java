package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatChannelDescriptor;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatRecipientRule;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the bridge may carry. A channel says whether it is bridgeable at
 * all, one heard only nearby never crosses (DISC-03), and the gates in
 * force say whether anybody may read it right now. A channel only some
 * players read may be linked: on Discord, the Discord channel's own
 * permissions decide who reads it. A link named no direction carries an
 * in-character channel one way, an out-of-character one both (DISC-29).
 */
public final class DiscordBridgePolicyTest {

    @After
    public void tearDown() {
        ChatChannelGates.install(ChatChannelGates.defaults());
        ChatChannel.resetToBuiltIn();
    }

    /** A channel a server's {@code channels.cfg} defines, marked {@code bridge:true}. */
    private static ChatChannel defined(String id, ChatRecipientRule rule,
                                       ChatPresentationMode presentation) {
        ChatChannel.installDefined(Collections.singletonList(new ChatChannelDescriptor(
                id, id, presentation, rule, ChatChannelAccess.NONE, 0xFFFFFF, true,
                ChatChannelScope.NONE)), null);
        return ChatChannel.fromId(id);
    }

    @Test
    public void noChannelCrossesNothing() {
        assertFalse(DiscordBridgePolicy.relaysOutbound(null));
        assertFalse(DiscordBridgePolicy.acceptsInbound(null));
        assertFalse(DiscordBridgePolicy.mayCross(null));
    }

    @Test
    public void aChannelOnlySomeMayReadMayBeLinkedAndSaysSo() {
        for (ChatChannel channel : ChatChannel.values()) {
            boolean crosses = channel.isBridgeable() && channel != ChatChannel.PROXIMITY;
            assertEquals(channel.getId(), crosses, DiscordBridgePolicy.mayCross(channel));
            assertEquals(channel.getId(), crosses, DiscordBridgePolicy.relaysOutbound(channel));
            assertEquals(channel.getId(), crosses, DiscordBridgePolicy.acceptsInbound(channel));
        }
        assertTrue(DiscordBridgePolicy.isLimitedInGame(ChatChannel.OPERATOR));
        assertFalse(DiscordBridgePolicy.isLimitedInGame(ChatChannel.GLOBAL));
    }

    /** Nobody on Discord stands near anyone: Proximity crosses neither way. */
    @Test
    public void proximityNeverCrosses() {
        assertTrue(ChatChannel.PROXIMITY.isBridgeable());
        assertTrue(DiscordBridgePolicy.isHeardNearby(ChatChannel.PROXIMITY));
        assertFalse(DiscordBridgePolicy.mayCross(ChatChannel.PROXIMITY));
        assertFalse(DiscordBridgePolicy.isOpenToTheBridge(ChatChannel.PROXIMITY));
        assertFalse(DiscordBridgePolicy.relaysOutbound(ChatChannel.PROXIMITY));
        assertFalse(DiscordBridgePolicy.acceptsInbound(ChatChannel.PROXIMITY));
    }

    /** A server's channel heard nearby crosses no more than Proximity, whatever its bridge:true. */
    @Test
    public void aServerChannelHeardNearbyNeverCrosses() {
        ChatChannel tavern = defined("tavern", ChatRecipientRule.PROXIMITY,
                ChatPresentationMode.IN_CHARACTER);
        assertTrue(tavern.isBridgeable());
        assertTrue(DiscordBridgePolicy.isHeardNearby(tavern));
        assertFalse(DiscordBridgePolicy.relaysOutbound(tavern));
        assertFalse(DiscordBridgePolicy.acceptsInbound(tavern));

        ChatChannel square = defined("square", ChatRecipientRule.EVERYONE,
                ChatPresentationMode.IN_CHARACTER);
        assertTrue("one everyone hears may cross", DiscordBridgePolicy.mayCross(square));
    }

    /** An in-character channel links one way unless asked; an out-of-character one both ways. */
    @Test
    public void aLinkWithNoDirectionFollowsTheChannelsKind() {
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                DiscordBridgePolicy.defaultDirection(ChatChannel.GLOBAL));
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                DiscordBridgePolicy.defaultDirection(ChatChannel.FACTION));
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL,
                DiscordBridgePolicy.defaultDirection(ChatChannel.OOC));
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL,
                DiscordBridgePolicy.defaultDirection(ChatChannel.OPERATOR));
        ChatChannel.installDefined(Arrays.asList(
                new ChatChannelDescriptor("square", "Square", ChatPresentationMode.IN_CHARACTER,
                        ChatRecipientRule.EVERYONE, ChatChannelAccess.NONE, 0xFFFFFF, true,
                        ChatChannelScope.NONE),
                new ChatChannelDescriptor("lounge", "Lounge",
                        ChatPresentationMode.OUT_OF_CHARACTER, ChatRecipientRule.EVERYONE,
                        ChatChannelAccess.NONE, 0xFFFFFF, true, ChatChannelScope.NONE)), null);
        assertEquals("a server's own without ooc:true",
                DiscordBridgeDirection.GAME_TO_DISCORD,
                DiscordBridgePolicy.defaultDirection(ChatChannel.fromId("square")));
        assertEquals("a server's own with ooc:true",
                DiscordBridgeDirection.BIDIRECTIONAL,
                DiscordBridgePolicy.defaultDirection(ChatChannel.fromId("lounge")));
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
