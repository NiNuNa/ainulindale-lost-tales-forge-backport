package com.ninuna.losttales.compat.discord;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The bridge's own posts and the topic are short, fixed, and safe to show. */
public final class DiscordServerNoticesTest {

    @Test
    public void noticesNameThePlayerWithMarkdownEscaped() {
        assertEquals("**Steve** joined the game",
                DiscordServerNotices.playerJoined("Steve"));
        assertEquals("**Steve** left the game",
                DiscordServerNotices.playerLeft(" Steve "));
        assertEquals("**x\\_y\\_z** joined the game",
                DiscordServerNotices.playerJoined("x_y_z"));
        assertEquals("**\\*\\*bold\\*\\*** left the game",
                DiscordServerNotices.playerLeft("**bold**"));
        assertEquals("**\\@everyone** joined the game",
                DiscordServerNotices.playerJoined("@everyone"));
        assertEquals("**** joined the game",
                DiscordServerNotices.playerJoined(null));
    }

    @Test
    public void lifecycleNoticesAreOneLineEach() {
        assertTrue(DiscordServerNotices.serverStarted().endsWith("Server started"));
        assertTrue(DiscordServerNotices.serverStopping()
                .endsWith("Server shutting down"));
        assertFalse(DiscordServerNotices.serverStarted().contains("\n"));
        assertFalse(DiscordServerNotices.serverStopping().contains("\n"));
    }

    @Test
    public void topicStatesTheCountAgainstTheCap() {
        assertEquals("Server online • 14/40 players",
                DiscordServerNotices.onlineTopic(14, 40));
        assertEquals("Server online • 0/20 players",
                DiscordServerNotices.onlineTopic(0, 20));
        assertEquals("Server online • 1 player",
                DiscordServerNotices.onlineTopic(1, 0));
        assertEquals("Server online • 0 players",
                DiscordServerNotices.onlineTopic(-3, -1));
        assertEquals("Server offline", DiscordServerNotices.offlineTopic());
        assertEquals("{\"topic\":\"Server offline\"}",
                DiscordJson.channelTopicBody(DiscordServerNotices.offlineTopic()));
        assertEquals("{\"topic\":\"\"}", DiscordJson.channelTopicBody(null));
    }
}
