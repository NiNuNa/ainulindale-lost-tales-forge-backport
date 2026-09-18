package com.ninuna.losttales.client.chat;

import java.util.Calendar;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The chat's clock has twelve hours. A stamp behind a name shows the
 * time alone for today and says the day otherwise, as Discord does, the
 * time itself in italics; the timestamp area shows the clock alone, in
 * the local zone the stamp itself was written in.
 */
public final class ChatTimestampFormatterTest {
    private static final String ITALIC = "§o";
    private static final String RESET = "§r";

    private static long at(int year, int month, int day, int hour,
                           int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTimeInMillis();
    }

    @Test
    public void theClockHasTwelveHours() {
        assertEquals("7:04 PM", ChatTimestampFormatter.format(
                at(2026, Calendar.SEPTEMBER, 14, 19, 4)));
        assertEquals("12:30 AM", ChatTimestampFormatter.format(
                at(2026, Calendar.SEPTEMBER, 14, 0, 30)));
    }

    @Test
    public void aStampBehindANameSaysTheDayOnceItIsNotToday() {
        long now = at(2026, Calendar.SEPTEMBER, 14, 22, 0);
        assertEquals(ITALIC + "7:04 PM" + RESET, ChatTimestampFormatter
                .formatDrawnStamp(at(2026, Calendar.SEPTEMBER, 14, 19, 4), now));
        assertEquals("Yesterday at " + ITALIC + "9:54 PM" + RESET,
                ChatTimestampFormatter.formatDrawnStamp(
                        at(2026, Calendar.SEPTEMBER, 13, 21, 54), now));
        assertEquals("September 11, 2026 at " + ITALIC + "9:05 AM" + RESET,
                ChatTimestampFormatter.formatDrawnStamp(
                        at(2026, Calendar.SEPTEMBER, 11, 9, 5), now));
        // Yesterday reaches back across the turn of the year.
        assertEquals("Yesterday at " + ITALIC + "11:50 PM" + RESET,
                ChatTimestampFormatter.formatDrawnStamp(
                        at(2026, Calendar.DECEMBER, 31, 23, 50),
                        at(2027, Calendar.JANUARY, 1, 0, 10)));
    }

    /** The timestamp area shows the clock alone, in italics. */
    @Test
    public void theAreaShowsTheClockAlone() {
        assertEquals(ITALIC + "7:04" + RESET,
                ChatTimestampFormatter.formatDrawnClock(
                        at(2026, Calendar.SEPTEMBER, 14, 19, 4)));
        assertEquals(ITALIC + "12:30" + RESET,
                ChatTimestampFormatter.formatDrawnClock(
                        at(2026, Calendar.SEPTEMBER, 14, 0, 30)));
    }
}
