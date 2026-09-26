package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.PointerRegions;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
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
final class ChatCommandSuggestionBox extends ChatSuggestionBox {

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

    @Override
    boolean isActive() {
        return !this.candidates.isEmpty();
    }

    /** Rows drawn: the candidates, capped, plus a possible fold row. */
    @Override
    int shownRows() {
        int shown = Math.min(this.candidates.size(), MAX_ROWS);
        return shown + (this.candidates.size() > MAX_ROWS ? 1 : 0);
    }

    /** The candidates shown; the fold row is not one. */
    @Override
    int pickableRows() {
        return Math.min(this.candidates.size(), MAX_ROWS);
    }

    @Override
    int rowHeight() {
        return ROW_HEIGHT;
    }

    void draw(FontRenderer font, PointerRegions regions,
              int screenHeight, int inputX, double mouseX, double mouseY) {
        if (!isActive()) {
            return;
        }
        int hoveredRow = rowAt(font, mouseX, mouseY, screenHeight, inputX);
        int shown = Math.min(this.candidates.size(), MAX_ROWS);
        int top = drawFrame(font, regions, screenHeight, inputX,
                litRow(hoveredRow, shown));
        for (int row = 0; row < shown; row++) {
            int rowTop = top + PADDING + row * ROW_HEIGHT;
            LostTalesUiInk.drawText(font,
                    this.candidates.get(row), inputX + PADDING, rowTop + 2,
                    LostTalesUiInk.IVORY, 255);
        }
        if (this.candidates.size() > MAX_ROWS) {
            LostTalesUiInk.drawText(font,
                    "+" + (this.candidates.size() - MAX_ROWS),
                    inputX + PADDING, top + PADDING + shown * ROW_HEIGHT + 2,
                    LostTalesUiInk.IVORY, 160);
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

    @Override
    int boxWidth(FontRenderer font) {
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
        return width + PADDING * 2;
    }
}
