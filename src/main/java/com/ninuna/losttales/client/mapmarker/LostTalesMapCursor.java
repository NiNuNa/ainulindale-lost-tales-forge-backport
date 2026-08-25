package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.gui.style.LostTalesColors;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;
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
    /**
     * The poses stand side by side in one strip, each as wide as its own
     * artwork with a clear column between them, so a pose addresses its
     * own rectangle rather than a cell of a fixed size.
     * {@code LostTalesMapCursorTest} holds the sheet to these numbers, so
     * a re-export at another size fails the build instead of smearing
     * every pose after the first.
     */
    static final int TEXTURE_WIDTH = 33;
    static final int TEXTURE_HEIGHT = 10;
    /**
     * Drawn in GUI pixels, so the pointer is the same size relative to the
     * interface at every resolution and GUI scale, and its pixels line up with
     * everything else drawn on the map.
     */
    static final int SPRITE_WIDTH = 8;
    static final int SPRITE_HEIGHT = 10;
    /** Where each pose's artwork sits in the strip, and how wide it is. */
    private static final int ARROW_WIDTH = 8;
    private static final int HAND_WIDTH = 8;
    private static final int STRETCH_U = 17;
    private static final int STRETCH_WIDTH = 5;
    private static final int DIAGONAL_U = 23;
    private static final int DIAGONAL_WIDTH = 10;
    /** The interface's own drop shadow, shared with the chat's glyphs. */
    private static final int SHADOW_RGB =
            LostTalesColors.rgb(LostTalesColors.HUD_SHADOW);
    private static final float SHADOW_OPACITY = 0.5F;
    private static final int SHADOW_OFFSET = 1;

    /**
     * Which pose the pointer is drawn in, and where its point sits inside it.
     *
     * <p>Both poses are drawn from their own point, so the pixel the player is
     * aiming at is the pixel every GUI hit test resolved against, whichever
     * artwork is showing. A pose whose point is elsewhere moves its own
     * hotspot and nothing else.</p>
     */
    public enum Pose {
        /** Nothing under the pointer answers to a click. */
        ARROW(0, ARROW_WIDTH, 0, 0, Turn.NONE),
        /** Something under the pointer does: the hand, tip of finger first. */
        HAND(ARROW_WIDTH, HAND_WIDTH, 3, 0, Turn.NONE),
        /** A top or bottom edge: the double arrow stood on end, as drawn. */
        RESIZE_VERTICAL(STRETCH_U, STRETCH_WIDTH, STRETCH_WIDTH / 2,
                SPRITE_HEIGHT / 2, Turn.NONE),
        /** A left or right edge: the same arrow laid on its side. */
        RESIZE_HORIZONTAL(STRETCH_U, STRETCH_WIDTH, SPRITE_HEIGHT / 2,
                STRETCH_WIDTH / 2, Turn.QUARTER),
        /** The corners the drawn diagonal runs between: top-right, bottom-left. */
        RESIZE_DIAGONAL(DIAGONAL_U, DIAGONAL_WIDTH, DIAGONAL_WIDTH / 2,
                SPRITE_HEIGHT / 2, Turn.NONE),
        /** The other pair of corners: the same diagonal mirrored. */
        RESIZE_ANTI_DIAGONAL(DIAGONAL_U, DIAGONAL_WIDTH, DIAGONAL_WIDTH / 2,
                SPRITE_HEIGHT / 2, Turn.MIRROR);

        /** How the sprite is turned as it is drawn. */
        private enum Turn { NONE, QUARTER, MIRROR }

        /** The sprite's own rectangle in the strip. */
        private final int u;
        private final int spriteWidth;
        private final int hotspotX;
        private final int hotspotY;
        private final Turn turn;

        Pose(int u, int spriteWidth, int hotspotX, int hotspotY, Turn turn) {
            this.u = u;
            this.spriteWidth = spriteWidth;
            this.hotspotX = hotspotX;
            this.hotspotY = hotspotY;
            this.turn = turn;
        }

        /** Width on screen: a quarter turn stands the sprite on its end. */
        int drawnWidth() {
            return this.turn == Turn.QUARTER ? SPRITE_HEIGHT
                    : this.spriteWidth;
        }

        int drawnHeight() {
            return this.turn == Turn.QUARTER ? this.spriteWidth
                    : SPRITE_HEIGHT;
        }

        /** Whether the sheet on disk is wide enough to hold this pose. */
        boolean fitsSheet(int sheetWidth) {
            return this.u + this.spriteWidth <= sheetWidth;
        }

        int textureU() { return this.u; }

        int spriteWidth() { return this.spriteWidth; }

        int hotspotX() { return this.hotspotX; }

        int hotspotY() { return this.hotspotY; }
    }

    private static Cursor blankCursor;
    private static boolean cursorUnavailable;
    private static boolean held;
    /** The strip's width, measured from the file; 0 until read. */
    private static int measuredWidth;
    /** A pose asked for by a screen this frame; spent by the next draw. */
    private static Pose requestedPose;

    /**
     * Asks for a pose for the coming frame — a screen that knows better
     * than "is this clickable" says so here. Spent by the next
     * {@link #render}, so a frame that does not ask keeps the plain
     * poses. A pose the sheet has no sprite for is ignored.
     */
    public static void requestPose(Pose pose) {
        requestedPose = pose;
    }

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
        Pose asked = requestedPose;
        // Spent whether or not it is drawn, so a pose asked for by one
        // frame can never show up on a later one.
        requestedPose = null;
        if (!held || minecraft == null
                || minecraft.getTextureManager() == null) {
            return;
        }
        int sheetWidth = sheetWidth(minecraft);
        Pose pose = asked != null && asked.fitsSheet(sheetWidth)
                ? asked : (interactable ? Pose.HAND : Pose.ARROW);
        // The GUI's mouse coordinate is a whole GUI pixel, but the raw
        // pointer moves display pixel by display pixel — the same
        // granularity a dragged chat window moves at. Drawn at the GUI
        // coordinate the sprite would step several display pixels at a
        // time at higher GUI scales and jitter against whatever it is
        // dragging, so the fraction the GUI's integer conversion
        // discarded is put back: the caller's coordinate is kept — a
        // screen animating its content passes a transformed one — and
        // the sub-pixel remainder is added to it, which lands the
        // sprite on a whole display pixel and keeps the pixel art
        // crisp. Hit testing keeps the whole-pixel coordinate; the two
        // never differ by as much as one GUI pixel.
        float x = preciseMouseX(minecraft, mouseX) - pose.hotspotX;
        float y = preciseMouseY(minecraft, mouseY) - pose.hotspotY;
        float minU = pose.u / (float)sheetWidth;
        float maxU = (pose.u + pose.spriteWidth) / (float)sheetWidth;
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
            // The pointer carries the interface's own drop shadow: the
            // same flat colour, the same one-pixel offset, at half the
            // opacity, so it sits over a GUI like every glyph in it.
            LostTalesSilhouetteRenderState.begin(SHADOW_RGB);
            try {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, SHADOW_OPACITY);
                drawSprite(pose, x + SHADOW_OFFSET, y + SHADOW_OFFSET,
                        minU, maxU);
            } finally {
                LostTalesSilhouetteRenderState.end();
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            drawSprite(pose, x, y, minU, maxU);
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
     * The caller's mouse x with the raw pointer's sub-GUI-pixel
     * remainder restored: the GUI derives its coordinate with integer
     * division, and the fraction it drops is exactly how far into the
     * GUI pixel the real pointer stands. Falls back to the whole-pixel
     * coordinate when the raw mouse cannot be read.
     */
    private static float preciseMouseX(Minecraft minecraft, int mouseX) {
        if (!Mouse.isCreated() || minecraft.displayWidth <= 0
                || minecraft.currentScreen == null) {
            return mouseX;
        }
        int raw = Mouse.getX();
        int width = minecraft.currentScreen.width;
        double exact = raw * (double)width / minecraft.displayWidth;
        int whole = raw * width / minecraft.displayWidth;
        return (float)(mouseX + exact - whole);
    }

    /** As above for y; the raw axis counts up from the display's bottom. */
    private static float preciseMouseY(Minecraft minecraft, int mouseY) {
        if (!Mouse.isCreated() || minecraft.displayHeight <= 0
                || minecraft.currentScreen == null) {
            return mouseY;
        }
        int raw = Mouse.getY();
        int height = minecraft.currentScreen.height;
        double exact = height - raw * (double)height
                / minecraft.displayHeight - 1.0D;
        int whole = height - raw * height / minecraft.displayHeight - 1;
        return (float)(mouseY + exact - whole);
    }

    /**
     * One pose's quad, turned as the pose asks. Both turns keep the
     * sprite's own pixels square: a quarter turn swaps the corners
     * round, a mirror swaps them left for right.
     */
    private static void drawSprite(Pose pose, float x, float y,
                                   float minU, float maxU) {
        int width = pose.drawnWidth();
        int height = pose.drawnHeight();
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        if (pose.turn == Pose.Turn.QUARTER) {
            // Laid on its side: the sprite's u runs down the quad and
            // its v across it.
            tessellator.addVertexWithUV(x, y + height, 0.0D, maxU, 1.0D);
            tessellator.addVertexWithUV(x + width, y + height, 0.0D,
                    maxU, 0.0D);
            tessellator.addVertexWithUV(x + width, y, 0.0D, minU, 0.0D);
            tessellator.addVertexWithUV(x, y, 0.0D, minU, 1.0D);
            tessellator.draw();
            return;
        }
        float left = pose.turn == Pose.Turn.MIRROR ? maxU : minU;
        float right = pose.turn == Pose.Turn.MIRROR ? minU : maxU;
        tessellator.addVertexWithUV(x, y + height, 0.0D, left, 1.0D);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, right, 1.0D);
        tessellator.addVertexWithUV(x + width, y, 0.0D, right, 0.0D);
        tessellator.addVertexWithUV(x, y, 0.0D, left, 0.0D);
        tessellator.draw();
    }

    /**
     * The sheet's width in pixels, read from the file once. A sheet that
     * cannot be read is taken for the two plain poses, so a pose it has
     * no room for is never addressed.
     */
    private static int sheetWidth(Minecraft minecraft) {
        if (measuredWidth > 0) {
            return measuredWidth;
        }
        measuredWidth = ARROW_WIDTH + HAND_WIDTH;
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return measuredWidth;
        }
        InputStream stream = null;
        try {
            stream = minecraft.getResourceManager()
                    .getResource(TEXTURE).getInputStream();
            BufferedImage image = ImageIO.read(stream);
            if (image != null && image.getWidth() > 0) {
                measuredWidth = image.getWidth();
            }
        } catch (IOException failure) {
            warnUnmeasured(failure);
        } catch (RuntimeException failure) {
            warnUnmeasured(failure);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Nothing useful to do while closing a resource.
                }
            }
        }
        return measuredWidth;
    }

    private static void warnUnmeasured(Throwable failure) {
        FMLLog.warning("[%s] Could not measure the cursor sheet; keeping "
                + "the plain poses (%s)", LostTalesMetaData.MOD_ID,
                failure.toString());
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
