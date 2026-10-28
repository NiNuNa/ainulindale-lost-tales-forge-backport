package com.ninuna.losttales.compat.discord;

import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Each linked Discord channel sends at most so many lines into the game in
 * five seconds (DISC-31); the rest is dropped and told once a minute.
 */
public final class DiscordChannelCapTest {

    @Test
    public void aChannelTakesItsLinesAndNoMoreInFiveSeconds() {
        DiscordChannelCap cap = new DiscordChannelCap();
        for (int index = 0; index < 40; index++) {
            assertTrue(cap.admits("5", 40, 1000L + index));
        }
        assertFalse(cap.admits("5", 40, 1100L));
        assertTrue("another channel has lines of its own", cap.admits("6", 40, 1100L));
        assertFalse(cap.admits("5", 40, 5999L));
        assertTrue("the first line has left the window",
                cap.admits("5", 40, 1000L + DiscordChannelCap.WINDOW_MILLIS));
    }

    /** The option's number is held to the bridge's own bounds. */
    @Test
    public void theNumberIsBounded() {
        DiscordChannelCap cap = new DiscordChannelCap();
        assertTrue(cap.admits("5", 0, 0L));
        assertFalse("at least one line, never none", cap.admits("5", 0, 1L));
        DiscordChannelCap wide = new DiscordChannelCap();
        for (int index = 0; index < DiscordChannelCap.MOST_LINES; index++) {
            assertTrue(wide.admits("5", 100000, 0L));
        }
        assertFalse(wide.admits("5", 100000, 0L));
    }

    /** What was dropped is told once a minute, per channel, then forgotten. */
    @Test
    public void theDropsAreToldOnceAMinute() {
        DiscordChannelCap cap = new DiscordChannelCap();
        assertTrue(cap.takeReport(0L).isEmpty());
        cap.admits("5", 1, 0L);
        cap.admits("5", 1, 1L);
        cap.admits("5", 1, 2L);
        cap.admits("6", 1, 2L);
        cap.admits("6", 1, 3L);
        Map<String, Integer> first = cap.takeReport(10L);
        assertEquals(Integer.valueOf(2), first.get("5"));
        assertEquals(Integer.valueOf(1), first.get("6"));
        cap.admits("5", 1, 20L);
        assertTrue("not within the minute", cap.takeReport(30L).isEmpty());
        Map<String, Integer> next = cap.takeReport(10L + DiscordChannelCap.REPORT_MILLIS);
        assertEquals(Integer.valueOf(1), next.get("5"));
        assertEquals(1, next.size());
        cap.clear();
        assertTrue(cap.takeReport(10L + 2 * DiscordChannelCap.REPORT_MILLIS).isEmpty());
    }
}
