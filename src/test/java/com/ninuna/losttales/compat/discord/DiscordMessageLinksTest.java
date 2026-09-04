package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class DiscordMessageLinksTest {

    @Test
    public void linksAnswerInBothDirections() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111");
        links.link(2000L, "222");
        assertEquals("111", links.discordIdOf(1000L));
        assertEquals("222", links.discordIdOf(2000L));
        assertEquals(1000L, links.messageIdOf("111"));
        assertEquals(2000L, links.messageIdOf("222"));
        assertEquals("", links.discordIdOf(3000L));
        assertEquals(ChatMessageIds.NONE, links.messageIdOf("333"));
        assertEquals(ChatMessageIds.NONE, links.messageIdOf(null));
    }

    @Test
    public void headersAreKeptForTheEditThatSaysThemAgain() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111", "-# reply header\n");
        links.link(2000L, "222");
        assertEquals("-# reply header\n", links.headerOf(1000L));
        assertEquals("", links.headerOf(2000L));
        assertEquals("", links.headerOf(3000L));
        links.link(1000L, "111", null);
        assertEquals("", links.headerOf(1000L));
    }

    @Test
    public void copiesRememberTheDestinationTheyLiveIn() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111", "", "channel:5");
        links.link(2000L, "222", "-# header\n", "channel:6");
        links.link(3000L, "333");
        assertEquals("channel:5", links.destinationOf(1000L));
        assertEquals("channel:6", links.destinationOf(2000L));
        assertEquals("", links.destinationOf(3000L));
        assertEquals("", links.destinationOf(4000L));
        assertTrue(links.hasCopyIn(1000L, "channel:5"));
        assertFalse(links.hasCopyIn(1000L, "channel:6"));
        assertFalse(links.hasCopyIn(1000L, null));
        assertEquals("111", links.discordIdOf(1000L, "channel:5"));
        assertEquals("", links.discordIdOf(1000L, "channel:6"));
        // Both directions still answer for a placed copy.
        assertEquals("111", links.discordIdOf(1000L));
        assertEquals(1000L, links.messageIdOf("111"));
    }

    /**
     * A game line posted to several Discord channels is one message
     * with a copy in each, every copy answering by the channel it is in
     * and remembering the webhook it went through.
     */
    @Test
    public void aMessageMayHaveACopyPerDestination() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111", "", "channel:5", "hookA");
        links.link(1000L, "222", "-# h\n", "channel:6", "hookB");
        assertEquals(1, links.size());
        List<DiscordMessageLinks.Copy> copies = links.copiesOf(1000L);
        assertEquals(2, copies.size());
        assertEquals("111", copies.get(0).discordId);
        assertEquals("hookA", copies.get(0).webhookUrl);
        assertEquals("", copies.get(0).header);
        assertEquals("222", copies.get(1).discordId);
        assertEquals("channel:6", copies.get(1).destination);
        assertEquals("-# h\n", copies.get(1).header);
        assertEquals("111", links.discordIdOf(1000L));
        assertEquals("222", links.discordIdOf(1000L, "channel:6"));
        assertEquals(1000L, links.messageIdOf("111"));
        assertEquals(1000L, links.messageIdOf("222"));
        assertTrue(links.copiesOf(2000L).isEmpty());
        // A correction sent through a webhook finds the copy that went
        // through it, and nothing for a webhook the message never used.
        assertEquals("222", links.copyThrough(1000L, "hookB").discordId);
        assertEquals("-# h\n", links.copyThrough(1000L, "hookB").header);
        assertNull(links.copyThrough(1000L, "hookC"));
        assertNull(links.copyThrough(1000L, ""));
        assertNull(links.copyThrough(2000L, "hookA"));
        // A copy relinked in its own destination replaces the old one
        // there and leaves the other alone.
        links.link(1000L, "333", "", "channel:5", "hookA");
        assertEquals(2, links.copiesOf(1000L).size());
        assertEquals("333", links.discordIdOf(1000L, "channel:5"));
        assertEquals(ChatMessageIds.NONE, links.messageIdOf("111"));
        assertEquals(1000L, links.messageIdOf("333"));
        assertEquals(1000L, links.messageIdOf("222"));
    }

    @Test
    public void halfALinkIsNoLink() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(ChatMessageIds.NONE, "111");
        links.link(1000L, "");
        links.link(1000L, null);
        assertEquals(0, links.size());
    }

    @Test
    public void relinkingAMessageReplacesItsPair() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111");
        links.link(1000L, "112");
        assertEquals("112", links.discordIdOf(1000L));
        assertEquals(1000L, links.messageIdOf("112"));
        assertEquals(ChatMessageIds.NONE, links.messageIdOf("111"));
        assertEquals(1, links.copiesOf(1000L).size());
    }

    @Test
    public void theOldestMessagesGoFirst() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        for (int index = 0; index < 600; index++) {
            links.link(1000L + index, "d" + index);
        }
        assertEquals(512, links.size());
        assertEquals("", links.discordIdOf(1000L));
        assertEquals(ChatMessageIds.NONE, links.messageIdOf("d0"));
        assertEquals("d599", links.discordIdOf(1599L));
        assertEquals(1599L, links.messageIdOf("d599"));
        // A message given a second copy counts as the newest again.
        links.link(1088L, "d88b", "", "channel:2");
        for (int index = 600; index < 1111; index++) {
            links.link(1000L + index, "d" + index);
        }
        assertEquals(512, links.size());
        assertEquals("d88", links.discordIdOf(1088L));
        assertEquals("d88b", links.discordIdOf(1088L, "channel:2"));
        // The messages that were older than the re-put one went first.
        assertEquals("", links.discordIdOf(1089L));
        assertEquals("", links.discordIdOf(1599L));
        assertEquals("d600", links.discordIdOf(1600L));
    }

    @Test
    public void clearingForgetsEverything() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111");
        links.clear();
        assertEquals(0, links.size());
        assertEquals("", links.discordIdOf(1000L));
        assertEquals(ChatMessageIds.NONE, links.messageIdOf("111"));
    }
}
