package com.ninuna.losttales.client.chat;

import java.util.Calendar;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A stamp shows the time alone; resting the pointer on it reads out the
 * whole moment, the day of the week and the date included, in the local
 * zone the stamp itself was written in.
 */
public final class ChatTimestampFormatterTest {

    @Test
    public void aStampReadsOutItsWholeMoment() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.SEPTEMBER, 14, 19, 4);
        long moment = calendar.getTimeInMillis();
        assertEquals("19:04", ChatTimestampFormatter.format(moment));
        assertEquals("Monday, September 14, 2026 at 19:04",
                ChatTimestampFormatter.formatFull(moment));
    }
}
