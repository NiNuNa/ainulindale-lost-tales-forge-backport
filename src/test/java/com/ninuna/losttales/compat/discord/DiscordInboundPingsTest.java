package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A Discord member's lines ping players in the game only within the
 * budget a player has on Discord: past it the line still goes, but its
 * names call on nobody. Each member has a budget of their own.
 */
public final class DiscordInboundPingsTest {

    @After
    public void forget() {
        LostTalesDiscordBridge.getInstance().resetSession();
    }

    private static List<ChatNamedPlayer> named(int count) {
        List<ChatNamedPlayer> named = new ArrayList<ChatNamedPlayer>();
        for (int index = 0; index < count; index++) {
            named.add(ChatNamedPlayer.account(UUID.randomUUID(), "Player" + index));
        }
        return named;
    }

    @Test
    public void aMembersPingsStopAtTheBudget() {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        UUID member = UUID.randomUUID();
        List<ChatNamedPlayer> eight = named(ChatNamedPlayer.MAX_PER_LINE);
        assertEquals(eight, bridge.pingsFromDiscord(member, eight));
        assertTrue("past the budget the line names nobody",
                bridge.pingsFromDiscord(member, eight).isEmpty());
        assertEquals("what is left of the budget still pings", named(2).size(),
                bridge.pingsFromDiscord(member, named(2)).size());
        assertTrue(bridge.pingsFromDiscord(member, named(1)).isEmpty());
        assertEquals("another member has a budget of their own", eight,
                bridge.pingsFromDiscord(UUID.randomUUID(), eight));
        assertTrue("a line that names nobody costs nothing",
                bridge.pingsFromDiscord(member, named(0)).isEmpty());
    }
}
