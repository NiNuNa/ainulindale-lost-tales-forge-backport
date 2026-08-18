package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Lost Tales' GUI pointer, and the only thing that touches the real one.
 *
 * <p>Nothing is grabbed and nothing is moved: the operating system's cursor is
 * replaced with one that has no pixels in it, so the pointer keeps behaving
 * exactly as it did — clicks, drags, hovering and every GUI control still
 * see the same coordinate — and only its appearance changes. Grabbing it
 * instead would centre it and leave the map with no pointer at all.</p>
 *
 * <p>The drawn cursor's tip sits on that same coordinate, so what the player
 * aims at and what the GUI hit-tests are the same pixel by construction rather
 * than by an offset that has to be kept in step.</p>
 *
 * <p>Native cursor state outlives any one screen, so it is owned here alone and
 * given back whenever Minecraft leaves the GUI layer or a frame fails to draw.
 * A hidden cursor left behind is unusable
 * desktop, not a cosmetic bug.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesMapCursor {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            LostTalesMetaData.MOD_ID, "textures/gui/cursor.png");
    /** The two poses, side by side in one strip. */
    static final int TEXTURE_WIDTH = 16;
    static final int TEXTURE_HEIGHT = 10;
    /**
     * Drawn in GUI pixels, so the pointer is the same size relative to the
     * interface at every resolution and GUI scale, and its pixels line up with
     * everything else drawn on the map.
     */
    static final int SPRITE_WIDTH = 8;
    static final int SPRITE_HEIGHT = 10;

    /**
     * Which pose the pointer is drawn in, and where its point sits inside it.
     *
     * <p>Both poses are drawn from their own point, so the pixel the player is
     * aiming at is the pixel every GUI hit test resolved against, whichever
     * artwork is showing. A pose whose point is elsewhere moves its own
     * hotspot and nothing else.</p>
     */
    enum Pose {
        /** Nothing under the pointer answers to a click. */
        ARROW(0, 0, 0),
        /** Something under the pointer does: the hand, tip of finger first. */
        HAND(SPRITE_WIDTH, 3, 0);

        private final int u;
        private final int hotspotX;
        private final int hotspotY;

        Pose(int u, int hotspotX, int hotspotY) {
            this.u = u;
            this.hotspotX = hotspotX;
            this.hotspotY = hotspotY;
        }
    }

    private static Cursor blankCursor;
    private static boolean cursorUnavailable;
    private static boolean held;

    private LostTalesMapCursor() {}

    /**
     * Takes the pointer over, if this platform lets it.
     *
     * <p>A platform that refuses a custom cursor keeps its own, and the GUI
     * simply draws nothing over it rather than drawing two pointers.</p>
     */
    public static void acquire() {
        if (held || cursorUnavailable) {
            return;
        }
        try {
            if (blankCursor == null) {
                blankCursor = createBlankCursor();
            }
            Mouse.setNativeCursor(blankCursor);
            held = true;
        } catch (Throwable throwable) {
            cursorUnavailable = true;
            held = false;
            FMLLog.warning(
                    "[%s] GUI cursor disabled; keeping the system pointer (%s)",
                    LostTalesMetaData.MOD_ID, throwable.toString());
        }
    }

    /** Gives the pointer back. Safe to call when it was never taken. */
    public static void release() {
        if (!held) {
            return;
        }
        held = false;
        try {
            Mouse.setNativeCursor(null);
        } catch (Throwable ignored) {
            // Nothing further can be done, and retrying every frame would
            // only spend the log on it.
        }
    }

    /**
     * Gives the pointer back when Minecraft no longer has an open GUI.
     *
     * <p>The safety net for screens that stop being current without closing
     * tidily. Keeping ownership while one GUI replaces another also avoids a
     * one-frame native-pointer flash during transitions.</p>
     */
    public static void releaseIfUnowned(Minecraft minecraft) {
        if (!held) {
            return;
        }
        if (minecraft == null || minecraft.currentScreen == null) {
            release();
        }
    }

    static boolean isHeld() {
        return held;
    }

    /**
     * Draws the pointer, last of everything, at the GUI's hit-test position.
     *
     * @param interactable whether the thing under the pointer answers to a
     *                     click, which is the only thing that decides the pose
     */
    public static void render(Minecraft minecraft, int mouseX, int mouseY,
                              boolean interactable) {
        if (!held || minecraft == null
                || minecraft.getTextureManager() == null) {
            return;
        }
        Pose pose = interactable ? Pose.HAND : Pose.ARROW;
        int x = mouseX - pose.hotspotX;
        int y = mouseY - pose.hotspotY;
        float minU = pose.u / (float)TEXTURE_WIDTH;
        float maxU = (pose.u + SPRITE_WIDTH) / (float)TEXTURE_WIDTH;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            minecraft.getTextureManager().bindTexture(TEXTURE);
            // Nearest, because the artwork is pixels and any filtering at all
            // turns a crisp arrow into a smudge at large GUI scales.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(
                    x, y + SPRITE_HEIGHT, 0.0D, minU, 1.0D);
            tessellator.addVertexWithUV(
                    x + SPRITE_WIDTH, y + SPRITE_HEIGHT, 0.0D, maxU, 1.0D);
            tessellator.addVertexWithUV(
                    x + SPRITE_WIDTH, y, 0.0D, maxU, 0.0D);
            tessellator.addVertexWithUV(x, y, 0.0D, minU, 0.0D);
            tessellator.draw();
        } catch (Throwable ignored) {
            // A pointer that cannot be drawn must not leave one that cannot
            // be seen either.
            release();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /**
     * A cursor with nothing in it.
     *
     * <p>One transparent pixel where the platform allows it, and the smallest
     * size it does allow otherwise; LWJGL refuses sizes a platform has no
     * cursor format for, so the dimensions are asked for rather than
     * assumed.</p>
     */
    private static Cursor createBlankCursor() throws LWJGLException {
        int width = Math.max(1, Cursor.getMinCursorSize());
        int height = width;
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        while (pixels.hasRemaining()) {
            pixels.put(0);
        }
        pixels.flip();
        return new Cursor(width, height, 0, 0, 1, pixels, null);
    }
}
