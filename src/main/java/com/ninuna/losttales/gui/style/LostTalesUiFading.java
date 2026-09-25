package com.ninuna.losttales.gui.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;

/**
 * Words and pictures cut at an edge fade into it rather than stopping
 * hard: the stretch at the edge is drawn in one-pixel slices at falling
 * opacity, as a browser's cut tab name is.
 */
public final class LostTalesUiFading {
    private LostTalesUiFading() {}

    /** How deep a cut edge's fade reaches at most: one line of a list. */
    public static final float REACH = 12.0F;

    /** Something drawn once per slice of a fading cut, at a share of its opacity. */
    public interface FadingPainter {
        void paint(float share);
    }

    /**
     * Words drawn between two clip edges, thinning out into either edge
     * over {@code depth} pixels as far as {@code leftStrength} and
     * {@code rightStrength} say (0..1, how far the words have gone past
     * that edge): the stretch at a fading edge is drawn in one-pixel
     * slices at falling opacity, so the words themselves fade rather
     * than a wash being laid over them, and the fade is seamless over
     * whatever surface they stand on, as a browser's cut tab name is.
     * The clip edges are in screen space; {@code x} and {@code fraction}
     * are in the caller's matrix, and {@code clipBottom} may be NaN.
     */
    public static void drawFadingText(Minecraft minecraft, FontRenderer font,
                               String text, int x, float fraction, int y,
                               int rgb, int alpha, double clipLeft,
                               double clipRight, double clipBottom,
                               float depth, float leftStrength,
                               float rightStrength) {
        drawFadingText(minecraft, font, text, x, fraction, y, rgb, alpha,
                clipLeft, clipRight, Double.NaN, clipBottom, depth,
                leftStrength, rightStrength);
    }

    /** As above, cut at {@code clipTop} as well; either may be NaN. */
    public static void drawFadingText(Minecraft minecraft, final FontRenderer font,
                               final String text, final int x,
                               final float fraction, final int y,
                               final int rgb, final int alpha,
                               double clipLeft, double clipRight,
                               double clipTop, double clipBottom, float depth,
                               float leftStrength, float rightStrength) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        drawFading(minecraft, clipLeft, clipRight, clipTop, clipBottom, depth,
                leftStrength, rightStrength, new FadingPainter() {
                    @Override
                    public void paint(float share) {
                        int sliceAlpha = Math.round(alpha * share);
                        if (sliceAlpha < LostTalesUiInk
                                .MIN_VISIBLE_ALPHA) {
                            return;
                        }
                        GL11.glPushMatrix();
                        try {
                            GL11.glTranslatef(fraction, 0.0F, 0.0F);
                            LostTalesUiInk.drawText(font, text,
                                    x, y, rgb, sliceAlpha);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                });
    }

    /**
     * Whatever {@code painter} draws — words, an icon — between two clip
     * edges, thinning out into either edge over {@code depth} pixels as
     * far as {@code leftStrength} and {@code rightStrength} say (0..1,
     * how far it has gone past that edge): the stretch at a fading edge
     * is drawn in one-pixel slices at falling opacity, so the thing
     * itself fades rather than a wash being laid over it, and the fade
     * is seamless over whatever surface it stands on. The clip edges are
     * in screen space; either vertical one may be NaN.
     */
    public static void drawFading(Minecraft minecraft, double clipLeft,
                           double clipRight, double clipTop,
                           double clipBottom, float depth, float leftStrength,
                           float rightStrength, FadingPainter painter) {
        if (clipRight <= clipLeft) {
            return;
        }
        double leftZone = leftStrength > 0.0F ? Math.min(depth,
                (clipRight - clipLeft) / 2.0D) : 0.0D;
        double rightZone = rightStrength > 0.0F ? Math.min(depth,
                (clipRight - clipLeft) / 2.0D) : 0.0D;
        // Every slice is one display pixel wide, its edges laid on the
        // display grid: the clip rounds inward, and two neighbours
        // meeting on a fraction of a pixel would each give that pixel
        // up and leave a gap in the words.
        int factor = LostTalesDisplayPixels.scaleFactor();
        int leftSlices = (int)Math.round(leftZone * factor);
        int rightSlices = (int)Math.round(rightZone * factor);
        double leftEnd = LostTalesDisplayPixels.snap(
                clipLeft + leftSlices / (double)factor);
        double rightStart = LostTalesDisplayPixels.snap(
                clipRight - rightSlices / (double)factor);
        drawSlice(minecraft, painter, 1.0F, leftEnd, rightStart, clipTop,
                clipBottom);
        for (int slice = 0; slice < leftSlices; slice++) {
            // A slice's opacity is read at its middle: none at the very
            // edge for what has fully gone past it, the whole at the
            // zone's inner end.
            float share = 1.0F - leftStrength
                    * (1.0F - (slice + 0.5F) / leftSlices);
            drawSlice(minecraft, painter, share,
                    LostTalesDisplayPixels.snap(
                            leftEnd - (leftSlices - slice) / (double)factor),
                    LostTalesDisplayPixels.snap(
                            leftEnd - (leftSlices - slice - 1) / (double)factor),
                    clipTop, clipBottom);
        }
        for (int slice = 0; slice < rightSlices; slice++) {
            float share = 1.0F - rightStrength
                    * (1.0F - (slice + 0.5F) / rightSlices);
            drawSlice(minecraft, painter, share,
                    LostTalesDisplayPixels.snap(
                            rightStart + (rightSlices - slice - 1) / (double)factor),
                    LostTalesDisplayPixels.snap(
                            rightStart + (rightSlices - slice) / (double)factor),
                    clipTop, clipBottom);
        }
    }

    /** What the painter draws, once, cut to one stretch of screen. */
    private static void drawSlice(Minecraft minecraft, FadingPainter painter,
                                  float share, double clipLeft,
                                  double clipRight, double clipTop,
                                  double clipBottom) {
        if (clipRight <= clipLeft || share <= 0.0F) {
            return;
        }
        boolean clipped = LostTalesUiClip.begin(minecraft, clipLeft, clipRight,
                clipTop, clipBottom, true);
        try {
            painter.paint(share);
        } finally {
            LostTalesUiClip.end(clipped);
        }
    }

    /**
     * How strongly words fade into an edge they are cut at: as far as
     * they have gone past the edge, up to the fade's depth. The fade
     * comes and goes with the marquee instead of appearing, and an edge
     * nothing is cut at has none.
     */
    public static float sideFadeStrength(double hiddenPixels, float depth) {
        if (depth <= 0.0F || hiddenPixels <= 0.0D) {
            return 0.0F;
        }
        return (float)Math.min(1.0D, hiddenPixels / depth);
    }

    /**
     * How deep a side fade reaches into a clip {@code width} pixels wide:
     * one line, as the history's top shade does, but never past a third of
     * the clip, so a short name keeps its middle clear.
     */
    public static float sideFadeDepth(double width) {
        return (float)Math.max(0.0D,
                Math.min(REACH, width / 3.0D));
    }
}
