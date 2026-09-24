package com.ninuna.losttales.quest;

/**
 * A quest's time limit or time left, written the one way every screen
 * writes it: in-game time, two parts at most, a part that is nothing left
 * out: {@code 1d 3h}, {@code 1d}, {@code 3h 20m}, {@code 20m}. Time limits
 * run on the world's clock, so an in-game day is 24000 ticks and an hour
 * 1000.
 */
public final class LostTalesQuestTimeText {
    public static final long TICKS_PER_DAY = 24000L;
    public static final long TICKS_PER_HOUR = 1000L;

    private LostTalesQuestTimeText() {}

    /** {@code ticks} as the short form; a minute at least for any time at all, {@code 0m} for none. */
    public static String shortForm(long ticks) {
        long time = Math.max(0L, ticks);
        long days = time / TICKS_PER_DAY;
        long hours = time % TICKS_PER_DAY / TICKS_PER_HOUR;
        long minutes = time % TICKS_PER_HOUR * 60L / TICKS_PER_HOUR;
        if (days > 0L) {
            return hours > 0L ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0L) {
            return minutes > 0L ? hours + "h " + minutes + "m" : hours + "h";
        }
        return (time > 0L ? Math.max(1L, minutes) : 0L) + "m";
    }
}
