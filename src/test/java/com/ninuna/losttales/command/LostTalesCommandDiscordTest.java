package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.compat.discord.DiscordBridgeDirection;
import com.ninuna.losttales.util.EnglishWords;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
        assertEquals("Cannot link ooc: its gate in channels.cfg lets nobody read it,"
                + " so nothing would cross.", EnglishWords.INSTANCE.format(refusal, "ooc"));
        assertNull("another channel is left alone",
                LostTalesCommandDiscord.linkRefusal("global"));
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

    private static String direction(DiscordBridgeDirection direction) {
        return EnglishWords.INSTANCE.format(LostTalesCommandDiscord.directionKey(direction));
    }
}
