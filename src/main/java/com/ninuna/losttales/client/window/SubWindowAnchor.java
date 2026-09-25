package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;

/**
 * Where a sub-window opens from a control or the pointer: the box it
 * hangs from — a control's footprint, or the pointer's point — and
 * which way it grows from it. It opens toward the middle of the
 * window the control belongs to: below a control in the window's
 * upper half, above one in its lower half, lining up with the box's
 * left edge in the window's left half and its right edge in the right
 * half. Only when that side of the screen has less room than the
 * other does it turn round.
 */
public final class SubWindowAnchor {
    /** Clear space between a window's frame and the box it hangs from. */
    public static final int GAP = 2;
    /** From the box it hangs from to the window's own box, its frame between them. */
    public static final int REACH = GAP + LostTalesUiWindowFrame.WIDTH;
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;
    /** Whether it opens below the box rather than above it. */
    public final boolean below;
    /** Whether its right edge lines up with the box's, growing leftward. */
    public final boolean fromRight;
    /** The window the box belongs to, whose room the sub-window opens in; null for the bare screen. */
    public final String windowId;

    public SubWindowAnchor(int left, int top, int right, int bottom,
                           boolean below, boolean fromRight,
                           String windowId) {
        this.left = left;
        this.top = top;
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
        this.below = below;
        this.fromRight = fromRight;
        this.windowId = windowId;
    }

    /**
     * Opening from a box toward the middle of the space spanning
     * {@code spaceLeft}..{@code spaceRight} and
     * {@code spaceTop}..{@code spaceBottom}, in window
     * {@code windowId}.
     */
    public static SubWindowAnchor inward(int left, int top, int right, int bottom,
                         double spaceLeft, double spaceTop,
                         double spaceRight, double spaceBottom,
                         String windowId) {
        double middleX = (spaceLeft + spaceRight) / 2.0D;
        double middleY = (spaceTop + spaceBottom) / 2.0D;
        return new SubWindowAnchor(left, top, right, bottom,
                (top + bottom) / 2.0D < middleY,
                (left + right) / 2.0D > middleX, windowId);
    }

    /**
     * Where a sub-window opened from a control on a window's tab row
     * hangs: the row's band at {@code x}, where the pointer is on the
     * control, and toward the window's middle, which is under the row.
     */
    public static SubWindowAnchor onRow(WindowFrame frame, TabRow.Row row,
                                        int x, int screenWidth,
                                        int screenHeight) {
        return inward(x - 4, TabRow.rowTop(row.rowBottom), x + 4,
                row.rowBottom, frame, screenWidth, screenHeight);
    }

    /**
     * Where a sub-window opened from a control on a window's tool strip
     * hangs: the strip's band at {@code x}, toward the window's middle.
     */
    public static SubWindowAnchor onToolStrip(WindowFrame frame,
                                              TabRow.Row row, int x,
                                              int screenWidth,
                                              int screenHeight) {
        return inward(x - 4, row.rowBottom, x + 4,
                row.rowBottom + WindowPlacement.TOOL_STRIP_HEIGHT, frame,
                screenWidth, screenHeight);
    }

    /**
     * Opening from a box toward the middle of a window, in that
     * window; with none drawn, toward the middle of the bare screen.
     */
    public static SubWindowAnchor inward(int left, int top, int right, int bottom,
                         WindowFrame frame, int screenWidth,
                         int screenHeight) {
        if (frame == null || !frame.drawn) {
            return inward(left, top, right, bottom, 0.0D, 0.0D,
                    screenWidth, screenHeight, null);
        }
        double windowLeft = frame.drawnLeft();
        return inward(left, top, right, bottom, windowLeft,
                frame.boxTop + frame.motionY,
                windowLeft + (frame.boxRight - frame.boxLeft),
                frame.boxBottom + frame.motionY, frame.windowId);
    }
}
