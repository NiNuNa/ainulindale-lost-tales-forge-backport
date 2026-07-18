package com.ninuna.losttales.gui.hud;

/**
 * Shared, resolution-independent positioning rules for movable HUD panels.
 */
public final class HudPlacementLayout {
    public static final int SCREEN_MARGIN = 4;

    private HudPlacementLayout() {}

    public static Bounds calculate(
            int screenWidth,
            int screenHeight,
            int requestedWidth,
            int requestedHeight,
            double offsetX,
            double offsetY,
            CoordinateMode horizontalMode,
            CoordinateMode verticalMode) {
        return calculate(screenWidth, screenHeight, requestedWidth,
                requestedHeight, offsetX, offsetY, horizontalMode,
                verticalMode, 0, 0);
    }

    public static Bounds calculate(
            int screenWidth,
            int screenHeight,
            int requestedWidth,
            int requestedHeight,
            double offsetX,
            double offsetY,
            CoordinateMode horizontalMode,
            CoordinateMode verticalMode,
            int pixelOffsetX,
            int pixelOffsetY) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int width = Math.max(1, Math.min(requestedWidth,
                Math.max(1, safeWidth - SCREEN_MARGIN * 2)));
        int height = Math.max(1, Math.min(requestedHeight,
                Math.max(1, safeHeight - SCREEN_MARGIN * 2)));
        int x = positionForPercent(offsetX, safeWidth, width,
                horizontalMode, pixelOffsetX);
        int y = positionForPercent(offsetY, safeHeight, height,
                verticalMode, pixelOffsetY);
        return new Bounds(
                clamp(x, SCREEN_MARGIN,
                        Math.max(SCREEN_MARGIN,
                                safeWidth - width - SCREEN_MARGIN)),
                clamp(y, SCREEN_MARGIN,
                        Math.max(SCREEN_MARGIN,
                                safeHeight - height - SCREEN_MARGIN)),
                width,
                height);
    }

    public static double percentForPosition(
            int position,
            int screenSize,
            int elementSize,
            CoordinateMode mode,
            int pixelOffset) {
        int safeScreenSize = Math.max(1, screenSize);
        if (mode == CoordinateMode.AVAILABLE_SPACE_PERCENT) {
            int travel = Math.max(0, safeScreenSize
                    - elementSize - SCREEN_MARGIN * 2);
            if (travel == 0) {
                return 0;
            }
            return clampPercent(
                    (position - SCREEN_MARGIN) * 100.0D / travel);
        }
        return clampPercent(
                (position - pixelOffset) * 100.0D / safeScreenSize);
    }

    public static DragResult constrainDrag(
            int requestedX,
            int requestedY,
            int width,
            int height,
            int screenWidth,
            int screenHeight,
            int snapThreshold) {
        int x = requestedX;
        int y = requestedY;
        int centeredX = (screenWidth - width) / 2;
        int centeredY = (screenHeight - height) / 2;
        boolean snappedX = Math.abs(
                requestedX + width / 2 - screenWidth / 2)
                <= Math.max(0, snapThreshold);
        boolean snappedY = Math.abs(
                requestedY + height / 2 - screenHeight / 2)
                <= Math.max(0, snapThreshold);
        if (snappedX) {
            x = centeredX;
        }
        if (snappedY) {
            y = centeredY;
        }
        x = clamp(x, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN,
                        screenWidth - width - SCREEN_MARGIN));
        y = clamp(y, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN,
                        screenHeight - height - SCREEN_MARGIN));
        return new DragResult(x, y, snappedX, snappedY);
    }

    private static int positionForPercent(
            double percent,
            int screenSize,
            int elementSize,
            CoordinateMode mode,
            int pixelOffset) {
        double boundedPercent = clampPercent(percent);
        if (mode == CoordinateMode.AVAILABLE_SPACE_PERCENT) {
            int travel = Math.max(0,
                    screenSize - elementSize - SCREEN_MARGIN * 2);
            return SCREEN_MARGIN + (int)Math.round(
                    travel * boundedPercent / 100.0D);
        }
        return (int)Math.round(screenSize * boundedPercent / 100.0D)
                + pixelOffset;
    }

    private static double clampPercent(double value) {
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

    public enum CoordinateMode {
        SCREEN_PERCENT,
        AVAILABLE_SPACE_PERCENT
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

    public static final class DragResult {
        public final int x;
        public final int y;
        public final boolean snappedX;
        public final boolean snappedY;

        private DragResult(int x, int y,
                           boolean snappedX, boolean snappedY) {
            this.x = x;
            this.y = y;
            this.snappedX = snappedX;
            this.snappedY = snappedY;
        }
    }
}
