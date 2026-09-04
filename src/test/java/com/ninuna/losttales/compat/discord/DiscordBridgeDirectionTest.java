package com.ninuna.losttales.compat.discord;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class DiscordBridgeDirectionTest {

    @Test
    public void eachDirectionSaysWhichWayLinesCross() {
        assertFalse(DiscordBridgeDirection.DISABLED.sendsToDiscord());
        assertFalse(DiscordBridgeDirection.DISABLED.readsFromDiscord());
        assertTrue(DiscordBridgeDirection.GAME_TO_DISCORD.sendsToDiscord());
        assertFalse(DiscordBridgeDirection.GAME_TO_DISCORD.readsFromDiscord());
        assertFalse(DiscordBridgeDirection.DISCORD_TO_GAME.sendsToDiscord());
        assertTrue(DiscordBridgeDirection.DISCORD_TO_GAME.readsFromDiscord());
        assertTrue(DiscordBridgeDirection.BIDIRECTIONAL.sendsToDiscord());
        assertTrue(DiscordBridgeDirection.BIDIRECTIONAL.readsFromDiscord());
    }

    @Test
    public void takingAHalfAwayLeavesTheOther() {
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                DiscordBridgeDirection.BIDIRECTIONAL.withoutReads());
        assertEquals(DiscordBridgeDirection.DISCORD_TO_GAME,
                DiscordBridgeDirection.BIDIRECTIONAL.withoutSends());
        assertEquals(DiscordBridgeDirection.DISABLED,
                DiscordBridgeDirection.DISCORD_TO_GAME.withoutReads());
        assertEquals(DiscordBridgeDirection.DISABLED,
                DiscordBridgeDirection.GAME_TO_DISCORD.withoutSends());
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL,
                DiscordBridgeDirection.of(true, true));
        assertEquals(DiscordBridgeDirection.DISABLED,
                DiscordBridgeDirection.of(false, false));
    }

    @Test
    public void namesParseLooselyAndUnknownOnesAreNull() {
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL,
                DiscordBridgeDirection.parse(" bidirectional "));
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                DiscordBridgeDirection.parse("game-to-discord"));
        assertNull(DiscordBridgeDirection.parse("sideways"));
        assertNull(DiscordBridgeDirection.parse(null));
    }
}
