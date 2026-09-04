package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageIds;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    public void postsRememberTheBindingTheyCrossedThrough() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(1000L, "111", "", "faction:lotr:gondor");
        links.link(2000L, "222", "-# header\n", "discord");
        links.link(3000L, "333");
        assertEquals("faction:lotr:gondor", links.bindingKeyOf(1000L));
        assertEquals("discord", links.bindingKeyOf(2000L));
        assertEquals("", links.bindingKeyOf(3000L));
        assertEquals("", links.bindingKeyOf(4000L));
        // Both directions still answer for a bound post.
        assertEquals("111", links.discordIdOf(1000L));
        assertEquals(1000L, links.messageIdOf("111"));
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
    }

    @Test
    public void theOldestPairsGoFirst() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        for (int index = 0; index < 600; index++) {
            links.link(1000L + index, "d" + index);
        }
        assertEquals(512, links.size());
        assertEquals("", links.discordIdOf(1000L));
        assertEquals(ChatMessageIds.NONE, links.messageIdOf("d0"));
        assertEquals("d599", links.discordIdOf(1599L));
        assertEquals(1599L, links.messageIdOf("d599"));
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
