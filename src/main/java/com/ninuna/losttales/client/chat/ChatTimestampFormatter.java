package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatEpithet;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The chat's time formats, on a twelve-hour clock: a message's time
 * behind its speaker's name ({@code 9:54 PM}), which says the day too
 * once it is not today, the clock alone in the timestamp area
 * ({@code 9:54}), and the day alone for a day's rule.
 */
public final class ChatTimestampFormatter {
    /** The time of day: {@code 9:54 PM}. */
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("h:mm a", Locale.ROOT);
    /** The clock alone: {@code 9:54}. */
    private static final SimpleDateFormat CLOCK_FORMAT =
            new SimpleDateFormat("h:mm", Locale.ROOT);
    /** The day written out: {@code September 19, 2026}. */
    private static final SimpleDateFormat DAY_FORMAT =
            new SimpleDateFormat("MMMM d, yyyy", Locale.ROOT);
    /** The section-sign codes the chat sets a time in italics with. */
    private static final String ITALIC = "§o";
    private static final String RESET = "§r";

    private ChatTimestampFormatter() {}

    /** The time of day the moment falls on: {@code 9:54 PM}. */
    public static synchronized String format(long timestampMillis) {
        return FORMAT.format(new Date(Math.max(0L, timestampMillis)));
    }

    /**
     * A message's time as it stands behind its speaker's name, read
     * against {@code nowMillis}, the way Discord dates a message: the
     * time alone for one said today, {@code Yesterday at 9:54 PM} for
     * one said yesterday, and the day written out before the time for
     * anything else — {@code September 14, 2026 at 9:54 PM}; the time
     * itself in italics and the words round it upright, as the chat
     * draws it.
     */
    static String formatDrawnStamp(long timestampMillis, long nowMillis) {
        String time = formatDrawnTime(timestampMillis);
        long day = dayKey(timestampMillis);
        if (day == dayKey(nowMillis)) {
            return time;
        }
        Calendar yesterday = Calendar.getInstance();
        yesterday.setTimeInMillis(Math.max(0L, nowMillis));
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (day == dayKey(yesterday.getTimeInMillis())) {
            return ChatEpithet.translate("gui.losttales.chat.time.yesterday",
                    "Yesterday at %s", time);
        }
        return ChatEpithet.translate("gui.losttales.chat.time.day",
                "%s at %s", formatDay(timestampMillis), time);
    }

    /** The time of day in italics, the way the chat draws it behind a name. */
    private static String formatDrawnTime(long timestampMillis) {
        return ITALIC + format(timestampMillis) + RESET;
    }

    /**
     * The clock alone, {@code 9:54}, in italics: what the timestamp area
     * shows beside a later message of a group, which the time behind the
     * group's name already puts in its half of the day. It is narrow
     * enough to stand in the area beside the avatar at every GUI scale.
     */
    static synchronized String formatDrawnClock(long timestampMillis) {
        return ITALIC + CLOCK_FORMAT.format(
                new Date(Math.max(0L, timestampMillis))) + RESET;
    }

    /**
     * The day the moment falls on, in the local zone, as a key two
     * moments share exactly when they share a calendar day: what a
     * history reads to stand a dated rule over each day's first message,
     * and what a window's layout is kept for, since a stamp says
     * <em>Yesterday</em> once the day has turned.
     */
    public static long dayKey(long timestampMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Math.max(0L, timestampMillis));
        return calendar.get(Calendar.YEAR) * 1000L
                + calendar.get(Calendar.DAY_OF_YEAR);
    }

    /** Whether two moments fall on the same calendar day in the local zone. */
    public static boolean isSameDay(long firstMillis, long secondMillis) {
        return dayKey(firstMillis) == dayKey(secondMillis);
    }

    /**
     * The day written out, the way a day's rule, the unread divider and
     * an older message's stamp write it: the month's name, the day and
     * the year.
     */
    public static synchronized String formatDay(long timestampMillis) {
        return DAY_FORMAT.format(new Date(Math.max(0L, timestampMillis)));
    }
}
