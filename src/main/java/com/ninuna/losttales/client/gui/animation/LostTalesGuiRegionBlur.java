package com.ninuna.losttales.client.gui.animation;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Copies of the frame as it stood before any window was drawn, for the
 * windows drawn over it. The sharp copy cuts away whatever lies behind a
 * window ({@link #cutFramedRegion}): pasted over the window's box first,
 * it leaves only the world under it, so no window ever shows through
 * another. The blurred copy softens the world behind a window's own
 * rectangle while everything around it stays sharp: the frame is blurred
 * with the same Gaussian shader the full-screen GUI blur uses, captured
 * again, and the sharp copy is put back. Both are textures any caller may
 * paste a region of, in GUI coordinates. Everything is best-effort:
 * without framebuffers nothing is captured and nothing is pasted, and
 * without the shader or a world there is no blur.
 */
public final class LostTalesGuiRegionBlur {
    private static final LostTalesGuiRegionBlur INSTANCE =
            new LostTalesGuiRegionBlur();
    /** A capture older than this is another frame's; never drawn. */
    private static final long FRESH_NANOS = 250L * 1000000L;

    private final LostTalesGuiBlurRenderer blur =
            new LostTalesGuiBlurRenderer();
    /** The model view matrix a framed box is pasted in, read back for each paste. */
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
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
    /** When the sharp copy was taken, or 0 for none. */
    private long sharpNanos;
    /** When the blurred copy was taken, or 0 for none. */
    private long blurredNanos;

    private LostTalesGuiRegionBlur() {}

    public static LostTalesGuiRegionBlur getInstance() {
        return INSTANCE;
    }

    /**
     * Captures the frame as drawn so far, and blurs a copy of it at
     * {@code strength} while there is a world to blur; 0 takes the sharp
     * copy alone. The screen looks exactly as before when this returns:
     * {@link #cutFramedRegion} can paste the sharp frame and
     * {@link #drawFramedRegion} the blurred one for the rest of the frame.
     * Called before anything of the caller's own is drawn. Answers
     * whether the blurred copy was taken.
     */
    public boolean capture(Minecraft minecraft, float partialTicks,
                           float strength) {
        this.sharpNanos = 0L;
        this.blurredNanos = 0L;
        if (minecraft == null || minecraft.getFramebuffer() == null
                || !OpenGlHelper.isFramebufferEnabled()
                || minecraft.displayWidth <= 0
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
            this.sharpNanos = System.nanoTime();
            if (minecraft.theWorld == null || strength <= 0.01F
                    || !this.blur.render(minecraft, partialTicks, strength)) {
                return false;
            }
            copyFrameInto(this.blurredTexture, minecraft);
            drawFullFrame(this.sharpTexture, minecraft);
            this.blurredNanos = System.nanoTime();
            return true;
        } catch (RuntimeException failure) {
            release();
            return false;
        }
    }

    /** Whether a copy taken at {@code nanos} is this frame's. */
    private static boolean fresh(long nanos) {
        return nanos > 0L && System.nanoTime() - nanos < FRESH_NANOS;
    }

    /**
     * Cuts away whatever was drawn since the capture inside a framed box
     * {@code left} to {@code right} by {@code top} to {@code bottom}: the
     * sharp frame is pasted over the box and the frame's {@code ring}
     * round it, the ring's four outermost corner pixels left out as a
     * frame leaves them. A window or a sub-window does this before it
     * draws itself, so nothing behind it shows through. Nothing is drawn
     * without a fresh capture.
     */
    public void cutFramedRegion(double left, double top, double right,
                                double bottom, int ring, float opacity) {
        if (fresh(this.sharpNanos)) {
            drawFramed(this.sharpTexture, left, top, right, bottom, ring,
                    opacity);
        }
    }

    /**
     * The whole sharp frame, the screen as it stood before any window,
     * drawn shrunk into {@code left} to {@code right} by {@code top} to
     * {@code bottom}: a picture of the screen, behind the HUD Placement
     * page's preview. Answers whether a fresh capture was there to draw.
     */
    public boolean drawScreenInto(double left, double top, double right,
                                  double bottom, float opacity) {
        if (!fresh(this.sharpNanos)) {
            return false;
        }
        drawMapped(this.sharpTexture, left, top, right, bottom, null, 0.0D,
                0.0D, this.guiWidth, this.guiHeight, 0.0D, 1.0D, opacity);
        return true;
    }

    /**
     * Softens the world under a framed box: the blurred frame pasted over
     * the box and its frame's {@code ring}, in the frame's own shape, as
     * {@link #cutFramedRegion} cuts it. Nothing is drawn without a fresh
     * blurred capture, so with the windows' blur off the world stays sharp.
     */
    public void drawFramedRegion(double left, double top, double right,
                                 double bottom, int ring, float opacity) {
        if (fresh(this.blurredNanos)) {
            drawFramed(this.blurredTexture, left, top, right, bottom, ring,
                    opacity);
        }
    }

    /**
     * A texture over a box and its ring, the ring's four outermost corner
     * pixels left out. The box may stand in a moved or scaled matrix, as a
     * list following the caret does: the frame is sampled where the box
     * really lands on the screen, so what shows under it is what stands
     * behind it.
     */
    private void drawFramed(int texture, double left, double top,
                            double right, double bottom, int ring,
                            float opacity) {
        this.matrix.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, this.matrix);
        double scaleX = this.matrix.get(0);
        double scaleY = this.matrix.get(5);
        double moveX = this.matrix.get(12);
        double moveY = this.matrix.get(13);
        if (!(scaleX > 0.0D) || !(scaleY > 0.0D)) {
            scaleX = 1.0D;
            scaleY = 1.0D;
            moveX = 0.0D;
            moveY = 0.0D;
        }
        drawStrip(texture, left - ring + 1, top - ring, right + ring - 1,
                top - ring + 1, scaleX, scaleY, moveX, moveY, opacity);
        drawStrip(texture, left - ring, top - ring + 1, right + ring,
                bottom + ring - 1, scaleX, scaleY, moveX, moveY, opacity);
        drawStrip(texture, left - ring + 1, bottom + ring - 1,
                right + ring - 1, bottom + ring, scaleX, scaleY, moveX,
                moveY, opacity);
    }

    /** One strip of a framed paste, sampled where the matrix puts it. */
    private void drawStrip(int texture, double left, double top,
                           double right, double bottom, double scaleX,
                           double scaleY, double moveX, double moveY,
                           float opacity) {
        drawMapped(texture, left, top, right, bottom, null,
                moveX + left * scaleX, moveY + top * scaleY,
                moveX + right * scaleX, moveY + bottom * scaleY, 0.0D, 1.0D,
                opacity);
    }

    /**
     * Pastes one band, {@code top} to {@code bottom}, of the blurred frame
     * under a framed box: the frame's ring reaches from {@code frameTop}
     * to {@code frameBottom}, and where the band takes in the ring's first
     * or last row, its four outermost corner pixels are left out as
     * {@link #drawFramedRegion} leaves them. {@code fadeWeights} thins the
     * blur out across the band as the chat backdrop does: that profile
     * sampled evenly from the left edge to the right, one more entry than
     * the steps it is drawn in; {@code null} fades nothing. Nothing is
     * drawn without a fresh capture.
     */
    public void drawFramedBand(double left, double top, double right,
                               double bottom, double frameTop,
                               double frameBottom, float[] fadeWeights,
                               float opacity) {
        if (!fresh(this.blurredNanos) || right - left <= 2.0D
                || bottom <= top) {
            return;
        }
        double inset = 1.0D / (right - left);
        double upper = Math.max(top, Math.min(bottom, frameTop + 1.0D));
        double lower = Math.max(upper, Math.min(bottom, frameBottom - 1.0D));
        // The ring's first row, the rows between, and its last row.
        double[] from = {top, upper, lower};
        double[] to = {upper, lower, bottom};
        for (int piece = 0; piece < 3; piece++) {
            if (to[piece] <= from[piece]) {
                continue;
            }
            boolean corners = piece != 1;
            drawMapped(this.blurredTexture, left, from[piece], right,
                    to[piece], fadeWeights, left, from[piece], right,
                    to[piece], corners ? inset : 0.0D,
                    corners ? 1.0D - inset : 1.0D, opacity);
        }
    }

    /**
     * Pastes the blurred frame, thinned out by {@code fadeWeights} as
     * {@link #drawFramedBand} does, for callers drawing inside their own
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
        if (scale <= 0.0D || !fresh(this.blurredNanos)) {
            return;
        }
        drawMapped(this.blurredTexture, localLeft, localTop, localRight,
                localBottom, fadeWeights,
                screenLeft + (localLeft) * scale,
                screenTop + (localTop) * scale,
                screenLeft + (localRight) * scale,
                screenTop + (localBottom) * scale,
                0.0D, 1.0D, opacity);
    }

    /**
     * Pastes {@code texture} over a region, the share {@code spanFrom} to
     * {@code spanTo} of its width alone; the fade profile still runs
     * across the whole region.
     */
    private void drawMapped(int texture, double vertexLeft,
                            double vertexTop, double vertexRight,
                            double vertexBottom, float[] fadeWeights,
                            double screenLeft, double screenTop,
                            double screenRight, double screenBottom,
                            double spanFrom, double spanTo, float opacity) {
        if (texture < 0 || vertexRight <= vertexLeft
                || vertexBottom <= vertexTop || screenRight <= screenLeft
                || opacity <= 0.0F) {
            return;
        }
        // Only what lies on the screen is drawn: a region reaching past
        // an edge — a window hanging off the screen — is cut to the
        // screen, the quad and the sample alike, so the frame's last
        // column is never stretched across the part beyond.
        double screenWidth = screenRight - screenLeft;
        double shownFrom = Math.max(spanFrom, -screenLeft / screenWidth);
        double shownTo = Math.min(spanTo,
                (this.guiWidth - screenLeft) / screenWidth);
        double clipLeft = screenLeft + screenWidth * shownFrom;
        double clipRight = screenLeft + screenWidth * shownTo;
        double clipTop = Math.max(0.0D, screenTop);
        double clipBottom = Math.min(this.guiHeight, screenBottom);
        if (clipRight <= clipLeft || clipBottom <= clipTop) {
            return;
        }
        double topShare = (clipTop - screenTop) / (screenBottom - screenTop);
        double bottomShare = (clipBottom - screenTop)
                / (screenBottom - screenTop);
        double drawnTop = vertexTop + (vertexBottom - vertexTop) * topShare;
        double drawnBottom = vertexTop
                + (vertexBottom - vertexTop) * bottomShare;
        // Sampled against the projection's exact size, so a region lands
        // on the very pixels the world drew; see {@link #guiWidth}.
        double u0 = clipLeft / this.guiWidth;
        double u1 = clipRight / this.guiWidth;
        // The capture reads bottom-up; GUI space counts down from the top.
        double v0 = 1.0D - clipBottom / this.guiHeight;
        double v1 = 1.0D - clipTop / this.guiHeight;
        int alpha = Math.max(0, Math.min(255,
                (int)Math.round(255.0D * opacity)));
        int steps = fadeWeights == null ? 1 : fadeWeights.length - 1;
        if (steps < 1) {
            return;
        }
        boolean depthTest = pasteWithoutDepth();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
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
            double w0 = fadeWeights == null ? 1.0D : fadeWeights[step];
            double w1 = fadeWeights == null ? 1.0D : fadeWeights[step + 1];
            // A step cut by the screen's edge keeps the profile's value
            // where it is cut.
            if (to <= shownFrom || from >= shownTo) {
                continue;
            }
            if (from < shownFrom) {
                w0 += (w1 - w0) * (shownFrom - from) / (to - from);
                from = shownFrom;
            }
            if (to > shownTo) {
                w1 = w0 + (w1 - w0) * (shownTo - from) / (to - from);
                to = shownTo;
            }
            double x0 = vertexLeft + (vertexRight - vertexLeft) * from;
            double x1 = vertexLeft + (vertexRight - vertexLeft) * to;
            double uFrom = u0 + (u1 - u0) * (from - shownFrom)
                    / (shownTo - shownFrom);
            double uTo = u0 + (u1 - u0) * (to - shownFrom)
                    / (shownTo - shownFrom);
            int a0 = (int)Math.round(alpha * w0);
            int a1 = (int)Math.round(alpha * w1);
            tessellator.setColorRGBA(255, 255, 255, a0);
            tessellator.addVertexWithUV(x0, drawnBottom, 0.0D, uFrom, v0);
            tessellator.setColorRGBA(255, 255, 255, a1);
            tessellator.addVertexWithUV(x1, drawnBottom, 0.0D, uTo, v0);
            tessellator.addVertexWithUV(x1, drawnTop, 0.0D, uTo, v1);
            tessellator.setColorRGBA(255, 255, 255, a0);
            tessellator.addVertexWithUV(x0, drawnTop, 0.0D, uFrom, v1);
        }
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        endPaste(depthTest);
    }

    /**
     * Turns the depth test off for a paste and answers whether it was on.
     * A paste is flat and lies at z 0: tested, it would leave its depth
     * behind, and whatever the HUD draws after it further back would be
     * hidden there. LOTR's compass leans back from z 0 and lost its far
     * half that way.
     */
    private static boolean pasteWithoutDepth() {
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (depthTest) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        return depthTest;
    }

    /** Puts the depth test back as {@link #pasteWithoutDepth} found it. */
    private static void endPaste(boolean depthTest) {
        if (depthTest) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
    }

    public void release() {
        this.blur.release();
        releaseTextures();
        this.sharpNanos = 0L;
        this.blurredNanos = 0L;
    }

    public void resetAfterResourceReload() {
        this.blur.resetAfterResourceReload();
        releaseTextures();
        this.sharpNanos = 0L;
        this.blurredNanos = 0L;
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
        boolean depthTest = pasteWithoutDepth();
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
        endPaste(depthTest);
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
}
