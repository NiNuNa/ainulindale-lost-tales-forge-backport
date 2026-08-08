package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LostTalesLotrMapCalendarTest {
    /** Tick zero is dawn, not midnight, which is the easy one to get wrong. */
    @Test
    public void theClockStartsAtDawn() {
        assertEquals("06:00",
                LostTalesLotrMapCalendar.formatTime(0L));
        assertEquals("12:00",
                LostTalesLotrMapCalendar.formatTime(6000L));
        assertEquals("18:00",
                LostTalesLotrMapCalendar.formatTime(12000L));
        assertEquals("00:00",
                LostTalesLotrMapCalendar.formatTime(18000L));
    }

    @Test
    public void theClockIsAlwaysFourDigitsAndWrapsWithTheDay() {
        assertEquals("06:30",
                LostTalesLotrMapCalendar.formatTime(500L));
        assertEquals("07:03",
                LostTalesLotrMapCalendar.formatTime(1050L));
        // A world that has been running a while, and one that reads back
        // before the epoch, both have to give an hour of the day.
        assertEquals("06:00",
                LostTalesLotrMapCalendar.formatTime(24000L * 91L));
        assertEquals("05:00",
                LostTalesLotrMapCalendar.formatTime(-1000L));
    }
}
