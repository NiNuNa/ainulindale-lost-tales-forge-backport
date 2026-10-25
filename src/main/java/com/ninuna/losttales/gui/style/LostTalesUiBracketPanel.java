package com.ninuna.losttales.gui.style;

import net.minecraft.client.gui.Gui;

/**
 * The bracketed panel: a translucent surface over a shade a pixel down and
 * right, its top edge in sand, its sides and foot in mauve, and a sand
 * bracket at each corner. The map's prompts and its marker card stand on
 * it, and so does the fellowship HUD panel in the windows' primary colour;
 * the brackets alone frame the map marker picked. A popup of the windows
 * wears the window frame instead ({@link LostTalesUiWindowFrame}).
 */
public final class LostTalesUiBracketPanel {
    /** How far a corner's bracket runs along the top or the foot. */
    public static final int BRACKET_ACROSS = 12;
    /** How far it runs down or up the side. */
    public static final int BRACKET_DOWN = 8;
    /** The shade under the panel. */
    private static final int SHADE = LostTalesColors.withAlpha(
            LostTalesColors.PLUM_BLACK, 0x78);
    /** A row under the pointer: plum grey laid over the panel. */
    private static final int HOVERED_ROW = LostTalesColors.withAlpha(
            LostTalesColors.PLUM_GRAY, 0x36);
    /** How far a chosen row's diamond stands its middle from the row's left. */
    private static final int CHOSEN_DIAMOND_X = 8;

    private LostTalesUiBracketPanel() {}

    /** The panel on its own surface, {@link LostTalesColors#PANEL_FILL}. */
    public static void draw(int x, int y, int width, int height) {
        draw(x, y, width, height, LostTalesColors.PANEL_FILL);
    }

    /** The panel from {@code x}, {@code y}, its surface {@code fill}. */
    public static void draw(int x, int y, int width, int height, int fill) {
        Gui.drawRect(x + 1, y + 1, x + width + 1, y + height + 1, SHADE);
        Gui.drawRect(x, y, x + width, y + height, fill);
        Gui.drawRect(x, y, x + width, y + 1, LostTalesColors.BORDER);
        Gui.drawRect(x, y + height - 1, x + width, y + height,
                LostTalesColors.BORDER_DIM);
        Gui.drawRect(x, y, x + 1, y + height, LostTalesColors.BORDER_DIM);
        Gui.drawRect(x + width - 1, y, x + width, y + height,
                LostTalesColors.BORDER_DIM);
        drawCorner(x, y, 1, 1, BRACKET_ACROSS, BRACKET_DOWN,
                LostTalesColors.BORDER);
        drawCorner(x + width - 1, y, -1, 1, BRACKET_ACROSS, BRACKET_DOWN,
                LostTalesColors.BORDER);
        drawCorner(x, y + height - 1, 1, -1, BRACKET_ACROSS, BRACKET_DOWN,
                LostTalesColors.BORDER);
        drawCorner(x + width - 1, y + height - 1, -1, -1, BRACKET_ACROSS,
                BRACKET_DOWN, LostTalesColors.BORDER);
    }

    /**
     * The panel's corner brackets on their own round {@code x}, {@code y},
     * {@code width}, {@code height}, each arm {@code arm} long, in
     * {@code argb}: what frames the map marker picked, in the panels'
     * language without boxing its artwork in.
     */
    public static void drawBrackets(int x, int y, int width, int height,
                                    int arm, int argb) {
        int length = Math.max(1, arm);
        drawCorner(x, y, 1, 1, length, length, argb);
        drawCorner(x + width - 1, y, -1, 1, length, length, argb);
        drawCorner(x, y + height - 1, 1, -1, length, length, argb);
        drawCorner(x + width - 1, y + height - 1, -1, -1, length, length,
                argb);
    }

    /**
     * A row of a list on the panel: a chosen one darker, with a honey bar
     * at its left and a honey diamond beside it; one under the pointer
     * lightened, with a mauve line at its left. Drawn before its words.
     */
    public static void drawRow(int x, int y, int width, int height,
                               boolean chosen, boolean hovered) {
        if (chosen) {
            Gui.drawRect(x, y, x + width, y + height,
                    LostTalesColors.PANEL_SELECTED);
            Gui.drawRect(x, y, x + 2, y + height, LostTalesColors.GOLD);
            LostTalesUiInk.fillDiamond(x + CHOSEN_DIAMOND_X,
                    y + LostTalesUiInk.centredStart(height,
                            LostTalesUiInk.DIAMOND_HEIGHT)
                            + LostTalesUiInk.DIAMOND_HEIGHT / 2,
                    LostTalesColors.GOLD);
        } else if (hovered) {
            Gui.drawRect(x, y, x + width, y + height, HOVERED_ROW);
            Gui.drawRect(x, y, x + 1, y + height, LostTalesColors.BORDER_DIM);
        }
    }

    /**
     * One corner's bracket from its corner pixel {@code x}, {@code y},
     * toward {@code sx}, {@code sy}: an arm reaching {@code across} pixels
     * past the corner along the edge and one reaching {@code down} along
     * the side, each two pixels thick.
     */
    private static void drawCorner(int x, int y, int sx, int sy, int across,
                                   int down, int argb) {
        Gui.drawRect(Math.min(x, x + sx * across), Math.min(y, y + sy),
                Math.max(x, x + sx * across) + 1, Math.max(y, y + sy) + 1,
                argb);
        Gui.drawRect(Math.min(x, x + sx), Math.min(y, y + sy * down),
                Math.max(x, x + sx) + 1, Math.max(y, y + sy * down) + 1,
                argb);
    }
}
