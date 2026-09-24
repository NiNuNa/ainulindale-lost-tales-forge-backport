package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiSuggester;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Live shortcode completion list shown above the chat input while an
 * unclosed {@code :prefix} sits at the cursor. Selection state lives here;
 * applying a completion to the input field is the chat screen's job.
 */
final class ChatEmojiSuggestionBox {
    static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 12;
    /** The rows stand two pixels inside the frame's ink, as a framed button's content does. */
    private static final int PADDING = LostTalesChatVisualStyle.POPUP_INSET;
    /** Between the emoji's slot and its shortcode. */
    private static final int ICON_GAP = 4;
    /**
     * How far above the input anchor ({@link ChatInputBar#inputAnchor})
     * the box ends: one pixel clear of the bar's top.
     */
    private static final int BOTTOM_MARGIN = 15;

    private List<ChatEmoji> matches = Collections.emptyList();
    private ChatEmojiSuggester.Query query;
    private int selectedIndex;
    private int dismissedColonIndex = -1;

    /** Recomputes the query and matches; cheap enough to run per frame. */
    void update(String text, int cursor) {
        ChatEmojiSuggester.Query found =
                ChatEmojiSuggester.findQuery(text, cursor);
        if (found == null) {
            this.query = null;
            this.matches = Collections.emptyList();
            this.dismissedColonIndex = -1;
            return;
        }
        if (found.colonIndex != this.dismissedColonIndex) {
            this.dismissedColonIndex = -1;
        }
        boolean changed = this.query == null
                || this.query.colonIndex != found.colonIndex
                || !this.query.prefix.equals(found.prefix);
        this.query = found;
        if (changed) {
            this.matches = ChatEmojiSuggester.matches(
                    found.prefix, MAX_ROWS);
            this.selectedIndex = 0;
        }
    }

    boolean isActive() {
        return this.query != null && !this.matches.isEmpty()
                && this.query.colonIndex != this.dismissedColonIndex;
    }

    ChatEmoji getSelected() {
        return isActive() && this.selectedIndex < this.matches.size()
                ? this.matches.get(this.selectedIndex) : null;
    }

    ChatEmojiSuggester.Query getQuery() {
        return this.query;
    }

    void moveSelection(int delta) {
        if (!isActive()) {
            return;
        }
        int size = this.matches.size();
        this.selectedIndex =
                ((this.selectedIndex + delta) % size + size) % size;
    }

    /** Hides the current query's list until a new query starts. */
    void dismiss() {
        if (this.query != null) {
            this.dismissedColonIndex = this.query.colonIndex;
        }
    }

    /** True while the mouse is over the visible popup, including padding. */
    boolean contains(FontRenderer font, double mouseX, double mouseY,
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
    int rowAt(FontRenderer font, double mouseX, double mouseY,
              int screenHeight, int inputX) {
        if (!contains(font, mouseX, mouseY, screenHeight, inputX)
                || mouseY < boxTop(screenHeight) + PADDING) {
            return -1;
        }
        int row = (int)Math.floor((mouseY - boxTop(screenHeight) - PADDING)
                / (double)ROW_HEIGHT);
        return row >= 0 && row < this.matches.size() ? row : -1;
    }

    /** The suggestion on a row, or null. */
    ChatEmoji at(int row) {
        return row >= 0 && row < this.matches.size()
                ? this.matches.get(row) : null;
    }

    void draw(Minecraft minecraft, FontRenderer font,
              ChatPointerRegions regions, int screenHeight,
              int inputX, double mouseX, double mouseY) {
        if (!isActive()) {
            return;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        int bottom = screenHeight - BOTTOM_MARGIN;
        int hoveredRow = rowAt(font, mouseX, mouseY, screenHeight, inputX);
        if (hoveredRow >= 0) {
            this.selectedIndex = hoveredRow;
        }
        regions.add(inputX, top, inputX + width, bottom);
        LostTalesChatVisualStyle.drawPopupList(inputX, top, inputX + width,
                bottom, top + PADDING, ROW_HEIGHT,
                this.selectedIndex < this.matches.size()
                        ? this.selectedIndex : -1);
        for (int row = 0; row < this.matches.size(); row++) {
            ChatEmoji emoji = this.matches.get(row);
            int rowTop = top + PADDING + row * ROW_HEIGHT;
            ChatInlineIcons.drawEmoji(minecraft, emoji,
                    ChatInlineIcons.boxLeft(inputX + PADDING,
                            ChatInlineIcons.SLOT_WIDTH),
                    ChatInlineIcons.boxTop(rowTop + 2,
                            ChatInlineIcons.SLOT_WIDTH),
                    ChatInlineIcons.CONTENT_SIZE, 255);
            LostTalesChatVisualStyle.drawPlain(font, emoji.getShortcode(),
                    inputX + PADDING + ChatInlineIcons.SLOT_WIDTH + ICON_GAP,
                    rowTop + 2,
                    row == this.selectedIndex ? 255 : 200);
        }
    }

    private int boxWidth(FontRenderer font) {
        int width = 0;
        for (ChatEmoji emoji : this.matches) {
            width = Math.max(width,
                    font.getStringWidth(emoji.getShortcode()));
        }
        return PADDING + ChatInlineIcons.SLOT_WIDTH + ICON_GAP + width
                + PADDING;
    }

    private int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN
                - this.matches.size() * ROW_HEIGHT - PADDING * 2;
    }
}
