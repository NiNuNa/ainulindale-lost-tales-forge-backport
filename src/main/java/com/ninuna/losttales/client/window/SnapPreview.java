package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Where a carried window will go, shown as a desktop shows it: a pane
 * of frosted glass — what lies behind it blurred, a window's surface
 * faint over that, and a window's frame round it — standing in the part
 * of the screen the window fills when the button comes up. It grows out
 * of the window as the pointer enters a snap zone, travels from zone to
 * zone, and shrinks back into the window when the pointer leaves; the
 * window itself keeps its size under the hand all the while. Let go in a
 * zone, the window glides into the pane as the pane fades where it
 * stands.
 */
public final class SnapPreview {
    /**
     * The share of a window's surface the glass is tinted with: half of
     * what a window's strips wear, so it reads as an empty window rather
     * than as one.
     */
    static final float GLASS_SHARE = 0.5F;

    /** The window the pane belongs to, the carried one, or null while none shows. */
    private String windowId;
    /** The carried window's pane. */
    private final Track own = new Track();

    SnapPreview() {
        reset();
    }

    /** A pane's box, the window's box it stands for: the frame lies round it. */
    public static final class Pane {
        final double left;
        final double top;
        final double right;
        final double bottom;

        Pane(double left, double top, double right, double bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        /** Every edge {@code share} of the way from this pane to {@code to}. */
        Pane toward(Pane to, double share) {
            return new Pane(this.left + (to.left - this.left) * share,
                    this.top + (to.top - this.top) * share,
                    this.right + (to.right - this.right) * share,
                    this.bottom + (to.bottom - this.bottom) * share);
        }

        /** The pane laid on whole display pixels, edge by edge. */
        Pane snapped() {
            return new Pane(LostTalesDisplayPixels.snap(this.left),
                    LostTalesDisplayPixels.snap(this.top),
                    LostTalesDisplayPixels.snap(this.right),
                    LostTalesDisplayPixels.snap(this.bottom));
        }
    }

    /**
     * One frame of a carry: the pane is bound for {@code fill} for the
     * window {@code windowId}, or back into the window for none. A new aim
     * starts a leg from the pane as it stands.
     */
    void aim(String windowId, Window.ScreenFill fill) {
        if (windowId == null) {
            reset();
            return;
        }
        if (!windowId.equals(this.windowId)) {
            // Another window's pane, still fading, gives way at once.
            reset();
            this.windowId = windowId;
        }
        this.own.aim(fill);
    }

    /**
     * The carry is over. Let go into the pane ({@code filled}), the
     * window glides into it and the pane fades where it stands; otherwise
     * the pane goes back into the window.
     */
    void release(boolean filled) {
        if (filled && this.own.isBound()) {
            this.own.land();
            return;
        }
        aim(this.windowId, Window.ScreenFill.NONE);
    }

    /** Forgets the pane at once. */
    void reset() {
        this.windowId = null;
        this.own.reset();
    }

    /** The window the pane is drawn under — between it and the windows behind it — or null. */
    public String windowId() {
        return this.windowId;
    }

    /**
     * Moves the carried window's pane on to this instant and answers
     * where it stands, on whole display pixels, or null while nothing of
     * it shows. {@code window} is the window's box as it is drawn this
     * frame: where a pane growing out of the window sets out, and where
     * one going back into it is bound. Once no pane shows at all, the
     * preview is let go.
     */
    public Pane advance(Minecraft minecraft, WindowPlacement.Box window,
                 int screenWidth, int screenHeight) {
        if (this.windowId == null || window == null) {
            return null;
        }
        Pane pane = this.own.advance(minecraft, window, screenWidth,
                screenHeight, System.nanoTime());
        if (pane == null) {
            reset();
        }
        return pane;
    }

    /** How much of the carried window's pane shows, 0..1, as last advanced. */
    public float opacity() {
        return this.own.opacity();
    }

    /**
     * One pane's way: out of its window into a part of the screen, from
     * part to part, and back into the window.
     */
    private static final class Track {
        private final MotionTransition shown =
                new MotionTransition(MotionIds.WINDOW_SNAP_PREVIEW);
        private final MotionTransition glide =
                new MotionTransition(MotionIds.WINDOW_SNAP_PREVIEW, true);
        /** What the running leg is bound for: a part of the screen, or none for the window itself. */
        private Window.ScreenFill legTo = Window.ScreenFill.NONE;
        /** Where the running leg set out: the pane as drawn then, or null for the window itself. */
        private Pane legFrom;
        /** The pane as last placed, or null. */
        private Pane placed;
        /** Whether the window was let go into the pane, which then fades where it stands. */
        private boolean landing;

        Track() {
            reset();
        }

