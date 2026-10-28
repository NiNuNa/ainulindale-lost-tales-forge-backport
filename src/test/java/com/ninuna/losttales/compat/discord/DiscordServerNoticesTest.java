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
        DiscordNotice joined = DiscordServerNotices.playerJoined("Steve",
                "https://heads/Steve");
        assertEquals(DiscordNotice.Kind.PLAYER_JOINED, joined.getKind());
        assertEquals("✅ Steve joined the game", joined.getText(ENGLISH));
        assertEquals("https://heads/Steve", joined.getIconUrl());
        assertEquals(LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN),
                joined.getColor());

        DiscordNotice left = DiscordServerNotices.playerLeft(" Steve ", "");
        assertEquals(DiscordNotice.Kind.PLAYER_LEFT, left.getKind());
        assertEquals("👋 Steve left the game", left.getText(ENGLISH));
        assertEquals("", left.getIconUrl());
        assertEquals(LostTalesColors.rgb(LostTalesColors.SALMON),
                left.getColor());

        // The author row renders no markdown, so nothing is escaped and
        // a name reads exactly as it is; a team colour code is dropped.
        assertEquals("✅ x_y_z joined the game",
                DiscordServerNotices.playerJoined("x_y_z", null).getText(ENGLISH));
        assertEquals("✅ **bold** joined the game",
                DiscordServerNotices.playerJoined("**bold**", null).getText(ENGLISH));
        assertEquals("✅ @everyone joined the game",
                DiscordServerNotices.playerJoined("@everyone", null).getText(ENGLISH));
        assertEquals("✅ Steve joined the game",
                DiscordServerNotices.playerJoined("§aSteve§r", null).getText(ENGLISH));
        assertEquals("✅  joined the game",
                DiscordServerNotices.playerJoined(null, null).getText(ENGLISH));
    }

    /** A join reads in the linked channel's own words; the name stays as it is. */
    @Test
    public void aJoinIsWordedInEachChannelsLanguage() {
        com.ninuna.losttales.util.LostTalesWords german =
                new com.ninuna.losttales.util.LostTalesWords() {
                    @Override
                    public String format(String key, Object... arguments) {
                        return "chat.losttales.discord.notice.joined".equals(key)
                                ? arguments[0] + " ist da" : key;
                    }
                };
        DiscordNotice joined = DiscordServerNotices.playerJoined("Steve", "");
        assertEquals("✅ Steve ist da", joined.getText(german));
        assertEquals("✅ Steve joined the game", joined.getText(ENGLISH));
        assertTrue(!joined.isEmpty());
        assertTrue(DiscordServerNotices.playerDied(" ", "").isEmpty());
        // A death is the game's own line in every channel.
        assertEquals("💀 Steve fell", DiscordServerNotices.playerDied("Steve fell", "")
                .getText(german));
    }

    @Test
    public void deathsAndAchievementsCarryTheGamesOwnWords() {
        DiscordNotice death = DiscordServerNotices.playerDied(
                "Aragorn was slain by Mordor Orc", "https://heads/Steve");
        assertEquals(DiscordNotice.Kind.PLAYER_DIED, death.getKind());
        assertEquals("💀 Aragorn was slain by Mordor Orc", death.getText(ENGLISH));
        assertEquals("https://heads/Steve", death.getIconUrl());
        assertEquals(LostTalesColors.rgb(LostTalesColors.PLUM_GRAY),
                death.getColor());

        DiscordNotice earned = DiscordServerNotices.achievement(
                "Aragorn has just earned the achievement [Taking Inventory]",
                "");
        assertEquals(DiscordNotice.Kind.ACHIEVEMENT, earned.getKind());
        assertEquals("🏆 Aragorn has just earned the achievement "
                + "[Taking Inventory]", earned.getText(ENGLISH));
        assertEquals(LostTalesColors.rgb(LostTalesColors.HONEY),
                earned.getColor());

        // Line breaks and formatting codes never reach the row.
        assertEquals("💀 Steve was slain by Bob using Sword",
                DiscordServerNotices.playerDied(
                        "Steve was slain by Bob\nusing §b§lSword§r", "")
                        .getText(ENGLISH));
    }

    @Test
    public void longTextIsCutAtDiscordsBound() {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            name.append('a');
        }
        DiscordNotice notice = DiscordServerNotices.playerDied(
                name.toString(), "");
        assertEquals(DiscordNotice.MAX_TEXT_LENGTH, notice.getText(ENGLISH).length());
        assertTrue(notice.getText(ENGLISH).endsWith("..."));
    }


    /**
     * The topic carries the status line, the players against the cap, the
     * address and the ticks a second, and never the time up: two hours
     * apart, the topic reads the same.
     */
    @Test
    public void theTopicLeavesTheTimeUpOut() {
        String early = DiscordServerNotices.onlineTopic(ENGLISH,
                ChatServerStatus.topicPartsOf(ENGLISH, 3, 20, "play.example.org", 19));
        assertEquals("Server online • 3/20 players • play.example.org • 20 TPS", early);
        assertTrue(!early.contains("up "));
        assertEquals(early, DiscordServerNotices.onlineTopic(ENGLISH,
                ChatServerStatus.topicPartsOf(ENGLISH, 3, 20, "play.example.org", 20)));
    }

    /** The topic says the server's status, the parts the game's member lists show. */
    @Test
    public void topicStatesTheServersStatus() {
        assertEquals("Server online • 14/40 players • 20 TPS",
                DiscordServerNotices.onlineTopic(ENGLISH,
                        ChatServerStatus.topicPartsOf(ENGLISH, 14, 40, "", 20)));
        assertEquals("Server online • 1 player • play.example.org • 10 TPS",
                DiscordServerNotices.onlineTopic(ENGLISH,
                        ChatServerStatus.topicPartsOf(ENGLISH, 1, 0,
                                "play.example.org", 11)));
        assertEquals("Server offline",
                DiscordServerNotices.offlineTopic(ENGLISH));
        assertEquals("{\"topic\":\"Server offline\"}",
                DiscordJson.channelTopicBody(
                        DiscordServerNotices.offlineTopic(ENGLISH)));
        assertEquals("{\"topic\":\"\"}", DiscordJson.channelTopicBody(null));
    }
}
