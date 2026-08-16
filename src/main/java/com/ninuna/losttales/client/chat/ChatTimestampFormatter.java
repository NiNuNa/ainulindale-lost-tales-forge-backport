package com.ninuna.losttales.client.chat;

import java.text.SimpleDateFormat;
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
}
