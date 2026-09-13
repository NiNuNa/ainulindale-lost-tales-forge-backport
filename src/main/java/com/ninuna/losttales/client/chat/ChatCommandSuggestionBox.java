package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/**
 * The command tab-completion list, shown above the input like the emoji
 * and mention completions instead of printed into the chat: the
 * candidates are composition UI, and a popup leaves nothing in the
 * history or the feed. Unlike its siblings this box is fed explicitly —
 * the candidates arrive asynchronously from the server — and the screen
 * clears it on any keystroke that is not another Tab. The highlighted
 * row is the candidate currently standing in the field; walking the list
 * (Tab, or Up and Down) replaces the word, as vanilla's cycling does.
 */
final class ChatCommandSuggestionBox {
    /** Rows shown; more candidates fold into a trailing count. */
    static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 11;
    private static final int PADDING = 2;
    /**
     * How far above the input anchor ({@link ChatInputBar#inputAnchor})
     * the box ends: one pixel clear of the bar's top.
     */
    private static final int BOTTOM_MARGIN = 15;

    private List<String> candidates = Collections.emptyList();
    private int selectedIndex = -1;

    /** Shows a fresh candidate list, nothing highlighted yet. */
    void show(List<String> shown) {
        this.candidates = shown == null || shown.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(shown));
        this.selectedIndex = -1;
    }

    /** Highlights the candidate standing in the field; -1 for none. */
    void setSelected(int index) {
        this.selectedIndex = index;
    }

    void clear() {
        this.candidates = Collections.emptyList();
        this.selectedIndex = -1;
    }

    boolean isActive() {
        return !this.candidates.isEmpty();
    }

    /** Rows drawn: the candidates, capped, plus a possible fold row. */
    private int rowCount() {
        int shown = Math.min(this.candidates.size(), MAX_ROWS);
        return shown + (this.candidates.size() > MAX_ROWS ? 1 : 0);
    }

    boolean contains(FontRenderer font, double mouseX, double mouseY,
                     int screenHeight, int inputX) {
        if (!isActive()) {
            return false;
        }
        return mouseX >= inputX && mouseX < inputX + boxWidth(font)
                && mouseY >= boxTop(screenHeight)
                && mouseY < screenHeight - BOTTOM_MARGIN;
    }

    /** The candidate index under the mouse, or -1. Fold row answers -1. */
    int candidateAt(FontRenderer font, double mouseX, double mouseY,
                    int screenHeight, int inputX) {
        if (!contains(font, mouseX, mouseY, screenHeight, inputX)
                || mouseY < boxTop(screenHeight) + PADDING) {
            return -1;
        }
        int row = (int)Math.floor((mouseY - boxTop(screenHeight) - PADDING)
                / (double)ROW_HEIGHT);
        return row >= 0 && row < Math.min(this.candidates.size(), MAX_ROWS)
                ? row : -1;
    }

    void draw(FontRenderer font, ChatPointerRegions regions,
              int screenHeight, int inputX, double mouseX, double mouseY) {
        if (!isActive()) {
            return;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        int bottom = screenHeight - BOTTOM_MARGIN;
        int hoveredRow = candidateAt(font, mouseX, mouseY, screenHeight,
                inputX);
        regions.add(inputX, top, inputX + width, bottom);
        int shown = Math.min(this.candidates.size(), MAX_ROWS);
        LostTalesChatVisualStyle.drawPopupList(inputX, top, inputX + width,
                bottom, top + PADDING, ROW_HEIGHT, litRow(hoveredRow, shown));
        for (int row = 0; row < shown; row++) {
            int rowTop = top + PADDING + row * ROW_HEIGHT;
            LostTalesChatVisualStyle.drawColored(font,
                    this.candidates.get(row), inputX + 4, rowTop + 2,
                    LostTalesChatVisualStyle.IVORY, 255);
        }
        if (this.candidates.size() > MAX_ROWS) {
            LostTalesChatVisualStyle.drawColored(font,
                    "+" + (this.candidates.size() - MAX_ROWS),
                    inputX + 4, top + PADDING + shown * ROW_HEIGHT + 2,
                    LostTalesChatVisualStyle.IVORY, 160);
        }
    }

    /**
     * The one row lit: the one under the pointer, which a press takes,
     * else the candidate standing in the field. Cycling past the fold
     * lights the fold row: the list scrolls no further, so the fold
     * stands for wherever the walk is.
     */
    private int litRow(int hoveredRow, int shown) {
        if (hoveredRow >= 0) {
            return hoveredRow;
        }
        return this.selectedIndex >= MAX_ROWS ? shown : this.selectedIndex;
    }

    private int boxWidth(FontRenderer font) {
        int width = 0;
        int shown = Math.min(this.candidates.size(), MAX_ROWS);
        for (int index = 0; index < shown; index++) {
            width = Math.max(width,
                    font.getStringWidth(this.candidates.get(index)));
        }
        if (this.candidates.size() > MAX_ROWS) {
            width = Math.max(width, font.getStringWidth(
                    "+" + (this.candidates.size() - MAX_ROWS)));
        }
        return width + 8;
    }

    private int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN - rowCount() * ROW_HEIGHT
                - PADDING * 2;
    }
}
