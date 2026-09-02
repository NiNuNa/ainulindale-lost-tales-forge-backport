package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.gui.style.LostTalesColors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The bridge's own posts and the topic are short, fixed, and safe to show. */
public final class DiscordServerNoticesTest {

    @Test
    public void playerNoticesNameThePlayerAsPlainText() {
        DiscordNotice joined = DiscordServerNotices.playerJoined("Steve",
                "https://heads/Steve");
        assertEquals(DiscordNotice.Kind.PLAYER_JOINED, joined.getKind());
        assertEquals("✅ Steve joined the game", joined.getText());
        assertEquals("https://heads/Steve", joined.getIconUrl());
        assertEquals(LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN),
                joined.getColor());

        DiscordNotice left = DiscordServerNotices.playerLeft(" Steve ", "");
        assertEquals(DiscordNotice.Kind.PLAYER_LEFT, left.getKind());
        assertEquals("👋 Steve left the game", left.getText());
        assertEquals("", left.getIconUrl());
        assertEquals(LostTalesColors.rgb(LostTalesColors.SALMON),
                left.getColor());

        // The author row renders no markdown, so nothing is escaped and
        // a name reads exactly as it is; a team colour code is dropped.
        assertEquals("✅ x_y_z joined the game",
                DiscordServerNotices.playerJoined("x_y_z", null).getText());
        assertEquals("✅ **bold** joined the game",
                DiscordServerNotices.playerJoined("**bold**", null).getText());
        assertEquals("✅ @everyone joined the game",
                DiscordServerNotices.playerJoined("@everyone", null).getText());
        assertEquals("✅ Steve joined the game",
                DiscordServerNotices.playerJoined("§aSteve§r", null).getText());
        assertEquals("✅  joined the game",
                DiscordServerNotices.playerJoined(null, null).getText());
    }

    @Test
    public void deathsAndAchievementsCarryTheGamesOwnWords() {
        DiscordNotice death = DiscordServerNotices.playerDied(
                "Aragorn was slain by Mordor Orc", "https://heads/Steve");
        assertEquals(DiscordNotice.Kind.PLAYER_DIED, death.getKind());
        assertEquals("💀 Aragorn was slain by Mordor Orc", death.getText());
        assertEquals("https://heads/Steve", death.getIconUrl());
        assertEquals(LostTalesColors.rgb(LostTalesColors.PLUM_GRAY),
                death.getColor());

        DiscordNotice earned = DiscordServerNotices.achievement(
                "Aragorn has just earned the achievement [Taking Inventory]",
                "");
        assertEquals(DiscordNotice.Kind.ACHIEVEMENT, earned.getKind());
        assertEquals("🏆 Aragorn has just earned the achievement "
                + "[Taking Inventory]", earned.getText());
        assertEquals(LostTalesColors.rgb(LostTalesColors.HONEY),
                earned.getColor());

        // Line breaks and formatting codes never reach the row.
        assertEquals("💀 Steve was slain by Bob using Sword",
                DiscordServerNotices.playerDied(
                        "Steve was slain by Bob\nusing §b§lSword§r", "")
                        .getText());
    }

    @Test
    public void longTextIsCutAtDiscordsBound() {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            name.append('a');
        }
        DiscordNotice notice = DiscordServerNotices.playerDied(
                name.toString(), "");
        assertEquals(DiscordNotice.MAX_TEXT_LENGTH, notice.getText().length());
        assertTrue(notice.getText().endsWith("..."));
    }

    @Test
    public void lifecycleNoticesAreOneLineEach() {
        DiscordNotice started = DiscordServerNotices.serverStarted();
        DiscordNotice stopping = DiscordServerNotices.serverStopping();
        assertEquals(DiscordNotice.Kind.SERVER_STARTED, started.getKind());
        assertEquals(DiscordNotice.Kind.SERVER_STOPPING, stopping.getKind());
        assertTrue(started.getText().endsWith("Server started"));
        assertTrue(stopping.getText().endsWith("Server shutting down"));
        assertFalse(started.getText().contains("\n"));
        assertFalse(stopping.getText().contains("\n"));
        assertEquals("", started.getIconUrl());
        assertEquals(LostTalesColors.rgb(LostTalesColors.FERN_GREEN),
                started.getColor());
        assertEquals(LostTalesColors.rgb(LostTalesColors.CRIMSON),
                stopping.getColor());
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
