package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatEpithet;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.util.StatCollector;

/**
 * The chat's time formats, as the game's language writes them: a
 * message's time behind its speaker's name ({@code 9:54 PM} in English),
 * which says the day too once it is not today, the time alone in the
 * timestamp area, and the day alone for a day's rule. Each pattern is a
 * lang line ({@link #CLOCK_KEY}, {@link #DATE_KEY}) read in the locale of
 * the game's language, so month names and the half of the day are that
 * language's too.
 */
public final class ChatTimestampFormatter {
    /** The lang line with the pattern of a time of day: {@code h:mm a}. */
    static final String CLOCK_KEY = "gui.losttales.chat.time.clock";
    /** The lang line with the pattern of a day written out: {@code MMMM d, yyyy}. */
    static final String DATE_KEY = "gui.losttales.chat.time.date";
    /** The language a game without one set writes in. */
    private static final String ENGLISH = "en_US";
    /** The section-sign codes the chat sets a time in italics with. */
    private static final String ITALIC = "§o";
    private static final String RESET = "§r";
    /** The format last made for each key, with the line and language it was made from. */
    private static final Map<String, Made> FORMATS = new HashMap<String, Made>();

    private ChatTimestampFormatter() {}

    /** The time of day the moment falls on: {@code 9:54 PM}. */
    public static synchronized String format(long timestampMillis) {
        return format(CLOCK_KEY, timestampMillis, languageCode());
    }

    /**
     * The moment under the lang line {@code key}, in the locale of the
     * language {@code languageCode} names ({@code de_DE}).
     */
    static synchronized String format(String key, long timestampMillis,
                                      String languageCode) {
        return formatFor(key, languageCode).format(
                new Date(Math.max(0L, timestampMillis)));
    }

    /**
     * The two widest times of day the clock line writes, one before noon
     * and one after, with hours of two digits: what the timestamp area is
     * measured for.
     */
    static synchronized String[] widestTimes() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.JANUARY, 10, 10, 0);
        String language = languageCode();
        String morning = format(CLOCK_KEY, calendar.getTimeInMillis(), language);
        calendar.set(Calendar.HOUR_OF_DAY, 22);
        String evening = format(CLOCK_KEY, calendar.getTimeInMillis(), language);
        return new String[] {morning, evening};
    }

    /**
     * The format the lang line under {@code key} gives in that language:
     * this game's line, else the mod's English line, else the locale's
     * own short time or long date where neither is a pattern.
     */
    private static DateFormat formatFor(String key, String languageCode) {
        String pattern = StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : null;
        Made made = FORMATS.get(key);
        if (made == null || !same(made.pattern, pattern)
                || !same(made.languageCode, languageCode)) {
            Locale locale = locale(languageCode);
            DateFormat format = create(pattern, locale);
            if (format == null) {
                format = create(LostTalesLangFile.english().get(key), locale);
            }
            if (format == null) {
                format = CLOCK_KEY.equals(key)
                        ? DateFormat.getTimeInstance(DateFormat.SHORT, locale)
                        : DateFormat.getDateInstance(DateFormat.LONG, locale);
            }
            made = new Made(pattern, languageCode, format);
            FORMATS.put(key, made);
        }
        return made.format;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    /** A format and the line and language it was made from. */
    private static final class Made {
        final String pattern;
        final String languageCode;
        final DateFormat format;

        Made(String pattern, String languageCode, DateFormat format) {
            this.pattern = pattern;
            this.languageCode = languageCode;
            this.format = format;
        }
    }

    private static DateFormat create(String pattern, Locale locale) {
        if (pattern == null || pattern.trim().length() == 0) {
            return null;
        }
        try {
            return new SimpleDateFormat(pattern, locale);
        } catch (IllegalArgumentException notAPattern) {
            return null;
        }
    }

    /** The locale a language code names: {@code de_DE} is German in Germany. */
    static Locale locale(String languageCode) {
        String code = languageCode == null || languageCode.length() == 0
                ? ENGLISH : languageCode;
        int split = code.indexOf('_');
        return split < 0 ? new Locale(code)
                : new Locale(code.substring(0, split), code.substring(split + 1));
    }

    /** The code of the game's language, or English's where there is no game. */
    private static String languageCode() {
        Minecraft minecraft = Minecraft.getMinecraft();
        Language language = minecraft == null
                || minecraft.getLanguageManager() == null ? null
                : minecraft.getLanguageManager().getCurrentLanguage();
        return language == null ? ENGLISH : language.getLanguageCode();
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
            return words("gui.losttales.chat.time.yesterday", time);
        }
        return words("gui.losttales.chat.time.day",
                formatDay(timestampMillis), time);
    }

    /** The line under {@code key} in the game's language, the mod's English line where the game has none. */
    private static String words(String key, Object... arguments) {
        return ChatEpithet.translate(key, arguments);
    }

    /**
     * The time of day in italics, the way the chat draws it: behind a
     * name, and on its own in the timestamp area beside a later message
     * of a group.
     */
    static String formatDrawnTime(long timestampMillis) {
        return ITALIC + format(timestampMillis) + RESET;
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
     * an older message's stamp write it: in English the month's name, the
     * day and the year.
     */
    public static synchronized String formatDay(long timestampMillis) {
        return format(DATE_KEY, timestampMillis, languageCode());
    }
}
