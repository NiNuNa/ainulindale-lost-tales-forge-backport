package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.quest.LostTalesQuestIds;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One line of {@code worldQuestSchedules} in {@code server.cfg}: a world
 * quest started on the server's real clock (QUEST-36), on a weekday
 * ({@code losttales:server/wolf_hunt saturday 18:00}) or every so many days
 * ({@code losttales:server/harvest every 7 days 20:00}), in the server
 * machine's own time zone, since players gather at real times. Every N
 * days counts from 1 January 1970, so the days fall the same whenever the
 * server starts. Pure logic; {@link WorldQuestSchedules} runs it.
 */
public final class WorldQuestSchedule {
    /** The most days between two runs of an "every" schedule. */
    public static final int MAX_EVERY_DAYS = 365;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    private static final String[] WEEKDAYS = { "sunday", "monday", "tuesday",
            "wednesday", "thursday", "friday", "saturday" };

    private final String line;
    private final String questId;
    /** The day of the week, {@link Calendar#SUNDAY} to {@link Calendar#SATURDAY}; 0 for an "every" schedule. */
    private final int weekday;
    private final int everyDays;
    private final int hour;
    private final int minute;

    private WorldQuestSchedule(String line, String questId, int weekday,
                               int everyDays, int hour, int minute) {
        this.line = line;
        this.questId = questId;
        this.weekday = weekday;
        this.everyDays = everyDays;
        this.hour = hour;
        this.minute = minute;
    }

    /** The schedule a line writes, or null for one written otherwise ({@link #problem} says why). */
    public static WorldQuestSchedule parse(String line) {
        return problem(line).length() == 0 ? read(line) : null;
    }

    /** Why a line is no schedule, in words for the log; empty for a good one. */
    public static String problem(String line) {
        String[] words = words(line);
        if (words.length != 3 && words.length != 5) {
            return "it is written <quest id> <weekday> <HH:MM> or <quest id> every <days> days <HH:MM>";
        }
        if (!LostTalesQuestIds.fits(words[0])) {
            return "its quest id is longer than " + LostTalesQuestIds.MAX_BYTES + " bytes";
        }
        if (minuteOfDay(words[words.length - 1]) < 0) {
            return "its time '" + words[words.length - 1] + "' is not HH:MM";
        }
        if (words.length == 3) {
            return weekdayOf(words[1]) == 0
                    ? "'" + words[1] + "' is no weekday" : "";
        }
        if (!"every".equals(words[1]) || !"days".equals(words[3])
                && !"day".equals(words[3])) {
            return "it is written <quest id> every <days> days <HH:MM>";
        }
        int days;
        try {
            days = Integer.parseInt(words[2]);
        } catch (NumberFormatException notANumber) {
            return "'" + words[2] + "' is no number of days";
        }
        return days < 1 || days > MAX_EVERY_DAYS
                ? "it runs every 1 to " + MAX_EVERY_DAYS + " days" : "";
    }

    private static WorldQuestSchedule read(String line) {
        String[] words = words(line);
        int minuteOfDay = minuteOfDay(words[words.length - 1]);
        return new WorldQuestSchedule(line.trim(), words[0],
                words.length == 3 ? weekdayOf(words[1]) : 0,
                words.length == 5 ? Integer.parseInt(words[2]) : 0,
                minuteOfDay / 60, minuteOfDay % 60);
    }

    public String getQuestId() {
        return this.questId;
    }

    /** The line as written, trimmed: what its last start is kept under. */
    public String getLine() {
        return this.line;
    }

    /**
     * The latest moment, at or before {@code nowMillis}, the schedule
     * starts its quest, in {@code zone}; -1 for none in the last
     * {@link #MAX_EVERY_DAYS} days.
     */
    public long latestDue(long nowMillis, TimeZone zone) {
        Calendar day = Calendar.getInstance(zone, Locale.ROOT);
        day.setTimeInMillis(nowMillis);
        day.set(Calendar.HOUR_OF_DAY, this.hour);
        day.set(Calendar.MINUTE, this.minute);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        for (int back = 0; back <= MAX_EVERY_DAYS; back++) {
            if (day.getTimeInMillis() <= nowMillis && fallsOn(day, zone)) {
                return day.getTimeInMillis();
            }
            day.add(Calendar.DAY_OF_MONTH, -1);
        }
        return -1L;
    }

    /**
     * Whether a due moment is to be kept now: it falls on the same day as
     * {@code nowMillis}, so a time missed while the server was down is
     * caught up at start-up only that day.
     */
    public static boolean isToday(long dueMillis, long nowMillis, TimeZone zone) {
        if (dueMillis < 0L) {
            return false;
        }
        return dayNumber(dueMillis, zone) == dayNumber(nowMillis, zone);
    }

    private boolean fallsOn(Calendar day, TimeZone zone) {
        if (this.weekday != 0) {
            return day.get(Calendar.DAY_OF_WEEK) == this.weekday;
        }
        return dayNumber(day.getTimeInMillis(), zone) % this.everyDays == 0;
    }

    /** Days since 1 January 1970 on the zone's calendar. */
    private static long dayNumber(long millis, TimeZone zone) {
        return Math.floorDiv(millis + zone.getOffset(millis), DAY_MILLIS);
    }

    private static String[] words(String line) {
        String trimmed = line == null ? "" : line.trim();
        String[] words = trimmed.length() == 0 ? new String[0] : trimmed.split("\\s+");
        // The quest id keeps its case; every other word is read in lower case.
        for (int index = 1; index < words.length; index++) {
            words[index] = words[index].toLowerCase(Locale.ROOT);
        }
        return words;
    }

    private static int weekdayOf(String word) {
        for (int index = 0; index < WEEKDAYS.length; index++) {
            if (WEEKDAYS[index].equals(word)) {
                return Calendar.SUNDAY + index;
            }
        }
        return 0;
    }

    /** The minute of the day {@code HH:MM} names; -1 for anything else. */
    private static int minuteOfDay(String time) {
        String[] parts = time.split(":");
        if (parts.length != 2 || parts[0].length() == 0 || parts[1].length() != 2) {
            return -1;
        }
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            return hour < 0 || hour > 23 || minute < 0 || minute > 59 ? -1
                    : hour * 60 + minute;
        } catch (NumberFormatException notATime) {
            return -1;
        }
    }
}
