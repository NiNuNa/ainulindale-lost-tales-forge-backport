package com.ninuna.losttales.gui.hud.party;

/** Pure layout helper so party HUD placement can be validated without rendering. */
public final class PartyHudLayout {

    public static final int PANEL_WIDTH = 166;
    public static final int ROW_HEIGHT = 29;
    public static final int PANEL_PADDING = 4;

    private PartyHudLayout() {}

    public static Bounds calculate(int screenWidth,
                                   int screenHeight,
                                   int offsetX,
                                   int offsetY,
                                   int rowCount) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int rows = Math.max(1, Math.min(3, rowCount));
        int width = Math.max(1, Math.min(PANEL_WIDTH, safeWidth - 8));
        int height = PANEL_PADDING * 2 + rows * ROW_HEIGHT;
        height = Math.max(1, Math.min(height, safeHeight - 8));

        int x = safeWidth * clampPercent(offsetX) / 100;
        int y = safeHeight * clampPercent(offsetY) / 100;
        x = clamp(x, 4, Math.max(4, safeWidth - width - 4));
        y = clamp(y, 4, Math.max(4, safeHeight - height - 4));
        return new Bounds(x, y, width, height, rows);
    }

    private static int clampPercent(int value) {
        return clamp(value, 0, 100);
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
        public final int rowCount;

        private Bounds(int x, int y, int width, int height, int rowCount) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rowCount = rowCount;
        }
    }
}
