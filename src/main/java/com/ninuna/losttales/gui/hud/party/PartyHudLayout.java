package com.ninuna.losttales.gui.hud.party;

import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.party.model.Party;

/**
 * Where the party HUD stands: one row for each other member, stacked, as
 * many as a party can hold besides the player. Pure, so it is tested
 * without drawing.
 */
public final class PartyHudLayout {

    public static final int PANEL_WIDTH = 166;
    public static final int ROW_HEIGHT = 29;
    public static final int PANEL_PADDING = 4;
    /** The most rows the panel stacks: every member but the player. */
    public static final int MAX_ROWS = Party.MAX_MEMBERS - 1;

    private PartyHudLayout() {}

    public static Bounds calculate(int screenWidth,
                                   int screenHeight,
                                   double offsetX,
                                   double offsetY,
                                   int rowCount) {
        int rows = Math.max(1, Math.min(MAX_ROWS, rowCount));
        int height = height(rows);
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                screenWidth, screenHeight, PANEL_WIDTH, height,
                offsetX, offsetY,
                HudPlacementLayout.CoordinateMode.SCREEN_PERCENT,
                HudPlacementLayout.CoordinateMode.SCREEN_PERCENT);
        return new Bounds(bounds.x, bounds.y,
                bounds.width, bounds.height, rows);
    }

    /** The panel's height with this many rows. */
    public static int height(int rowCount) {
        return PANEL_PADDING * 2 + rowCount * ROW_HEIGHT;
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
