package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where a tab carried over a window's content would split it: within a
 * quarter of one of the content's edges, the nearest of them, the tab
 * taking the half on that side. Pure, so it is tested without drawing.
 */
final class SplitDrop {
    static final int LEFT = 0;
    static final int RIGHT = 1;
    static final int TOP = 2;
    static final int BOTTOM = 3;
    /** How far in from an edge a carried tab still splits on it: a share of the content. */
    static final double REACH = 0.25D;

    private SplitDrop() {}

    /** The edge a point in {@code room} would split on; -1 off the content, or away from every edge. */
    static int edgeAt(LostTalesUiHitBox room, double x, double y) {
        if (room == null || room.width <= 0.0D || room.height <= 0.0D
                || !room.contains(x, y)) {
            return -1;
        }
        double[] distances = {
                (x - room.left) / room.width,
                (room.left + room.width - x) / room.width,
                (y - room.top) / room.height,
                (room.top + room.height - y) / room.height};
        int nearest = -1;
        for (int edge = LEFT; edge <= BOTTOM; edge++) {
            if (distances[edge] < REACH
                    && (nearest < 0 || distances[edge] < distances[nearest])) {
                nearest = edge;
            }
        }
        return nearest;
    }

    /**
     * Whether a window with {@code front} in front takes {@code carried}
     * on {@code edge}: a conversation goes only beside the other side,
     * never over or under it.
     */
    static boolean takes(WindowTab front, WindowTab carried, int edge) {
        if (front == null || carried == null || front.equals(carried)) {
            return false;
        }
        return edge == LEFT || edge == RIGHT
                || front instanceof PageTab && carried instanceof PageTab;
    }

    /** The half of {@code room} a tab let go on {@code edge} takes: {@code left, top, right, bottom}. */
    static double[] half(LostTalesUiHitBox room, int edge) {
        return WindowSplit.box(edge == TOP || edge == BOTTOM, 0.5D,
                edge == LEFT || edge == TOP, room.left, room.top,
                room.left + room.width, room.top + room.height);
    }
}
