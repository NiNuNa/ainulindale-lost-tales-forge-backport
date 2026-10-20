package com.ninuna.losttales.gui.style;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.BufferUtils;
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
        drawFadingWithin(minecraft, clipLeft, clipRight, Double.NaN, clipTop,
                clipBottom, depth, leftStrength, rightStrength, painter);
    }

    /**
     * As {@link #drawFading}, with nothing drawn past {@code boundRight}:
     * the right edge of the clip the caller stands in, which a slice's own
     * cut would otherwise replace. The fade stays where the words' room
     * ends, and the bound cuts them hard, as a window's edge does.
     */
    public static void drawFadingWithin(Minecraft minecraft, double clipLeft,
                           double clipRight, double boundRight,
                           double clipTop, double clipBottom, float depth,
                           float leftStrength, float rightStrength,
                           FadingPainter painter) {
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
        drawSlice(minecraft, painter, 1.0F, leftEnd, rightStart, boundRight,
                clipTop, clipBottom);
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
                    boundRight, clipTop, clipBottom);
        }
        for (int slice = 0; slice < rightSlices; slice++) {
            float share = 1.0F - rightStrength
                    * (1.0F - (slice + 0.5F) / rightSlices);
            drawSlice(minecraft, painter, share,
                    LostTalesDisplayPixels.snap(
                            rightStart + (rightSlices - slice - 1) / (double)factor),
                    LostTalesDisplayPixels.snap(
                            rightStart + (rightSlices - slice) / (double)factor),
                    boundRight, clipTop, clipBottom);
        }
    }

    /**
     * Whatever {@code painter} draws, cut to {@code clipLeft} to
     * {@code clipRight} in the space the caller draws in — the matrix in
     * force only scales and translates — and inside whatever cut already
     * stands, thinning out into either edge over {@code depth} of that
     * space as far as {@code leftStrength} and {@code rightStrength} say:
     * each stretch at a fading edge one display pixel wide at falling
     * opacity, as {@link #drawFading} cuts them. The cut in force is
     * narrowed, never replaced, so a window's, a list's or a row's own cut
     * still holds.
     */
    public static void drawFadingLocal(Minecraft minecraft, float clipLeft,
                                       float clipRight, float depth,
                                       float leftStrength, float rightStrength,
                                       FadingPainter painter) {
        if (minecraft == null || painter == null || !(clipRight > clipLeft)) {
            return;
        }
        LOCAL_MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, LOCAL_MATRIX);
        float scaleX = LOCAL_MATRIX.get(0);
        float shiftX = LOCAL_MATRIX.get(12);
        if (!(scaleX > 0.0F)) {
            return;
        }
        int factor = LostTalesDisplayPixels.scaleFactor();
        int[] columns = fadeColumns(shiftX * (double)factor,
                scaleX * (double)factor,
                clipLeft, clipRight, depth, leftStrength > 0.0F,
                rightStrength > 0.0F);
        if (columns == null) {
            return;
        }
        int boxLeft = 0;
        int boxBottom = 0;
        int boxRight = minecraft.displayWidth;
        int boxTop = minecraft.displayHeight;
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            LOCAL_BOX.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, LOCAL_BOX);
            boxLeft = LOCAL_BOX.get(0);
            boxBottom = LOCAL_BOX.get(1);
            boxRight = boxLeft + LOCAL_BOX.get(2);
            boxTop = boxBottom + LOCAL_BOX.get(3);
        }
        if (boxTop <= boxBottom || boxRight <= boxLeft) {
            return;
        }
        int first = columns[0];
        int end = columns[1];
        int leftSlices = columns[2];
        int rightSlices = columns[3];
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            paintColumns(painter, 1.0F, first + leftSlices, end - rightSlices,
                    boxLeft, boxRight, boxBottom, boxTop);
            for (int slice = 0; slice < leftSlices; slice++) {
                paintColumns(painter, sliceShare(leftStrength, slice,
                                leftSlices), first + slice, first + slice + 1,
                        boxLeft, boxRight, boxBottom, boxTop);
            }
            for (int slice = 0; slice < rightSlices; slice++) {
                int column = end - 1 - slice;
                paintColumns(painter, sliceShare(rightStrength, slice,
                                rightSlices), column, column + 1, boxLeft,
                        boxRight, boxBottom, boxTop);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * The display columns a local cut takes: its first, the one past its
     * last, and how many of them fade at its left and at its right — each
     * one display pixel, never more than the cut holds between them. The
     * cut's edges round inward, so not a pixel of the words lands past
     * its room. {@code shift} is the matrix's offset in display pixels and
     * {@code perUnit} the display pixels to one unit of the caller's
     * space; null when the cut takes no whole column.
     */
    static int[] fadeColumns(double shift, double perUnit, double clipLeft,
                             double clipRight, float depth, boolean fadeLeft,
                             boolean fadeRight) {
        if (!(perUnit > 0.0D) || !(clipRight > clipLeft)) {
            return null;
        }
        int first = (int)Math.ceil(shift + clipLeft * perUnit
                - LostTalesDisplayPixels.TOLERANCE);
        int end = (int)Math.floor(shift + clipRight * perUnit
                + LostTalesDisplayPixels.TOLERANCE);
        int whole = end - first;
        if (whole <= 0) {
            return null;
        }
        double zone = Math.max(0.0D, Math.min(depth,
                (clipRight - clipLeft) / 2.0D)) * perUnit;
        int leftSlices = fadeLeft ? (int)Math.round(zone) : 0;
        int rightSlices = fadeRight ? (int)Math.round(zone) : 0;
        leftSlices = Math.min(leftSlices, whole / 2);
        rightSlices = Math.min(rightSlices, whole - leftSlices);
        return new int[] {first, end, leftSlices, rightSlices};
    }

    /**
     * A slice's opacity, read at its middle: {@code slice} 0 the one at
     * the very edge, none there for what has fully gone past it, the
     * whole at the zone's inner end.
     */
    static float sliceShare(float strength, int slice, int slices) {
        return 1.0F - strength * (1.0F - (slice + 0.5F) / slices);
    }

    /** What the painter draws, cut to display columns {@code from} to {@code to} inside the box in force. */
    private static void paintColumns(FadingPainter painter, float share,
                                     int from, int to, int boxLeft,
                                     int boxRight, int boxBottom, int boxTop) {
        int left = Math.max(from, boxLeft);
        int right = Math.min(to, boxRight);
        if (right <= left || share <= 0.0F) {
            return;
        }
        GL11.glScissor(left, boxBottom, right - left, boxTop - boxBottom);
        painter.paint(share);
    }

    private static final FloatBuffer LOCAL_MATRIX =
            BufferUtils.createFloatBuffer(16);
    private static final IntBuffer LOCAL_BOX = BufferUtils.createIntBuffer(16);

    /**
     * What the painter draws, once, cut to one stretch of screen and at
     * {@code boundRight}, which may be NaN for no bound.
     */
    private static void drawSlice(Minecraft minecraft, FadingPainter painter,
                                  float share, double clipLeft,
                                  double clipRight, double boundRight,
                                  double clipTop, double clipBottom) {
        if (!Double.isNaN(boundRight)) {
            clipRight = Math.min(clipRight, boundRight);
        }
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
