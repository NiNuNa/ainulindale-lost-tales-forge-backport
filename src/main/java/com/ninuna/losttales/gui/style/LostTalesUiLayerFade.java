package com.ninuna.losttales.gui.style;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * A layer drawn at part strength as one picture, however it draws and
 * blends: the display under the layer's rectangle is copied before the
 * layer draws, and laid back over it afterwards at the share the layer
 * should not show. What the layer drew then shows at exactly its
 * strength over what was there. It needs no framebuffer of its own;
 * where the copy fails, the caller draws the layer at full strength.
 *
 * <p>The HUD stepping aside for the chat fades this way, and so does the
 * map entering with its window.</p>
 */
public final class LostTalesUiLayerFade {
    private int texture = -1;
    private int textureWidth = -1;
    private int textureHeight = -1;
    /** The rectangle copied, in display pixels from the display's top left. */
    private int left;
    private int top;
    private int width;
    private int height;
    /** Whether a copy waits to be laid back. */
    private boolean copied;

    /**
     * Copies the display under a rectangle of the GUI, in GUI pixels from
     * the screen's top left, before the layer draws; answers whether the
     * copy was taken. Every display pixel the rectangle touches is copied.
     */
    public boolean begin(Minecraft minecraft, double x, double y,
                         double rectangleWidth, double rectangleHeight) {
        this.copied = false;
        if (minecraft == null || minecraft.displayWidth <= 0
                || minecraft.displayHeight <= 0) {
            return false;
        }
        int scale = LostTalesDisplayPixels.scaleFactor();
        int copyLeft = Math.max(0, (int)Math.floor(x * scale));
        int copyTop = Math.max(0, (int)Math.floor(y * scale));
        int copyRight = Math.min(minecraft.displayWidth,
                (int)Math.ceil((x + rectangleWidth) * scale));
        int copyBottom = Math.min(minecraft.displayHeight,
                (int)Math.ceil((y + rectangleHeight) * scale));
        if (copyRight <= copyLeft || copyBottom <= copyTop) {
            return false;
        }
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            ensureTexture(minecraft);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
            // Copies read up from the display's bottom.
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, copyLeft,
                    minecraft.displayHeight - copyBottom,
                    copyRight - copyLeft, copyBottom - copyTop);
        } catch (RuntimeException failure) {
            return false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        }
        this.left = copyLeft;
        this.top = copyTop;
        this.width = copyRight - copyLeft;
        this.height = copyBottom - copyTop;
        this.copied = true;
        return true;
    }

    /** Copies the whole display before the layer draws; answers whether the copy was taken. */
    public boolean beginDisplay(Minecraft minecraft) {
        if (minecraft == null) {
            this.copied = false;
            return false;
        }
        int scale = LostTalesDisplayPixels.scaleFactor();
        return begin(minecraft, 0.0D, 0.0D,
                minecraft.displayWidth / (double)scale,
                minecraft.displayHeight / (double)scale);
    }

    /**
     * Lays the copy back over what the layer drew, so the layer shows at
     * {@code strength}, 0 to 1. Nothing without a copy. Drawn in the
     * display's own pixels, whatever matrices the caller stands in.
     */
    public void end(Minecraft minecraft, float strength) {
        if (!this.copied) {
            return;
        }
        this.copied = false;
        float back = 1.0F - strength;
        if (this.texture < 0 || minecraft == null || back <= 0.0F) {
            return;
        }
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, minecraft.displayWidth, minecraft.displayHeight,
                0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, back);
            double right = this.left + this.width;
            double bottom = this.top + this.height;
            double u = this.width / (double)this.textureWidth;
            double v = this.height / (double)this.textureHeight;
            // The copy's first row is the rectangle's bottom.
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(this.left, bottom, 0.0D, 0.0D, 0.0D);
            tessellator.addVertexWithUV(right, bottom, 0.0D, u, 0.0D);
            tessellator.addVertexWithUV(right, this.top, 0.0D, u, v);
            tessellator.addVertexWithUV(this.left, this.top, 0.0D, 0.0D, v);
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        }
    }

    /** Lets the copy's texture go; the next fade makes it again. */
    public void release() {
        this.copied = false;
        if (this.texture >= 0) {
            GL11.glDeleteTextures(this.texture);
        }
        this.texture = -1;
        this.textureWidth = -1;
        this.textureHeight = -1;
    }

    /** A texture the display's size, made again when the display is resized. */
    private void ensureTexture(Minecraft minecraft) {
        if (this.texture >= 0 && this.textureWidth == minecraft.displayWidth
                && this.textureHeight == minecraft.displayHeight) {
            return;
        }
        if (this.texture >= 0) {
            GL11.glDeleteTextures(this.texture);
        }
        this.textureWidth = minecraft.displayWidth;
        this.textureHeight = minecraft.displayHeight;
        this.texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
                GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
                GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8,
                this.textureWidth, this.textureHeight, 0, GL11.GL_RGB,
                GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
    }
}
