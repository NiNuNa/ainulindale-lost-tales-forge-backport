package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/**
 * What a sub-window holds: a picker, a person's card or a menu. The
 * window owns the frame, the title strip, the surface, the fade and the
 * place; the content draws itself in the box under the strip and answers
 * the pointer and the keys there. Every box handed in is in whole GUI
 * pixels, the window having moved the matrix by whatever fraction of a
 * pixel it stands on.
 */
public abstract class SubWindowContent {
    /** The name on the window's strip; null for its kind's own. */
    public String stripTitle() {
        return null;
    }

    /** The glyph before the window's name on its strip; null for none. */
    public abstract LostTalesUiSheet stripIcon();

    /** The width the content box opens at. */
    public abstract int naturalWidth();

    /** The height the content box opens at, {@code width} wide. */
    public abstract int naturalHeight(int width);

    /** The narrowest content box the content still works in. */
    public abstract int minWidth();

    /** The shortest content box the content still works in. */
    public abstract int minHeight();

    /**
     * Draws the content in {@code box} at {@code alpha} (0-255), on the
     * window's surface, plum black at {@code surfaceAlpha}, which a
     * highlight recolours rather than covers.
     * {@code pointerX}/{@code pointerY} is the pointer while it is on the
     * content, in the box's own space, else {@link WindowHover#AWAY}.
     * {@code clipX}/{@code clipY} is where the box's whole pixels really
     * stand on the screen, for a scissor.
     */
    public abstract void draw(Minecraft minecraft, LostTalesUiHitBox box,
                       double clipX, double clipY, double pointerX,
                       double pointerY, int alpha, int surfaceAlpha);

    /**
     * Draws the tip of what the pointer rests on, beside
     * {@code tipX}/{@code tipY}, once every window is drawn.
     */
    public void drawTip(Minecraft minecraft, int tipX, int tipY, int screenWidth) {}

    /**
     * What a point on the content is, as a hover of its own kind; null
     * where the content answers as bare content.
     */
    public WindowHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        return null;
    }

    /**
     * Draws what a field of the content opens over everything — the emoji
     * list of a status line — in the content's whole pixels, once every
     * window is drawn; the pointer in the same space.
     */
    public void drawPopups(Minecraft minecraft, PointerRegions regions,
                    double pointerX, double pointerY) {}

    /** What a point on those popups is, in the content's whole pixels; null off them. */
    public WindowHover popupHoverAt(double x, double y) {
        return null;
    }

    /** Puts away a list a field has out; answers whether one was. */
    public boolean dismissPopup() {
        return false;
    }

    /**
     * A press on what {@link #hoverAt} or {@link #popupHoverAt} found, at
     * {@code x}, {@code y} in the content's whole pixels; answers whether
     * the content took it. The content that answers for itself takes its
     * presses here, before any system's part is asked.
     */
    public boolean pressed(WindowHover hover, double x, double y,
                           int button) {
        return false;
    }

    /**
     * The pointer moving with the left button held that went down on the
     * content, at {@code x}, {@code y} in its whole pixels: a slider's knob
     * following the hand.
     */
    public void dragged(double x, double y) {}

    /** The left button that went down on the content has come up. */
    public void released() {}

    /** A wheel turn over the content, in lines, positive toward later rows. */
    public void scrollBy(int lines) {}

    /** Whether one of its fields holds the keys. */
    public boolean holdsKeys() {
        return false;
    }

    /** Keys while it holds them; answers whether it took the key. */
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    /** Its window has come in front: a field that takes the keys as it does takes them. */
    public void takeKeys() {}

    /** Its fields let the keys go. */
    public void releaseKeys() {}

    /**
     * Whether it works on the chat's input bar, and so waits, undrawn,
     * while no window is open to carry one: the pickers.
     */
    public boolean needsInputBar() {
        return false;
    }

    /** The window has opened, or come back with the screen. */
    public void opened() {}

    /** The window has closed. */
    public void closed() {}

    /**
     * What the window needs to come back with the screen as it was: the
     * person a card is about, the message the Reactions window is aimed
     * at, a menu itself; null for nothing beyond its kind.
     */
    public Object sessionState() {
        return null;
    }

    /** The room of a window too large for it, which every cut is kept inside too; null for none. */
    private static LostTalesUiHitBox outerClip;

    /** Keeps every cut inside {@code box} until it is set back to null. */
    static void setOuterClip(LostTalesUiHitBox box) {
        outerClip = box;
    }

    /**
     * Cuts what is drawn next to a box of the screen, {@code left} and
     * {@code top} where it really stands, and never past the outer cut;
     * false where the scissor cannot be had, and nothing is then cut.
     */
    public static boolean beginClip(Minecraft minecraft, double left, double top,
                             double width, double height) {
        if (outerClip != null) {
            double right = Math.min(left + width,
                    outerClip.left + outerClip.width);
            double bottom = Math.min(top + height,
                    outerClip.top + outerClip.height);
            left = Math.max(left, outerClip.left);
            top = Math.max(top, outerClip.top);
            width = Math.max(0.0D, right - left);
            height = Math.max(0.0D, bottom - top);
        }
        try {
            ScaledResolution resolution = new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
            int factor = Math.max(1, resolution.getScaleFactor());
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((int)Math.round(left * factor),
                    (int)Math.round((resolution.getScaledHeight() - top
                            - height) * factor),
                    Math.max(0, (int)Math.round(width * factor)),
                    Math.max(0, (int)Math.round(height * factor)));
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public static void endClip(boolean clipped) {
        if (clipped) {
            GL11.glPopAttrib();
        }
    }
}
