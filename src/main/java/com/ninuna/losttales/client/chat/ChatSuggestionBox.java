package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PointerRegions;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import net.minecraft.client.gui.FontRenderer;

/**
 * What the completion lists that open above the chat input share: their
 * look and where they stand. Each shows {@link #MAX_ROWS} rows at most,
 * stands them {@link #PADDING} inside its frame, and ends
 * {@link #BOTTOM_MARGIN} above the input. Its frame, its hit test and its
 * rows are read from the same numbers, so what lights under the pointer
 * is what a press takes.
 */
abstract class ChatSuggestionBox {
    /** Rows shown at most. */
    static final int MAX_ROWS = 8;
    /** A row, a menu's: words, or words behind a face or a glyph. */
    static final int ROW_HEIGHT = MenuWindow.ROW_HEIGHT;
    /** The rows stand two pixels inside the frame's ink, as a framed button's content does. */
    static final int PADDING = WindowStyle.POPUP_INSET;
    /**
     * How far above the input anchor ({@link ChatInputBar#inputAnchor})
     * the box ends: one pixel clear of the bar's top.
     */
    static final int BOTTOM_MARGIN = 15;

    /** The anchor at which a box ends at {@code bottom}: for a list over a field that is not the bar. */
    static int anchorEndingAt(int bottom) {
        return bottom + BOTTOM_MARGIN;
    }

    abstract boolean isActive();

    /** The rows the box stands, a fold row included. */
    abstract int shownRows();

    /** The rows a press can take: every row shown but a fold row. */
    int pickableRows() {
        return shownRows();
    }

    /** The box's width, its padding included. */
    abstract int boxWidth(FontRenderer font);

    final int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN - shownRows() * ROW_HEIGHT
                - PADDING * 2;
    }

    /** True while the point is over the open box, its padding included. */
    final boolean contains(FontRenderer font, double mouseX, double mouseY,
                           int screenHeight, int inputX) {
        if (!isActive()) {
            return false;
        }
        int top = boxTop(screenHeight);
        return LostTalesUiHitBox.contains(mouseX, mouseY, inputX, top,
                boxWidth(font), screenHeight - BOTTOM_MARGIN - top);
    }

    /**
     * The row under the point, or -1: the one test the row's highlight,
     * a press and the pointer all ask.
     */
    final int rowAt(FontRenderer font, double mouseX, double mouseY,
                    int screenHeight, int inputX) {
        if (!contains(font, mouseX, mouseY, screenHeight, inputX)
                || mouseY < boxTop(screenHeight) + PADDING) {
            return -1;
        }
        int row = (int)Math.floor((mouseY - boxTop(screenHeight) - PADDING)
                / (double)ROW_HEIGHT);
        return row >= 0 && row < pickableRows() ? row : -1;
    }

    /**
     * Draws the box's frame with {@code litRow} lit and gives its
     * footprint to the pointer. Answers the box's top; the first row
     * stands {@link #PADDING} below it.
     */
    final int drawFrame(FontRenderer font, PointerRegions regions,
                        int screenHeight, int inputX, int litRow) {
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        int bottom = screenHeight - BOTTOM_MARGIN;
        regions.add(inputX, top, inputX + width, bottom);
        WindowStyle.drawPopupList(inputX, top, inputX + width,
                bottom, top + PADDING, ROW_HEIGHT, litRow);
        return top;
    }
}
