package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Calendar;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The chat writes a time and a day by the patterns of the game's
 * language, in that language's locale: a German game reads a 24-hour
 * clock and German month names, and the timestamp area is measured for
 * what the pattern writes, with no half of the day where it has none.
 */
public final class ChatTimestampPatternsTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** The English lines of the patterns, as the lang file has them. */
    @After
    public void readInEnglishAgain() {
        inject(ChatTimestampFormatter.CLOCK_KEY + "="
                + LostTalesLangFile.english().get(ChatTimestampFormatter.CLOCK_KEY) + "\n"
                + ChatTimestampFormatter.DATE_KEY + "="
                + LostTalesLangFile.english().get(ChatTimestampFormatter.DATE_KEY) + "\n");
    }

    @Test
    public void englishKeepsItsTwelveHourClock() {
        assertTrue(EnglishWords.INSTANCE.has(ChatTimestampFormatter.CLOCK_KEY));
        assertTrue(EnglishWords.INSTANCE.has(ChatTimestampFormatter.DATE_KEY));
        long evening = at(2026, Calendar.SEPTEMBER, 14, 19, 4);
        assertEquals("7:04 PM", ChatTimestampFormatter.format(
                ChatTimestampFormatter.CLOCK_KEY, evening, "en_US"));
        assertEquals("September 14, 2026", ChatTimestampFormatter.format(
                ChatTimestampFormatter.DATE_KEY, evening, "en_US"));
        assertEquals(Arrays.asList("10:00 AM", "10:00 PM"),
                Arrays.asList(ChatTimestampFormatter.widestTimes()));
    }

    @Test
    public void germanReadsItsOwnPatternsAndMonths() {
        inject(ChatTimestampFormatter.CLOCK_KEY + "=HH:mm\n"
                + ChatTimestampFormatter.DATE_KEY + "=d. MMMM yyyy\n");
        long evening = at(2026, Calendar.MARCH, 3, 19, 4);
        assertEquals("19:04", ChatTimestampFormatter.format(
                ChatTimestampFormatter.CLOCK_KEY, evening, "de_DE"));
        assertEquals("3. März 2026", ChatTimestampFormatter.format(
                ChatTimestampFormatter.DATE_KEY, evening, "de_DE"));
        // No half of the day is measured where the clock writes none.
        assertEquals(Arrays.asList("10:00", "22:00"),
                Arrays.asList(ChatTimestampFormatter.widestTimes()));
    }

    /** A line that is no pattern falls back to the mod's English one. */
    @Test
    public void aBrokenPatternFallsBackToEnglish() {
        inject(ChatTimestampFormatter.CLOCK_KEY + "=qq:qq\n");
        assertEquals("7:04 PM", ChatTimestampFormatter.format(
                ChatTimestampFormatter.CLOCK_KEY,
                at(2026, Calendar.SEPTEMBER, 14, 19, 4), "en_US"));
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTimeInMillis();
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
