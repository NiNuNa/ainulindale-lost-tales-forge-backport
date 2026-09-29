package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;

/**
 * A short line over a window's bar saying what just happened to it: why
 * a page's tab closed by itself. It fades in as it rises its last pixels
 * into place, stands while it is read, and fades out where it stands
 * ({@link MotionIds#WINDOW_NOTICE}, its {@code hold} and {@code rise}).
 * It keeps the place it was shown at, so it stays where the bar stood
 * even once the window has gone with its last tab. One stands at a
 * time; a new one takes the old one's place. A page's answer over its
 * bar is drawn the same way ({@link #drawLine}).
 */
final class WindowNotice {
    /** Clear pixels between the notice and the bar under it, as over the chat's bar. */
    private static final int GAP = 4;
    /** Clear pixels it keeps from the screen's sides. */
    private static final int MARGIN = 2;

    private final MotionTransition fade =
            new MotionTransition(MotionIds.WINDOW_NOTICE);
    private String text = "";
    private long shownNanos;
    /** The middle it is centred on and the bar's top it stands over, in GUI pixels. */
    private double centreX;
    private double barTop;

    /** Shows {@code text} centred on {@code centreX}, over a bar whose top is {@code barTop}. */
    void show(String text, double centreX, double barTop) {
        this.text = text == null ? "" : text;
        this.centreX = centreX;
        this.barTop = barTop;
        this.shownNanos = System.nanoTime();
        this.fade.settle(false);
    }

    /** Draws it while it stands, over every window. */
    void draw(FontRenderer font, int screenWidth, long now) {
        if (this.text.length() == 0) {
            return;
        }
        long holdNanos = (long)(Motions.param(MotionIds.WINDOW_NOTICE,
                "hold", 1600.0F) * 1000000L);
        boolean standing = now - this.shownNanos < holdNanos;
        float shown = this.fade.advance(now, standing);
        if (!standing && this.fade.isSettled() && shown <= 0.0F) {
            this.text = "";
            return;
        }
        float opacity = this.fade.clamped();
        if (opacity <= 0.0F) {
            return;
        }
        drawLine(font, this.text, LostTalesUiInk.IVORY, this.centreX,
                this.barTop, 0.0F, screenWidth, opacity, standing
                        ? (1.0F - opacity) * Motions.param(
                                MotionIds.WINDOW_NOTICE, "rise", 3.0F)
                        : 0.0F);
    }

    /**
     * One notice line as every notice and page answer is drawn: a
     * one-line popup centred on {@code centreX}, {@link #GAP} clear pixels
     * over a bar whose top is {@code barTop} and which stands
     * {@code barOffset} below it now, kept {@link #MARGIN} inside the
     * screen's sides, its words in {@code rgb}, {@code rise} pixels short
     * of its place. The fractions of its place and its rise are drawn
     * through the matrix, never rounded to a pixel.
     */
    static void drawLine(FontRenderer font, String text, int rgb,
                         double centreX, double barTop, float barOffset,
                         int screenWidth, float opacity, float rise) {
        if (text == null || text.length() == 0 || opacity <= 0.0F) {
            return;
        }
        int width = WindowStyle.popupLineWidth(font, text);
        int left = (int)Math.floor(centreX) - width / 2;
        left = Math.max(MARGIN, Math.min(screenWidth - width - MARGIN, left));
        int top = (int)Math.floor(barTop) - GAP
                - WindowStyle.POPUP_LINE_HEIGHT;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float)(centreX - Math.floor(centreX)),
                    (float)(barTop - Math.floor(barTop)) + barOffset + rise,
                    0.0F);
            LostTalesUiInk.beginContent();
            WindowStyle.drawPopupLine(font, text, rgb, left, top, opacity);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
