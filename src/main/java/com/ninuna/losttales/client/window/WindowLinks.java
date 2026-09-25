package com.ninuna.losttales.client.window;

import java.util.List;

/**
 * Windows stuck together: a window locked while it touches another sticks
 * to it and moves with it from then on, and unlocking lets go again. The
 * locked window is the one that follows, since a locked window is the one
 * that cannot be dragged.
 */
public final class WindowLinks {
    private WindowLinks() {}

    /** Locks or unlocks a window, and with it whether it is stuck to a neighbour. */
    public static void setLocked(Window window, boolean locked) {
        if (window == null) {
            return;
        }
        WindowLayout.setLocked(window.getId(), locked);
        if (!locked) {
            WindowLayout.unlink(window.getId());
            return;
        }
        WindowFrame frame = WindowFrame.find(window.getId());
        if (frame == null || !frame.drawn) {
            return;
        }
        Window neighbour = touchingWindow(frame);
        if (neighbour != null
                // The window it touches may already be stuck to this one;
                // two windows never hold each other.
                && !window.getId().equals(neighbour.getLinkTarget())) {
            WindowLayout.link(window.getId(), neighbour.getId(),
                    touchingSide(frame, neighbour));
        }
    }

    /** The window this one is resting against, or null. */
    private static Window touchingWindow(WindowFrame frame) {
        List<WindowFrame> frames = WindowFrame.drawnFrames();
        for (int index = 0; index < frames.size(); index++) {
            WindowFrame other = frames.get(index);
            if (other == frame) {
                continue;
            }
            Window candidate = WindowLayout.window(other.windowId);
            // A window filling the screen has no edge of its own to be
            // stuck to.
            if (candidate != null
                    && candidate.getFill() == Window.ScreenFill.NONE
                    && touchingSide(frame, candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Which of a neighbour's edges this window is resting against, or
     * null when it is against none of them. The same margin a snap uses,
     * so a window that showed the touch highlight is one that sticks.
     */
    private static Window.LinkSide touchingSide(WindowFrame frame,
                                                Window neighbour) {
        WindowFrame other = WindowFrame.find(neighbour.getId());
        if (other == null || !other.drawn) {
            return null;
        }
        return touchingSide(frame, other);
    }

    /** As above between two drawn frames. */
    static Window.LinkSide touchingSide(WindowFrame frame, WindowFrame other) {
        int margin = WindowPlacement.WINDOW_GAP;
        boolean overlapsColumn = frame.boxLeft < other.boxRight + margin
                && frame.boxRight + margin > other.boxLeft;
        boolean overlapsRow = frame.boxTop < other.boxBottom + margin
                && frame.boxBottom + margin > other.boxTop;
        if (overlapsColumn) {
            if (Math.abs(other.boxTop - margin - frame.boxBottom)
                    <= WindowGestures.LINK_SNAP) {
                return Window.LinkSide.ABOVE;
            }
            if (Math.abs(frame.boxTop - (other.boxBottom + margin))
                    <= WindowGestures.LINK_SNAP) {
                return Window.LinkSide.BELOW;
            }
        }
        if (overlapsRow) {
            if (Math.abs(frame.boxRight + margin - other.boxLeft)
                    <= WindowGestures.LINK_SNAP) {
                return Window.LinkSide.LEFT;
            }
            if (Math.abs(frame.boxLeft - (other.boxRight + margin))
                    <= WindowGestures.LINK_SNAP) {
                return Window.LinkSide.RIGHT;
            }
        }
        return null;
    }
}
