package com.ninuna.losttales.client.chat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** One centralized short timestamp format for channel-message presentation. */
public final class ChatTimestampFormatter {
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("HH:mm", Locale.ROOT);

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

    /**
     * The day written out the way the unread divider writes today's:
     * the platform's long date in the player's own locale.
     */
    public static String formatDay(long timestampMillis) {
        return DateFormat.getDateInstance(DateFormat.LONG)
                .format(new Date(Math.max(0L, timestampMillis)));
    }
}
