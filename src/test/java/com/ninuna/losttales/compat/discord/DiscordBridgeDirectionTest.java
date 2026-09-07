package com.ninuna.losttales.compat.discord;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class DiscordBridgeDirectionTest {


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
