package com.ninuna.losttales.gui.hud.party;

import com.ninuna.losttales.gui.hud.HudPlacementLayout;

/** Pure layout helper so party HUD placement can be validated without rendering. */
public final class PartyHudLayout {

    public static final int PANEL_WIDTH = 166;
    public static final int ROW_HEIGHT = 29;
    public static final int PANEL_PADDING = 4;

    private PartyHudLayout() {}

    public static Bounds calculate(int screenWidth,
                                   int screenHeight,
                                   double offsetX,
                                   double offsetY,
                                   int rowCount) {
        int rows = Math.max(1, Math.min(3, rowCount));
        int height = PANEL_PADDING * 2 + rows * ROW_HEIGHT;
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                screenWidth, screenHeight, PANEL_WIDTH, height,
                offsetX, offsetY,
                HudPlacementLayout.CoordinateMode.SCREEN_PERCENT,
                HudPlacementLayout.CoordinateMode.SCREEN_PERCENT);
        return new Bounds(bounds.x, bounds.y,
                bounds.width, bounds.height, rows);
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
