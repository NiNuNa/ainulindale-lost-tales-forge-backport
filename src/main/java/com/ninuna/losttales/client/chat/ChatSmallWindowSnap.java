package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.List;

/**
 * Where a small window being moved or resized sticks: to the screen's
 * margins and to every other window, as a chat window sticks to its
 * neighbours. An edge within {@link #REACH} of a place it can rest against
 * lands there — beside another window with the gap windows keep, on the
 * screen's margin, or in line with the edge of a window it stands beside
 * or over. Only the edges that move are stuck, and only the nearest of
 * them on each axis, so a window never jumps across the screen.
 */
final class ChatSmallWindowSnap {
    /** How far an edge reaches for a place to stick to: a chat window's reach. */
    static final double REACH = ChatTabActions.LINK_SNAP;
    private static final double GAP = ChatWindowPlacement.WINDOW_GAP;
    private static final double MARGIN = ChatWindowPlacement.EDGE_MARGIN;

    private ChatSmallWindowSnap() {}

    /** The moved {@code box} shifted so its nearest edges stick. */
    static LostTalesUiHitBox moved(LostTalesUiHitBox box,
                                   List<LostTalesUiHitBox> others,
                                   int screenWidth, int screenHeight) {
        double dx = shift(box, others, screenWidth, true, true, true);
        double dy = shift(box, others, screenHeight, false, true, true);
        return new LostTalesUiHitBox(box.left + dx, box.top + dy, box.width,
                box.height);
    }

    /**
     * The resized {@code box} with the edges {@code edge} carries stuck,
     * the edges across from them where they were.
     */
    static LostTalesUiHitBox resized(LostTalesUiHitBox box,
                                     ChatWindowGestures.ResizeEdge edge,
                                     List<LostTalesUiHitBox> others,
                                     int screenWidth, int screenHeight) {
        double left = box.left;
        double top = box.top;
        double right = box.left + box.width;
        double bottom = box.top + box.height;
        if (edge.horizontal) {
            double dx = shift(box, others, screenWidth, true, edge.fromLeft,
                    !edge.fromLeft);
            if (edge.fromLeft) {
                left += dx;
            } else {
                right += dx;
            }
        }
        if (edge.vertical) {
            double dy = shift(box, others, screenHeight, false, edge.fromTop,
                    !edge.fromTop);
            if (edge.fromTop) {
                top += dy;
            } else {
                bottom += dy;
            }
        }
        return new LostTalesUiHitBox(left, top, right - left, bottom - top);
    }

    /**
     * The shift, along x when {@code horizontal} else along y, that sticks
     * the nearest of the box's start ({@code start}) and end ({@code end})
     * edges to a place within reach; 0 where none is.
     */
    private static double shift(LostTalesUiHitBox box,
                                List<LostTalesUiHitBox> others, int screen,
                                boolean horizontal, boolean start,
                                boolean end) {
        double low = horizontal ? box.left : box.top;
        double high = low + (horizontal ? box.width : box.height);
        Best best = new Best();
        if (start) {
            best.offer(MARGIN - low);
        }
        if (end) {
            best.offer(screen - MARGIN - high);
        }
        if (others != null) {
            for (LostTalesUiHitBox other : others) {
                if (!beside(box, other, horizontal)) {
                    continue;
                }
                double otherLow = horizontal ? other.left : other.top;
                double otherHigh = otherLow
                        + (horizontal ? other.width : other.height);
                if (start) {
                    // Beside it, past its far edge; or in line with it.
                    best.offer(otherHigh + GAP - low);
                    best.offer(otherLow - low);
                }
                if (end) {
                    best.offer(otherLow - GAP - high);
                    best.offer(otherHigh - high);
                }
            }
        }
        return best.shift;
    }

    /**
     * Whether {@code other} stands across the axis from the box closely
     * enough to stick to: their spans on the other axis overlap, or come
     * within the gap and the reach of each other.
     */
    private static boolean beside(LostTalesUiHitBox box,
                                  LostTalesUiHitBox other,
                                  boolean horizontal) {
        double low = horizontal ? box.top : box.left;
        double high = low + (horizontal ? box.height : box.width);
        double otherLow = horizontal ? other.top : other.left;
        double otherHigh = otherLow + (horizontal ? other.height : other.width);
        return otherLow - GAP - REACH <= high && otherHigh + GAP + REACH >= low;
    }

    /** The smallest shift offered within reach. */
    private static final class Best {
        double shift;
        private double distance = REACH + 1.0E-6D;

        void offer(double candidate) {
            double distance = Math.abs(candidate);
            if (distance < this.distance) {
                this.distance = distance;
                this.shift = candidate;
            }
        }
    }
}
