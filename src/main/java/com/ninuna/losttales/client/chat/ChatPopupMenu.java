package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * A small vertical list of actions anchored above a control — the
 * channel settings behind a tab's cog, the closed channels behind the
 * restore control. One instance serves the whole chat screen: opening it
 * for another purpose replaces the previous list. Entries are plain ids
 * the screen interprets; the menu only lays them out, draws them, hit
 * tests them and registers its rectangle so nothing under it reacts. An
 * entry may carry a second, right-aligned <em>action</em> column —
 * {@code Mute} beside a closed channel — that hit tests apart from the
 * entry itself, so one row offers two things without a submenu. The
 * list opens upward from its anchor, above the strip whose control
 * opened it, so it never lies over the history it belongs to.
 */
final class ChatPopupMenu {
    static final int ROW_HEIGHT = 11;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int MIN_WIDTH = 56;
    /** Gap between the widest label and the action column. */
    private static final int ACTION_GAP = 8;

    /** Width of the upright colour bar before a channel's name, and its gap. */
    private static final int SWATCH_WIDTH = 1;
    private static final int SWATCH_GAP = 4;

    static final class Entry {
        final String id;
        final String label;
        /** Right-aligned action text, or empty for a plain entry. */
        final String action;
        /** Drawn italic: a muted channel, like its tab. */
        final boolean dim;
        /** The channel's colour, shown as a small bar before the name; -1 for none. */
        final int color;
        /** The channel's emote before the name, or null. */
        final ChatEmoji icon;

        Entry(String id, String label) {
            this(id, label, "", false, -1, null);
        }

        Entry(String id, String label, String action, boolean dim,
              int color, ChatEmoji icon) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.action = action == null ? "" : action;
            this.dim = dim;
            this.color = color;
            this.icon = icon;
        }
    }

    private String kind = "";
    private ChatTab channel;
    private List<Entry> entries = Collections.emptyList();
    private int x;
    private int y;
    private int width;
    private int height;
    /** Left edge of the labels inside the menu, past any swatch column. */
    private int labelX = PADDING_X;
    /** Left edge of the action column inside the menu; -1 without one. */
    private int actionX = -1;

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
     * Opens the list with its left edge at {@code anchorX} and its
     * bottom edge at {@code anchorBottom} — it grows upward — shifted
     * to stay inside the screen.
     */
    void open(String kind, ChatTab channel, List<Entry> entries,
              FontRenderer font, int anchorX, int anchorBottom,
              int screenWidth, int screenHeight) {
        if (entries == null || entries.isEmpty() || font == null) {
            close();
            return;
        }
        this.kind = kind == null ? "" : kind;
        this.channel = channel;
        this.entries = new ArrayList<Entry>(entries);
        int widest = 0;
        int widestAction = 0;
        boolean swatches = false;
        boolean icons = false;
        for (Entry entry : this.entries) {
            widest = Math.max(widest, font.getStringWidth(entry.label));
            widestAction = Math.max(widestAction,
                    font.getStringWidth(entry.action));
            swatches |= entry.color >= 0;
            icons |= entry.icon != null;
        }
        // One swatch column and one icon column for the whole list, so
        // the names line up.
        this.labelX = PADDING_X + (swatches ? SWATCH_WIDTH + SWATCH_GAP : 0)
                + (icons ? ChatChannelIcons.SIZE + ChatChannelIcons.GAP : 0);
        this.actionX = widestAction > 0
                ? this.labelX + widest + ACTION_GAP : -1;
        int content = widestAction > 0
                ? this.actionX + widestAction : this.labelX + widest;
        this.width = Math.max(MIN_WIDTH, content + PADDING_X);
        this.height = PADDING_Y * 2 + ROW_HEIGHT * this.entries.size();
        this.x = Math.max(0, Math.min(screenWidth - this.width, anchorX));
        this.y = Math.max(0, Math.min(screenHeight - this.height,
                anchorBottom - this.height));
    }

    /**
     * Swaps the entries of the open menu in place — after an action
     * changed what a row should say — keeping the anchor so the list
     * does not jump under the pointer.
     */
    void replaceEntries(List<Entry> entries, FontRenderer font,
                        int screenWidth, int screenHeight) {
        open(this.kind, this.channel, entries, font, this.x,
                this.y + this.height, screenWidth, screenHeight);
    }

    void close() {
        this.entries = Collections.emptyList();
        this.kind = "";
        this.channel = null;
        this.actionX = -1;
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

    /** Whether the point is on the action column of a row that has one. */
    boolean isOnAction(int mouseX, int mouseY) {
        Entry entry = entryAt(mouseX, mouseY);
        return entry != null && entry.action.length() > 0
                && this.actionX >= 0 && mouseX >= this.x + this.actionX - 2;
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
        // A one-pixel outline, so the list reads as a panel of its own.
        int outline = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB, 0xE0);
        Gui.drawRect(this.x, this.y, this.x + this.width, this.y + 1, outline);
        Gui.drawRect(this.x, this.y + this.height - 1, this.x + this.width,
                this.y + this.height, outline);
        Gui.drawRect(this.x, this.y, this.x + 1, this.y + this.height,
                outline);
        Gui.drawRect(this.x + this.width - 1, this.y, this.x + this.width,
                this.y + this.height, outline);
        Entry hovered = entryAt(mouseX, mouseY);
        boolean onAction = isOnAction(mouseX, mouseY);
        int rowY = this.y + PADDING_Y;
        for (Entry entry : this.entries) {
            boolean hasAction = entry.action.length() > 0
                    && this.actionX >= 0;
            if (entry == hovered) {
                // The highlight follows the half the pointer is on.
                int splitX = hasAction && onAction
                        ? this.x + this.actionX - 2 : this.x + 1;
                int splitRight = hasAction && !onAction
                        ? this.x + this.actionX - 2 : this.x + this.width - 1;
                Gui.drawRect(splitX, rowY, splitRight, rowY + ROW_HEIGHT,
                        LostTalesChatVisualStyle.SURFACE_HOVER);
            }
            if (entry.color >= 0) {
                // The channel's colour as a one-pixel upright bar the
                // height of the row's text.
                Gui.drawRect(this.x + PADDING_X, rowY + 1,
                        this.x + PADDING_X + SWATCH_WIDTH,
                        rowY + ROW_HEIGHT - 1,
                        LostTalesChatVisualStyle.argb(entry.color, 0xFF));
            }
            if (entry.icon != null) {
                ChatInlineIcons.drawEmoji(Minecraft.getMinecraft(), entry.icon,
                        this.x + this.labelX - ChatChannelIcons.SIZE
                                - ChatChannelIcons.GAP,
                        rowY + 0.5F, ChatChannelIcons.SIZE, 255);
            }
            // Text at full opacity always; a muted channel is italic, the
            // hovered half is told by its highlight.
            LostTalesChatVisualStyle.drawPlain(font,
                    entry.dim ? "§o" + entry.label : entry.label,
                    this.x + this.labelX, rowY + 2, 255);
            if (hasAction) {
                LostTalesChatVisualStyle.drawPlain(font, entry.action,
                        this.x + this.actionX, rowY + 2, 255);
            }
            rowY += ROW_HEIGHT;
        }
        registerRegion(regions);
    }
}
