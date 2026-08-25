package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * A small vertical list of actions anchored above a control — the
 * channel settings behind a tab's cog, the closed channels and open-able
 * conversations behind the {@code +}. One instance serves the whole chat
 * screen: opening it for another purpose replaces the previous list.
 * Entries are plain ids the screen interprets; the menu only lays them
 * out, draws them, hit tests them and registers its rectangle so nothing
 * under it reacts. A list may carry <em>header</em> rows — a section
 * label over a hairline, never clickable — and a list taller than
 * {@link #MAX_VISIBLE_ROWS} shows that many rows and scrolls by the
 * wheel, a honey hairline on an edge saying more lies past it. The list
 * opens upward from its anchor, above the strip whose control opened it,
 * so it never lies over the history it belongs to.
 */
final class ChatPopupMenu {
    static final int ROW_HEIGHT = 11;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int MIN_WIDTH = 56;
    /** Rows shown at most; a longer list scrolls behind them. */
    static final int MAX_VISIBLE_ROWS = 12;

    /** Width of the upright colour bar before a channel's name, and its gap. */
    private static final int SWATCH_WIDTH = 1;
    private static final int SWATCH_GAP = 4;
    /** The hairline that says the list continues past an edge. */
    private static final int MORE_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);

    static final class Entry {
        final String id;
        final String label;
        /** A section label over a hairline; never hovered, never clicked. */
        final boolean header;
        /** Drawn italic: a muted channel, like its tab. */
        final boolean dim;
        /** The channel's colour, shown as a small bar before the name; -1 for none. */
        final int color;
        /** The tab whose icon stands before the name, or null for none. */
        final ChatTab icon;
        /** Player whose head stands before the name, or null for none. */
        ChatHeadOwner head;
        /** A sheet sprite before the name — the appearance lock — or null. */
        ChatIconSheet sprite;

        Entry(String id, String label) {
            this(id, label, false, false, -1, null);
        }

        Entry(String id, String label, boolean dim, int color, ChatTab icon) {
            this(id, label, false, dim, color, icon);
        }

        private Entry(String id, String label, boolean header, boolean dim,
                      int color, ChatTab icon) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.header = header;
            this.dim = dim;
            this.color = color;
            this.icon = icon;
        }

        /** A section label: {@code Channels}, {@code Direct Messages}. */
        static Entry header(String label) {
            return new Entry("", label, true, false, -1, null);
        }

        Entry withHead(java.util.UUID owner, String skinId) {
            this.head = new ChatHeadOwner(owner, skinId);
            return this;
        }

        Entry withSprite(ChatIconSheet sprite) {
            this.sprite = sprite;
            return this;
        }
    }

    /** The head a row wears: an account's, or a character skin's. */
    static final class ChatHeadOwner {
        final java.util.UUID owner;
        final String skinId;

        ChatHeadOwner(java.util.UUID owner, String skinId) {
            this.owner = owner;
            this.skinId = skinId == null ? "" : skinId;
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
    /** First row asked for — the wheel's target; rows above it lie past
     *  the top edge. */
    private int scrollRows;
    /**
     * The row offset the list is drawn at, easing toward
     * {@link #scrollRows} with the chat's shared scroll motion so a
     * wheel turn glides the rows instead of jumping them. Hit testing
     * reads this too, so it always answers for what is on screen.
     */
    private double renderedScrollRows;
    private long scrollNanos;
    /** Rows the menu has room for; the rest scroll. */
    private int visibleRows;

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
     * to stay inside the screen. The scroll survives a re-open in place
     * ({@link #replaceEntries}), clamped to the new list.
     */
    void open(String kind, ChatTab channel, List<Entry> entries,
              FontRenderer font, int anchorX, int anchorBottom,
              int screenWidth, int screenHeight) {
        if (entries == null || entries.isEmpty() || font == null) {
            close();
            return;
        }
        boolean samePlace = !this.kind.equals("") && this.kind.equals(kind);
        int keptScroll = samePlace ? this.scrollRows : 0;
        this.kind = kind == null ? "" : kind;
        this.channel = channel;
        this.entries = new ArrayList<Entry>(entries);
        int widest = 0;
        boolean swatches = false;
        boolean icons = false;
        for (Entry entry : this.entries) {
            widest = Math.max(widest, font.getStringWidth(entry.label));
            swatches |= entry.color >= 0;
            icons |= entry.icon != null || entry.head != null
                    || entry.sprite != null;
        }
        // One swatch column and one icon column for the whole list, so
        // the names line up; headers hang left of them with the padding.
        this.labelX = PADDING_X + (swatches ? SWATCH_WIDTH + SWATCH_GAP : 0)
                + (icons ? ChatChannelIcons.SIZE + ChatChannelIcons.GAP : 0);
        this.width = Math.max(MIN_WIDTH, this.labelX + widest + PADDING_X);
        // As many rows as the cap and the room above the anchor allow.
        int roomRows = (anchorBottom - 2 - PADDING_Y * 2) / ROW_HEIGHT;
        this.visibleRows = Math.max(1, Math.min(this.entries.size(),
                Math.min(MAX_VISIBLE_ROWS, roomRows)));
        this.height = PADDING_Y * 2 + ROW_HEIGHT * this.visibleRows;
        this.x = Math.max(0, Math.min(screenWidth - this.width, anchorX));
        this.y = Math.max(0, Math.min(screenHeight - this.height,
                anchorBottom - this.height));
        this.scrollRows = clampScroll(keptScroll);
        if (!samePlace) {
            // A fresh opening starts where it is asked; only wheel turns
            // on the open list glide.
            this.renderedScrollRows = this.scrollRows;
        }
    }

    /**
     * Swaps the entries of the open menu in place — after something
     * changed what a row should say, or who the rows are — keeping the
     * anchor and the scroll so the list does not jump under the pointer.
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
        this.scrollRows = 0;
        this.renderedScrollRows = 0.0D;
        this.visibleRows = 0;
    }

    boolean contains(int mouseX, int mouseY) {
        return isOpen() && mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }

    /** Moves the list's target by whole rows; beyond either end it stays
     *  put. The drawn rows glide after the target. */
    void scrollBy(int rows) {
        this.scrollRows = clampScroll(this.scrollRows + rows);
    }

    private int clampScroll(int rows) {
        return Math.max(0, Math.min(
                this.entries.size() - this.visibleRows, rows));
    }

    /**
     * Advances the drawn offset toward its target, once per drawn
     * frame; with chat animations off it simply arrives.
     */
    private void advanceScrollEasing() {
        long now = System.nanoTime();
        double elapsed = (now - this.scrollNanos) / 1.0E9D;
        this.scrollNanos = now;
        if (!LostTalesConfig.enableChatAnimations
                || Math.abs(this.scrollRows - this.renderedScrollRows)
                        <= 0.01D) {
            this.renderedScrollRows = this.scrollRows;
            return;
        }
        this.renderedScrollRows = LostTalesChatMotion.approach(
                this.renderedScrollRows, this.scrollRows, elapsed,
                LostTalesChatMotion.SCROLL_EASE_SECONDS);
    }

    /** The clickable entry under the point, resolved against the drawn
     *  offset so a gliding list answers for what is on screen; headers
     *  and the padding bands are nobody's. */
    Entry entryAt(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)
                || mouseY < this.y + PADDING_Y
                || mouseY >= this.y + this.height - PADDING_Y) {
            return null;
        }
        int index = (int)Math.floor((mouseY - this.y - PADDING_Y)
                / (double)ROW_HEIGHT + this.renderedScrollRows);
        Entry entry = index >= 0 && index < this.entries.size()
                ? this.entries.get(index) : null;
        return entry == null || entry.header ? null : entry;
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
        advanceScrollEasing();
        Entry hovered = entryAt(mouseX, mouseY);
        // Rows are laid out from the drawn offset — whole rows pick where
        // the list starts, the fraction slides it — and clipped to the
        // menu's interior so the glide never paints over its frame; one
        // extra row fills the gap the slide opens.
        int firstRow = (int)Math.floor(this.renderedScrollRows);
        int rowY = this.y + PADDING_Y - (int)Math.round(
                (this.renderedScrollRows - firstRow) * ROW_HEIGHT);
        int last = Math.min(this.entries.size(),
                firstRow + this.visibleRows + 1);
        boolean clipped = LostTalesChatOverlayRenderer.beginVerticalClip(
                Minecraft.getMinecraft(), this.y + PADDING_Y - 1.0D,
                this.y + this.height - PADDING_Y + 1.0D, false);
        try {
        for (int index = Math.max(0, firstRow); index < last; index++) {
            Entry entry = this.entries.get(index);
            if (entry.header) {
                // The section's name over a hairline, in the sand the
                // timestamps wear, so it reads as a label, not a row.
                LostTalesChatVisualStyle.drawColored(font, entry.label,
                        this.x + PADDING_X, rowY + 1,
                        LostTalesColors.rgb(LostTalesColors.SAND), 255);
                Gui.drawRect(this.x + PADDING_X, rowY + ROW_HEIGHT - 1,
                        this.x + this.width - PADDING_X, rowY + ROW_HEIGHT,
                        outline);
                rowY += ROW_HEIGHT;
                continue;
            }
            if (entry == hovered) {
                Gui.drawRect(this.x + 1, rowY, this.x + this.width - 1,
                        rowY + ROW_HEIGHT,
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
                ChatChannelIcons.draw(Minecraft.getMinecraft(), entry.icon,
                        this.x + this.labelX - ChatChannelIcons.SIZE
                                - ChatChannelIcons.GAP,
                        rowY + 0.5F, 255);
            } else if (entry.head != null) {
                // A head drawn as the tabs draw theirs: eight pixels,
                // centred in the icon column.
                float headX = this.x + this.labelX - ChatChannelIcons.SIZE
                        - ChatChannelIcons.GAP + 1.0F;
                if (entry.head.skinId.length() == 0) {
                    LostTalesCharacterHeadIconRenderer.drawAccountHead(
                            Minecraft.getMinecraft(), entry.head.owner,
                            headX, rowY + 1.5F, 8.0F, 1.0F, 1.0F);
                } else {
                    LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                            Minecraft.getMinecraft(), entry.head.owner,
                            entry.head.skinId, headX, rowY + 1.5F, 8.0F,
                            1.0F, 1.0F);
                }
            } else if (entry.sprite != null) {
                entry.sprite.drawWithShadow(
                        this.x + this.labelX - ChatChannelIcons.SIZE
                                - ChatChannelIcons.GAP + 1,
                        rowY + 2, 255);
            }
            // Text at full opacity always; a muted channel is italic, the
            // hovered row is told by its highlight.
            LostTalesChatVisualStyle.drawPlain(font,
                    entry.dim ? "§o" + entry.label : entry.label,
                    this.x + this.labelX, rowY + 2, 255);
            rowY += ROW_HEIGHT;
        }
        } finally {
            LostTalesChatOverlayRenderer.endVerticalClip(clipped);
        }
        // A hairline on an edge the list continues past.
        if (this.scrollRows > 0) {
            Gui.drawRect(this.x + 1, this.y + 1, this.x + this.width - 1,
                    this.y + 2,
                    LostTalesChatVisualStyle.argb(MORE_RGB, 0xFF));
        }
        if (last < this.entries.size()) {
            Gui.drawRect(this.x + 1, this.y + this.height - 2,
                    this.x + this.width - 1, this.y + this.height - 1,
                    LostTalesChatVisualStyle.argb(MORE_RGB, 0xFF));
        }
        registerRegion(regions);
    }
}
