package com.ninuna.losttales.client.chat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** One centralized short timestamp format for channel-message presentation. */
public final class ChatTimestampFormatter {
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("HH:mm", Locale.ROOT);
    /** The day written out: {@code September 19, 2026}. */
    private static final SimpleDateFormat DAY_FORMAT =
            new SimpleDateFormat("MMMM d, yyyy", Locale.ROOT);

    private ChatTimestampFormatter() {}

    public static synchronized String format(long timestampMillis) {
        return FORMAT.format(new Date(Math.max(0L, timestampMillis)));
    }

    /**
     * The day the moment falls on, in the local zone, as a key two
     * moments share exactly when they share a calendar day: what a
     * history reads to stand a dated rule over each day's first message.
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
     * The day written out, the way a day's rule and the unread divider
     * write it: the month's name, the day and the year.
     */
    public static synchronized String formatDay(long timestampMillis) {
        return DAY_FORMAT.format(new Date(Math.max(0L, timestampMillis)));
    }
}
