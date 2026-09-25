package com.ninuna.losttales.gui.style;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Scissors for the interface: cutting what is drawn to a band of rows,
 * a rectangle, or a rectangle in the space the caller draws in, each
 * ended with {@link #end}.
 */
public final class LostTalesUiClip {
    private LostTalesUiClip() {}

    /**
     * Slack for the clip's display-pixel conversion: far above any
     * floating-point error the conversion can accumulate, far below the
     * smallest genuine fraction of a display pixel an edge can carry.
     */
    private static final double CLIP_EDGE_EPSILON = 1.0E-3D;

    /**
     * Scissors drawing to the band between two GUI-space y values: a
     * window that ends part-way through a line cuts it cleanly, a stack
     * sliding under a scroll never reaches past the baseline, and a
     * sprite's shadow never crosses the rule its strip ends on. Either
     * edge may be {@code NaN}, meaning nothing is cut on that side. The
     * rectangle is converted with the display's own pixels per GUI
     * pixel, which is exact at every GUI scale and window size; the
     * whole scissor state is pushed so nothing outlives the clip.
     *
     * <p>A fractional edge is rounded to a whole display pixel, never to
     * the nearest — that would flip between two rows as the stack
     * slides, and the row it gave up would flicker. {@code inward} says
     * which way: the message stack rounds inward, so not one display
     * pixel of a line ever lands on a rule (what it gives up shows the
     * window's own panel, drawn unclipped behind it); a caller whose
     * background continues past the clip rounds outward. The epsilon
     * absorbs floating-point noise, so an edge that lands exactly on a
     * display pixel is cut exactly there either way.</p>
     */
    public static boolean beginRows(Minecraft minecraft, double topY,
                                     double bottomY, boolean inward) {
        return begin(minecraft, Double.NaN, Double.NaN, topY, bottomY,
                inward);
    }

    /**
     * As {@link #beginRows}, cutting on any of the four sides.
     * Any edge may be {@code NaN}, meaning nothing is cut there.
     *
     * <p>The rectangle replaces whatever scissor is in force rather than
     * narrowing it, so a caller clipping inside another clip passes both
     * — the tab row hands its own bottom edge down to each tab, which
     * then adds its own two sides.</p>
     */
    public static boolean begin(Minecraft minecraft, double leftX,
                             double rightX, double topY, double bottomY,
                             boolean inward) {
        try {
            ScaledResolution resolution = new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
            // The GUI ortho maps its exact fractional height onto the
            // display, so one GUI pixel is exactly the scale factor of
            // display pixels — never the display over the ceil-rounded
            // integer height, which drifts a pixel at display sizes the
            // factor does not divide and opened hairline gaps between
            // clipped content and the rules it should meet.
            double pixelsPerGuiPixel =
                    Math.max(1, resolution.getScaleFactor());
            // Scissor space counts up from the bottom of the display, so
            // the GUI's lower edge is the rectangle's origin.
            double lowEdge = minecraft.displayHeight
                    - bottomY * pixelsPerGuiPixel;
            double highEdge = minecraft.displayHeight
                    - topY * pixelsPerGuiPixel;
            int top = Double.isNaN(bottomY) ? 0 : (int)(inward
                    ? Math.ceil(lowEdge - CLIP_EDGE_EPSILON)
                    : Math.floor(lowEdge + CLIP_EDGE_EPSILON));
            int bottom = Double.isNaN(topY) ? minecraft.displayHeight
                    : (int)(inward
                            ? Math.floor(highEdge + CLIP_EDGE_EPSILON)
                            : Math.ceil(highEdge - CLIP_EDGE_EPSILON));
            top = Math.max(0, top);
            bottom = Math.min(minecraft.displayHeight, bottom);
            // Scissor space counts rightward from the display's left, so
            // the horizontal edges need no flip; the rounding is the
            // same rule, inward giving up the pixel a fraction falls in.
            int left = Double.isNaN(leftX) ? 0 : (int)(inward
                    ? Math.ceil(leftX * pixelsPerGuiPixel
                            - CLIP_EDGE_EPSILON)
                    : Math.floor(leftX * pixelsPerGuiPixel
                            + CLIP_EDGE_EPSILON));
            int right = Double.isNaN(rightX) ? minecraft.displayWidth
                    : (int)(inward
                            ? Math.floor(rightX * pixelsPerGuiPixel
                                    + CLIP_EDGE_EPSILON)
                            : Math.ceil(rightX * pixelsPerGuiPixel
                                    - CLIP_EDGE_EPSILON));
            left = Math.max(0, left);
            right = Math.min(minecraft.displayWidth, right);
            if (bottom <= top || right <= left) {
                return false;
            }
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(left, top, right - left, bottom - top);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public static void end(boolean clipped) {
        if (clipped) {
            GL11.glPopAttrib();
        }
    }

    private static final FloatBuffer LOCAL_CLIP_MATRIX =
            BufferUtils.createFloatBuffer(16);

    private static final IntBuffer LOCAL_CLIP_BOX =
            BufferUtils.createIntBuffer(16);

    /**
     * Narrows what is drawn to a rectangle given in the space the caller
     * draws in — the matrix in force only scales and translates — and
     * inside whatever clip already stands, so a picture cut in pieces
     * inside a cut tab stays inside the tab. False when nothing of the
     * rectangle shows, and then nothing is changed and there is nothing
     * to end; otherwise ended with {@link #end}.
     */
    public static boolean beginLocal(Minecraft minecraft, float left, float top,
                                  float right, float bottom) {
        LOCAL_CLIP_MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, LOCAL_CLIP_MATRIX);
        float scaleX = LOCAL_CLIP_MATRIX.get(0);
        float scaleY = LOCAL_CLIP_MATRIX.get(5);
        float shiftX = LOCAL_CLIP_MATRIX.get(12);
        float shiftY = LOCAL_CLIP_MATRIX.get(13);
        int factor = LostTalesDisplayPixels.scaleFactor();
        int x0 = (int)Math.round((shiftX + left * scaleX) * factor);
        int x1 = (int)Math.round((shiftX + right * scaleX) * factor);
        int y0 = minecraft.displayHeight
                - (int)Math.round((shiftY + bottom * scaleY) * factor);
        int y1 = minecraft.displayHeight
                - (int)Math.round((shiftY + top * scaleY) * factor);
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            LOCAL_CLIP_BOX.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, LOCAL_CLIP_BOX);
            int boxX = LOCAL_CLIP_BOX.get(0);
            int boxY = LOCAL_CLIP_BOX.get(1);
            x0 = Math.max(x0, boxX);
            y0 = Math.max(y0, boxY);
            x1 = Math.min(x1, boxX + LOCAL_CLIP_BOX.get(2));
            y1 = Math.min(y1, boxY + LOCAL_CLIP_BOX.get(3));
        }
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x0, y0, x1 - x0, y1 - y0);
        return true;
    }
}
