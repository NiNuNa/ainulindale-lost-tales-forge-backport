package com.ninuna.losttales.compat.discord;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A player may ping only so many Discord members in a minute; the rest of their lines still post. */
public final class DiscordPingBudgetTest {

    @Test
    public void pingsAreSpentWithinTheWindowAndComeBackAfterIt() {
        DiscordPingBudget budget = new DiscordPingBudget();
        UUID player = UUID.randomUUID();
        long now = 1000000L;
        assertTrue(budget.spend(player, 5, now));
        assertTrue(budget.spend(player, 5, now + 1000L));
        // Spent all or none: one more is past the budget.
        assertFalse(budget.spend(player, 1, now + 2000L));
        // No pings is always within it.
        assertTrue(budget.spend(player, 0, now + 2000L));
        // Another player has their own.
        assertTrue(budget.spend(UUID.randomUUID(), 3, now + 2000L));
        // Once the window has passed the first five, they come back.
        assertTrue(budget.spend(player, 5,
                now + DiscordPingBudget.WINDOW_MILLIS + 10L));
        assertFalse(budget.spend(player, DiscordPingBudget.MOST_PINGS + 1,
                now + 10L * DiscordPingBudget.WINDOW_MILLIS));
        assertFalse(budget.spend(null, 1, now));
        budget.clear();
        assertTrue(budget.spend(player, DiscordPingBudget.MOST_PINGS, now + 3000L));
    }
}
