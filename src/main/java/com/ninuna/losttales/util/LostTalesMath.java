package com.ninuna.losttales.util;

/** Arithmetic that stays in range. */
public final class LostTalesMath {

    private LostTalesMath() {}

    /**
     * The sum, held at {@link Long#MAX_VALUE} where adding a positive
     * {@code right} would overflow: a deadline a duration after now never
     * wraps into the past.
     */
    public static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
