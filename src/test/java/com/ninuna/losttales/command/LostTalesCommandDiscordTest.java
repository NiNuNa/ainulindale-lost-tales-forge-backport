package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatChannelDescriptor;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.compat.discord.DiscordBridgeDirection;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.util.EnglishWords;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The link command hands out a code only for a channel a link can carry:
 * never a private one, one heard nearby, nor one whose gate lets nobody
 * read it. With no direction named, the channel's kind picks one.
 */
public final class LostTalesCommandDiscordTest {

    @After
    public void tearDown() {
        ChatChannelGates.install(ChatChannelGates.defaults());
        ChatChannel.resetToBuiltIn();
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
        assertEquals("Cannot link ooc: its gate in channels.cfg lets nobody read it,"
                + " so nothing would cross.", EnglishWords.INSTANCE.format(refusal, "ooc"));
        assertNull("another channel is left alone",
                LostTalesCommandDiscord.linkRefusal("global"));
    }

    /**
     * Proximity, and a server's channel heard nearby however its config
     * marks it, can never be linked, and the answer says why (DISC-03).
     */
    @Test
    public void aChannelHeardNearbyIsRefusedAndSaysWhy() {
        String refusal = LostTalesCommandDiscord.linkRefusal("proximity");
        assertEquals(LostTalesCommandDiscord.SAY + "refused.nearby", refusal);
        assertEquals("Cannot link proximity: it is heard only nearby, and nobody on"
                + " Discord stands near anyone.",
                EnglishWords.INSTANCE.format(refusal, "proximity"));
        ChatChannel.installDefined(Collections.singletonList(new ChatChannelDescriptor(
                "tavern", "Tavern", ChatPresentationMode.IN_CHARACTER,
                ChatRecipientRule.PROXIMITY, ChatChannelAccess.NONE, 0xFFFFFF, true,
                ChatChannelScope.NONE)), null);
        assertEquals(LostTalesCommandDiscord.SAY + "refused.nearby",
                LostTalesCommandDiscord.linkRefusal("tavern"));
    }

    /**
     * With no direction named, an in-character channel links one way and
     * an out-of-character one both ways; a named direction stands either
     * way (DISC-29).
     */
    @Test
    public void aLinkWithNoDirectionFollowsTheChannelsKind() {
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                LostTalesCommandDiscord.linkDirection(ChatChannel.GLOBAL, null));
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                LostTalesCommandDiscord.linkDirection(ChatChannel.FACTION, null));
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL,
                LostTalesCommandDiscord.linkDirection(ChatChannel.OOC, null));
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL,
                LostTalesCommandDiscord.linkDirection(ChatChannel.OPERATOR, null));
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL,
                LostTalesCommandDiscord.linkDirection(ChatChannel.GLOBAL, "bidirectional"));
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                LostTalesCommandDiscord.linkDirection(ChatChannel.OOC, "GAME_TO_DISCORD"));
        assertEquals(DiscordBridgeDirection.DISCORD_TO_GAME,
                LostTalesCommandDiscord.linkDirection(ChatChannel.GLOBAL, "discord-to-game"));
        assertNull(LostTalesCommandDiscord.linkDirection(ChatChannel.GLOBAL, "DISABLED"));
        assertNull(LostTalesCommandDiscord.linkDirection(ChatChannel.GLOBAL, "sideways"));
        assertEquals("Global Chat is in character, so Discord members' messages stay out"
                + " of it. To let them in, ask for a code with BIDIRECTIONAL after the"
                + " channel.", EnglishWords.INSTANCE.format(
                        LostTalesCommandDiscord.SAY + "one_way", "Global Chat"));
    }

    /** The answers read in the English lang file's words. */
    @Test
    public void theAnswersReadInTheLangFilesWords() {
        assertEquals("Cannot link fellowship: name a channel that may reach Discord,"
                + " or a faction such as gondor.", EnglishWords.INSTANCE.format(
                        LostTalesCommandDiscord.linkRefusal("fellowship"), "fellowship"));
        assertEquals("both ways", direction(DiscordBridgeDirection.BIDIRECTIONAL));
        assertEquals("game to Discord", direction(DiscordBridgeDirection.GAME_TO_DISCORD));
        assertEquals("Discord to game", direction(DiscordBridgeDirection.DISCORD_TO_GAME));
        assertEquals("switched off", direction(DiscordBridgeDirection.DISABLED));
        assertEquals("switched off", direction(null));
        assertEquals("Linked Discord channel #general of The Shire to OOC Chat.",
                EnglishWords.INSTANCE.format(LostTalesCommandDiscord.SAY + "linked",
                        "#general of The Shire", "OOC Chat"));
    }

    /** No code is handed out while the bot cannot take it, and the answer says why. */
    @Test
    public void eachReasonTheBotCannotPairHasItsOwnAnswer() {
        assertEquals("The Discord bot is still connecting. Try again in a moment.",
                EnglishWords.INSTANCE.format(LostTalesCommandDiscord.notConnectedKey(
                        LostTalesDiscordBridge.Pairing.CONNECTING)));
        java.util.Set<String> keys = new java.util.HashSet<String>();
        for (LostTalesDiscordBridge.Pairing pairing : LostTalesDiscordBridge.Pairing.values()) {
            if (pairing != LostTalesDiscordBridge.Pairing.READY) {
                String key = LostTalesCommandDiscord.notConnectedKey(pairing);
                keys.add(key);
                assertNotNull(key, EnglishWords.INSTANCE.format(key));
                assertFalse(key, key.equals(EnglishWords.INSTANCE.format(key)));
            }
        }
        assertEquals(4, keys.size());
    }

    private static String direction(DiscordBridgeDirection direction) {
        return EnglishWords.INSTANCE.format(LostTalesCommandDiscord.directionKey(direction));
    }
}
