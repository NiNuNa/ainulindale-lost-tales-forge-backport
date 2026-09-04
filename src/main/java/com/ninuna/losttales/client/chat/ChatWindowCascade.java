package com.ninuna.losttales.client.chat;

/**
 * Where a window the chat opens by itself lands: one step right and down
 * from the window it is cascaded from, the way desktop windows stack, so
 * the same arrangement always gives the same place. A step that would
 * carry the new window off the screen wraps that axis back to the
 * margin and keeps stepping along the other, and a window too big to
 * fit is clamped into the screen rather than lost. All in GUI pixels.
 */
final class ChatWindowCascade {
    /**
     * The step, right and down alike: one tab row plus the head-room
     * under its rule, so the window behind keeps its whole tab strip in
     * view — its tabs are how the player gets back to it.
     */
    static final int STEP = ChatChannelTabBar.ROW_HEIGHT
            + ChatWindowPlacement.HISTORY_TOP_MARGIN;

    private ChatWindowCascade() {}

    /** A box's top-left corner, in GUI pixels. */
    static final class Corner {
        final double x;
        final double y;

        Corner(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * The corner a box {@code width} by {@code height} takes when cascaded
     * from the box whose corner is {@code (referenceX, referenceY)}.
     */
    static Corner place(double referenceX, double referenceY, int width,
                        double height, int screenWidth, int screenHeight,
                        int margin, int step) {
        double x = referenceX + step;
        double y = referenceY + step;
        // Each axis wraps on its own: a column that has reached the
        // bottom restarts at the top, still one step further right; a
        // row that has reached the right edge restarts at the left.
        if (y + height > screenHeight - margin) {
            y = margin;
        }
        if (x + width > screenWidth - margin) {
            x = margin;
        }
        double maxX = Math.max(margin, screenWidth - margin - width);
        double maxY = Math.max(margin, screenHeight - margin - height);
        return new Corner(Math.max(margin, Math.min(maxX, x)),
                Math.max(margin, Math.min(maxY, y)));
    }
}
