package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiInk;

/**
 * The mark a tab wears after its counters while its channel is linked to
 * Discord, so a player always knows what they say there leaves the game.
 * Five pixels square, on the capitals like the draft mark, in the steel
 * blue that stands for the game's side of Discord, with the chat's one
 * shadow. Drawn from its own pixels until its artwork is on the chat
 * sheet.
 */
final class ChatDiscordMark {
    /** The mark's width and height in GUI pixels. */
    static final int WIDTH = 5;
    /** The mark's rows, left pixel first, read as the bits of each row. */
    private static final int[] ROWS = {
            0b01110,
            0b11111,
            0b10101,
            0b11111,
            0b11011,
    };
    private static final int RGB = LostTalesColors.rgb(LostTalesColors.STEEL_BLUE);

    private ChatDiscordMark() {}

    /** Draws the mark with its top-left at {@code x}, {@code y}. */
    static void draw(int x, int y, int alpha) {
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        int shadow = LostTalesUiInk.argb(LostTalesUiInk.SHADOW,
                LostTalesUiInk.shadowAlpha(alpha));
        drawPixels(x + LostTalesUiInk.SHADOW_OFFSET, y + LostTalesUiInk.SHADOW_OFFSET,
                shadow);
        drawPixels(x, y, LostTalesUiInk.argb(RGB, alpha));
    }

    private static void drawPixels(int x, int y, int argb) {
        for (int row = 0; row < ROWS.length; row++) {
            for (int column = 0; column < WIDTH; column++) {
                if ((ROWS[row] >> (WIDTH - 1 - column) & 1) != 0) {
                    LostTalesUiInk.fillRect(x + column, y + row,
                            x + column + 1, y + row + 1, argb);
                }
            }
        }
    }
}
