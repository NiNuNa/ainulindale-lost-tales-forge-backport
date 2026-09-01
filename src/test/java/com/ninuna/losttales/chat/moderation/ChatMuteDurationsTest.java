package com.ninuna.losttales.chat.moderation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ChatMuteDurationsTest {

    @Test
    public void parsesCountAndUnitAndNothingElse() {
        assertEquals(30L * 1000L, ChatMuteDurations.parse("30s"));
        assertEquals(15L * 60L * 1000L, ChatMuteDurations.parse("15m"));
        assertEquals(2L * 60L * 60L * 1000L, ChatMuteDurations.parse("2H"));
        assertEquals(7L * 24L * 60L * 60L * 1000L,
                ChatMuteDurations.parse(" 7d "));
        assertEquals(ChatMuteDurations.NOT_A_DURATION,
                ChatMuteDurations.parse("spamming"));
        assertEquals(ChatMuteDurations.NOT_A_DURATION,
                ChatMuteDurations.parse("0m"));
        assertEquals(ChatMuteDurations.NOT_A_DURATION,
                ChatMuteDurations.parse("m"));
        assertEquals(ChatMuteDurations.NOT_A_DURATION,
                ChatMuteDurations.parse("1x"));
        assertEquals(ChatMuteDurations.NOT_A_DURATION,
                ChatMuteDurations.parse(""));
        assertEquals(ChatMuteDurations.NOT_A_DURATION,
                ChatMuteDurations.parse(null));
        assertEquals(ChatMuteDurations.NOT_A_DURATION,
                ChatMuteDurations.parse("1 d"));
    }

    @Test
    public void oversizedDurationsCapAtAYear() {
        assertEquals(ChatMuteDurations.MAX_MILLIS,
                ChatMuteDurations.parse("9999d"));
        assertEquals(ChatMuteDurations.MAX_MILLIS,
                ChatMuteDurations.parse("99999999999s"));
    }

    @Test
    public void remainingReadsInTheLargestTwoUnits() {
        assertEquals("2d 5h", ChatMuteDurations.formatRemaining(
                (2L * 86400L + 5L * 3600L) * 1000L));
        assertEquals("2d", ChatMuteDurations.formatRemaining(
                2L * 86400L * 1000L));
        assertEquals("3h 12m", ChatMuteDurations.formatRemaining(
                (3L * 3600L + 12L * 60L) * 1000L));
        assertEquals("45m 30s", ChatMuteDurations.formatRemaining(
                (45L * 60L + 30L) * 1000L));
        assertEquals("20s", ChatMuteDurations.formatRemaining(20000L));
        // A fraction of a second still reads as a mute.
        assertEquals("1s", ChatMuteDurations.formatRemaining(1L));
        assertEquals("1s", ChatMuteDurations.formatRemaining(0L));
    }
}