        /** Binds the pane for {@code fill}, or back into the window for none. */
        void aim(Window.ScreenFill fill) {
            Window.ScreenFill wanted = fill == null
                    ? Window.ScreenFill.NONE : fill;
            this.landing = false;
            if (!wanted.equals(this.legTo)) {
                this.legFrom = this.placed;
                this.legTo = wanted;
                this.glide.settle(false);
            }
        }

        boolean isBound() {
            return this.legTo != Window.ScreenFill.NONE;
        }

        /** The window was let go into the pane: it fades where it stands. */
        void land() {
            this.landing = true;
        }

        /**
         * Back at rest, hidden, so the next pane fades in rather than
         * standing at once as a first sight would.
         */
        void reset() {
            this.legTo = Window.ScreenFill.NONE;
            this.legFrom = null;
            this.placed = null;
            this.landing = false;
            this.shown.settle(false);
            this.glide.settle(true);
        }

        /**
         * Moves the pane on to {@code now} and answers where it stands,
         * on whole display pixels, growing out of or going back into
         * {@code window}; null once nothing of it shows, which sets it
         * back at rest.
         */
        Pane advance(Minecraft minecraft, WindowPlacement.Box window,
                     int screenWidth, int screenHeight, long now) {
            boolean on = isBound() && !this.landing;
            float opacity = this.shown.advance(now, on);
            float share = this.glide.advance(now, true);
            if (!on && this.shown.isSettled() && opacity <= 0.0F) {
                reset();
                return null;
            }
            Pane own = new Pane(window.x, window.y, window.right(),
                    window.bottom());
            Pane from = this.legFrom == null ? own : this.legFrom;
            Pane to = own;
            if (isBound()) {
                WindowPlacement.Box fill = WindowPlacement.fillBounds(
                        this.legTo, minecraft, screenWidth, screenHeight);
                to = new Pane(fill.x, fill.y, fill.right(), fill.bottom());
            }
            this.placed = from.toward(to, share);
            return this.placed.snapped();
        }

        float opacity() {
            return this.shown.clamped();
        }
    }

    /** The glass's surface over the pane with every hole left out. */
    private static void fillGlass(float left, float top, float right,
                                  float bottom, List<LostTalesUiHitBox> holes,
                                  int argb) {
        float cursor = top;
        for (LostTalesUiHitBox hole : holes) {
            float holeLeft = (float)hole.left;
            float holeTop = (float)hole.top;
            float holeRight = (float)hole.right();
            float holeBottom = (float)hole.bottom();
            LostTalesUiInk.fillRect(left, cursor, right, holeTop,
                    argb);
            LostTalesUiInk.fillRect(left, holeTop, holeLeft,
                    holeBottom, argb);
            LostTalesUiInk.fillRect(holeRight, holeTop, right,
                    holeBottom, argb);
            LostTalesUiFramedButton.fillCorners(holeLeft, holeTop,
                    holeRight - holeLeft, holeBottom - holeTop, argb);
            cursor = holeBottom;
        }
        LostTalesUiInk.fillRect(left, cursor, right, bottom,
                argb);
    }

    /**
     * Draws the pane at {@code opacity}: the blurred frame under it and
     * its frame's ring — a window's own rectangle of the blur — the
     * glass's faint surface inside, and the ring's surface and white
     * edges round it exactly as a window wears them, each edge fading
     * along its length from the lit corner.
     */
    public static void draw(Minecraft minecraft, Pane pane, float opacity) {
        draw(minecraft, pane, opacity,
                Collections.<LostTalesUiHitBox>emptyList());
    }

    /**
     * As above, with {@code holes} left out of the glass's surface for
     * what stands on it with a surface of its own — snap assist's cards,
     * framed buttons whose corner pixels stay the glass's — so no surface
     * lies over another. The holes are one column, top to bottom.
     */
    public static void draw(Minecraft minecraft, Pane pane, float opacity,
                     List<LostTalesUiHitBox> holes) {
        if (minecraft == null || pane == null || opacity <= 0.0F) {
            return;
        }
        float left = (float)pane.left;
        float top = (float)pane.top;
        float right = (float)pane.right;
        float bottom = (float)pane.bottom;
        if (right - left < 2.0F || bottom - top < 2.0F) {
            return;
        }
        LostTalesUiHitBox standsIn = new LostTalesUiHitBox(left, top,
                right - left, bottom - top);
        WindowDrawing.cutBehind(standsIn, opacity);
        WindowDrawing.softenBehind(standsIn, opacity);
        float surfaceShare = opacity
                * WindowStyle.opacity(minecraft);
        fillGlass(left, top, right, bottom, holes,
                WindowStyle.surfaceArgb(GLASS_SHARE
                        * surfaceShare));
        LostTalesUiWindowFrame.drawSurface(left, top, right,
                bottom, WindowStyle.surfaceArgb(surfaceShare));
        LostTalesUiWindowFrame.drawEdges(left, top, right, bottom,
                Math.round(255.0F * Math.min(1.0F, opacity)));
    }
}
