package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * A small vertical list of actions anchored under a control — the
 * channel settings behind a tab's cog, the closed channels behind the
 * restore control. One instance serves the whole chat screen: opening it
 * for another purpose replaces the previous list. Entries are plain ids
 * the screen interprets; the menu only lays them out, draws them, hit
 * tests them and registers its rectangle so nothing under it reacts.
 */
final class ChatPopupMenu {
    static final int ROW_HEIGHT = 11;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int MIN_WIDTH = 56;

    static final class Entry {
        final String id;
        final String label;

        Entry(String id, String label) {
            this.id = id;
            this.label = label == null ? "" : label;
        }
    }

    private String kind = "";
    private ChatTab channel;
    private List<Entry> entries = Collections.emptyList();
    private int x;
    private int y;
    private int width;
    private int height;

    boolean isOpen() {
        return !this.entries.isEmpty();
    }

    String kind() {
        return this.kind;
    }

    ChatTab channel() {
        return this.channel;
    }

    /**
     * Opens the list with its top-left at the anchor, shifted to stay
     * inside the screen.
     */
    void open(String kind, ChatTab channel, List<Entry> entries,
              FontRenderer font, int anchorX, int anchorY,
              int screenWidth, int screenHeight) {
        if (entries == null || entries.isEmpty() || font == null) {
            close();
            return;
        }
        this.kind = kind == null ? "" : kind;
        this.channel = channel;
        this.entries = new ArrayList<Entry>(entries);
        int widest = 0;
        for (Entry entry : this.entries) {
            widest = Math.max(widest, font.getStringWidth(entry.label));
        }
        this.width = Math.max(MIN_WIDTH, widest + PADDING_X * 2);
        this.height = PADDING_Y * 2 + ROW_HEIGHT * this.entries.size();
        this.x = Math.max(0, Math.min(screenWidth - this.width, anchorX));
        this.y = Math.max(0, Math.min(screenHeight - this.height, anchorY));
    }

    void close() {
        this.entries = Collections.emptyList();
        this.kind = "";
        this.channel = null;
    }

    boolean contains(int mouseX, int mouseY) {
        return isOpen() && mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }

    Entry entryAt(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return null;
        }
        int index = (mouseY - this.y - PADDING_Y) / ROW_HEIGHT;
        return index >= 0 && index < this.entries.size()
                ? this.entries.get(index) : null;
    }

    /**
     * Claims the menu's rectangle for this frame. Called before anything
     * under the menu asks who owns the pointer; the menu itself is drawn
     * last so it paints above everything.
     */
    void registerRegion(ChatPointerRegions regions) {
        if (isOpen()) {
            regions.addScreen(this.x, this.y, this.x + this.width,
                    this.y + this.height);
        }
    }

    void draw(FontRenderer font, ChatPointerRegions regions,
              int mouseX, int mouseY) {
        if (!isOpen() || font == null) {
            return;
        }
        Gui.drawRect(this.x, this.y, this.x + this.width,
                this.y + this.height, LostTalesChatVisualStyle.SURFACE);
        Gui.drawRect(this.x, this.y, this.x + this.width, this.y + 1,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                        0xE0));
        Entry hovered = entryAt(mouseX, mouseY);
        int rowY = this.y + PADDING_Y;
        for (Entry entry : this.entries) {
            if (entry == hovered) {
                Gui.drawRect(this.x + 1, rowY, this.x + this.width - 1,
                        rowY + ROW_HEIGHT,
                        LostTalesChatVisualStyle.SURFACE_HOVER);
            }
            LostTalesChatVisualStyle.drawPlain(font, entry.label,
                    this.x + PADDING_X, rowY + 2,
                    entry == hovered ? 255 : 215);
            rowY += ROW_HEIGHT;
        }
        registerRegion(regions);
    }
}
