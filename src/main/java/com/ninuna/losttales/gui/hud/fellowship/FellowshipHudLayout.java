package com.ninuna.losttales.gui.hud.fellowship;

import com.ninuna.losttales.gui.hud.HudPlacementLayout;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Where the fellowship HUD stands: one row for each of the travelling
 * fellowship's other members nearest the player, stacked. Pure, so it is
 * tested without drawing.
 */
public final class FellowshipHudLayout {

    public static final int PANEL_WIDTH = 166;
    public static final int ROW_HEIGHT = 29;
    public static final int PANEL_PADDING = 4;
    /** The most rows the panel stacks: the members nearest the player. */
    public static final int MAX_ROWS = 7;

    private FellowshipHudLayout() {}

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

    /**
     * The order the panel shows members in, as indices into the list the
     * distances follow: the nearest first, then those with no distance (in
     * another world, or not seen) in their own order.
     */
    public static Integer[] nearestFirst(final double[] squaredDistances) {
        Integer[] order = new Integer[squaredDistances.length];
        for (int index = 0; index < order.length; index++) {
            order[index] = Integer.valueOf(index);
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer left, Integer right) {
                double near = squaredDistances[left.intValue()];
                double far = squaredDistances[right.intValue()];
                boolean nearKnown = !Double.isNaN(near);
                boolean farKnown = !Double.isNaN(far);
                if (nearKnown != farKnown) {
                    return nearKnown ? -1 : 1;
                }
                int byDistance = nearKnown ? Double.compare(near, far) : 0;
                return byDistance != 0 ? byDistance : left.compareTo(right);
            }
        });
        return order;
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
