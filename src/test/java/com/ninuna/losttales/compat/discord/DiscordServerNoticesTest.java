package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.server.ChatServerStatus;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.util.EnglishWords;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The bridge's own posts and the topic are short, fixed, and safe to show. */
public final class DiscordServerNoticesTest {
    private static final EnglishWords ENGLISH = EnglishWords.INSTANCE;

    @Test
    public void playerNoticesNameThePlayerAsPlainText() {
        DiscordNotice joined = DiscordServerNotices.playerJoined(ENGLISH, "Steve",
                "https://heads/Steve");
        assertEquals(DiscordNotice.Kind.PLAYER_JOINED, joined.getKind());
        assertEquals("✅ Steve joined the game", joined.getText());
        assertEquals("https://heads/Steve", joined.getIconUrl());
        assertEquals(LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN),
                joined.getColor());

        DiscordNotice left = DiscordServerNotices.playerLeft(ENGLISH, " Steve ", "");
        assertEquals(DiscordNotice.Kind.PLAYER_LEFT, left.getKind());
        assertEquals("👋 Steve left the game", left.getText());
        assertEquals("", left.getIconUrl());
        assertEquals(LostTalesColors.rgb(LostTalesColors.SALMON),
                left.getColor());

        // The author row renders no markdown, so nothing is escaped and
        // a name reads exactly as it is; a team colour code is dropped.
        assertEquals("✅ x_y_z joined the game",
                DiscordServerNotices.playerJoined(ENGLISH, "x_y_z", null).getText());
        assertEquals("✅ **bold** joined the game",
                DiscordServerNotices.playerJoined(ENGLISH, "**bold**", null).getText());
        assertEquals("✅ @everyone joined the game",
                DiscordServerNotices.playerJoined(ENGLISH, "@everyone", null).getText());
        assertEquals("✅ Steve joined the game",
                DiscordServerNotices.playerJoined(ENGLISH, "§aSteve§r", null).getText());
        assertEquals("✅  joined the game",
                DiscordServerNotices.playerJoined(ENGLISH, null, null).getText());
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


    /** The topic says the server's status, the parts the game's member lists show. */
    @Test
    public void topicStatesTheServersStatus() {
        assertEquals("Server online • 14/40 players • 20 TPS",
                DiscordServerNotices.onlineTopic(ENGLISH,
                        ChatServerStatus.partsOf(ENGLISH, 14, 40, "", 20, 0L)));
        assertEquals("Server online • 1 player • play.example.org • 18 TPS"
                + " • up 1h 30m",
                DiscordServerNotices.onlineTopic(ENGLISH,
                        ChatServerStatus.partsOf(ENGLISH, 1, 0,
                                "play.example.org", 18, 90L * 60000L)));
        assertEquals("Server offline",
                DiscordServerNotices.offlineTopic(ENGLISH));
        assertEquals("{\"topic\":\"Server offline\"}",
                DiscordJson.channelTopicBody(
                        DiscordServerNotices.offlineTopic(ENGLISH)));
        assertEquals("{\"topic\":\"\"}", DiscordJson.channelTopicBody(null));
    }
}
