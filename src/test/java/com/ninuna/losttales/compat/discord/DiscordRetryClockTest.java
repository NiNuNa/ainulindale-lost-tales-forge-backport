package com.ninuna.losttales.compat.discord;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A lookup Discord did not answer is asked again after a pause, never
 * given up for the session: the pause doubles up to a minute and an
 * answer resets it.
 */
public final class DiscordRetryClockTest {
    private static final String HOOK = "https://discord.com/api/webhooks/1/a";
    private static final String OTHER = "https://discord.com/api/webhooks/2/b";

    @Test
    public void aKeyNeverAskedMayBeAsked() {
        assertTrue(new DiscordRetryClock().mayAsk(HOOK, 0L));
    }

    @Test
    public void aFailureHoldsTheKeyBackForAPauseThatDoubles() {
        DiscordRetryClock clock = new DiscordRetryClock();
        clock.failed(HOOK, 1000L);
        assertFalse(clock.mayAsk(HOOK, 1000L));
        assertFalse(clock.mayAsk(HOOK, 5999L));
        assertTrue(clock.mayAsk(HOOK, 6000L));
        assertTrue("another key is not held back", clock.mayAsk(OTHER, 1000L));

        clock.failed(HOOK, 6000L);
        assertFalse(clock.mayAsk(HOOK, 15999L));
        assertTrue(clock.mayAsk(HOOK, 16000L));
    }

    @Test
    public void thePauseStopsGrowingAtAMinute() {
        DiscordRetryClock clock = new DiscordRetryClock();
        long now = 0L;
        for (int failure = 0; failure < 10; failure++) {
            clock.failed(HOOK, now);
        }
        assertFalse(clock.mayAsk(HOOK, now + DiscordRetryClock.MAX_WAIT_MILLIS - 1L));
        assertTrue(clock.mayAsk(HOOK, now + DiscordRetryClock.MAX_WAIT_MILLIS));
    }

    @Test
    public void anAnswerResetsThePause() {
        DiscordRetryClock clock = new DiscordRetryClock();
        clock.failed(HOOK, 0L);
        clock.failed(HOOK, 5000L);
        clock.answered(HOOK);
        assertTrue(clock.mayAsk(HOOK, 5000L));
        clock.failed(HOOK, 5000L);
        assertTrue("back to the shortest pause",
                clock.mayAsk(HOOK, 5000L + DiscordRetryClock.MIN_WAIT_MILLIS));
    }
}
