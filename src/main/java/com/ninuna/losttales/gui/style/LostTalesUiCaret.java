package com.ninuna.losttales.gui.style;

import net.minecraft.client.gui.Gui;

/**
 * The mod's one text caret, the chat bar's: a one-pixel ivory bar with
 * the one plum-black shadow, and one blink for every field that has it.
 *
 * <p>It stands lit for {@link #LIT_MILLIS}, then out for as long, timed
 * from the moment its field last took a key or its caret last moved, so
 * it stays lit while somebody types, as a desktop's caret does.</p>
 *
 * <p>Its shadow lands on the column of the glyph the caret stands
 * before, so a field draws {@link #drawShadow} before its text and
 * {@link #drawBar} after it; where no glyph follows, {@link #draw} does
 * both. Only the field that holds the keys draws one: the screen that
 * owns the fields decides which that is.</p>
 */
public final class LostTalesUiCaret {
    /** The caret's width, wherever it stands. */
    public static final int WIDTH = 1;
    /**
     * The caret's height: an emoji's ten rows, from the row over the
     * capitals down to the shadow under the descenders.
     */
    public static final int HEIGHT = 10;
    /** How long the caret stays lit, and then how long it stays out. */
    public static final long LIT_MILLIS = 530L;

    private LostTalesUiCaret() {}

    /** Where the caret's top stands for text whose top is {@code textTop}: a row above it. */
    public static int topFor(int textTop) {
        return textTop - 1;
    }

    /**
     * Whether a caret whose field last took a key or moved its caret at
     * {@code sinceNanos} is lit at {@code nowNanos}.
     */
    public static boolean isLit(long sinceNanos, long nowNanos) {
        long elapsed = (nowNanos - sinceNanos) / 1000000L;
        return elapsed < 0L || elapsed % (2L * LIT_MILLIS) < LIT_MILLIS;
    }

    /** The caret and its shadow, where no glyph stands after it. */
    public static void draw(int x, int top, int height, int alpha) {
        drawShadow(x, top, height, alpha);
        drawBar(x, top, height, alpha);
    }

    /** The bar: ivory, {@code height} rows from {@code top}. */
    public static void drawBar(int x, int top, int height, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        Gui.drawRect(x, top, x + WIDTH, top + height,
                LostTalesUiInk.argb(LostTalesUiInk.IVORY, alpha));
        LostTalesUiInk.beginContent();
    }

    /** The bar's shadow, a pixel down and to the right, as every glyph casts one. */
    public static void drawShadow(int x, int top, int height, int alpha) {
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int left = x + LostTalesUiInk.SHADOW_OFFSET;
        int shadowTop = top + LostTalesUiInk.SHADOW_OFFSET;
        Gui.drawRect(left, shadowTop, left + WIDTH, shadowTop + height,
                LostTalesUiInk.argb(LostTalesUiInk.SHADOW, shadow));
        LostTalesUiInk.beginContent();
    }
}
