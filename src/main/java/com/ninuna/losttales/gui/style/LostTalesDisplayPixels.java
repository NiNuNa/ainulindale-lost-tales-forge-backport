package com.ninuna.losttales.gui.style;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * The display's own pixel grid, which everything drawn as pixel art is
 * laid on: how many display pixels one GUI pixel is, and where a point
 * of the matrix a caller is drawing in lands on it. Only whole display
 * pixels keep pixel art crisp, and keep it still while what carries it
 * moves by fractions of a pixel.
 */
public final class LostTalesDisplayPixels {
    /** The display the scale factor below was measured for. */
    private static int measuredWidth;
    private static int measuredHeight;
    private static int measuredFactor = 1;
    private static int measuredGuiScale = -1;
    private static boolean measuredUnicode;
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    /**
     * How far past a display pixel's edge a snapped point stands: a
     * sixteenth of a pixel, so a texel edge that falls on a pixel's
     * middle — the headwear's, drawn a quarter larger than its texels —
     * always falls the same side of it. Exactly on the middle, which
     * texel a pixel shows is left to rounding, and rounding changes as
     * the point moves.
     */
    static final double TIE_BREAK = 1.0D / 16.0D;

    private LostTalesDisplayPixels() {}

    /**
     * Display pixels per GUI pixel. It is asked several times for every
     * window of every frame, so it is measured once for each display
     * size, GUI Scale option and font choice — everything the answer is
     * made of. The option and the font change it with the window left
     * as it is; without them in the key, everything laid on display
     * pixels would land between them after a scale change.
     */
    public static int scaleFactor() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.displayWidth <= 0
                || minecraft.displayHeight <= 0
                || minecraft.gameSettings == null) {
            return 1;
        }
        int guiScale = minecraft.gameSettings.guiScale;
        boolean unicode = minecraft.func_152349_b();
        if (minecraft.displayWidth == measuredWidth
                && minecraft.displayHeight == measuredHeight
                && guiScale == measuredGuiScale
                && unicode == measuredUnicode) {
            return measuredFactor;
        }
        try {
            measuredFactor = Math.max(1, new ScaledResolution(minecraft,
                    minecraft.displayWidth,
                    minecraft.displayHeight).getScaleFactor());
            measuredWidth = minecraft.displayWidth;
            measuredHeight = minecraft.displayHeight;
            measuredGuiScale = guiScale;
            measuredUnicode = unicode;
        } catch (RuntimeException unavailable) {
            return 1;
        }
        return measuredFactor;
    }

    /**
     * How far a point, {@code x} and {@code y} in the units of the matrix
     * drawn in right now, moves to land on a whole display pixel — and
     * {@link #TIE_BREAK} past it — written to {@code out} in the same
     * units: what pixel art whose texels do not fill whole pixels stands
     * on, so each of its texels takes the same pixels wherever it is
     * carried. Read off the matrix itself, so every caller is answered
     * alike; under a matrix that turns, nothing lines up with the grid
     * and nothing moves.
     */
    public static void snapShift(float x, float y, float[] out) {
        out[0] = 0.0F;
        out[1] = 0.0F;
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        if (MATRIX.get(1) != 0.0F || MATRIX.get(4) != 0.0F) {
            return;
        }
        int factor = scaleFactor();
        out[0] = snapDelta(x, MATRIX.get(0), MATRIX.get(12), factor);
        out[1] = snapDelta(y, MATRIX.get(5), MATRIX.get(13), factor);
    }

    /**
     * How far {@code at}, in units drawn at {@code scale} GUI pixels each
     * from {@code shift}, moves to land {@link #TIE_BREAK} past the
     * nearest whole display pixel of a display {@code factor} pixels to a
     * GUI pixel; nothing for a scale that draws nothing.
     */
    static float snapDelta(float at, float scale, float shift, int factor) {
        if (scale == 0.0F || factor <= 0) {
            return 0.0F;
        }
        double display = ((double)at * scale + shift) * factor;
        return (float)((Math.floor(display + 0.5D) + TIE_BREAK - display)
                / (scale * factor));
    }
}
