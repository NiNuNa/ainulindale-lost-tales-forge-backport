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
                           double bottom, int screenWidth, int screenHeight,
                           float opacity) {
        drawFadedRegion(left, top, right, bottom, Double.NaN,
                screenWidth, screenHeight, opacity);
    }

    /**
     * As {@link #drawRegion}, but the blur thins out to the right the
     * way the chat backdrop does: full left of {@code fadeStartX},
     * nothing at the right edge. {@code NaN} fades nothing.
     */
    public void drawFadedRegion(double left, double top, double right,
                                double bottom, double fadeStartX,
                                int screenWidth, int screenHeight,
                                float opacity) {
        drawMapped(left, top, right, bottom, fadeStartX,
                left, top, right, bottom, screenWidth, screenHeight,
                opacity);
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
            double localBottom, double localFadeStartX, double screenLeft,
            double screenTop, double scale, int screenWidth,
            int screenHeight, float opacity) {
        if (scale <= 0.0D) {
            return;
        }
        drawMapped(localLeft, localTop, localRight, localBottom,
                localFadeStartX,
                screenLeft + (localLeft) * scale,
                screenTop + (localTop) * scale,
                screenLeft + (localRight) * scale,
                screenTop + (localBottom) * scale,
                screenWidth, screenHeight, opacity);
    }

    private void drawMapped(double vertexLeft, double vertexTop,
                            double vertexRight, double vertexBottom,
                            double fadeStartVertexX, double screenLeft,
                            double screenTop, double screenRight,
                            double screenBottom, int screenWidth,
                            int screenHeight, float opacity) {
        if (!isFresh() || this.blurredTexture < 0 || screenWidth <= 0
                || screenHeight <= 0 || vertexRight <= vertexLeft
                || vertexBottom <= vertexTop || opacity <= 0.0F) {
            return;
        }
        double u0 = clamp01(screenLeft / screenWidth);
        double u1 = clamp01(screenRight / screenWidth);
        // The capture reads bottom-up; GUI space counts down from the top.
        double v0 = clamp01(1.0D - screenBottom / screenHeight);
        double v1 = clamp01(1.0D - screenTop / screenHeight);
        int alpha = Math.max(0, Math.min(255,
                (int)Math.round(255.0D * opacity)));
        boolean faded = !Double.isNaN(fadeStartVertexX)
                && fadeStartVertexX < vertexRight;
        double solidRight = faded
                ? Math.max(vertexLeft, fadeStartVertexX) : vertexRight;
        double uSolid = u0 + (u1 - u0) * (solidRight - vertexLeft)
                / (vertexRight - vertexLeft);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.blurredTexture);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        if (solidRight > vertexLeft) {
            tessellator.setColorRGBA(255, 255, 255, alpha);
            tessellator.addVertexWithUV(vertexLeft, vertexBottom, 0.0D,
                    u0, v0);
            tessellator.addVertexWithUV(solidRight, vertexBottom, 0.0D,
                    uSolid, v0);
            tessellator.addVertexWithUV(solidRight, vertexTop, 0.0D,
                    uSolid, v1);
            tessellator.addVertexWithUV(vertexLeft, vertexTop, 0.0D,
                    u0, v1);
        }
        if (faded) {
            tessellator.setColorRGBA(255, 255, 255, alpha);
            tessellator.addVertexWithUV(solidRight, vertexBottom, 0.0D,
                    uSolid, v0);
            tessellator.setColorRGBA(255, 255, 255, 0);
            tessellator.addVertexWithUV(vertexRight, vertexBottom, 0.0D,
                    u1, v0);
            tessellator.addVertexWithUV(vertexRight, vertexTop, 0.0D,
                    u1, v1);
            tessellator.setColorRGBA(255, 255, 255, alpha);
            tessellator.addVertexWithUV(solidRight, vertexTop, 0.0D,
                    uSolid, v1);
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

    /** The whole sharp frame back over the blurred one, one to one. */
    private static void drawFullFrame(int texture, Minecraft minecraft) {
        ScaledResolution resolution = new ScaledResolution(minecraft,
                minecraft.displayWidth, minecraft.displayHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
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
