package com.ninuna.losttales.client.window;

/**
 * Where a new window lands: one step right and down from the window it
 * opens from, the way desktop windows stack, so the window behind keeps
 * the top of its tab row in view. A step that would carry the new window
 * off the screen starts that axis again at the margin and keeps stepping
 * along the other, and a window too big to fit is held inside the screen.
 * All in GUI pixels.
 */
final class WindowCascade {
    /**
     * The step, right and down alike: enough for the window behind to
     * show its frame and the tops of its tabs, short of their names. A
     * click there brings it back to the front.
     */
    static final int STEP = 10;

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
