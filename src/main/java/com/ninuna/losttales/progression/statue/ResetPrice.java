package com.ninuna.losttales.progression.statue;

/**
 * What a reset at a statue costs a character (Nils, 2026-10-19): the
 * first one {@code first} coins, each later one {@code factor} times the
 * one before, as Zelda's statue asks more each time; both server
 * settings. The price never passes {@link #MOST}.
 */
public final class ResetPrice {
    /** The most coins a reset ever asks. */
    public static final int MOST = 1000000000;

    private ResetPrice() {}

    /** The coins the character's next reset costs, after {@code resets} before it. */
    public static int of(int resets, int first, double factor) {
        if (first <= 0) {
            return 0;
        }
        double price = first * Math.pow(Math.max(1.0D, factor), Math.max(0, resets));
        return price >= MOST || Double.isInfinite(price) ? MOST : (int) Math.round(price);
    }
}
