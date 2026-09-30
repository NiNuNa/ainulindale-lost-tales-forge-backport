package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiInk;

/**
 * The small mark a tab's icon wears at its top-left corner while the tab
 * stays where others go: a diamond for a tab kept in every view, a pin for
 * one pinned to stay on screen while playing, the pin where it is both. In
 * honey, casting the one shadow. Drawn from its own pixels, standing in
 * until the marks' artwork is painted.
 */
final class TabStayMark {
    /** How far the mark stands out past the icon's left and top edges. */
    private static final int OVERHANG = 1;

    private static final String[] KEPT = {
            ".#.",
            "###",
            ".#."};
    private static final String[] PINNED = {
            "###",
            ".#.",
            ".#."};

    private TabStayMark() {}

    /**
     * Draws the tab's mark at the corner of its icon drawn from
     * {@code (iconX, iconY)}, at {@code alpha}; nothing for a tab that is
     * neither kept nor pinned.
     */
    static void draw(WindowTab tab, float iconX, float iconY, int alpha) {
        boolean pinned = WindowLayout.isPinned(tab);
        if (!pinned && !WindowLayout.isKept(tab)
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        String[] rows = pinned ? PINNED : KEPT;
        float x = iconX - OVERHANG;
        float y = iconY - OVERHANG;
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            drawPixels(rows, x + LostTalesUiInk.SHADOW_OFFSET,
                    y + LostTalesUiInk.SHADOW_OFFSET,
                    LostTalesUiInk.argb(LostTalesUiInk.SHADOW, shadow));
        }
        drawPixels(rows, x, y, LostTalesUiInk.argb(
                LostTalesColors.HONEY & 0xFFFFFF, alpha));
        LostTalesUiInk.beginContent();
    }

    private static void drawPixels(String[] rows, float x, float y, int argb) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) == '#') {
                    LostTalesUiInk.fillRect(x + column, y + row,
                            x + column + 1, y + row + 1, argb);
                }
            }
        }
    }
}
