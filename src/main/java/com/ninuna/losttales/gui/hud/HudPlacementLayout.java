package com.ninuna.losttales.gui.hud;

/**
 * Where a HUD panel stands, by the one rule every panel follows (MAP-10):
 * its Across and Down are each a share of the room its box can move in,
 * the screen less the box. 0 puts the box flush with the left or top
 * edge, 100 flush with the right or bottom, 50 centres it. So a panel
 * stands at the same share at every screen size and never leaves the
 * screen. A panel drawn larger than its box grows out of it to the right
 * and down, held inside the screen ({@link #drawn}).
 *
 * <p>The margin is zero: a panel's travel reaches the screen's edge.</p>
 */
public final class HudPlacementLayout {
    public static final int SCREEN_MARGIN = 0;

    private HudPlacementLayout() {}

    /**
     * The box {@code boxWidth} by {@code boxHeight} at {@code across} and
     * {@code down} on a screen of that size. A box larger than the screen
     * is cut to it.
     */
    public static Bounds calculate(
            int screenWidth,
            int screenHeight,
            int boxWidth,
            int boxHeight,
            double across,
            double down) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int width = fitted(boxWidth, safeWidth);
        int height = fitted(boxHeight, safeHeight);
        return new Bounds(place(across, safeWidth, width),
                place(down, safeHeight, height), width, height);
    }

    /**
     * Where a box {@code boxSize} long stands at {@code share} of its room
     * on a screen {@code screenSize} long: its left or top, on a whole
     * pixel. A share past 0 or 100 counts as that end.
     */
    public static int place(double share, int screenSize, int boxSize) {
        return SCREEN_MARGIN + (int)Math.round(
                room(screenSize, boxSize) * clampShare(share) / 100.0D);
    }

    /**
     * The share of its room a box {@code boxSize} long has, standing at
     * {@code place}: the inverse of {@link #place}, held between 0 and
     * 100. A box with no room to move in has 0.
     */
    public static double share(double place, int screenSize, int boxSize) {
        int room = room(screenSize, boxSize);
        if (room == 0 || Double.isNaN(place) || Double.isInfinite(place)) {
            return 0.0D;
        }
        return clampShare((place - SCREEN_MARGIN) * 100.0D / room);
    }

    /**
     * A panel {@code width} by {@code height} drawn in its {@code box}:
     * from the box's top left, held inside the screen where it is larger
     * than its box, and cut to the screen where it is larger than that.
     */
    public static Bounds drawn(Bounds box, int width, int height,
                               int screenWidth, int screenHeight) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int drawnWidth = fitted(width, safeWidth);
        int drawnHeight = fitted(height, safeHeight);
        return new Bounds(
                clamp(box.x, SCREEN_MARGIN,
                        SCREEN_MARGIN + room(safeWidth, drawnWidth)),
                clamp(box.y, SCREEN_MARGIN,
                        SCREEN_MARGIN + room(safeHeight, drawnHeight)),
                drawnWidth, drawnHeight);
    }

    /**
     * Where a panel carried to {@code requestedX, requestedY} lands: pulled
     * to the screen's middle within {@code snapThreshold} pixels, and held
     * inside the screen. It moves in fractions of a pixel.
     */
    public static PreciseDragResult constrainDrag(
            double requestedX,
            double requestedY,
            int width,
            int height,
            int screenWidth,
            int screenHeight,
            int snapThreshold) {
        double x = requestedX;
        double y = requestedY;
        boolean snappedX = Math.abs(
                requestedX + width / 2.0D - screenWidth / 2.0D)
                <= Math.max(0, snapThreshold);
        boolean snappedY = Math.abs(
                requestedY + height / 2.0D - screenHeight / 2.0D)
                <= Math.max(0, snapThreshold);
        // Centred on the column and row the page draws its centre guides on
        // (screenWidth / 2, screenHeight / 2). Halving the leftover space
        // instead would put an odd-sized panel's centre pixel (the compass
        // is 257 wide) one column left of the guide it snapped to.
        if (snappedX) {
            x = screenWidth / 2 - width / 2;
        }
        if (snappedY) {
            y = screenHeight / 2 - height / 2;
        }
        x = Math.max(SCREEN_MARGIN, Math.min(
                Math.max(SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN),
                x));
        y = Math.max(SCREEN_MARGIN, Math.min(
                Math.max(SCREEN_MARGIN, screenHeight - height - SCREEN_MARGIN),
                y));
        return new PreciseDragResult(x, y, snappedX, snappedY);
    }

    /** The room a box that long has to move in on a screen that long. */
    private static int room(int screenSize, int boxSize) {
        return Math.max(0, screenSize - boxSize - SCREEN_MARGIN * 2);
    }

    /** A size cut to what the screen holds within its margins, at least a pixel. */
    private static int fitted(int size, int screenSize) {
        return Math.max(1, Math.min(size,
                Math.max(1, screenSize - SCREEN_MARGIN * 2)));
    }

    private static double clampShare(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }

    public static final class Bounds {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static final class PreciseDragResult {
        public final double x;
        public final double y;
        public final boolean snappedX;
        public final boolean snappedY;

        private PreciseDragResult(double x, double y,
                                  boolean snappedX, boolean snappedY) {
            this.x = x;
            this.y = y;
            this.snappedX = snappedX;
            this.snappedY = snappedY;
        }
    }
}
