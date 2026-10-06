package com.ninuna.losttales.quest;

import com.ninuna.losttales.util.LostTalesDuration;
import com.ninuna.losttales.util.LostTalesWords;

/**
 * A quest's time limit or time left, written the one way every screen
 * writes it: in-game time, two parts at most, a part that is nothing left
 * out: {@code 1d 3h}, {@code 1d}, {@code 3h 20m}, {@code 20m}, each unit
 * in the reader's language ({@link LostTalesDuration}). Time limits run on
 * the world's clock, so an in-game day is 24000 ticks and an hour 1000.
 */
public final class LostTalesQuestTimeText {
    public static final long TICKS_PER_DAY = 24000L;
    public static final long TICKS_PER_HOUR = 1000L;

    private LostTalesQuestTimeText() {}

    /** {@code ticks} as the short form in this side's language. */
    public static String shortForm(long ticks) {
        return duration(ticks).write(LostTalesWords.LANG);
    }

    /**
     * {@code ticks} as the short form's parts: a minute at least for any
     * time at all, {@code 0m} for none.
     */
    public static LostTalesDuration duration(long ticks) {
        long time = Math.max(0L, ticks);
        long days = time / TICKS_PER_DAY;
        long hours = time % TICKS_PER_DAY / TICKS_PER_HOUR;
        long minutes = time % TICKS_PER_HOUR * 60L / TICKS_PER_HOUR;
        if (days > 0L) {
            LostTalesDuration written = LostTalesDuration.of(
                    days, LostTalesDuration.Unit.DAYS);
            return hours > 0L
                    ? written.and(hours, LostTalesDuration.Unit.HOURS) : written;
        }
        if (hours > 0L) {
            LostTalesDuration written = LostTalesDuration.of(
                    hours, LostTalesDuration.Unit.HOURS);
            return minutes > 0L
                    ? written.and(minutes, LostTalesDuration.Unit.MINUTES)
                    : written;
        }
        return LostTalesDuration.of(time > 0L ? Math.max(1L, minutes) : 0L,
                LostTalesDuration.Unit.MINUTES);
    }
}
