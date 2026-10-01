package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The link command hands out a code only for a channel a link can carry:
 * never a private one, nor one whose gate lets nobody read it.
 */
public final class LostTalesCommandDiscordTest {

    @After
    public void tearDown() {
        ChatChannelGates.install(ChatChannelGates.defaults());
    }

    @Test
    public void anOpenChannelMayBeLinked() {
        assertNull(LostTalesCommandDiscord.linkRefusal("ooc"));
        assertNull(LostTalesCommandDiscord.linkRefusal("Global"));
    }

    @Test
    public void aPrivateOrUnknownChannelIsRefused() {
        assertNotNull(LostTalesCommandDiscord.linkRefusal("fellowship"));
        assertNotNull(LostTalesCommandDiscord.linkRefusal("nowhere"));
        assertNotNull(LostTalesCommandDiscord.linkRefusal(null));
    }

    @Test
    public void aChannelNobodyMayReadIsRefused() {
        Map<ChatChannel, ChatChannelGates.Gate> gates =
                new HashMap<ChatChannel, ChatChannelGates.Gate>();
        gates.put(ChatChannel.OOC, new ChatChannelGates.Gate(
                Collections.<String>emptySet(), Collections.<String>emptySet(),
                true, false));
        ChatChannelGates.install(ChatChannelGates.of(gates));
        String refusal = LostTalesCommandDiscord.linkRefusal("ooc");
        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains("nobody read"));
        assertNull("another channel is left alone",
                LostTalesCommandDiscord.linkRefusal("global"));
    }
}
