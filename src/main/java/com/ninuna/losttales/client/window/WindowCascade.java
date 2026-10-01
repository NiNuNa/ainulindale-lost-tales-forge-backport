package com.ninuna.losttales.client.window;

/**
 * Where a new window lands: one step right and down from the window it
 * opens from, the way desktop windows stack, so the window behind keeps
 * its tab row in view. A step that would carry the new window off the
 * screen starts that axis again at the margin and keeps stepping along
 * the other, and a window too big to fit is held inside the screen. All
 * in GUI pixels.
 */
final class WindowCascade {
    /**
     * The step, right and down alike: one tab row and the room under its
     * rule, so the window behind shows its whole tab row. Its tabs are how
     * the player gets back to it.
     */
    static final int STEP = TabRow.ROW_HEIGHT
            + WindowPlacement.HISTORY_TOP_MARGIN;

    private WindowCascade() {}

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
     * The corner a box {@code width} by {@code height} takes one step on
     * from the box whose corner is {@code (referenceX, referenceY)}.
     */
    static Corner place(double referenceX, double referenceY, int width,
                        double height, int screenWidth, int screenHeight,
                        int margin, int step) {
        double x = referenceX + step;
        double y = referenceY + step;
        // Each axis starts again on its own: a column that reached the
        // bottom goes on at the top, still a step further right; a row
        // that reached the right edge goes on at the left.
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
