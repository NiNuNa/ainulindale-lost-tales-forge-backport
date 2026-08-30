package com.ninuna.losttales.client.gui.animation;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * A blurred copy of the frame for panels that soften the world behind
 * their own rectangle while everything around them stays sharp — the
 * open chat windows above all. Once per frame the current frame is
 * captured, blurred with the same Gaussian shader the full-screen GUI
 * blur uses, captured again, and the sharp copy is put back; the
 * blurred copy is then a texture any caller may paste a region of, in
 * GUI coordinates. Everything is best-effort: without framebuffers or
 * the shader nothing is captured, {@link #drawRegion} draws nothing,
 * and the panels keep their plain backdrop.
 */
public final class LostTalesGuiRegionBlur {
    private static final LostTalesGuiRegionBlur INSTANCE =
            new LostTalesGuiRegionBlur();
    /** A capture older than this is another frame's; never drawn. */
    private static final long FRESH_NANOS = 250L * 1000000L;

    private final LostTalesGuiBlurRenderer blur =
            new LostTalesGuiBlurRenderer();
    private int sharpTexture = -1;
    private int blurredTexture = -1;
    private int width = -1;
    private int height = -1;
    /**
     * The GUI projection's exact fractional size at capture time. The
     * capture fills the display, and the ortho maps this size onto the
     * display exactly, so sampling with it keeps a pasted region on the
     * very pixels the world drew — the ceil-rounded integer size would
     * shift the content up to a pixel at display sizes the scale factor
     * does not divide.
     */
    private double guiWidth = 1.0D;
    private double guiHeight = 1.0D;
    private long capturedNanos;

    private LostTalesGuiRegionBlur() {}

    public static LostTalesGuiRegionBlur getInstance() {
        return INSTANCE;
    }

    /**
     * Captures and blurs the frame as drawn so far; the screen looks
     * exactly as before when this returns, and {@link #drawRegion} can
     * paste blurred rectangles for the rest of the frame. Called before
     * anything of the caller's own is drawn.
     */
    public boolean capture(Minecraft minecraft, float partialTicks,
                           float strength) {
        this.capturedNanos = 0L;
        if (minecraft == null || minecraft.theWorld == null
                || minecraft.getFramebuffer() == null
                || !OpenGlHelper.isFramebufferEnabled()
                || strength <= 0.01F || minecraft.displayWidth <= 0
                || minecraft.displayHeight <= 0) {
            return false;
        }
        try {
            ensureTextures(minecraft);
            ScaledResolution resolution = new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
            this.guiWidth = Math.max(1.0D,
                    resolution.getScaledWidth_double());
            this.guiHeight = Math.max(1.0D,
                    resolution.getScaledHeight_double());
            copyFrameInto(this.sharpTexture, minecraft);
            if (!this.blur.render(minecraft, partialTicks, strength)) {
                return false;
            }
            copyFrameInto(this.blurredTexture, minecraft);
            drawFullFrame(this.sharpTexture, minecraft);
            this.capturedNanos = System.nanoTime();
            return true;
        } catch (RuntimeException failure) {
            release();
            return false;
        }
    }

    /** Whether a region drawn now would show this frame's capture. */
    public boolean isFresh() {
        return this.capturedNanos > 0L
                && System.nanoTime() - this.capturedNanos < FRESH_NANOS;
    }

    /**
     * Pastes the blurred frame inside one GUI-space rectangle. Nothing
     * is drawn without a fresh capture, so callers need not know
     * whether the blur is available at all.
     */
    public void drawRegion(double left, double top, double right,
                           double bottom, float opacity) {
        drawFadedRegion(left, top, right, bottom, null, opacity);
    }

    /**
     * As {@link #drawRegion}, but the blur thins out across the region
     * the way the chat backdrop does. {@code fadeWeights} is that
     * profile sampled evenly from the left edge to the right — one more
     * entry than the steps it is drawn in — and {@code null} fades
     * nothing.
     */
    public void drawFadedRegion(double left, double top, double right,
                                double bottom, float[] fadeWeights,
                                float opacity) {
        drawMapped(left, top, right, bottom, fadeWeights,
                left, top, right, bottom, opacity);
    }

    /**
     * As {@link #drawFadedRegion} for callers drawing inside their own
     * translate and scale: the quad's vertices are the local coordinates
     * given — the current transform places them — while the texture is
     * sampled at the screen rectangle they map to, described by where
     * the local origin lands ({@code screenLeft}, {@code screenTop}) and
     * the transform's scale.
     */
    public void drawFadedRegionInTransform(
            double localLeft, double localTop, double localRight,
            double localBottom, float[] fadeWeights, double screenLeft,
            double screenTop, double scale, float opacity) {
        if (scale <= 0.0D) {
            return;
        }
        drawMapped(localLeft, localTop, localRight, localBottom,
                fadeWeights,
                screenLeft + (localLeft) * scale,
                screenTop + (localTop) * scale,
                screenLeft + (localRight) * scale,
                screenTop + (localBottom) * scale,
                opacity);
    }

    private void drawMapped(double vertexLeft, double vertexTop,
                            double vertexRight, double vertexBottom,
                            float[] fadeWeights, double screenLeft,
                            double screenTop, double screenRight,
                            double screenBottom, float opacity) {
        if (!isFresh() || this.blurredTexture < 0
                || vertexRight <= vertexLeft
                || vertexBottom <= vertexTop || opacity <= 0.0F) {
            return;
        }
        // Sampled against the projection's exact size, so a region lands
        // on the very pixels the world drew; see {@link #guiWidth}.
        double u0 = clamp01(screenLeft / this.guiWidth);
        double u1 = clamp01(screenRight / this.guiWidth);
        // The capture reads bottom-up; GUI space counts down from the top.
        double v0 = clamp01(1.0D - screenBottom / this.guiHeight);
        double v1 = clamp01(1.0D - screenTop / this.guiHeight);
        int alpha = Math.max(0, Math.min(255,
                (int)Math.round(255.0D * opacity)));
        int steps = fadeWeights == null ? 1 : fadeWeights.length - 1;
        if (steps < 1) {
            return;
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.blurredTexture);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        // One straight piece per step, the profile read at each of its
        // edges: a quad blends in a straight line between its own edges,
        // so a curve is drawn as a run of them.
        for (int step = 0; step < steps; step++) {
            double from = step / (double)steps;
            double to = (step + 1) / (double)steps;
            double x0 = vertexLeft + (vertexRight - vertexLeft) * from;
            double x1 = vertexLeft + (vertexRight - vertexLeft) * to;
            double uFrom = u0 + (u1 - u0) * from;
            double uTo = u0 + (u1 - u0) * to;
            int a0 = fadeWeights == null ? alpha
                    : Math.round(alpha * fadeWeights[step]);
            int a1 = fadeWeights == null ? alpha
                    : Math.round(alpha * fadeWeights[step + 1]);
            tessellator.setColorRGBA(255, 255, 255, a0);
            tessellator.addVertexWithUV(x0, vertexBottom, 0.0D, uFrom, v0);
            tessellator.setColorRGBA(255, 255, 255, a1);
            tessellator.addVertexWithUV(x1, vertexBottom, 0.0D, uTo, v0);
            tessellator.addVertexWithUV(x1, vertexTop, 0.0D, uTo, v1);
            tessellator.setColorRGBA(255, 255, 255, a0);
            tessellator.addVertexWithUV(x0, vertexTop, 0.0D, uFrom, v1);
        }
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void release() {
        this.blur.release();
        releaseTextures();
        this.capturedNanos = 0L;
    }

    public void resetAfterResourceReload() {
        this.blur.resetAfterResourceReload();
        releaseTextures();
        this.capturedNanos = 0L;
    }

    /**
     * The whole sharp frame back over the blurred one, one to one. The
     * quad's extent is the GUI projection's exact fractional size: the
     * ortho maps that — not the ceil-rounded integer size — onto the
     * display, so an integer-sized quad at a display size the scale
     * factor does not divide would stretch the frame a pixel down and
     * right, and the whole world would appear to shift while the chat
     * is open.
     */
    private static void drawFullFrame(int texture, Minecraft minecraft) {
        ScaledResolution resolution = new ScaledResolution(minecraft,
                minecraft.displayWidth, minecraft.displayHeight);
        double width = resolution.getScaledWidth_double();
        double height = resolution.getScaledHeight_double();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0.0D, height, 0.0D, 0.0D, 0.0D);
        tessellator.addVertexWithUV(width, height, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(width, 0.0D, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(0.0D, 0.0D, 0.0D, 0.0D, 1.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    private void ensureTextures(Minecraft minecraft) {
        if (this.sharpTexture >= 0 && this.width == minecraft.displayWidth
                && this.height == minecraft.displayHeight) {
            return;
        }
        releaseTextures();
        this.width = minecraft.displayWidth;
        this.height = minecraft.displayHeight;
        this.sharpTexture = createTexture(this.width, this.height);
        this.blurredTexture = createTexture(this.width, this.height);
    }

    private static int createTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, width,
                height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer)null);
        return texture;
    }

    private static void copyFrameInto(int texture, Minecraft minecraft) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                minecraft.displayWidth, minecraft.displayHeight);
    }

    private void releaseTextures() {
        if (this.sharpTexture >= 0) {
            GL11.glDeleteTextures(this.sharpTexture);
        }
        if (this.blurredTexture >= 0) {
            GL11.glDeleteTextures(this.blurredTexture);
        }
        this.sharpTexture = -1;
        this.blurredTexture = -1;
        this.width = -1;
        this.height = -1;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
