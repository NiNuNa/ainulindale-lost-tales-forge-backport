package com.ninuna.losttales.network.server;

import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A request window counts on the monotonic clock and starts over once its
 * time is up; a time before its start, which only a clock set back could
 * give, starts it over too, so no player is ever held out until a clock
 * catches up.
 */
public final class LostTalesRequestRateLimiterTest {
    private static final LostTalesRequestRateLimiter.RequestType TYPE =
            LostTalesRequestRateLimiter.RequestType.WAYSTONE_TRAVEL;
    private static final int BUDGET = 6;
    private static final long WINDOW = 5000L;

    @Before
    @After
    public void forget() {
        LostTalesRequestRateLimiter.clear();
    }

    @Test
    public void aWindowHoldsItsBudgetAndOpensAgainWhenItsTimeIsUp() {
        UUID player = UUID.randomUUID();
        for (int request = 0; request < BUDGET; request++) {
            assertTrue(LostTalesRequestRateLimiter.allow(player, TYPE, 1000L));
        }
        assertFalse(LostTalesRequestRateLimiter.allow(player, TYPE, 1000L + WINDOW - 1L));
        assertTrue(LostTalesRequestRateLimiter.allow(player, TYPE, 1000L + WINDOW));
    }

    @Test
    public void aClockSetBackNeverHoldsTheWindowShut() {
        UUID player = UUID.randomUUID();
        for (int request = 0; request <= BUDGET; request++) {
            LostTalesRequestRateLimiter.allow(player, TYPE, 1000000L);
        }
        assertFalse(LostTalesRequestRateLimiter.allow(player, TYPE, 1000000L));
        assertTrue("an hour back starts the window over",
                LostTalesRequestRateLimiter.allow(player, TYPE, 1000000L - 3600000L));
    }

    @Test
    public void aReplyIsBoundedAndNeverHeldShutByAClockSetBack() {
        UUID player = UUID.randomUUID();
        assertTrue("the first answer goes at any time, even at zero",
                LostTalesRequestRateLimiter.allowReply(player, TYPE, 1000L, 0L));
        assertFalse(LostTalesRequestRateLimiter.allowReply(player, TYPE, 1000L, 999L));
        assertTrue(LostTalesRequestRateLimiter.allowReply(player, TYPE, 1000L, 1000L));
        assertTrue(LostTalesRequestRateLimiter.allowReply(player, TYPE, 1000L, -50000L));
    }

    @Test
    public void theClockIsTheMonotonicOne() {
        long before = System.nanoTime() / 1000000L;
        long read = LostTalesRequestRateLimiter.monotonicMillis();
        long after = System.nanoTime() / 1000000L;
        assertTrue(read >= before && read <= after);
    }
}
