package com.ninuna.losttales.quest.world;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A world quest's schedule in server.cfg (QUEST-36): read from one line,
 * on the server's real clock, on a weekday or every so many days, and a
 * time missed while the server was down caught up only that day.
 */
public final class WorldQuestScheduleTest {
    private static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Berlin");

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(ZONE, Locale.ROOT);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    @Test
    public void aLineIsReadOrSaysWhyNot() {
        assertNotNull(WorldQuestSchedule.parse("losttales:server/Wolf_Hunt Saturday 18:00"));
        assertEquals("the quest id keeps its case", "losttales:server/Wolf_Hunt",
                WorldQuestSchedule.parse("losttales:server/Wolf_Hunt saturday 18:00")
                        .getQuestId());
        assertNotNull(WorldQuestSchedule.parse("losttales:server/harvest every 7 days 20:00"));
        assertNull(WorldQuestSchedule.parse("losttales:server/harvest someday 20:00"));
        assertTrue(WorldQuestSchedule.problem("losttales:a saturday 25:00").contains("HH:MM"));
        assertTrue(WorldQuestSchedule.problem("losttales:a every 0 days 20:00")
                .contains("every 1 to 365 days"));
        assertTrue(WorldQuestSchedule.problem("losttales:a").length() > 0);
    }

    /** 17 October 2026 is a Saturday. */
    @Test
    public void aWeekdayFallsOnThatDayAtThatTime() {
        WorldQuestSchedule saturday = WorldQuestSchedule.parse("losttales:a saturday 18:00");
        long due = saturday.latestDue(at(2026, 10, 18, 9, 0), ZONE);
        assertEquals(at(2026, 10, 17, 18, 0), due);
        assertFalse("the day after, it is not caught up",
                WorldQuestSchedule.isToday(due, at(2026, 10, 18, 9, 0), ZONE));
        assertTrue(WorldQuestSchedule.isToday(due, at(2026, 10, 17, 23, 30), ZONE));
        assertEquals("before the hour, last week's", at(2026, 10, 10, 18, 0),
                saturday.latestDue(at(2026, 10, 17, 17, 59), ZONE));
    }

    @Test
    public void everySoManyDaysFallsTheSameWhateverTheStart() {
        WorldQuestSchedule every = WorldQuestSchedule.parse("losttales:a every 3 days 20:00");
        long first = every.latestDue(at(2026, 10, 18, 21, 0), ZONE);
        long later = every.latestDue(first + 3L * 24L * 60L * 60L * 1000L + 1000L, ZONE);
        assertEquals(3L * 24L * 60L * 60L * 1000L, later - first);
        assertTrue(first <= at(2026, 10, 18, 21, 0));
        assertTrue(first > at(2026, 10, 15, 20, 59));
    }
}
