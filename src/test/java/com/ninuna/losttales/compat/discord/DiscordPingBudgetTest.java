package com.ninuna.losttales.compat.discord;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A player may make only so many pings on Discord in a minute; the rest
 * of their posts still go, pinging nobody. What counts is every ping a
 * post really makes.
 */
public final class DiscordPingBudgetTest {

    @Test
    public void pingsAreSpentWithinTheWindowAndComeBackAfterIt() {
        DiscordPingBudget budget = new DiscordPingBudget();
        UUID player = UUID.randomUUID();
        long now = 1000000L;
        assertTrue(budget.allows(player, 5, now));
        budget.spend(player, 5, now);
        budget.spend(player, 5, now + 1000L);
        // One more is past the budget.
        assertFalse(budget.allows(player, 1, now + 2000L));
        // No pings is always within it.
        assertTrue(budget.allows(player, 0, now + 2000L));
        // Another player has their own.
        assertTrue(budget.allows(UUID.randomUUID(), 3, now + 2000L));
        // Once the window has passed the first five, they come back.
        assertTrue(budget.allows(player, 5, now + DiscordPingBudget.WINDOW_MILLIS + 10L));
        assertFalse(budget.allows(player, 6, now + DiscordPingBudget.WINDOW_MILLIS + 10L));
        assertFalse(budget.allows(player, DiscordPingBudget.MOST_PINGS + 1,
                now + 10L * DiscordPingBudget.WINDOW_MILLIS));
        assertFalse(budget.allows(null, 1, now));
        budget.clear();
        assertTrue(budget.allows(player, DiscordPingBudget.MOST_PINGS, now + 3000L));
    }

    /**
     * Asking spends nothing, since a post that is refused or never sent
     * pings nobody; a member pinged in several linked channels costs one
     * ping in each.
     */
    @Test
    public void onlyPingsReallyMadeAreCounted() {
        DiscordPingBudget budget = new DiscordPingBudget();
        UUID player = UUID.randomUUID();
        long now = 1000000L;
        for (int ask = 0; ask < 50; ask++) {
            assertTrue(budget.allows(player, DiscordPingBudget.MOST_PINGS, now));
        }
        // One member named in a line posted to three linked channels.
        for (int post = 0; post < 3; post++) {
            assertTrue(budget.allows(player, 1, now));
            budget.spend(player, 1, now);
        }
        assertTrue(budget.allows(player, DiscordPingBudget.MOST_PINGS - 3, now));
        assertFalse(budget.allows(player, DiscordPingBudget.MOST_PINGS - 2, now));
        // A spend past the budget, as two workers might make, is still
        // counted, and only the newest pings are kept.
        budget.spend(player, 50, now + 1000L);
        assertFalse(budget.allows(player, 1, now + 1000L));
        assertTrue(budget.allows(player, DiscordPingBudget.MOST_PINGS,
                now + 1000L + DiscordPingBudget.WINDOW_MILLIS));
        budget.spend(null, 1, now);
        budget.spend(player, 0, now);
    }
}
